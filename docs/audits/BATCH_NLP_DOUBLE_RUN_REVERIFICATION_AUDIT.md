# Batch NLP Double-Run — Re-verification Audit

**Scope.** User claim: *"Every batch NLP command runs twice."* Re-verify
the QuickAdd → BatchPreview → Haiku flow at the current main `HEAD`
(post v1.8.22, branch `claude/fix-duplicate-nlp-commands-FKWJb`) and
either (a) ship a fresh fix if the symptom is reproducible against
current code, or (b) confirm STOP-no-work-needed if the prior fix
(PR #1068) still defends the path.

**Why now.** The branch slug suggests the user wants a defensive sweep
even though `BATCH_OPERATIONS_DOUBLE_RUN_AUDIT.md` claims the issue was
closed by PR #1068 in the same release. This audit re-runs the trace
end-to-end against current `HEAD` so the verdict is grounded in
present-day code, not a stale audit.

**Investigation method.** (1) Walk every `_batchIntents` /
`_multiCreateIntents` emit-to-collect chain. (2) Confirm the
synchronous `_isSubmitting` guard at the top of
`QuickAddViewModel.onSubmit` is intact. (3) Enumerate every
composition site of `QuickAddBar` and verify whether any pair can
share the same `QuickAddViewModel` instance (which would cause one
emit to fan out to two collectors). (4) Spot-check `BatchPreviewVM`
re-entry guards. (5) Check whether any post-PR #1068 commit touched
the relevant files.

---

## R1 — `_isSubmitting` synchronous guard at top of `onSubmit` (GREEN)

`app/src/main/java/com/averycorp/prismtask/ui/components/QuickAddViewModel.kt:361-362`:

```kotlin
if (_isSubmitting.value) return
_isSubmitting.value = true
```

Lives **outside** any `viewModelScope.launch{}`, in the synchronous
prelude of `onSubmit`. A second tap arriving in the same main-thread
frame reads `_isSubmitting == true` and returns immediately, so:

- No second `_batchIntents.emit` for the batch path
  (`QuickAddViewModel.kt:389`).
- No second `_multiCreateIntents.emit` for the multi-create path
  (`QuickAddViewModel.kt:411`).
- No second `parser.parseRemote(text)` for the regular task path.

The flag is released in the relevant `try/finally` blocks
(`:377-393`, `:387-393`, `:432-442`, `:455-465`, `:560-567`,
`:631-633`), so a third tap *after* the first completes is allowed
through.

**Verified intact at HEAD.** No post-PR #1068 commit has touched these
lines (`git log 08120ed..HEAD QuickAddViewModel.kt` returns empty).

---

## R2 — Single collector for `batchIntents` + `multiCreateIntents` (GREEN)

`grep -rn "batchIntents\b\|multiCreateIntents\b"
app/src/main/java/com/averycorp/prismtask` returns exactly one
collector for each flow, both inside `QuickAddBar`:

- `QuickAddBar.kt:107` — `viewModel.batchIntents.collect { onBatchCommand(it) }`
- `QuickAddBar.kt:114` — `viewModel.multiCreateIntents.collect { onMultiCreate(it) }`

Both wrapped in `LaunchedEffect(viewModel)`. Key is the VM reference
(stable across recompositions), so each `QuickAddBar` instance
registers exactly one collector that lives for the QuickAddBar's
composition lifetime.

`MutableSharedFlow` fans out one emit to **all** subscribers, so
"every emit = N navigations where N = subscriber count." A second
QuickAddBar over the same VM would inflate N to 2. R3 enumerates
every composition site to rule that out.

---

## R3 — Composition-site enumeration: can two `QuickAddBar`s share a VM? (GREEN)

The four production composition sites:

| File | Line | VM source | Shared with another? |
|------|------|-----------|----------------------|
| `TodayQuickAddBar.kt` | 69, 93, 116, 138 | default `hiltViewModel()` | No — sites are `when {}` branches keyed on theme attrs (`brackets` / `sunset` / `terminal` / else). Mutually exclusive — exactly one fires per composition. |
| `PlanForTodaySheet.kt` | 229-236 | `hiltViewModel(key = "plan_sheet_quickadd")` | No — explicit key gives a distinct VM scope. The bottomBar's `FloatingQuickAddBar` and the sheet's `QuickAddBar` get **different** `QuickAddViewModel` instances even though they both live under the Today route's `NavBackStackEntry`. |
| `TaskListScreen.kt` | 737-749 | default `hiltViewModel()` | No — TaskList route has its own `NavBackStackEntry`; not co-mounted with TodayScreen. |
| `TodayScreen.kt` (via `FloatingQuickAddBar`) | 234 | default `hiltViewModel()` | No — the only QuickAddBar under the Today route's default VM scope; PlanForTodaySheet's keyed VM is sibling-scoped, not the same instance. |

The keyed `hiltViewModel(key = "plan_sheet_quickadd")` at
`PlanForTodaySheet.kt:230` is the load-bearing line that prevents
double-collection during plan-for-today flows. Without it, both
QuickAddBars would resolve to the same Today-route-scoped VM, and a
batch command typed into the sheet would fan out to two collectors —
two navigations to BatchPreview, two fresh `BatchPreviewViewModel`s,
two non-deterministic Haiku parses, the user's reported symptom.

**Per-VM-instance, only one collector exists at any given time.** No
fan-out path under any composition state I can construct.

---

## R4 — `BatchPreviewViewModel.loadPreview` re-entry guard (GREEN, closed by PR #1049)

`BatchPreviewViewModel.kt:69-76` short-circuits on `Loading`,
`Committing`, `Applied`, and `Loaded(sameCommand)`. So even if
`LaunchedEffect(commandText)` re-fires within one screen instance
(which shouldn't happen — `commandText` is a stable nav arg), the
second call returns before any second Haiku round-trip.

Per-NavBackStackEntry scope still means a *new* navigation creates a
*new* VM. R1 + R3 close the upstream emit path that would create such
a duplicate navigation — the only known trigger.

---

## R5 — Other `parseCommand` / `applyBatch` entry points (GREEN)

`grep -rn "parseCommand\b" app/src/main/java`:

- `BatchPreviewViewModel.kt:80` — only call site for
  `BatchOperationsRepository.parseCommand`. R4 covers the re-entry.
- `VoiceCommandParser.kt:53` — different function (local voice command
  grammar, no Haiku).

`grep -rn "applyBatch\b" app/src/main/java`:

- `BatchPreviewViewModel.kt:264` — gated by terminal `Applied` state in
  `approve()` (PR #1049). One-shot per VM lifetime.
- `MedicationViewModel.kt:510` — bulk-mark medication slot path,
  one-shot per dialog confirm.
- `AiActionHandlers.kt:220` (`ApplyBatchActionHandler`) — automation
  rule fire. One-shot per rule fire. The engine itself (out of audit
  scope) has its own dedup at the trigger layer — see
  `AUTOMATION_ENGINE_ARCHITECTURE.md` for the rule-fire dedup contract.

No new caller has been introduced post-PR #1068 that would re-trigger
the symptom.

---

## R6 — Post-PR #1068 commits touching the relevant files (GREEN)

```
git log --oneline 08120ed..HEAD -- \
  app/src/main/java/com/averycorp/prismtask/ui/components/QuickAddViewModel.kt \
  app/src/main/java/com/averycorp/prismtask/ui/components/QuickAddBar.kt \
  app/src/main/java/com/averycorp/prismtask/ui/screens/batch/ \
  app/src/main/java/com/averycorp/prismtask/data/repository/BatchOperationsRepository.kt \
  app/src/main/java/com/averycorp/prismtask/domain/automation/handlers/
```

Returns one commit:

- `ea7bdb3 feat(automation): /ai/automation/* endpoints + on-device handler wiring (A7) (#1071)`

That commit added `ApplyBatchActionHandler` (R5 covers it). It does
**not** touch `QuickAddViewModel`, `QuickAddBar`, or
`BatchPreviewViewModel`. The PR #1068 fix is intact.

---

## R7 — Existing regression test still passes shape (GREEN)

`app/src/test/java/com/averycorp/prismtask/ui/components/QuickAddViewModelBatchSubmitGuardTest.kt`
covers three cases:

- `onSubmit_batch_doubleTap_emitsBatchIntentOnce()` — locks in R1 for
  the batch path.
- `onSubmit_multiCreate_doubleTap_emitsMultiCreateIntentOnce()` —
  locks in R1 for the multi-create path.
- `onSubmit_batch_secondSubmitAfterFirstCompletes_isAllowed()` —
  locks in flag-release on `try/finally`.

Test file matches the production code shape verified in R1. No drift
between guard and test.

---

## Verdict

**Premise wrong — STOP-no-work-needed.** The user's claim *"Every batch
NLP command runs twice"* is **not reproducible against current main
`HEAD`**. PR #1068 closed the only known double-emit path (synchronous
re-entry race in `QuickAddViewModel.onSubmit`), and PR #1049 closed
the destination-side re-fire (`BatchPreviewViewModel.loadPreview`
re-entry guard). Both fixes are still in place; no post-merge commit
has touched the relevant files; the regression test
`QuickAddViewModelBatchSubmitGuardTest` still locks in the guard
shape.

If the user is observing the symptom on a real device, the next
investigation steps would be:

1. **Confirm app version**: capture `versionCode` (820 = v1.8.22)
   from `Settings → About`. Anything ≤ 819 (= v1.8.21) ships **without**
   PR #1068's fix and would still race on rapid resubmit.
2. **Capture a logcat trace** filtered to `QuickAddVM` /
   `BatchPreviewVM` while reproducing — the prior audit
   (`BATCH_OPERATIONS_DOUBLE_RUN_AUDIT.md`) flagged "different options
   the second time" as the unique signature of a non-deterministic
   Haiku double-parse. If that signature is absent on the user's
   build, the symptom is something else (e.g., visual flash from a
   recomposition, or a non-NLP duplicate from multi-select bulk-edit
   per A9 of the prior audit).
3. **Check for sideloaded debug builds** that might have stale
   `_isSubmitting` semantics — the synchronous claim is a single line
   that's easy to revert during local debugging.

No PR fans out from this audit. No code changes. The follow-up is a
reproduction-confirming round-trip with the user, not a defensive
re-fix on top of an already-defended path.

---

## Ranked improvement table

| Rank | Item | Risk | Action | Cost | Savings |
|------|------|------|--------|------|---------|
| — | (none) | GREEN | STOP-no-work-needed | 0 | 0 |

All seven verification items came back GREEN. The deferred items from
the prior audit (A8, A9) remain deferred under the same re-trigger
criteria — both are wasted-work-only failure modes with no
user-facing double-effect and no current trigger.

---

## Anti-patterns surfaced (re-confirmed, not actioned)

- **Re-running an audit on a closed bug without a fresh repro.** The
  user's branch slug suggests they want a defensive sweep, but the
  defensive sweep is the prior audit; running it again costs
  wall-clock and produces no PR. If the user has a fresh repro,
  capture it first; if they don't, this audit is the cheapest possible
  receipt that the path remains defended. (Memory candidate:
  *audit-first workflow should accept "premise wrong" as the
  terminal state on STOP-no-work-needed without rationalizing scope
  into busy-work fixes.*)
- **Multiple `QuickAddBar` mounts under the same `NavBackStackEntry`.**
  The keyed `hiltViewModel(key = "plan_sheet_quickadd")` at
  `PlanForTodaySheet.kt:230` is load-bearing. If a future PR adds a
  third QuickAddBar mount under the Today route without a unique key,
  it would silently start fan-out. Consider a lint rule or comment-
  level reminder near the existing keyed call. Not actioned here —
  hypothetical future-proofing without a current trigger.

---

## Phase 3 — Bundle summary

No PRs shipped. All seven verification items GREEN.

**Re-baselined wall-clock:** ~25min from audit-doc start to commit.
The audit doc itself is ~190 lines — well under the 500-line cap.

**Memory entry candidates:**

- *When the user re-files a previously-closed bug, re-verify against
  current `HEAD` before fanning out PRs.* The prior audit's verdict
  may have changed (regression, new code path, scope drift) — but
  equally often it hasn't, and the cheapest correct response is a
  STOP-no-work-needed receipt + a request for a fresh repro.

**Schedule for next audit:** none. If the user provides a logcat
trace or a versionCode below 820 reproducing the symptom, fan out a
fresh audit with that repro as the seed.

---

## Phase 4 — Claude Chat handoff

```markdown
## Scope
PrismTask Android — re-verified user claim "Every batch NLP command
runs twice" against current main HEAD (v1.8.22, build 820).

## Verdicts
| Item | Verdict | One-line finding |
|------|---------|------------------|
| R1 — `_isSubmitting` synchronous guard | GREEN | Intact at `QuickAddViewModel.kt:361-362`; no post-PR #1068 commit touched it |
| R2 — single collector per intent flow | GREEN | One `LaunchedEffect(viewModel)` collector each, in `QuickAddBar.kt:107` / `:114` |
| R3 — composition-site enumeration | GREEN | All four `QuickAddBar` mounts either mutually exclusive (`when {}`) or scoped to distinct VMs (keyed `hiltViewModel("plan_sheet_quickadd")`) |
| R4 — `BatchPreviewViewModel` re-entry | GREEN | Closed by PR #1049 (terminal `Applied` state + widened `loadPreview` guard) |
| R5 — other batch entry points | GREEN | `MedicationViewModel`, `ApplyBatchActionHandler` are one-shot; no new fan-out paths |
| R6 — post-PR #1068 commits | GREEN | Only `ea7bdb3` touched relevant files; doesn't change re-entry semantics |
| R7 — regression test integrity | GREEN | `QuickAddViewModelBatchSubmitGuardTest` still locks in three cases that match production guard shape |

## Shipped
None. Premise wrong — STOP-no-work-needed.

## Deferred
None new. Prior audit's A8 (calendar push enqueue not unique) and A9
(`TaskListViewModelBulk` re-entry guards) remain deferred under their
existing re-trigger criteria.

## Non-obvious findings
- The keyed `hiltViewModel(key = "plan_sheet_quickadd")` at
  `PlanForTodaySheet.kt:230` is load-bearing: it gives the sheet's
  `QuickAddBar` a distinct `QuickAddViewModel` instance from the
  bottomBar's `FloatingQuickAddBar`. Without it, both would share a
  VM and one emit would fan out to two collectors (the symptom the
  user describes). The line was added pre-PR #1068 and isn't called
  out anywhere as load-bearing — adding a comment would be
  defense-in-depth but isn't blocking.
- "Every batch NLP command runs twice" is the exact user-language
  signature of the bug PR #1068 closed. A user re-filing it on a
  build ≥ 820 implies either a regression (none found) or a
  different but visually-similar symptom (no current candidate). A
  logcat capture filtered to `QuickAddVM` / `BatchPreviewVM` would
  disambiguate.

## Open questions
- Does the user have a build ≥ 820 (the first build with PR #1068's
  fix shipped)? If they're on ≤ 819, the symptom is expected and the
  fix is to update.
- Does the symptom present as "two navigations to BatchPreview" or
  "one navigation, two confirmation toasts" or "two database
  mutations applied"? Each maps to a different sub-path that this
  audit ruled out independently.
```
