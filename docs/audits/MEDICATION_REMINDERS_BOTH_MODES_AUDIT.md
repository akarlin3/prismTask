# Medication reminders — CLOCK + INTERVAL audit

**Scope.** Verify both user-facing medication reminder modes — `CLOCK`
and `INTERVAL`, as exposed by `MedicationReminderModeSection.kt`
("Reminders fire at each slot's ideal time" / "Reminders fire N
minutes after the most recent dose") — work end-to-end from settings
toggle through alarm dispatch to notification delivery.

**Date.** 2026-04-30
**Author.** Audit-first sweep
**Pre-audit baseline commit.** main @ 19a8583d

---

## Overview

Two reminder systems coexist, both wired to a single
`MedicationReminderReceiver`:

1. `MedicationReminderScheduler` (request-code base `400_000`) —
   schedules per-medication alarms keyed by
   `MedicationEntity.scheduleMode` (`TIMES_OF_DAY`, `SPECIFIC_TIMES`,
   `INTERVAL` of `intervalMillis`, `AS_NEEDED`). Pre-dates the
   reminder-mode track. Doc-comment: "CLOCK-mode flow is unchanged —
   `MedicationReminderScheduler` continues to own those alarms."
2. `MedicationIntervalRescheduler` (request-code base `500_000` for
   slots, `600_000` for per-med overrides) — schedules INTERVAL-mode
   alarms by walking active slots + active meds and consulting
   `MedicationReminderModeResolver`. Reactive on dose changes via
   `medicationDoseDao.observeMostRecentDoseAny()`. Reschedule pass also
   runs on app launch (`PrismTaskApplication.startMedicationIntervalRescheduler`)
   and global-pref save (`MedicationReminderModeSettingsViewModel.save`).

The user-facing model in `MedicationReminderModeSection.kt:55-77` is
**slot-driven**: each slot has an `idealTime`, and reminders fire either
at that time (CLOCK) or N minutes after the most recent dose (INTERVAL).
The implementation does not match this model — see Findings 1 and 2.

Both modes route their alarms to `MedicationReminderReceiver.onReceive`,
which dispatches by extra:

```kotlin
val medicationId = intent.getLongExtra("medicationId", -1L)
val habitId = intent.getLongExtra("habitId", -1L)
when {
    medicationId >= 0 -> handleMedicationAlarm(...)
    habitId >= 0 -> handleHabitAlarm(...)
    else -> Log.w("MedReminderReceiver", "Alarm fired with neither …")
}
```

---

## Item 1 — Slot INTERVAL alarms never fire a notification (RED, PROCEED)

**Findings.**
`MedicationIntervalRescheduler.registerAlarmForSlot()`
(`MedicationIntervalRescheduler.kt:152-167`) puts the medicationId
extra to a sentinel `-1L`:

```kotlin
val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
    putExtra("medicationId", -1L)
    putExtra("slotKey", slot.id.toString())
    putExtra("intervalSlotId", slot.id)
}
```

The receiver (`MedicationReminderReceiver.kt:46-72`) dispatches by
checking `medicationId >= 0` then `habitId >= 0`; with both `-1L` the
alarm hits the `else` branch and only emits a `Log.w`. **No notification
is shown.** `intervalSlotId` is never read.

Worse, `ExactAlarmHelper.scheduleExact` is one-shot — once an alarm
fires without rescheduling, the chain only resumes when the rescheduler
re-runs `rescheduleAll` (boot, dose-change Flow, app launch, settings
save). A user who never logs a dose from a notification (because none
shows) sees the slot INTERVAL alarms silently die after the first miss.

**Risk.** RED. The headline INTERVAL feature is non-functional for the
slot path, which is the only path the settings UI describes
("Reminders fire `formatInterval(prefs.intervalDefaultMinutes)` after
the most recent dose"). End-to-end broken.

**Recommendation.** PROCEED.
- Add an `intervalSlotId` branch to the receiver dispatch.
- Build `handleSlotIntervalAlarm(context, intent, entryPoint, slotId)`
  that loads the slot, picks a label (`"${slot.name}"`), and calls
  `NotificationHelper.showMedicationReminder` with the slot id as the
  notification id (using a base offset to avoid colliding with
  per-medication notification ids).
- The Flow observer in `MedicationIntervalRescheduler.start()` already
  re-anchors after the next dose insert — no self-re-register from the
  receiver is required for the slot path. Document that explicitly so a
  future reader doesn't add one and double-fire.

---

## Item 2 — CLOCK reminders never fire for new medications (RED, PROCEED)

**Findings.**
`MedicationViewModel.addMedication()`
(`MedicationViewModel.kt:286-322`) inserts a fresh `MedicationEntity`
without setting `scheduleMode`, `timesOfDay`, `specificTimes`, or
`intervalMillis` — they default to `scheduleMode = "TIMES_OF_DAY"`,
all schedule fields null. `MedicationReminderScheduler.scheduleTimesOfDay`
(`MedicationReminderScheduler.kt:108-114`) reads `med.timesOfDay`,
parses it via `parseTimesOfDay(null) → []`, and registers zero alarms.

Two compounding gaps:

1. `MedicationViewModel.addMedication` never calls
   `medicationReminderScheduler.scheduleForMedication(...)`. Confirmed by
   `grep -rn 'scheduleForMedication' app/src` — the only call site is
   inside `rescheduleAll` itself.
2. `PrismTaskApplication.onCreate` does **not** call
   `medicationReminderScheduler.rescheduleAll()`. The only call sites are
   `BootReceiver.onReceive` and `MedicationReminderScheduler.rescheduleAll`
   (self). After install/update without a device reboot, even legacy
   medications with populated `scheduleMode`/`timesOfDay` get no CLOCK
   alarms.

The settings string promises "Reminders fire at each slot's ideal time."
The legacy scheduler doesn't read slots at all; slots only feed the
INTERVAL rescheduler via `MedicationSlotDao.getActiveOnce()`.

**Risk.** RED. CLOCK is the default global mode (see
`UserPreferencesDataStore.kt:151`), so the most common user path —
"create medication, link it to morning/evening slots, expect a
notification at the slot's idealTime" — produces zero notifications.

**Recommendation.** PROCEED. Two coordinated fixes:

1. **Bridge slots → CLOCK alarms.** Either:
   - Extend `MedicationReminderScheduler` to iterate active slots when
     the resolved mode is CLOCK and register one alarm per (slot,
     medication-or-slot-anchor) at the slot's `idealTime`, OR
   - Mirror the `MedicationIntervalRescheduler` shape: introduce a
     `MedicationClockRescheduler` that walks slots, resolves to CLOCK,
     and registers per-slot wall-clock alarms. Symmetric with the
     INTERVAL side, easier to reason about.

   Recommend the second — symmetry beats extending the legacy scheduler,
   and the legacy `scheduleMode` path can be retired once
   `MedicationPreferences` is dropped (see
   `HabitReminderScheduler.kt:30-41` doc-comment, which already flags
   the cleanup migration).

2. **Run the rescheduler on app launch.** Add a one-shot
   `medicationReminderScheduler.rescheduleAll()` to
   `PrismTaskApplication.startMedicationIntervalRescheduler` (rename to
   `startMedicationReschedulers`). Symmetric with the existing INTERVAL
   pass at line 349.

---

## Item 3 — CLOCK + INTERVAL double-fire when both schedulers register (YELLOW, PROCEED)

**Findings.**
`MedicationReminderScheduler.scheduleForMedication`
(`MedicationReminderScheduler.kt:55-63`) switches on `med.scheduleMode`
only — it does not consult the resolved `MedicationReminderMode`. For a
medication with `scheduleMode = "TIMES_OF_DAY"` and
`reminderMode = "INTERVAL"` (or global INTERVAL), both schedulers
register alarms:

- `MedicationReminderScheduler` registers up to 4 TIMES_OF_DAY alarms
  at `400_000 + slotIndex` (CLOCK-style notification fires via
  `handleMedicationAlarm`).
- `MedicationIntervalRescheduler` registers per-medication-override
  interval alarms at `600_000 + medId` (INTERVAL-style notification
  also fires via `handleMedicationAlarm`).

User receives BOTH styles. The opt-in to INTERVAL does not silence the
CLOCK path because the legacy scheduler is reminder-mode-blind.

**Risk.** YELLOW. Less catastrophic than items 1-2 (notifications still
fire, just too many), but a bad UX for the explicit INTERVAL opt-in
case. Currently masked because Item 2 means brand-new meds rarely have
populated `timesOfDay`/`specificTimes` — fixing Item 2 will surface
this directly.

**Recommendation.** PROCEED. In
`MedicationReminderScheduler.scheduleForMedication`, resolve the
medication's reminder mode via `MedicationReminderModeResolver` (with
`slot = null`, `medication = med`, `global = global`); if the resolved
mode is INTERVAL, skip CLOCK registration. Pull the global prefs from
`UserPreferencesDataStore.medicationReminderModeFlow.first()` like
`MedicationIntervalRescheduler.rescheduleAll` does.

Add a unit test fixture mirroring
`MedicationReminderModeResolverTest.resolveMode_medicationOverridesSlotAndGlobal`
covering the "TIMES_OF_DAY + INTERVAL → no CLOCK alarms" case.

---

## Item 4 — Per-med INTERVAL override re-registers via legacy scheduler (YELLOW, PROCEED)

**Findings.**
`MedicationIntervalRescheduler.registerAlarmForMedication`
(`MedicationIntervalRescheduler.kt:169-184`) sets `medicationId =
med.id` so the receiver routes to `handleMedicationAlarm`. That handler
unconditionally calls
`entryPoint.medicationReminderScheduler().onAlarmFired(medicationId,
slotKey)` (`MedicationReminderReceiver.kt:141`). `onAlarmFired`
(`MedicationReminderScheduler.kt:84-100`) switches on `med.scheduleMode`
and:

- If `scheduleMode = "TIMES_OF_DAY"` or `"SPECIFIC_TIMES"`, calls
  `nextTriggerForTimeOfDay("interval-override")` →
  `TIME_OF_DAY_CLOCK["interval-override"]` is null →
  `nextTriggerForTimeOfDay` returns null → no re-register. Harmless but
  spurious lookup.
- If `scheduleMode = "INTERVAL"`, calls `scheduleInterval(med)` which
  re-anchors at `400_000 + INTERVAL_SLOT_INDEX (=9)`. That's a separate
  alarm from the `600_000 + medId` per-med-override alarm — now there
  are two parallel interval alarms for the same medication, both
  rolling on the dose-change Flow but on slightly different anchors
  (legacy uses `medicationDoseDao.getLatestForMedOnce(med.id)`, the
  rescheduler uses `getMostRecentDoseAnyOnce()` which is the most
  recent dose for *any* medication).

**Risk.** YELLOW. The "scheduleMode=INTERVAL ∧ reminderMode=INTERVAL"
case is rare but produces double alarms with subtly different anchors.
The other branches are silent no-ops.

**Recommendation.** PROCEED. In `handleMedicationAlarm`, only call
`onAlarmFired` when the alarm originated from the legacy scheduler. The
cleanest discriminator is the `slotKey`: legacy keys are TIMES_OF_DAY
buckets (`morning|afternoon|evening|night`), HH:mm strings, or
`"interval"`; rescheduler-override key is `"interval-override"`. Skip
the call when slotKey == `"interval-override"`.

---

## Item 5 — Global mode flip CLOCK→INTERVAL doesn't cancel CLOCK alarms (YELLOW, PROCEED)

**Findings.**
`MedicationReminderModeSettingsViewModel.save`
(`MedicationReminderModeSettingsViewModel.kt:34-38`) writes the new
prefs and triggers `intervalRescheduler.rescheduleAll()` only. That
cancels every interval-namespace alarm and re-registers INTERVAL ones —
fine for INTERVAL→CLOCK.

But CLOCK→INTERVAL leaves the legacy CLOCK alarms registered — they
fire on top of the INTERVAL alarms. Tied to Item 3 (the legacy
scheduler is reminder-mode-blind), but worth a separate fix because
even after Item 3 the alarms registered before the toggle remain
pending in AlarmManager.

**Risk.** YELLOW. Until the user reboots, opens the app (after Item 2's
fix lands), or hits another `rescheduleAll` trigger, ghost CLOCK
alarms fire.

**Recommendation.** PROCEED. In
`MedicationReminderModeSettingsViewModel.save`, also invoke
`medicationReminderScheduler.rescheduleAll()` so the legacy scheduler
re-resolves modes per medication and skips the ones now in INTERVAL
(once Item 3 is fixed). Alternatively, cancel-then-skip in the legacy
scheduler is fine if the resolver consultation lands.

---

## Item 6 — Test coverage gap on the alarm-fire → notification path (YELLOW, DEFER)

**Findings.**
`MedicationReminderModeResolverTest` (151 lines, 9 tests) covers the
pure resolver thoroughly. `MedicationReminderSchedulerTest` (97 lines)
covers `baseRequestCode` arithmetic and `isValidClockString` parsing.
**Nothing covers the dispatch contract** between
`MedicationIntervalRescheduler.registerAlarmForSlot` and
`MedicationReminderReceiver.onReceive` — the bug in Item 1 is exactly
the gap this test would catch.

`grep -l "MedicationInterval" app/src/test app/src/androidTest` returns
zero. No instrumented test exercises the receiver either (it would need
a `RobolectricTestRunner` or a Hilt androidTest harness).

**Risk.** YELLOW. Item 1's fix should ship with a unit test that asserts
the intent extras shape registered for slot alarms; that catches a
recurrence cheaply. End-to-end is more expensive — defer.

**Recommendation.** DEFER the end-to-end androidTest; PROCEED with a
unit-level dispatch contract test as part of Item 1.
- Use Robolectric to capture pending broadcasts after
  `registerAlarmForSlot`, then drive the captured intent into a
  `MedicationReminderReceiver` instance and assert
  `NotificationHelper.showMedicationReminder` was called (mock
  `NotificationHelper` via a thin sealed interface, or use
  `ShadowNotificationManager`).
- File a follow-up to extend coverage to the per-med override path
  once Items 1, 3, 4 land.

---

## Improvement ranking

Sorted by wall-clock-savings ÷ implementation-cost (each PR worktree
cost is ≈ 30–60 min including review iterations).

| Rank | Item | Severity | Cost | Why |
|------|------|----------|------|-----|
| 1 | #1 slot INTERVAL receiver branch | RED | low | 1 file changed in receiver + 1 helper in NotificationHelper. Restores the entire INTERVAL feature for slots. |
| 2 | #2a app-launch CLOCK reschedule | RED | low | One line in `PrismTaskApplication.onCreate`. Restores legacy-medication CLOCK alarms post-update without reboot. |
| 3 | #2b slots → CLOCK alarms | RED | medium | New `MedicationClockRescheduler` mirroring the interval one; the symmetric shape pays back when item #5 lands. |
| 4 | #3 reminderMode-aware legacy scheduler | YELLOW | low | One resolver call inside `scheduleForMedication`. Stops double-fire once #2 surfaces it. |
| 5 | #4 skip onAlarmFired for `interval-override` | YELLOW | very low | One-line guard in `handleMedicationAlarm`. |
| 6 | #5 also call legacy `rescheduleAll` on save | YELLOW | very low | One line in the settings VM. |
| 7 | #6 dispatch contract unit test | YELLOW | low | Co-shipped with #1; not a separate PR. |

---

## Anti-patterns flagged but not blocking

- **Mis-named injected fields**: `HabitRepository.kt:53` and
  `AddEditHabitViewModel.kt:29` declare
  `private val medicationReminderScheduler: HabitReminderScheduler`.
  The variable name predates the v1.4 refactor that split the legacy
  `MedicationReminderScheduler` into `HabitReminderScheduler` (habits)
  and `MedicationReminderScheduler` (top-level meds). Renaming costs
  one Edit per call site and removes a real reading hazard. Good
  candidate for a follow-up `style:` PR after the RED items land —
  rename churn during a critical-bug fix is noise.
- **Receiver class still named `MedicationReminderReceiver`** despite
  handling habits + medications + (post-fix) slot alarms. Doc-comment
  acknowledges the name lag. Renaming is a public-class-rename + manifest
  edit; not worth it until the legacy `MedicationPreferences` cleanup
  migration drops the habit path.
- **`MedicationReminderScheduler.SLOT_CAPACITY = 10`** with `INTERVAL_SLOT_INDEX
  = 9`. If a med has more than 9 TIMES_OF_DAY/SPECIFIC_TIMES alarms (it
  can't today — TIMES_OF_DAY caps at 4 buckets, SPECIFIC_TIMES is
  user-set and currently unconstrained at the entity level), index 9
  collides with the interval slot. Fix is to gate `SPECIFIC_TIMES` to
  ≤ 9 entries at the entity / dialog layer, but the input UI
  (`MedicationEditorDialog`) doesn't currently allow specifying
  `specificTimes` directly so this is theoretical.

---

## Phase 3 — Bundle summary

All five PROCEED items shipped via three implementation PRs stacked
behind the audit doc PR. Stacking order `#977 → #979 → #980 → #986`;
each later PR was branched off the prior so receiver-dispatch and
NotificationHelper edits compose cleanly.

| Item | Severity | Fix | PR | Status |
|------|----------|-----|----|--------|
| #1 — Slot INTERVAL receiver dispatch | RED | Add `intervalSlotId` branch + `NotificationHelper.showSlotIntervalReminder`; pure `classifyAlarm` helper for unit-testability | [#979](https://github.com/averycorp/prismTask/pull/979) | Auto-merge, CI pending |
| #2 — CLOCK reminders for new meds | RED | New `MedicationClockRescheduler`; receiver `clockSlotId` branch; app-launch + boot reschedule of legacy + clock + interval | [#980](https://github.com/averycorp/prismTask/pull/980) | Auto-merge, CI pending |
| #3 — Legacy scheduler reminderMode-aware | YELLOW | `scheduleForMedication` consults `MedicationReminderModeResolver`; skips wall-clock paths when resolved mode is INTERVAL | [#986](https://github.com/averycorp/prismTask/pull/986) | Auto-merge, CI pending |
| #4 — `interval-override` re-arm guard | YELLOW | Receiver skips legacy `onAlarmFired` for `slotKey == "interval-override"` | [#986](https://github.com/averycorp/prismTask/pull/986) | Bundled with #3 |
| #5 — Settings flip also re-arms CLOCK | YELLOW | `MedicationReminderModeSettingsViewModel.save` runs both reschedulers | [#980](https://github.com/averycorp/prismTask/pull/980) | Bundled with #2 |
| #6 — Dispatch contract test | DEFERRED → PARTIAL | Co-shipped: `MedicationReminderReceiverDispatchTest` (7→9 cases across #979 / #980). Full receiver-end-to-end androidTest still deferred. | #979 + #980 | Co-shipped |

### Wall-clock-per-PR

- Phase 1 audit: ~50 min wall clock (read all schedulers + receiver +
  resolver + entities; cross-check call sites; compose 341-line doc).
- PR1 (slot INTERVAL): ~25 min (receiver edit, helper added in
  `NotificationHelper`, 7-case unit test).
- PR2 (CLOCK rescheduler): ~50 min (new ~150-LOC scheduler, receiver +
  notif-helper + boot + app + settings VM all touched, 5-case helper
  test).
- PR3 (cleanup): ~20 min (two-file edit, no new tests required —
  resolver tests already exercise the predicate the guard is built on).

Total ~145 min for one RED-RED-YELLOW-YELLOW-YELLOW + audit. The
single biggest cost was understanding the dual scheduler topology
(legacy `MedicationReminderScheduler` per-med + new
`MedicationIntervalRescheduler` per-slot), which is not surfaced
anywhere in `CLAUDE.md` — see "Memory candidates" below.

### Memory candidates

- **Candidate**: medication reminders are scheduled by **two**
  components that share a single `MedicationReminderReceiver`:
  `MedicationReminderScheduler` (per-medication, scheduleMode-driven,
  legacy) and `MedicationIntervalRescheduler` /
  `MedicationClockRescheduler` (per-slot, resolver-driven, current).
  The receiver's `classifyAlarm` companion routes by extra-id presence
  with priority `medication > slot-clock > slot-interval > habit`.
- **Recommendation**: hold this memory until v1.4-era
  `MedicationPreferences` cleanup migration ships — the legacy
  scheduler's `onAlarmFired` re-arm path goes away with that migration,
  so the dual-ownership fact will collapse into a single component.
  A premature memory entry would rot.

### Schedule for next audit

- 2026-05-15 — confirm `MedicationClockRescheduler` is firing for
  fresh-install users, and verify no double-fire on legacy migrated
  medications. If burnout/double-reminder telemetry surfaces earlier,
  promote this check.
- Pair with the broader medication-architecture sweep tracked in
  `docs/audits/MEDICATION_SYNC_AUDIT.md` and
  `docs/audits/MEDICATION_TAB_LOG_INVARIANT_AUDIT.md` so the cleanup
  migration that retires `MedicationPreferences.specificTimes` /
  `MedicationReminderScheduler` legacy paths can be planned with all
  three audits' findings on the table.

## Phase 4 — Claude Chat handoff

```markdown
## Medication reminders audit (PrismTask Android, 2026-04-30)

**Scope.** End-to-end check of both medication reminder modes — CLOCK
("Reminders fire at each slot's ideal time") and INTERVAL ("Reminders
fire N minutes after the most recent dose"), as exposed by
`MedicationReminderModeSection.kt`. Triggered by user ask: "Ensure
both the types of medication reminders work appropriately." Run
against `main` at commit `175a15ef`.

**Verdicts.**

| # | Item | Verdict | One-line finding |
|---|------|---------|------------------|
| 1 | Slot INTERVAL receiver dispatch | RED | `MedicationReminderReceiver` only checked `medicationId` / `habitId`; alarms with `medicationId=-1L` from `MedicationIntervalRescheduler.registerAlarmForSlot` silently logged. Slot INTERVAL reminders showed zero notifications. |
| 2 | CLOCK reminders for new meds | RED | Legacy `MedicationReminderScheduler` reads `MedicationEntity.timesOfDay` / `specificTimes` — fields the medication editor never sets. New medications received zero CLOCK alarms. Compounded by the legacy scheduler being re-armed only on device boot, never at app launch. |
| 3 | Legacy scheduler reminderMode-blind | YELLOW | `scheduleForMedication` switched on `scheduleMode` only, so a med with `reminderMode=INTERVAL` got both legacy CLOCK alarms AND the per-med override alarm — double-fire. |
| 4 | `interval-override` re-arm | YELLOW | Receiver fed every medication-keyed alarm (including `MedicationIntervalRescheduler`'s per-med override) into the legacy `onAlarmFired` re-arm — at best a no-op lookup, at worst a double-anchor. |
| 5 | Settings flip stale alarms | YELLOW | `MedicationReminderModeSettingsViewModel.save` only re-armed the interval rescheduler; CLOCK side stayed stale until next reboot. |
| 6 | Dispatch contract test | DEFERRED-then-PARTIAL | Pure `classifyAlarm` helper test co-shipped; full receiver-end-to-end androidTest still deferred. |

**Shipped.**

- PR #977 (MERGED, e36a06a3) — Phase 1 audit doc (341 lines) at `docs/audits/MEDICATION_REMINDERS_BOTH_MODES_AUDIT.md`.
- PR #979 (auto-merge enabled) — slot-INTERVAL receiver branch + `NotificationHelper.showSlotIntervalReminder` + 7-case dispatch helper unit test.
- PR #980 (auto-merge enabled) — new `MedicationClockRescheduler` (symmetric with `MedicationIntervalRescheduler`), receiver `clockSlotId` branch, app-launch + boot reschedule of all three med schedulers, settings save also re-arms clock side.
- PR #986 (auto-merge enabled) — legacy `MedicationReminderScheduler.scheduleForMedication` consults `MedicationReminderModeResolver` and skips wall-clock paths when mode is INTERVAL; receiver skips legacy `onAlarmFired` for `slotKey="interval-override"`.

**Deferred / stopped.**

- End-to-end androidTest of `MedicationReminderReceiver` dispatch (audit item #6 "DEFERRED") — needs a Robolectric / Hilt androidTest harness; the unit-level pure `classifyAlarm` helper test is sufficient regression coverage for the seam that broke this audit.
- Legacy `MedicationReminderScheduler` retirement — kept alive for now because legacy migrated medications with populated `timesOfDay` / `specificTimes` / `intervalMillis` rely on it, and the `MedicationPreferences` cleanup migration is the right place to drop it.

**Non-obvious findings.**

- Medication reminders are scheduled by **two** parallel components, both routed through `MedicationReminderReceiver`: a per-medication legacy scheduler (`MedicationReminderScheduler`, request codes `400_000+`, switches on `scheduleMode`) and slot-driven reschedulers (`MedicationIntervalRescheduler` at `500_000+` / `600_000+`, `MedicationClockRescheduler` at `700_000+`, both consult `MedicationReminderModeResolver`). Neither audit doc nor `CLAUDE.md` flagged this duality before this sweep — the receiver's class doc-comment now says so.
- `MedicationEntity.scheduleMode` (`TIMES_OF_DAY` / `SPECIFIC_TIMES` / `INTERVAL` / `AS_NEEDED`) and `MedicationEntity.reminderMode` (`CLOCK` / `INTERVAL` / null-inherit) describe **different** dimensions. The first decides which schedule the legacy scheduler walks; the second decides which rescheduler claims ownership. The medication editor exposes only `reminderMode` and never sets `scheduleMode` away from its `TIMES_OF_DAY` default — so for fresh-install users only the slot-driven path matters, which is exactly the path that was broken.
- The medication editor (`MedicationEditorDialog`) and slot editor (`MedicationSlotEditorSheet`) both surface a Default/CLOCK/INTERVAL picker, and the resolver's three-level precedence (med → slot → global) was already correctly implemented; the failures were entirely on the scheduler / receiver / wiring side, not on the resolver itself.

**Open questions.**

- Should the legacy `MedicationReminderScheduler` be retired entirely once the `MedicationPreferences` cleanup migration ships? The `INTERVAL`-schedule-mode chained-alarm path overlaps with `MedicationIntervalRescheduler`'s per-medication-override path — only the legacy code reads `MedicationEntity.intervalMillis`, but no UI surface still writes that field. Carry into the next medication-architecture sweep.
- Should slot-only CLOCK reminders fire for slots with no linked medications? Current behaviour: yes, with title "$slotName Medications" (matches the INTERVAL side's symmetric behaviour). Unclear whether that's noise — a follow-up "skip empty slots" optimization could land in either rescheduler if user reports come in.
```

