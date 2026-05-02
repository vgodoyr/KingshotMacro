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
AAPT=/usr/lib/android-sdk/build-tools/debian/aapt
APKSIGNER=/usr/lib/android-sdk/build-tools/29.0.3/apksigner
ZIPALIGN=/usr/lib/android-sdk/build-tools/29.0.3/zipalign
KOTLIN_STDLIB=/tmp/kotlinc/lib/kotlin-stdlib.jar

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$PROJECT_DIR/app/src/main"
BUILD="$PROJECT_DIR/build_tmp"
OUT="$PROJECT_DIR/app/build/outputs/apk/debug"

echo "==> Cleaning build directory"
rm -rf "$BUILD"
mkdir -p "$BUILD/compiled_res" "$BUILD/gen" "$BUILD/classes" "$OUT"

# ── 1. Compile ALL resources with aapt2 (XML + PNG) ────────
echo "==> Compiling resources"
"$AAPT2" compile --dir "$SRC/res" -o "$BUILD/compiled_res/"

# ── 2. Link resources → resources.ap_ + R.java ─
echo "==> Linking resources"
"$AAPT2" link \
    -I "$ANDROID_JAR" \
    --manifest "$SRC/AndroidManifest.xml" \
    --min-sdk-version 21 \
    --target-sdk-version 23 \
    --version-code 1 \
    --version-name "1.0" \
    -o "$BUILD/resources.ap_" \
    "$BUILD/compiled_res/"*.flat \
    --java "$BUILD/gen" \
    2>&1 | grep -v "^$" || true

# ── 3. Compile R.java ──────────────────────────
echo "==> Compiling R.java"
javac -source 1.8 -target 1.8 \
    -classpath "$ANDROID_JAR" \
    -d "$BUILD/classes" \
    $(find "$BUILD/gen" -name "*.java") 2>&1

# ── 4. Bundle R classes into jar ──────────────
echo "==> Creating R.jar"
cd "$BUILD/classes"
jar cf "$BUILD/r.jar" .
cd "$PROJECT_DIR"

# ── 5. Compile Kotlin sources ─────────────────
echo "==> Compiling Kotlin sources"
"$KOTLINC" \
    -classpath "$ANDROID_JAR:$BUILD/r.jar:$KOTLIN_STDLIB" \
    -jvm-target 1.8 \
    -no-reflect \
    -d "$BUILD/app_classes.jar" \
    "$SRC/java/com/kingshot/macro/" \
    2>&1

# ── 6. Strip multi-release entries from kotlin-stdlib (dx can't parse them)
echo "==> Preparing stdlib for DEX"
cp "$KOTLIN_STDLIB" "$BUILD/kotlin-stdlib-stripped.jar"
zip -d "$BUILD/kotlin-stdlib-stripped.jar" "META-INF/versions/*" 2>/dev/null || true

# ── 7. Convert to Dalvik (.dex) ───────────────
echo "==> Converting to DEX"
"$DX" --dex \
    --min-sdk-version=26 \
    --output="$BUILD/classes.dex" \
    "$BUILD/app_classes.jar" \
    "$BUILD/r.jar" \
    "$BUILD/kotlin-stdlib-stripped.jar" \
    2>&1

# ── 7. Package unsigned APK ───────────────────
echo "==> Packaging APK"
cp "$BUILD/resources.ap_" "$BUILD/unsigned.apk"
cd "$BUILD"
zip -j unsigned.apk classes.dex
cd "$PROJECT_DIR"

# ── 8. Align APK ──────────────────────────────
echo "==> Zipaligning"
"$ZIPALIGN" -f 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

# ── 9. Use persistent keystore (same key across all builds) ───────────────────
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
        -noprompt 2>&1
fi

# ── 10. Sign APK ──────────────────────────────
echo "==> Signing APK"
"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$OUT/app-debug.apk" \
    "$BUILD/aligned.apk" \
    2>&1

echo ""
echo "==> Build successful!"
echo "==> APK: $OUT/app-debug.apk"
ls -lh "$OUT/app-debug.apk"
