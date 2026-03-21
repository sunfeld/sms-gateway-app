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
    - [ ] Functionality verified manually
    - [ ] Automated tests pass (or written if missing)
    - [ ] No regressions introduced
- [ ] : Create `POST /api/projects/{id}/install-gateway` endpoint to trigger the deployment pipeline for the SMS Gateway.
  - **Verification Tests**:
    - [ ] Functionality verified manually
    - [ ] Automated tests pass (or written if missing)
    - [ ] No regressions introduced
- [ ] : Create `GatewayInstallButton.kt` component with states for `IDLE`, `INSTALLING`, and `ERROR` using Material 3 Button styles.
  - **Verification Tests**:
    - [ ] Functionality verified manually
    - [ ] Automated tests pass (or written if missing)
    - [ ] No regressions introduced
- [ ] : Integrate `GatewayInstallButton` into the `ProjectDetailScreen` layout, ensuring visibility when `sms_gateway_installed` is false.
  - **Verification Tests**:
    - [ ] Functionality verified manually
    - [ ] Automated tests pass (or written if missing)
    - [ ] No regressions introduced
- [ ] : Implement `ProjectViewModel` logic to handle the installation API call and update the local UI state upon success or failure.
  - **Verification Tests**:
    - [ ] Functionality verified manually
    - [ ] Automated tests pass (or written if missing)
    - [ ] No regressions introduced
- [ ] Documentation & Demo: Record demo screencast and update docs for ": Implement `ProjectViewModel` logic to handle the installation API call and ..."
  - **Verification Tests**:
    - [ ] Functionality verified manually
    - [ ] Automated tests pass (or written if missing)
    - [ ] No regressions introduced
- [ ] : Add a `BroadcastReceiver` or polling mechanism to refresh the project status once the backend confirms the gateway is "ACTIVE".
  - **Verification Tests**:
    - [ ] Functionality verified manually
    - [ ] Automated tests pass (or written if missing)
    - [ ] No regressions introduced
