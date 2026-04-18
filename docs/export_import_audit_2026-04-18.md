# Export/Import Completeness Audit — 2026-04-18

## Scope

Audits `DataExporter` / `DataImporter` against every user-state location in the app.
Goal: an exported JSON file is a **true device-portable backup** — export → wipe →
import produces a byte-identical user experience (minus cloud auth session).

This is a **local-only** format. Cloud/auth state (Firestore, Firebase auth, Backend
JWT, Play Billing cache) is intentionally excluded.

**Current export format:** v4 (pre-audit). Bumped to **v5** by the accompanying fix.

---

## Legend

| Icon | Meaning |
|------|---------|
| ✅ | Covered in both export and import |
| 🔶 | Partial — data loss, missing fields, or import-only skeleton |
| ❌ | Missing entirely |
| 🚫 | **Intentionally excluded** (cloud sync, secrets, derived-from-server) |

"Fix after" column shows the state after applying the audit's code changes.

---

## Section A — Room @Entity coverage

| Entity | Table | Before | After | Notes |
|--------|-------|--------|-------|-------|
| `TaskEntity` | `tasks` | ✅ | ✅ | Gson-tree; `_oldId`, `_parentOldId`, `_projectName`, `_tagNames` helpers |
| `ProjectEntity` | `projects` | ✅ | ✅ | |
| `TagEntity` | `tags` | ✅ | ✅ | |
| `TaskTagCrossRef` | `task_tags` | ✅ | ✅ | via `_tagNames` helper on tasks |
| `TaskCompletionEntity` | `task_completions` | ✅ | ✅ | Derived (history) — now behind `includeDerivedData` |
| `HabitEntity` | `habits` | ✅ | ✅ | |
| `HabitCompletionEntity` | `habit_completions` | ✅ | ✅ | Derived |
| `HabitLogEntity` | `habit_logs` | ✅ | ✅ | Derived |
| `AttachmentEntity` | `attachments` | 🔶 | 🔶 | Row metadata exported since v4; file bytes under `filesDir/attachments/` are **NOT** bundled — known limitation |
| `LeisureLogEntity` | `leisure_logs` | ✅ | ✅ | |
| `SelfCareLogEntity` | `self_care_logs` | ✅ | ✅ | |
| `SelfCareStepEntity` | `self_care_steps` | ✅ | ✅ | |
| `CourseEntity` | `courses` | ✅ | ✅ | |
| `AssignmentEntity` | `assignments` | ✅ | ✅ | |
| `CourseCompletionEntity` | `course_completions` | ✅ | ✅ | Derived |
| `StudyLogEntity` | `study_logs` | ✅ | ✅ | v4 addition |
| `NotificationProfileEntity` | `reminder_profiles` | ✅ | ✅ | Built-ins filtered on export (re-seeded on install) |
| `CustomSoundEntity` | `custom_sounds` | 🔶 | 🔶 | Row + URI exported; referenced audio files under `filesDir/notification_sounds/` are **NOT** bundled — known limitation |
| `BoundaryRuleEntity` | `boundary_rules` | ✅ | ✅ | v4 addition |
| `CheckInLogEntity` | `check_in_logs` | ✅ | ✅ | v4 addition |
| `MoodEnergyLogEntity` | `mood_energy_logs` | ✅ | ✅ | v4 addition |
| `WeeklyReviewEntity` | `weekly_reviews` | ✅ | ✅ | v4 addition |
| `MedicationRefillEntity` | `medication_refills` | ✅ | ✅ | v4 addition |
| `FocusReleaseLogEntity` | `focus_release_logs` | ✅ | ✅ | v4 addition; `_taskOldId` helper |
| `NlpShortcutEntity` | `nlp_shortcuts` | ✅ | ✅ | v4 addition |
| `SavedFilterEntity` | `saved_filters` | ✅ | ✅ | v4 addition |
| `TaskTemplateEntity` | `task_templates` | ✅ | ✅ | v4 addition; `_projectName` helper |
| `HabitTemplateEntity` | `habit_templates` | ✅ | ✅ | v4 addition |
| `ProjectTemplateEntity` | `project_templates` | ✅ | ✅ | v4 addition |
| `DailyEssentialSlotCompletionEntity` | `daily_essential_slot_completions` | ❌ | 🔶 | **Export added in v5** — materialized medication slot completions. Import path lands in v6 (will upsert via DAO composite uniqueness `(date, slot_key)`). |
| `UsageLogEntity` | `usage_logs` | ❌ | 🔶 | **Export added in v5** — derived analytics event log. Import path lands in v6 (usage log is append-only; import will require a new DAO bulk-insert). |
| `CalendarSyncEntity` | `calendar_sync` | ❌ | 🔶 | **Export added in v5** — local task ↔ GCal event mapping. Import path lands in v6; the mapping depends on the post-restore `tasks.id` remap, which requires threading the old-to-new id map through to the calendar-sync importer. |
| `SyncMetadataEntity` | `sync_metadata` | 🚫 | 🚫 | Intentionally excluded — Firestore pending-action queue; would re-push stale writes on restore |

---

## Section B — DataStore preferences coverage

### Covered before audit

| Preference | Before | After | Notes |
|------------|--------|-------|-------|
| `ThemePreferences` | 🔶 | ✅ | v4 exported core 11 keys; v5 now also exports `recentCustomColors`, `prefPrismTheme` |
| `ArchivePreferences` | ✅ | ✅ | |
| `DashboardPreferences` | 🔶 | ✅ | v4 missed `collapsedSections`; now included |
| `TabPreferences` | ✅ | ✅ | |
| `TaskBehaviorPreferences` | ✅ | ✅ | |
| `HabitListPreferences` | 🔶 | ✅ | v4 missed `streakMaxMissedDays`, `todaySkipAfterCompleteDays`, `todaySkipBeforeScheduleDays`; now included |
| `LeisurePreferences` | 🔶 | ✅ | v4 exported only `customMusicActivities`/`customFlexActivities`; v5 also ships per-slot enabled/label/emoji/duration/grid-columns/auto-complete/hidden-builtins + custom-sections JSON |
| `MedicationPreferences` | ✅ | ✅ | |
| `UserPreferencesDataStore` | 🔶 | ✅ | v4 missed `taskMenuActionsJson`, `taskCardDisplayJson`, `forgiveness*`, `uiComplexityTier`, `tierOnboardingShown`; all now exported |

### Missing before audit — added in v5

| Preference | Before | After | Notes |
|------------|--------|-------|-------|
| `A11yPreferences` | ❌ | ✅ | `reduceMotion`, `highContrast`, `largeTouchTargets` |
| `VoicePreferences` | ❌ | ✅ | `voiceInputEnabled`, `voiceFeedbackEnabled`, `continuousModeEnabled` |
| `ShakePreferences` | ❌ | ✅ | `enabled`, `sensitivity` |
| `TimerPreferences` | ❌ | ✅ | All 11 Pomodoro keys (work/break/long-break seconds, sessions-until-long-break, auto-start, available-minutes, focus-preference, buzz-until-dismissed) |
| `NotificationPreferences` | ❌ | ✅ | Full 40+ key surface (per-type enables, importance, default offset, active profile id, category overrides, streak alerts, briefing schedule/tone/sections/read-aloud, collaborator digest, watch sync, badge/toast, habit nag suppression, snooze durations) |
| `NdPreferencesDataStore` | ❌ | ✅ | All 25 ND Mode keys (ADHD/Calm/Focus&Release modes + sub-settings: reduce-animations, muted-palette, quiet-mode, reduce-haptics, soft-contrast, check-in-interval, completion-animations, streak-celebrations, show-progress-bars, forgiveness-streaks, good-enough-timers, anti-rework, cooling-off, revision counter, ship-it celebrations) |
| `DailyEssentialsPreferences` | ❌ | ✅ | `houseworkHabitId`, `schoolworkHabitId`, `hasSeenHint` |
| `MorningCheckInPreferences` | ❌ | ✅ | `featureEnabled` only; `bannerDismissedDate` classified as derived/session |
| `SortPreferences` | ❌ | ✅ | Per-screen sort mode + direction (dynamic keys) — dumped as flat JSON map |
| `CalendarSyncPreferences` | ❌ | ✅ | `calendarSyncEnabled`, `syncCalendarId`, `syncDirection`, `showCalendarEvents`, `selectedDisplayCalendarIds`, `syncFrequency`, `syncCompletedTasks` — user config; `lastSyncTimestamp` excluded (derived, re-computed) |
| `OnboardingPreferences` | ❌ | 🔶 | Exported in v5 but not imported: `OnboardingPreferences.setOnboardingCompleted()` stamps the current time, so importing the historical timestamp would require a new API. Low priority — a restored user sees onboarding once; acceptable UX. Full round-trip in v6. |
| `TemplatePreferences` | ❌ | ✅ | `templatesSeeded`, `templatesFirstSyncDone` — derived (seeding orchestration) |
| `CoachingPreferences` | 🔶 | ✅ | User-facing `energyLevel` + dismissal dates round-trip; `aiBreakdownCount` (free-tier daily quota) is derived |

### Intentionally excluded

| Preference | Reason |
|------------|--------|
| `AuthTokenPreferences` | 🚫 — Contains backend access/refresh JWTs. **Do not export secrets.** |
| `ProStatusPreferences` | 🚫 — Cached subscription tier from Play Billing / backend. Restoring stale values would let a downgraded user keep Pro until the next verify. Re-fetched on sign-in. |
| `BackendSyncPreferences` | 🚫 — `lastSyncAt` for incremental pull. Restoring would trigger partial sync (missed deltas). |
| `CalendarSyncPreferences.LAST_SYNC_TIMESTAMP` | 🚫 — Derived watermark; restoring would suppress real changes. |
| Firebase Auth user state | 🚫 — Sign-in is a runtime concern, not backup. |
| `NotificationPreferences.PREVIOUS_IMPORTANCE` | 🚫 — Internal channel-rename bookkeeping. |

---

## Section C — Other user-state locations

### Widget DataStores

| Location | Before | After | Notes |
|----------|--------|-------|-------|
| `widget/WidgetConfigDataStore` | ❌ | ✅ (derived) | Per-widgetId configs: Today (show_progress, show_task_list, show_habit_summary, max_tasks, show_overdue_badge, bg_opacity), Habit Streak (habit_ids, show_streak_count, layout_grid), Quick-Add (placeholder, default_project) — serialized as a flat map under `derived.widgetConfig` |
| `widget/TimerStateDataStore` | ❌ | 🚫 | Running-timer transient state (is_running, remaining_seconds). Not user-portable — excluded |

### File system

| Location | Coverage | Notes |
|----------|----------|-------|
| `filesDir/attachments/` | ❌ | Task attachment files. Entity rows exported, binary files NOT. Documented limitation — users must re-attach after restore. |
| `filesDir/notification_sounds/` | ❌ | User-uploaded sound files. Entity rows exported, file bytes NOT. Documented limitation. |
| `cacheDir/screenshots/` | 🚫 | Transient bug-report captures |
| `cacheDir/debug_logs/` | 🚫 | Transient |

### Legacy SharedPreferences

None found.

---

## New in v5 — schema + import semantics

### 1. Schema versioning & metadata

Every export now carries:

```json
{
  "version": 5,
  "schemaVersion": 5,
  "exportedAt": 1745000000000,
  "exportedAtIso": "2026-04-18T12:00:00Z",
  "appVersion": "0.7.1",
  "deviceModel": "…",
  "includeDerivedData": true,
  "tasks": […],
  "projects": […],
  "config": {…},
  "derived": {
    "taskCompletions": […],
    "habitCompletions": […],
    "habitLogs": […],
    "courseCompletions": […],
    "usageLogs": […],
    "calendarSync": […],
    "widgetConfig": {…}
  }
}
```

`v3`/`v4` imports continue to work — the top-level layout is additive.

### 2. Core vs derived split

- **Core** (user-authored, top-level keys): `tasks`, `projects`, `tags`, `habits`,
  all templates, all config/preferences.
- **Derived** (nested under `derived`): `taskCompletions`, `habitCompletions`,
  `habitLogs`, `courseCompletions`, `usageLogs`, `calendarSync`, `widgetConfig`,
  `focusReleaseLogs` — opt-out on export (`includeDerivedData=false`) and
  opt-out on import.

For v3/v4 backups, derived collections remain at the top level (legacy layout)
and are still imported unless the user opts out.

### 3. Last-write-wins upsert

Import default is still MERGE, but now compares `updatedAt` on tasks, habits,
projects, and notification profiles. Newer incoming rows overwrite local; older
incoming rows are counted as `duplicatesSkipped`. This matches Firestore sync
semantics and makes "export → tweak locally → import" safe.

### 4. Per-section REPLACE

`ImportMode.REPLACE` now accepts an optional `replaceScope: Set<ReplaceSection>`.
Callers can wipe `TASKS_PROJECTS` while preserving `HABITS_AND_HISTORY`, or
the other way around. Default = all sections (existing behavior).

### 5. Transactional

Every DB write happens inside `database.withTransaction {}` (already true
pre-audit; preserved). Config writes remain outside the transaction (DataStore
has its own atomicity model).

### 6. Orphan handling

Cross-table FK resolution (projects → tasks, habits → completions, courses →
assignments) logs a warning and skips the orphan row rather than aborting.
The `ImportResult.errors` list surfaces dropped rows.

### 7. Streak recomputation

When `includeDerivedData=false` on import, `HabitCompletionEntity` rows from the
export are still imported (they're user history) but streak-state fields on
`HabitEntity` are **not** trusted — they're recomputed from completion history
on next habit fetch. When `includeDerivedData=true`, exported streak values are
trusted verbatim.

### 8. Type-converter safety

New `RoundTripSmokeTest` exercises `RecurrenceRule` JSON, `LifeCategory` enum
strings, `Instant`-like `Long` timestamps, and nullable foreign keys through
the serializer to guarantee Gson converters are exercised.

---

## Known limitations (carry-forward)

1. **Attachment files** and **custom sound files** are referenced by URI in the
   exported JSON but their binary payloads are not bundled. A future v6 could
   zip the JSON + `filesDir/attachments/` + `filesDir/notification_sounds/`
   into a single `.pttask` archive.
2. **Firebase cloud state** (signed-in user, real-time listeners) is not part
   of the backup. Users who sign in after restore pull fresh cloud state.
3. **Running Pomodoro timer state** (`TimerStateDataStore`) is transient.
4. **Play Billing entitlement** is re-verified on next launch; the cached tier
   is deliberately not restored to prevent stale-Pro exploits.

---

## Phase 6 test coverage summary

- `DataExportImportRoundTripTest` — seeds in-memory Room DB with one of every
  core entity + preferences, exports, wipes, imports, asserts equivalence.
- `DataImporterV5Test` — schema version detection, derived-data opt-out, orphan
  skip, last-write-wins collision, per-section REPLACE.
- `EntityJsonMergerTest` — existing; unchanged.

See `app/src/test/java/com/averycorp/prismtask/data/export/` for the files.
