# Contributing to PrismTask

Thank you for your interest in contributing. This document covers development setup, code conventions, and the pull request process.

## Development Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/akarlin3/prismTask.git
   cd prismTask
   ```

### Android

2. Open the project in Android Studio Ladybug (2024.2.1) or later.

3. Ensure you have JDK 17 and Android SDK 35 installed.

4. Build and run:
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

### Web

2. Ensure you have Node.js 22+ installed.

3. Install dependencies and start the dev server:
   ```bash
   cd web
   npm install
   cp .env.example .env.local   # edit API URL if needed
   npm run dev
   ```

4. Open [http://localhost:5173](http://localhost:5173) in your browser.

### Backend

2. Ensure you have Python 3.11+ installed.

3. Install dependencies and start the server:
   ```bash
   cd backend
   pip install -r requirements.txt
   cp .env.example .env          # configure DATABASE_URL, JWT_SECRET_KEY, etc.
   uvicorn app.main:app --reload
   ```

4. API docs are available at [http://localhost:8000/docs](http://localhost:8000/docs).

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
- Use `PrismTaskTheme` as the root wrapper in all previews and the main content.
- Extract reusable composables into dedicated files under `ui/components/`.

### TypeScript / React (Web)

- Use functional components with hooks — no class components.
- Colocate feature code under `web/src/features/<feature>/`.
- Shared components go in `web/src/components/` (ui/ for primitives, shared/ for domain components).
- Use Zustand stores for global state; keep component-local state with `useState`/`useReducer`.
- Style with TailwindCSS utility classes. Avoid inline style objects.
- Use path aliases (`@/components/...`) configured in `vite.config.ts`.

### Dependencies

- **Android:** Add new dependencies to `app/build.gradle.kts`. Use the Compose BOM for all Compose library versions — do not pin individual Compose artifact versions. If a new dependency uses reflection or annotation processing, update `app/proguard-rules.pro` with the appropriate keep rules.
- **Web:** Add new dependencies via `npm install` in the `web/` directory. Keep dev dependencies separate (`npm install -D`).

## Making Changes

1. Fork the repository and create a feature branch from `main`:
   ```bash
   git checkout -b feature/my-change
   ```
2. Keep commits focused and atomic — one logical change per commit.
3. Test your changes:
   - **Android:** Device or emulator running Android 8.0+ (API 26).
   - **Web:** Run `npm run lint && npm run test:run` in `web/`.
   - **Backend:** Run `pytest` in `backend/`.
4. Verify the build succeeds:
   ```bash
   # Android
   ./gradlew assembleDebug

   # Web
   cd web && npm run build
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
- How you verified the change (device/emulator/browser, API level if Android).

## Screenshots
<!-- If applicable -->
```

## Reporting Issues

Use [GitHub Issues](https://github.com/akarlin3/prismTask/issues) to report bugs or request features. Include:

- Steps to reproduce (for bugs)
- Device/emulator info and Android version
- Expected vs. actual behavior
- Screenshots or screen recordings if applicable

## License

By contributing, you agree that your contributions will be licensed under the [AGPL-3.0 License](LICENSE).
