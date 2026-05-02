#!/usr/bin/env bash
set -euo pipefail

# ──────────────────────────────────────────────
# KingshotMacro – offline manual build script
# Produces: app/build/outputs/apk/debug/app-debug.apk
# ──────────────────────────────────────────────

ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
KOTLINC=/tmp/kotlinc/bin/kotlinc
DX=/usr/lib/android-sdk/build-tools/debian/dx
AAPT2=/usr/lib/android-sdk/build-tools/29.0.3/aapt2
APKSIGNER=/usr/lib/android-sdk/build-tools/29.0.3/apksigner
ZIPALIGN=/usr/lib/android-sdk/build-tools/29.0.3/zipalign
KOTLIN_STDLIB=/tmp/kotlinc/lib/kotlin-stdlib.jar

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$PROJECT_DIR/app/src/main"
BUILD="$PROJECT_DIR/build_tmp"
OUT="$PROJECT_DIR/app/build/outputs/apk/debug"

# Sanity-check every tool before doing any work — explicit failure beats silent corruption.
for f in "$ANDROID_JAR" "$KOTLINC" "$DX" "$AAPT2" "$APKSIGNER" "$ZIPALIGN" "$KOTLIN_STDLIB"; do
    if [ ! -e "$f" ]; then
        echo "ERROR: missing tool: $f" >&2
        exit 1
    fi
done

echo "==> Cleaning build directory"
rm -rf "$BUILD"
mkdir -p "$BUILD/compiled_res" "$BUILD/gen" "$BUILD/classes" "$OUT"

# ── 1. Compile resources ──────────────────────
echo "==> Compiling resources"
"$AAPT2" compile --dir "$SRC/res" -o "$BUILD/compiled_res/"

# ── 2. Link resources → resources.ap_ + R.java
# NOTE: We do NOT pass --target-sdk-version / --min-sdk-version here.
# The manifest's <uses-sdk> element is the source of truth (min=26, target=29).
# Passing flags here only adds noise; aapt2 keeps the manifest values.
echo "==> Linking resources"
"$AAPT2" link \
    -I "$ANDROID_JAR" \
    --manifest "$SRC/AndroidManifest.xml" \
    --version-code 4 \
    --version-name "2.2-bare-test" \
    -o "$BUILD/resources.ap_" \
    "$BUILD/compiled_res/"*.flat \
    --java "$BUILD/gen"

# ── 3. Compile generated R.java ───────────────
echo "==> Compiling R.java"
javac -source 1.8 -target 1.8 \
    -classpath "$ANDROID_JAR" \
    -d "$BUILD/classes" \
    $(find "$BUILD/gen" -name "*.java")

# ── 4. Bundle R into a jar ────────────────────
echo "==> Creating r.jar"
( cd "$BUILD/classes" && jar cf "$BUILD/r.jar" . )

# ── 5. Compile Kotlin sources ─────────────────
echo "==> Compiling Kotlin sources"
"$KOTLINC" \
    -classpath "$ANDROID_JAR:$BUILD/r.jar:$KOTLIN_STDLIB" \
    -jvm-target 1.8 \
    -no-reflect \
    -d "$BUILD/app_classes.jar" \
    "$SRC/java/com/kingshot/macro/"

# ── 6. Strip multi-release entries from kotlin-stdlib (dx can't parse them)
echo "==> Preparing stdlib for DEX"
cp "$KOTLIN_STDLIB" "$BUILD/kotlin-stdlib-stripped.jar"
zip -d "$BUILD/kotlin-stdlib-stripped.jar" "META-INF/versions/*" >/dev/null 2>&1 || true
zip -d "$BUILD/kotlin-stdlib-stripped.jar" "module-info.class" >/dev/null 2>&1 || true

# ── 7. Convert to Dalvik (.dex) ───────────────
echo "==> Converting to DEX"
"$DX" --dex \
    --min-sdk-version=26 \
    --output="$BUILD/classes.dex" \
    "$BUILD/app_classes.jar" \
    "$BUILD/r.jar" \
    "$BUILD/kotlin-stdlib-stripped.jar"

# ── 8. Package unsigned APK ───────────────────
echo "==> Packaging APK"
cp "$BUILD/resources.ap_" "$BUILD/unsigned.apk"
( cd "$BUILD" && zip -j unsigned.apk classes.dex >/dev/null )

# ── 9. Align APK ──────────────────────────────
echo "==> Zipaligning"
"$ZIPALIGN" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

# ── 10. Persistent debug keystore ─────────────
KEYSTORE="$PROJECT_DIR/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    echo "==> Generating persistent debug keystore"
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -alias androiddebugkey \
        -keyalg RSA -keysize 2048 \
        -validity 10000 \
        -dname "CN=Debug, O=Android, C=US" \
        -storepass android \
        -keypass android \
        -noprompt
fi

# ── 11. Sign APK (v1+v2+v3) ───────────────────
echo "==> Signing APK"
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --out "$OUT/app-debug.apk" \
    "$BUILD/aligned.apk"

echo ""
echo "==> Verification"
"$APKSIGNER" verify --print-certs "$OUT/app-debug.apk" | head -10
"$AAPT2" dump badging "$OUT/app-debug.apk" | head -3

echo ""
echo "==> Build successful!"
echo "==> APK: $OUT/app-debug.apk"
ls -lh "$OUT/app-debug.apk"
