# Medication Custom Interval Chooser — Input Drop Audit

**Date**: 2026-05-02
**Scope**: User report — *"The custom interval chooser for medication is not taking the correct input."*
**Optimization target**: User-typed minute values must end up in state unchanged when valid; intermediate digits must not be clobbered while typing toward a valid value.
**Suspected failure mode**: `coerceIn(60, 1440)` runs on every keystroke, the resulting integer is the key for the text-state `remember(...)`, so typing any digit < 60 (e.g., the `1` while typing `120`) silently rewrites the field to `60`.

## Summary table

| # | Site | Field | Range | Verdict |
|---|------|-------|-------|---------|
| 1 | `MedicationEditorDialog.kt` (per-med) | Custom interval | 60–1440m | RED — PROCEED |
| 2 | `MedicationReminderModeSection.kt` (settings default) | Custom interval | 60–1440m | RED — PROCEED |
| 3a | `MedicationSlotEditorSheet.kt` (slot interval) | Custom interval | 60–1440m | RED — PROCEED |
| 3b | `MedicationSlotEditorSheet.kt` (slot drift) | Custom drift | 1–1440m | YELLOW — PROCEED (same root cause; bundles cleanly) |
| 4 | `MedicationSlotPicker.kt` `OverrideEditor` | Override drift | none in-component | GREEN — STOP-no-work-needed |
| 5 | `TimerSection.kt` / `SettingsDialogs.kt` `DurationPickerDialog` | Slider-driven | varies | GREEN — STOP-no-work-needed |

## 1. `MedicationEditorDialog.kt` — per-medication custom interval (RED)

**Files**: `app/src/main/java/com/averycorp/prismtask/ui/screens/medication/components/MedicationEditorDialog.kt:84-91, 203-216`.

**Reproducer (manual)**: Open any medication editor → reminder mode "Interval" → tap "Custom" chip → backspace the seeded value → type `1` aiming for `120`. Expected: text shows `1`. Observed: text snaps to `60` on the very first keystroke; subsequent keystrokes append to `60` (so typing `120` actually yields `601` then `6012` clamped to `1440`, etc.).

**Root cause**: Two-line interaction in the same composable:

```kotlin
var intervalMinutes by remember { mutableStateOf(initialReminderIntervalMinutes ?: 240) }
var customIntervalText by remember(intervalMinutes) {     // KEY = intervalMinutes
    mutableStateOf(intervalMinutes.toString())
}
...
OutlinedTextField(
    value = customIntervalText,
    onValueChange = { raw ->
        customIntervalText = raw.filter { it.isDigit() }.take(4)   // (a) sets text to "1"
        customIntervalText.toIntOrNull()?.let { mins ->
            intervalMinutes = mins.coerceIn(60, 1440)              // (b) writes 60 back
        }
    },
)
```

(b) flips `intervalMinutes` from 240 → 60. On the next recomposition, `remember(intervalMinutes)` sees the key change and re-runs its initializer, overwriting the just-typed `customIntervalText = "1"` with `"60"`. The TextField's `value` prop then displays `"60"` and the user's keystroke appears to be ignored.

The bug only fires when the typed value is *temporarily* below 60 (every value in `1..59`, which includes the leading digit of every intended `1xx`–`5xx` value). Typing values that begin with `6`–`9` accidentally avoids the bug.

**Risk**: RED — this is the only path to set a non-preset interval (60, 90, 150, 200, …). Users cannot enter most custom values short of pasting the full string at once or typing right-to-left, both undiscoverable.

**Recommendation**: PROCEED. Decouple the text-field state from the validated integer: drop the `remember(intervalMinutes)` key, advance `intervalMinutes` only when the typed text parses to a value already in `[60, 1440]`. Keep the seeded text up-to-date when the *chip* changes the value (use `LaunchedEffect(intervalMinutes)` with a guard, or set `customIntervalText` inline in the chip's `onClick`).

## 2. `MedicationReminderModeSection.kt` — settings default custom interval (RED)

**Files**: `app/src/main/java/com/averycorp/prismtask/ui/screens/settings/sections/medication/MedicationReminderModeSection.kt:113-168`.

`IntervalPicker(currentMinutes, onSave)` has the same shape: `var customText by remember(currentMinutes) { mutableStateOf(currentMinutes.toString()) }`, and `onValueChange` calls `onSave(mins.coerceIn(60, 1440))`. The parent `MedicationReminderModeSection` immediately persists via `viewModel.save(prefs.copy(intervalDefaultMinutes = minutes))` (line 90). The new prefs flow back through `prefs.intervalDefaultMinutes`, so `currentMinutes` re-keys the remember on every keystroke that produces a sub-60 number — same clobber.

**Risk**: RED — identical UX to site 1, on the global default. The settings screen is also where users change cadence after the fact, so this hits power-users.

**Recommendation**: PROCEED. Same fix shape as site 1, applied at the `IntervalPicker` composable. Bonus: `IntervalPicker` is a private helper (so the fix is local), but it is not shared with site 1's intra-dialog interval row — a tiny extraction (e.g., `MedicationCustomIntervalField`) could collapse the duplicated UI in this PR or a follow-up.

## 3. `MedicationSlotEditorSheet.kt` — slot-level custom interval AND drift (RED + YELLOW)

**Files**: `app/src/main/java/com/averycorp/prismtask/ui/screens/settings/sections/medication/MedicationSlotEditorSheet.kt:81-95, 160-173, 254-267`.

Two pickers in this sheet, both with the same pattern:

- **Interval** (lines 91-95, 254-267): `var customIntervalText by remember(intervalMinutes) { ... }` + `intervalMinutes = mins.coerceIn(60, 1440)`. RED for the same reason as sites 1 and 2.
- **Drift** (lines 81-84, 160-173): `var customDriftText by remember(driftMinutes) { ... }` + `driftMinutes = mins.coerceIn(1, 1440)`. The lower bound is **1**, so the clobber only fires when the user (a) deletes everything to type a 5-digit value (won't happen — `.take(4)` caps at 1440 anyway) or (b) the chip's `(driftMinutes + 1).coerceAtLeast(1)` accidentally seeds a value the user then tries to backspace below 1. In practice the user *can* type intermediate digits because `1.coerceIn(1, 1440) == 1` doesn't change the key. The bug pattern is present but the symptom is much milder. YELLOW.

**Risk**: RED on interval (same blast as sites 1–2), YELLOW on drift (mostly latent, would bite if the floor changes).

**Recommendation**: PROCEED on both — the fix is structurally identical, both pickers live in the same file, and bundling avoids leaving a known-fragile duplicate of the same anti-pattern next door.

## 4. `MedicationSlotPicker.kt` `OverrideEditor` — override drift (GREEN)

**Files**: `app/src/main/java/com/averycorp/prismtask/ui/screens/medication/components/MedicationSlotPicker.kt:200-268`.

`driftInput` is `remember(sel.overrideDriftMinutes)`, but `onValueChange` only calls `onOverrideChange(timeInput, driftInput.toIntOrNull())` — there is **no `coerceIn` and no write-back to a state that would re-key the remember mid-keystroke**. The selection upstream ultimately accepts the new value, but it doesn't normalize it back to a different integer. So intermediate digits stay typed.

**Risk**: GREEN — pattern looks similar but does not exhibit the bug. (Validation is deferred to wherever the override is consumed; that's a separate question of input *acceptance*, not input *display*.)

**Recommendation**: STOP-no-work-needed for this audit. Worth noting as an anti-pattern *near miss* — if anyone adds a coerced write-back here later, they will reintroduce the bug.

## 5. Slider-based duration pickers (GREEN)

**Files**: `app/src/main/java/com/averycorp/prismtask/ui/screens/settings/sections/TimerSection.kt:220-269`, `app/src/main/java/com/averycorp/prismtask/ui/components/settings/SettingsDialogs.kt:130-178`.

Both use a `Slider` for input rather than a free-form `OutlinedTextField`. Slider values are always in-range by construction; coercion is a defensive no-op. The `remember(currentMinutes)` keying is fine because no mid-edit state mutation triggers it.

**Risk**: GREEN.

**Recommendation**: STOP-no-work-needed.

## Ranked improvement table

| Rank | Item | Wall-clock saved | Implementation cost | Ratio (saved/cost) |
|------|------|------------------|----------------------|---------------------|
| 1 | Sites 1 + 2 + 3a + 3b — fix the four custom-numeric pickers | High (silently broken core flow on all three medication entry surfaces) | ~Small (one shared helper or four near-identical edits + 1 unit test per shape) | **High** |

Single coherent scope ⇒ single PR (per fan-out bundling guidance: "bundle multiple small fixes into one PR only when they're a single coherent scope").

## Anti-pattern note (not necessarily fixed)

> **Avoid keying `remember` on a value that the same composable's input handler mutates via coercion.** The hidden round-trip silently rewrites typed text. Safer shapes:
>
> 1. Hold the text in plain `remember { mutableStateOf(...) }` (no key) and only update the validated integer when the parse falls inside the valid range. Reseed the text from the integer only when an *external* event (chip click, parent emission) changes it.
> 2. If you must key on the integer, also gate the write: `if (mins in lo..hi) intervalMinutes = mins`. Don't coerce.

Worth flagging to the codebase generally; not fixing the GREEN site (`OverrideEditor`) opportunistically because it doesn't currently exhibit the bug — the trigger for fixing it would be the next time someone adds coercion there.

## Phase 3 — Bundle summary

**Shipped (1 PR, single coherent scope per fan-out rule):**

- **PR #1051** — `fix(medication): preserve typed digits in custom interval/drift fields` (squash-merged 2026-05-02 04:14 UTC, merge SHA `4ecf0450`). Touched four call sites (per-med interval, settings-default interval, slot interval, slot drift) by routing them through a new pure helper `applyMinuteFieldEdit` in `app/src/main/java/com/averycorp/prismtask/ui/screens/medication/components/MinuteFieldInput.kt`. Locked the contract with `MinuteFieldInputTest` (10 cases, all green).

**Measured impact (post-merge):**

- Behavioral: typing any sub-floor digit no longer round-trips through `coerceIn(60,1440)` → re-keyed `remember(...)` → text-field clobber. Verified by the new unit tests; manual UI verification deferred to release validation per the "QA can't repro on Compose UI in CI" carve-out.
- Code-debt: collapsed 4 copies of the same anti-pattern into one helper. Future numeric pickers should reuse `applyMinuteFieldEdit` rather than re-deriving the parse-+-clamp logic.

**Wall-clock per PR:** Phase 1 audit + Phase 2 fix + tests landed in one session (~25 min including CI wait — under cache TTL). The audit doc itself is 114 lines (well under the 500-line cap).

**Memory entry candidates:**

- *Surprising:* `remember(intMutatedByInputHandler) { mutableStateOf(int.toString()) }` is a structural Compose anti-pattern that *looks* idempotent but silently clobbers typed text whenever the input handler coerces. Worth a feedback memory because the failure mode is invisible in code review (each line looks fine in isolation) and is a likely repeat occurrence in any numeric `OutlinedTextField`. **Save.**
- *Not surprising:* the "extract a pure helper to test composable input logic" pattern was already established by `composeIntendedTime`; following it here didn't reveal anything new. Skip.

**Schedule for next audit:** none queued — this was a single-bug audit. The anti-pattern memory will catch the next instance during normal review.

