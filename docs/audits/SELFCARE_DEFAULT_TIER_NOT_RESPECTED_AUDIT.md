# Self-Care Default Tier Not Respected on Tab Open — Audit

**Scope:** User reports: "My default self-care is survival, and it says that on
the habits page, but it doesn't default to that when I actually click the
self-care tab." Investigate the disconnect between Habits page (correct) and
Self-Care screen (wrong) for the configured default tier.

**Date:** 2026-05-06
**Branch:** `claude/fix-selfcare-default-4u4x2`

## Item 1 — Self-Care screen ignores configured default tier on first open (RED)

### Findings

User-configured default tiers live in DataStore via
`AdvancedTuningPreferences.getSelfCareTierDefaults()`
(`app/src/main/java/com/averycorp/prismtask/data/preferences/AdvancedTuningPreferences.kt:822-830`).
The `SelfCareTierDefaults` data class itself defaults to
`morning="solid", bedtime="solid"`
(`AdvancedTuningPreferences.kt:235-239`).

**Habits page (works):**
`HabitListViewModel` declares `tierDefaults` as a `StateFlow`
(`HabitListViewModel.kt:160-162`) AND folds it into the giant `combine(...)`
that produces `items` (`HabitListViewModel.kt:164-183`, slot index 17 at
line 182). The screen collects `items`, which transitively subscribes
`tierDefaults`. Inside `computeCardData` the resolution order is correct:
stored log tier → configured default → penultimate fallback → first
(`HabitListViewModel.kt:283-289`).

**Self-Care screen (broken):**
`SelfCareViewModel` exposes `tierDefaults` as its own `StateFlow` with
`SharingStarted.WhileSubscribed(5000)` and an initial value of
`SelfCareTierDefaults()` (`SelfCareViewModel.kt:53-55`). Resolution is via
the plain (non-Composable, non-Flow) function `getSelectedTier(log)`
(`SelfCareViewModel.kt:152-165`), which reads `tierDefaults.value`
directly at call time.

`SelfCareScreen` invokes it as a plain expression:

```kotlin
val selectedTier = viewModel.getSelectedTier(todayLog)
```
(`SelfCareScreen.kt:77`)

The screen never collects `tierDefaults` — it collects only `routineType`,
`todayLog`, `allSteps`, `editMode` (`SelfCareScreen.kt:71-74`). Because
`WhileSubscribed(5000)` only runs the upstream DataStore flow while there
is at least one collector, and nothing collects `tierDefaults`, the
StateFlow's `.value` stays at its initial constructor default
(`SelfCareTierDefaults()` → morning="solid", bedtime="solid") forever for
this screen's lifetime. Even if the upstream did run, the Composable
wouldn't observe it for recomposition.

Net effect: a user who set the default to `survival` sees `solid`
(or whatever the data-class default is — never their stored value) on the
Self-Care screen, while the Habits card on the same routine reads
`survival` correctly.

This matches the user's symptom exactly: Habits page shows "Survival"
(reads stored DataStore value via subscribed combine), Self-Care tab
defaults to a non-stored value (reads unsubscribed StateFlow at its
initial-constructor fallback).

### Risk

RED — visible functional bug. Default-tier preference is silently
discarded on the actual screen the user interacts with, while reporting
the correct value elsewhere. Not a data-loss bug — the stored preference
is fine; only the read path is broken.

### Recommendation

PROCEED. Fix `SelfCareScreen.kt` to actually observe `tierDefaults` and
pass the live value into a refactored `getSelectedTier`.

Two equivalently good shapes:

1. Collect `tierDefaults` in the screen with `collectAsStateWithLifecycle`,
   and change `getSelectedTier` to take `(log, defaults)` instead of
   reading `.value`. Mirrors the `computeCardData(... tierDefaults)`
   pattern already used in `HabitListViewModel.kt:281-289`.
2. Convert `selectedTier` itself into a `StateFlow` inside
   `SelfCareViewModel` that combines `_routineType`, `todayLog`,
   `tierDefaults`, then collect it in the screen.

Shape 1 is smaller, matches the existing parity in `HabitListViewModel`,
and avoids redesigning the VM's exposed surface. Use it.

Test:
- Add a `SelfCareViewModelTest` that sets the default to `survival` via a
  fake `AdvancedTuningPreferences` and asserts `getSelectedTier(null)`
  returns `survival` for `morning` and `bedtime`. Without the fix this
  fails because `tierDefaults.value` returns the constructor default.

## Anti-patterns observed (flag, not fix)

- `WhileSubscribed(5000)` + reading `.value` from a non-collector site is
  a footgun whenever the StateFlow's initial value is a meaningful but
  wrong fallback (here, `SelfCareTierDefaults()` defaults to "solid").
  Worth a follow-up sweep for other VMs that expose preference-backed
  StateFlows but never have them collected. Out of scope for this audit.

- `getSelectedTier` and `HabitListViewModel.computeCardData` reimplement
  the same "stored → configured → penultimate → first" resolution
  inline. A small `SelfCareTierResolver` in `domain/usecase/` would
  remove the duplication. DEFERRED — not blocking this fix; flag for a
  future quality pass.

## Ranked improvements

| # | Improvement | Wall-clock saved | Cost | Ratio |
|---|---|---|---|---|
| 1 | Wire `tierDefaults` into `SelfCareScreen` and refactor `getSelectedTier(log, defaults)` (+ unit test) | High — fixes user-facing bug on every tab open | ~30 min | High |
| 2 | (Deferred) Extract shared `SelfCareTierResolver` to `domain/usecase/` | Low (cleanup) | ~45 min | Low |
| 3 | (Deferred) Audit other VMs for unsubscribed `WhileSubscribed` StateFlows whose `.value` is read directly | Medium (latent bugs) | Multi-hour sweep | Medium |
