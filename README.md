# SMS Gateway App

An Android application for sending and receiving SMS messages programmatically. Built as part of the Sunfeld infrastructure to provide SMS gateway capabilities from an Android device.

## Project Scope

- Send SMS messages via `SmsManager` API
- Track delivery status with `PendingIntent` callbacks (SMS_SENT / SMS_DELIVERED)
- Runtime permission handling for SMS, phone state, and location
- Minimal Material Design UI with recipient input, message field, and send button
- Phone number validation and error handling

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

### Install on Device

```bash
./gradlew installDebug
```

Ensure USB debugging is enabled and the device is connected via ADB.
