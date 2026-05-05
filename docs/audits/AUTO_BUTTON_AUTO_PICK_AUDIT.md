# Auto Button — Auto-Pick Audit (Phase 1)

**Scope.** Convert the "Auto" affordance on the task editor's Organize tab
from a *meta-selection chip* (current) into an *auto-pressed button* that
runs the keyword classifier and pre-picks one of the real chips for the
user. The button auto-fires on signal (so the user always sees a real
selection); "Auto" is no longer one of the choices. Applies to all three
classifier-backed selectors: **Life Category**, **Task Mode**, and
**Cognitive Load**.

**Operator framing (verbatim).**
> "Auto should be an automatically pressed button that chooses a pick for
> you on the task, not a selection."

---

## 1. Auto chip on Life Category / Task Mode / Cognitive Load (GREEN, PROCEED)

**Findings.**

The Organize tab on the task editor renders three classifier-backed
selectors, each of which currently includes an `"Auto"` chip alongside the
real category chips:

- `LifeCategorySelector` — `app/src/main/java/com/averycorp/prismtask/ui/screens/addedittask/tabs/OrganizeTab.kt:806-848` — chip at line 818 (`label = "Auto"`).
- `TaskModeSelector` — same file `:856-891` — chip at line 867.
- `CognitiveLoadSelector` — same file `:899-934` — chip at line 910.

Each "Auto" chip is rendered as `selected = !manuallySet` and
`onClick = { onSelect(null) }`, i.e. clicking it clears the user's pick
and re-enters "deferred classification" mode.

The state machine is mirrored in three `<X>ManuallySet: Boolean` flags on
the ViewModel (`AddEditTaskViewModel.kt:160`, `:185`, `:204`), which are
flipped from the chip handlers
(`onLifeCategoryChange` / `onTaskModeChange` / `onCognitiveLoadChange` at
`:614-635`).

Resolution at save time is deferred — when `manuallySet == false`, the
keyword classifier runs against `title + description` and the guess is
written to the entity:

- `resolveLifeCategoryForSave` — `:644-651`
- `resolveTaskModeForSave` — `:657-664`
- `resolveCognitiveLoadForSave` — `:671-678`

Called from the save path at `:914-916`.

Initial state (create):
- New task → all three default to `null` + `manuallySet = false`
  (`:398-403`), so "Auto" is the default.
- Existing task → loaded enum value is the chip selection; `manuallySet`
  is set to `true` whenever the persisted value is non-`UNCATEGORIZED`
  (`:436-443`). Persisted `UNCATEGORIZED` reads back as `null` + Auto
  selected — i.e. the chip cannot distinguish "user explicitly picked
  Uncategorized" from "system gave up".

There is also a quiet behavioural coupling worth flagging: the boundary
suggestion path (`:248-253`) writes through `lifeCategory` *only when
`!lifeCategoryManuallySet`*. Whatever replaces "Auto" must keep an
equivalent gate or boundary-suggested categories will overwrite the
auto-picked value silently.

No tests reference `manuallySet` or the `resolveXxxForSave` helpers
directly (`grep` over `app/src/test/` and `app/src/androidTest/` returns
zero matches). The classifier behaviour itself is tested in
`LifeCategoryClassifierTest`, `TaskModeClassifierTest`,
`CognitiveLoadClassifierTest` — those don't need to change.

**Premise** — verified GREEN. "Auto" is currently a selectable chip in
all three selectors and behaves exactly as the operator describes
(chip-as-meta-selection, classifier deferred to save).

**Risk classification — GREEN.**
- Surface area is local: one composable file (3 selectors), one ViewModel,
  no DAO/migration changes, no entity changes, no schema or sync impact.
- The persisted column shape (`life_category`, `task_mode`, `cognitive_load`
  enum names) is unchanged — the classifier's output already lands in those
  columns today, so no migration / backfill / sync churn.
- Free of cross-feature coupling beyond the boundary-suggestion gate
  flagged above.

**Recommendation — PROCEED.** Single coherent change; ship as one PR.

---

## 2. Design — what "auto-pressed button" means in practice (GREEN, PROCEED)

The operator's phrase "automatically pressed button that chooses a pick
for you" is unambiguous on the **outcome** (the user always sees a real
chip selected, never an "Auto" placeholder), but the **trigger** for the
auto-press has three plausible shapes. Locking it down before
implementation:

### Trigger options considered

| Option | When it fires | Trade-off |
| --- | --- | --- |
| A. On editor open | Once, when the sheet appears | Title is empty for new tasks → classifier returns `UNCATEGORIZED` → useless. Only helps when editing an existing untyped task. |
| B. On title/description settle | Debounced after the user stops typing | Matches operator framing best — the auto-press happens "for you" while you fill the form. Costs one debounced effect per selector. |
| C. On save | Already what happens today | This is the *current* behaviour with a different label — operator explicitly rejected "selection" UX. |

**Decision — Option B.** Auto-fires when (a) the user has not made a
manual pick on that selector, and (b) `title` is non-blank. Re-runs on
title/description change while the manual flag is still false. The first
time the classifier returns a non-`UNCATEGORIZED` guess, the chip becomes
selected and the manual-flag stays *false* (so subsequent title edits can
re-pick if relevant) **but** the boundary-suggestion gate at `:249` keeps
working because we keep the `manuallySet` semantic intact.

> Edge case: if classifier returns `UNCATEGORIZED`, no chip is selected
> and the auto-press will retry on the next title/description change.

### Manual override semantics

- Tapping any *real* chip sets that selector's `manuallySet = true` and
  freezes the auto-press (no further auto re-runs for that selector while
  the editor is open).
- There is no "go back to Auto" affordance — the operator's framing is
  that Auto is no longer a selection. To "reset", the user would need to
  long-press the chip (deselect) — covered by the existing chip handler
  if `onSelect(null)` is wired to a long-press / "clear" gesture.

  **Open question for Phase 2** (not blocking): is a clear-selection
  gesture needed at all? Existing UX has no "tap-again-to-deselect" — a
  user who wants to re-trigger auto would have to clear title and retype,
  which is awkward. A small "Reset" icon button next to each selector
  label is the safest add. Defaulting to *yes, ship a Reset icon* unless
  the operator says otherwise during Phase 2.

### Visible affordance

A single "Auto" button (icon: `AutoAwesome` or `AutoFixHigh`) sits at the
left edge of each selector's `FlowRow`, where the "Auto" chip used to be.
Tapping it manually re-runs the classifier on demand (useful when the
user changes the title and wants to re-pick without retyping). The button
is *visually distinct* from the chips (filled tonal vs. outlined) so the
user reads it as an action, not a selection.

**Risk classification — GREEN.** Local UX choice with no data-shape
implications. Worst case the auto-press picks the wrong chip; user taps
the right one and overrides. No regression worse than the current
implicit save-time guess.

**Recommendation — PROCEED.** Implement Option B with a manual "Auto"
icon button as the visible affordance.

---

## 3. Existing test surface (GREEN, PROCEED)

`grep` confirms no existing test asserts on the chip's "Auto"
selected-state. The classifier units (`LifeCategoryClassifierTest` etc.)
test the classifier itself, not the editor's wiring to it.

New unit tests to add (`AddEditTaskViewModelTest` doesn't currently cover
this area — adding the slice for this PR):

1. Auto-press fires once when title becomes non-blank → chip reflects
   classifier guess; `manuallySet` stays `false`.
2. Auto-press is suppressed once the user manually picks a chip; further
   title edits do not move the chip.
3. Boundary-suggestion path still works when chip is auto-picked
   (i.e. `manuallySet == false`) — boundary suggestion overwrites the
   auto pick.
4. Save path: when chip shows an auto-picked value, the *displayed* value
   is what gets persisted (no second classifier run that could pick
   differently if keywords changed mid-session).
5. UNCATEGORIZED guess → no chip selected; auto-press retries on next
   title/description change.

**Recommendation — PROCEED**, bundle the tests into the same PR.

---

## 4. Open implementation notes (non-blocking)

- `manuallySet = false && lifeCategory != null` is a *new* state combo
  ("auto-picked, not user-confirmed"). Confirm no other reader of the
  ViewModel relies on the old invariant
  (`manuallySet == false ⇒ lifeCategory == null`). A grep on
  `lifeCategoryManuallySet`, `taskModeManuallySet`,
  `cognitiveLoadManuallySet` already done at audit time shows only the
  Organize tab + ViewModel itself — no external readers. Safe.
- `resolveXxxForSave` already does the right thing — if
  `manuallySet == false && value != null`, we want to persist the
  displayed `value` (the auto-picked one). Today's helper falls through
  to the classifier in that combo. Fix: `if (value != null) return value`
  first, then fall through to the classifier only on `null`. Cleanly
  preserves the "guarantee a real value at save time" invariant.
- Keep the old "Auto" string out of the codebase to avoid future
  archaeology — delete the `label = "Auto"` chip rather than commenting it
  out. (Per CLAUDE.md: don't leave `// removed` markers.)

---

## Ranked improvement table

| # | Item | Verdict | Wall-clock saved ÷ cost |
| - | ---- | ------- | ----------------------- |
| 1 | Replace "Auto" chip with auto-pressed button on all 3 selectors (#1, #2, #3) | PROCEED | High — single coherent UX fix, ~150 LOC editor + ~80 LOC tests |

Single-item table — bundling the three selectors into one PR is correct
because (a) they share a single composable file, (b) they share a single
ViewModel, (c) the UX paradigm shift is identical for all three; shipping
two of three would leave a confusing inconsistent editor.

## Anti-pattern list

- **Don't introduce a new "auto-picked" persistence state.** The DB
  columns stay the same shape. The "auto vs manual" distinction lives in
  the ViewModel for as long as the editor is open and disappears at save
  time. Adding a new persisted column would create a sync / migration /
  backfill problem with no payoff.
- **Don't auto-press on editor open with a blank title.** Classifier on
  empty input returns UNCATEGORIZED and the user gets the impression auto
  is broken. Wait for `title.isNotBlank()`.
- **Don't gate the auto-press on Pro / paid tier.** The classifier was
  always free; nothing in the operator framing suggests changing that.
