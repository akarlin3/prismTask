# Shake-To-Report Removal Audit

**Date:** 2026-04-28
**Scope:** Remove the shake-to-report-a-bug gesture (Shake feature) from the Android app while keeping the Bug Report screen, the debug Floating Feedback button, and the AdminBugReports flow intact.
**Phase:** 1 — Audit only (no code or config changes).

## Premise verification

| Premise | Verified | Evidence |
|---|---|---|
| Shake gesture is wired in `MainActivity` | ✅ | `MainActivity.kt:546–590` — collects `shakeDetector.shakeEvents`, captures a screenshot, opens an "Report A Bug?" dialog, navigates to `BugReport.createRoute(fromScreen = "ShakeReport", screenshotUri = …)`. |
| `ShakeDetector` and `ScreenshotCapture` are only used by the shake feature | ✅ | `ShakeDetector` referenced only by `MainActivity`, `ShakeDetectorTest`, `HiltDependencyGraphTest`. `ScreenshotCapture` referenced only by `MainActivity`, `HiltDependencyGraphTest`. No diagnostics or non-shake call sites. |
| `ShakePreferences` is a leaf preference store | ✅ | Only `MainActivity` (read), `SettingsViewModel` (state + clearAll), `DataExporter`/`DataImporter` (config v5 round-trip), `AccountDeletionService` (cleared on sign-out), `PreferenceSyncModule` (cloud sync), `ShakePreferencesTest` (unit). |
| Bug Report screen has another entry point that survives shake removal | ✅ | `MainActivity.kt:687–697` — `FeedbackButton` floating action button gated by `BuildConfig.DEBUG`. `BugReportSmokeTest` already exercises this entry point. |
| `triggerHapticFeedback()` in `MainActivity` is shake-only | ✅ | The only call site in `MainActivity` is `MainActivity.kt:584`, inside the shake collector. Other haptics in the app use `LocalHapticFeedback`. Safe to delete. |

No premise is contradicted. Removal proceeds.

## Scope inventory

### Production source — files to delete entirely

1. `app/src/main/java/com/averycorp/prismtask/domain/usecase/ShakeDetector.kt`
2. `app/src/main/java/com/averycorp/prismtask/domain/usecase/ScreenshotCapture.kt`
3. `app/src/main/java/com/averycorp/prismtask/data/preferences/ShakePreferences.kt`
4. `app/src/main/java/com/averycorp/prismtask/ui/screens/settings/sections/ShakeSection.kt`

### Production source — files to edit

| File | Change |
|---|---|
| `MainActivity.kt` | Remove `ShakeDetector`/`ScreenshotCapture`/`ShakePreferences` imports + `@Inject` fields; delete `shakeFeatureEnabled` field; delete the three `LaunchedEffect` blocks (lines 558–590) and the `if (showShakeDialog) AlertDialog{...}` block (lines 610–646); remove the shake re-register from `onResume()` and the `shakeDetector.unregister()` from `onPause()`; delete the `triggerHapticFeedback()` helper. Keep `screenshotCapture.cleanupOldScreenshots(this)` removal — the helper goes with the class. |
| `ui/screens/settings/SettingsViewModel.kt` | Drop `ShakePreferences` import + constructor param; delete `shakeEnabled`/`shakeSensitivity` flows and their setters (`setShakeEnabled`, `setShakeSensitivity`); remove `shakePreferences.clearAll()` from the data-clear path. |
| `ui/screens/settings/AccessibilityScreen.kt` | Drop `ShakeSection` import; delete `shakeEnabled`/`shakeSensitivity` `collectAsStateWithLifecycle` calls and the `ShakeSection` composable invocation. |
| `ui/screens/settings/SettingsScreen.kt` | Update Accessibility nav-row subtitle from `"Motion, contrast, voice, shake"` → `"Motion, contrast, voice"`. |
| `data/export/DataExporter.kt` | Drop `ShakePreferences` import + constructor param; delete `exportShakeConfig()` and the `config.add("shake", …)` call. The exported JSON will no longer carry a `"shake"` block — that's an additive removal; importers are tolerant of missing keys (each `s.get("…")?.takeIf { !it.isJsonNull }` already guards). |
| `data/export/DataImporter.kt` | Drop `ShakePreferences` import + constructor param; delete `importShakeConfig()` and its call site. Backup files written before the removal will simply be ignored on the `"shake"` block — the JSON parse never throws on extra unknown top-level keys. |
| `data/remote/AccountDeletionService.kt` | Drop `"shake_prefs"` from `ALL_PREFERENCE_DATASTORE_NAMES`. |
| `di/PreferenceSyncModule.kt` | Drop the `shake_prefs` `PreferenceSyncSpec` and the `shakeDataStore` import. **Side effect:** existing installs will stop syncing already-uploaded `shake_prefs` Firestore docs. Those orphan docs are harmless (two scalar fields, no PII); we accept the orphan rather than writing a migration cleanup, since the data is now meaningless. Documented in CHANGELOG. |

### Tests — files to delete

1. `app/src/test/java/com/averycorp/prismtask/domain/ShakeDetectorTest.kt`
2. `app/src/test/java/com/averycorp/prismtask/data/preferences/ShakePreferencesTest.kt`

### Tests — files to edit

| File | Change |
|---|---|
| `app/src/test/java/com/averycorp/prismtask/data/export/DataImporterTest.kt` | Drop the `shakePreferences` mock field, its `mockk(relaxed = true)` initializer, and its slot in the `DataImporter(…)` constructor call. |
| `app/src/test/java/com/averycorp/prismtask/data/export/DataImporterV5Test.kt` | Same shape as above. Also remove any v5 round-trip assertions touching the `"shake"` block (verified: only the constructor wiring and mock are affected; no per-key assertions). |
| `app/src/test/java/com/averycorp/prismtask/startup/HiltDependencyGraphTest.kt` | Drop `"ShakeDetector"` and `"ScreenshotCapture"` from the asserted graph membership list. |
| `app/src/test/java/com/averycorp/prismtask/startup/DataStoreCollisionTest.kt` | Drop `"shake_prefs"` from the expected DataStore-name uniqueness set. |
| `app/src/androidTest/java/com/averycorp/prismtask/smoke/BugReportSmokeTest.kt` | Update the file-level KDoc to remove the shake mention; the assertions already cover only the floating button. |

### Documentation — files to edit

| File | Change |
|---|---|
| `CLAUDE.md` | Remove "Shake-to-capture" mentions from the v1.4–1.6 changelog summary (two occurrences). |
| `CHANGELOG.md` | Add an `Unreleased` "Removed: Shake-to-report-a-bug gesture and supporting infrastructure" entry. The historical mentions of shake in older releases stay — they're a record of what was once shipped. |
| `README.md` | No matches for "shake" — no change needed. |
| `docs/store-listing/copy/en-US/full-description.txt` | Drop "shake-to-capture" from the ND-friendly modes feature line. |
| `docs/store-listing/PHASE3_VERIFICATION.md` | Update the verification matrix row that names `ShakeDetector`. |
| `docs/store-listing/PHASE1_AUDIT.md` | Drop "Shake-to-capture" from the ND-friendly modes bullet. |
| `docs/store-listing/graphics/src/screenshots/08-onboarding-matrix.svg` | Update the "Brain Mode, UI complexity, shake to capture, 'good enough' timer" caption to remove "shake to capture". |
| `web/src/features/onboarding/OnboardingScreen.tsx` | Update the Brain Mode subtitle that lists "shake-to-capture" alongside Brain Mode, Focus Release, forgiveness streaks. |
| `docs/audits/ANDROID_WEB_PARITY_AUDIT.md` and `docs/audits/CRASH_STABILITY_AUDIT.md` and `docs/audits/PRE_PHASE_F_MEGA_AUDIT.md` and `docs/audits/AUTOMATED_EDGE_CASE_TESTING_AUDIT.md` and `docs/audits/DAILY_ESSENTIALS_AUDIT.md` and `docs/WEB_PARITY_GAP_ANALYSIS.md` | **No change.** These are point-in-time audit records; updating them rewrites history. |
| `themesets/screen-settings-shake.jsx` and other `themesets/` mockups | **No change.** Design mockups are historical artifacts; deletion is not in scope for a feature-removal PR. |
| `.design-bundle/**` | **No change.** Untracked (gitignored locally). |

### Out of scope (explicitly preserved)

- The Bug Report screen, ViewModel, route, navigation arg shape, and admin viewer (`BugReportScreen.kt`, `BugReportViewModel.kt`, `FeedbackRoutes.kt`, `AdminBugReportsScreen.kt`, etc.).
- The debug-only `FeedbackButton` and its placement in `MainActivity`.
- The `screenshotUri` route arg on `BugReport` — leave it; it's still useful if a future entry point wants to pre-fill a screenshot, and removing it is gratuitous churn that breaks the navigation contract.

## Findings & risks

### RED — none

### YELLOW

- **Cloud `shake_prefs` orphan.** Removing the spec from `PreferenceSyncModule` means existing users' Firestore mirrors of `shake_prefs` (two keys: `shake_enabled`, `shake_sensitivity`) become orphan docs. Mitigation: accept the orphan; the data is meaningless without the feature, and writing a one-shot delete migration costs more than it saves. CHANGELOG entry calls this out so future audits know why the orphan exists.
- **Local `shake_prefs` DataStore file persists on existing installs.** Same shape — the file stays on disk until uninstall. Harmless. No migration needed.

### GREEN

- The Bug Report screen's `screenshotUri` arg + `FromScreen = "ShakeReport"` analytics tag both vanish from the call graph naturally once `MainActivity`'s shake block is gone. Backend `bug_reports.from_screen` may have historical values of `"ShakeReport"` — that's analytics data, not a forward-coupling.
- `BugReportSmokeTest` already only exercises the floating-button entry point, so nothing breaks the smoke suite.
- `DataExporter`/`DataImporter` round-trip tests do not assert presence of the `"shake"` JSON block at the field level — only the mock wiring needs updating.
- `triggerHapticFeedback()` deletion is safe: every other haptic site in the app uses Compose's `LocalHapticFeedback` instead.

### DEFERRED — none

## Ranked improvement plan

Single bundled PR is the right shape — every edit is part of one coherent removal scope, and splitting (e.g. "remove preferences" / "remove UI" / "remove tests") would yield a broken intermediate state on `main`. Per CLAUDE.md, bundled-PR is the rule when the scope is a single coherent unit. No fan-out.

| # | Step | Why | Cost |
|---|---|---|---|
| 1 | Delete the four leaf source files (`ShakeDetector`, `ScreenshotCapture`, `ShakePreferences`, `ShakeSection`). | Source of truth — removes the symbols the rest of the diff has to forget about. | Low |
| 2 | Unwire `MainActivity` (imports, fields, `LaunchedEffect`s, dialog, `onResume`/`onPause`, `triggerHapticFeedback`). | Without this, the app fails to compile after step 1. | Low |
| 3 | Unwire `SettingsViewModel`, `AccessibilityScreen`, `SettingsScreen` subtitle. | Settings UI cleanup — same compile dependency as step 2. | Low |
| 4 | Unwire `DataExporter`, `DataImporter`, `AccountDeletionService`, `PreferenceSyncModule`. | Drops the four cross-cutting touchpoints in one pass. | Low |
| 5 | Update / delete tests in `app/src/test/...` and `app/src/androidTest/...`. | Compile + green test suite. | Low |
| 6 | Update `CLAUDE.md`, `CHANGELOG.md`, store-listing copy/svg, web `OnboardingScreen.tsx`. | Keeps user-facing docs and store listing honest. | Low |

## Anti-patterns flagged but not fixed

- **Two ScreenshotCapture mentions in `docs/audits/AUTOMATED_EDGE_CASE_TESTING_AUDIT.md` and `CRASH_STABILITY_AUDIT.md`** describe a `data/diagnostics/ScreenshotCapture.kt` path that does not exist in the tree (the only file is at `domain/usecase/ScreenshotCapture.kt`). These are audit-doc drift, harmless. Not fixing — past audits are point-in-time records.
- **`themesets/screen-settings-shake.jsx`** and the other shake-related design mockups remain in `themesets/`. Cleaning them up is design-asset work, not part of feature removal.
- **`ScreenshotCapture` is structured as a Hilt-injected singleton with an Activity-passing API.** The cleanup design is fine; it just becomes dead code post-removal so we delete the whole class rather than redesign.

## Phase 2 — Implementation plan

- **Branch:** `chore/remove-shake-to-report` worktree off latest `origin/main` (local main is 7 behind; the worktree pulls origin/main, untouched WIP on the medication test stays in the working tree of `main`).
- **Commits:** Squash-merge as a single coherent removal. No `[skip ci]` in the message.
- **Verification:** `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` locally before push; CI is the final gate (Android, web/parity, auto-merge).
- **Auto-merge:** `gh pr create` then `gh pr merge --auto --squash`.
- **Teardown:** After merge, `git worktree remove` + `git branch -d` in the same session per CLAUDE.md repo conventions.

## Phase 3 — Bundle summary

To be appended after merge with PR number, CI outcome, and any surprises.
