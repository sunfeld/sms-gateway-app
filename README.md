# SMS Gateway App

[![Download Latest APK](https://img.shields.io/github/v/release/rrrrekt/sms-gateway-app?label=Download%20Latest%20APK&logo=android&style=for-the-badge)](https://github.com/rrrrekt/sms-gateway-app/releases/latest)

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

### Async Orchestration Loop (Planned)

The `attack_orchestrator.py` module will implement an asynchronous event loop to manage concurrent Bluetooth pairing requests across multiple target devices.

**Architecture:**

```
TargetScanner (bleak)
    → target_list: List[BluetoothTarget]
        → attack_orchestrator.run_loop()
            → asyncio.gather(*[send_pairing_request(t) for t in targets])
            → cycle back with configurable interval
```

**Key design decisions:**

| Aspect | Approach |
|---|---|
| Concurrency model | `asyncio` event loop with `gather()` for parallel dispatch |
| Target iteration | Round-robin cycling through the target list with configurable batch size |
| Rate control | Configurable interval between cycles (default: 100ms) |
| Error handling | Per-target error isolation — one failed target does not block others |
| Lifecycle | Start/stop via `asyncio.Event` flag; graceful shutdown cancels pending tasks |

**Planned flow:**

1. `TargetScanner` populates the target list with discovered device addresses
2. `run_loop()` starts an infinite `while` loop gated by an `asyncio.Event`
3. Each iteration dispatches `send_pairing_request()` concurrently for all targets via `asyncio.gather()`
4. Failed requests are logged per-target; successful requests increment a counter
5. Loop sleeps for the configured interval before the next cycle
6. `stop()` clears the event flag, cancels pending gather, and awaits cleanup

**Integration point:** The FastAPI endpoint `POST /api/bluetooth/dos/start` will invoke `run_loop()` in a background task, returning a job ID for status polling.

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

## SDP Record Configuration

The `BluetoothKeyboardProfile` in `bluetooth/bluetooth_keyboard_profile.py` configures an SDP (Service Discovery Protocol) record that broadcasts the device as a Peripheral/Keyboard (device class `0x000540`). This record is registered with BlueZ via the `ProfileManager1` D-Bus interface when the profile starts.

### SDP Record Structure

The SDP record is an XML document containing 16 attributes that define the HID keyboard service:

| Attribute ID | Purpose | Value |
|---|---|---|
| `0x0001` | Service Class ID List | UUID `0x1124` (HID) |
| `0x0004` | Protocol Descriptor List | L2CAP (`0x0100`, PSM `0x0011`) + HIDP (`0x0011`) |
| `0x0005` | Browse Group List | Public Browse Root (`0x1002`) |
| `0x0006` | Language Base Attribute ID | English (`0x656e`), UTF-8 (`0x006a`) |
| `0x0009` | Profile Descriptor List | HID v1.0 (`0x1124`, `0x0100`) |
| `0x000d` | Additional Protocol Descriptors | L2CAP interrupt channel (`0x0013`) + HIDP |
| `0x0100` | Service Name | "Bluetooth HID Keyboard" |
| `0x0101` | Service Description | "Keyboard" |
| `0x0102` | Provider Name | "SMS Gateway HID Keyboard Profile" |
| `0x0200` | HID Device Release Number | `0x0100` |
| `0x0201` | HID Parser Version | `0x0111` |
| `0x0202` | HID Device Subclass | `0x40` (Keyboard) |
| `0x0204` | HID Virtual Cable | `true` |
| `0x0205` | HID Reconnect Initiate | `true` |
| `0x0206` | HID Descriptor List | Boot keyboard HID report descriptor |
| `0x020e` | HID Boot Device | `true` |

### Profile Registration Flow

```
BluetoothManager.init()
  → Acquire D-Bus SystemBus
  → Get BlueZ ProfileManager1 interface
  → BluetoothKeyboardProfile(bus)
    → Export D-Bus object at /org/bluez/hid_keyboard_profile
  → profile.register(manager)
    → ProfileManager1.RegisterProfile(path, UUID, options)
      → options.ServiceRecord = SDP_RECORD_XML
      → options.Role = "server"
      → options.AutoConnect = true
```

When registered, the SDP record makes the device discoverable as a HID keyboard to nearby Bluetooth hosts. Incoming connections trigger the `NewConnection` D-Bus method, which receives the L2CAP file descriptor for HID data exchange.

## Just Works Pairing Security Parameters

The `bluetooth_manager.py` module implements "Just Works" Secure Simple Pairing (SSP) to force immediate pairing pop-ups on target devices without requiring numeric comparison or passkey entry.

### How It Works

Just Works uses the `NoInputNoOutput` IO capability to select the SSP association model that requires no user interaction on the initiating side. When a pairing request is sent, the target device (iOS, Android, etc.) shows an immediate simple pairing pop-up.

### JustWorksAgent

The `JustWorksAgent` is a D-Bus service object implementing the `org.bluez.Agent1` interface:

| Method | Behavior |
|---|---|
| `RequestPinCode` | Returns empty string (SSP does not use PIN codes) |
| `RequestPasskey` | Returns `0` (auto-accept) |
| `RequestConfirmation` | Auto-accepts numeric confirmation silently |
| `RequestAuthorization` | Auto-authorizes the device |
| `AuthorizeService` | Auto-authorizes service access (e.g., HID profile) |
| `DisplayPasskey` | No-op (NoInputNoOutput has no display) |
| `DisplayPinCode` | No-op |
| `Cancel` | Logs cancellation |
| `Release` | Logs release |

### Configuration Flow

```
BluetoothManager.configure_just_works_pairing()
  → ensure_ready() (power on, discoverable, pairable)
  → JustWorksAgent(bus, path)
  → AgentManager1.RegisterAgent(path, "NoInputNoOutput")
  → AgentManager1.RequestDefaultAgent(path)
```

### Constants

| Constant | Value | Purpose |
|---|---|---|
| `AGENT_CAPABILITY_NO_INPUT_NO_OUTPUT` | `"NoInputNoOutput"` | IO capability forcing Just Works SSP |
| `AGENT_PATH_JUST_WORKS` | `"/org/bluez/agent_just_works"` | D-Bus object path for the agent |
| `JUST_WORKS_DEFAULT_PASSKEY` | `0` | Default passkey returned if unexpectedly requested |

## Bluetooth Stress Test API

The app integrates with a FastAPI backend that orchestrates Bluetooth stress testing operations. The backend coordinates multiple Bluetooth subsystems (advertising, scanning, pairing) through a single control endpoint.

### Endpoint: `POST /api/bluetooth/dos/start`

Triggers the Bluetooth stress test orchestrator with configurable parameters.

**Request body:**

```json
{
  "duration": 60,
  "intensity": 3
}
```

| Parameter | Type | Required | Description |
|---|---|---|---|
| `duration` | `int` | Yes | Test duration in seconds. Controls how long the orchestrator cycles through targets. |
| `intensity` | `int` | Yes | Intensity level (1–5). Determines concurrent connection attempts and advertising interval frequency. |

**Intensity levels:**

| Level | Advertising Interval | Concurrent Connections | Use Case |
|---|---|---|---|
| 1 | 100ms | 1 | Baseline — single device, low frequency |
| 2 | 50ms | 2 | Light scan — moderate pairing rate |
| 3 | 30ms | 4 | Standard test — balanced throughput |
| 4 | 20ms | 8 | High load — rapid cycling with MAC rotation |
| 5 | 20ms | 16 | Maximum — full saturation with all subsystems active |

**Response (200 OK):**

```json
{
  "status": "started",
  "session_id": "bt-sess-1711036800",
  "duration": 60,
  "intensity": 3,
  "targets_discovered": 5
}
```

**Response (409 Conflict):**

```json
{
  "status": "already_running",
  "session_id": "bt-sess-1711036700",
  "remaining_seconds": 34
}
```

### Architecture

```
Frontend Dashboard → POST /api/bluetooth/dos/start
                          ↓
                   attack_orchestrator.py
                     ├── TargetScanner (bleak)
                     ├── AdvertisingPayload (MAC rotation + device name spoofing)
                     ├── start_rapid_advertising() (hcitool/mgmt API)
                     └── trigger_pairing_request() (HID connection attempts)
```

**Component flow:**

1. The FastAPI endpoint validates `duration` and `intensity` parameters
2. `attack_orchestrator.py` initializes an async event loop
3. `TargetScanner` discovers nearby devices via BLE scan (filters Apple manufacturer data `0x004c` for iOS targets)
4. `AdvertisingPayload` generates randomized MAC addresses and rotates device names (e.g., "Apple Magic Keyboard", "Logitech K380")
5. `start_rapid_advertising()` begins high-frequency BLE advertisements at the configured interval
6. `trigger_pairing_request()` initiates outbound HID connection attempts using "Just Works" SSP to force pairing pop-ups
7. The orchestrator cycles through the target list for the specified duration, then cleanly shuts down

### Dependencies (Backend)

| Library | Purpose |
|---|---|
| `fastapi` | HTTP endpoint framework |
| `dbus-python` | D-Bus interface to BlueZ Bluetooth stack |
| `bleak` | BLE scanning and device discovery |
| `bluez` | System Bluetooth protocol stack |
| `hcitool` | Low-level HCI advertising control |

## Bluetooth Stress Test Dashboard Toggle

The frontend dashboard includes a dedicated control panel for managing Bluetooth stress test sessions. The toggle provides a single-click start/stop interface with real-time telemetry counters.

### UI Components

| Component | Description |
|---|---|
| **Stress Test Toggle** | Material switch that triggers `POST /api/bluetooth/dos/start` on activation and cancels the running session on deactivation |
| **Packets Sent Counter** | Real-time numeric display showing total BLE advertisement + pairing packets dispatched since session start |
| **Devices Targeted Counter** | Live count of unique Bluetooth addresses discovered and actively targeted by the orchestrator |
| **Session Timer** | Countdown showing remaining duration based on the `duration` parameter passed at session start |

### Data Flow

```
Toggle ON
  → POST /api/bluetooth/dos/start { duration, intensity }
  → Receive session_id
  → Poll GET /api/bluetooth/dos/status/{session_id} every 1s
      → Update "Packets Sent" from response.packets_sent
      → Update "Devices Targeted" from response.targets_active
      → Update timer from response.remaining_seconds
  → When remaining_seconds == 0 or toggle OFF
      → POST /api/bluetooth/dos/stop/{session_id}
      → Reset counters, toggle returns to OFF state
```

### Real-Time Counter Updates

The dashboard polls the backend status endpoint at 1-second intervals while a session is active. Each poll response contains:

```json
{
  "session_id": "bt-sess-1711036800",
  "status": "running",
  "packets_sent": 14832,
  "targets_active": 5,
  "remaining_seconds": 34,
  "intensity": 3
}
```

| Counter | Source Field | Update Frequency |
|---|---|---|
| Packets Sent | `packets_sent` | Every 1s poll |
| Devices Targeted | `targets_active` | Every 1s poll |
| Time Remaining | `remaining_seconds` | Every 1s poll |

### Toggle States

| State | Toggle Position | Counters | Action |
|---|---|---|---|
| Idle | OFF | Hidden or zeroed | No active session |
| Starting | OFF → ON (transitioning) | Loading spinner | `POST /start` in flight |
| Running | ON | Live updating | Polling status endpoint |
| Stopping | ON → OFF (transitioning) | Frozen at last values | `POST /stop` in flight |
| Error | OFF (auto-reset) | Last known values | Toast with error message, auto-retry available |
| Conflict | OFF (blocked) | Shows existing session info | Another session already running (409 response) |
