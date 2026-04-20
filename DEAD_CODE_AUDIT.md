# Dead Code & Abandoned Work Audit — 2026-04-17

---

## Resolution Addendum — 2026-04-18/19

Items resolved or changed since original audit. Original findings are preserved below.

| Finding | Original severity | Status | Notes |
|---------|------------------|--------|-------|
| README/CLAUDE.md advertise three-tier pricing; `UserTier` only has two tiers | CRITICAL | ✅ Fixed | Three-tier pricing consolidated to two-tier (Free/Pro) in CHANGELOG and all docs. `UserTier` now matches marketed model. |
| DB version stale across CLAUDE.md, ARCHITECTURE.md, DATA_INTEGRITY_AUDIT.md | — | ✅ Fixed | All docs now reflect Room version 50 (see docs refresh PR). |

Items **not yet resolved** (carry-forwards from original audit):
- `ProUpgradePrompt.kt` advertising Collaboration and Google Drive Backup features that have no backing implementation (CRITICAL — pre-launch trust risk).
- `GoogleDriveService` not injected anywhere (CRITICAL).
- ND-mode sub-toggles persisted but never read (HIGH).
- Four repositories never injected (`NlpShortcutRepository`, `SavedFilterRepository`, `ProjectTemplateRepository`, `HabitTemplateRepository`) (MEDIUM).

---



## Summary

Two sharply different pictures emerge.

**Mechanically, the codebase is very clean.** Almost no TODOs, no commented-out code blocks, no unreachable branches, no unused dependencies, no scratch files, no obvious dead button handlers. The Google Calendar "Sync Now" concern from the prior audit is fixed. The `IntegrationSource.CALENDAR` concern is also resolved. `CalendarSyncService.getTodayUpcomingEvents()` is now consumed by the Morning Check-In screen.

**Strategically, there is a real pre-launch trust problem.** The paid-tier upgrade sheet (`ProUpgradePrompt`) markets **Collaboration** and **Google Drive Backup** — neither of which has any backing implementation. `GoogleDriveService` exists as a class but is not injected by anyone; there is no collaboration code anywhere. Several Pro constants (`COLLABORATION`, `INTEGRATIONS`, `ANALYTICS_BASIC/FULL/CORRELATIONS`, `DRIVE_BACKUP`, `TIME_TRACKING`, `AI_WEEKLY_INSIGHTS`) are defined and routed to `UserTier.PRO` but never enforced via `hasAccess(...)`. The README / CLAUDE.md advertise a three-tier Free / Pro / Premium pricing model, but `UserTier` only has two tiers — so every "Premium-only" claim is at best miswired and at worst a refund risk.

Meanwhile, an entire category of UX toggles — the ADHD-Mode sub-settings and `ParalysisBreaker` — persist state to DataStore and write-cascade on mode toggle, but the domain layer never reads those flags or calls `ParalysisBreaker`. Users flipping these switches see no behavioral change.

Four repositories (`NlpShortcutRepository`, `SavedFilterRepository`, `ProjectTemplateRepository`, `HabitTemplateRepository`) are defined but never injected — the corresponding CLAUDE.md features (NLP shortcuts, saved filters, project/habit templates) are either unwired or handled entirely via `TaskTemplateRepository`. One abandoned Expo prototype (`mobile/`) sits in the repo, unreferenced by docs or CI, left over from an ARCHITECTURE.md plan that pivoted to native Kotlin.

**Severity totals: 3 CRITICAL · 14 HIGH · 9 MEDIUM · 5 LOW.**

Fix the three CRITICAL items (Collaboration / Drive Backup promises + tier mismatch) and the HIGH ND-mode dead toggles before shipping.


## Severity legend
- **CRITICAL** — UI promises a feature that doesn't work (user-visible)
- **HIGH** — feature partially works and will confuse users
- **MEDIUM** — unused code clutters codebase; no user impact
- **LOW** — commented-out code, stale imports, etc.

## 1. UI elements with no backing logic

Narrow button-handler audit (are onClicks non-empty? do Sync Now buttons actually sync?) returned **0 issues**. All examined handlers route into real logic:

- `ui/screens/settings/sections/GoogleCalendarSection.kt:255` — "Sync Now" correctly calls `calendarSyncRepository.syncNow()` which invokes the API and updates the last-sync timestamp. The prior-audit concern is resolved.
- `ui/screens/settings/sections/AccountSyncSection.kt:62` — "Sync Now" calls `syncService.fullSync()`.
- `ui/screens/settings/sections/BackupExportSection.kt:16–17` — Export JSON / Export CSV call `DataExporter` paths.
- `ui/screens/settings/sections/AboutSection.kt:42` — "Refresh Widgets" calls `WidgetUpdateManager.updateAllWidgets()`.
- CheckIn / Review / Balance / Mood / Extract screens all have wired ViewModels.

**But** the *deeper* UI-trust failures in section 2 (toggles that persist but are never read, Pro-upgrade cards advertising non-existent features) are effectively category-1 issues from the user's point of view — they're the user-visible trust killers the audit warns about. Cross-referenced there and included in the prioritized action list below.

**CRITICAL cross-refs:**
- `ui/components/ProUpgradePrompt.kt:188` — **"Collaboration — Share projects and work with your team"** shown in upgrade sheet; no collaboration code exists. User pays and gets nothing.
- `ui/components/ProUpgradePrompt.kt:189` — **"Google Drive Backup — Back up your data to Google Drive"** shown in upgrade sheet; `GoogleDriveService` is not injected anywhere. User pays and gets nothing.
- `ui/screens/settings/BrainModeScreen.kt` toggles (Paralysis Breakers, Task Decomposition, Focus Guard, Body Doubling, Streak Celebrations, Completion Animations) — toggles persist state but the state is never read by feature code, and `ParalysisBreaker` has zero call sites. User flips switches and nothing changes.
- README.md advertises a **three-tier** Free/Pro/Premium pricing model; `UserTier` enum only has `FREE | PRO`, so all "Premium-only" claims (AI briefing, planner, integrations, collaboration, Drive backup) are either ungated or dead. If App Store marketing mirrors README, this is a potential refund/review risk.

## 2. Half-built features behind toggles

### Pro-gate constants that are never enforced (dead gates)
These constants are defined in `ProFeatureGate.kt:70–93`, mapped to `UserTier.PRO` via `requiredTier()`, but **never** passed to `hasAccess(...)` anywhere in the codebase. Users can't unlock what's never locked.

- `ProFeatureGate.COLLABORATION` — **CRITICAL**. Advertised in `ProUpgradePrompt.kt:188` ("Share projects and work with your team") but no collaboration code exists anywhere. Upgrading unlocks nothing.
- `ProFeatureGate.INTEGRATIONS` — **CRITICAL**. Declared but never referenced, including in the upgrade prompt. Gmail/Slack/Calendar integrations advertised in CLAUDE.md are ungated.
- `ProFeatureGate.DRIVE_BACKUP` — **CRITICAL**. Advertised in `ProUpgradePrompt.kt:189` but `GoogleDriveService` (`data/remote/GoogleDriveService.kt`) is never injected, never referenced — the feature is not wired into the app. Pro users will pay and find no Drive backup UI.
- `ProFeatureGate.ANALYTICS_BASIC`, `ANALYTICS_FULL`, `ANALYTICS_CORRELATIONS` — **HIGH**. Defined but no screen ever calls `hasAccess(ANALYTICS_*)`. Analytics is either fully open (Free can see it) or fully closed depending on how the screens actually gate themselves.
- `ProFeatureGate.TIME_TRACKING` — **HIGH**. Defined but never checked.
- `ProFeatureGate.AI_WEEKLY_INSIGHTS` — **MEDIUM**. Defined but never referenced.

### ND-mode sub-toggles persisted but never consulted
`NdPreferences` exposes these booleans and `NdPreferencesDataStore.setAdhdMode(enabled)` cascades them to DataStore, but no feature code ever reads them:

- `taskDecompositionEnabled` (`NdPreferences.kt:26`) — **HIGH**. Nothing in domain/UI guards on it.
- `focusGuardEnabled` (`NdPreferences.kt:27`) — **HIGH**. Same.
- `bodyDoublingEnabled` (`NdPreferences.kt:28`) — **HIGH**. Same.
- `completionAnimationsEnabled`, `streakCelebrationsEnabled`, `showProgressBarsEnabled`, `forgivenessStreaksEnabled` — **MEDIUM**. Written by `setAdhdMode`, never read.

Turning on "ADHD Mode" in settings changes nothing at runtime.

### Paralysis Breakers: class exists, zero call sites
- `ParalysisBreaker` object at `domain/usecase/ParalysisBreaker.kt:10` — **HIGH**. Checks `ndPrefs.paralysisBreakersEnabled` internally, but `ParalysisBreaker.*` is never invoked from any ViewModel, Worker, or Repository. The toggle in `BrainModeScreen` / `FocusReleaseSubSettings` persists a value nothing reads downstream.

### Repositories defined but never injected
These classes exist with DAOs, but no ViewModel / Worker / other repository depends on them:

- `NlpShortcutRepository` — **HIGH**. CLAUDE.md advertises NLP shortcuts (`/templatename`) but the repo is dead.
- `SavedFilterRepository` — **HIGH**. CLAUDE.md advertises saved filter presets.
- `ProjectTemplateRepository` — **HIGH**. CLAUDE.md advertises project templates. Templates UI only uses `TaskTemplateRepository`.
- `HabitTemplateRepository` — **HIGH**. Same story for habit templates.

The only code touching these DAOs is `SettingsViewModel.kt:1324–1325` where `habitTemplateDao().deleteAll()` / `projectTemplateDao().deleteAll()` are called for "Clear all data". That's it.

### Two-tier enforcement vs three-tier marketing (HIGH)
`UserTier` is `FREE | PRO` in `ProFeatureGate.kt:46–53`, but the README and CLAUDE.md advertise **three tiers** (Free $0 / Pro $3.99 / Premium $7.99) with Premium-only features (briefing, planner, integrations, Drive backup, collaboration). The codebase has no `UserTier.PREMIUM`; Premium-gated marketing features are either untiered or dead.

## 3. TODO / FIXME / HACK comments in production paths

Only genuine debt markers found in production code (tests excluded):

- `app/src/main/java/com/averycorp/prismtask/ui/screens/timer/TimerViewModel.kt:156` — TODO: migrate countdown to `PomodoroTimerService`-style foreground service to survive backgrounding when ViewModel is cleared. **Category: blocks-launch** (HIGH). A user starting a timer and switching apps today will lose their timer state.
- `app/src/main/java/com/averycorp/prismtask/domain/usecase/ConversationTaskExtractor.kt:52` — "TODO" appears in a comment describing the *pattern match data* (the extractor detects TODO markers in chat text). Not a code debt marker. **Category: don't-fix**.

Totals: 1 blocks-launch, 0 should-fix-this-week, 0 post-launch-ok, 1 don't-fix. Backend has **zero** markers. Codebase is unusually clean on this dimension.

## 4. Unused public APIs / dead code

### Unused repositories (entire class has no injection site)
- `data/repository/NlpShortcutRepository.kt` — **HIGH**. No `@Inject` consumers.
- `data/repository/SavedFilterRepository.kt` — **HIGH**. No consumers.
- `data/repository/ProjectTemplateRepository.kt` — **HIGH**. No consumers. Templates UI only uses `TaskTemplateRepository`.
- `data/repository/HabitTemplateRepository.kt` — **HIGH**. No consumers.

(These are also flagged in §2 — CLAUDE.md markets the backing features but the wiring is absent.)

### Unused DAO methods
- `FocusReleaseLogDao.getRecentLogs()` (`data/local/dao/FocusReleaseLogDao.kt:14`) — MEDIUM.
- `FocusReleaseLogDao.countByEventType()` (`FocusReleaseLogDao.kt:17`) — MEDIUM.
- `FocusReleaseLogDao.deleteOlderThan()` (`FocusReleaseLogDao.kt:20`) — MEDIUM.
- `SchoolworkDao.getLogForDate()` / `getLogForDateOnce()` / `insertLog()` / `updateLog()` (`SchoolworkDao.kt:117,120,123,126`) — MEDIUM. All four only operate on the legacy `StudyLogEntity` (see §6 / §8).

### Unused domain use-case (never invoked)
- `domain/usecase/ParalysisBreaker.kt:10` — **HIGH**. Object defined with logic, gated by `ndPrefs.paralysisBreakersEnabled`, but no ViewModel / Worker / Repository calls `ParalysisBreaker.*`. The paralysis-breakers toggle is purely cosmetic.

### Verified previously-suspicious items (now NOT dead)
- `CalendarSyncRepository.getTodayUpcomingEvents()` — **IN USE** by `MorningCheckInViewModel.kt:182`.
- Backend `IntegrationSource.CALENDAR` — **IN USE** by `backend/app/services/integrations/calendar_integration.py` and `backend/app/routers/calendar.py` (prior-audit concern resolved).

### Unused preference flags
See §7 — several `NdPreferences` flags are written by `setAdhdMode` / `setCalmMode` but never read.

Note: Hilt DI / NavGraph / Firestore reflection may hide usages. All findings above were cross-checked against `@Inject` constructor parameters and `NavGraph.kt` route tables. Verify before deletion.

## 5. Unreachable code paths

No instances found. Specifically scanned for:
- `if (true)` / `if (false)` literals — none
- `TODO()` / `NotImplementedError` in production — none
- Obvious code after unconditional `return` / `throw` — none surfaced
- Kotlin compiler would flag unreachable branches as warnings; none observed in the existing codebase.

This is the cleanest dimension of the codebase.

## 6. Abandoned experiments

### `mobile/` Expo React Native app — **HIGH, abandoned prototype**
- `/home/user/prismTask/mobile/` — a full Expo Router 4 / React Native 0.76 project (`mobile/package.json` name: `prismtask-mobile`, version 1.0.0) with `app/(auth)`, `app/(tabs)`, `goal/`, `overdue/`, `project/`, `today/`, `upcoming/` screens and `services/api.ts` + `services/auth.ts`.
- **Not referenced anywhere** in `README.md`, `ARCHITECTURE.md`, `CLAUDE.md`, or GitHub Actions (`.github/workflows/` has android-ci, backend-ci, web-ci, release, auto-merge — **no mobile-ci**).
- `ARCHITECTURE.md:549–556` contains an unchecked "Phase 2: Mobile Foundation (Week 2) — [ ] Expo project init" checklist that corresponds to this directory. Implementation plan was abandoned after Phase 2 in favor of the native Android Kotlin/Compose app, but the scaffolding was never deleted.
- Appears unused, verify before removal — may still be intended for iOS future.

### Legacy entity kept for migration compatibility — **MEDIUM**
- `data/local/entity/StudyLogEntity.kt` — registered in `PrismTaskDatabase.kt:81` but none of its 5 data fields (`coursePick`, `studyDone`, `assignmentPick`, `assignmentDone`, `startedAt`) are read or written anywhere in app code. Only referenced by `SchoolworkDao` methods that are themselves dead (see §4). Previously flagged as "legacy, kept for migration compatibility" — status unchanged.

### Obsolete ADR doc
- `docs/ADR-calendar-sync.md:20` — says "`IntegrationSource.CALENDAR` enum exists on the backend but is unused." That claim is now **stale** — the enum is actively used by `calendar_integration.py` and `routers/calendar.py`. **LOW** — update or delete the doc line.

### Previously-dead, now-live
- Backend `IntegrationSource.CALENDAR` enum — now live. ✓
- `CalendarSyncService.getTodayUpcomingEvents()` — now live (MorningCheckIn). ✓

### No evidence found of
- `experiments/`, `unused/`, `_deprecated/`, `archive/` (the existing `ui/screens/archive/` is a legitimate task-archive feature), `scratch/`, `wip/`, `draft/` directories.
- Files named `*Old*`, `*WIP*`, `*Scratch*`, `*Temp*` in production source.
- Test files without a corresponding source file.

## 7. Stale feature flags

### ND-mode preference flags — persisted but never consulted (HIGH)
`NdPreferencesDataStore.setAdhdMode(enabled)` at `data/preferences/NdPreferencesDataStore.kt:129–140` cascades these flags to DataStore, and setters exist for each individually, but **no feature code ever reads them**:

| Flag | Declared | Read by feature code? |
|---|---|---|
| `taskDecompositionEnabled` | `NdPreferences.kt:26` | No |
| `focusGuardEnabled` | `NdPreferences.kt:27` | No |
| `bodyDoublingEnabled` | `NdPreferences.kt:28` | No |
| `completionAnimationsEnabled` | `NdPreferences.kt` | No (read only by settings UI, not by animation sites) |
| `streakCelebrationsEnabled` | `NdPreferences.kt` | No (not gated in celebration logic) |
| `showProgressBarsEnabled` | `NdPreferences.kt` | No |
| `forgivenessStreaksEnabled` | `NdPreferences.kt` | Partial — only checked in one place |
| `paralysisBreakersEnabled` | `NdPreferences.kt:50` | Checked only inside `ParalysisBreaker` — which has zero call sites (§4) |

Effectively, "ADHD Mode" and most Focus-&-Release sub-toggles are decorative.

### Two-tier codebase vs three-tier marketing (HIGH / near-CRITICAL)
`UserTier` enum has only `FREE` and `PRO` (`data/billing/UserTier.kt`, referenced in `ProFeatureGate.kt:46–53`). README + CLAUDE.md advertise a third `Premium` tier with exclusive features. No code path distinguishes Premium from Pro.

### Dead `ProFeatureGate` constants
See §2 — `COLLABORATION`, `INTEGRATIONS`, `ANALYTICS_BASIC`, `ANALYTICS_FULL`, `ANALYTICS_CORRELATIONS`, `DRIVE_BACKUP`, `TIME_TRACKING`, `AI_WEEKLY_INSIGHTS` are all defined, mapped to PRO, and never consulted via `hasAccess(...)`.

### No BuildConfig / flavor gates hiding features
- No `BuildConfig.FEATURE_*` booleans found.
- Only `BuildConfig.DEBUG` is used, and only to expose the debug tier override (intentional).

## 8. Dead columns / unused database fields

### Android (Room)
- `StudyLogEntity` — **MEDIUM**. All five data columns (`coursePick`, `studyDone`, `assignmentPick`, `assignmentDone`, `startedAt` at `data/local/entity/StudyLogEntity.kt:15–19`) are never written or read. Entity exists only to preserve the `study_logs` table across migrations.
- `CalendarSyncEntity.syncState` (`data/local/entity/CalendarSyncEntity.kt:46`) — **LOW**. Written in `CalendarSyncRepository.kt:85` but never consulted during push/pull decisions.
- `CalendarSyncEntity.etag` (`CalendarSyncEntity.kt:48`) — **LOW**. Written in `CalendarSyncRepository.kt:86`; never read for conditional-GET optimization or conflict resolution. Adds wire cost with no benefit today.
- `FocusReleaseLogEntity.context` (`data/local/entity/FocusReleaseLogEntity.kt:25`) — **LOW**. Field is written by log insertions but no analytics query reads it. (Compounded by the fact that `FocusReleaseLogDao` has three dead read methods — §4.)
- `UsageLogEntity.taskTitle` (`data/local/entity/UsageLogEntity.kt:18`) — **LOW**. Written on every usage log but queries match on tags/projects rather than title. Candidate for removal if profiler shows cost.

### Backend (PostgreSQL)
- `IntegrationConfig.webhook_token` (`backend/app/models.py:426`, migration `008_add_integration_tables.py:49`) — **MEDIUM**. Column defined, unique index maintained, but **never read or written** anywhere in backend code. Prior-audit concern remains unresolved. Drop in a follow-up migration.

## 9. Commented-out code

No significant findings. Searched for:
- `// OLD CODE:`, `// was:`, `// previously`, `// TODO remove`, `// DELETE ME`, `// REMOVE` — zero hits in production source.
- Multi-line `/* ... */` blocks — all matches are legitimate KDoc/Javadoc headers, not commented-out code.
- Single `//` comments for "Remove the existing completion" / "Remove from current day" etc. are comments describing behavior, not disabled code.

Codebase is clean on this axis.

## 10. Stale dependencies

### Android `app/build.gradle.kts`
- All declared dependencies (Room, Hilt, Compose BOM, Firebase BOM, Google Drive API, Billing, Glance, Reorderable, Gson, etc.) map to code that is actively imported.
- No unused external library imports surfaced.

### Backend `backend/requirements.txt`
- All libs (`anthropic`, `google-api-python-client`, `pypdf`, `apscheduler`, `fastapi`, `sqlalchemy`, `alembic`, etc.) have active usage sites.
- No stale deps found.

### Caveat
- `mobile/package.json` declares a full Expo dependency tree (expo-router, react-native-reanimated, zustand, etc.) that is only used by the **abandoned Expo prototype** (§6). If the `mobile/` directory is deleted, its `package.json` and `node_modules` go with it — no other code depends on those packages.

## Prioritized action list

### Critical (user-facing broken UI — fix before launch)

- **[`ui/components/ProUpgradePrompt.kt:188`]** Remove or implement `ProFeature.COLLABORATION` — the upgrade sheet tells users they are buying "Share projects and work with your team"; no collaboration code exists. Removal ~5 min; implementation is weeks. **5 min to remove.**
- **[`ui/components/ProUpgradePrompt.kt:189` + `data/remote/GoogleDriveService.kt`]** Remove or wire `ProFeature.DRIVE_BACKUP` — advertised in upgrade sheet but `GoogleDriveService` is not injected anywhere. Either hide the prompt item or finish wiring the backup flow. **10 min to remove, ≥1 day to wire.**
- **[`README.md` + `data/billing/UserTier.kt`]** Resolve the three-tier marketing vs two-tier code mismatch. Either add `UserTier.PREMIUM` and gate Premium features (collab, briefing, planner, integrations, Drive backup), or rewrite marketing to match the shipping Free/Pro reality. **20 min doc-only fix, ≥1 day if adding tier.**

### High (fix this week)

- **[`domain/usecase/ParalysisBreaker.kt:10` + `ui/screens/settings/BrainModeScreen.kt`]** Either call `ParalysisBreaker` from the paralysis-detection site (stuck-task suggestions, AI Eisenhower, task list) or hide the settings toggle. Users flipping Paralysis Breakers see no change today. **30 min to hide, ~half day to wire.**
- **[`data/preferences/NdPreferences.kt:26–28,50`]** Remove or consume `taskDecompositionEnabled`, `focusGuardEnabled`, `bodyDoublingEnabled` flags. No feature code currently reads them. **20 min to delete flags + setters; hours to implement features.**
- **[`data/preferences/NdPreferences.kt`]** Audit `completionAnimationsEnabled`, `streakCelebrationsEnabled`, `showProgressBarsEnabled` — ensure each toggle has a consumer in the animation / celebration / progress-bar code paths. **~1 hour of wiring per toggle.**
- **[`domain/usecase/ProFeatureGate.kt:55–65`]** Delete unused PRO-gate constants (`COLLABORATION`, `INTEGRATIONS`, `ANALYTICS_BASIC`, `ANALYTICS_FULL`, `ANALYTICS_CORRELATIONS`, `DRIVE_BACKUP`, `TIME_TRACKING`, `AI_WEEKLY_INSIGHTS`) **only after** confirming the corresponding screens aren't supposed to call them. If analytics / time tracking **should** be Pro-gated, add the `hasAccess(...)` call in the ViewModel / screen instead. **~2 hours to audit + act.**
- **[`data/repository/NlpShortcutRepository.kt`, `SavedFilterRepository.kt`, `ProjectTemplateRepository.kt`, `HabitTemplateRepository.kt`]** Either wire these into their respective screens or delete the files. CLAUDE.md still markets their features. **20 min to delete each; hours to wire.**
- **[`mobile/`]** Decide: delete the abandoned Expo prototype, or commit to a mobile CI workflow. Today it adds ~100 MB of JS dependencies and zero value. **10 min delete + update ARCHITECTURE.md:549.**

### Medium (cleanup debt, post-launch)

- **[`data/local/entity/StudyLogEntity.kt` + `data/local/dao/SchoolworkDao.kt:117–126`]** Retire legacy `study_logs` table via a new Room migration that drops the table; delete the entity and the four dead DAO methods. **~1 hour including migration test.**
- **[`data/local/dao/FocusReleaseLogDao.kt:14,17,20`]** Delete `getRecentLogs`, `countByEventType`, `deleteOlderThan` — they have no callers. Preserve only the insert method. **10 min.**
- **[`backend/app/models.py:426`]** Drop `IntegrationConfig.webhook_token` via a new Alembic migration — column is declared and uniquely-indexed but never read or written. **30 min incl. migration rollback.**
- **[`backend/app/schemas/ai.py`, `backend/app/schemas/analytics.py`]** Confirm whether the ~20 unreferenced Pydantic models are waiting for future endpoints or truly dead; delete if dead. **~1 hour to audit.**
- **[`data/local/entity/CalendarSyncEntity.kt:46,48`]** Either start using `syncState` and `etag` (the latter for conditional-GET) or drop both via a migration. **~30 min.**
- **[`data/local/entity/FocusReleaseLogEntity.kt:25`]** Drop `context` column if analytics never consumes it. **~20 min.**
- **[`docs/ADR-calendar-sync.md:20`]** Update the stale line claiming `IntegrationSource.CALENDAR` is unused. **2 min.**

### Low (janitor work, whenever)

- 1 genuine TODO comment in production (`TimerViewModel.kt:156` — foreground-service migration). Plus 1 TODO comment in pattern-match data (`ConversationTaskExtractor.kt:52`).
- 1 stale `README.md:21` comment: `<!-- TODO: Replace with actual Play Store link once published -->`.
- Unused `ProFeatureGate.COLLABORATION` / `INTEGRATIONS` / `DRIVE_BACKUP` / etc. string constants after removing the gate code.
- 1 obsolete ADR doc line (see medium).
- `ARCHITECTURE.md` Phase 2 checklist items (lines 549–556) still unchecked despite native Android delivery — rewrite or delete the roadmap section.

## Finish

Audit complete. Report at DEAD_CODE_AUDIT.md. Total: 3 CRITICAL, 14 HIGH, 9 MEDIUM, 5 LOW.
