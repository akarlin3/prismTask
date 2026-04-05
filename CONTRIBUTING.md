# Contributing to AveryTask

Thank you for your interest in contributing. This document covers development setup, code conventions, and the pull request process.

## Development Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/akarlin3/averyTask.git
   cd averyTask
   ```

2. Open the project in Android Studio Ladybug (2024.2.1) or later.

3. Ensure you have JDK 17 and Android SDK 35 installed.

4. Build and run:
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

## Code Style

### Kotlin

- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Use data classes for immutable state objects.
- Prefer `val` over `var` wherever possible.
- Use expression bodies for single-expression functions.
- Name files after the primary class or object they contain.

### Compose

- All UI is Jetpack Compose. Do not introduce XML layouts.
- Keep composable functions stateless where possible; hoist state up.
- Use `AveryTaskTheme` as the root wrapper in all previews and the main content.
- Extract reusable composables into dedicated files under `ui/components/`.

### Dependencies

- Add new dependencies to `app/build.gradle.kts`.
- Use the Compose BOM for all Compose library versions — do not pin individual Compose artifact versions.
- If a new dependency uses reflection or annotation processing, update `app/proguard-rules.pro` with the appropriate keep rules.

## Making Changes

1. Fork the repository and create a feature branch from `main`:
   ```bash
   git checkout -b feature/my-change
   ```
2. Keep commits focused and atomic — one logical change per commit.
3. Test your changes on a device or emulator running Android 8.0+ (API 26).
4. Verify the build succeeds:
   ```bash
   ./gradlew assembleDebug
   ```

## Pull Requests

- Open PRs against the `main` branch.
- Use a descriptive title (under 70 characters) and explain the **what** and **why** in the body.
- Keep PRs reasonably scoped — large changes are harder to review.
- Ensure the project builds cleanly before submitting.

### PR Body Template

```markdown
## Summary
- What this PR does and why.

## Testing
- How you verified the change (device, emulator, API level).

## Screenshots
<!-- If applicable -->
```

## Reporting Issues

Use [GitHub Issues](https://github.com/akarlin3/averyTask/issues) to report bugs or request features. Include:

- Steps to reproduce (for bugs)
- Device/emulator info and Android version
- Expected vs. actual behavior
- Screenshots or screen recordings if applicable

## License

By contributing, you agree that your contributions will be licensed under the [AGPL-3.0 License](LICENSE).
