# A2 Post-Merge Audit — 2026-04-22 20:16 UTC-ish

**Verdict:** A2 ready for Phase B. **Post-audit status (same day, 2026-04-22):** all three noted issues fixed in the same branch — `CloudIdOrphanHealer` now covers all 16 v1.4.37+v1.4.38 entity families, three new migration tests (`Migration54To55Test`, `Migration55To56Test`, `Migration56To57Test`) land in `app/src/androidTest`, and `NotificationChannelsInstrumentedTest` now asserts the A2 `prismtask_weekly_task_summary` and `prismtask_weekly_review` channels. `CLAUDE.md` DB version / migration chain refreshed to v57 / `MIGRATION_56_57`. `./gradlew compileDebugKotlin compileDebugAndroidTestKotlin ktlintCheck detekt testDebugUnitTest` all green locally. See the per-checkpoint "Post-audit fix" notes and the new §6 below for specifics.

Original verdict (pre-fix, retained for record): A2 ready for Phase B — 2 non-blocking gaps (migration-test coverage for v54→57, CloudIdOrphanHealer coverage for the 16 new-synced entity families). All 4 merged A2 features landed cleanly and are wired end-to-end. Not-yet-started items remain untouched. No shared-infra regressions found.

- Branch audited: `main` at `23def10b` (v1.4.39, build 683)
- DB version on main: **57** (CLAUDE.md still says 54 — stale)
- 4 A2 merges on main: `f371a08a` (weekly task summary), `050b02b8` (pomodoro+), `c4ba8346` (eisenhower), `87143512` (weekly reviews); plus follow-up CI fixes `4fe83ebf` `daa89278` `09a42e09` `8c4fac69` `a28c35e8` `fc44cbc2` `23def10b`

---

## Checkpoint 1 — Merge Verification

### Weekly Task Summary — ✅ all good

| # | Check | Result |
|---|-------|--------|
| 1 | `WeeklyTaskSummaryWorker` `@HiltWorker` | ✅ `app/src/main/.../notifications/WeeklyTaskSummaryWorker.kt:50-67` |
| 2 | `weeklyTaskSummaryEnabled` default true | ✅ `NotificationPreferences.kt:270-271` (`?: true`) |
| 3 | Channel `prismtask_weekly_task_summary` | ✅ `WeeklyTaskSummaryWorker.kt:109` (no central registrar — lazy per-worker) |
| 4 | Sunday ~19:30 local, 30-min offset | ✅ `WeeklyTaskSummaryWorker.kt:120-122,131-137` |
| 5 | `WeeklyTaskSummaryMigration` one-shot | ✅ `NotificationWorkerScheduler.kt:160-185`, flag `HAS_SEEDED_WEEKLY_TASK_SUMMARY_WORKER` |
| 6 | Settings toggle wired | ✅ `NotificationTypesScreen.kt:39,137` → `SettingsViewModel.setWeeklyTaskSummaryEnabled` |
| 7 | Unit tests | ✅ `app/src/test/.../notifications/WeeklyTaskSummaryWorkerTest.kt` (202 lines, matches merge stat) |
| 8 | Prior habit-summary TODO anchor | ✅ `WeeklyHabitSummaryWorker.kt` comment at lines 55-59 rewritten to reference the new sibling; no dangling TODO |

Minor note: the worker's "still open" count uses `taskDao.getIncompleteTaskCount()` — whole-app incomplete count, not current-week open. Reasonable default, but worth confirming with product.

### Pomodoro+ AI Coaching — ✅ all good

| # | Check | Result |
|---|-------|--------|
| 1 | `PomodoroAICoach` with 3 methods | ✅ `domain/usecase/PomodoroAICoach.kt:26,38,52` |
| 2 | Three pref keys default ON | ✅ `TimerPreferences.kt:41-43,175-182` (default `?: true` on all three getters) |
| 3 | Pre-session wired at session start | ✅ `SmartPomodoroViewModel.kt:338-348,542-571`, 2s `withTimeoutOrNull` cap, modal in `SmartPomodoroScreen.kt:183-216` |
| 4 | Break-time wired | ✅ `SmartPomodoroViewModel.kt:391-411`, rendered at `SmartPomodoroScreen.kt:639,679-697` |
| 5 | Post-session recap wired | ✅ `SmartPomodoroViewModel.kt:435-466`, card at `SmartPomodoroScreen.kt:745` |
| 6 | Settings toggles | ✅ `TimerSection.kt:45-47,181-193` via `FocusTimerScreen.kt:71-73` |
| 7 | Unit tests | ✅ `PomodoroAICoachTest.kt` (120 lines) + `SmartPomodoroViewModelTest.kt` (375 lines, 17 cases per merge commit msg) |
| 8 | Offline silent-skip | ✅ `PomodoroAICoach.runBackendCall` returns `Result.failure` on exception; VM hides pre-session modal on timeout/failure (`SmartPomodoroViewModel.kt:571`) |
| 9 | ≤2 s pre-session gate | ✅ `preSessionCoachingTimeoutMs = 2_000L` (line 348); `withTimeoutOrNull` at line 560 |

Pref-key naming note: backing SharedPreferences keys are `pomodoro_ai_pre_session_coaching` / `..._break_coaching` / `..._recap_coaching`. The spec listed property-style names; the property-level API matches exactly (`preSessionCoachingEnabled`, `breakCoachingEnabled`, `recapCoachingEnabled`). No mismatch.

### Eisenhower Matrix — ✅ all good

| # | Check | Result |
|---|-------|--------|
| 1 | `EisenhowerQuadrant` enum 5 values | ✅ `domain/model/EisenhowerQuadrant.kt:12-17` (Q1..Q4 + UNCLASSIFIED) |
| 2 | TaskEntity columns | ✅ `TaskEntity.kt:74-85` — `eisenhowerQuadrant`, `eisenhowerUpdatedAt`, `eisenhowerReason` (pre-existing since v47→48), `userOverrodeQuadrant` (new, default "0") |
| 3 | Migration 56→57 adds override column | ✅ `Migrations.kt:1401-1407`, `DEFAULT 0` |
| 4 | `EisenhowerClassifier` | ✅ `data/remote/EisenhowerClassifier.kt` |
| 5 | `eisenhowerAutoClassifyEnabled` default true | ✅ `UserPreferencesDataStore.kt:115-116,178,317-319` (`?: true`) |
| 6 | `SyncMapper` round-trips new field | ✅ `SyncMapper.kt:63,106` — `userOverrodeQuadrant` both directions |
| 7 | All 3 creation paths fire classify | ✅ `TaskRepository.kt:65-84,156,196,246` — `addTask`, `insertTask`, `addSubtask` all call `classifyInBackground(id)` on `classifyScope = Dispatchers.IO + SupervisorJob` |
| 8 | `setQuadrantManual` + `reclassify` | ✅ `TaskRepository.kt:502-521` |
| 9 | `EisenhowerScreen` exists + navigable | ✅ `ui/screens/eisenhower/EisenhowerScreen.kt`, routed at `AIRoutes.kt:31-32`, `NavGraph.kt:152` |
| 10 | Settings toggle | ✅ `AiSection.kt:21-22,34-37` (Auto-Classify Tasks), `AiSection.kt:41-43` (Eisenhower Matrix nav) |
| 11 | Unit tests | ✅ `EisenhowerClassifierTest.kt`, `EisenhowerViewModelTest.kt`, plus `TaskRepositoryTest.kt` (touched by CI fix `daa89278`) |
| 12 | Non-blocking classification | ✅ `classifyInBackground` uses a dedicated `classifyScope` distinct from the VM; insert path returns before the IO coroutine launches |

Additional integrity signal: `TaskDao.updateEisenhowerQuadrantIfNotOverridden` (DAO line 217) has a SQL-level `WHERE id = :id AND user_overrode_quadrant = 0` guard, preventing a racing manual-move from being clobbered.

### Weekly Reviews — ✅ all good

| # | Check | Result |
|---|-------|--------|
| 1 | `WeeklyReviewGenerator.generateReview(…)` | ✅ `domain/usecase/WeeklyReviewGenerator.kt:74-111` (accepts `referenceMillis`, not `weekStart/weekEnd` — aggregator derives both) |
| 2 | `WeeklyReviewWorker` `@HiltWorker` | ✅ `notifications/WeeklyReviewWorker.kt:36-44` |
| 3 | Two pref keys default true | ✅ `NotificationPreferences.kt:628-640` (auto-generate + notification) |
| 4 | Channel `prismtask_weekly_review` | ✅ `WeeklyReviewWorker.kt:116` |
| 5 | Sunday ~20:00 local | ✅ `WeeklyReviewWorker.kt:127-137` |
| 6 | `WeeklyReviewSchedulerMigration` | ✅ `NotificationWorkerScheduler.kt:187-206`, flag `WEEKLY_REVIEW_WORKER_SEEDED` |
| 7 | List + detail screens | ✅ `ui/screens/review/WeeklyReviewsListScreen.kt` + `WeeklyReviewDetailScreen.kt` |
| 8 | Settings toggles | ✅ `NotificationTypesScreen.kt:37-38,141-149` + nav entry at 153-156 |
| 9 | Unit tests | ✅ `WeeklyReviewGeneratorTest.kt` (200 lines), `WeeklyReviewWorkerTest.kt` (142 lines), `WeeklyReviewContentTest.kt` |
| 10 | `WeeklyReviewEntity` sync preserved | ✅ `SyncMapper.kt:1025,1034` — prior mapper untouched by this merge |

Outcome model (sealed `WeeklyReviewGenerationOutcome`) cleanly distinguishes `Generated` / `NoActivity` / `NotEligible` (Free tier) / `BackendUnavailable` (retry) / `Error` (retry with cap).  Pro gating via `ProFeatureGate.AI_WEEKLY_REVIEW` constant (`ProFeatureGate.kt:79`).

---

## Checkpoint 2 — Shared Infrastructure Integrity

### 2.1 Preferences DataStore files

The project uses three DataStores, not one file, so "order coherence" is per-store. Each was touched by a different A2 branch and all look clean:

| Store | File | A2 additions |
|-------|------|--------------|
| notification_prefs | `NotificationPreferences.kt` | `WEEKLY_TASK_SUMMARY_ENABLED` + `HAS_SEEDED_WEEKLY_TASK_SUMMARY_WORKER` (v1.4.38); `WEEKLY_REVIEW_AUTO_GENERATE_ENABLED` + `WEEKLY_REVIEW_NOTIFICATION_ENABLED` + `WEEKLY_REVIEW_WORKER_SEEDED` (A2 reviews) — all grouped in their own labeled regions at lines 128-146, 626-649 |
| timer_prefs | `TimerPreferences.kt` | `POMODORO_AI_PRE_SESSION` / `POMODORO_AI_BREAK` / `POMODORO_AI_RECAP` in a clearly-labeled "A2 Pomodoro+ AI Coaching toggles" section at 38-43 and accessors at 172-194 |
| user_prefs (`UserPreferencesDataStore`) | same file | `KEY_EISENHOWER_AUTO_CLASSIFY` in a labeled block at 177-178; flow at 317-319; setter at 434-435; `EisenhowerPrefs` data class at 115-117 |

No mid-section injection. Pref sections stay navigable.

### 2.2 Notification channel IDs (no central registrar exists)

There is **no** `NotificationChannelRegistrar` in this codebase — channels are created lazily in the posting worker / receiver. That's the pre-existing pattern and each A2 feature followed it. Distinct channel IDs currently registered across the app:

```
balance_alerts                              (OverloadCheckWorker)
pomodoro_timer                              (PomodoroTimerService — foreground service ongoing)
pomodoro_timer_alerts                       (PomodoroTimerService — completion)
prismtask_briefing                          (BriefingNotificationWorker)
prismtask_evening_summary                   (EveningSummaryWorker)
prismtask_medication_reminders[_<suffix>]   (NotificationHelper + HabitFollowUpReceiver)
prismtask_reengagement                      (ReengagementWorker)
prismtask_reminders[_<suffix>]              (NotificationHelper base, style-suffixed)
prismtask_timer_alerts[_<suffix>]           (NotificationHelper)
prismtask_weekly_review                     (WeeklyReviewWorker) ← A2
prismtask_weekly_summary                    (WeeklyHabitSummaryWorker, pre-existing)
prismtask_weekly_task_summary               (WeeklyTaskSummaryWorker) ← A2
prismtask_escalation_<action>_<tier>        (EscalationScheduler, dynamic)
```

No duplicates. All three expected channels (`prismtask_weekly_summary`, `prismtask_weekly_task_summary`, `prismtask_weekly_review`) present. Pomodoro channels are pre-existing from the foreground timer service — Pomodoro+ A2 added no new channels (coaching surfaces are in-app dialogs, not notifications). **Minor inconsistency:** `balance_alerts` lacks the `prismtask_` prefix — pre-existing, not an A2 issue.

### 2.3 Settings UI reachability

| A2 setting | Path | File |
|------------|------|------|
| Pomodoro pre-session toggle | Settings → Focus Timer → "AI Coaching" subsection | `FocusTimerScreen.kt:71`, `TimerSection.kt:181` |
| Pomodoro break toggle | same | `TimerSection.kt:187` |
| Pomodoro recap toggle | same | `TimerSection.kt:193` |
| Auto-Classify Tasks (Eisenhower) | Settings → AI Features → Auto-Classify | `AiSection.kt:34-37` |
| Eisenhower Matrix nav | Settings → AI Features → Eisenhower Matrix | `AiSection.kt:41-43` → `PrismTaskRoute.EisenhowerMatrix` |
| Weekly Task Summary toggle | Settings → Notifications → Types | `NotificationTypesScreen.kt:137` |
| Weekly Review auto-generate | same | `NotificationTypesScreen.kt:141-143` |
| Weekly Review notification | same | `NotificationTypesScreen.kt:147-149` |
| Weekly Reviews List nav | Settings → Notifications → Types → "View Weekly Reviews" | `NotificationTypesScreen.kt:155` → `PrismTaskRoute.WeeklyReviewsList` |

No orphaned sections. All five new toggle surfaces reachable from the settings root.

### 2.4 AI client / Haiku wrapper — **single wrapper, ✅ no fragmentation**

All three AI-backed A2 features go through one Retrofit interface:

- `PomodoroAICoach` → `PrismTaskApi.getPomodoroCoaching` (`PrismTaskApi.kt:79-80`)
- `EisenhowerClassifier` → `PrismTaskApi.classifyEisenhowerText` (`PrismTaskApi.kt:69-70`)
- `WeeklyReviewGenerator` → `PrismTaskApi.getWeeklyReview` (`PrismTaskApi.kt:94-95`)

Shared `ApiClient` singleton, shared auth interceptor, shared `PrismTaskApi` contract — no parallel wrappers. **No integration bug here.**

### 2.5 Summary-worker scheduler — ✅ clean

`NotificationWorkerScheduler.applyAll()` orchestrates 6 periodic workers + 3 one-shot seeding migrations:

```
19:00 Sun  WeeklyHabitSummaryWorker   work_name="weekly_habit_summary"    ch=prismtask_weekly_summary
19:30 Sun  WeeklyTaskSummaryWorker    work_name="weekly_task_summary"     ch=prismtask_weekly_task_summary
20:00 Sun  WeeklyReviewWorker         work_name="weekly_review"           ch=prismtask_weekly_review
(daily)    BriefingNotificationWorker / EveningSummaryWorker
(periodic) OverloadCheckWorker / ReengagementWorker
```

Unique work names all distinct. 30-min / 60-min staggered fire times. Migrations:

- `WeeklyHabitSummaryMigration` — cancels stale `WeeklySummaryWorker` → `WeeklyHabitSummaryWorker` class-rename (one-shot)
- `WeeklyTaskSummaryMigration` — seeds new periodic work on first post-upgrade boot (one-shot, guarded by `HAS_SEEDED_WEEKLY_TASK_SUMMARY_WORKER`)
- `WeeklyReviewSchedulerMigration` — seeds new periodic work on first post-upgrade boot (one-shot, guarded by `WEEKLY_REVIEW_WORKER_SEEDED`)

All three have persistent flags; re-runs are no-ops. Enqueue policy `UPDATE` on every `applyAll`, so schedule follows the current preference state.

### 2.6 AlarmManager request-code offsets — ✅ no A2 additions

Current offset bases (searched all of `app/src/main/java/.../notifications/`):

- `400_000` — `MedicationReminderScheduler.BASE_REQUEST_CODE`
- `700_000` — `HabitFollowUpReceiver.FOLLOW_UP_REQUEST_CODE_OFFSET`
- `900_000` — `EscalationScheduler.BASE_REQUEST_CODE`
- (Plus per-task `reminderOffset` alarms keyed on task id directly in `ReminderScheduler`.)

**None of the four A2 features added AlarmManager alarms.** They all use WorkManager (`enqueueUniquePeriodicWork`) + in-app dialogs (Pomodoro coaching). No contention.

### 2.7 Room migrations chain — ✅ clean, DB at v57

`PrismTaskDatabase.kt:112` = `version = 57`. `ALL_MIGRATIONS` composes cleanly 1→57:

| Migration | Added | Source |
|-----------|-------|--------|
| 54→55 | v1.4.37 — `cloud_id` + `updated_at` + unique-index on 7 config tables | `Migrations.kt:1321-1342` |
| 55→56 | v1.4.38 — `cloud_id` (+ `updated_at` where missing) + unique-index on 9 content tables | `Migrations.kt:1366-1394` |
| 56→57 | A2 Eisenhower — `tasks.user_overrode_quadrant INTEGER NOT NULL DEFAULT 0` | `Migrations.kt:1401-1407` |

`CLAUDE.md` still reads "Current Room version is 54" — that's stale and should be updated to 57 (cosmetic). `docs/PHASE_A_DEVICE_TESTING_RUNBOOK_HYBRID.md` is an untracked local doc — unrelated.

### 2.8 TaskSyncMapper — ✅ new field round-trips

`SyncMapper.kt:60,63,103,106`:
- push: `"eisenhowerQuadrant" to task.eisenhowerQuadrant`, `"userOverrodeQuadrant" to task.userOverrodeQuadrant`
- pull: `eisenhowerQuadrant = data["eisenhowerQuadrant"] as? String`, `userOverrodeQuadrant = data["userOverrodeQuadrant"] as? Boolean ?: false`

Firestore docs will carry the boolean; pre-v1.4.x docs read as `false` via the null fallback. No sync-schema break.

---

## Checkpoint 3 — A2 Roadmap Reconciliation

| Item | State | Notes |
|------|-------|-------|
| Weekly Task Summary | **100%** | Ship-ready. 8/8 CP1 checks pass. |
| Pomodoro+ (AI coaching) | **100%** | Ship-ready. 9/9 CP1 checks pass. 11 backend pytest + 17 Android unit tests per merge msg. |
| Eisenhower matrix auto-classify | **100%** | Ship-ready. 12/12 CP1 checks pass. |
| Auto-generated weekly reviews | **100%** | Ship-ready. 10/10 CP1 checks pass. |
| AI time blocking | **0% new work** (see note) | Pre-existing code exists from commit `30786248` ("feat(android): AI Time Blocking in Timeline view with drag-to-rearrange blocks"): `TimelineViewModel.generateTimeBlocks()`, `POST /api/v1/ai/time-block`, `ProFeatureGate.AI_TIME_BLOCK`. **If the A2 roadmap item was "new/expanded AI time blocking", none of that new work was started.** If it was "wire up the existing feature", please clarify scope; the base is already shipping. |
| Per-day medication tier-state Room entity | **0%** | Confirmed: zero files match `MedicationTierState` / `tier_state` / `tierState` / `PerDayMedicationTier` in `app/src/main/java` (or anywhere). Not accidentally started. |
| Main MedicationScreen Compose rewire | **Still blocked** | `MedicationScreen.kt:94` still reads `viewModel.getTiersByTime(todayLog)` (SelfCareLog JSON path). Last modification was `b5e62f02` (2026-02-ish, cosmetic TopAppBar fix). No rewire work since. |

---

## Checkpoint 4 — Remaining A2 Work

### AI Time Blocking (if A2 expansion — **scope clarification needed**)
- Pre-existing: Timeline view drag-to-rearrange + `/ai/time-block` endpoint + Pro gate.
- Possible A2 expansion surface areas: (a) Eisenhower-quadrant-aware ranking input, (b) Pomodoro-session-tracking-aware scheduling (avoid time blocks during break cadence), (c) conflict detection with existing `scheduledStartTime` tasks, (d) multi-day horizon instead of single-day.
- **Effort**: depends on scope
  - Small clarification-only wiring (add Eisenhower signal to the existing request): ~20-45 min, 1 PR.
  - Moderate scope (add conflict detection + quadrant-aware ranking): ~45-75 min × 2 PRs.
  - Full rewrite with Pomodoro cross-integration: 4-6 PRs of ~45-75 min each.
- **Dependencies**: Eisenhower quadrants are already on `TaskEntity`, so no hard block. Pomodoro session-tracking currently lives only in `SmartPomodoroViewModel` state (not persisted); cross-feature use would need a session log table — **this is currently outside the codebase**.
- **Risks**: low — a feature that already ships in a basic form; expansions are additive.

### Per-day medication tier-state Room entity
- **Effort**: ~45-75 min large PR for entity + migration (+ an emulator migration test in a sibling PR).
- **Schema spec**: not clear yet. Current `SelfCareLogEntity.tiersByTime` is a JSON-serialized `Map<String,String>` on a single daily log row. A canonical relational shape would be:
  ```
  medication_tier_states (
    id INTEGER PK,
    medication_id INTEGER NOT NULL FK→medications,
    log_date TEXT NOT NULL,          -- ISO yyyy-MM-dd local
    time_of_day TEXT NOT NULL,       -- morning/noon/evening/bedtime slot key
    tier TEXT NOT NULL,              -- taken / missed / deferred / …
    updated_at INTEGER NOT NULL,
    cloud_id TEXT,
    UNIQUE(medication_id, log_date, time_of_day)
  )
  ```
  Needs product/spec confirmation on the tier enum and slot keys.
- **Migration strategy**: MIGRATION_57_58 creates the table, then backfills from `self_care_logs WHERE routine_type='medication'` by parsing each row's `tiersByTime` JSON into per-slot inserts. Legacy column stays (quarantine) per the self-care → medications pattern (commit `d782cd13`), cleaned up in a Phase 2 migration after convergence.
- **Unblocks**: Main MedicationScreen Compose rewire.

### Main MedicationScreen Compose rewire (post-tier-state)
- **Effort**: ~20-45 min multi-checkpoint PR after the tier-state entity lands.
- **Scope**: refactor `MedicationScreen.kt` (703 lines) and `MedicationViewModel.getTiersByTime(todayLog)` to pull from the new DAO; delete the JSON-parsing path; keep component surface (time-of-day chips, tier-picker sheet) unchanged to minimize reflow.

### Medication migration instrumentation suite — **not started, Phase B gate**
- **Status**: `app/src/androidTest/.../Migration47To48Test.kt`, `Migration51To52Test.kt`, `Migration53To54Test.kt` — **migrations 54→55, 55→56, 56→57 have no tests.**
- **Reminder**: per §3.4, this is the cross-device-race safety net and MUST land before Phase C.
- **Effort**: ~45-75 min large PR for `Migration54To55Test` (cloud_id + updated_at backfill across 7 tables), `Migration55To56Test` (9 tables + conditional `updated_at`), `Migration56To57Test` (`user_overrode_quadrant DEFAULT 0`). Emulator-based, all three can go in one PR.
- **Post-audit fix (2026-04-22): closed.** Three new test files land in `app/src/androidTest/java/com/averycorp/prismtask/`: `Migration54To55Test.kt` (column presence + unique index + default backfill + duplicate-cloud_id rejection, across all 7 config tables), `Migration55To56Test.kt` (same coverage for 9 content tables, plus explicit test that `medication_refills` / `daily_essential_slot_completions` keep their pre-existing `updated_at` value rather than getting re-added), and `Migration56To57Test.kt` (column presence + NOT NULL DEFAULT 0 + backfill across Q1/Q3/unclassified rows + post-migration manual-override update). All three mirror the `Migration53To54Test` pattern — `SupportSQLiteOpenHelper` + `FrameworkSQLiteOpenHelperFactory` + direct `migrate(db)` invocation, since the project uses `exportSchema = false` and can't wire `MigrationTestHelper`.

### v1.4.37/v1.4.38 entity integration verification — **gap found, Phase B gate**
- **Status**: `CloudIdOrphanHealer.kt` covers 14 families (`healFamily` calls at lines 139-206): `self_care_steps`, `self_care_logs`, `courses`, `course_completions`, `leisure_logs`, `projects`, `tags`, `habits`, `habit_completions`, `habit_logs`, `tasks`, `task_completions`, `task_templates`, `milestones`.
- **Missing (16 families that got `cloud_id` in migrations 54→56):**
  - v1.4.37 (7): `reminder_profiles`, `custom_sounds`, `saved_filters`, `nlp_shortcuts`, `habit_templates`, `project_templates`, `boundary_rules`
  - v1.4.38 (9): `check_in_logs`, `mood_energy_logs`, `focus_release_logs`, `medication_refills`, `weekly_reviews`, `daily_essential_slot_completions`, `assignments`, `attachments`, `study_logs`
- **Impact**: SyncMapper push/pull path works; orphan-recovery path does not. An out-of-band Firestore wipe of these families will leave the 16 entity types with stranded local `cloud_id`s that never re-upload.
- **Effort**: ~45-75 min large PR to add 16 `healFamily(...)` calls + 16 DAO enumerators (`getRowsWithCloudId`) + a parameterized `CloudIdOrphanHealerFullCoverageTest`.
- **Post-audit fix (2026-04-22): closed.** `CloudIdOrphanHealer.kt` now injects 14 additional DAOs (`NotificationProfileDao`, `CustomSoundDao`, `SavedFilterDao`, `NlpShortcutDao`, `HabitTemplateDao`, `ProjectTemplateDao`, `BoundaryRuleDao`, `CheckInLogDao`, `MoodEnergyLogDao`, `FocusReleaseLogDao`, `MedicationRefillDao`, `WeeklyReviewDao`, `DailyEssentialSlotCompletionDao`, `AttachmentDao` — `SchoolworkDao` was already injected and now also enumerates `assignments` + `study_logs`). Each family uses its existing `getAllOnce()` enumerator; entity-type strings match `SyncService.pushUpdate` (e.g. `notification_profile` for the `reminder_profiles` table / `notification_profiles` Firestore collection). All 5 healer-instantiation sites in `app/src/androidTest/.../CloudIdOrphanHealer*Test.kt` updated. Hilt wiring unchanged — every new DAO was already `@Provides`'d via `DatabaseModule`.

---

## Checkpoint 5 — Integration Issues Found

1. **Rate-limit contention across AI-calling features — low risk.** Backend limiters (`backend/app/routers/ai.py`): pomodoro-coaching 15/10min, eisenhower classify_text 20/min, weekly-review 1/hr. They're on different limiter objects, different endpoints, so no shared-bucket contention. A 4-session Pomodoro flow burns ~8 coaching calls (4 pre + ~3 break + 1 recap) — well under budget. Eisenhower fires per-task-create which spiking above 20/min on bulk imports; the per-task classifier silently drops on rate-limit so no UX break, but bulk creations would quietly miss quadrants until the next `reclassify`.

2. **No migration test for 56→57.** The `user_overrode_quadrant DEFAULT 0` migration is low-risk schema-wise, but the test gap is real (see §4 above).

3. **No notification-channel coverage for A2 channels.** `NotificationChannelsInstrumentedTest.kt` only asserts on `prismtask_reminders`-prefixed task-reminder channels. `prismtask_weekly_task_summary` / `prismtask_weekly_review` have zero instrumentation — merge landed without extending the test. Low urgency (channel creation is straightforward in the worker code) but worth a follow-up.
   **Post-audit fix (2026-04-22): closed.** `NotificationChannelsInstrumentedTest.kt` now includes: `weeklyTaskSummaryChannel_constantMatchesExpectedId`, `weeklyTaskSummaryChannel_registersAtDefaultImportance`, `weeklyReviewChannel_constantMatchesExpectedId`, `weeklyReviewChannel_registersAtDefaultImportance`, `a2Channels_areDistinctFromLegacyAndEachOther` (pins the three weekly channel IDs against mutual + legacy collision and against loss of the `prismtask_` prefix), and `weeklyTaskSummaryChannel_reCreationIsIdempotent`. The tests read the worker classes' public `CHANNEL_ID` constants directly so a future rename surfaces as a compile-time break, and exercise the OS contract (`NotificationManager.createNotificationChannel` + `getNotificationChannel`) without opening private seams on the workers.

4. **`WeeklyTaskSummaryWorker` "still open" counts whole-app incomplete.** Line 175: `taskDao.getIncompleteTaskCount()` is all-time incomplete, not week-end open. The user-facing string reads "P still open" — could be larger than expected (includes all deferred/future-dated tasks). Non-bug but worth a product/copy review.

5. **`WeeklyReviewWorker.doWork()` retry semantics look fine.** `BackendUnavailable → Result.retry` with WorkManager default exponential backoff 30s → ~5h. `Error → Result.retry` capped at `MAX_ATTEMPTS = 3`. Next periodic fire is 7 days out, so retry amplitude is naturally bounded. No risk of tight retry loops against a broken backend.

6. **Generator / on-demand race is benign.** `WeeklyReviewRepository.save(weekStart, …)` is idempotent on `week_start_date` (DAO index — DB schema for `weekly_reviews`). Worker-generated and ViewModel-generated rows for the same week upsert; no duplicate risk. Both paths call the same `save()` with distinct `aiInsightsJson` shapes — `WeeklyReviewContent.kt`'s "lenient JSON parser" (per merge commit msg) handles both shapes.

7. **Documentation drift (cosmetic).** `CLAUDE.md` says DB v54; actual is v57. `CLAUDE.md` lists migrations "through `MIGRATION_53_54`"; actual chain ends at `MIGRATION_56_57`. `migration instrumentation suite` is referenced in spec language as a Phase B gate but not tracked in any checklist file in the repo — easy to miss.
   **Post-audit fix (2026-04-22):** `CLAUDE.md` updated to `Current Room version is **57** with 56 cumulative migrations (MIGRATION_1_2 through MIGRATION_56_57)`, with new prose covering v54→v55 (7 config tables), v55→v56 (9 content tables, with the two pre-existing `updated_at` carve-outs), and v56→v57 (Eisenhower `user_overrode_quadrant`). The project-tree comment for `Migrations.kt` is bumped similarly.

8. **Dead-code observation — none.** `tiersByTime` is still live code (read by `MedicationScreen.kt:94`, written by `SelfCareRepository`), pending the per-day tier-state entity work. No stranded references from the medication refactor.

9. **`SmartPomodoroViewModel` `nextSession()` re-fires pre-session.** Per merge commit: "nextSession now re-fires pre-session coaching so the modal surfaces on every work block, not only the first." Verified at `SmartPomodoroViewModel.kt:355` (`_preSessionCoaching.value = PreSessionCoachingUiState.Hidden` reset on each session start) and line 542-571 (re-launch pipeline). No stale state carryover between sessions.

10. **Untracked local file.** `docs/PHASE_A_DEVICE_TESTING_RUNBOOK_HYBRID.md` is on the working tree but not committed. Flagging for awareness; not an audit issue.

---

## Summary Action Items (for planning Phase B)

Non-blocking but worth scheduling:

1. ~~**Extend `CloudIdOrphanHealer` to the 16 v1.4.37+v1.4.38 families** — ~45-75 min, unblocks genuine Phase-3 cleanup resilience.~~ **Closed 2026-04-22** (same-branch fix).
2. ~~**Write `Migration54To55Test` / `55To56` / `56To57` tests** — ~45-75 min, required before Phase C per §3.4.~~ **Closed 2026-04-22** (same-branch fix).
3. ~~**Extend `NotificationChannelsInstrumentedTest` to cover weekly_task_summary + weekly_review channels** — small PR, ~10-20 min.~~ **Closed 2026-04-22** (same-branch fix).
4. ~~**Update `CLAUDE.md`** to reflect DB v57 and migration chain through `MIGRATION_56_57` — trivial.~~ **Closed 2026-04-22** (same-branch fix).
5. **Clarify scope of "AI time blocking" A2 item** — the base feature already ships; need product signal on expansion shape before estimating. *(Still open — requires product input.)*

Blocking Phase B: nothing.

---

## §6 — Post-Audit Fix Summary (2026-04-22, same branch)

All four code-reachable follow-ups from §5/§action-items above landed in the same branch as this audit:

| Fix | File(s) |
|-----|---------|
| `CloudIdOrphanHealer` coverage extended from 14 to 30 entity families (15 direct DAOs + 2 via existing `SchoolworkDao`) | `app/src/main/java/com/averycorp/prismtask/data/remote/CloudIdOrphanHealer.kt` (+14 DAO fields, +16 `healFamily(...)` calls, doc-comment updated); `app/src/androidTest/.../CloudIdOrphanHealer{,Scenario,Tier1,TwoDevice,Emulator}Test.kt` (5 instantiation sites updated with named args for the new DAOs — drop-in since the production class is Hilt-wired via `DatabaseModule` which already `@Provides` all 14 of them). |
| Migration tests for v54 → v57 | `app/src/androidTest/.../Migration54To55Test.kt` (6 test cases), `Migration55To56Test.kt` (7 cases, including pre-existing `updated_at` preservation), `Migration56To57Test.kt` (6 cases incl. NOT NULL / DEFAULT 0 pragma assertions). |
| A2 channel coverage in the instrumented suite | `app/src/androidTest/.../notifications/NotificationChannelsInstrumentedTest.kt` (+5 tests asserting `prismtask_weekly_task_summary` / `prismtask_weekly_review` constants, OS-level `createNotificationChannel` registration, mutual distinctness, and idempotent re-registration). |
| DB-version drift in project memory | `CLAUDE.md` (Database bullet + Project Structure tree comment). |

**Local gates run pre-push:** `./gradlew compileDebugKotlin` ✅ · `compileDebugAndroidTestKotlin` ✅ · `ktlintCheck` ✅ · `detekt` ✅ · `testDebugUnitTest` ✅. Instrumented migration / channel tests require an emulator (no device available locally this session) — CI will pick them up on push.
