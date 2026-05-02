# Medication Time Picker — Display vs. Saved Drift Audit

**Scope.** User report: *"The custom med time picker showed me the wrong
time for what I picked, but logged the right time."* — display ≠ saved
on the two Material 3 `TimePicker` surfaces in the medication feature.
Saved value is correct (per user); the visual displayed back to the
user mid-flow is wrong. This audit identifies the surfaces, the most
plausible mechanism, and the fix bundle.

**Local main HEAD at audit time:** `50205ef` (chore: bump to v1.8.15
build 813). Branch under work: `claude/fix-med-time-picker-G4Wnn`.

**Pre-existing tests in scope.** `MedicationTimeEditComposeTest` (6
tests) pins `composeIntendedTime(...)` — i.e. the *post-pick* compose
of `(state.hour, state.minute)` → epoch millis. These tests prove the
saved value is correct given correct picker state, which matches the
user's "logged the right time" half. They do not exercise the picker
UI itself, which is where the display drift lives.

---

## Inventory — every "custom med time picker" surface

The user's wording ("custom med time picker") matches at least three
candidate surfaces. Listing all so the verdict below is unambiguous:

| # | File | Mechanism | Fits "displayed wrong, logged right"? |
|---|------|-----------|---------------------------------------|
| 1 | `MedicationTimeEditSheet.kt` (long-press tier chip → "When did you take it?") | Material 3 `TimePicker(state)` + `composeIntendedTime` | **YES** — picker has visual state separate from `state.hour`. |
| 2 | `LogCustomDoseSheet.kt` ("Log Custom Dose" from the medication log) | Material 3 `TimePicker(state)` + `todayAt(state.hour, state.minute)` | **YES** — same Material 3 picker. |
| 3 | `MedicationSlotEditorSheet.kt` ("Ideal Time (HH:mm)" in Settings → Slots) | `OutlinedTextField` with `sanitizeHhMm` | NO — text field IS the display; what you type is what you save. |
| 4 | `MedicationSlotPicker.OverrideEditor` ("Use different time for this med") | `OutlinedTextField` with HH:mm typing | NO — same as (3). |

Surfaces (3) and (4) are eliminated: their display surface is the
text field itself, so a `display ≠ saved` pattern is structurally
impossible. The bug lives in (1) and/or (2).

---

## I1 — `MedicationTimeEditSheet` + `LogCustomDoseSheet` force `is24Hour = false` (RED)

**Findings.** Both medication time pickers hardcode `is24Hour = false`:

- `MedicationTimeEditSheet.kt:63-67`
- `LogCustomDoseSheet.kt:65-69`

```kotlin
val timePickerState = rememberTimePickerState(
    initialHour = seedTime.hour,        // 0..23
    initialMinute = seedTime.minute,
    is24Hour = false                    // forced 12-hour with AM/PM toggle
)
```

The rest of the codebase defers to the system default by omitting the
parameter (whose Material 3 default is `is24HourFormat()`):

- `AddEditTaskScreen.kt:531-534` — no `is24Hour` arg.
- `TimelineScreen.kt:493-496` — no `is24Hour` arg.

(For symmetry: `AddBoundaryRuleSheet.kt:528-532` and
`AddEditHabitScreen.kt:428-432` *also* hardcode `is24Hour = false`,
but they're out of scope for this audit — same anti-pattern, different
features. Flagged in the anti-pattern catalog below.)

The forced 12-hour mode introduces an AM/PM toggle that the dial
surface does not. The two together produce three documented failure
modes:

1. **24-hour-locale users.** A user whose system is set to 24-hour
   sees a forced 12-hour picker. They mentally parse "8:30" as
   08:30 (24h), tap the dial at 8 with the toggle in its initial
   state, and walk away. State stores 08:30 or 20:30 depending on the
   toggle's initial position — and the toggle's initial position is
   set from `initialHour`'s natural half (AM if hour < 12, PM
   otherwise) so the state IS what wall-clock-now would be, not what
   the user mentally picked. Save logs "the right time" for an
   absolute-clock interpretation but not the user's pick.
2. **Toggle as a near-zero-affordance target.** The Material 3
   AM/PM toggle is a small chip beside the dial; users primed for the
   dial easily miss it. They re-confirm the picker is set correctly
   by reading the dial alone, save, and the toggle's initial position
   silently decides AM vs PM.
3. **Initial display mismatch on re-open.** When the sheet re-opens
   with an existing `initialIntendedTime` from a prior log (e.g. user
   opens to adjust), the AM/PM toggle reflects the *stored* hour, not
   the wall-clock — but the user, having just opened the sheet, may
   read the dial as "now" and miss the toggle's stored state.

In every case the saved value is internally consistent (`state.hour`
is the truth and the compose helpers operate on it correctly — see
`MedicationTimeEditComposeTest`). The visual presentation is what
diverges.

**Why this matches the report.** "Logged the right time" eliminates a
compose-helper bug (those are tested and green). "Showed wrong" with
a correct save points squarely at a presentation-layer mismatch — and
the only presentation-layer ambiguity in either sheet is the forced
12-hour mode + AM/PM toggle. The dial alone doesn't disambiguate
8 AM from 8 PM; only the toggle does, and the toggle is the visual
element most likely to be misread or missed.

**Risk classification — RED.** Medication logging is one of the
load-bearing trust surfaces in the app — a user logging "I took my
8 AM dose" needs to see exactly that on screen. A picker that can
mislead about AM/PM, even when the saved value is correct, breaks
the user's audit-of-themselves loop and is the kind of thing that
makes them stop trusting the medication log entirely.

**Recommendation — PROCEED.** Bundled into PR 1. Two-part fix:

1. **Stop forcing 12-hour mode.** Remove `is24Hour = false` from both
   pickers so they honor the system default (`is24HourFormat()`).
   Matches `AddEditTaskScreen` and `TimelineScreen`. Closes failure
   mode (1) for the population that experiences it most acutely
   (24-hour-locale users).
2. **Add an unambiguous selected-time confirmation label.** Show the
   currently-selected time below the dial, formatted from
   `state.hour` + `state.minute` in the user's locale's `h:mm a`
   (12-hour) or `HH:mm` (24-hour). The label is the source-of-truth
   readout: regardless of how the dial / toggle render, the user sees
   exactly what `state` holds — which is exactly what
   `composeIntendedTime` / `todayAt` will save. Closes failure modes
   (2) and (3) defensively even if a future Material 3 regression
   re-introduces toggle drift.

Both changes are additive — no schema, no behavior change to
`composeIntendedTime` / `todayAt`, no migration. Existing tests
continue to apply because the saved value remains derived from
`state.hour` / `state.minute` exactly as before.

---

## I2 — No UI test pins picker display state (YELLOW)

**Findings.** The compose-helper test
(`MedicationTimeEditComposeTest`, 6 tests) covers the math but the
sheet's actual UI never composes in a test. There is no
`LogCustomDoseSheet` test of any kind. This is consistent with the
broader codebase pattern (most Compose UI lives behind smoke tests in
`androidTest/` rather than per-component Robolectric tests), so the
audit is not flagging this as a P0 gap.

**Risk classification — YELLOW.** A targeted Robolectric (or compose
UI) test that asserts the new selected-time label renders the
expected `state.hour`/`state.minute` value would lock the fix in
permanently. Unit-test reach is limited because the underlying
Material 3 picker is opaque to compose-test interactions, but the
*label* itself is plain `Text` and trivially assertable.

**Recommendation — PROCEED.** Bundled into PR 1 — add a unit test
asserting the formatter helper used by the new label produces the
expected `h:mm a` / `HH:mm` strings for representative
(`state.hour`, `state.minute`, `is24Hour`) tuples. Pure-function so
it composes test-side cheaply.

---

## I3 — `seed = ... ?: System.currentTimeMillis()` recomputes per composition (DEFERRED)

**Findings.** `MedicationTimeEditSheet.kt:59-62`:

```kotlin
val seed = initialIntendedTime ?: System.currentTimeMillis()
val seedTime = remember(seed) {
    Instant.ofEpochMilli(seed).atZone(zone).toLocalTime()
}
```

When `initialIntendedTime == null`, `seed` advances on every
recomposition. The `remember(seed)` invalidates `seedTime` each time,
but `rememberTimePickerState` only consumes `initialHour` /
`initialMinute` on first composition — subsequent values are ignored.
So the picker keeps the *first* `seedTime`, and `seedTime` is only
used inside `rememberTimePickerState`. No observable bug today.

**Risk classification — DEFERRED.** Cosmetic / future-fragile. If a
later change adds another consumer of `seedTime` (e.g. a label!), the
recomputation pattern would silently bind the new consumer to a value
the picker has long since diverged from. Worth tightening to
`remember(initialIntendedTime) { ... ?: System.currentTimeMillis() }`
the next time this file is touched, but not the cause of the user's
report.

**Recommendation — STOP-no-work-needed for this audit.** Add a memory
entry candidate (Phase 3) so the next person touching this sheet
notices.

---

## Improvement table — wall-clock-savings ÷ implementation-cost

This audit is one user-facing bug, not a portfolio. Implementation
cost is small and savings are user-trust-shaped (hard to quantify
in wall-clock minutes — the realistic value is "user keeps trusting
the medication log"):

| Scope | Files touched | Approx LOC |
|-------|---------------|------------|
| Drop `is24Hour = false` (med pickers only) | `MedicationTimeEditSheet.kt`, `LogCustomDoseSheet.kt` | ~4 LOC |
| Selected-time confirmation label + formatter helper | Same two files + small shared formatter | ~30 LOC |
| Formatter unit test | new test file under `app/src/test/` | ~40 LOC |
| Total | 2 source files + 1 test file | ~75 LOC |

Well under any single-PR-too-big threshold. One coherent scope:
"medication time pickers no longer mislead the user about the
selected time." Bundled correctly per the workflow's coherent-scope
rule.

---

## Anti-pattern catalog (out-of-scope for this audit)

- **`is24Hour = false` hardcoded elsewhere** —
  `AddBoundaryRuleSheet.kt:531` and `AddEditHabitScreen.kt:431`
  carry the same anti-pattern but for boundary rules and habit
  reminders. Same fix would apply (remove the flag, defer to system
  default). Flagged here so the next pass sweeps both; not bundled
  because the user report is medication-scoped and these features
  have their own audit gravity.
- **Bare Material 3 `TimePicker(state)` with no confirmation
  readout** — applies to every site listed under "All time picker
  usages". Most of those are protected by being inside an
  `AlertDialog` whose Confirm button reads as "OK" (so the user
  isn't surprised by what got saved), but the medication sheets
  send the user back to a page where the picked time becomes
  audit-bearing data. The label fix is most justified there.
- **Hardcoded "anytime" slotKey for custom doses (LogCustomDoseSheet
  → repository)** — orthogonal to display drift, already chosen
  deliberately in the prior audit (`MEDICATION_LOG_ONE_TIME_CUSTOM_AUDIT.md`
  D2). Not revisiting.

---

## Phase 2 fan-out preview

One implementation PR (audit-doc PR is this file):

- **PR 0** — `docs/med-time-picker-display-drift-audit` — this audit
  doc. (Bundled into PR 1 since it's small per `audit-first` skip
  rule? No — keep the audit-doc commit separate per the local
  hook's branch-shape preference; both can live on the same branch.)
- **PR 1** — `fix/med-time-picker-display-drift` —
  - drop `is24Hour = false` from `MedicationTimeEditSheet.kt`,
    `LogCustomDoseSheet.kt`,
  - add a "Selected: H:mm a/HH:mm" `Text` below each picker reading
    `state.hour` / `state.minute`,
  - add a formatter unit test,
  - update inline comments to call out the new readout-source
    invariant.

Acceptance for PR 1:

- `./gradlew :app:assembleDebug` green locally.
- `./gradlew :app:testDebugUnitTest --tests "*Medication*"` green
  locally (existing 6 + new formatter test).
- Manual smoke (described in PR body): on a 24-hour-locale device,
  open Log Custom Dose at 14:30 → picker no longer forces 12-hour →
  selected-time label reads "14:30" → save → log row shows "2:30 PM"
  (locale-formatted). On a 12-hour-locale device: picker stays
  12-hour, label reads "2:30 PM", log matches.
- Auto-merge enabled.
