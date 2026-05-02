# Medication CLOCK reminder not firing on S25 Ultra — audit

**Scope.** Reproduction from May 1 device session, Test 2.1: a medication
with `reminderMode = CLOCK` does not produce a notification when its
slot's `idealTime` is set to "now + 2 minutes" and the screen is locked.

**Severity.** P0 — Phase F GATE BLOCKER (Phase F kickoff May 25).

**Date.** 2026-05-02
**Author.** Audit-first sweep
**Pre-audit baseline commit.** main @ f6073441
**Reference audit.** `docs/audits/MEDICATION_REMINDERS_BOTH_MODES_AUDIT.md`
(PRs #977-#991, Apr 30) and `docs/audits/MEDICATION_SLOT_LABEL_DRIFT_AUDIT.md`
(PR #1017, Apr 30).

---

## Verdict (TL;DR)

**STOP-and-request more evidence.** The four hypothesised causes
(CAUSE-3 permission / CAUSE-4 scheduler defect / CAUSE-5 boot regression
/ CAUSE-? slot-generation) all read GREEN on a static sweep of the
codebase as it sits at `f6073441`. Without runtime evidence I cannot
distinguish between:

- Runtime exact-alarm denial on the S25 specifically (manifest is fine
  but the OS may still report `canScheduleExactAlarms() == false`),
- Samsung One UI Doze-killing `setExactAndAllowWhileIdle` despite the
  "Unrestricted" battery setting,
- A UI-path ambiguity in the repro: the user may have used the
  per-medication slot-time *override* toggle, which is a real
  separate defect (see CAUSE-? below) but isn't in the original
  4-cause hypothesis,
- A defect outside all four hypotheses.

Ship-blocker recommendation: capture the runtime evidence requested in
§ "Required follow-up evidence" before any code lands. The static read
does not justify a speculative fix — and shipping a wrong fix burns the
gate window.

One **incidental real defect** was uncovered during the sweep
(CAUSE-?, override path): worth fixing on its own merits, but unlikely
to match the user's stated "modify a time-of-day slot" repro unless the
user clicked the "Use different time for this med" toggle. Do not ship
this fix yet — confirm first that it matches the failing repro.

---

## CAUSE-3 — exact-alarm permission denied (GREEN, STOP-no-work-needed)

**Static findings.**

- `app/src/main/AndroidManifest.xml:29-30` declares both
  `SCHEDULE_EXACT_ALARM` *and* `USE_EXACT_ALARM`. The accompanying
  comment block (lines 17-28) explicitly notes that USE_EXACT_ALARM
  was added to stop Samsung silently dropping inexact alarms.
- `app/src/main/java/com/averycorp/prismtask/notifications/ExactAlarmHelper.kt:36-72`
  uses `setExactAndAllowWhileIdle` (Doze-bypassing) and **falls back
  to `setAndAllowWhileIdle`** (still allow-while-idle, just inexact)
  when `canScheduleExactAlarms()` returns false. There is no path
  that drops the alarm silently — every call routes through the
  helper.
- The S25 Ultra is API 34+ (Android 14 / 15). `USE_EXACT_ALARM` is
  auto-granted on API 33+ for "alarm clock / calendar / reminder /
  healthcare" apps. Once granted, `AlarmManager.canScheduleExactAlarms`
  returns `true` regardless of the SCHEDULE_EXACT_ALARM toggle.

**Risk classification.** GREEN on static read. The manifest + helper
are correctly shaped.

**Residual uncertainty.** Whether Google Play classifies
`com.averycorp.prismtask` (a "todo + medication tracker") under one of
the categories that auto-grants USE_EXACT_ALARM is *not* statically
verifiable. If Play strips the permission at delivery time, the helper
still works but routes through the inexact branch — Samsung One UI
defers those aggressively even on "Unrestricted" battery.

**Recommendation.** STOP-no-work-needed for code; require the runtime
verification in § "Required follow-up evidence" to either reclassify as
RED (and fix permission gate, e.g. add SCHEDULE_EXACT_ALARM settings
deep-link) or confirm GREEN.

---

## CAUSE-4 — CLOCK scheduler defect (GREEN, STOP-no-work-needed)

**Static findings.**

- `MedicationClockRescheduler.registerAlarmForSlot`
  (`app/src/main/java/com/averycorp/prismtask/notifications/MedicationClockRescheduler.kt:136-149`)
  builds a PendingIntent with extras
  `{ medicationId=-1L, clockSlotId=slot.id, slotKey=slot.id.toString() }`
  and `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`, then routes through
  `ExactAlarmHelper.scheduleExact`. Request code is `700_000 +
  (slot.id % 1000)` — distinct from the 400_000 (legacy per-med),
  500_000 (slot INTERVAL), and 600_000 (per-med INTERVAL override)
  namespaces.
- `MedicationReminderReceiver.classifyAlarm`
  (`MedicationReminderReceiver.kt:258-269`) dispatches in the order
  `medicationId >= 0 → Medication`,
  `clockSlotId >= 0 → SlotClock`,
  `intervalSlotId >= 0 → SlotInterval`,
  `habitId >= 0 → Habit`. With `medicationId=-1` and
  `clockSlotId=slot.id (>0)`, the receiver routes to
  `handleSlotClockAlarm` deterministically.
- `handleSlotClockAlarm` (`MedicationReminderReceiver.kt:217-237`)
  re-arms tomorrow via
  `medicationClockRescheduler.onAlarmFired(slotId)` *before*
  showing the notification, so a process death between fire and
  re-register doesn't leave the slot dark.
- `NotificationHelper.showSlotClockReminder`
  (`NotificationHelper.kt:454-465`) delegates to `showSlotReminder`
  with notification id offset 700_000+, gates on
  `medicationRemindersEnabled` (defaults to `true` —
  `NotificationPreferences.kt:251-253`), creates the channel, and
  calls `manager.notify`.

**Coverage.** `MedicationClockReschedulerTest` pins the request-code
namespace and `nextTriggerForClock` helper.
`MedicationSlotFlowReschedulerTest` (added by PR #1017) pins the
slot-Flow → rescheduleAll contract.
`MedicationReminderReceiverDispatchTest` pins `classifyAlarm`.

**Risk classification.** GREEN on static read. No defect-family
analogue to PR #979's interval-side dispatch bug found.

**Recommendation.** STOP-no-work-needed. If the runtime evidence
points at this layer, attach the alarm dump + logcat and revisit.

---

## CAUSE-5 — boot-required regression (GREEN, STOP-no-work-needed)

**Static findings.**

- `PrismTaskApplication.startMedicationReschedulers`
  (`PrismTaskApplication.kt:410-434`) launches three `appScope.launch`
  blocks for `medicationReminderScheduler.rescheduleAll()`,
  `medicationClockRescheduler.rescheduleAll()`, and
  `medicationIntervalRescheduler.rescheduleAll()`, then calls
  `medicationClockRescheduler.start(appScope)` and
  `medicationIntervalRescheduler.start(appScope)`. Both
  `start()` methods subscribe to
  `medicationSlotDao.observeAll()` (the Flow seam added by PR #1017),
  so any slot insert/update/soft-delete/restore re-arms.
- `MedicationReminderModeSettingsViewModel.save`
  (`MedicationReminderModeSettingsViewModel.kt:40-44`) launches
  `intervalRescheduler.rescheduleAll()` and
  `clockRescheduler.rescheduleAll()` after a global-default flip.
- `MedicationSlotsViewModel.update`
  (`MedicationSlotsViewModel.kt:59-79`) writes through
  `repository.updateSlot(slot.copy(...))` which Room emits via
  `observeAll()` → `start()` observer → `rescheduleAll()`.
- `BootReceiver` (referenced in
  `MedicationReminderScheduler.kt:96-101`) was the original boot
  hook; PR #986 added the on-launch hook so a fresh install / app
  update with previously migrated meds doesn't need a reboot.

**Risk classification.** GREEN on static read. App-launch + slot-edit
+ settings-save all hit the rescheduler.

**Recommendation.** STOP-no-work-needed.

---

## CAUSE-? — per-medication slot-time override is ignored (YELLOW, DEFER)

**This is a real defect uncovered during the sweep.** It is *not*
necessarily the user's repro cause, but it is correctness-relevant.

**Static findings.**

- `MedicationSlotPicker.OverrideEditor`
  (`app/src/main/java/com/averycorp/prismtask/ui/screens/medication/components/MedicationSlotPicker.kt:200-269`)
  exposes a "Use different time for this med" toggle. Toggling it on
  and entering an HH:mm value writes a
  `MedicationSlotOverrideEntity` row via
  `MedicationViewModel.updateMedication` →
  `slotRepository.upsertOverride`
  (`MedicationViewModel.kt:355-366`).
- The override is written to the `medication_slot_overrides` table.
  It is **not** written back to `medication_slots`.
- `MedicationClockRescheduler.rescheduleAll`
  (`MedicationClockRescheduler.kt:74-92`) reads
  `slot.idealTime` directly from each row returned by
  `MedicationSlotDao.getActiveOnce()`. Per-medication overrides are
  *not* consulted: the rescheduler walks at slot granularity, not
  (slot, medication) granularity, and `MedicationSlotOverrideEntity`
  is never read in any of the three reschedulers.
- The Flow observer in `start()` watches `medication_slots`, *not*
  `medication_slot_overrides`. So writing an override does not
  trigger any rescheduler.

**Net effect.** A user who toggles "Use different time for this med"
to (say) 08:30 for med M against a slot S with `idealTime=08:00` will
see the alarm fire at 08:00 (the slot's value), with the per-med
override silently ignored. Worse, an override-only edit produces *no*
rescheduler pass — because the two seams that drive a pass (slot
table change, `medication_slots` row update) don't fire.

**Risk classification.** YELLOW. The override UI advertises behaviour
the scheduler does not implement. This is a documented-feature ↔
implementation gap, not a regression.

**Whether this matches the user's repro.** UNCERTAIN. The repro text —
"add or modify a time-of-day slot to fire at 'now + 2 minutes'" —
could mean:

(a) Edit the underlying slot's `idealTime` in **Settings → Medication
    Slots** (writes `medication_slots`, triggers reschedule, *should
    work*).
(b) Toggle the per-med override in the **medication editor's slot
    picker** (writes `medication_slot_overrides`, does *not* trigger
    reschedule, alarm fires at original time).
(c) Add a new slot via "Add Slot" (writes `medication_slots`, *should
    work*).

If (b), this defect is the user's repro cause. If (a) or (c), the
static audit cannot explain the failure.

**Recommendation.** DEFER. Do not ship a fix until evidence confirms
the user used path (b). If confirmed, the fix shape would be either
(i) walk slots at `(slot, medication)` granularity in the clock
rescheduler and consult overrides, plus add a Flow observer on
`medication_slot_overrides`; or (ii) deprecate the override UI
(simpler — the per-med drift override has a similar gap and
overrides are a small surface).

---

## CAUSE-Asymmetric per-med CLOCK override (YELLOW, DEFER)

**Adjacent finding worth flagging, unlikely to match repro.**

`MedicationIntervalRescheduler.rescheduleAll`
(`MedicationIntervalRescheduler.kt:107-129`) has a
*per-medication INTERVAL override* pass: when
`med.reminderMode == "INTERVAL"` and the resolver returns INTERVAL,
it registers a separate alarm at `600_000 + medId`.

`MedicationClockRescheduler.rescheduleAll` has **no symmetric
per-medication CLOCK override pass**. So a medication with
`reminderMode = "CLOCK"` against a global default of `INTERVAL` and
a slot with `reminderMode = NULL` produces *zero* CLOCK alarms — the
slot-level resolver call uses `medication=null` and walks only
slot+global, which resolves to INTERVAL.

**Whether this matches the user's repro.** UNLIKELY. The global
default ships as `MedicationReminderMode.CLOCK`
(`UserPreferencesDataStore.kt:150`), so the slot-level resolver
returns CLOCK for a NULL-mode slot, and the alarm fires.

**Risk classification.** YELLOW. Latent gap; no production user has
reported this exact configuration.

**Recommendation.** DEFER. Adjacent to CAUSE-? — both stem from the
clock rescheduler walking at slot granularity instead of (slot, med)
granularity. Bundle the fix if/when CAUSE-? lands.

---

## Required follow-up evidence

The audit cannot pick a single cause without runtime data. Ask Avery
to capture, on the S25 Ultra, *immediately after saving the slot edit
and BEFORE locking the screen*:

```bash
# Confirm exact-alarm permission state.
adb -s <s25_serial> shell appops get com.averycorp.prismtask SCHEDULE_EXACT_ALARM
adb -s <s25_serial> shell appops get com.averycorp.prismtask USE_EXACT_ALARM

# Confirm the alarm IS scheduled (or is not).
adb -s <s25_serial> shell dumpsys alarm | grep -B 2 -A 30 "com.averycorp.prismtask"

# Stream logs across the 3-minute wait. Filter to scheduling +
# receiver dispatch lines.
adb -s <s25_serial> logcat -v time -T 1 \
  ExactAlarmHelper:V \
  MedReminderReceiver:V \
  NotificationHelper:V \
  AlarmManager:V \
  '*:S'
```

**Decision tree:**

1. **`appops` shows USE_EXACT_ALARM `default` or `allow`** AND
   `dumpsys alarm` shows a scheduled alarm at `+2min` for
   `com.averycorp.prismtask` → permission is fine, scheduling is
   fine. Wait 3 minutes; if logcat shows
   `MedReminderReceiver` fire but `NotificationHelper` skip,
   `medicationRemindersEnabled` was disabled. If the receiver does
   not fire, Samsung Doze is killing it despite "Unrestricted" —
   look at the `dumpsys alarm` line for the alarm's `pending` /
   `next-time` fields and compare to fire time.
2. **`dumpsys alarm` shows ZERO alarms** for the package after
   save → rescheduler never fired. Check whether the user's UI path
   is actually CAUSE-? (override only, see below). Confirm by
   looking at `medication_slot_overrides` vs `medication_slots`
   contents:

   ```bash
   adb -s <s25> shell run-as com.averycorp.prismtask \
       sqlite3 /data/data/com.averycorp.prismtask/databases/prismtask.db \
       "SELECT id, name, ideal_time, is_active FROM medication_slots; \
        SELECT * FROM medication_slot_overrides;"
   ```

   If the slot's `ideal_time` is "now+2min" → CAUSE-? does not match;
   investigate further. If the override is the only thing pointing at
   "now+2min" → CAUSE-? confirmed.
3. **`appops` shows SCHEDULE_EXACT_ALARM `ignore`/`deny`** AND
   USE_EXACT_ALARM is missing/denied → CAUSE-3 confirmed; fix is to
   add a settings deep-link prompt (`canScheduleExact` already exists
   in the helper).
4. **Logcat shows `ExactAlarmHelper Scheduling alarm: ...
   canExact=false`** at scheduling time → CAUSE-3 (runtime denial).
5. **None of the above explain it** → out-of-hypothesis bug. Capture
   the full logcat from the 3-minute window and re-audit with that
   evidence.

Also clarify the UI path in the repro: did the user (a) edit the
slot's idealTime in **Settings → Medication Slots**, or (b) toggle
"Use different time for this med" in the **medication editor**? The
two paths route to different storage tables and different rescheduler
seams.

---

## Improvements ranked by wall-clock-savings ÷ implementation-cost

| Rank | Improvement | Wall-clock savings | Implementation cost | Status |
|------|-------------|--------------------|--------------------|--------|
| 1 | Capture runtime adb evidence on S25 | ~30 min vs days of speculation | 5 min user time | **Required** |
| 2 | Clarify UI path in repro (slot editor vs override toggle) | Disambiguates CAUSE-? from rest | 1 min user reply | **Required** |
| 3 | Add ProjectedNotificationLog row stating "alarm scheduled at HH:mm for slot N" on every `registerAlarmForSlot` call | Lets future bugs of this shape be diagnosed from app data, not adb | ~30 LOC | DEFER (separate PR) |
| 4 | Fix CAUSE-? (per-med slot-time override ignored) | Aligns documented UI feature with implementation | ~60-100 LOC + tests | DEFER pending evidence |
| 5 | Fix CAUSE-Asymmetric per-med CLOCK override | Closes latent gap symmetric to INTERVAL side | ~30-50 LOC + test | DEFER (bundle with #4) |

---

## Anti-patterns flagged but not fixed

- **Override-walking at slot granularity, not (slot, med) granularity.**
  The clock rescheduler's `for (slot in slots)` loop produces one
  alarm per slot regardless of which/how-many meds are linked. The
  notification body shows the slot label only — not the med — so a
  user with five meds at the morning slot and one with a per-med
  override gets one notification at the slot's idealTime. This is
  intentional per the `MedicationClockRescheduler` doc-comment, but
  the UI-side override toggle implies otherwise.
- **`MedicationViewModel.updateMedication` does not invoke any
  rescheduler.** Today this is fine because the resolver doesn't read
  `medication.reminderMode` at slot granularity (only at
  per-medication-override granularity in the INTERVAL rescheduler),
  but if someone adds per-med CLOCK support later (CAUSE-Asymmetric
  fix), they'll need to re-arm here.
- **The legacy `MedicationReminderScheduler` still walks
  `med.timesOfDay`/`med.specificTimes`** — both unused fields in
  current UI. PR #986 made it INTERVAL-aware but left the CLOCK side
  as a no-op for new meds. Worth deleting in a follow-up cleanup
  PR; it's dead code that adds confusion.

---

## Phase 2 — Promoted by user override

After the audit landed, the operator overrode the original DEFER
recommendation and asked for both YELLOW items to be fixed regardless
of whether they match the S25 repro. The reasoning: both are real
defects independent of the failing test, and the unified fix shape
(walk at `(med, slot)` granularity, observe the override + meds Flow
seams) closes both gaps in one PR. The S25 evidence-gathering for
CAUSE-3/4/5 remains outstanding and is handled by the Phase 4
handoff summary.

The fix lands as a single PR (CAUSE-? + CAUSE-Asymmetric bundled,
since both stem from the same architectural gap). CAUSE-3 / CAUSE-4 /
CAUSE-5 stay DEFER pending the runtime evidence in
§ "Required follow-up evidence."

## Phase 3 — Bundle summary

**Shipped.** Single PR — `fix/medication-clock-per-med-override` —
addressing both YELLOW items with one architectural change: the
clock rescheduler now walks at `(medication, slot)` granularity in
addition to its existing slot-level walk, observes the meds + slots +
overrides Flow seams, and dispatches per-pair alarms via a new
`AlarmKind.MedSlotClock` receiver branch.

**Files touched.**

| File | Change |
|------|--------|
| `data/local/dao/MedicationSlotOverrideDao.kt` | New `observeAll()` Flow seam so override-only edits trigger reschedule. |
| `notifications/MedicationClockRescheduler.kt` | Inject `MedicationDao` + `MedicationSlotOverrideDao`. Add per-(med, slot) walk to `rescheduleAll()`. New `onMedSlotAlarmFired` re-arm path. New `registerAlarmForMedSlot` / `cancelForMedSlot` with request code namespace `800_000+`. Three Flow observers in `start()` (slots, meds, overrides). New pure helper `needsPerMedAlarm` deciding when a pair needs its own alarm. |
| `notifications/MedicationReminderReceiver.kt` | New `AlarmKind.MedSlotClock` branch (priority above `Medication`); new `handleMedSlotClockAlarm` re-arms tomorrow + shows med-specific notification with effective time at fire-time. Entry point exposes `MedicationSlotOverrideDao`. |
| `notifications/NotificationHelper.kt` | New `showMedSlotClockReminder(medName, slotName, idealTime)` with notification id offset `800_000+(medId%1000)*1000+(slotId%1000)`. |
| `ui/screens/medication/MedicationViewModel.kt` | Inject `MedicationClockRescheduler`, invoke `rescheduleAll()` after `addMedication` and `updateMedication` so junction-table edits (`medication_medication_slots`) re-arm — neither slot nor med Flow fires for junction-only changes. |
| `app/src/test/.../MedicationClockReschedulerTest.kt` | New tests for `medSlotRequestCode` namespace and `needsPerMedAlarm` truth table (5 cases: override-differs, override-matches-slot, no-override + slot-CLOCK, med-opts-into-CLOCK-over-non-CLOCK-slot, med-opts-into-INTERVAL). |
| `app/src/test/.../MedicationReminderReceiverDispatchTest.kt` | New tests for `MedSlotClock` priority over `Medication` when both extras set; updated `medicationId_winsWhenMultipleNonSlotIdsPresent` to clarify the slot-less precondition. |
| `app/src/test/.../MedicationSlotFlowReschedulerTest.kt` | Extended to exercise the new meds + overrides Flow seams (`observeAll() on overrides → reschedule pass`, `getAll() on meds → reschedule pass`). |
| `app/src/test/.../MedicationViewModelBulkMarkTest.kt`, `MedicationSlotTodayStatePerMedTimeTest.kt` | Updated to inject the new `clockRescheduler` parameter on the ViewModel constructor (mocked relaxed). |
| `app/src/test/.../MedicationSlotRepositoryTest.kt` | Updated `FakeMedicationSlotOverrideDao` to implement the new abstract member `observeAll()`. |

**Verification.** `./gradlew :app:testDebugUnitTest` passes locally
(fresh worktree, JBR Java 21).

**Behaviour after the fix.**

| Configuration | Before | After |
|---------------|--------|-------|
| Slot at 08:00, no override, global=CLOCK | Slot-level alarm at 08:00. ✓ | Slot-level alarm at 08:00. ✓ (unchanged) |
| Slot at 08:00, override 08:30 for med A | Slot-level alarm at 08:00. **Override silently ignored.** | Slot-level alarm at 08:00 (covers other linked meds) AND per-(A, slot) alarm at 08:30. ✓ |
| Med A `reminderMode=CLOCK`, slot inherits, global=INTERVAL | INTERVAL slot-level alarm fires; **no CLOCK alarm for A despite the override.** | INTERVAL slot-level alarm fires AND per-(A, slot) CLOCK alarm at `slot.idealTime`. ✓ |
| Med A `reminderMode=INTERVAL`, slot=CLOCK, global=CLOCK | Slot-level CLOCK alarm fires. INTERVAL rescheduler also registers per-med alarm. (Existing behaviour, unchanged.) | Same. The fix only adds per-pair CLOCK; the per-pair INTERVAL path is unchanged on the INTERVAL rescheduler side. |

**Memory entries.** None added — the fix is documented in code
(rescheduler doc-comment + `needsPerMedAlarm` doc) and in this audit
section. The `(med, slot)` granularity decision is captured in the
file-level KDoc, which is the authoritative pointer for future work.

**Anti-patterns left as DEFER.**

- `MedicationIntervalRescheduler.start` does not observe
  `MedicationDao.getAll()` — flipping `medication.reminderMode` to
  `INTERVAL` doesn't auto-rearm. The interval-side fix is symmetric
  to what landed on the clock side; bundle it with any future
  interval-side change rather than ship a one-line PR.
- The legacy `MedicationReminderScheduler` still walks
  `med.timesOfDay` / `med.specificTimes`, both unused by the current
  UI. PR #986 made it INTERVAL-aware; the CLOCK side is dead code
  for new meds. A cleanup PR can drop it once the next release
  validates that no migrated v1.3 user still has populated
  `timesOfDay`.
