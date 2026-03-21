# SMS Gateway App

An Android application for sending and receiving SMS messages programmatically. Built as part of the Sunfeld infrastructure to provide SMS gateway capabilities from an Android device.

## Project Scope

- Send SMS messages via `SmsManager` API
- Track delivery status with `PendingIntent` callbacks (SMS_SENT / SMS_DELIVERED)
- Runtime permission handling for SMS, phone state, and location
- Minimal Material Design UI with recipient input, message field, and send button
- Phone number validation and error handling
- Remote gateway installation via Mission Control API integration
- ViewModel-based architecture with LiveData state management

## Architecture

### Gateway Installation Flow

The app integrates with Mission Control's backend API to install SMS gateway instances on projects:

```
ProjectDetailScreen → GatewayInstallButton → ProjectViewModel → POST /api/projects/{id}/install-gateway
```

**Key components:**

| Component | Role |
|---|---|
| `ProjectViewModel` | Manages installation state via `LiveData<InstallResult>`. Uses coroutine-dispatched OkHttp calls to `POST /api/projects/{id}/install-gateway`. |
| `InstallResult` | Sealed class with states: `Idle`, `Installing`, `Success`, `Error(message)` |
| `ProjectDetailActivity` | Observes `installState` LiveData and updates `GatewayInstallButton` state + visibility accordingly |
| `GatewayInstallButton` | Custom Material 3 button with `IDLE`, `INSTALLING`, `ERROR` visual states |

**State transitions:**

1. `Idle` — Button visible, ready to tap
2. `Installing` — Button shows loading indicator, API call in progress
3. `Success` — Button hidden, gateway status text updated to "Installed"
4. `Error` — Button shows error state, toast displays error message

### Dependencies

| Library | Version | Purpose |
|---|---|---|
| `lifecycle-viewmodel-ktx` | 2.7.0 | ViewModel with coroutine scope |
| `lifecycle-livedata-ktx` | 2.7.0 | Observable state management |
| `kotlinx-coroutines-android` | 1.7.3 | Async API calls on IO dispatcher |
| `okhttp` | 4.12.0 | HTTP client for gateway API |

## Tech Stack

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Build System**: Gradle (Kotlin DSL)
- **Package**: `com.sunfeld.smsgateway`

## Project Structure

```
sms-gateway-app/
├── app/
│   ├── build.gradle.kts          # App-level dependencies
│   └── src/main/
│       ├── kotlin/               # Kotlin source files
│       ├── res/                   # Layouts, drawables, values
│       └── AndroidManifest.xml   # Permissions and activity declarations
├── gradle/
│   └── wrapper/                  # Gradle wrapper
├── build.gradle.kts              # Project-level plugins
├── settings.gradle.kts           # Module configuration
└── gradle.properties             # JVM and AndroidX settings
```

## Required Permissions

| Permission | Purpose |
|---|---|
| `SEND_SMS` | Send SMS messages |
| `READ_SMS` | Read incoming messages |
| `RECEIVE_SMS` | Listen for incoming SMS |
| `READ_PHONE_STATE` | Access device telephony info |
| `ACCESS_FINE_LOCATION` | Location-aware features |

## Build Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Build Debug APK

```bash
./gradlew assembleDebug
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

### Build Release APK

```bash
./gradlew assembleRelease
```

### Run Tests

```bash
./gradlew test
```

## Installation

There are several ways to get the APK onto your Android device. All methods require **Developer Mode** enabled on the device.

### Prerequisites (Device Setup)

1. **Enable Developer Options**: Go to `Settings → About Phone` and tap **Build Number** 7 times
2. **Enable USB Debugging**: Go to `Settings → Developer Options → USB Debugging` and toggle it on
3. **Allow Unknown Sources**: Go to `Settings → Apps → Special app access → Install unknown apps` and allow your file manager or browser

### Method 1: ADB USB Install

The simplest method when the device is physically connected via USB cable.

```bash
# Build and install in one step
./gradlew installDebug

# Or use the build script first, then install manually
./build.sh debug
adb install app/build/outputs/apk/debug/app-debug.apk
```

To install a release build:

```bash
./build.sh release
adb install app/build/outputs/apk/release/app-release.apk
```

**Troubleshooting**: If `adb devices` shows no devices, check that:
- USB debugging is enabled on the device
- You've accepted the "Allow USB debugging?" prompt on the device
- The USB cable supports data transfer (not charge-only)

### Method 2: ADB Wireless (Wi-Fi)

Install over Wi-Fi without a USB cable. The device and build machine must be on the same network.

#### Android 11+ (Native Wireless Debugging)

1. On the device: `Settings → Developer Options → Wireless debugging` → toggle on
2. Tap **Pair device with pairing code** — note the IP:port and pairing code
3. On the build machine:

```bash
# Pair (one-time setup)
adb pair <IP>:<PAIRING_PORT>
# Enter the pairing code when prompted

# Connect
adb connect <IP>:<PORT>
# Use the port shown under "Wireless debugging" (NOT the pairing port)

# Verify connection
adb devices

# Install
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### Android 10 and below (TCP/IP method)

Requires an initial USB connection to enable TCP mode:

```bash
# With USB connected, switch ADB to TCP mode
adb tcpip 5555

# Disconnect USB cable, then connect over Wi-Fi
adb connect <DEVICE_IP>:5555

# Install
adb install app/build/outputs/apk/debug/app-debug.apk
```

Find the device IP at `Settings → Wi-Fi → [network name] → IP address`.

### Method 3: Direct APK Sideloading

Transfer the APK file to the device and install it manually — no ADB required on the receiving end.

#### Via USB file transfer

1. Build the APK: `./build.sh debug`
2. Connect the device via USB and select **File Transfer (MTP)** mode
3. Copy `app/build/outputs/apk/debug/app-debug.apk` to the device (e.g., `Downloads/`)
4. On the device, open a file manager and tap the APK to install

#### Via cloud/messaging

1. Build the APK: `./build.sh debug`
2. Upload `app/build/outputs/apk/debug/app-debug.apk` to Google Drive, Dropbox, Telegram, or email it
3. Open the link/attachment on the device and install

### Method 4: QR Code Transfer

Serve the APK over HTTP on your local network and scan a QR code from the device to download it.

```bash
# Build the APK
./build.sh debug

# Start a temporary HTTP server in the APK output directory
cd app/build/outputs/apk/debug/
python3 -m http.server 8080 &
HTTP_PID=$!

# Get your machine's local IP
LOCAL_IP=$(hostname -I | awk '{print $1}')
echo "Download URL: http://$LOCAL_IP:8080/app-debug.apk"

# Generate a QR code (install qrencode if needed: apt install qrencode)
qrencode -t UTF8 "http://$LOCAL_IP:8080/app-debug.apk"

# Scan the QR code with the device camera, download the APK, and install
# When done, stop the server:
kill $HTTP_PID
```

If `qrencode` is not available, paste the URL into any online QR code generator.

**Note**: The device must be on the same Wi-Fi network as the build machine.

### Verifying the Installation

After installing via any method:

1. Open **SMS Gateway** from the app drawer
2. Grant all requested permissions (SMS, Phone, Location)
3. The app is ready — enter a phone number and message to test

### Build Variants

| Variant | Command | APK Path | Signed |
|---|---|---|---|
| Debug | `./build.sh debug` | `app/build/outputs/apk/debug/app-debug.apk` | Debug key (auto) |
| Release | `./build.sh release` | `app/build/outputs/apk/release/app-release.apk` | Release keystore |

The **debug** APK works for sideloading and personal use. The **release** APK is signed with the project keystore (configured in `keystore.properties`) and suitable for distribution.
