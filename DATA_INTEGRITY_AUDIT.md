# Data Integrity Audit — 2026-04-17

---

## Resolution Addendum — 2026-04-18/19

Items resolved since original audit. Original findings are preserved below.

| Finding | Original severity | Status | Resolution |
|---------|------------------|--------|------------|
| Habit uncheck not propagated cross-device | CRITICAL | ✅ Fixed | `processRemoteDeletions()` wired to `HabitCompletionDao.deleteById()` (PR #557) |
| Habit completion push uses wrong row ID | HIGH | ✅ Fixed | `pushCreate()` now looks up by completion `id`; `uncompleteHabit` uses `getLatestByHabitAndDateLocal` (PR #540, #557) |
| Cross-device deletion not propagated (all entity types) | HIGH | ✅ Fixed | REMOVED document events now processed in real-time listener (PR #539) |
| Habit completion dates misalign across timezones | HIGH | ✅ Fixed | `completed_date_local` column added (migration 49→50); all day-boundary queries use pre-computed local date string (PR #556) |
| Push only fires at app launch (stale reads on slow networks) | HIGH | ✅ Fixed | Reactive `observePending()` debounce; edits push within ~500 ms (PR #536) |
| Task completions not synced | HIGH | ✅ Fixed | `TaskCompletionEntity` wired into push/pull pipeline (PR #543) |
| Onboarding flag written after template work (survives process-death) | MEDIUM | ✅ Fixed | Flag written before seeding begins (PR #538) |
| Built-in habit identity lost on sync (duplicate cloud rows) | HIGH | ✅ Fixed | `is_built_in`/`template_key` columns + `BuiltInHabitReconciler` one-time repair (PR #549–553) |

All other original findings (export/import gaps, remaining FK orphan vectors, migration ordering, `exportSchema=false`) are carry-forwards — see original text below.

---



## Summary

Scope: 32 Room entities, 43 migrations (DB v44 — CLAUDE.md is two versions stale), Firebase + backend sync, JSON/CSV export, FastAPI SQLAlchemy backend, calendar sync (push partly built, pull stub).

The biggest data-loss vectors are concentrated in three places:
1. **Export/import** silently omits ~13 entity types and destroys subtask hierarchies on round-trip; REPLACE-mode wipe is not atomic.
2. **Firebase sync** has non-idempotent pulls for habit logs, no transactional pairing of entity write + sync_metadata upsert, no LWW conflict guard, and sign-out leaves cross-account contamination in `sync_metadata`.
3. **Multi-tap completion** (habits and tasks) is a read-then-insert race with no unique constraint on `(habit_id, completed_date)` or `(task_id, completed_date)` — duplicates on a slow tap.

Other notable items: MIGRATION_42_43 unconditionally drops the `calendar_sync` table, calendar pull is a stub, calendar `etag` is written but never read, `sync_metadata` has no max-retry, and several orphan tables (`usage_logs`, `focus_release_logs`, NULL `task_completions`) accumulate forever. The backend has consistent `ondelete` behavior on user-scoped FKs but mixes naive and timezone-aware datetimes and uses `String(20)` columns where Enums are defined.

Counts: **17 CRITICAL, 28 HIGH, 28 MEDIUM, 13 LOW** total findings across sections.

## Severity legend
- **CRITICAL** — user data will be lost or corrupted
- **HIGH** — orphaned data accumulates, queries return wrong results
- **MEDIUM** — inconsistency possible under specific conditions
- **LOW** — defensive-data issue

## 1. Foreign key integrity

**Entities with FKs (correct):**
- `tasks.project_id` → projects(id) **SET_NULL** (intent: orphan tasks survive project delete) ✓
- `tasks.parent_task_id` → tasks(id) **CASCADE** (subtree delete) ✓
- `task_tags.taskId` / `tagId` → CASCADE on both ✓
- `attachments.taskId` → CASCADE ✓
- `habit_completions.habit_id` → CASCADE ✓
- `habit_logs.habit_id` → CASCADE ✓
- `assignments.course_id` → CASCADE ✓
- `course_completions.course_id` → CASCADE ✓
- `calendar_sync.task_id` → CASCADE ✓ (but see §5 — does NOT delete remote Google event)
- `task_completions.task_id` / `project_id` → SET_NULL ✓ (audit trail preserved)
- `task_templates.templateProjectId` → SET_NULL ✓

**Missing FKs (orphan-prone):**
- **HIGH** — `self_care_steps.medication_name` (TEXT) name-string link to `medication_refills.medication_name`. No FK; if user renames or deletes a refill, steps are silently orphaned. (added in MIGRATION_36_37)
- **HIGH** — `study_logs.course_pick` (INTEGER?) and `study_logs.assignment_pick` (INTEGER?) reference `courses.id` / `assignments.id` with no FK. Course/assignment delete leaves stale picks; UI may render "ghost" study sessions.
- **HIGH** — `focus_release_logs.task_id` (INTEGER?) references `tasks.id` with no FK. Task delete leaves logs pointing to nonexistent task IDs; analytics queries can crash on join.
- **MEDIUM** — `tasks.source_habit_id` (INTEGER?) references `habits.id` with no FK. Habit delete leaves stale pointer (this column is also marked "removed" in MIGRATION_15_16 comment yet still written by code).
- **MEDIUM** — `sync_metadata.local_id` is polymorphic by design (entity_type discriminator); requires app-level cascade that doesn't exist for every entity (see §3).
- **MEDIUM** — `usage_logs.entity_id` polymorphic (entity_name discriminator); no app-level cleanup, grows unbounded (see §3).

**CASCADE risk (intentional but worth confirming):**
- `tasks.parent_task_id` CASCADE: deleting a parent removes all subtasks recursively, including any subtasks the user has manually completed. No "promote subtasks" option.
- `attachments.taskId` CASCADE: file URIs vanish from DB but underlying files in app sandbox / content provider are NOT deleted (storage leak, not data loss).
- `calendar_sync.task_id` CASCADE: removes the local mapping but the remote Google Calendar event remains orphaned (see §5).

## 2. Room migration history

**Reality check vs. CLAUDE.md:** CLAUDE.md says version 42 with 41 migrations. Actual code: `PrismTaskDatabase.kt:100` declares `version = 44`; `Migrations.kt` defines `MIGRATION_1_2` through `MIGRATION_43_44` (43 migrations). Documentation is two versions stale.

**All migrations use `db.execSQL()` directly. No `fallbackToDestructiveMigration()` found in either `PrismTaskDatabase.kt` or any DI module — good.**

**Issues:**
- **CRITICAL** — `MIGRATION_42_43` (Migrations.kt:763–781) `DROP TABLE IF EXISTS calendar_sync` then recreates it. Comment justifies it (device-calendar path deprecated, IDs don't resolve), but **all in-flight calendar mappings are deleted unconditionally** with no warning to the user. Users who had calendar sync enabled will see all their event ↔ task links vanish on upgrade. No backup, no notification.
- **HIGH** — `exportSchema = false` (PrismTaskDatabase.kt:101). Room schema JSON files are not generated, so migration tests cannot use `MigrationTestHelper` reliably and CI cannot detect a schema/migration mismatch automatically.
- **HIGH** — `MIGRATION_15_16` (Migrations.kt:286–291) is a no-op marker that "removed" `create_daily_task` and `source_habit_id` columns but **does not drop them**. Columns still exist in older DBs; new installs (created via `entities`) will not have them. Code paths that still write `source_habit_id` (TaskEntity carries it) will succeed on old DBs but silently no-op on new ones — schema divergence between fresh-install and upgrade users.
- **HIGH** — Migration ordering is interleaved in source (MIGRATION_22_23 declared before MIGRATION_20_21; MIGRATION_36_37 before MIGRATION_35_36; MIGRATION_34_35 after MIGRATION_35_36; MIGRATION_33_34 after MIGRATION_36_37). Functionally fine because Room sorts by `(start, end)`, but easy for a future contributor to assume sequential and miss one. The `ALL_MIGRATIONS` array (Migrations.kt:793–837) IS in correct order, and that's what's wired in — but a missed entry would silently downgrade users to destructive migration if `fallbackToDestructiveMigration` is ever added.
- **MEDIUM** — `MIGRATION_36_37` adds `self_care_steps.medication_name` as nullable TEXT with no backfill. Existing rows get NULL. The medication self-care routine then expects exact-name match against `medication_refills` — silent disconnect for any pre-upgrade routine.
- **MEDIUM** — `MIGRATION_37_38` backfill (Migrations.kt:649–665) reads `t.completed_at` for both `completed_date` and `completed_at_time`. Any row where `is_completed = 1` but `completed_at IS NULL` is silently excluded — historical "completed without timestamp" tasks are missing from analytics forever.
- **MEDIUM** — `MIGRATION_37_38` backfill `days_to_complete` assumes ms timestamps; if an older row stored seconds (none currently do, but no validation), the integer division by 86400000 underflows to 0.
- **MEDIUM** — No schema tests directory present. Search confirms no `MigrationTest*.kt` in `app/src/androidTest/`. With `exportSchema = false`, there is no automated verification that the entity definitions actually match the migration output. Drift between `@Entity`-declared columns and migration-applied columns will only be caught at runtime via `IllegalStateException`.
- **LOW** — `MIGRATION_35_36` creates `boundary_rules.start_time`/`end_time` as TEXT (HH:mm strings) — relies on convention, not constraints. A bad string from the parser silently sorts wrong.
- **LOW** — `MIGRATION_5_6` declared `calendar_sync.task_id` as INTEGER PRIMARY KEY (not autoincrement). The 42→43 recreate kept this contract; correct.
- **LOW** — `MIGRATION_43_44` adds `today_skip_*_days` with default `-1` ("inherit") — convention not enforced; any value < -1 is undefined.

No migration is annotated with a "destructive" or "risky" warning even though MIGRATION_42_43 unconditionally drops a user-data table.

## 3. Orphan data patterns

- **CRITICAL** — `SettingsViewModel.kt:1307–1350` `resetAppData()` clears habits/tasks/attachments but **does not** delete `sync_metadata`. Pending push/delete actions for already-removed entities dangle forever; sync silently retries to delete things that don't exist.
- **CRITICAL** — `AuthManager` sign-out path leaves `sync_metadata` intact. Sign-in as a different account → old cloud_id mappings get re-attached to the new user's local rows. Cross-account contamination.
- **HIGH** — `usage_logs` (UsageLogDao.kt) has only `insert()` / frequency queries — **no purge, no TTL, no max-rows cap**. Grows ~10+ rows/day per active user; multi-year users = 50k+ rows powering only "smart suggestions."
- **HIGH** — `task_completions.task_id` SET_NULL on task delete (TaskCompletionEntity.kt:34–35). Orphan rows accumulate forever; analytics sees "?" for every deleted task. No purge.
- **HIGH** — `focus_release_logs` — `FocusReleaseLogDao.deleteOlderThan()` exists but is **never called** by any worker. Power users running focus mode generate dozens of events/day; unbounded.
- **HIGH** — `self_care_logs.completed_steps` is a JSON array of step IDs. `SelfCareRepository.deleteStep()` (line 265–266) deletes the step but never sweeps log JSON. Stale step IDs persist; reads silently render unknown steps.
- **HIGH** — Habit archive vs delete asymmetry: `is_archived = 1` keeps the habit row + all `habit_completions` / `habit_logs` forever. No "purge after archive" path. Heavy users with many archived habits accumulate completion history indefinitely.
- **HIGH** — Auto-archive worker exists (TaskRepository.kt:298–302, AutoArchiveWorker.kt) but **only marks `archived_at`**; no follow-up purge. Old completed tasks live forever, unindexed in default queries but bloating DB.
- **MEDIUM** — `calendar_sync` mappings: `CalendarManager.disconnect()` and `setSyncCalendarId()` neither delete nor mark stale rows (see §5). Mappings persist beyond their useful life.
- **MEDIUM** — `WidgetConfigDataStore.clearForWidget()` exists (line 111–118) but no widget receiver invokes it on widget removal. Per-instance config orphans.
- **MEDIUM** — `mood_energy_logs`, `check_in_logs`, `weekly_reviews` — daily/weekly cadence ⇒ ~400 rows/year. No user-facing "delete history." Bounded but irreversible.
- **MEDIUM** — `AttachmentRepository.deleteAttachment()` (line 56–62) calls `file.delete()` and ignores the boolean result. Delete-failure leaves orphan files in app sandbox; no retry, no log.
- **MEDIUM** — `CustomSoundRepository.delete()` (line 41–44) wraps `File(...).delete()` in `runCatching {}` and discards the error. Notification sound files orphan silently in `notification_sounds/`.
- **LOW** — Pending sync_metadata for an entity deleted locally before the push completes: no code path clears the dangling pending action; SyncService will try to push update for a row that no longer exists, swallow the exception, increment `retry_count` forever.
- **LOW** — `TimerStateDataStore` is a single global state, not per-widget-instance. Multiple timer widgets share state; not technically orphan but stale.

## 4. Firebase sync consistency

- **CRITICAL** — `SyncService.kt:425–459` pull handlers for `habit_completions` / `habit_logs` are insert-only (`if (localId == null) insert`). Same remote event applied twice creates **duplicate rows** with identical cloudId. No update path.
- **CRITICAL** — `SyncService.kt:60–67, 381–388` entity write and `sync_metadata` upsert are not in a transaction. App crash between them leaves an entity with no metadata; next sync re-uploads it as a new doc → Firestore duplicates.
- **CRITICAL** — `SyncService.kt:378–395` `taskDao.insert()` and tag cross-ref inserts (`addTagToTask`, line 394) are not transactional. Crash between → tags silently lost.
- **CRITICAL** — `AuthManager.kt:67–70` sign-out does **not** clear pending sync queue. Pending actions for the previous user can be re-played against a new account if local IDs collide; cross-account data leakage possible.
- **HIGH** — `SyncService.kt:396–399` remote update overwrites local row unconditionally. No last-write-wins timestamp guard, no dirty-flag check. Active local edits are silently clobbered by inbound listener events. (Note: `BackendSyncService` does have an LWW check; the Firebase path does not.)
- **HIGH** — `SyncService.kt:425–459` habit_completion / habit_log pull never calls `clearPendingAction()` for already-existing records. Local pending state can persist forever, causing endless re-push loops.
- **HIGH** — `SyncMetadataDao.kt:45–46` `incrementRetry()` has no max bound. Permanently failing operations retry forever, no backoff, no DLQ.
- **HIGH** — `SyncService.kt:513–556` `startAutoSync()` → `startRealtimeListeners()` calls `stopRealtimeListeners()` first but the unregister and re-register happen sequentially without guarding against concurrent callers — brief double-listen window possible (small but real duplicate-event risk).
- **MEDIUM** — `SyncMapper.kt:235–245` `taskCompletionToMap()` serializes `TaskCompletionEntity` for upload, but no `mapToTaskCompletion()` exists in pull. Completion history is a one-way sync — second device never sees it.
- **MEDIUM** — `SyncService.kt:379–388` cloudId↔localId race: insert-then-upsert window means a parallel pull seeing the same cloudId before metadata is written will insert a duplicate.
- **MEDIUM** — `SyncService.kt:376–401, 424–441` cloudId lookup keys on `(cloudId, entityType)` but there's no validation that the resolved local row's actual entity type matches. Polymorphic confusion possible if a sync bug ever crosses types.
- **MEDIUM** — `SyncService.kt:539–552` `isSyncing` flag check is read-then-act with no lock. Listener callback can race with `fullSync()` and trigger concurrent sync passes.
- **LOW** — `SyncService.kt:225–243` push exception path increments retry but `clearPendingAction()` already ran at line 232; failed pushes drop out of the pending queue but retry_count keeps climbing — masks failures.
- **LOW** — `SyncService.kt:59, 79, 168, 291, 325` all `docRef.set(data)` calls use full replacement, never `SetOptions.merge()`. Two devices pushing different fields to the same doc concurrently → one device's fields lost.
- **LOW** — `SyncService.kt:392–394` tag cross-ref pull doesn't verify tag exists locally; out-of-order pull (task before tag) silently drops tag links.

## 5. Calendar sync data mapping

(Note: device-calendar sync was hard-deprecated by MIGRATION_42_43; backend-mediated Google Calendar sync via `CalendarSyncRepository` is partially built — push works, pull does not. `CalendarSyncService.kt` does not exist.)

- **CRITICAL** — `CalendarSyncRepository.kt:135–162` `pullEvents()` returns `EventsPullResponse` with `created/updated/deleted` counts but **no code creates/updates/deletes tasks from the response**. Pull is a stub; any remote change made on the user's Google Calendar never reaches PrismTask. One-way sync silently.
- **CRITICAL** — `SettingsViewModel.kt:968–973` `setSyncCalendarId()` changes the target calendar but **does not clean up old `calendar_sync` rows**. Switching calendars leaves stale mappings whose `calendar_id` still points to the prior calendar; next push uses a stale target.
- **CRITICAL** — `CalendarSyncDao.findByState()` is defined but **never called**. Rows in `PENDING_PUSH`, `PENDING_DELETE`, `ERROR` states never get retried — they leak forever. Failed pushes are permanently lost.
- **HIGH** — `CalendarSyncRepository.kt:78–88` `etag` is written on every push but **never read or compared anywhere**. Incremental change detection is non-functional. Once pull is implemented, conflict detection will be impossible.
- **HIGH** — `TaskRepository.kt:251–256` deletes the local task before the remote-event delete RPC is awaited. If the remote delete fails, the mapping row CASCADEs away with the task — the Google Calendar event is **orphaned with no recovery path**.
- **HIGH** — `CalendarSyncRepository.pushTask()` writes the mapping row only after the API succeeds. App crash between API success and DB write → Google has the event, local has no mapping. Next sync creates a duplicate event.
- **MEDIUM** — Conflict resolution: `lastSyncedVersion = task.updatedAt` is stored but never compared on subsequent syncs; with no LWW/version check on pull (when implemented), remote always wins.
- **MEDIUM** — `CalendarSyncEntity.kt:40` `calendar_id` defaults to `"primary"` at the entity level. Any insert path that forgets to set it explicitly silently routes to the primary calendar regardless of the user's selection.
- **MEDIUM** — `CalendarSyncRepository.kt:144–147` pull merges `displayIds + targetId` into the calendars to fetch from. Display-only calendars can leak into the sync destination if pull is wired up later.
- **MEDIUM** — No FK/uniqueness on `calendar_sync.calendar_event_id`. Duplicate event IDs across rows can occur (and would silently corrupt 1:1 task↔event semantics).
- **MEDIUM** — No transaction wrapping {API call → mapping upsert} in `pushTask()` (Room runInTransaction not used).
- **LOW** — `sync_state` is plain TEXT; typo'd states (`"SYNCD"`) accepted silently. No enum check.
- **LOW** — `CalendarManager.kt:144–149` calendar disconnect leaves `calendar_sync` rows untouched. They persist as zombie mappings.
- **LOW** — `CalendarSyncRepository.kt:151–157` `PullSummary` returned but counts never logged, never surfaced to UI. Sync failures are invisible.
- **LOW** — Default `lastSyncedVersion = 0` on initial mapping insert; first sync after upgrade will mis-detect "no changes since 0" if `task.updatedAt = 0`.

## 6. Daily Essentials data pointers

Feature is implemented (`DailyEssentialsUseCase.kt`, `DailyEssentialsPreferences.kt`) and not currently mentioned in CLAUDE.md.

- **OK** — `DailyEssentialsUseCase.observeHouseworkCard()` (lines 227–241) and `observeSchoolworkCard()` (lines 243–279) handle deleted/archived habit IDs gracefully via `if (habit == null || habit.isArchived) flowOf(null)` — card disappears instead of crashing.
- **OK** — `DailyEssentialsPreferences.markHintSeen()` (lines 60–64) only sets `hasSeenHint = true`. No code path unsets it.
- **LOW** — Stale pointer cleanup: when the pointed-to habit is deleted, `housework_habit_id` / `schoolwork_habit_id` keys are not cleared from the DataStore. The card hides but a future habit reusing the same primary key (very unlikely with autoincrement) would resurrect the link. No actual bug, just hygiene.

## 7. Backend SQLAlchemy models

- **HIGH** — `services/urgency.py:59` mixes naive `datetime.now()` with timezone-aware `datetime.now(timezone.utc)` (line 61). Comparison against `created_at` (server_default `func.now()`, naive in SQLite/aware in Postgres) raises `TypeError` whenever the path crosses backends.
- **MEDIUM** — `routers/tasks.py:148` writes `completed_at = datetime.now(timezone.utc)` but `routers/tasks.py:298,363` use naive `datetime.now()`. Inconsistent timestamp tz-awareness; comparisons across these fields will throw under Postgres.
- **MEDIUM** — `models.py:173` `Task.priority = Column(Integer)` with no Enum or CheckConstraint; any int value persists. Status uses `Enum(TaskStatus, values_callable=...)` correctly — priority is the outlier.
- **MEDIUM** — `models.py:497` `BugReportModel.status = String(20)` while `BugReportStatus` enum exists at lines 464–468. No DB enforcement; typos persist.
- **MEDIUM** — `models.py:421` `IntegrationConfig.source = String(20)` while `SuggestedTask.source` (lines 387–390) uses `Enum(IntegrationSource, values_callable=...)` properly. Inconsistent — IntegrationConfig allows arbitrary strings.
- **MEDIUM** — `models.py:323` `ProjectInvite.expires_at` is `nullable=False` with no default. Insert without explicit value raises `IntegrityError`. Should have `server_default` or app-side default.
- **MEDIUM** — `models.py:126–138` no `UniqueConstraint("user_id", "title")` on `Project`. Duplicate project titles per user are allowed → confusing API responses, breaks UI keyed on title.
- **MEDIUM** — `models.py:108–124` same: no uniqueness on `Goal (user_id, title)`.
- **MEDIUM** — `models.py:338` `ActivityLog.user_id` FK has no `index=True`. Per-user activity queries do full table scan.
- **MEDIUM** — `models.py:355` `TaskComment.user_id` FK has no `index=True`. "Comments by user" queries are slow and lock-prone.
- **MEDIUM** — `models.py:317` `ProjectInvite.inviter_id` FK has no `index=True`.
- **MEDIUM** — `models.py:279` `TaskTemplate.template_project_id` FK with `SET NULL` lacks index. Orphan-finding queries (`WHERE template_project_id IS NULL`) become slow as templates grow.
- **LOW** — `models.py:443–446` `CalendarSyncSettings.user_id` is both PK and FK with `ondelete=CASCADE`. Pattern is unusual; future composite-PK additions will break.
- **LOW** — `models.py:253` `HabitCompletion` unique on `(habit_id, date)` only. Defensively should include `user_id` if cross-user collision is possible (FK tree should prevent it, but data-model doesn't).
- **LOW** — `models.py:476` `BugReportModel.user_id` uses `ondelete="SET NULL"`; only place outside templates that uses SET NULL — inconsistent pattern, worth confirming intentional.

## 8. Import/export paths

- **CRITICAL** — `DataExporter.kt:56–74` ~13+ entities silently omitted from export: `AttachmentEntity`, `BoundaryRuleEntity`, `CheckInLogEntity`, `CustomSoundEntity`, `FocusReleaseLogEntity`, `MoodEnergyLogEntity`, `NotificationProfileEntity`, `StudyLogEntity`, `WeeklyReviewEntity`, `MedicationRefillEntity`, `HabitTemplateEntity`, `ProjectTemplateEntity`, `TaskTemplateEntity` (and `NlpShortcutEntity`, `SavedFilterEntity`, `UsageLogEntity`). "Full backup" claim is false; users restoring a backup lose all of this data.
- **CRITICAL** — `DataImporter.kt:154–164` REPLACE mode does sequential deletes (`taskDao`, `projectDao`, `tagDao`, `habitDao`, `taskCompletionDao`, `habitCompletionDao`) outside any `runInTransaction { }`. Mid-import failure leaves a half-deleted DB with no rollback.
- **CRITICAL** — `DataImporter.kt:282` import forcibly sets `parentTaskId = null` on every imported task. **Subtask hierarchies are destroyed** by export/import round-trip; users lose all parent/child relationships.
- **HIGH** — `DataImporter.kt:289–296` TaskTagCrossRef re-link uses tag *name* lookup (case-insensitive). Two tags with the same name (e.g., `Work` vs `work`) — wrong tag is bound silently.
- **HIGH** — Attachment files are not copied during export (DataExporter never injects the file content). On import, `AttachmentEntity.uri` points to a `file://` or `content://` path that doesn't exist on the new device → broken attachments.
- **HIGH** — `DataExporter.kt:296` CSV export drops everything except 10 fields (Title, Description, DueDate, DueTime, Priority, Project, Tags, Status, Created, Completed). No subtasks, no recurrence, no reminders, no attachments. The export UI does not warn the user that CSV is lossy.
- **HIGH** — REPLACE mode only deletes the 6 entity types above. `LeisureLogs`, `SelfCareLogs`, `Assignments`, `CourseCompletions`, `MoodEnergyLogs`, etc. survive the "replace" — old data silently merges with imported data.
- **MEDIUM** — `DataImporter.kt:619–625` `importConfig()` runs outside any transaction. Partial preference apply on failure → inconsistent state.
- **MEDIUM** — `DataImporter.kt:283` task `sourceHabitId` set to null on import; provenance lost (but column is half-deprecated, so impact small).
- **MEDIUM** — `NotificationProfileEntity.soundId` references `CustomSoundEntity` IDs that get regenerated on import. No remap → notification profiles point to invalid sound IDs after restore.
- **MEDIUM** — `MedicationRefillEntity` ↔ `SelfCareStepEntity.medication_name` linkage fragile across import: medications with case-different names silently collide on dedup; non-imported medication entity means link silently breaks.
- **MEDIUM** — `LeisureLogEntity` dedup key = `date` only; `CourseCompletionEntity` = `(courseId, date)`. MERGE mode silently skips records on collision; users importing same date from two backups lose one.
- **LOW** — CSV has "Due Time" column but writes ISO timestamp string. Format claim mismatches data; CSV is one-way (read-only).
- **LOW** — `NdPreferencesDataStore` is not exported (only `userPreferencesDataStore` injected at DataExporter.kt:74). All ND-mode prefs lost on restore.
- **LOW** — Google Drive backup just uploads the JSON blob — same omissions apply, plus no integrity hash on the uploaded file.

## 9. Concurrent write safety

- **CRITICAL** — `HabitCompletionDao.kt:49` `@Insert(onConflict = REPLACE)` but **no unique index on (habit_id, completed_date)**. Double-tap → two rows for the same day. Migration MIGRATION_6_7 only created non-unique indexes on `habit_id` and `completed_date` independently.
- **CRITICAL** — `HabitRepository.completeHabit()` (HabitRepository.kt:127–155) read-then-check-then-insert: reads count, branches on threshold, inserts. No mutex / not transactional. Two near-simultaneous taps both see `count = 0` and both insert.
- **CRITICAL** — `TaskRepository.completeTask()` (lines 210–242) does `recordCompletion()` (TaskCompletionEntity insert) → insert next recurrence task → `markCompleted()` original — **3+ writes, no `runInTransaction`**. Crash mid-flow leaves analytics inflated and the original task uncompleted, or a recurrence-only with no parent record.
- **CRITICAL** — `ReminderScheduler.kt:76` uses `taskId.toInt()` as alarm `requestCode`. Long → Int truncation collides for taskIds ≥ 2³¹. With autoincrement starting at 1 the practical risk is years away, but it is unavoidable lossy truncation.
- **CRITICAL** — Firestore writes use `docRef.set(data)` (SyncService.kt:291, 325) — full replacement, not `set(merge)`, no version vector. Concurrent two-device edits silently lose one device's fields.
- **CRITICAL** — Real-time listener fires while user is editing in `AddEditTaskViewModel`; pull calls `taskDao.update(task)` from cloud, blowing away in-progress unsaved edits with no isEditing guard (SyncService.kt:376–401, 492).
- **HIGH** — `TaskCompletionDao.kt:27` `@Insert(REPLACE)` but no unique key on `(task_id, completed_date)` — duplicate analytics rows possible from rapid double completion.
- **HIGH** — `SyncService` push: `docRef.set(...)` then `syncMetadataDao.upsert(...)` are two separate suspends. Crash between → pending action remains, next sync re-pushes → duplicate cloud doc with new cloudId.
- **HIGH** — `TaskRepository.deleteTasksByProjectId()` (lines 40–43) is not transactional with the subsequent widget refresh. Concurrent realtime pull can re-insert tasks post-delete, mid-flow.
- **MEDIUM** — Widget read concurrent with sync write: no isolation guarantee at the application level (Room provides snapshot-isolated reads, but the partial-state window from a 100-task pull means widgets can show inconsistent counts vs. the analytics tab).
- **MEDIUM** — `SyncMetadataEntity.syncVersion` is defined but **never incremented or compared** anywhere. The version vector field is unused — no actual conflict detection happens, anywhere.
- **MEDIUM** — `SyncService.isSyncing` is `@Volatile` but the `if (isSyncing) return@launch` check is read-then-act, no atomic CAS. Two concurrent listeners can both observe `false` and both proceed.
- **MEDIUM** — `HabitRepository.logActivity()` (lines 224–252): `habitLogDao.insertLog()` then `habitDao.update(habit)` separate. Crash between → habit booking flag stale relative to log.
- **MEDIUM** — `ReminderScheduler.kt:74–78` uses `FLAG_UPDATE_CURRENT`. Re-scheduling races with an in-flight broadcast can deliver an alarm with stale extras (wrong taskId in notification body).
- **LOW** — No row-level optimistic locking anywhere (no `@Update` with `WHERE updated_at = :expected`). All updates assume single-writer.

## Prioritized fix list

### Critical (data loss risk — fix before launch)
1. Wrap `DataImporter` REPLACE-mode wipe in `runInTransaction { }`; add the ~13 missing entities to both export and the REPLACE-clear list. (§8)
2. Add the missing entities to JSON export (`AttachmentEntity`, `BoundaryRuleEntity`, `CheckInLogEntity`, `CustomSoundEntity`, `FocusReleaseLogEntity`, `MoodEnergyLogEntity`, `NotificationProfileEntity`, `StudyLogEntity`, `WeeklyReviewEntity`, `MedicationRefillEntity`, all 3 template entities). (§8)
3. Stop forcing `parentTaskId = null` on import — remap and preserve the subtree. (§8)
4. Add unique index on `habit_completions(habit_id, completed_date)`; wrap `HabitRepository.completeHabit()` and `TaskRepository.completeTask()` in `runInTransaction { }` to prevent double-tap duplicates and partial completion writes. (§9)
5. Wire MIGRATION_42_43 to migrate existing `calendar_sync` rows to the new schema instead of dropping the table — at minimum, warn the user before nuking their mappings. (§2)
6. Fix Firebase pull idempotency for `habit_completions` / `habit_logs` — match by cloudId on update, not insert-only. (§4)
7. Wrap `{ entity write, sync_metadata upsert }` in `runInTransaction { }` everywhere in `SyncService` (lines 60, 381, 291, 325). (§4)
8. Sign-out and `resetAppData()` must clear `sync_metadata` (and pending action queue) to prevent cross-account contamination. (§3, §4)
9. Add isEditing / dirty-flag guard so realtime listener does not overwrite in-progress local edits. Switch Firestore writes to `set(data, SetOptions.merge())` and add a server-side timestamp version check. (§4, §9)
10. Pull path: actually create/update/delete tasks from `EventsPullResponse` — current calendar sync is one-way push only. (§5)
11. Drain `calendar_sync` rows in `PENDING_PUSH` / `PENDING_DELETE` / `ERROR` via a worker; otherwise failed pushes are permanent data loss. (§5)
12. Cap `incrementRetry()` in `SyncMetadataDao` and dead-letter operations after N failures; current code retries forever. (§4)

### High (fix this week)
13. Add foreign keys with `SET_NULL` on `study_logs.course_pick`, `study_logs.assignment_pick`, `focus_release_logs.task_id`, `tasks.source_habit_id`. (§1)
14. Add a maintenance worker to purge `usage_logs` (TTL 90d), `focus_release_logs` (call existing `deleteOlderThan`), and `task_completions` rows where `task_id IS NULL` AND `completed_date < now - 365d`. (§3)
15. When `setSyncCalendarId()` changes target calendar, mark old `calendar_sync` rows `PENDING_DELETE` and process them. (§5)
16. Delete the remote Google Calendar event before the local task delete (or hold the mapping until remote-delete confirms). Currently CASCADE removes the mapping first → orphan event. (§5)
17. Add `MigrationTestHelper`-based schema tests; flip `exportSchema = true`. (§2)
18. CSV export must either round-trip or be labelled "preview only — JSON for full backup." (§8)
19. Copy attachment files into the JSON export (or warn user that attachments are not portable). (§8)
20. Tag re-link in import must use stable IDs, not name lookup; or de-dup by name + color and warn on collision. (§8)
21. Use `set(data, SetOptions.merge())` and add `updated_at` LWW guard on Firestore push to stop concurrent two-device fields from overwriting each other. (§9)
22. Fix `HabitCompletionDao` / `TaskCompletionDao` `@Insert REPLACE` strategy — add unique constraints so REPLACE actually de-dups instead of inserting another autoincrement row. (§9)
23. Wire missing FK indexes on backend (`ActivityLog.user_id`, `TaskComment.user_id`, `ProjectInvite.inviter_id`, `TaskTemplate.template_project_id`). (§7)
24. Backend: standardize on `datetime.now(timezone.utc)` everywhere; remove naive `datetime.utcnow()` and `datetime.now()`. (§7)

### Medium (fix post-launch v1.0.1)
25. Sweep `WidgetConfigDataStore` on widget removal (call existing `clearForWidget()` from `AppWidgetProvider.onDeleted`). (§3)
26. Backend: convert `Task.priority`, `BugReportModel.status`, `IntegrationConfig.source` to proper `Enum` columns with `values_callable`. (§7)
27. Backend: add `UniqueConstraint("user_id", "title")` on `Project` and `Goal`. (§7)
28. Add FK on `self_care_steps.medication_name` → `medication_refills.medication_name` with `SET NULL` (or restructure to use refill IDs). (§1)
29. Sweep `self_care_logs.completed_steps` JSON when a step is deleted (or move to a join table). (§3)
30. Auto-archive worker should optionally purge archived tasks older than user-configured threshold. (§3)
31. Notification profile soundId remap on import. (§8)
32. Backend: provide `server_default` for `ProjectInvite.expires_at`. (§7)
33. Make sync_metadata version vector actually update + check on conflict. (§9)
34. Wrap multi-step recurrence completion (TaskRepository) and habit log+booking (HabitRepository.logActivity) in `runInTransaction`. (§9)
35. Validate `sync_state` against an enum at the DAO layer. (§5)

### Low
36. Provide a "delete history" UI for mood / check-in / weekly-review logs. (§3)
37. Log file-deletion failures in `AttachmentRepository` and `CustomSoundRepository`. (§3)
38. Per-instance `TimerStateDataStore` (currently global). (§3)
39. Document that `MIGRATION_15_16` left `create_daily_task` / `source_habit_id` as dead columns; consider a real `DROP COLUMN` migration. (§2)
40. Add CHECK constraint on `boundary_rules.start_time` / `end_time` HH:mm format. (§2)
41. Update CLAUDE.md: DB version is 44 (not 42), 43 migrations, Daily Essentials feature exists. (§2, §6)
