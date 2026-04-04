# // TODO — Unified Todo List Manager

A native Android todo list app that combines standard task management with AI-powered JSX component import. Built with Kotlin, Jetpack Compose, and the Anthropic Claude API.

## Features

- **Task Lists** — Create multiple lists with custom names and emojis
- **Rich Tasks** — Priorities (low/medium/high/urgent), due dates, tags, and completion tracking
- **Filter & Sort** — View all/active/done tasks; sort by creation date, priority, or due date
- **JSX Import** — Import React/JSX todo components from files and extract tasks using Claude AI
- **Merge Workflow** — Preview imported components, then merge extracted tasks into native lists
- **Dark Theme** — Monospace-inspired design with Material 3

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin (JVM 17) |
| UI | Jetpack Compose + Material 3 |
| Database | Room 2.6.1 |
| Networking | OkHttp 4.12.0 |
| AI | Anthropic Claude API |
| Build | Gradle 8.5 (Kotlin DSL) + KSP |

## Requirements

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- Device or emulator running Android 8.0+ (API 26)

## Getting Started

```bash
# Clone the repository
git clone https://github.com/akarlin3/personal_todo.git
cd personal_todo

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

To use the JSX import feature, open Settings in the app and enter your Anthropic API key.

## Project Structure

```
app/src/main/java/com/todounified/
├── TodoApp.kt              # Application class (database initialization)
├── MainActivity.kt         # Single-activity entry point
├── ai/                     # Claude API integration
├── data/                   # Room entities, DAO, and database
├── viewmodel/              # MVVM ViewModel with StateFlow
└── ui/                     # Compose screens, components, and theme
```

## Architecture

The app follows **MVVM** with a single `TodoViewModel` exposing an immutable `UiState` via Kotlin `StateFlow`. The UI layer is built entirely with Jetpack Compose (no XML layouts). Room handles persistence with three tables: `task_lists`, `tasks`, and `imported_tabs`.

## License

Private project — not licensed for redistribution.