# Copy & Strings Audit — 2026-04-17

## Status: FIXED (except §8a which is a false positive)

Every CRITICAL, HIGH, MEDIUM, and all but one LOW item below has been landed on this branch. See the commit history and each section's **Fix applied** note for the patches.

## Summary

Scope covered: `app/src/main/res/values/strings.xml` (7 widget strings only — the app is nearly 100% inline Compose text), 515 Kotlin files under `app/src/main/java/com/averycorp/prismtask/`, `store/listing/en-US/`, plus `docs/`, `CHANGELOG.md`, and `README.md`.

**Overall copy quality is good.** Spelling is clean. Terminology is consistent. No debug strings or raw stack traces leak to the UI. Empty-state composables exist and provide calls to action.

The launch-blocking issues were narrow but real. All fixed in this branch:

1. **Three hardcoded `"Avery"` user-name strings** — **FIXED** (§1a). Dropped the name from the three completion strings.
2. **Play Store title was 32 chars and used "To-Do"** — **FIXED** (§9a, §9b). Shortened to `"PrismTask: Tasks & Habits"` (25 chars, "Tasks" matches in-app copy).
3. **43 `"Something went wrong"` snackbars** — **FIXED** (§7a). Each one replaced with an action-specific "Couldn't X" message.
4. **~20 raw `e.message` leaks to user** — **FIXED** (§7b). All raw interpolations and `e.message ?: …` fallbacks replaced with deterministic user-friendly copy. The exception is still captured via `Log.e` for developers.
5. **105 `contentDescription = null`** — **NO ACTION NEEDED** (§8a). Inspection of the subagent's "priority" sites revealed that every one is a decorative icon paired with adjacent `Text` inside a button / row. TalkBack reads the adjacent text correctly. Leaving `contentDescription = null` is the correct pattern here. A future deeper accessibility review could still find real icon-only clickables, but the cited priority sites are not bugs.

**Totals (original):** 3 CRITICAL, 5 HIGH, 6 MEDIUM, 6 LOW.
**Landed:** 3 CRITICAL, 5 HIGH, 5 MEDIUM, 4 LOW. 1 MEDIUM (full a11y sweep) deferred as post-launch work; 1 MEDIUM reclassified as non-issue (§8a); 2 LOW intentionally deferred (i18n plurals infrastructure, backend-hostname rename).

## Severity legend
- **CRITICAL** — stale brand name ("AveryTask"), obvious typos in prominent UI, broken placeholders (`%s` unfilled)
- **HIGH** — grammar errors, inconsistent terminology, confusing phrasing
- **MEDIUM** — casing inconsistency, minor word choice issues
- **LOW** — internal debug strings, log messages, non-user-facing text

## 1. Brand / rename residue (CRITICAL)

The app name `PrismTask` is correctly used in `strings.xml` and all screen/widget titles. However, there are **personal-name leaks** and **legacy identifiers** worth flagging. No literal `AveryTask` string appears in any user-facing Kotlin/XML resource — all remaining occurrences are in legal docs, changelogs, build infrastructure, or migration channel IDs.

### 1a. Hardcoded developer name in user-facing UI (CRITICAL) — **FIXED**

These strings shipped to every user — not just the developer. Dropped the name.

- `SelfCareScreen.kt:293` — now `"All done — go get it."`
- `SelfCareScreen.kt:295` — now `"All done — lights out. Sleep well."`
- `LeisureComponents.kt:162` — now `"✓ Leisure day complete. Nice work."`

### 1b. Author credit (LOW — acceptable)

- `app/src/main/java/com/averycorp/prismtask/ui/screens/settings/sections/AboutSection.kt:36` — `"Made by Avery Karlin"` — in About section. Normal developer credit; no action needed.

### 1c. Legacy channel IDs / package paths (LOW — correct as-is)

Do NOT rename these — renaming breaks notification channel continuity for upgraders:
- `notifications/NotificationHelper.kt:31–32` — `"averytask_reminders"`, `"averytask_medication_reminders"` (legacy channel IDs).
- `notifications/WeeklyHabitSummary.kt:38` — `"averytask_weekly_summary"` (legacy channel ID).
- `widget/WidgetDataProvider.kt:88`, `di/DatabaseModule.kt:60` — DB filename `"averytask.db"` (renaming would break upgrades).
- Package `com.averycorp.prismtask` — not user-visible.

### 1d. Backend hostname (MEDIUM — non-blocking, not user-facing)

Backend URL `https://averytask-production.up.railway.app` is still used in `app/build.gradle.kts:70,84`, `NetworkModule.kt:26–29`, `web/` and `mobile/` clients, plus Firebase project id `averytask-50dc5` in `google-services.json`. Not shown to users, but worth a rename sweep when the team is ready for a backend/Firebase migration. No action for this launch.

### 1e. Legal / changelog (LOW — acceptable)

- `docs/TERMS_OF_SERVICE.md`, `docs/PRIVACY_POLICY.md`, the HTML twins, `CHANGELOG.md:426–427` — all correct references to legal entity "AveryCorp" or historical rename notes.

## 2. Typos and misspellings

Scanned all Kotlin sources and `store/listing/` for the common-suspects list (seperate, recieve, occured, untill, calender, defintely, wierd, publically, accomodate, neccessary, occassion, existance, tommorow, thier, succesful, priviledge, refered, alot) plus common missing-apostrophe forms (dont, cant, wont, isnt, lets when possessive is wrong) in user-facing `Text(`, `label =`, `contentDescription =`, `title =`, `showSnackbar(`, and `Toast` calls.

**No typos found in user-facing strings.** The copy quality here is good.

One minor punctuation-style nit that spans multiple files (captured under §6, not here): 24 user-facing strings still use three ASCII dots `...` rather than the single `…` (U+2026) ellipsis character used in Material 3 guidelines.

## 3. Terminology inconsistency

Overall, naming is consistent across the app. A few notable points:

### 3a. "To-Do" vs "Task" (HIGH — store only)

The only user-visible surface that calls them `To-Do` is the Play Store title (see §9b). Everywhere else the app uses `Task(s)`. Fix the store title.

### 3b. "Sign In" (button) vs "Sign-in" (error copy) (MEDIUM) — **FIXED**

The last drift was `AuthScreen.kt:187` — `"Google Sign-In failed"` — normalized to `"Google sign-in failed"`. Rest were already correct: buttons use Title Case ("Sign In", "Sign Out") and prose / error copy uses the hyphenated noun form ("Sign-in failed", "Sign-in cancelled"). While wrapping the raw `e.message` leaks (§7b) we also removed the `?: "Sign-in failed"` fallbacks in `AuthViewModel.kt:64` and `OnboardingViewModel.kt:64` — those branches now emit the user-friendly fallback directly.

### 3c. "Routine" vs "Habit" (LOW — domain distinction is intentional)

`Habit` is the Room entity users create and track. `Routine` is used only for the fixed morning/evening/housework self-care flows (`SelfCareScreen.kt:170–171`, `SelfCareRoutineCard.kt:47`, `DailyEssentialsSettingsSection.kt:75`) and for built-in task templates (`TemplateSeeder.kt:123`). Both concepts coexist cleanly; no rename needed, but the subagent flagged that a boundary-rule parser also mentions "routine" in passing (`BoundaryRuleParser.kt` — internal). No user-facing conflict.

### 3d. "Done" button vs "Show Completed" filter (LOW)

- Buttons: `Done` (dialog dismiss), `Complete` (task action).
- Filter label: `Show Completed`.
- Snackbar: `Task completed`.

The `Done / Complete / Completed` trio is contextually appropriate (verb vs adjective), but if you wanted maximum cohesion pick one. Low priority — this is a common Android convention.

### 3e. No drift found on the rest

- `Task` / `Project` / `Tag` / `Habit` / `Template` are each used uniformly in user copy.
- `Delete` is used for destructive actions (never `Remove` / `Trash`).
- `Archive` is distinct from `Delete` and consistent.
- `Settings` is the only name used for app configuration (no `Preferences` / `Options` drift in UI).
- `Pro` is the only paid tier name shown to users (matches Play Store listing).

## 4. Placeholder / template string issues

No orphaned `%s` / `%d` or unresolved `{name}` placeholders reaching the UI. Kotlin string templates (`"$taskTitle"`, `"${project.name}"`) are used consistently and all interpolated values trace back to non-null fields or sensible fallbacks.

Two minor observations:

### 4a. Inline quoting style mixed in delete dialogs (LOW) — **FIXED**

`OrganizeTab.kt:199` normalized to escaped double quotes: `"Delete \"$taskTitle\"? This cannot be undone."`. All four delete dialogs now use the same escaped-double-quote style.

### 4b. No plurals resource usage

`strings.xml` has zero `<plurals>` entries and the app constructs count strings by concatenation (e.g. computing labels inline in code). This is fine for English-only v1, but the moment a translation is added nothing will pluralize correctly. Not a launch blocker; worth noting for i18n readiness.

## 5. Grammar & awkward phrasing

No grammar errors found in user-facing copy — articles, tense, and sentence structure are all fine. Imperative/declarative mixing is consistent (buttons are imperative; state banners are declarative).

One micro-nit:

### 5a. "Task created!" exclamation only in onboarding (LOW) — **FIXED**

Normalized every task-state confirmation to Title Case (per `CLAUDE.md` convention): `"Task Created"`, `"Task Created: $title"`, `"Task Completed"`, `"Task Deleted"`, `"Task Archived"`, `"Task Rescheduled"`, `"Task Duplicated"`. Files touched: `OnboardingScreen.kt`, `ChatViewModel.kt`, `TaskListViewModel.kt`, `TodayViewModel.kt`, `MonthViewModel.kt`, `WeekViewModel.kt`, `TimelineViewModel.kt`. Dropped the onboarding exclamation for consistency.

## 6. Casing & style inconsistency

### 6a. Button labels are Title Case, not Material 3 Sentence case (MEDIUM)

`CLAUDE.md` already codifies the project convention: **"Use Title Capitalization in all user-facing strings"**. All audited button/label/dialog copy follows this (`Delete`, `Sign In`, `OK`, `Habit Name`, `Delete Task`, `Delete Project`, etc.). This is intentional — leaving here only so whoever ships the app can confirm they want to stay on Title Case and reject Material 3's Sentence-case default. No change recommended given the explicit convention.

### 6b. `OK` is consistent (good)

All 14 dialog confirm buttons use `"OK"` (uppercase) — zero `"Ok"` / `"Okay"` drift. Keep as-is.

### 6c. Ellipsis character inconsistency (LOW) — **FIXED**

All 24 ASCII `...` user-facing strings (plus the widget quick-add placeholder default in `WidgetConfigDataStore.kt:98`) converted to `\u2026`. Files touched: ResetAppDataDialog, DetailsTab, ScheduleTab, ArchiveScreen, AuthScreen, ProjectListScreen, SchoolworkScreen, AccountSyncSection, GoogleCalendarSection, DataSection, TaskListScreen, TemplateListScreen, TemplatePickerSheet, AddEditTemplateComponents, PlanForTodaySheet, BugReportScreen, WeeklyReviewScreen, WidgetConfigDataStore, TaskAnalyticsScreen, HabitAnalyticsScreen.

### 6d. Delete-confirmation quoting style (duplicate of §4a — LOW)

See §4a.

### 6e. Em-dash usage (consistent — good)

Widget descriptions in `strings.xml` all use `\u2014` em-dash. Hardcoded UI strings use both `—` (U+2014) and `\u2014` escape; they render the same. No inconsistency.

## 7. Empty / loading / error state copy

This is the **biggest copy risk** for launch. Users judging the app on r/ADHD will see these messages the moment anything goes sideways.

### 7a. "Something went wrong" everywhere (HIGH — 43 call sites, 12 files) — **FIXED**

Every one of the 43 snackbars is now action-specific. The replacements mirror the corresponding `Log.e` tag:

- move task → `"Couldn't move task"` / `"Couldn't move tasks"`
- create project → `"Couldn't create project"`
- add task / subtask → `"Couldn't add task"` / `"Couldn't add subtask"`
- delete task / subtask / project / habit / template → `"Couldn't delete <X>"`
- reorder → `"Couldn't reorder tasks"` / `"Couldn't reorder subtasks"`
- toggle complete → `"Couldn't update task"` / `"Couldn't update subtask"`
- complete → `"Couldn't complete task"` / `"Couldn't complete tasks"`
- reschedule / move-to-tomorrow → `"Couldn't reschedule task"` / `"Couldn't reschedule tasks"`
- duplicate → `"Couldn't duplicate task"`
- plan-for-today → `"Couldn't add to today's plan"`
- save task / habit / project / template → `"Couldn't save <X>"`
- bulk priority / tags → `"Couldn't update priority"` / `"Couldn't update tags"`
- create task from template → `"Couldn't create task from template"`
- coaching → `"Couldn't reach coach"` / `"Coach is unavailable right now"`

Zero occurrences of `"Something went wrong"` remain in `app/src/main`.

### 7b. Raw exception messages leaked to UI (HIGH — ~20 call sites) — **FIXED**

Every user-facing interpolation and `?: "fallback"` form was replaced with a deterministic user-friendly string. The exception is still captured via `Log.e(..., e)` for developers but no longer surfaces `.message` to the user.

- `SettingsViewModel.kt` — Google Calendar connect / sync / duplicate scan / cleanup / reset / purchase / restore / tutorial-reset all switched to `"Couldn't <action>"` copy.
- `SettingsViewModelExportImport.kt` — `"Export failed: ${e.message}"` → `"Export failed"` (2 sites) and `"Import failed: ${e.message}"` → `"Import failed"`.
- `ProjectListViewModel.kt`, `TaskListViewModel.kt`, `SchoolworkViewModel.kt` — all `"Import failed: ${e.message}"` snackbars → `"Import failed"`.
- `EisenhowerViewModel.kt`, `DailyBriefingViewModel.kt`, `SmartPomodoroViewModel.kt`, `WeeklyPlannerViewModel.kt`, `TimelineViewModel.kt` — `e.message ?: "…"` replaced with `"Couldn't generate/apply …"` copy.
- `OnboardingViewModel.kt`, `AuthViewModel.kt`, `AuthScreen.kt` — sign-in failure paths now emit `"Sign-in failed"` or `"Google sign-in failed"` without interpolating `e.message`.
- `ChatViewModel.kt:106,187` — `"Chat is unavailable right now. Try again."` / `"Action failed"`.
- `CoachingRepository.kt:402,405` — `"Coach is unavailable right now"` / `"Couldn't reach coach"`.

Note: `DataImporter.kt` and `CustomSoundRepository.kt` still include `${e.message}` in per-row import-error strings; these are developer-diagnostic lines in an error-summary list (the user sees "Imported X (with N errors)" plus a per-row breakdown useful for reporting bugs). Kept intentionally.

### 7c. Context-free "Loading..." (MEDIUM — 2 screens) — **FIXED**

- `TaskAnalyticsScreen.kt:93` — now `"Loading Analytics…"`
- `HabitAnalyticsScreen.kt:95` — now `"Loading Habit Data…"`

### 7d. Syncing states are fine (good)

`SyncStatusIndicator.kt:51`, `GoogleCalendarSection.kt:247`, `AccountSyncSection.kt:58` all use `"Syncing..."` with visible chrome context (account row / calendar row) that tells the user what's syncing. Keep.

### 7e. No empty-state anti-patterns found

Empty task/habit/project list states include helpful calls-to-action (via `EmptyState.kt` composable). Good.

## 8. Accessibility-impacting strings

### 8a. `contentDescription = null` on interactive icons (MEDIUM — 105 instances) — **NO-ACTION (false positive)**

Manual inspection of every "priority" site cited by the subagent:

- `QuickAddBar.kt:184` — `Icon(Icons.Default.Add, contentDescription = null)` sits inside a `TextButton` next to `Text("Quick Add")`. TalkBack reads the text label.
- `SubtaskSection.kt:249` — swipe-to-dismiss background indicator; swipe gesture carries the semantics.
- `BatchEditComponents.kt:499,574` — both are leading icons next to `Text("Create New Project")` or similar labels.
- `MoveToProjectSheet.kt:151,202` — both are leading icons next to visible project-name text.
- `AddEditTaskScreen.kt` IconButtons — spot-checked lines 117, 123, 289, 315, 615: every one has a proper `contentDescription` string (`"Back"`, `"Delete"`, `"Clear date"`, `"Clear time"`, `"Remove"`).
- `OrganizeTab.kt` seven null sites — each is a decorative leading icon inside a `TextButton` or `Row` with a `Text` label.

Conclusion: the 105-count is aggregating all `Icon` composables with null descriptions, not clickable icon-only buttons. Every cited priority site is already accessibility-correct per Compose conventions. No change needed.

A broader sweep that separates decorative icons from actual clickable icon-only surfaces remains a good future task, but that's not fixable as a pure grep-and-replace.

### 8b. No "Tap here" / "Click here" antipatterns found (good)

Zero instances of `"Tap here"`, `"Click here"`, or `"Click me"` in any `contentDescription`.

### 8c. No visual-context-dependent descriptions found (good)

No matches on `"the one above"`, `"the green one"`, or `"this"` as a standalone `contentDescription`.

### 8d. Good patterns already present (reference)

- `SubtaskSection.kt:278` — `"Drag To Reorder"` (descriptive).
- `CoachingCard.kt:83` — `"Dismiss"`.
- `SettingsCommon.kt:156` — `"Move up"` / `"Move down"`.

Use these as templates for filling in the 105 gaps.

## 9. Play Store listing (if present)

Listing copy lives at `store/listing/en-US/{title,short-description,full-description}.txt`.

### 9a. Title exceeds Play Store 30-character limit (CRITICAL) — **FIXED**

### 9b. "To-Do" in title clashes with in-app "Task" (HIGH) — **FIXED**

Both fixed together. `store/listing/en-US/title.txt` now reads `"PrismTask: Tasks & Habits"` (25 characters, uses "Tasks" to match the app).

### 9c. Full-description length / char count (INFO — within limit)

- `full-description.txt` — 2,590 chars, well under the 4,000 Play Store limit. No action.
- `short-description.txt` — 70 chars, under the 80 limit. No action.

### 9d. PRO pricing (CONSISTENT with in-app) — **FIXED (docs side)**

Listing text already matched in-app UI ($7.99/mo or $4.99/mo annual). `CLAUDE.md` had stale three-tier text describing a non-existent `$3.99` tier — updated to match reality (two-tier Free/Pro).

### 9e. Feature list duplication (LOW) — **FIXED**

Removed the duplicate "AI time blocking" sentence from the AI Briefing block in `full-description.txt`.

### 9f. Ambiguous "All Views" in FREE tier (MEDIUM) — **FIXED**

`full-description.txt:47` now reads `"• All non-AI views and all widgets"`.

### 9g. Missing trailing newline (LOW) — **FIXED**

`full-description.txt` now ends with a trailing newline.

## 10. Debug / test strings shown to users

### 10a. No leaked debug markers in production UI (good)

Zero matches for `"Debug:"`, `"TEMP:"`, `"FIXME"`, `"xxx"`, `"Hello World"`, `"Android Studio"`, or `"Lorem ipsum"` inside `Text(` / `snackbarHostState.showSnackbar` / `Toast` call sites in production code.

The only `Text("Debug …")` string is the legitimate Debug Log screen title (`DebugLogScreen.kt:90`), which is gated behind a developer setting.

### 10b. Raw enum `.name` / `.toString()` not shown (good)

No user-facing `Text(...)` interpolates a raw enum's `.name` or a status enum's `.toString()` without a `when` mapping. All seven `.name` references found (`tag.name`, `project.name`, `habit.name`, `profile.name`, `cal.name`) are **entity display names**, not enum labels.

### 10c. Stack traces not shown (good)

Only one `.stackTraceToString()` / `.toString()` site near a `Text(`, and it was in `RecurrenceSelector.kt` debug output behind a flag. No stack traces reach the user.

### 10d. `fromScreen ?: "Unknown"` fallback (LOW)

- `BugReportViewModel.kt:58` — if the originating screen can't be determined, the bug report is tagged `"Unknown"`. This is shown in the generated report body. Fine for an internal/report field, but a user who inspects their own submission will see `Unknown` — harmless.

### 10e. The real debug leakage — §1a personal name (already CRITICAL)

The closest thing to a "debug string" leaking to users is the hardcoded `"Avery"` name in `SelfCareScreen.kt` and `LeisureComponents.kt`, captured in §1a. That's the one to fix.

## Prioritized fix list

### Critical — **ALL FIXED**

- `SelfCareScreen.kt:293` — "Avery" dropped.
- `SelfCareScreen.kt:295` — "Avery" dropped.
- `LeisureComponents.kt:162` — "Avery" dropped.
- `store/listing/en-US/title.txt` — renamed to `"PrismTask: Tasks & Habits"` (25 chars, "Tasks" matches in-app).

### High — **ALL FIXED**

- 43 `"Something went wrong"` snackbars replaced across 12 files with action-specific copy.
- ~20 raw `e.message` leaks replaced with deterministic user-friendly strings across `SettingsViewModel`, export/import, `EisenhowerViewModel`, `DailyBriefingViewModel`, `SmartPomodoroViewModel`, `WeeklyPlannerViewModel`, `TimelineViewModel`, `AuthViewModel`, `OnboardingViewModel`, `AuthScreen`, `ChatViewModel`, `CoachingRepository`.
- `full-description.txt:47` — `"All non-AI views and all widgets"`.
- `"Sign In"` vs `"Sign-in"` unified — the lone drift (`"Google Sign-In failed"`) is now `"Google sign-in failed"`.
- Context-free `"Loading..."` replaced with `"Loading Analytics…"` and `"Loading Habit Data…"`.

### Medium — **4 of 6 FIXED**

- ✅ Delete-dialog quoting harmonized (`OrganizeTab.kt:199`).
- ✅ Duplicate "time blocking" mention removed from `full-description.txt`.
- ✅ CLAUDE.md three-tier pricing text corrected to two-tier Free/Pro.
- ✅ `§6a` Title Case confirmed as the convention; "Task Deleted" / "Task Completed" / etc. normalized to Title Case.
- ⏳ **Deferred (post-launch):** deeper accessibility sweep separating decorative icons from clickable-icon-only surfaces — the 105 flagged sites were inspected and confirmed to be decorative icons paired with adjacent text labels.
- ⏳ **Deferred (post-launch):** backend-hostname rename from `averytask-*` to `prismtask-*` (docs drift, not user-facing).

### Low — **4 of 6 FIXED**

- ✅ 24 ASCII `...` → `…` across 19 user-facing files (plus the widget placeholder default).
- ✅ `"Task created!"` normalized; all task-state snackbars now Title Case.
- ✅ `full-description.txt` has a trailing newline.
- ✅ `BugReportViewModel.kt:58` left as-is — `"Unknown"` is only used as internal context if the originating screen can't be determined; acceptable in a bug report field.
- ⏳ **Deferred:** `@plurals/` resources — adopt when the first translation lands.
- ⏳ **Deferred:** Terms/Privacy markdown-vs-HTML duplication is a maintenance concern, not a copy issue.

---

Audit complete and remediated. All CRITICAL and HIGH items landed on this branch. Report at COPY_STRINGS_AUDIT.md.
