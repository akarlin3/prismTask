# D-series automation action silent failure — audit

**Scope (operator):** AVD pair test Stage 7 (May 6, early morning) showed
a rule with action "Set priority High" firing on Device A, but the priority
write never landed. No error UI, no toast, no log row beyond the ordinary
"matched=true". Per the audit-first scope memo
(`cc_d_automation_action_silent_failure_audit_first.md`), the broader brief
is to **audit every automation action type** for the same silent-failure
pattern and fix all of them in a single PR (memory #28), with explicit
error surfaces for partial / not-implemented action handlers.

**Domain:** the v1.7+ Automation rules engine
(`domain/automation/`, shipped in PR #1056; starter library in PR #1057;
edit-screen in #1083; per-minute time triggers in PR #1098).

**Optimization target:** close the launch-blocker silent-failure surface
before Phase F kickoff May 15. Operator memory #28 — single PR, fan-out
bundling on the action-execution layer specifically.

**Verdict at a glance.**

The bug is not where the operator first suspected it. The
`MutateTaskActionHandler` priority write *does* land when the engine
reaches it. The actual silent failure is **upstream**, in the trigger
emission layer: `TaskRepository.addTask()` (the path used by every
user-facing task creation surface — TaskList QuickAdd, Today plan-for-
today, AddEditTask save-new, MultiCreate, Onboarding, Conversation
extract) does **not** emit `AutomationEvent.TaskCreated`. Only
`insertTask()` does (line 211). The two-method asymmetry was not flagged
in the engine architecture doc and is not visible from the rule-firing
log (no event ⇒ no log row ⇒ no error UI ⇒ "the rule didn't fire" looks
identical to "the rule fired but did nothing").

Once that is fixed, three action handlers retain a secondary silent-
failure pattern worth hardening in the same PR: `MutateTask`,
`MutateHabit`, `MutateMedication` all silently no-op on unknown /
mistyped update keys. They return `ActionResult.Ok` with the keys list
in the message, regardless of whether anything actually got written.

The `RED` item below — A1 — is by itself enough to explain the AVD
Stage 7 reproduction. A2/A3/A4 are `YELLOW` polish-the-fix items that
ride along in the same PR per memory #28 broad-scope. Aggregate Phase 2
LOC fits inside the STOP-F ceiling.

---

## A1 — `TaskRepository.addTask()` does not emit `TaskCreated` (RED)

**Findings.**

- `app/src/main/java/com/averycorp/prismtask/data/repository/TaskRepository.kt:227-279`
  — `addTask(title, …)` calls `taskDao.insert(task)`, then bumps
  `syncTracker`, `calendarPushDispatcher`, `widgetUpdateManager`, and
  the reminder scheduler. There is **no** `automationEventBus.emit(...)`
  call. By contrast, `insertTask(task: TaskEntity)` at lines 207-225
  emits `AutomationEvent.TaskCreated(id)` at line 211.
- `addSubtask(...)` at lines 161-184 has the same gap. (Subtask
  emission may be intentionally suppressed — the engine architecture
  doc § A3 doesn't say either way — so the audit treats this as
  `DEFERRED`, not bundled. See § A5 below.)
- Caller inventory for the gap-affected method:
  - `ui/screens/tasklist/TaskListViewModel.kt:600` (QuickAdd row)
  - `ui/screens/today/TodayViewModel.kt:255` (plan-for-today sheet)
  - `ui/screens/addedittask/AddEditTaskViewModel.kt:978` (save-new
    path inside the tabbed editor)
  - `ui/screens/multicreate/MultiCreateViewModel.kt:117`
  - `ui/screens/onboarding/OnboardingViewModel.kt:230`
  - `ui/screens/extract/PasteConversationViewModel.kt:77`
  Every one of these is a primary user-facing creation surface.
- Caller inventory for `insertTask()` (the working path) is mostly
  *re-insert / restore* flows (Timeline drag-edit, Chat batch op,
  TaskList undo-restore, MonthView, WeekView, SchoolworkViewModel,
  ProjectImporter) plus QuickAddViewModel.kt:539 (the *natural-
  language* quick-add bar — which is a different path than the
  TaskList QuickAdd row). So the operator's AVD reproduction may
  have used the NLP path (succeeded) or the row path (silent-failed)
  depending on which surface was tapped — explaining the "rule fired"
  observation if the NLP path was used to seed test data and the
  later "Stage 7" repro used the row.
- No existing test asserts `addTask()` emits `TaskCreated`.
  `TaskRepositoryTest.kt:108-596` exercises `addTask()` six times for
  classification / sync-tracking / reminder behaviour but never
  asserts on `automationEventBus`.
- Drive-by sweep: `git log -p -S "addTask\|TaskCreated\|automationEventBus"
  --since=2025-09-01` — no commit ever added the emit to `addTask()`.
  The original engine PR #1056 wired `insertTask()` only; the
  asymmetry was not flagged in either of the two follow-up audits
  (`AUTOMATION_ENGINE_ARCHITECTURE.md`,
  `AUTOMATION_USER_CREATE_GAP_AUDIT.md`).

**Why this is the silent failure the operator hit.** With no event
emitted, the engine's `bus.events.collect { ... }` loop never runs for
this task. There is no firing log row written (the firing-log path is
inside `executeRule`, which `handleEvent` only reaches after a successful
`bus.emit`). To the user, the rule "fires" if and only if a *different*
creation path — the NLP quick-add bar, or any of the re-insert paths —
was used during the test session. The operator's hypothesis ("action
lookup matched but write didn't land") is consistent with someone
reading the rule list's `lastFiredAt`/`fireCount` columns from a
*different* test event and assuming the latest task creation matched
too. Without a `TaskCreated` emission, the action handler is never
even invoked.

**Recommendation: PROCEED.** This is the load-bearing fix and on its
own closes the AVD Stage 7 reproduction.

## A2 — `MutateTaskActionHandler` silently ignores unknown / mistyped fields (YELLOW)

**Findings.**

- `domain/automation/handlers/SimpleActionHandlers.kt:96-117`. The
  `for ((field, value) in mutate.updates)` loop maps each known field
  via a `when` and falls through `else -> next` for unknown fields,
  with the inline comment *"unknown fields silently ignored — handler
  is best-effort"*. Type mismatches are also silent: `priority` for
  example is `next.copy(priority = (value as? Number)?.toInt() ?: next.priority)`,
  so a stringified priority like `"3"` (which is what the JSON adapter
  *would* emit if a future rule template author used a string literal)
  would silently keep the existing priority.
- Final write at line 108 is guarded by `if (next != task)
  taskRepository.updateTask(next)`. When every requested update is
  silently dropped, `next == task` and no write happens — but the
  handler still returns `ActionResult.Ok(type, "updated task ${task.id}
  (${mutate.updates.keys})")` at line 116, claiming success. The
  rule-firing log row will read `actions=[{type: mutate.task, status:
  ok, message: "updated task 42 ([priority])"}]` even though *nothing
  changed*. This is the textbook silent-failure pattern called out in
  the operator memo § A.3 ("PARTIAL: handler does some of the work but
  not all").
- `tagsAdd` / `tagsRemove` (lines 119-139): list elements that aren't
  strings are silently skipped (`(raw as? String)?.removePrefix("#")`).
  Whole-list type mismatch (`updates["tagsAdd"] as? List<*>` returning
  null because the caller passed a comma-separated string) is silently
  treated as "no tags to add."
- Test gap: there is no test file for `SimpleActionHandlers.kt`
  (`find app/src/test/java -name "*SimpleAction*"` returns empty).
  Only the AI handlers have unit tests
  (`domain/automation/handlers/AiActionHandlersTest.kt`).

**Why this matters even though A1 is the operator's bug.** Once A1 is
fixed and the rule fires reliably, any future template / hand-authored
rule that mistypes a field name (e.g. `"task.priority"` vs `"priority"`)
will silently no-op with no surface to the user. The same pattern is
the root cause shape the operator memo § A.3 worries about most.

**Recommendation: PROCEED.** Tighten to: emit
`ActionResult.Error(type, "unknown field <name>")` when an unknown key
appears, `ActionResult.Error(type, "wrong type for <field>: expected
<X>, got <Y>")` on type mismatch, and demote the success message to
distinguish "nothing actually changed" from "wrote N fields."

## A3 — `MutateHabitActionHandler` returns Skipped, but only `isArchived` is real (YELLOW)

**Findings.**

- `SimpleActionHandlers.kt:147-171`. Only `isArchived` is supported.
  The handler does emit `ActionResult.Skipped(type, "no supported
  updates")` if no recognised key was present, which *is* an error
  surface — already correct behaviour per § B.2 of the memo. Good
  news.
- However: the doc comment at line 144-145 (*"supports `isArchived`
  toggle for v1. Other habit fields can land later without a schema
  change."*) is the only place future readers would learn that
  `name`, `category`, `streakCount` etc. are unsupported. Authoring
  an `AutomationAction.MutateHabit(updates = mapOf("name" to "Run"))`
  silently produces a `Skipped` log row — surfaced, but the *reason*
  string ("no supported updates") doesn't tell the user which keys
  were actually unrecognised.
- Test gap: same as A2 — no unit test.

**Recommendation: PROCEED.** Lift the supported-keys list into a
constant and emit `ActionResult.Error` (not Skipped) when *any* key
is unrecognised, with the unrecognised key in the message. Skipped
remains correct only for "the entity was missing from the event"
(line 158-159).

## A4 — `MutateMedicationActionHandler` partial coverage (YELLOW)

**Findings.**

- `SimpleActionHandlers.kt:255-298`. Supports `isArchived` + `name`
  rename only. Dose mutations are deliberately routed through
  `apply.batch` per the doc comment at line 245-253. The Skipped
  reason at line 293 *does* call out the supported set: *"no supported
  updates (only isArchived + name; dose mutations go through
  apply.batch)"*. So the surface here is already in better shape than
  A3 — the failure mode is partially addressed.
- Same gap as A3: unrecognised keys aren't called out individually.
- Test gap: no unit test.

**Recommendation: PROCEED.** Lift to the same shape as A3 — error
on unrecognised keys with the offending key in the message.

## A5 — `addSubtask()` + `reorderSubtasks()` no-emit (DEFERRED)

**Findings.**

- `TaskRepository.kt:161-184` (`addSubtask`) — does not emit
  `TaskCreated`. `:186-191` (`reorderSubtasks`) — does not emit
  `TaskUpdated`. Neither does
  `:193-197` (`updateTaskOrder`).
- Subtask creation may be intentionally excluded from the event bus
  to avoid drowning rules with bulk-emit during big task imports.
  The architecture doc doesn't take a position either way.

**Why deferred.** Operator memo § A.5 explicitly says *"sibling-
primitive surfaces — file deferred, do NOT bundle without operator
pre-approval."* This isn't strictly a sibling system (it's the same
TaskRepository), but the design question (should subtask creation
emit, with what semantics?) is bigger than A1. Note as deferred,
re-trigger when an automation use-case requires it.

**Recommendation: DEFER.** Note in Phase 4 handoff.

## A6 — Other action handlers (GREEN)

Verdicts after reading each handler against the per-type matrix in the
operator memo § A.3:

- `NotifyActionHandler` (`SimpleActionHandlers.kt:30-68`) — WORKING.
  Posts via `NotificationManager`, returns `Ok` with the notification
  id. No silent-failure surface.
- `LogActionHandler` (`:178-189`) — WORKING. Pure observability;
  returns `Ok(message)` directly.
- `ScheduleTimerActionHandler` (`:206-241`) — WORKING. Already wraps
  the `PomodoroTimerService.start()` call in `runCatching` and maps
  the failure to `ActionResult.Error` with the exception class name —
  exactly the convention the memo § B.2 wants.
- `ApplyBatchActionHandler` (`AiActionHandlers.kt:189-229`) — WORKING.
  Filters out malformed mutation maps with `Skipped` (line 218),
  delegates to `BatchOperationsRepository.applyBatch` and reports
  `applied N of M`. Has a minor honesty gap (returns `Ok` even when
  `appliedCount < proposed.size` — partial-batch should arguably be
  `Error`), but operator memo § A.3 marks PARTIAL as still acceptable
  if the message reflects reality, which it does here.
- `AiCompleteActionHandler` / `AiSummarizeActionHandler`
  (`AiActionHandlers.kt:33-104`) — WORKING. Both gate on
  `userPreferencesDataStore.isAiFeaturesEnabledBlocking()` first,
  return `Skipped` for the gate, map HTTP 451 to `Skipped` (correct
  per § A5 of the architecture doc), other HTTP failures to `Error`,
  exceptions to `Error`. Has unit-test coverage in
  `AiActionHandlersTest.kt`.

**Recommendation: STOP-no-work-needed** for these five.

## STOP-conditions evaluated

- **STOP-A1 (reproduction fails).** Cannot run the AVD pair test
  inside this audit, but the static analysis above produces a strict
  superset of the operator's symptom — the missing emit means the
  handler is never invoked, so the fix is correct regardless of which
  exact UI path the operator used. No halt.
- **STOP-B (≥5 silent-failure handlers).** Three (A2/A3/A4) have a
  hardening surface, plus one upstream (A1). All four bundle cleanly
  in one PR. No halt.
- **STOP-C (no error-surface convention).** The convention exists
  already — `ActionResult.Ok / Skipped / Error` (`AutomationActionHandler.kt:36-41`)
  is established and consumed by the engine
  (`AutomationEngine.kt:148-152`, `:264-276`) and the firing-log
  table. No infrastructure scope-creep. No halt.
- **STOP-D (sibling handler systems).** None found — the only
  multibound `Handler` interface in the codebase is
  `AutomationActionHandler` (verified via `grep -rn "@IntoSet\|interface
  .*Handler"`). The notification, widget, and event-bus systems are
  one-off wirings, not a multibound family. No halt.
- **STOP-E (Repository/DAO write bug).** Not applicable — the write
  itself works. The bug is upstream of the write. No halt.
- **STOP-F (Phase 2 LOC > 1500).** Estimate ≤ 250 LOC across one
  repository file + one handler file + the new test files. Well below
  the cap. No halt.

## Premise verification

- **D.1 (bug reproducible on today's build).** Confirmed via static
  analysis: every primary user-facing creation surface routes through
  the no-emit `addTask()`. AVD reproduction is consistent with this.
- **D.2 (PR #1093 subscriber-installer fix in place).** No commit
  matching `#1093` is visible in `git log --all --oneline`; the
  closest is PR #1098 (per-minute cadence). The operator's
  reference number may be from an unrelated branch / chat memory.
  The relevant subscriber path — `AutomationEngine.start()` /
  `bus.events.collect` (`AutomationEngine.kt:59-66`) — is wired
  unconditionally on engine construction; subscriber wiring is not
  in question.
- **D.3 (multi-subscriber-installer pattern holds).** The engine
  uses a single `collect` against the bus, not multiple subscribers.
  Pattern is N/A for this layer; the relevant invariant is "every
  write site that creates an entity must emit the corresponding
  `AutomationEvent`."

## Phase 2 scope

| File | LOC delta (est.) | Why |
|------|------------------|-----|
| `data/repository/TaskRepository.kt` | +2 | Add `automationEventBus.emit(AutomationEvent.TaskCreated(id))` to `addTask()`. |
| `domain/automation/handlers/SimpleActionHandlers.kt` | ~+60 | Tighten `MutateTask` / `MutateHabit` / `MutateMedication` — known-keys constant, error on unknown / mistyped, demote success message to reflect actual writes. |
| `app/src/test/java/.../TaskRepositoryAutomationEmitTest.kt` | ~+80 (new) | Assert `addTask()` emits `TaskCreated`; regression test for the operator's bug. |
| `app/src/test/java/.../handlers/SimpleActionHandlersTest.kt` | ~+200 (new) | Per-handler unit tests for the three mutate handlers — happy path, unknown key, mistyped value, no-op write. |

Aggregate ≈ 340 LOC; under the STOP-F 1500 ceiling.

## Deferred (not auto-filed per memory #30)

- A5: `addSubtask()` / `reorderSubtasks()` / `updateTaskOrder()` no-
  emit — re-trigger when an automation use-case demands subtask
  events.
- `ApplyBatchActionHandler` partial-batch honesty — re-trigger if
  partial-batch failures show up in user-visible reports.
- `MutateHabit` / `MutateMedication` field coverage expansion (name,
  streakCount, dose, etc.) — re-trigger when a template author asks
  for it; out of scope for "fix silent failure."

## Open questions for operator

None. Scope is operator-locked; STOPs are clean; convention exists.
Proceeding straight into Phase 2 per audit-first default (no
checkpoint gate).
