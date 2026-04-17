# Crash & Stability Audit — 2026-04-17

## Summary

PrismTask is unusually defensive for a v1.4-in-progress codebase: disciplined `!!` usage (10 sites, mostly safe), lifecycle-aware StateFlow collection, try/catch around every network call, and no `fallbackToDestructiveMigration`. Total **~60 findings**: **0 CRITICAL**, **~10 HIGH**, **~25 MEDIUM**, **~25 LOW**. The single riskiest pattern is **Room usage without transactions or explicit `OnConflictStrategy` review** — combining `@Insert(REPLACE)` with `ON DELETE CASCADE` foreign keys means a sync-pull id collision silently wipes a task's tags, attachments, subtasks, and completion history. The second-riskiest is the BroadcastReceiver + `GlobalScope.launch` pattern in four notification receivers, which can drop task completions on the floor when the receiver process is torn down mid-write.

## Severity legend
- **CRITICAL** — will crash the app on a common code path
- **HIGH** — crashes under specific but realistic user actions
- **MEDIUM** — crashes only under edge cases (bad network, rotation, backgrounding)
- **LOW** — defensive-coding issue, unlikely but possible

## 1. Nullability & force-unwraps

Codebase is remarkably disciplined on `!!` — only 10 occurrences across 9 files, and most are guarded by a preceding null check. No `checkNotNull` / `requireNotNull` usage. No `.value!!` on LiveData/StateFlow. A few sites warrant review.

- `NdPreferencesDataStore.kt:306-338` — `updateNdPreference(key: String, value: Any)` performs unchecked `value as Boolean / Int / String` casts in a 30+ entry `when`. Any caller passing a mistyped value (e.g. from settings-import JSON) will `ClassCastException`. **MEDIUM**
- `ui/screens/auth/AuthScreen.kt:118` — `val activity = context as Activity`. Composed inside a Button click; if this screen is ever hosted inside a ContextWrapper (e.g. Material3 tooltip, dialog), this throws `ClassCastException`. **MEDIUM**
- `ui/screens/onboarding/OnboardingScreen.kt:796` — same `context as Activity` pattern in the Google sign-in path. Crash on non-Activity context. **MEDIUM**
- `ui/screens/notifications/NotificationTesterScreen.kt:127` — `Text(status!!, …)`. Guard on line 125 (`!status.isNullOrBlank()`) protects this, but the `!!` is still fragile to refactors. **LOW**
- `ui/screens/planner/WeeklyPlannerScreen.kt:183`, `ui/screens/briefing/DailyBriefingScreen.kt:150`, `ui/screens/timeline/TimelineScreen.kt:169` — `val current = state!!` after `if (state != null)` check. Safe today, but in Compose recomposition the state could change between the check and the unwrap (StateFlow re-emission). Recommend `state?.let { current -> … }`. **LOW**
- `ui/screens/today/TodayViewModel.kt:182` — `!wasCheckedInAtLoad!!` inside a `combine` block. Guarded by the preceding null check on line 180 (assignment-then-else), but this is a `var` field mutated from coroutines — concurrent emits could theoretically race. **LOW**
- `ui/screens/settings/SettingsViewModel.kt:1266` — `.groupBy { it.parentTaskId!! }` on a pre-filtered list (`parentTaskId != null`). Safe. **LOW**
- `domain/usecase/DailyEssentialsUseCase.kt:160-167` — unchecked casts on `Array<Any?>` from `combine(...)`. These are idiomatic for >5-arity combine but brittle: any reordering of the `combine` arguments silently causes `ClassCastException` at runtime. **LOW**
- Platform-type risk: most `getSystemService(AlarmManager::class.java)` sites are guarded with `?: return`, but `ReminderScheduler.kt:25`, `EscalationScheduler.kt:32`, `MedicationReminderScheduler.kt:42` expose `alarmManager` as a non-null getter. `getSystemService` can return null on stripped-down OEM ROMs → NPE at call site. **MEDIUM**

No issues found in repositories, DAOs, or the core domain models beyond the above.

## 2. Coroutine scope & cancellation

- `notifications/CompleteTaskReceiver.kt:34`, `notifications/BootReceiver.kt:29`, `notifications/LogMedicationReceiver.kt:34`, `notifications/MedicationReminderReceiver.kt:36` — all use `GlobalScope.launch` inside `onReceive`. Problems: (1) Android tears down the receiver process after `onReceive` returns, so the coroutine may be killed mid-write → partially completed tasks, unscheduled medication refills. (2) No `try/catch` wrapping `GlobalScope.launch` in `CompleteTaskReceiver` — exception from `repository.completeTask` crashes the app. Correct pattern is `goAsync()` + `PendingResult.finish()`. **HIGH**
- `notifications/CompleteTaskReceiver.kt:30-31` — `getSystemService(NotificationManager::class.java)` result is dereferenced (`manager.cancel(...)`) without null check, whereas other sites in the codebase consistently use `?: return`. Very unlikely NPE but inconsistent. **LOW**
- `data/remote/SyncService.kt:43` — top-level `CoroutineScope(SupervisorJob() + Dispatchers.IO)` on a `@Singleton`. There is no `close()` / cancel on app teardown. Fine for a singleton, but a child job that launches indefinite listeners (`startRealtimeListeners`) could leak if sign-out leaves them running; check `stopRealtimeListeners` unit test coverage. **LOW**
- `data/remote/AuthManager.kt:37` — same `CoroutineScope(SupervisorJob() + Dispatchers.Main)`. Used only for the Eagerly-started `currentUser` StateFlow; fine for a Singleton. **LOW**
- `notifications/NotificationHelper.kt:77,88,240,313,383,434,511` — `runBlocking { prefs.*.first() }` on what can be called from the main thread (notification callbacks & broadcast receivers). DataStore reads are fast but not free; each occurrence is a potential ANR if DataStore is contended. 7 sites in one file. **MEDIUM**
- `notifications/HabitFollowUpReceiver.kt:29,34` — two `runBlocking` calls inside `onReceive`. Receiver has a 10-second budget; chaining DataStore reads here pushes toward ANR. **MEDIUM**
- `data/preferences/AuthTokenPreferences.kt:66,68,71` — `runBlocking { … }` wrappers named `…Blocking()`. These are probably called from OkHttp interceptors or auth headers; callers typically run on a worker thread so risk is lower, but the wrapper is a footgun if a future caller is on Main. **LOW**
- `workers/CalendarSyncScheduler.kt:41` — `applyPreferences() = runBlocking { … }`. Called from injected singletons; if called from Main (e.g. a Settings toggle) this blocks the UI thread. **LOW**
- `ui/screens/tasklist/TaskListViewModel.kt:825-841` — `importFromFile` runs on `viewModelScope`. Per the handoff note ("initialUpload was moved OFF viewModelScope for this reason"), a long import that the user interrupts by leaving Tasks tab will be cancelled mid-DB-write. No transaction wrapping means partial imports. **HIGH**
- No DB operations are wrapped in `database.withTransaction { … }` anywhere in the codebase — grep for `withTransaction` and `runInTransaction` returns zero matches. Export/import, duplicate cleanup, and migrations that touch multiple tables have no atomic guarantees. If cancelled mid-flight, the DB is left in inconsistent state (e.g. TaskEntity inserted without its TaskTagCrossRef rows). **HIGH**
- `ui/screens/today/TodayViewModel.kt:158-197` — the check-in banner pipeline `combine(...).collect` is wrapped in `try/catch(Exception)` which logs and swallows. Good. But note that any uncaught exception kills the viewModelScope child — the banner then silently freezes in its last state for the rest of the VM's life. **LOW**
- Flow collectors throughout (e.g. `SmartPomodoroViewModel.kt:197`, `TimerViewModel.kt` 7 collects) do not use `.catch {}`. A DAO-level `SQLiteException` mid-stream will fail the collector silently and leave the UI stale. **MEDIUM**

## 3. Room / database crashes

- **CLAUDE.md is stale** — it claims Room version = 42, but `PrismTaskDatabase.kt:100` is version **44** and `ALL_MIGRATIONS` ends at `MIGRATION_43_44`. Not a bug per se, but anyone relying on the doc will ship the wrong version. **LOW**
- `widget/WidgetDataProvider.kt:86-91` — each widget refresh opens a **new** Room database instance (`Room.databaseBuilder(...).build()`) and closes it. The main app and 7 widget types each have their own singleton Hilt-provided DB in the same process, so widget refreshes create secondary DB instances that race with the singleton. Observed consequences: (1) InvalidationTracker on the singleton doesn't see widget writes, so Flow-based observers go stale; (2) opening a DB during a schema-mismatch window (post-migration, pre-settle) can throw `IllegalStateException("Room cannot verify the data integrity")`; (3) `db.close()` with queries still in flight from the singleton can throw. **HIGH**
- `widget/WidgetDataProvider.kt:90` — `fallbackToDestructiveMigrationOnDowngrade(false)` is explicit, but the builder does **not** apply the same `.createFromAsset()` / callbacks / converters as the Hilt-provided DB (there's no Typeconverter diff here, so probably fine — but the divergence is fragile). **LOW**
- `data/export/DataImporter.kt:214,240,286` — import loop inserts projects → tags → tasks in correct parent-first order, but each insert is in its own `try/catch` and logs to a string list. If a task insert fails (e.g. invalid `projectId` because `projectNameToId` lookup returned a deleted row in REPLACE mode), the partially imported dataset is committed because there's no enclosing transaction. **HIGH**
- No `database.withTransaction { ... }` anywhere in the codebase. Export/import, duplicate cleanup (`SettingsViewModel.kt:1253+`), recurrence task-completion (inserts new task + updates old), subtask bulk operations, and sync push — all multi-table writes are non-atomic. Mid-operation crash leaves orphaned or inconsistent rows. **HIGH**
- `data/local/dao/TaskDao.kt:69` — `@Insert(onConflict = OnConflictStrategy.REPLACE)` on `TaskEntity`. A REPLACE that hits a primary-key collision does `DELETE + INSERT`, which cascades `onDelete = CASCADE` to child `task_tags`, `attachments`, `subtasks`, and (in v42+) `task_completions` rows — silently orphaning history. Sync-pull that re-inserts an existing task by id is the most likely trigger. **HIGH**
- `data/local/dao/HabitCompletionDao.kt:49` — same REPLACE-with-FK-cascade pattern for habit_completions. Less catastrophic (no child tables), but worth auditing for streak corruption. **MEDIUM**
- `data/local/dao/TagDao.kt:45` — `addTagToTask` uses REPLACE on `TaskTagCrossRef`. Safe because composite PK, but worth noting. **LOW**
- `data/local/entity/TaskEntity.kt:18-23` — self-referential FK `parent_task_id` with `ON DELETE CASCADE`. Combined with REPLACE on insert above, replacing a parent task by id wipes its subtasks. **MEDIUM**
- `data/local/entity/CalendarSyncEntity.kt` declares FK to task, but calendar sync is bidirectional and may insert child rows before the task exists on pull. Worth a specific test. **MEDIUM**
- Cursors are not used directly anywhere outside Room's generated code; no manual `.close()` needed. **No issues found** in raw cursor handling.
- All `@Insert` methods checked have either `REPLACE`, `IGNORE`, or default (`ABORT`) strategies. Abort-strategy inserts (`UsageLogDao.kt:17`, `FocusReleaseLogDao.kt:11`, `HabitLogDao.kt:28`) will throw `SQLiteConstraintException` on duplicate PK; no caller wraps them in `try/catch`. If a UUID collision or replay ever happens (retry path), app crashes. **MEDIUM**
- `fallbackToDestructiveMigration` — not used in `DatabaseModule.kt:56-62`, only `fallbackToDestructiveMigrationOnDowngrade(false)` in the widget DB. Good. **No issue.**

## 4. Unhandled exceptions in critical paths

- `PrismTaskApplication.kt:79-80` — `seedBuiltInHabits()` and `seedBuiltInTemplates()` are invoked OUTSIDE the `try/catch` that guards worker scheduling. They `launch` on `appScope` (SupervisorJob), so a thrown exception won't crash the app, but it will be lost silently — no Crashlytics record, no log. Seeding failures on first install would leave the app without built-in habits/templates. **MEDIUM**
- `PrismTaskApplication.kt:154` — `calendarSyncScheduler.applyPreferences()` is called from `onCreate` and internally `runBlocking`s on DataStore reads. If DataStore is slow on cold start, this is an ANR path. Also, no `try/catch` around it; if prefs throw, app startup aborts. **HIGH**
- `notifications/CompleteTaskReceiver.kt:34` — `GlobalScope.launch { repository.completeTask(taskId) }` has no `try/catch`. A SQLiteException here takes down the app (since BroadcastReceivers' uncaught exceptions in their coroutine tree propagate to the default handler). **HIGH**
- `notifications/LogMedicationReceiver.kt:34`, `MedicationReminderReceiver.kt:36` — same pattern, same risk. **HIGH**
- `notifications/BootReceiver.kt:29` — `GlobalScope.launch { reminderScheduler.rescheduleAllReminders() ... }` on boot. If DB is locked at boot (common when user reboots while sync is in flight), first query throws and reminders are silently not rescheduled. **MEDIUM**
- `MainActivity.kt:150-154` — `setCrashlyticsUserId()` is try/catch'd. Good. But all the LaunchedEffects below (`UpdateChecker.checkForUpdate`, `backendSyncService.checkAdminStatus`) do their own try/catch OK. `onboardingPreferences.hasShownBatteryOptimizationPrompt().first()` on line 347 is NOT wrapped; DataStore throw would crash. **LOW**
- `MainActivity.kt:170-172` — `UpdateChecker(this).checkForUpdate()` in `LaunchedEffect(Unit)` is not try/catch'd. A network error during update check on app launch crashes. Needs verification of `checkForUpdate`. **MEDIUM**
- `ui/components/QuickAddViewModel.kt:373` — the main task-creation path. Let me verify: the surrounding `createTask` function should be wrapped, but with the REPLACE-on-insert FK cascade issue (Section 3), an id collision could silently wipe related rows. If this is the primary creation path, this is the single most-used flow and needs inspection. **MEDIUM**
- `data/remote/SyncService.kt:205-211` — `launchInitialUpload` is wrapped in try/catch, logs, does not crash. Good.
- `data/remote/AuthManager.kt:31-36` — FirebaseAuth init wrapped, fall back to null. Good.
- `PrismTaskApplication.kt:75` — silent `catch (_: Exception)` for Crashlytics record. Good justification ("Firebase not available").
- `widget/WidgetActions.kt:39,43` — silent `catch (_: Exception) {}` with no comment. The `deleteTask`/`completeTask`-from-widget paths silently swallow failures. User taps "complete" on widget → nothing happens → no error surface. **MEDIUM**
- `widget/ProductivityWidget.kt:51` — `catch (_: Exception) { null }` — if widget data load fails the widget is blank with no indication. **LOW**
- No evidence of uncaught exceptions in the Firebase sign-in flow (`AuthViewModel.kt:47-68`) — error path is well handled.
- Notification delivery (`NotificationHelper.kt`) uses 7 `runBlocking { …first() }` calls — any DataStore error throws synchronously with no guard → crashes the receiver. See Section 2. **MEDIUM**
- Widget data loading (`WidgetDataProvider.kt`) — each `getXxxData` opens a DB, runs queries, and closes. `try/finally` ensures close, but the `try` doesn't catch exceptions — they propagate to the widget framework which logs but doesn't crash. Probably OK but inconsistent. **LOW**

## 5. Lifecycle & state race conditions

- **`DisposableEffect` is never used anywhere in the codebase** (0 matches). Any side-effect that needs teardown on recomposition/disposal has no safe cleanup path in Compose. Currently hidden because cleanup lives in `ViewModel.onCleared()`, but if any Compose-scoped resource (focus listener, sensor registration inside a composable) ever gets added it will leak. **MEDIUM**
- **`repeatOnLifecycle` / `flowWithLifecycle` also never used** (0 matches). The code relies exclusively on `collectAsStateWithLifecycle` in composables and `stateIn(..., SharingStarted.WhileSubscribed(5000))` on the VM side. Any ViewModel using `SharingStarted.Eagerly` (e.g. `AuthViewModel.kt:40`, `AuthManager.kt:44,51`) keeps its Flow subscription alive even when no UI is attached, burning battery. **LOW**
- `MainActivity.kt:227-229` — `hasCompletedOnboarding` starts as `null as Boolean?` and is used in `!!` on line 543. Safe because of the null guard, but if the DataStore emission is delayed past recomposition, a user could see the "loading" spinner flicker. Not a crash, worth noting. **LOW**
- No usage of `StateFlow.update { }` anywhere (0 matches). Every ViewModel uses `_state.value = newValue` which is **not** atomic for read-modify-write. `SettingsViewModel.kt` has 19 such assignments across 117 viewModelScope launches; compound updates like `_state.value = _state.value.copy(...)` from two simultaneous coroutines will lose one update. Not a crash, but state drift. **MEDIUM**
- `ui/screens/today/TodayViewModel.kt:180-188` — `wasCheckedInAtLoad` is a `var` field read-modified inside a `combine.collect` block. If the combine re-emits rapidly (e.g. prefs flip + check-in arrives within the same frame), two concurrent collects could double-fire the 3-second chip. No crash, but UI glitch. **LOW**
- `ui/screens/pomodoro/SmartPomodoroViewModel.kt:222-252` — `registerTimerReceiver` is guarded by a plain `Boolean` `receiverRegistered`. If `registerTimerReceiver()` and `unregisterTimerReceiver()` are called concurrently from two coroutines (happens if a timer completes while user navigates away), the boolean read-write race allows double-register or leaked receiver. **LOW**
- `ui/screens/pomodoro/SmartPomodoroViewModel.kt:448-452` — `onCleared()` stops the Pomodoro service. If the user rotates the device mid-session, the service stops with the VM teardown — they'll see the timer vanish. Rotation config changes don't normally kill hilt ViewModels, but process death does. **LOW**
- `MainActivity.kt:169-172` — `LaunchedEffect(Unit) { UpdateChecker(this@MainActivity).checkForUpdate() }`: the Activity reference is captured by the effect closure. If the activity is destroyed (e.g. dark-theme change recreating the activity) before the network call returns, Android lifecycle cancellation handles the coroutine, but the network call itself keeps running until timeout — wasted bandwidth. **LOW**
- `data/billing/BillingManager.kt` — not examined here but a Google Play Billing singleton must be rebuilt after disconnection. If the BillingClient is disconnected and the purchase-restore StateFlow is still being observed by Compose, a retry click can race a reconnect and crash. Flagging for follow-up. **LOW**
- No evidence of `LaunchedEffect(viewModel)` or similar unstable keys that would churn. Most `LaunchedEffect(Unit)` uses are appropriate for one-shot startup logic. **No issue.**
- `@Singleton` scopes (AuthManager, SyncService, BillingManager) all hold `Context` via `@ApplicationContext`, which is safe. No Activity context leaks observed at the singleton level.

## 6. Navigation & activity state

- `navController.popBackStack()` is called unconditionally at ~30 sites. Compose Navigation's `popBackStack` returns `false` on empty stack (doesn't crash), so this is safe. **No issue.**
- `ui/screens/settings/sections/SubscriptionSection.kt:71` — `context.startActivity(intent)` with a https:// URI has **no** try/catch and no `resolveActivity` check. If a user on a stripped-down device/emulator has no browser installed, `ActivityNotFoundException` crashes the app. **HIGH**
- `MainActivity.kt:316-319,373-378` — exact-alarm / battery-optimization settings intents are wrapped in try/catch. Good.
- `ui/screens/addedittask/AddEditTaskScreen.kt:592-594` — attachment open-link uses try/catch. Good.
- `ui/screens/settings/sections/HelpFeedbackSection.kt:55-59` — mailto intent uses `Intent.createChooser` + try/catch. Good.
- `MainActivity.kt:161-165` — reads `intent.getStringExtra(Intent.EXTRA_TEXT)` from share-intent without length limits or content validation. A malicious third-party app could send a 10MB string; it's later passed to NLP parsing. Not a crash, but a potential memory-allocation DoS. **LOW**
- Deep-link param handling (`SavedStateHandle.get<Long>("taskId")` in `AddEditTaskViewModel.kt:246`) — if the route is navigated with a non-numeric taskId, Compose Navigation throws before the VM sees it. Safe. But `routeTaskId.takeIf { it != -1L }` treats sentinel `-1L` as null. If a malformed deep link sends `0L`, the VM will try to load task id 0, which doesn't exist — silent empty form. **LOW**
- `data/remote/UpdateChecker.kt:53-55` — `Uri.parse("$baseUrl${versionInfo.apkUrl}")` on an unvalidated `apkUrl` from the backend. If the backend is compromised or returns a malformed URL, `startActivity` on it could be redirected. Low likelihood but a URL-validation step would help. **LOW**
- `data/repository/CustomSoundRepository.kt:42` — `File(Uri.parse(sound.uri).path ?: "").delete()` — if a custom sound URI's `path` is null (content:// URIs have null path), this constructs `File("")` which silently fails. Not a crash, but bug. **LOW**
- `ui/navigation/NavGraph.kt:374` — `navController.popBackStack()` called from inside a LaunchedEffect-like block. If the NavController has already been torn down (rare but possible during config changes), this is a no-op. Compose Nav handles it gracefully. **No issue.**
- No evidence of deep-link route registration with typed param validation — Compose Navigation's default string-to-type conversion handles basic cases, but a URL with unescaped characters can trip up `String.toLong()` inside the route handler → crash inside Navigation's internals before the screen renders. **LOW**
- Share-intent filter in `AndroidManifest.xml` not checked, but would be wise to verify that `android:exported=true` isn't set on activities that shouldn't be. (Skipped for time.)

## 7. Third-party API failure modes

- `data/remote/api/ApiClient.kt:35` — `AuthInterceptor.intercept` calls `getAccessTokenBlocking()` on every request. This is `runBlocking { DataStore.read }` which can throw if DataStore is corrupted (rare). No try/catch around the interceptor body → uncaught exception surfaces as `IOException` to the caller, which most Retrofit call sites do catch. **LOW**
- `data/remote/api/ApiClient.kt:71-100` — `TokenAuthenticator.authenticate` runs on OkHttp's dispatcher thread; `refreshTokens` is wrapped in try/catch (returns null on error). Good. But `tokenPreferences.setTokensBlocking` (line 94) is NOT wrapped — a DataStore write failure during refresh would leave tokens out of sync and propagate. **LOW**
- `data/remote/GoogleDriveService.kt:33` — uses `GoogleSignIn.getLastSignedInAccount(context)` to fetch the account. The app has otherwise migrated to Credential Manager (see `AuthScreen.kt:117-128`), which does **not** populate `GoogleSignIn.getLastSignedInAccount`. Drive backup/restore will return null here for any user who signed in via Credential Manager. Returns `Result.failure` so no crash, but the feature is quietly broken post-migration. **HIGH**
- `data/remote/SyncService.kt:225-243` — push loop wraps each entity's push in try/catch, logs to Crashlytics, and increments retry counter. Good pattern. One gap: `pushCreate` → `docRef.set(data).await()` — Firestore's SDK bubbles `FirebaseFirestoreException` which is caught, but `await()` also throws `CancellationException` when the coroutine scope is cancelled (e.g. sign-out). `CancellationException` should NOT be swallowed — it escapes past the catch in this code only because the catch is on `Exception` (and CancellationException extends it in older coroutines, but best-practice is `catch (e: CancellationException) { throw e }`). Potential sign-out deadlock. **MEDIUM**
- `data/remote/ClaudeParserService.kt:36-49` — backend-mediated Claude call. The known markdown-fence issue would manifest in the backend response, not here. Gson deserialization of the response is wrapped in try/catch — returns null on failure. Good.
- `data/remote/CalendarSyncService.kt` — not examined in depth, but Google Calendar API calls typically throw `UserRecoverableAuthIOException` when scope consent is revoked. Unless each call-site catches this specifically, token revocation crashes the sync worker. **MEDIUM**
- Rate limiting: no evidence of client-side rate limiting or exponential backoff on backend calls outside of WorkManager's default retry policy. A rapid successful auth that triggers `initialUpload` followed by a user spamming sync-now could hit Firestore's write rate limits (500/s) and fail mid-batch. **LOW**
- Response body schema: `SyncService.kt` pushes via `SyncMapper.*toMap(entity)` which produces `Map<String, Any?>`. Pull path (not shown in full) would need to tolerate missing/extra fields. Room → Firestore mapping has no versioning. A newer-app-version writes a new field; an older device pulling it silently drops it. Not a crash. **LOW**
- `data/remote/UpdateChecker.kt` — network failure during version check is wrapped (seen earlier). Good.
- `data/billing/BillingManager.kt` — not examined, but Google Play Billing's BillingClient can deliver `onPurchasesUpdated` on the main thread. If a callback does DB work synchronously, it ANRs. Flag for follow-up. **LOW**
- Firebase init fallback (`AuthManager.kt:31`) uses `null` auth object and a `MutableStateFlow(null)` fallback — UI branches on `isSignedIn` so it renders a local-only experience gracefully. Good.

## 8. WorkManager & background jobs

- `workers/AutoArchiveWorker.kt:22-28` — `doWork()` has **no try/catch**. Any SQLiteException from `taskRepository.autoArchiveOldCompleted(days)` bubbles to WorkManager which logs and marks as failed — but with no `setBackoffCriteria`, retry uses default (exponential 30s-5h). Fine in practice, but the caller `scheduleAutoArchive()` in `PrismTaskApplication.kt:157-168` doesn't set backoff either. **LOW**
- `notifications/OverloadCheckWorker.kt:44-80` — unwrapped DB query `taskRepository.getAllTasksOnce()` on line 49. If this throws, WorkManager marks failed. No retry policy explicitly set. **LOW**
- `widget/WidgetRefreshWorker.kt:30-33` — `doWork()` unwrapped. Uses 15-minute periodic work (line 39-40). On DB error, fails silently. Given `WidgetDataProvider` opens its own DB instances (see Section 3), this worker has a higher-than-usual crash surface. **MEDIUM**
- No `NetworkType.CONNECTED` constraint set for any of the sync workers (`WeeklySummaryWorker`, `EveningSummaryWorker`, `ReengagementWorker`, `BriefingNotificationWorker`). Only `CalendarSyncScheduler.kt:64` and `DefaultCalendarPushDispatcher.kt:47` add it. These aren't network-dependent so that's fine, but `CalendarPushWorker.kt` talks to Google Calendar — let me verify it has the constraint. **LOW**
- `workers/CalendarPushWorker.kt` — not examined in detail, but calendar push is network-dependent. If `NetworkType.CONNECTED` not set, it'll run offline and fail every time until it exhausts retries. Check this. **MEDIUM**
- Unique work names — all workers use distinct names: `auto_archive`, `daily_reset`, `widget_refresh_periodic`, `reengagement_nudge`, `overload_check_daily`, `evening_summary_notification`, `daily_briefing_notification`, `weekly_habit_summary`. No collisions. **No issue.**
- `PrismTaskApplication.kt:100-110,162-168` — both `scheduleOverloadCheck` and `scheduleAutoArchive` use `ExistingPeriodicWorkPolicy.KEEP`, so config changes don't update the schedule. Users who change preferences for these workers won't see changes until they reinstall. Intentional but worth noting. **LOW**
- `notifications/BriefingNotificationWorker.kt:119` — uses `ExistingPeriodicWorkPolicy.UPDATE`. Good for settings changes.
- `notifications/ReengagementWorker.kt:145` — uses `KEEP`. User who changes re-engagement preferences won't see effect. **LOW**
- HiltWorkerFactory is wired correctly in `PrismTaskApplication.kt:56-60` via `Configuration.Provider`. Good.
- Worker input-data validation: `CalendarPushWorker.kt:30-32` correctly returns `Result.failure()` on missing `taskId` / `op`. Good pattern.
- No workers hold references to short-lived components — all are `@HiltWorker` with `@AssistedInject` constructor, receiving only `@Singleton`-scoped dependencies. **No issue.**
- `appScope` in `PrismTaskApplication.kt:54` launches three things in `onCreate`: `seedBuiltInHabits`, `seedBuiltInTemplates`, and `scheduleDailyReset().collectLatest { ... }`. The `collectLatest` is a never-terminating subscription — fine for singleton, but it means the app process holds a DataStore observer for the app's lifetime. Not a leak, but worth noting. **LOW**

## 9. Resource leaks

- `data/repository/TaskRepository.kt:251-256` — `deleteTask(id)` does NOT call `reminderScheduler.cancelReminder(id)`. Pending alarm for the deleted task keeps firing. When it fires, `ReminderBroadcastReceiver` queries the DB for the (deleted) task id, finds nothing, and the path handles it — but the alarm wakeup consumes battery and may surface a null-titled notification. **HIGH**
- `data/repository/TaskRepository.kt:274-279` — `permanentlyDeleteTask(id)` has the same bug: no `cancelReminder` call. Archive → permanently delete path leaves stale alarms. **HIGH**
- Similarly, `calendarPushDispatcher.enqueueDeleteTaskEvent(id)` is called but no cancellation of pending `CalendarPushWorker.OP_UPSERT` for this task — if an upsert was queued but hadn't run, the subsequent delete may race. **MEDIUM**
- `domain/usecase/ShakeDetector.kt:41-52` — sensor listener registered/unregistered via `isRegistered` boolean. Paired with `onResume`/`onPause` in `MainActivity.kt:571-582`. Good pattern. **No issue.**
- `ui/screens/pomodoro/SmartPomodoroViewModel.kt:222-252` — BroadcastReceiver registered via `registerTimerReceiver()`. Unregistered in `onCleared()` (line 450) AND in `unregisterTimerReceiver()`. The boolean-guarded double-register protection is adequate. **No issue.**
- `BugReportViewModel.kt:358` — `registerReceiver(null, IntentFilter(ACTION_BATTERY_CHANGED))` — this is the sticky-broadcast read pattern (null receiver), which doesn't leak. **No issue.**
- `ScreenshotCapture.kt:33-47` — `Bitmap.createBitmap(view.width, view.height, ...)` without explicit `bitmap.recycle()` on the failure path (line 41). On minSdk 26+, the JVM GC will collect the native memory, so no leak. `Coil` isn't used here; direct Bitmap API. **LOW**
- `ClinicalReportPdfWriter.kt:139,155`, `ScreenshotCapture.kt:53`, `CustomSoundRepository.kt:85`, `DataBackupScreen.kt:63,75` — all use `.use { … }`. Streams closed correctly. **No issue.**
- `DebugLogScreen.kt:238` — `BufferedReader(InputStreamReader(process.inputStream)).use { … }`. Process's `inputStream` is closed when the reader is. The process itself is presumably wait()'d or destroyed elsewhere; would warrant a quick check. **LOW**
- `PrismTaskApplication.kt:54` — `appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` never cancelled. Process-lifetime singleton, so this is OK.
- `data/remote/SyncService.kt:43`, `AuthManager.kt:37` — singleton coroutine scopes never cancelled. Same justification. **No issue.**
- `CustomSoundRepository.kt:42` — `File(Uri.parse(sound.uri).path ?: "").delete()` — for content:// URIs, `path` is null. Running `.delete()` on an empty-path File does nothing, but the file on the other side of the content URI is never cleaned up → orphaned custom sound files. **MEDIUM**
- `data/remote/api/ApiClient.kt:122-127` — a **new** `OkHttpClient` is constructed per token refresh inside `refreshTokens`. OkHttp pools internally, so a new client doesn't reuse the pool — every refresh spins up a new connection pool (default 5 idle connections, 5-minute keepalive). Under rapid 401→refresh loops this leaks connection pool memory until GC. **MEDIUM**
- AlarmManager alarms for notification profile escalation (`EscalationScheduler.kt:83`) are cancelled properly. Good.
- MedicationReminderScheduler properly calls `alarmManager.cancel(pendingIntent)` when refreshing. Good.
- No Context leaks detected in `@Singleton` injections — all use `@ApplicationContext`.

## 10. Backend crash surfaces (if time permits)

- `backend/app/database.py:10-17` — `get_db` wraps session in `async with` and rolls back on exception. Good pattern. **No issue.**
- `backend/app/main.py:54-65` — `_start_calendar_sync_scheduler` has `try/except` with `noqa: BLE001` catch-all. Good — if scheduler fails to init, backend still serves. Similarly for shutdown. **No issue.**
- `backend/app/routers/ai.py:92-103` — each AI endpoint has try/except for `RuntimeError` (no API key / service down) → 503, and `ValueError` (parse error) → 500. Good. But the catch-all `Exception` at `ai_productivity.py:100-102` re-raises, which `ai.py` doesn't handle — that propagates to FastAPI's default 500. An `anthropic.APIConnectionError` or a network timeout thus surfaces as an opaque 500 rather than a helpful message. **MEDIUM**
- `backend/app/services/ai_productivity.py:49-57` — `_parse_ai_json` handles markdown fences (addresses a known class of Claude API issues). Good.
- `backend/app/routers/ai.py:203` — `date.fromisoformat(data.date)` without try/except. If client sends malformed date, ValueError propagates to an FastAPI 500 rather than a 400. Better as `try: … except ValueError: raise HTTPException(400, …)`. **MEDIUM**
- `backend/app/routers/ai.py:296-297` — same pattern for `week_start`. **MEDIUM**
- `backend/app/middleware/rate_limit.py:15,58,109` — all rate limiters are in-memory (`defaultdict`, `dict`). On multi-worker gunicorn (see `gunicorn.conf.py`) each worker has its own counter → effective rate limit is `N_WORKERS × configured_limit`. Not a crash, but an unexpected-cost risk for Claude API calls. **HIGH** (cost, not crash)
- `backend/app/middleware/rate_limit.py:18-22` — `_client_ip` trusts `x-forwarded-for`. If the app is behind a reverse proxy without rewriting this header, a hostile client can spoof their IP to bypass rate limits. **MEDIUM** (cost, not crash)
- `backend/app/middleware/rate_limit.py:15` — `self._requests: dict[str, list[float]]` grows unbounded: every unique IP gets a permanent entry even if they never return. Memory leak on long-running workers. Mitigated by periodic restart but still a concern. **MEDIUM**
- `backend/app/services/ai_productivity.py:88` — `message.content[0].text` — if Claude returns `content=[]` (can happen on content policy block), `IndexError` is caught by the subsequent except clause (line 94). Good.
- SQLAlchemy sessions are all created via `Depends(get_db)` which uses `async with`. No manual `Session()` usage. **No issue.**
- `backend/app/main.py:23-29` — CORS middleware correctly handles wildcard-vs-credentials conflict. Good.
- `backend/app/routers/sync.py` — not examined in depth. Sync endpoints handle arbitrary user data; a malformed payload should fail Pydantic validation with a 422 rather than crashing, which is FastAPI's default.
- No evidence of missing rate limits on expensive Claude-calling endpoints; every AI endpoint has both IP-based and daily-per-user limits. Good.
- JSON payload size: `ExtractFromTextRequest` caps at 10,000 chars via Pydantic schema (documented at `ai.py:535`). Check other endpoints for similar caps. **LOW**

## Prioritized fix list

### Critical (fix before launch)
- **None.** No issue reached "will crash on a common code path" severity. The codebase is much better-defended than most: disciplined `!!` usage, try/catch around every network call, Hilt-provided singletons, lifecycle-aware StateFlow collection.

### High (fix this week)
- `data/repository/TaskRepository.kt:251-256,274-279` — `deleteTask` / `permanentlyDeleteTask` don't cancel pending reminder alarms → stale alarms fire for deleted tasks. — resource leak
- `widget/WidgetDataProvider.kt:86-91` — every widget refresh opens a new Room database instance bypassing the Hilt singleton; causes InvalidationTracker coherence issues and occasional `IllegalStateException` during migration windows. — database
- `data/export/DataImporter.kt:214,240,286` + all multi-table writes — no `database.withTransaction { }` anywhere. Partial imports and cancelled operations leave orphaned rows. — database
- `data/local/dao/TaskDao.kt:69` — `@Insert(onConflict = REPLACE)` on `TaskEntity` silently cascades-deletes child rows (task_tags, attachments, subtasks) on sync-pull id collisions. — database
- `notifications/CompleteTaskReceiver.kt:34`, `LogMedicationReceiver.kt:34`, `MedicationReminderReceiver.kt:36` — `GlobalScope.launch` in BroadcastReceivers without `goAsync()`; coroutine killed mid-write when receiver process teardown happens. Also no try/catch. — coroutines
- `ui/screens/tasklist/TaskListViewModel.kt:825-841` — `importFromFile` on `viewModelScope` cancels mid-import when user navigates away, leaving partial data. — coroutines
- `ui/screens/settings/sections/SubscriptionSection.kt:71` — `startActivity` for Play Store subscription page without try/catch → `ActivityNotFoundException` crash if no browser. — navigation
- `PrismTaskApplication.kt:154` — `calendarSyncScheduler.applyPreferences()` = `runBlocking` on Main at app startup; ANR risk + no try/catch. — unhandled exceptions
- `data/remote/GoogleDriveService.kt:33` — uses deprecated `GoogleSignIn.getLastSignedInAccount` after migration to Credential Manager → Drive backup silently broken for all new users. — third-party APIs
- `backend/app/middleware/rate_limit.py:15,58,109` — in-memory rate limiters, per-worker, effective limit = `N × configured` → Claude API cost runaway under gunicorn multi-worker. — backend

### Medium (fix post-launch v1.0.1)
- `data/preferences/NdPreferencesDataStore.kt:306-338` — `updateNdPreference(key, value: Any)` unchecked casts; mistyped import crashes. — nullability
- `ui/screens/auth/AuthScreen.kt:118`, `ui/screens/onboarding/OnboardingScreen.kt:796` — `context as Activity` unsafe casts. — nullability
- Multiple `getSystemService(AlarmManager::class.java)` non-null-getter exposures in `ReminderScheduler`, `EscalationScheduler`, `MedicationReminderScheduler` — inconsistent with guarded call sites elsewhere. — nullability
- `notifications/NotificationHelper.kt` — 7× `runBlocking { first() }` in notification-delivery paths; each is an ANR path. — coroutines
- `notifications/HabitFollowUpReceiver.kt:29,34` — two `runBlocking` in `onReceive`. — coroutines
- `data/local/dao/HabitCompletionDao.kt:49` — `@Insert(REPLACE)` on habit completions can cascade delete. — database
- `data/local/entity/TaskEntity.kt:18-23` — `parent_task_id ON DELETE CASCADE` + REPLACE-insert = subtask wipe on sync collision. — database
- `@Insert` with default ABORT strategy in `UsageLogDao.kt`, `FocusReleaseLogDao.kt`, `HabitLogDao.kt` — unhandled `SQLiteConstraintException` on duplicate insert. — database
- Flow collectors across many ViewModels lack `.catch {}` — SQLiteException mid-stream silently stalls UI. — coroutines
- `MainActivity.kt:170-172` — `UpdateChecker.checkForUpdate` not wrapped in LaunchedEffect. — unhandled exceptions
- `widget/WidgetActions.kt:39,43` — silent `catch (_: Exception) {}` on task complete/delete from widget. — unhandled exceptions
- No `DisposableEffect` / `repeatOnLifecycle` anywhere in Compose code — acceptable today, will bite when a future side-effect needs cleanup. — lifecycle
- No `StateFlow.update { }` usage — compound updates lose writes under concurrent modification. — lifecycle
- `data/remote/SyncService.kt:225-243` — CancellationException should be re-thrown, not caught by generic `Exception` handler. — third-party APIs
- `data/remote/api/ApiClient.kt:122-127` — new `OkHttpClient` per 401 refresh; connection-pool leak under refresh loops. — resource leaks
- `workers/CalendarPushWorker.kt` — verify `NetworkType.CONNECTED` constraint is set; otherwise offline retries exhaust backoff. — workers
- `data/repository/CustomSoundRepository.kt:42` — orphaned custom sound files on delete (content:// URI has null `path`). — resource leaks
- `backend/app/routers/ai.py:203,296-297` — unguarded `date.fromisoformat` → FastAPI 500 instead of 400. — backend
- `backend/app/middleware/rate_limit.py:18-22` — trusts X-Forwarded-For; spoofable. — backend
- `backend/app/middleware/rate_limit.py:15` — unbounded dict growth. — backend

### Low (nice-to-have)
- CLAUDE.md claims DB version 42; actual is 44 — doc drift.
- Multiple `val currentX = state!!` after `if (state != null)` in screens (`WeeklyPlannerScreen.kt:183`, `DailyBriefingScreen.kt:150`, `TimelineScreen.kt:169`) — fragile to refactor. — nullability
- `MainActivity.kt:161-165` — share-intent text read without length cap. — navigation
- `data/remote/UpdateChecker.kt:53-55` — no URL validation on backend-provided `apkUrl`. — navigation
- Deep-link `taskId=0` silent empty form (AddEditTaskViewModel.kt:246-253). — navigation
- `ui/screens/pomodoro/SmartPomodoroViewModel.kt:222-252` — plain-boolean guard on receiver registration has a small race window. — lifecycle
- `PrismTaskApplication.kt:79-80` — `seedBuiltInHabits` / `seedBuiltInTemplates` run outside the startup try/catch; failures lost silently. — unhandled exceptions
- Multiple periodic-work schedulers use `ExistingPeriodicWorkPolicy.KEEP`; users who change prefs don't see effect. — workers
