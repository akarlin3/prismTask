# CLAUDE.md

## Project Overview

**// TODO** (`com.todounified`) is a native Android todo list manager built with Kotlin and Jetpack Compose. It combines standard task management with an AI-powered JSX import feature that uses the Anthropic Claude API to extract tasks from React components.

## Tech Stack

- **Language**: Kotlin (JVM target 17)
- **UI**: Jetpack Compose with Material 3
- **Database**: Room 2.6.1 (SQLite ORM)
- **Networking**: OkHttp 4.12.0 + Gson 2.10.1
- **AI**: Anthropic Claude API (claude-sonnet-4-20250514)
- **Build**: Gradle 8.5 with Kotlin DSL + KSP
- **Min SDK**: 26 (Android 8.0) / **Target SDK**: 34 (Android 14)

## Project Structure

```
app/src/main/java/com/todounified/
├── TodoApp.kt                  # Application class (Room DB init)
├── MainActivity.kt             # Single activity entry point
├── ai/
│   └── JsxParserService.kt    # Claude API integration for JSX parsing
├── data/
│   ├── Entities.kt             # Room entities: TaskList, Task, ImportedTab + type converters
│   ├── TodoDao.kt              # Room DAO (queries for lists, tasks, imported tabs)
│   └── TodoDatabase.kt         # Room database definition
├── viewmodel/
│   └── TodoViewModel.kt        # Single ViewModel with UiState StateFlow
└── ui/
    ├── components/Components.kt # Reusable Compose components (task rows, dialogs, etc.)
    ├── screens/MainScreen.kt    # Main screen composable
    └── theme/Theme.kt           # Color scheme and typography
```

## Architecture

- **MVVM**: Single `TodoViewModel` manages all app state via an immutable `UiState` data class exposed as `StateFlow`
- **Room Database**: 3 entities (`task_lists`, `tasks`, `imported_tabs`) with cascade deletes on list removal
- **Coroutines**: All DB and network operations run in `viewModelScope` coroutine launches
- **Compose**: Purely functional UI observing ViewModel state; no XML layouts

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (uses ProGuard/R8 minification)
./gradlew assembleRelease

# Clean
./gradlew clean
```

There is no CI/CD pipeline or automated test suite configured.

## Key Conventions

- **State management**: All UI state flows through `TodoViewModel.UiState`. Update state with `_uiState.update { it.copy(...) }`.
- **Database access**: Use `TodoDao` methods from coroutines in the ViewModel. Room handles threading via `room-ktx` suspend functions.
- **Type converters**: `Priority` enum and `List<String>` tags use Room `@TypeConverter` in `Entities.kt`.
- **API key**: Stored in-memory only (not persisted to disk). User enters via Settings dialog.
- **No test infrastructure**: No unit or instrumentation tests exist yet.

## Database Schema

| Table | Key Columns |
|-------|------------|
| `task_lists` | id, name, emoji, sortOrder |
| `tasks` | id, listId (FK), title, done, priority, dueDate, tags, createdAt |
| `imported_tabs` | id, fileName, jsxCode, extractedTasksJson, htmlPreview |

## Important Files

- `app/build.gradle.kts` - All dependencies and build configuration
- `app/proguard-rules.pro` - R8 keep rules (preserves Gson + data classes)
- `app/src/main/AndroidManifest.xml` - INTERNET permission, FileProvider config
- `settings.gradle.kts` - Repository sources and project includes
