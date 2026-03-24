# SMS Gateway App - Tasks

## Phase 1: Project Setup & Core Implementation
- [x] Create directory structure at `/ws/sms-gateway-app` including `app/src/main/kotlin` and `gradle/wrapper`.
- [x] Generate `build.gradle.kts` (project level) and `app/build.gradle.kts` with Kotlin and Android dependencies.
- [x] Create `settings.gradle.kts` and `gradle.properties` for project configuration.
- [x] Generate `.gitignore` for Android/IntelliJ and `README.md` with project scope and build instructions.
- [x] Configure `AndroidManifest.xml` with package name and `MainActivity` declaration.
- [x] Add `<uses-permission>` tags for `SEND_SMS`, `READ_SMS`, `RECEIVE_SMS`, `READ_PHONE_STATE`, and `ACCESS_FINE_LOCATION`.
- [x] Add `<uses-feature>` tags for `android.hardware.telephony` to ensure hardware compatibility.
- [x] Create `SmsService.kt` utility class to encapsulate `SmsManager` logic.
- [x] Implement `sendDirectSms(phoneNumber: String, message: String)` using `SmsManager.sendTextMessage`.
- [x] Implement `PendingIntent` callbacks in `SmsService` to track `SMS_SENT` and `SMS_DELIVERED` statuses.
- [x] Add error handling logic to catch `SecurityException` or invalid phone number formats.
- [x] Create `activity_main.xml` layout with `EditText` for recipient, `EditText` for message, and a `Button` for sending.
- [x] Implement `PermissionManager.kt` helper to handle the `ActivityResultLauncher` for multiple runtime permissions.
- [x] Update `MainActivity.kt` to request permissions on `onCreate` and toggle the "Send" button state based on grant results.
- [x] Bind UI elements in `MainActivity` to call `SmsService.sendDirectSms` and display `Toast` notifications for success/failure.
- [x] Create a basic unit test `SmsValidationTest.kt` to verify phone number formatting logic.
- [x] Run `./gradlew assembleDebug` to verify build integrity and APK generation.

- [x] Assess the State Of The sms gateway and make a plan for How We Can get this app into an installable State

## Assessment: Current State (2026-03-13)

### What Works
- **Build**: `./gradlew assembleDebug` succeeds (5.5MB APK generated at `app/build/outputs/apk/debug/app-debug.apk`)
- **Tests**: All unit tests pass (`./gradlew test`)
- **Code quality**: Clean Kotlin, proper sealed class error handling, Material 3 UI, PendingIntent callbacks
- **SDK**: Android SDK present at `/home/sunai/Android/Sdk` with platform 34, build-tools 34.0.0
- **JDK**: OpenJDK 17 available at `/home/linuxbrew/.linuxbrew/Cellar/openjdk@17/17.0.18/libexec`

### Issues Blocking "Installable" State
1. **No JAVA_HOME set** — build requires manually exporting `JAVA_HOME` each time
2. **No `proguard-rules.pro`** — referenced in `build.gradle.kts` but missing (blocks clean release builds)
3. **No release signing keystore** — only debug APK can be built; no signed release for distribution
4. **No install/build script** — no convenience tooling for building + transferring APK to device
5. **No device connected** — ADB shows no devices; need USB debugging or wireless ADB setup
6. **Debug APK is already sideload-ready** — the unsigned debug APK can be installed on any device with developer mode

### Verdict
The app is **functionally complete** for its MVP scope. The debug APK is installable via sideloading right now. To make it properly distributable, we need signing and convenience tooling.

## Phase 2: Make Installable (Plan)
- [x] Add `proguard-rules.pro` stub file (referenced by build config but missing)
- [x] Create `build.sh` script that sets JAVA_HOME and runs `./gradlew assembleDebug`
- [x] Generate a release signing keystore and configure `signingConfigs` in `app/build.gradle.kts`
- [x] Create `install.sh` script that builds the APK and installs via ADB (with device detection)
- [x] Add `INTERNET` permission to AndroidManifest if gateway features will need network access
- [x] Document the full install process in README.md (sideloading, ADB wireless, QR code transfer)


## Plan: ### **Objective: Resolve SMS Gateway Installation Failure an
- [x] : Update `ProjectController` to include `sms_gateway_available` boolean in the project detail response payload.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] : Create `POST /api/projects/{id}/install-gateway` endpoint to trigger the deployment pipeline for the SMS Gateway.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] : Create `GatewayInstallButton.kt` component with states for `IDLE`, `INSTALLING`, and `ERROR` using Material 3 Button styles.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] : Integrate `GatewayInstallButton` into the `ProjectDetailScreen` layout, ensuring visibility when `sms_gateway_installed` is false.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] : Implement `ProjectViewModel` logic to handle the installation API call and update the local UI state upon success or failure.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for ": Implement `ProjectViewModel` logic to handle the installation API call and ..."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] : Add a `BroadcastReceiver` or polling mechanism to refresh the project status once the backend confirms the gateway is "ACTIVE".
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced


## API Integration and UI
- [x] Install `dbus-python` and `bluez` dependencies and create `bluetooth_manager.py` to interface with system Bluetooth adapter.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for "Install `dbus-python` and `bluez` dependencies and create `bluetooth_manager...."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Implement `BluetoothKeyboardProfile` class inheriting from BlueZ Profile1 to define HID keyboard service UUID (0x1124).
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for "Implement `BluetoothKeyboardProfile` class inheriting from BlueZ Profile1 to ..."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Configure SDP (Service Discovery Protocol) record in `bluetooth_manager.py` to broadcast device class as a Peripheral/Keyboard (0x000540).
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for "Configure SDP (Service Discovery Protocol) record in `bluetooth_manager.py` t..."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Create `AdvertisingPayload` generator to randomize MAC addresses and rotate device names (e.g., "Apple Magic Keyboard", "Logitech K380").
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for "Create `AdvertisingPayload` generator to randomize MAC addresses and rotate d..."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Implement `start_rapid_advertising()` function using `hcitool` or `mgmt` API to set high-frequency advertisement intervals (min/max 20ms).
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for "Implement `start_rapid_advertising()` function using `hcitool` or `mgmt` API ..."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Build a `TargetScanner` module using `bleak` to identify nearby iOS devices via Apple-specific manufacturer data (ID 0x004c).
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for "Build a `TargetScanner` module using `bleak` to identify nearby iOS devices v..."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Implement `trigger_pairing_request()` to initiate outbound HID connection attempts to discovered peer addresses.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for "Implement `trigger_pairing_request()` to initiate outbound HID connection att..."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Create an asynchronous loop in `attack_orchestrator.py` to cycle through the target list and send concurrent "Pairing Request" packets.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for "Create an asynchronous loop in `attack_orchestrator.py` to cycle through the ..."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Add "Just Works" pairing security parameters to `bluetooth_manager.py` to force immediate pop-up prompts on target devices.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for "Add "Just Works" pairing security parameters to `bluetooth_manager.py` to for..."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Create a FastAPI endpoint `POST /api/bluetooth/dos/start` that accepts duration and intensity parameters to trigger the orchestrator.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for "Create a FastAPI endpoint `POST /api/bluetooth/dos/start` that accepts durati..."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Add a "Bluetooth Stress Test" toggle to the frontend dashboard with a real-time counter of "Packets Sent" and "Devices Targeted".
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for "Add a "Bluetooth Stress Test" toggle to the frontend dashboard with a real-ti..."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced


## Plan: It says cleartext communication is not permitted to 10.0.0.2
- [x] : Create `res/xml/network_security_config.xml` to permit cleartext traffic for `10.0.0.2` and add `android:networkSecurityConfig` to the `<application>` tag in `AndroidManifest.xml`.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] : Define a `Config.kt` object containing `BASE_URL = "http://10.0.0.2:8080"` (or the verified public endpoint) to centralize API targeting.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] : Implement `RetrofitClient.kt` using OkHttp and Retrofit with a `GatewayApiService` interface defining `POST /api/projects/{id}/install-gateway`.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] : Create the `InstallResult` sealed class with `Idle`, `Installing`, `Success`, and `Error(message)` states to manage the UI lifecycle.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for ": Create the `InstallResult` sealed class with `Idle`, `Installing`, `Success..."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] : Implement `ProjectViewModel.kt` using `viewModelScope` to execute the gateway installation request and expose state via `MutableStateFlow`.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] : Update `MainActivity.kt` (or `ProjectDetailActivity`) to observe the installation state, updating a `MaterialButton` to show a loading spinner or success/error toasts.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced

---

## Phase 38: On-Device Bluetooth HID Keyboard Impersonation

**Goal:** Remove all API calls from Bluetooth feature. The phone scans for nearby BT devices,
connects to each as a Bluetooth HID keyboard (using Android BluetoothHidDevice API),
and sends keystrokes — entirely on-device, no server needed.

**Acceptance test:** Toggle activates BT scan → discovered devices appear in list →
phone connects to each as keyboard → keystroke counter increments — all without any HTTP call.

### 38.1 - Manifest & Build Config
- [x] 38.1.1 Raise `minSdk` to 28 in `app/build.gradle.kts` (BluetoothHidDevice requires API 28); add `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` permissions to `AndroidManifest.xml`; add `uses-feature android.hardware.bluetooth`
  - **Test:** `grep -c "BLUETOOTH" app/src/main/AndroidManifest.xml` returns ≥4

### 38.2 - HID Report Descriptor
- [x] 38.2.1 Create `HidKeyReport.kt` with standard USB HID boot keyboard descriptor (8-byte report: modifier byte + reserved + 6 keycode slots), `buildKeyPress(char)` returning 8-byte array, `KEY_RELEASE` constant (all zeros), ASCII→HID keycode map covering a-z, A-Z, 0-9, space, enter, backspace
  - **Test:** `HidKeyReport.buildKeyPress('A')` returns `[0x02, 0x00, 0x04, 0, 0, 0, 0, 0]`; `buildKeyPress('a')` returns `[0x00, 0x00, 0x04, 0, 0, 0, 0, 0]`

### 38.3 - Bluetooth Scanner
- [x] 38.3.1 Create `BluetoothScanner.kt` registering `BroadcastReceiver` for `ACTION_FOUND` + `ACTION_DISCOVERY_FINISHED`; exposes `fun startScan(context)`, `fun stopScan(context)`, `val devices: StateFlow<List<BluetoothDevice>>`; auto-restarts scan when finished while active
  - **Test:** Broadcasting mock `ACTION_FOUND` intent causes `devices` StateFlow to emit updated list

### 38.4 - Bluetooth HID Manager
- [x] 38.4.1 Create `BluetoothHidManager.kt` that: (1) gets `BluetoothHidDevice` proxy via `getProfileProxy`, (2) registers keyboard app via `registerApp()` with `BluetoothHidDeviceAppSdpSettings` + standard descriptor, (3) implements `connect(device: BluetoothDevice)`, (4) implements `sendText(device, text: String)` cycling through chars with 30ms press+release delay, (5) implements `disconnectAll()`; exposes `connectedDevices: StateFlow<Set<BluetoothDevice>>`
  - **Test:** `BluetoothHidManager` instantiates without crash; `sendText` builds correct byte sequence for "Hi"

### 38.5 - ViewModel Redesign
- [x] 38.5.1 Rewrite `BluetoothStressTestViewModel.kt`: replace `GatewayApiClient` with `BluetoothScanner` + `BluetoothHidManager`; state machine: `Idle → Scanning → Attacking → Idle`; expose `discoveredDevices: LiveData<List<BluetoothDevice>>`, `connectedCount: LiveData<Int>`, `keystrokesSent: LiveData<Int>`; on start → begin scan → for each found device call `hidManager.connect(device)` → on connect callback → loop `sendText` every 5s
  - **Test:** Mock BluetoothHidManager injected into VM; `startAttack()` call transitions state to Scanning

### 38.6 - UI Update
- [x] 38.6.1 Update `activity_bluetooth_stress_test.xml`: rename "Packets Sent" counter to "Keystrokes Sent", "Devices Targeted" to "Connected"; add `RecyclerView` below counter card showing discovered device list (name + address + status chip); update `strings.xml` with new labels
  - **Test:** Activity inflates without crash; switch toggle calls `viewModel.startAttack()`

### 38.7 - Remove Dead API Methods
- [x] 38.7.1 Remove `startBluetoothDos`, `getBluetoothDosStatus`, `stopBluetoothDos` methods from `GatewayApiClient.kt`; remove corresponding data classes `BluetoothDosStartResult`, `BluetoothDosStatus`, `BluetoothDosStopResult`; remove all BT API references from `BluetoothStressTestViewModel.kt`
  - **Test:** `grep -r "BluetoothDos" app/src/main` returns no results


## CI/CD and Release Automation
- [x] Create a GitHub Actions workflow in `.github/workflows/release.yml` triggered on tag creation to automate the build process.
  - **Verification Tests**:
    - [x] Workflow YAML validated: correct indentation, tag trigger on `v*`, JDK 17 setup, Gradle build, keystore from secrets, APK upload + GitHub Release
    - [x] No automated tests needed (workflow file, not application code)
    - [x] No regressions introduced
- [x] Configure the release workflow to use `actions/setup-java` and `subosito/flutter-action` to compile the Flutter app into a release APK.
  - **Verification Tests**:
    - [x] Workflow YAML validated: `actions/setup-java@v4` with JDK 17/temurin/gradle-cache present; `gradle/actions/wrapper-validation@v4` added; `subosito/flutter-action` skipped (native Android/Kotlin project, not Flutter — `gradlew assembleRelease` is the correct build command)
    - [x] No automated tests needed (workflow file, not application code)
    - [x] No regressions: existing workflow steps (checkout, keystore, build, upload, release) unchanged
- [x] Add a build step to sign the APK using `jarsigner` or `flutter build apk --release` with secrets for the keystore and alias.
  - **Verification Tests**:
    - [x] Workflow YAML validated: secrets validation step fails fast if `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, or `KEY_PASSWORD` missing; keystore decoded with size check; Gradle `assembleRelease` uses `signingConfigs.release`; `apksigner verify` (or `jarsigner -verify` fallback) confirms APK is signed
    - [x] No automated tests needed (workflow file, not application code)
    - [x] No regressions: existing build, upload, and release steps unchanged
- [x] Integrate `softprops/action-gh-release` in the workflow to upload the generated `app-release.apk` as a binary asset to the GitHub Release.
  - **Verification Tests**:
    - [x] Workflow YAML validated: `softprops/action-gh-release@v2` present with `files: app/build/outputs/apk/release/app-release.apk` and `generate_release_notes: true`; `permissions: contents: write` set at workflow level for release creation
    - [x] No automated tests needed (workflow file, not application code)
    - [x] No regressions: existing build, sign, verify, and upload-artifact steps unchanged
- [x] Update the `README.md` to include a dynamic "Download Latest APK" badge linking to `https://github.com/{owner}/{repo}/releases/latest`.
  - **Verification Tests**:
    - [x] Shields.io badge added to README.md with GitHub release version, Android logo, and link to `https://github.com/sunfeld/sms-gateway-app/releases/latest`
    - [x] No automated tests needed (README content change, not application code)
    - [x] No regressions: existing README content unchanged, badge added at top after title

---

## Phase 39: Bluetooth HID Impersonation Overhaul

**Goal:** Complete standalone Bluetooth HID keyboard impersonation tool with device profile selection,
custom naming, multi-target selection, preset storage, and big START/STOP button. Fully on-device, no internet.

### 39.A - Cleanup & GitHub Setup
- [x] 39.A.1 Delete dead `bluetooth/` Python directory (22 files replaced by Kotlin in Phase 38)
- [x] 39.A.2 Add `__pycache__/` and `.pytest_cache/` to `.gitignore`
- [x] 39.A.3 Update README badge URL from `rrrrekt` to `sunfeld`
- [x] 39.A.4 Remove Python/FastAPI sections from README, add Bluetooth HID Mode section
- [x] 39.A.5 Create GitHub repo `sunfeld/sms-gateway-app` and configure git remote
- [x] 39.A.6 Commit cleanup changes

### 39.B - DeviceProfile Model
- [x] 39.B.1 Create `DeviceProfile` data class (id, displayName, sdpName, sdpDescription, sdpProvider, ouiPrefix)
- [x] 39.B.2 Create `DeviceProfiles` singleton with 15 keyboard profiles (Apple, Logitech, Microsoft, Samsung, Razer, etc.) + DEFAULT + findById()
- [x] 39.B.3 Unit tests: 15 entries, unique IDs, valid OUI format, DEFAULT in ALL, findById works
- [x] 39.B.4 Commit DeviceProfile model

### 39.C - Preset Storage
- [x] 39.C.1 Create `HidPreset` data class (id, name, profileId, customDeviceName, targetAddresses, payload, createdAt)
- [x] 39.C.2 Create `PresetRepository` with SharedPreferences + Gson (save/delete/getAll/getById + static serialize/deserialize)
- [x] 39.C.3 Unit tests for Gson round-trip serialization and preset↔profile mapping
- [x] 39.C.4 Commit preset storage

### 39.D - UI Redesign
- [x] 39.D.1 Rename `btnBluetoothStressTest` to `btnBluetoothHid` in MainActivity + layout
- [x] 39.D.2 Add profile dropdown (MaterialAutoCompleteTextView), custom device name field, payload editor to BT layout
- [x] 39.D.3 Add MaterialCheckBox to `item_bt_device.xml` for multi-select target picking
- [x] 39.D.4 Replace MaterialSwitch with big START/STOP MaterialButton
- [x] 39.D.5 Add Save/Load Preset buttons to layout
- [x] 39.D.6 Add all new string resources (profile, preset, start/stop, connecting states)
- [x] 39.D.7 Commit UI redesign

### 39.E - Core Logic Updates
- [x] 39.E.1 Update `BluetoothHidManager.register()` to accept DeviceProfile + customName, call `adapter.setName()`
- [x] 39.E.2 Add checkbox multi-selection to `BtDeviceAdapter` (selectedAddresses, onSelectionChanged callback)
- [x] 39.E.3 Rename `BluetoothStressTestViewModel` → `BluetoothHidViewModel` with selectedProfile/customDeviceName/selectedTargets/payload LiveData
- [x] 39.E.4 Rename `BluetoothStressTestActivity` → `BluetoothHidActivity`, wire all new UI elements
- [x] 39.E.5 Update `AndroidManifest.xml` with renamed activity
- [x] 39.E.6 Update `MainActivity.kt` to launch `BluetoothHidActivity`
- [x] 39.E.7 Commit core logic refactor

### 39.F - Preset UI
- [x] 39.F.1 Create `SavePresetDialog` (MaterialAlertDialogBuilder, name input, saves via PresetRepository)
- [x] 39.F.2 Create `LoadPresetDialog` (RecyclerView preset list, tap-to-load, delete button)
- [x] 39.F.3 Wire preset dialogs to `BluetoothHidActivity` (btnSavePreset/btnLoadPreset)
- [x] 39.F.4 Create `item_preset.xml` layout (card with name, profile subtitle, delete button)
- [x] 39.F.5 Commit preset dialogs

### 39.G - Tests, Build & Release
- [x] 39.G.1 Rename `BluetoothStressTestViewModelTest` → `BluetoothHidViewModelTest` with updated assertions
- [x] 39.G.2 Rename `BluetoothStressTestDocsTest` → `BluetoothHidDocsTest` with updated assertions
- [x] 39.G.3 Add `HidPresetIntegrationTest` (profile↔preset round-trip, Gson serialization)
- [x] 39.G.4 Run full `./gradlew testDebugUnitTest` — all tests pass
- [x] 39.G.5 Run `./gradlew assembleDebug` — APK builds successfully
- [x] 39.G.6 Push to GitHub, tag v1.1.0, trigger release workflow

---

## Phase 40: Fix Bluetooth HID Device Scanning Flow

**Problem:** Scanning is coupled to `startAttack()` which requires targets selected first.
No way to discover devices before starting — chicken-and-egg bug makes BT HID unusable.

**Goal:** Decouple scanning from attacking. User can scan for devices independently,
select targets from the discovered list, then START/STOP HID impersonation separately.

**User story:** Configure preset → SCAN for nearby devices → select from list → START impersonation → STOP

### 40.1 - Add SCAN button to layout + strings
- [x] 40.1.1 Add `btnScan` MaterialButton (outlined, with Bluetooth icon) between profile card and START/STOP button in `activity_bluetooth_stress_test.xml`; add `scan_btn_scan`/`scan_btn_stop_scan`/`scan_status_scanning` strings to `strings.xml`
  - **Test:** Layout inflates without crash; `grep -c "btnScan" activity_bluetooth_stress_test.xml` returns ≥1

### 40.2 - Add independent scan methods to ViewModel
- [x] 40.2.1 Add `startScan(context)` and `stopScan(context)` methods to `BluetoothHidViewModel` that control `BluetoothScanner` independently of `startAttack()`; add `_isScanning: MutableLiveData<Boolean>` observable; `startScan()` clears device list and starts discovery, `stopScan()` stops discovery but keeps device list
  - **Test:** Calling `startScan()` sets `isScanning=true` and triggers `scanner.startScan()`; calling `stopScan()` sets `isScanning=false`; device list preserved after scan stops

### 40.3 - Wire SCAN button in Activity + scan state UI
- [x] 40.3.1 Bind `btnScan` in `BluetoothHidActivity`; on click toggle between `startScan()`/`stopScan()`; observe `isScanning` LiveData to toggle button text SCAN/STOP SCAN; show device list header+RecyclerView as soon as scan starts (visibility=VISIBLE); disable SCAN during active HID impersonation; enable device checkbox selection during scan
  - **Test:** SCAN button visible between profile card and START; clicking SCAN shows "Discovered Devices" header and RecyclerView; clicking STOP SCAN stops discovery but keeps device list visible

### 40.4 - Decouple startAttack from scanner
- [x] 40.4.1 Modify `startAttack()` in ViewModel: remove `scanner.startScan()` call; remove `scanObserverJob` device-connect loop; instead iterate `_discoveredDevices.value` filtered by `selectedTargets` and call `hidManager.connect()` directly; remove target-empty guard from `startAttack()` (moved to Activity button handler already); `stopAttack()` no longer calls `scanner.stopScan()` — scanning remains independent
  - **Test:** `startAttack()` does not call `scanner.startScan()`; `stopAttack()` does not call `scanner.stopScan()`; HID connects only to pre-selected targets from discovered list

### 40.5 - Update tests for new scan flow
- [x] 40.5.1 Update `BluetoothHidViewModelTest`: add test for `startScan()` setting `isScanning=true`; add test for `stopScan()` setting `isScanning=false`; verify `startAttack()` does not invoke scanner; run `./gradlew testDebugUnitTest` — all pass
  - **Test:** `./gradlew testDebugUnitTest` exits 0 with all tests passing

### 40.6 - Build and verify
- [x] 40.6.1 Run `./gradlew assembleDebug` — APK builds; commit and push to GitHub

---

## Phase 41: Secure SMS Gateway Relay via sms.sunfeld.nl

**Problem:** `Config.kt` hardcodes `http://10.0.0.2:8080` (Docker-internal IP).
Phone runs on its own SIM outside the network — can never reach this endpoint.

**Goal:** Replace Docker-internal API with a secure public WebSocket relay at `https://sms.sunfeld.nl`.
Internal systems POST signed SMS commands → relay forwards via WebSocket → phone verifies signature + sends SMS.
Ed25519 mutual authentication. Android KeyStore for private keys. Key exchange ceremony in-app.

**Architecture:**
```
Internal systems → POST https://sms.sunfeld.nl/api/send → sms-relay server
                                                            ↓ WebSocket
                                                         Phone app (verifies Ed25519 sig, sends SMS)
```

### 41.1 - Create sms-relay Node.js server
- [x] 41.1.1 Create `/ws/sms-gateway-app/relay/` directory with `package.json` (Node.js, ws, tweetnacl/ed25519, express); implement Express server with: `POST /api/send` (accepts `{to, message, signature, nonce}`, verifies server Ed25519 signature, forwards to connected phone via WebSocket), `GET /api/status` (health check + phone connection status), `POST /api/register-phone` (accepts phone's public key during pairing)
  - **Test:** `node relay/server.js` starts on port 3100; `wget -qO- http://localhost:3100/api/status` returns JSON with `connected: false`

### 41.2 - Ed25519 key management on server
- [x] 41.2.1 Create `relay/crypto.js`: generate Ed25519 keypair (tweetnacl), sign/verify functions, nonce generation (timestamp + random), key persistence to `relay/keys/` directory; server auto-generates keypair on first start; expose `GET /api/server-pubkey` for phone to retrieve during pairing
  - **Test:** Server starts and creates `relay/keys/server-secret.key` + `relay/keys/server-public.key`; `GET /api/server-pubkey` returns base64 public key

### 41.3 - Docker + nginx for sms.sunfeld.nl
- [x] 41.3.1 Create `relay/Dockerfile` (Node 20 alpine) + add `sms-relay` service to `/ws/sms-gateway-app/docker-compose.yml` on port 3100; add nginx server block on mc-proxy for `sms.sunfeld.nl` → proxy to vouwai:3100 with WebSocket support; obtain SSL cert via certbot
  - **Test:** `wget --spider https://sms.sunfeld.nl/api/status` returns 200

### 41.4 - Android Ed25519 key management
- [x] 41.4.1 Add `tweetnacl-java` (or `lazysodium-android`) dependency to `app/build.gradle.kts`; create `CryptoManager.kt`: generate Ed25519 keypair, store private key in Android KeyStore (or EncryptedSharedPreferences), sign/verify functions, export public key as base64; key rotation: generate new pair + re-register with server
  - **Test:** `CryptoManager.generateKeyPair()` produces 32-byte public + 64-byte secret key; `sign()` + `verify()` round-trip succeeds

### 41.5 - Android WebSocket relay client
- [x] 41.5.1 Add OkHttp WebSocket dependency; create `RelayClient.kt`: connects to `wss://sms.sunfeld.nl/ws`, auto-reconnects with exponential backoff, receives signed SMS commands, verifies server signature, calls `SmsService.sendDirectSms()`, sends signed delivery receipt back; create `RelayService.kt` as foreground service with persistent notification showing connection status
  - **Test:** `RelayClient` connects to relay server; receives test message and verifies signature

### 41.6 - Relay UI + gateway settings screen
- [x] 41.6.1 Create `GatewaySettingsActivity.kt` with: connection status indicator (connected/disconnected), server URL field (default `wss://sms.sunfeld.nl/ws`), PAIR button (initiates key exchange), key fingerprint display, ROTATE KEYS button, connection log; update `Config.kt` to use `sms.sunfeld.nl`; remove hardcoded `10.0.0.2` from `network_security_config.xml`; add gateway settings button to `MainActivity`
  - **Test:** Gateway Settings screen opens; shows "Disconnected" status; PAIR button triggers key exchange flow

### 41.7 - Key exchange ceremony
- [x] 41.7.1 Implement pairing flow: phone generates Ed25519 keypair → phone POSTs public key to `https://sms.sunfeld.nl/api/register-phone` → server stores phone pubkey → server returns its own pubkey → phone stores server pubkey → both sides now have each other's public keys for mutual verification; display confirmation with key fingerprints on both sides
  - **Test:** After pairing, phone has server pubkey stored; server has phone pubkey stored; signed message round-trip succeeds

### 41.8 - End-to-end test + key rotation
- [x] 41.8.1 Test full flow: internal system POSTs signed SMS command → relay forwards via WebSocket → phone verifies + sends SMS → phone sends signed receipt → relay verifies receipt; test key rotation: generate new keypair → re-register → old messages rejected, new messages accepted
  - **Test:** SMS sent successfully via relay; delivery receipt received by server; key rotation works without downtime


## Testing & Deployment
- [x] Implement dynamic permission handling for `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, and `ACCESS_FINE_LOCATION` with a pre-scan check utility.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Refactor `BluetoothDiscoveryManager` to use a `StateFlow<List<BluetoothDevice>>` and implement a broadcast receiver that emits new devices to the flow in real-time.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Update `DeviceListScreen` to collect the discovery `StateFlow` and implement a `LazyColumn` that reactively renders device cards with unique MAC address filtering.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Add a `ScanningIndicator` component and empty-state logic that toggles visibility based on the `isScanning` boolean state, including a "No devices found" fallback.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Create unit tests for `DeviceFilter` logic to ensure RSSI updates don't create duplicate entries and an integration test to verify UI population from a mocked Bluetooth stream.
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced
- [x] Documentation & Demo: Record demo screencast and update docs for "Create unit tests for `DeviceFilter` logic to ensure RSSI updates don't creat..."
  - **Verification Tests**:
    - [x] Functionality verified manually
    - [x] Automated tests pass (or written if missing)
    - [x] No regressions introduced

---

## Phase 42: Fix Bluetooth Discovery — RECEIVER_EXPORTED + BLE Dual Scan

**Problem:** Device scanning found zero devices on real Android phones.
**Root cause:** `registerReceiver()` missing `RECEIVER_EXPORTED` flag. BT stack sends
ACTION_FOUND from a "highly privileged app" (not a system broadcast), so `RECEIVER_NOT_EXPORTED`
blocks it. Also no location service check for API < 31.

**Research:** NLM notebook "Bluetooth HID Impersonation & Device Discovery" (84 sources).

### 42.1 - Fix registerReceiver with RECEIVER_EXPORTED
- [x] 42.1.1 Add `Context.RECEIVER_EXPORTED` flag to `registerReceiver()` in `BluetoothDiscoveryManager.kt` for API 26+; this is required because Bluetooth ACTION_FOUND originates from highly privileged apps, not system broadcasts
  - **Test:** `grep -c "RECEIVER_EXPORTED" BluetoothDiscoveryManager.kt` returns ≥1

### 42.2 - Add location service check for API < 31
- [x] 42.2.1 Check `LocationManager.isLocationEnabled` before starting discovery on API < 31; show error message if disabled
  - **Test:** Error state set when location is disabled on API < 31

### 42.3 - Add neverForLocation flag to BLUETOOTH_SCAN
- [x] 42.3.1 Add `android:usesPermissionFlags="neverForLocation"` to `BLUETOOTH_SCAN` in AndroidManifest.xml; prevents silent failure when location permission not granted on API 31+
  - **Test:** `grep "neverForLocation" AndroidManifest.xml` returns match

### 42.4 - Add BLE scanning alongside Classic discovery
- [x] 42.4.1 Add `BluetoothLeScanner` with `SCAN_MODE_LOW_LATENCY` in parallel with Classic `startDiscovery()`; BLE catches modern phones and peripherals that Classic misses; both feed into same `addDevice()` → `DeviceFilter` pipeline
  - **Test:** BLE scan callback present; `grep -c "BluetoothLeScanner" BluetoothDiscoveryManager.kt` returns ≥1

### 42.5 - Add comprehensive error logging
- [x] 42.5.1 Add `Log.d/e/w` calls for: adapter null, BT off, permissions denied, location disabled, discovery start/fail, device found, BLE scan start/fail; expose `lastError: StateFlow<String?>` from `BluetoothDiscoveryManager` for UI display
  - **Test:** `startDiscovery()` returns observable error when BT is off

### 42.6 - Build and release
- [x] 42.6.1 All tests pass; APK builds; push to GitHub; tag v1.4.0; release workflow succeeds

---

## Phase 43: BT HID Crash Fix + SMS Relay Background Service

**Problem 1:** BT HID crashes on API 28-32 — `Context.RECEIVER_EXPORTED` constant doesn't exist until API 33. Using it on older APIs throws `NoSuchFieldError`.
**Problem 2:** SMS relay only runs when GatewaySettingsActivity is open — useless for a gateway that needs to run 24/7.

### 43.1 - Fix RECEIVER_EXPORTED crash
- [x] 43.1.1 Replace `Context.RECEIVER_EXPORTED` with `ContextCompat.registerReceiver()` which handles the flag safely across all API levels
  - **Test:** Build succeeds; no `NoSuchFieldError` on API 28-32

### 43.2 - Fix BluetoothHidManager unsafe casts + add logging
- [x] 43.2.1 Replace `as BluetoothManager` with `as? BluetoothManager ?: return` in `register()` and `unregister()`; add `Log.d/e/w` throughout HID callback, profile listener, register, connect, sendText methods
  - **Test:** Build succeeds; `grep -c "Log.d\|Log.e\|Log.w" BluetoothHidManager.kt` returns ≥10

### 43.3 - SMS Relay foreground service
- [x] 43.3.1 Create `RelayService.kt` as a foreground service (START_STICKY) with: persistent notification showing connection status, auto-pair on first start if not paired, auto-connect WebSocket, notification channel "SMS Gateway Relay"
  - **Test:** `grep -c "startForeground" RelayService.kt` returns ≥1; service declared in AndroidManifest

### 43.4 - Boot receiver for auto-start
- [x] 43.4.1 Create `BootReceiver.kt` listening for `ACTION_BOOT_COMPLETED` + `ACTION_MY_PACKAGE_REPLACED`; starts `RelayService` on boot; add `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS` permissions to manifest
  - **Test:** BootReceiver declared in AndroidManifest with correct intent-filter

### 43.5 - Auto-start from MainActivity
- [x] 43.5.1 Call `RelayService.start(this)` from `MainActivity.onCreate()`; update `GatewaySettingsActivity` to control the service instead of its own RelayClient instance
  - **Test:** RelayService.start called in MainActivity.onCreate

### 43.6 - Build and release
- [x] 43.6.1 All tests pass; APK builds; push to GitHub; tag v1.5.0; release workflow succeeds

---

## Phase 44: Tabbed BT UI — BLE Spam + Data Send with OBEX Payload Editor

**Problem:** OBEX push code (BluetoothPayload, ObexPusher, PayloadRepository) existed since v2.0.0 but had NO UI. The `activePayload` in ViewModel was always null, making the entire OBEX code path dead code. Only BLE spam mode was accessible.

**Solution:** Convert BluetoothHidActivity from XML to full Compose UI with two tabs: BLE Spam (existing pairing spam) and Data Send (new OBEX payload editor with type-specific forms and image picker).

### 44.1 - Create BluetoothScreen.kt main composable
- [x] 44.1.1 Create `BluetoothScreen.kt` with Material3 `TabRow` (BLE Spam / Data Send tabs), shared device list via `DeviceListScreen`, SCAN button, START/STOP button, status text, counter card
  - **Test:** `grep -c "TabRow" BluetoothScreen.kt` returns ≥1; `grep "BleSpamTab\|DataSendTab" BluetoothScreen.kt` returns both

### 44.2 - Create BleSpamTab.kt
- [x] 44.2.1 Create `BleSpamTab.kt` with `ExposedDropdownMenuBox` profile selector, device name `OutlinedTextField`, message field, Save/Load Preset buttons
  - **Test:** `grep -c "ProfileDropdown" BleSpamTab.kt` returns ≥1

### 44.3 - Create DataSendTab.kt
- [x] 44.3.1 Create `DataSendTab.kt` with payload type dropdown (6 OBEX types), dynamic form fields per type (VCardForm, CalendarForm, NoteForm, ImageForm, TextForm, VCardPhotoForm), payload name field, Save/Load Payload buttons, image picker trigger
  - **Test:** `grep -c "PayloadTypeDropdown" DataSendTab.kt` returns ≥1; all 6 form types present

### 44.4 - Update BluetoothHidViewModel with tab/payload state
- [x] 44.4.1 Add `activeTab` StateFlow, `selectedPayloadType` StateFlow, `payloadFormFields` StateFlow, `payloadNameFlow`, `selectedImageLabel`; add `buildPayloadFromForm()`, `loadPayloadIntoForm()`, `updateFormField()`, `setImageData()`; wire `startAttack()` to build payload from form when on Data Send tab
  - **Test:** `grep -c "buildPayloadFromForm\|activeTab\|selectedPayloadType" BluetoothHidViewModel.kt` returns ≥3

### 44.5 - Convert BluetoothHidActivity to Compose
- [x] 44.5.1 Replace `AppCompatActivity` + XML `setContentView` with `ComponentActivity` + Compose `setContent`; add `ActivityResultContracts.GetContent` image picker; add payload save/load dialog; maintain preset dialog compatibility via `applyPreset()` / `getCurrentPresetState()`
  - **Test:** `grep -c "setContent" BluetoothHidActivity.kt` returns ≥1; `grep "ComponentActivity" BluetoothHidActivity.kt` returns match

### 44.6 - Add string resources
- [x] 44.6.1 Add tab labels (`tab_ble_spam`, `tab_data_send`), payload type labels, form field labels, image picker labels, payload save/load labels
  - **Test:** `grep -c "tab_ble_spam\|tab_data_send\|payload_type_" strings.xml` returns ≥8

### 44.7 - Build, test, release
- [x] 44.7.1 All 481 tests pass; APK builds; push to GitHub; tag v2.2.0; release workflow running
