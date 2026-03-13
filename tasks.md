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
