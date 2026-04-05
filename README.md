# AveryTask

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)

A native Android todo list app built with Kotlin and Jetpack Compose. Currently in early development — the project scaffold is in place with a single-activity Compose architecture ready to build on.

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.2.10 |
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| Build | Gradle (Kotlin DSL) | 8.13 |
| Compose Compiler | Kotlin Compiler Plugin | 2.2.10 |

**Target:** Android 8.0+ (API 26) through Android 15 (API 35)

## Requirements

- Android Studio Ladybug (2024.2.1) or later
- JDK 17
- Android SDK 35
- Device or emulator running Android 8.0+ (API 26)

## Getting Started

```bash
# Clone the repository
git clone https://github.com/akarlin3/averyTask.git
cd averyTask

# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug
```

## Project Structure

```
app/src/main/java/com/averykarlin/averytask/
├── MainActivity.kt              # Single-activity entry point (Compose)
└── ui/theme/
    ├── Color.kt                 # Material 3 color tokens
    ├── Theme.kt                 # Light/dark theme with dynamic color support
    └── Type.kt                  # Typography definitions
```

## Architecture

The app uses a single-activity architecture with Jetpack Compose for the entire UI layer. The theme supports Material 3 dynamic colors on Android 12+ and falls back to a static light/dark scheme on older devices.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumentation tests
./gradlew connectedAndroidTest

# Clean
./gradlew clean
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code conventions, and pull request guidelines.

## Security

See [SECURITY.md](SECURITY.md) for security considerations and how to report vulnerabilities.

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).
