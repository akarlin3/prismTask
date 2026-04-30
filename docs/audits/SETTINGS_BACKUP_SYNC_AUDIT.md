# Settings Backup + Sync Coverage

## Scope

User request: "Also make sure that every setting is backed up and synced."

PrismTask has two persistence-extension surfaces every user-tunable
preference is supposed to flow through:

- **Backup** — JSON full-export / import in `data/export/DataExporter.kt`
  + `DataImporter.kt` (`config` block, ~25 sub-objects).
- **Sync** — Firestore push/pull, mostly via the generic dispatcher
  registered in `app/src/main/java/com/averycorp/prismtask/di/PreferenceSyncModule.kt:48-72`,
  plus two bespoke services for `theme_prefs` and `sort_prefs`.

This audit cross-references every DataStore-backed preference class
against both. It also covers the brand-new
`AdvancedTuningPreferences` (Sections B–E of
`CUSTOMIZABLE_SETTINGS_GAP_AUDIT.md`, plus the
`SelfCareTierDefaults` knob that just shipped in PR
[#999](https://github.com/averycorp/prismTask/pull/999)).

## Phase 1 — Audit

### Item 1 — `AdvancedTuningPreferences` is missing from BOTH backup and sync (RED)

**Findings.**

- `app/src/main/java/com/averycorp/prismtask/data/preferences/AdvancedTuningPreferences.kt`
  was added in commit `3e94bb67` and extended through `2efbb605` (PR
  #997) plus PR #999 (SelfCareTierDefaults). It exposes 24 typed
  config flows + setters (`UrgencyBands`, `UrgencyWindows`,
  `BurnoutWeights`, `ProductivityWeights`, `MoodCorrelationConfig`,
  `RefillUrgencyConfig`, `EnergyPomodoroConfig`,
  `GoodEnoughTimerConfig`, `SuggestionConfig`, `ExtractorConfig`,
  `SmartDefaultsConfig`, `MorningCheckInPromptCutoff`,
  `LifeCategoryCustomKeywords`, `WeeklySummarySchedule`,
  `ReengagementConfig`, `OverloadCheckSchedule`, `BatchUndoConfig`,
  `HabitReminderFallback`, `ApiNetworkConfig`, `WidgetRefreshConfig`,
  `ProductivityWidgetThresholds`, `EditorFieldRows`, `QuickAddRows`,
  `SearchPreview`, `SelfCareTierDefaults`).
- **Backup**: not imported in `DataExporter.kt:109-130` (constructor
  injection list); `config.add(...)` block at `DataExporter.kt:451-463`
  has no `advancedTuning` entry; `DataImporter.kt:917-941`
  (`importConfig` dispatch) does not call any
  `importAdvancedTuningConfig`. **All 24 knobs are lost on
  export/import.**
- **Sync**: not in `PreferenceSyncModule.kt:53-72` (the dispatcher
  set) and has no bespoke `*SyncService`. **Cross-device sync simply
  doesn't fire.**
- This is exactly the gap the user is asking about — none of the
  power-user tuning the past five commits added survives a
  backup/restore cycle or shows up on a second device.

**Risk.** RED — the user's mental model is that everything in
Settings → Advanced Tuning is "their data." A fresh install or
device-swap silently resets all of it.

**Recommendation.** PROCEED — wire all 24 configs through both
backup and sync.

### Item 2 — Generic sync dispatcher is the right tool for the new sync entry (GREEN)

**Findings.**

- `GenericPreferenceSyncService`
  (`data/remote/GenericPreferenceSyncService.kt:49-87`) is the canonical
  fan-out: each `PreferenceSyncSpec` becomes a Firestore doc at
  `/users/{uid}/prefs/{docName}`, with last-write-wins + device-id
  echo suppression + 500ms debounce. New preference files plug in
  via `PreferenceSyncModule`'s `provideSpecs(...)` set.
- The DataStore extension `Context.advancedTuningDataStore`
  (`AdvancedTuningPreferences.kt:19-20`) is `internal` — same
  visibility as every other registered DataStore (e.g.
  `archiveDataStore`, `a11yDataStore`). Adding one line to
  `PreferenceSyncModule.provideSpecs` gives full sync coverage.
- The doc at `PreferenceSyncModule.kt:42-44` warns
  `Renaming a firestoreDocName ... orphans already-synced state`.
  Since this knob has never synced before, picking
  `"advanced_tuning_prefs"` (matching the local DataStore name) is
  free.

**Risk.** GREEN — well-trodden pattern, no migration risk.

**Recommendation.** PROCEED.

### Item 3 — Backup pattern is `gson.toJsonTree` per data class (GREEN)

**Findings.**

- `DataExporter.kt:299-465` builds a `config` JsonObject and adds
  one sub-key per logical preference group. Two shapes exist:
  - **Field-by-field** (e.g. `exportNotificationConfig` at
    `DataExporter.kt:502-542`) — explicit `addProperty(...)` per
    flow. Brittle but human-readable.
  - **Whole-object dump** (e.g. `exportNdConfig` at
    `DataExporter.kt:544-549`) —
    `gson.toJsonTree(ndPreferencesDataStore.ndPreferencesFlow.first())`,
    relies on the data class for forward-compat. Comment at line 545
    explicitly endorses this for new fields.
- `AdvancedTuningPreferences` has 24 small data classes — the
  whole-object dump shape is the right call, mirrored by the
  importer reading each sub-key and routing to the matching setter.
- No DB migration. No external schema. Adding to the export only
  appends a new `config.advancedTuning` object; old backups round-trip
  fine because `getAsJsonObject("advancedTuning") ?: return` no-ops on
  missing input.

**Risk.** GREEN.

**Recommendation.** PROCEED — bundle the export, import, and sync
wiring into one PR (single coherent scope).

### Item 4 — Inventory of every other preferences class (GREEN)

**Findings (matrix).**

| File | Backup | Sync | Notes |
|---|---|---|---|
| A11yPreferences | ✅ ex:476 / im:1359 | ✅ `a11y_prefs` | OK |
| AdvancedTuningPreferences | ❌ | ❌ | **Item 1, RED** |
| ArchivePreferences | ✅ ex:320 / im:1017 | ✅ `archive_prefs` | OK |
| AuthTokenPreferences | ❌ by design | ❌ by design | Per-device tokens |
| BackendSyncPreferences | ❌ by design | ❌ by design | Pull watermark, per-device |
| BuiltInSyncPreferences | ❌ by design | ❌ by design | One-time migration flag |
| CalendarSyncPreferences | ✅ ex:561 / im:1523 | ❌ by design | Per-device Google tokens — backup keeps the user-facing knobs (enable/calendar id/direction); excluded from sync at `PreferenceSyncModule.kt:37`. |
| CoachingPreferences | ✅ ex:597 / im:1589 | ✅ `coaching_prefs` | OK |
| DailyEssentialsPreferences | ✅ ex:551 / im:1500 | ✅ `daily_essentials_prefs` | OK |
| DashboardPreferences | ✅ ex:325 / im:1027 | ✅ `dashboard_prefs` | OK |
| HabitListPreferences | ✅ ex:353 / im:1093 | ✅ `habit_list_prefs` | OK |
| LeisurePreferences | ✅ ex:378 / im:1148 | ✅ `leisure_prefs` | OK |
| MedicationMigrationPreferences | ❌ by design | ❌ by design | One-time migration flag |
| MedicationPreferences | ✅ ex:384 / im:1186 | ✅ `medication_prefs` | OK |
| MorningCheckInPreferences | ✅ ex:557 / im:1515 | ✅ `morning_checkin_prefs` | OK |
| NdPreferencesDataStore | ✅ ex:544 / im:1477 | ✅ `nd_prefs` | OK |
| NotificationPreferences | ✅ ex:502 / im:1424 | ✅ `notification_prefs` | OK |
| OnboardingPreferences | ✅ ex:576 / im:1567 | ✅ `onboarding_prefs` | OK |
| ProStatusPreferences | ❌ by design | ❌ by design | Billing cache, server-authoritative |
| SortPreferences | ✅ ex:609 / im:1604 | ✅ bespoke `SortPreferencesSyncService` | OK |
| TabPreferences | ✅ ex:333 / im:1048 | ✅ `tab_prefs` | OK |
| TaskBehaviorPreferences | ✅ ex:339 / im:1059 | ✅ `task_behavior_prefs` | OK |
| TemplatePreferences | ✅ ex:585 / im:1551 | ✅ `template_prefs` | OK |
| ThemePreferences | ✅ ex:303 / im:949 | ✅ bespoke `ThemePreferencesSyncService` | OK |
| TimerPreferences | ✅ ex:488 / im:1387 | ✅ `timer_prefs` | OK |
| UserPreferencesDataStore | ✅ ex:391 / im:1206 | ✅ `user_prefs` | OK |
| VoicePreferences | ✅ ex:482 / im:1373 | ✅ `voice_prefs` | OK |

Plus three non-`/preferences/` DataStores discovered during the sweep:

- `notifications/ReengagementWorker.kt:32` — `reengagement_prefs`,
  transient nudge cooldown state (not user-facing settings).
- `widget/WidgetConfigDataStore.kt:31` — `widget_config`, per-widget
  instance configuration.
- `widget/TimerStateDataStore.kt:18` — `timer_widget_state`, transient
  widget runtime state.

**Risk.** GREEN for the transient ones (`reengagement_prefs`,
`timer_widget_state`); they are derived runtime state, not settings.
DEFERRED for `widget_config` (see Item 5).

**Recommendation.** STOP-no-work-needed for the 25 already-covered
preference classes (and the 5 by-design exclusions); PROCEED only on
Item 1.

### Item 5 — Widget configuration (DEFERRED)

**Findings.**

- `widget/WidgetConfigDataStore.kt` keeps per-Glance-widget-instance
  configuration. It IS a user setting in spirit (the user picks
  display options when they place a widget).
- Widgets ship disabled today: `WIDGETS_ENABLED = false` per
  `CLAUDE.md` and `app/build.gradle.kts`. Wiring sync for an
  unshipped feature is busy-work; the widget instance IDs are also
  per-device, so cross-device sync would need ID translation work
  similar to `SortPreferencesSyncService`'s project-cloud-id mapper.

**Risk.** DEFERRED.

**Recommendation.** DEFER until widgets re-enable. Note for the next
audit.

---

## Ranked improvements

| # | Improvement | Wall-clock saved / cost | Notes |
|---|---|---|---|
| 1 | Wire `AdvancedTuningPreferences` (24 configs) into `DataExporter` + `DataImporter` + `PreferenceSyncModule`. | High value / low cost | Single coherent scope per the fan-out bundling rule. |

## Anti-patterns flagged (not fixing)

- **Two shapes for export** (`gson.toJsonTree` whole-object vs.
  `addProperty` field-by-field) live side-by-side in
  `DataExporter`. New code should default to whole-object — it is
  forward-compatible with new fields without touching the exporter.
  Re-shaping the existing field-by-field exporters is churn-only and
  out of scope here.
- **`PreferenceSyncModule.provideSpecs` is a manually-maintained
  list.** Every new DataStore needs the author to remember to add a
  line. Consider a multibinding pattern where the preferences class
  itself contributes its `PreferenceSyncSpec` — would have caught
  this gap automatically. Out of scope here.

---

## Phase 3 — Bundle summary

_Filled in after Phase 2 PR merges._

## Phase 4 — Claude Chat handoff

_Emitted after Phase 3._
