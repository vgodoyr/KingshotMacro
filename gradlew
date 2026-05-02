#!/usr/bin/env bash
# Gradle wrapper – delegates to build.sh for offline Android builds
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [[ "${1:-}" == "assembleDebug" || "${1:-}" == ":app:assembleDebug" ]]; then
    exec "$SCRIPT_DIR/build.sh"
fi

# Fall back to system Gradle for other tasks
exec gradle "$@"
