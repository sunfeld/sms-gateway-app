#!/usr/bin/env bash
# SMS Gateway App - Build Script
# Sets up JAVA_HOME and builds the APK

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Auto-detect JAVA_HOME if not set
if [ -z "${JAVA_HOME:-}" ]; then
    if [ -d "/home/linuxbrew/.linuxbrew/Cellar/openjdk@17/17.0.18/libexec" ]; then
        export JAVA_HOME="/home/linuxbrew/.linuxbrew/Cellar/openjdk@17/17.0.18/libexec"
    elif command -v javac &>/dev/null; then
        export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
    else
        echo "ERROR: JAVA_HOME not set and no JDK found. Install JDK 17+."
        exit 1
    fi
fi

export PATH="$JAVA_HOME/bin:$PATH"

echo "Using JAVA_HOME=$JAVA_HOME"
echo "Java version: $(java -version 2>&1 | head -1)"
echo ""

BUILD_TYPE="${1:-debug}"

case "$BUILD_TYPE" in
    debug)
        echo "Building debug APK..."
        ./gradlew assembleDebug
        APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
        ;;
    release)
        echo "Building release APK..."
        ./gradlew assembleRelease
        APK_PATH="app/build/outputs/apk/release/app-release.apk"
        ;;
    *)
        echo "Usage: $0 [debug|release]"
        exit 1
        ;;
esac

if [ -f "$APK_PATH" ]; then
    SIZE=$(du -h "$APK_PATH" | cut -f1)
    echo ""
    echo "BUILD SUCCESSFUL"
    echo "APK: $APK_PATH ($SIZE)"
else
    echo "ERROR: APK not found at $APK_PATH"
    exit 1
fi
