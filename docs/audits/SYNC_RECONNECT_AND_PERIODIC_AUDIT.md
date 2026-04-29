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

## Phase 3 — bundle summary

Both items shipped together in PR #944
(`fix/sync-reconnect-and-periodic`, branched from
`origin/main` at SHA `8d5ffe08`). Auto-merge enabled with squash;
required CI gates the actual merge.

**Per-improvement:**

| # | Item | Result |
|---|------|--------|
| 1 | Reactive `isOnline` false→true → `fullSync` | Implemented in `ReactiveSyncDriver` and wired via `SyncService.startAutoSync`. Pending writes now push within ~1 s of network return instead of "next local edit / app restart". |
| 2 | 30 s periodic `fullSync` ticker | Implemented in the same driver. `PERIODIC_SYNC_INTERVAL_MS = 30_000L` companion constant on `SyncService` documents the user-requested cap. |

**Test coverage:** 6 unit tests under
`app/src/test/java/com/averycorp/prismtask/data/remote/sync/ReactiveSyncDriverTest.kt`,
all passing locally in 0.054 s total.

**Anti-patterns from the audit revisited:**

- Inline-launch sprawl in `startAutoSync` was deliberately preserved
  (now 4 launches). Defer the `TODO(sync-refactor)` split.
- `NetworkMonitor.STOP_TIMEOUT_MS` doc comment was updated in the
  same PR to note that `SyncService` is now a permanent collector.

**Wall-clock re-baseline:** end-to-end PR took ~30 minutes of work
(audit + driver + tests + verification + commit). Real impact
measurement requires a multi-device manual test the user can do once
PR #944 lands.

**Memory candidates (only the surprising ones):**

- The repo's `kotlinx-coroutines-test` 1.9.0 makes `runTest`'s
  `backgroundScope` the right place to launch never-ending coroutine
  loops in tests — auto-cancelled at end of test, no manual `cancel`
  needed. Worth remembering for future virtual-time tests.
- `MockK 1.13.13` exposes `captureNullable` only as a method on
  `MockKMatcherScope` (not as a top-level function), so importing
  `io.mockk.captureNullable` fails with "Unresolved reference" but
  calling it bare inside a `coEvery { ... }` block resolves fine.

**Schedule for next audit:** none — both items closed.

## Phase 4 — Claude Chat handoff

```markdown
**Scope.** PrismTask Android app — fix two sync cadence issues at once:
(1) sync didn't fire when the network returned from airplane mode /
Wi-Fi off, only on the next local edit or app restart; (2) user
requested a 30 s maximum sync cadence floor while online + signed-in.

**Verdicts.**

| Item | Verdict | Finding |
|------|---------|---------|
| Reactive reconnect trigger | RED · PROCEED | `SyncService.startAutoSync` (`SyncService.kt:3068-3163`) reactive push loop only re-emits on pending-queue changes, not on `isOnline` flips, so offline writes stranded until next mutation. |
| 30 s periodic floor | YELLOW · PROCEED | No periodic timer existed; `fullSync` only ran once per app launch + per-listener pull + per-queue-change push. |

**Shipped.** PR #944 `fix(sync): trigger sync on reconnect + 30 s
periodic floor` — adds `data/remote/sync/ReactiveSyncDriver.kt` with
two coroutine launches (false→true edge + 30 s ticker), wires it from
`startAutoSync`, plus 6 pure-JVM unit tests in
`ReactiveSyncDriverTest`. Branched off `8d5ffe08`; auto-merge
squashed. Also touched
`utils/NetworkMonitor.kt` companion-object comment to reflect that
`SyncService` is now a permanent collector.

**Deferred / stopped.** None — both audited items shipped together.

**Non-obvious findings.**

- `NetworkMonitor.isOnline` uses `SharingStarted.WhileSubscribed(5_000)`,
  which means before this PR the `ConnectivityManager.NetworkCallback`
  was only registered while a UI subscriber was collecting. The new
  driver's `isOnline.collect { }` makes `SyncService` a permanent
  collector, so the callback now stays registered for the process
  lifetime — the comment was updated to reflect this.
- The repo had a parallel in-flight PR (#943) fixing a pre-existing
  `compileDebugUnitTestKotlin` error in `MultiCreateViewModelTest`
  (uses `capture(captured)` where `captured: CapturingSlot<Long?>`).
  This blocks `testDebugUnitTest` on `main`. PR #944 was verified
  locally after temporarily applying #943's `captureNullable` fix; CI
  on #944 will need #943 to land first (or a rebase) to go green.
- `fullSync` already has an `isSyncing` reentrancy guard
  (`SyncService.kt:2917`), so firing it from both the reactive and the
  periodic paths is safe — concurrent fires no-op.

**Open questions.** None blocking. The user may want a follow-up to
make periodic sync work in background via WorkManager (today's
implementation is foreground/process-alive only), but that wasn't part
of the original ask.
```
