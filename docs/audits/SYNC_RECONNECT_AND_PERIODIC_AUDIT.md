# Sync — Reconnect Trigger + 30 s Periodic Floor — Audit

**Date:** 2026-04-29
**Scope:** Sync doesn't immediately resume when network returns from
airplane mode / wifi or data being toggled off-then-on. Pending writes
sit in the queue until the next *local* change re-triggers the push
loop. Additionally, the user wants a 30-second maximum sync cadence
floor so the app converges even when nothing reactive fires.

The two items share one code path (`SyncService.startAutoSync()`),
so they're audited together.

## Item 1 — Reconnect trigger (RED · PROCEED)

The reactive push loop in `SyncService.startAutoSync()`
(`app/src/main/java/com/averycorp/prismtask/data/remote/SyncService.kt:3125-3162`)
collects `syncMetadataDao.observePending().debounce(500)`. Inside the
collector, line 3137 reads `syncStateRepository.isOnline.value` and
short-circuits with `reason=offline` when the device is offline.

That short-circuit is the bug. The `Flow` only re-emits when the
*pending queue* changes — it does **not** re-emit when `isOnline`
flips. Once a write is enqueued offline:

1. `observePending` emits the queue contents.
2. After 500 ms debounce, the collector runs.
3. `isOnline.value == false` → return; queue stays as-is.
4. Network returns. `isOnline` flips `false → true`.
5. **Nothing fires.** The collector is dormant until another local
   edit changes the queue. The Firestore real-time listeners only
   fire on remote changes, and the per-app-launch `fullSync` already
   ran once on `MainActivity.onCreate`.

The only way out today is one of:
- Another local mutation re-triggers `observePending` (now-online,
  it pushes — including the previously-stranded entries, since
  `pushLocalChanges` pulls everything pending).
- The user manually taps "Force sync" via `SyncIndicatorViewModel`
  (`app/src/main/java/com/averycorp/prismtask/ui/components/sync/SyncIndicatorViewModel.kt:118`).
- The app process restarts and `startAutoSync` re-runs `fullSync`.

Neither `SyncService` nor any other class registers a reactive
listener on `isOnline`. Confirmed by:

```
grep -rn "isOnline\|networkMonitor" app/src/main --include="*.kt"
```

The only consumers are UI surfaces (`SyncIndicatorViewModel`,
`SyncDebugPanel`) that *display* the flag, and the synchronous
`.value` read inside the reactive push loop. There is no reactive
sync trigger keyed off connectivity.

**Secondary observation — `WhileSubscribed(5_000)` on `NetworkMonitor`**
(`app/src/main/java/com/averycorp/prismtask/utils/NetworkMonitor.kt:62`):
the upstream `ConnectivityManager.NetworkCallback` is only registered
while at least one collector is subscribed (5 s tail). Today the only
continuous collector is `SyncIndicatorViewModel` while its host
composable is on screen. If the indicator is off-screen and no other
subscriber exists, `isOnline.value` becomes stale and the
`isOnline` flip the fix relies on never lands. Adding `SyncService` as a
permanent collector closes this — once it `collect { }`s, the callback
stays registered for the app process's lifetime, which is the desired
behavior for a sync layer.

**Risk:** RED. User-facing data integrity issue — a task created in
airplane mode never reaches the cloud until the user happens to make
another change. Cross-device sync silently lags by hours.

**Recommendation:** PROCEED. Add a reactive coroutine in
`startAutoSync()` that observes `syncStateRepository.isOnline` and,
on a transition `false → true`, calls `fullSync(trigger = "network_resumed")`.
Use `fullSync` (not `pushLocalChanges`) so any remote changes that
landed during the offline window also pull. The existing
`if (isSyncing) return` guard at `SyncService.kt:2917` makes
double-trigger safe.

## Item 2 — 30 s periodic floor (YELLOW · PROCEED)

The user requested "automatically sync every 30 seconds at maximum"
i.e. **at least** every 30 s while online and signed-in.

There is currently no periodic timer in `SyncService`. The only
non-event-driven trigger is the per-launch `fullSync` in
`startAutoSync()` (`SyncService.kt:3113`). Confirmed by reading
`startAutoSync` end-to-end and grepping for `delay(`, `ticker(`,
`flow { while }`, `WorkManager`. `CalendarSyncWorker` is unrelated
(Calendar two-way sync, not Firestore).

A 30 s floor is also a defensive backstop against the Item 1 fix —
if the `isOnline` callback is missed for any reason (e.g. process was
in Doze, callback fired before SyncService started collecting,
`ConnectivityManager` reported a stale state), the periodic tick
will flush within 30 s.

**Risk:** YELLOW. No data-loss path, but matches an explicit user
ask and meaningfully improves convergence guarantees.

**Recommendation:** PROCEED. Add a coroutine in `startAutoSync()`
that loops `delay(30_000)` and calls `fullSync(trigger = "periodic_30s")`,
gated by `isOnline && userId != null && !isSyncing`. The
`isSyncing` flag both prevents stacking with manual / reactive runs
and means the timer naturally backs off when sync is already
in-flight (the next tick after a long sync just re-checks).

## Ranked improvements

| # | Change | Wall-clock impact | Cost | Ratio |
|---|--------|-------------------|------|-------|
| 1 | Reactive `isOnline` false→true → `fullSync` | Fixes the reported bug — pending writes flushed within ~1 s of network return instead of "next local edit / app restart". | ~30 lines, 1 unit test | High |
| 2 | 30 s periodic `fullSync` ticker | Bounds worst-case staleness to 30 s on top of Item 1 (defensive backstop + explicit user ask). | ~15 lines, 1 unit test | High |

Both ship in a single PR. They live in the same function
(`startAutoSync`), share the same gates (`isOnline`,
`userId != null`, `isSyncing`), and a shared regression test exercises
both behaviors via a fake clock + fake `isOnline` flow.

## Anti-patterns flagged (not fixed in this audit)

- **`SyncService.startAutoSync` is becoming a god-method.** Three
  separate `scope.launch { }` blocks (initial fullSync, reactive push
  on queue changes, and now reactive on reconnect + periodic) all
  inside one function. The existing
  `TODO(sync-refactor)` at `SyncService.kt:57` already calls out the
  split. Defer until that refactor lands; jamming all four launches
  into one function is the path of least resistance for now.
- **`WhileSubscribed(5_000)` documentation drift.** The companion
  comment on `NetworkMonitor.STOP_TIMEOUT_MS` says the callback is
  registered "while the UI layer is collecting." Item 1's fix makes
  `SyncService` a permanent collector, so the comment is no longer
  accurate post-fix. Update the comment in the same PR.
- **No structural test for `SyncService.startAutoSync` exists.** All
  five `*Sync*Test.kt` files cover mappers and fuzz, not the
  orchestration logic. Add a focused unit test in this PR rather
  than expanding scope to a full SyncService test harness.
