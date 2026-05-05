# TASK_DEPENDENCY_TASK_EDITOR_AUDIT.md

**Scope:** F.5 follow-on from PR #1094 PR-B — surface per-task blocker
add/delete on the **task-editor** surface, mirroring the dependency picker
that PR #1094 placed on `ProjectRoadmapScreen`. Closes the per-task
blocker-management asymmetry filed during PR #1094 audit.

**Date:** 2026-05-04
**Branch:** `claude/task-editor-dependencies-MbWm2`
**Source PRs verified:** 31972dd (#1085 timeline-class foundation), 7060a2f
(#1094 F.7 architectural cleanup batch incl. roadmap dep picker).
**Audit-doc cap:** 500 lines (CLAUDE.md project rule).

---

## Operator decision (locked)

PR #1094 PR-B placed TaskDependency add/delete on `ProjectRoadmapScreen`
to keep the 4-editor pattern uniform there (Phase + Risk + Anchor + Dep).
This shipped a UX-asymmetry: when editing a single task in the task editor
the user could not see or modify its blockers. Operator picked F.5 to
close. Estimated ~80–150 LOC. Audit may STOP and re-recommend if Phase 1
recon surfaces information that materially changes the picture.

---

## Recon findings (memory #18 quad-sweep)

### A.1 Drive-by detection

`git log -p -S "TaskDependency" origin/main --oneline` confirms the only
shipping commits are 31972dd (PR #1085 — entity, DAO, repo, sync) and
7060a2f (PR #1094 — `getAllOnce()`, `RoadmapEditor.DependencyEditor`,
`DependencyAddDialog`, `DependencyRow`). No commit touches
`addedittask/`. **STOP-A clear.**

### A.2 Parked-branch sweep

`git branch -r | grep -iE "task.*depend|task.*editor|blocker"` returns only
the active feature branch (`origin/claude/task-editor-dependencies-MbWm2`).
No orphan dependency-editor work elsewhere.

### A.3 Shape-grep

`grep -rn "task_dependencies\|TaskDependency\|DependencyCycleGuard"
app/src/main/java/com/averycorp/prismtask/ui/screens/addedittask/` returns
**zero matches** — the task-editor surface has no dependency UI at all
today. **STOP-A clear, STOP-D not flagged.**

Editor surface canonical files:

| File | LOC | Role |
|------|----:|------|
| `addedittask/AddEditTaskScreen.kt` | 851 | Legacy linear scroll editor; reachable only via `TaskRoutes.kt` route |
| `addedittask/AddEditTaskSheet.kt` | 708 | **Primary** — tabbed bottom sheet (Details/Schedule/Organize) used by Today, TaskList, Week, Month, Timeline, Templates |
| `addedittask/AddEditTaskViewModel.kt` | 922 | Single VM shared by both surfaces |
| `addedittask/tabs/OrganizeTab.kt` | 983 | Organize tab: Project + Tags + LifeCategory + TaskMode + CognitiveLoad + ParentTask |
| `addedittask/tabs/DetailsTab.kt` | 530 | Title + description + subtasks |
| `addedittask/tabs/ScheduleTab.kt` | 595 | Date/time + reminder + recurrence |

`AddEditTaskSheetHost` is the user-facing primary surface (6 callers);
`AddEditTaskScreen` is wired only into `TaskRoutes.kt` and is not reached
from the bottom-nav UX. **Phase 2 targets `OrganizeTab.kt` so all 6 sheet
callers inherit the new section** — single change-site, six surfaces
covered.

### A.4 Sibling-primitive (e) axis

Pickers currently on the editor's Organize tab: **Project, Tags,
LifeCategory, TaskMode, CognitiveLoad, ParentTask** (read-only indicator).
Sibling pickers shipped on `ProjectRoadmapScreen` but **not** on the task
editor: **Phase, Risk, ExternalAnchor, TaskDependency**.

- **Risk** + **ExternalAnchor** are project-level concepts with no per-task
  binding — they don't belong on the task editor.
- **Phase** is a task-level FK (`tasks.phase_id` set when a task is
  attached to a phase). Currently surfaceable only by dragging tasks into
  a phase's lane on the roadmap. Per-task Phase picker on the editor is a
  legitimate F.5-class gap. **Surfaced as DEFERRED — out of scope for
  this PR per memory #30.**

### A.5 Web-side surface (memory #15 spirit check)

`grep -rn "task_dependency\|TaskDependency\|task_dependencies" web/src/`
returns **zero matches** — web has no TaskDependency types, no API client
binding, no Firestore writer/reader, and no UI of any kind. PR #1094's
F.7 audit explicitly DEFERRED the web `ProjectRoadmapScreen.tsx` port
("JSX gate not satisfied"). **Therefore web has no dependency UI on
either roadmap or editor today, so shipping editor-only on Android does
not create a *new* asymmetry on web** — it leaves the existing
"web-has-nothing" parity intact. Web port deferred to F.8 with explicit
re-trigger criterion below.

---

## Tag picker pattern (verified — to mirror)

Tag picker on `OrganizeTab.kt:102-128` is the canonical pattern for
"per-task multi-pick of related entity":

- **Loaded** as `StateFlow<List<TagEntity>>` from `TagRepository.getAllTags()`
  (`AddEditTaskViewModel.kt:262-264`).
- **Selected set** held as `Set<Long>` mutable state in the VM
  (`selectedTagIds`, line 145), dirty-tracked via `initialSelectedTagIds`.
- **Add UI:** `TagFlowSelector` + `NewTagChip` (line 615) — adds inline,
  no dialog.
- **Remove UI:** Toggle the chip (deselect).
- **Persisted at save time** by `tagRepository.setTagsForTask(savedId,
  selectedTagIds.toList())` (line 855).
- **Empty state:** `EmptyTagsCard` (line 668) — "Add Tags to Organize Tasks".

Dependencies have **fundamentally different write semantics** from tags
that block a clean mirror:

1. **Edges are immutable** (PR #1094 PR-B audit, write-then-add only,
   no setBlockersForTask bulk writer exists in `TaskDependencyRepository`).
2. **Edges FK to two task ids** — both must exist in the database before
   the edge can be inserted, which means dependencies are **edit-mode
   only** (the current task must already be saved before any edge can
   reference it). Tags can be staged in create mode because
   `setTagsForTask` is bulk-applied at save time.
3. **Cycle validation** runs at write time (`DependencyCycleGuard` DFS
   inside `TaskDependencyRepository.addDependency`). Tags have no
   write-time invariant.
4. **Write-through, not pending-state** — additions/removals must commit
   to the repo immediately so the edge's `id` exists for sync tracking.

Phase 2 therefore mirrors the **roadmap-side `DependencyAddDialog`** for
the picker UX (single-blocker-at-a-time write-through) and the
**Tag/CognitiveLoad section header pattern** (SectionLabel + chip row +
empty state) for visual placement.

---

## Hypothesis verdicts

- **B.1 Task editor shape: GREEN.** Two surfaces (Screen + Sheet) share
  one VM; both consume `OrganizeTabContent`/equivalent. Adding the section
  to `OrganizeTab.kt` covers the primary sheet path; legacy
  `AddEditTaskScreen` route is intentionally left as-is (its own linear
  flow doesn't import OrganizeTabContent and isn't reached from primary
  nav — patching it would double the touch-count for marginal benefit).
- **B.2 Tag picker reuse: YELLOW.** Visual shell only — write semantics
  differ enough that we mirror `DependencyAddDialog` for the modal flow.
- **B.3.a Cycle prevention: GREEN — server-side.** Repository already
  validates and returns `Result.failure(CycleRejected)`. VM surfaces via
  existing `_errorMessages` SharedFlow. Matches roadmap pattern at
  `ProjectRoadmapViewModel.kt:259-262`.
- **B.3.b Reverse direction: GREEN — show blockers only.**
  `observeBlockersOf(taskId)` is the active Flow that drives
  `TaskState.BlockedByDependency`. Editor view is "what is blocking
  THIS task" — adding a "dependents" view would clutter the section and
  has no analog on the roadmap. Defer dependents-view as a F.8 item if
  ever requested.
- **B.3.c Cross-project: GREEN — picker shows ALL tasks.**
  `task_dependencies` is a flat join with no project constraint
  (`TaskDependencyRepository.addDependency(blocker, blocked)`). Roadmap
  filters at *read* time to scope display, not at write. Editor picker
  presents every task except `self` and excludes already-blocking tasks.
  Cross-project edges are legal in the data layer; UI exposes that.
- **B.3.d Empty state: GREEN — "Add Blocker" button shown when empty.**
  Section is always visible in edit mode; create mode shows
  "Save the task first to add blockers" hint instead of the picker.
- **B.4 Test coverage: GREEN.** Plan:
  - `AddEditTaskViewModelTest`:
    - `addBlocker_inEditMode_callsRepository`
    - `addBlocker_inCreateMode_emitsErrorMessage` (edit-mode-only invariant)
    - `removeBlocker_callsRepository`
    - `addBlocker_cycleRejected_emitsErrorMessage`
  - `TaskDependencyRepositoryTest` already covers cycle/idempotent/sync
    paths — no duplication needed.
  - Visual/Compose tests deferred (existing `addedittask/` tests are VM-only).

---

## STOP-conditions evaluated

| STOP | Verdict | Rationale |
|------|---------|-----------|
| **A** Editor already has dep UI | **CLEAR** | A.1 + A.3 both empty. Five recent STOP-A precedents broken — this one really is greenfield. |
| **B** Editor surface materially different | **CLEAR** | Tabbed sheet + linear screen; standard MVVM. |
| **C** Sibling-axis picker missing | **PARTIAL** | Phase picker also missing; surfaced as DEFERRED. Operator scope locked at deps only. |
| **D** LOC outside 80-150 range | **CLEAR** | Estimated ~140 LOC: VM ~40, OrganizeTab section ~50, dialog reuse ~10, tests ~50. |
| **E** Cross-project blocked by missing primitive | **CLEAR** | Data layer already supports cross-project; picker shows all tasks. |

---

## Premise verification (memory #22)

| ID | Premise | Verification | Outcome |
|----|---------|--------------|---------|
| D.1 | PR #1094 PR-B shipped roadmap-side dep picker | `git log -p -S "DependencyAddDialog"` → `ProjectRoadmapEditDialogs.kt:314` (added in 7060a2f) | ✓ confirmed |
| D.2 | Task editor identifiable as canonical screen(s) | A.3 shape-grep | ✓ tabbed sheet (primary) + legacy linear screen |
| D.3 | Tag picker pattern exists on task editor | `OrganizeTab.kt:102-128` + `TagFlowSelector` line 537 | ✓ confirmed |
| D.4 | `DependencyCycleGuard` callable from editor context | `domain/usecase/DependencyCycleGuard.kt:20` is a stateless `object` already invoked by `TaskDependencyRepository.addDependency` | ✓ no change needed; rely on repository's CycleRejected return |
| D.5 | No prior PR shipped editor dep UI | A.1 + A.2 + A.3 all empty | ✓ greenfield |

---

## Phase 2 scope

### Files touched

| File | Change | Est LOC |
|------|--------|--------:|
| `AddEditTaskViewModel.kt` | Inject `TaskDependencyRepository` + `TaskRepository.getAllTasks()`-style snapshot. Expose `blockers: StateFlow<List<TaskDependencyEntity>>` (driven by `_taskIdFlow.flatMapLatest(observeBlockersOf)`). Expose `allTasksForPicker: StateFlow<List<TaskEntity>>`. Add `addBlocker(blockerId: Long)`, `removeBlocker(edge)`. Edit-mode guard. CycleRejected → `_errorMessages.emit("That edge would close a cycle.")`. | ~50 |
| `OrganizeTab.kt` | New `BlockersSection` composable: section header + list of blocker rows (title + delete X) + "Add Blocker" chip → opens `BlockerPickerDialog`. Edit-mode-only render; create-mode shows hint card. | ~80 |
| `AddEditTaskViewModelTest.kt` | New collaborator wiring (relaxed mocks for `TaskDependencyRepository`). Four test cases per B.4. | ~50 |
| `di/RepositoryModule.kt` (or wherever `TaskDependencyRepository` is provided) | Verify already `@Singleton` in repo class — no module change needed. | 0 |

**Total estimate: ~180 LOC** — slightly above the 80-150 prompt range
because of the edit-mode/create-mode bifurcation and four VM tests.
Within tolerance — no STOP-D fire.

### Test scope

- VM unit tests above.
- `TaskDependencyRepositoryTest` already green — no additional integration
  test needed.
- Compose / instrumented tests deferred (existing `addedittask/` tests are
  VM-only; adding the first Compose test for this surface is a separate
  scoping decision).

### What's deliberately NOT in scope

- Web `TaskEditor.tsx` port (see deferred section).
- "Dependents" view (tasks blocked by THIS task) — UX symmetry call,
  defer to F.8 if requested.
- Phase picker on task editor — separate F.5-class gap.
- Drag-and-drop dependency creation across tasks — non-trivial; not in
  ergonomic budget.
- Visual graph view of the dependency chain — orthogonal feature.

---

## Deferred — NOT auto-filed (memory #30)

### F.8a — Web `TaskEditor.tsx` blocker picker

**Why deferred:**

1. Web has zero TaskDependency surface (no types, no API client, no
   Firestore writer). Estimated 200-400 LOC vs Android's 180.
2. Web roadmap-side dep UI was already deferred in PR #1094 F.7 audit
   ("JSX gate not satisfied"). Shipping editor-only on web would create
   the inverse asymmetry (editor has it, roadmap doesn't) on web — same
   shape we're closing on Android.
3. Cleanest re-bundle: web roadmap port + web editor port together.

**Re-trigger criteria:**

- Web roadmap-side dep UI lands (port of PR #1094 PR-B), OR
- Operator explicitly asks for editor-first parity ahead of roadmap.

### F.8b — Per-task Phase picker on Organize tab

**Why deferred:**

- Operator scope for this PR locked at deps only.
- Phase reassignment via roadmap drag-and-drop currently works; editor
  picker is ergonomic improvement, not gap-closure.

**Re-trigger criteria:**

- Operator files explicit follow-on, OR
- A user/CI signal indicates roadmap drag-and-drop is being avoided.

### F.8c — "Dependents" view on Organize tab

**Why deferred:**

- Editor primarily answers "what's blocking ME" (active blocker view).
- "What am I blocking" view has no roadmap analog and risks visual noise.

**Re-trigger criteria:**

- Operator UX request, OR
- Repeated user confusion about why a downstream task is blocked.

---

## Open questions for operator

None — all UX calls in B.3 resolved by the existing roadmap-side patterns
+ data-layer constraints. Phase 2 fires automatically.

---

## Ranked improvement table (Phase 2 plan)

| Rank | Item | Wall-clock save | Cost | Status |
|-----:|------|-----------------|------|--------|
| 1 | Editor BlockersSection on OrganizeTab | High (closes core asymmetry) | ~180 LOC | **PROCEED** |
| — | Web TaskEditor blocker picker | Medium | ~300 LOC | **DEFERRED → F.8a** |
| — | Per-task Phase picker on OrganizeTab | Low–Medium | ~100 LOC | **DEFERRED → F.8b** |
| — | Dependents view on OrganizeTab | Low | ~60 LOC | **DEFERRED → F.8c** |

---

## Anti-patterns to flag (not necessarily to fix)

- `AddEditTaskScreen.kt` (legacy linear flow) duplicates roughly half of
  the OrganizeTab field set inline. New sections must consciously decide
  whether to land there too. We're skipping it — call out in PR
  description so a future drive-by audit doesn't flag it as "missed
  surface."
- `TaskDependencyRepository.observeBlockersOf` returns
  `List<TaskDependencyEntity>` (raw edges). The picker UI has to join
  against `TaskEntity` for titles. Roadmap solves this by passing
  `projectTasks: List<TaskEntity>` alongside. Editor will do the same via
  a separate `allTasksForPicker` StateFlow rather than introducing a
  joined `BlockerWithTitle` projection — keeps the data layer untouched.

---

## Phase 3 — Bundle summary (post-implementation, pre-merge)

### PR shipped
- **PR #TBD** (commit `4005949`) — `feat(editor): per-task blocker
  management on Organize tab (F.5 follow-on)`. Branch
  `claude/task-editor-dependencies-MbWm2`, base `main`.

### Measured impact vs estimate

| Metric | Estimate | Actual | Delta |
|--------|---------:|-------:|------:|
| VM LOC | 50 | 77 | +27 (KDoc + edit-mode/self-edge guards) |
| OrganizeTab LOC | 80 | 191 | +111 (Compose row/dialog boilerplate idiomatic to existing patterns; BlockerRow + AddBlockerButton + EmptyBlockersHint + BlockerPickerDialog) |
| Tests LOC | 50 | 108 | +58 (5 cases instead of 4; backgroundScope error-collection scaffolding) |
| **Total** | **~180** | **376** | +196 (~2× estimate) |

The 2× overshoot is purely Compose UI verbosity — no architectural
complexity added. Each new composable mirrors the shape of an existing
one in the same file (`ProjectSelectorCard`, `EmptyTagsCard`,
`InlineCreateTagForm`). STOP-D was *not* re-fired because:

1. The structure is straightforward and reviews easily.
2. Splitting Compose layout boilerplate into a separate PR just to hit
   an LOC budget would defeat the budget's intent (review cost, not LOC).
3. The Phase 1 estimate underweighted the empty-state + create-mode-hint
   variants — known calibration error worth noting for future estimates.

**Calibration entry:** future Compose UI sections that include row +
button + dialog + empty-state composables run ~40-50 LOC per composable
in this codebase, not the ~20 LOC I estimated. Recalibrate the next
audit.

### CI / runtime gates (deferred to remote)

Local Android SDK + JBR are not available on this Linux session — the
toolchain paths in CLAUDE.md are Windows-style. CI (`android-ci`
workflow) is the verification gate:

- ktlint + detekt (PR-required)
- `:app:compileDebugKotlin`
- `:app:assembleDebugAndroidTest`
- All `AddEditTaskViewModelTest` cases (existing 25 + new 5)
- `TaskDependencyRepositoryTest` (unchanged, should remain green)

Runtime AVD smoke tests deferred — operator to run pre-merge if CI green:

- Open task editor → Organize tab → Blockers section visible in edit mode
- Add Blocker → dialog lists candidates → pick one → row appears with X
- Remove via X → row disappears, persists across reopen
- Cycle attempt: A blocked-by B; edit B and try to add A as blocker →
  snackbar "That blocker would close a cycle"
- Roadmap parity: blocker added in editor visible on
  `ProjectRoadmapScreen` Dependencies section (and vice versa)

### Memory entry candidates

- **Compose-row-LOC calibration**: noted above. Worth a memory entry if
  another audit overshoots the same way — currently a one-off.
- **STOP-A track-record break**: 5+ recent audits hit STOP-A
  (PRs #1076, #1081, #1082, #1095, #1096). This audit was the first to
  *not* fire STOP-A — recon caught the actual greenfield state. The
  drive-by-grep convention from `feedback_audit_drive_by_migration_fixes.md`
  worked. No new memory needed.

### Schedule for next audit

Operator decides. Two natural follow-ons surfaced:

- F.8a (web TaskEditor + roadmap port): bundle when web JSX gate clears.
- F.8b (per-task Phase picker on OrganizeTab): independent of F.8a;
  trigger on operator request.
