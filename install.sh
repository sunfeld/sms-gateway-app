#!/usr/bin/env bash
# SMS Gateway App - Build & Install Script
# Builds the APK and installs it on a connected Android device via ADB

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ANDROID_SDK="${ANDROID_HOME:-/home/sunai/Android/Sdk}"
ADB="$ANDROID_SDK/platform-tools/adb"
PACKAGE="com.sunfeld.smsgateway"
BUILD_TYPE="${1:-debug}"

# --- Helpers ---

die() { echo "ERROR: $1" >&2; exit 1; }

check_adb() {
    if [ ! -x "$ADB" ]; then
        die "ADB not found at $ADB. Set ANDROID_HOME or install platform-tools."
    fi
}

# Returns list of connected device serials (one per line)
get_devices() {
    "$ADB" devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1}'
}

select_device() {
    local devices
    devices=$(get_devices)
    local count
    count=$(echo "$devices" | grep -c . || true)

    if [ "$count" -eq 0 ]; then
        echo ""
        echo "No devices detected. To connect:"
        echo "  USB:      Enable USB Debugging on device and connect via USB"
        echo "  Wireless: adb pair <ip>:<port> then adb connect <ip>:<port>"
        echo ""
        die "No ADB devices found. Connect a device and retry."
    elif [ "$count" -eq 1 ]; then
        DEVICE="$devices"
        echo "Device found: $DEVICE"
    else
        echo "Multiple devices detected:"
        local i=1
        while IFS= read -r serial; do
            local model
            model=$("$ADB" -s "$serial" shell getprop ro.product.model 2>/dev/null || echo "unknown")
            echo "  [$i] $serial ($model)"
            i=$((i + 1))
        done <<< "$devices"
        echo ""
        read -rp "Select device [1-$count]: " choice
        if ! [[ "$choice" =~ ^[0-9]+$ ]] || [ "$choice" -lt 1 ] || [ "$choice" -gt "$count" ]; then
            die "Invalid selection: $choice"
        fi
        DEVICE=$(echo "$devices" | sed -n "${choice}p")
        echo "Selected: $DEVICE"
    fi
}

# --- Main ---

echo "=== SMS Gateway - Build & Install ==="
echo ""

# Check ADB is available
check_adb
echo "ADB: $ADB"

# Detect and select device
select_device
echo ""

# Build the APK
echo "--- Building $BUILD_TYPE APK ---"
bash "$SCRIPT_DIR/build.sh" "$BUILD_TYPE"
echo ""

# Determine APK path
case "$BUILD_TYPE" in
    debug)   APK_PATH="app/build/outputs/apk/debug/app-debug.apk" ;;
    release) APK_PATH="app/build/outputs/apk/release/app-release.apk" ;;
    *)       die "Unknown build type: $BUILD_TYPE" ;;
esac

if [ ! -f "$APK_PATH" ]; then
    die "APK not found at $APK_PATH — build may have failed."
fi

# Install the APK
echo "--- Installing on $DEVICE ---"
"$ADB" -s "$DEVICE" install -r "$APK_PATH"
echo ""

echo "INSTALL SUCCESSFUL"
echo "Package: $PACKAGE"
echo "Device:  $DEVICE"
echo "APK:     $APK_PATH"
echo ""
echo "To launch: $ADB -s $DEVICE shell am start -n $PACKAGE/.MainActivity"
