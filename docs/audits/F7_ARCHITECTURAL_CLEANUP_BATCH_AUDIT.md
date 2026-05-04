# F.7 Architectural Cleanup Batch — Phase 1 Audit

**Scope:** 6-item bundle of architectural follow-ons spawned by the May 3 mega-PR ship window
(PR #1061 task_mode → PR #1084 cognitive_load → PR #1085 timeline-class foundation).
Closes asymmetry items, a pre-existing sync bug, cross-platform-parity ports, and a
cleanup migration. All items P2 (no user-blocker).

**Date:** 2026-05-04
**Branch:** `claude/f7-architectural-cleanup-5F3qH`
**Source PRs verified:** 77e1291 (#1061), 82d112e (#1084), 31972dd (#1085) — all shipped May 3.
**Audit-doc cap:** 500 lines (CLAUDE.md project rule overrides prompt's 800).

---

## Item index

| # | Item | Verdict | LOC est | Sequence |
|---|------|---------|---------|----------|
| 4 | taskMode SyncMapper gap (+ cognitive_load co-fix) | **PROCEED** (scope expanded) | ~80–120 | PR-D (1st) |
| 1 | TaskMode web UI editor | **PROCEED** | ~50–100 | PR-A (2nd) |
| 3 | CloudIdOrphanHealer extension | **STOP-no-work-needed** | 0 | — |
| 5 | DayBoundary cleanup | **DEFER** (premise narrower than feasible) | n/a | — |
| 2 | Timeline-class editor UI | **PROCEED** | ~300–500 | PR-B (3rd) |
| 6 | Web ProjectRoadmapScreen.tsx port | **DEFERRED** (JSX gate not satisfied) | n/a | — |

**Net Phase 2:** 3 PRs (PR-D → PR-A → PR-B), down from the 6 in the prompt.
Items 3 and 5 close cleanly without work; items 6 stays in F.7 awaiting JSX upload.

---

## Item 4 — taskMode SyncMapper gap (RED — scope expands)

### Premise verification

Original premise: PR #1061 added `task_mode` to `tasks` (v70 → v71) but never wired it into
SyncMapper. Confirmed at `app/src/main/java/com/averycorp/prismtask/data/local/entity/TaskEntity.kt:112`.
SyncMapper `taskToMap` / `mapToTask` (lines 42–131 of
`app/src/main/java/com/averycorp/prismtask/data/remote/mapper/SyncMapper.kt`) lists
`lifeCategory` but **omits taskMode entirely**.

### Findings — broader gap

**Quad sweep (e) sibling-primitive surfaced a wider bug.** Same omission across **all 4 sync
surfaces** for **both** orthogonal-dimension columns shipped this window:

| Sync surface | lifeCategory | taskMode | cognitiveLoad |
|---|---|---|---|
| `data/remote/mapper/SyncMapper.kt` (Firestore direct) | ✓ L73, L119 | **✗** | **✗** |
| `data/remote/sync/BackendSyncMappers.kt` (HTTP→JSON) | ✓ L43 | **✗** | **✗** |
| `data/remote/sync/BackendSyncService.kt` (JSON→Task parse) | ✓ L445 | **✗** | **✗** |
| `data/remote/api/ApiModels.kt` (DTOs) | ✓ L317, L428 | **✗** | **✗** |

**Origin:**
- `task_mode` shipped in PR #1061 (commit `77e1291`, May 3).
- `cognitive_load` shipped in PR #1084 (commit `82d112e`, May 3, ~hours later).
- Both PRs missed all 4 sync surfaces. PR #1084 inherited the same blind spot.
- Web side (`web/src/api/firestore/tasks.ts`) handles **both** fields end-to-end (lines 75, 76,
  200–201, 205–206, 269–270). So Android writes never reach Firestore, but web writes do —
  which means **Android-side rows can be silently overwritten by web-side null on next pull**.

### Risk classification: RED (data loss path)

This is a sync-roundtrip bug, not just an asymmetry. Sequence that causes silent data loss:
1. Android user picks Work for a task → row written locally with `task_mode = "WORK"`.
2. Sync push runs → SyncMapper.taskToMap omits `taskMode` → Firestore doc has no taskMode field.
3. Web user opens same task → web Firestore mapper reads `taskMode = null` → web rewrites task.
4. Sync pull on Android → SyncMapper.mapToTask reads `taskMode` (absent) → entity built with
   `taskMode = null` → **user's Work tag silently lost**.

Same hazard for cognitive_load. Cross-device users on both platforms are exposed today.

### Recommendation: PROCEED — bundle co-fix into PR-D

PR-D scope expands from "fix taskMode in SyncMapper only" to "fix taskMode + cognitiveLoad
across all 4 sync surfaces":

- `SyncMapper.kt` — add 2 keys to `taskToMap`, 2 reads to `mapToTask`.
- `BackendSyncMappers.kt` — add 2 `if (... != null) addProperty(...)` lines.
- `BackendSyncService.kt` — add 2 `data.optString(...)` reads.
- `ApiModels.kt` — add 2 `@SerializedName(...)` fields to each of 2 DTOs (4 total fields).
- `app/src/test/java/com/averycorp/prismtask/domain/SyncMapperTest.kt` — extend round-trip
  cases for both fields. Pattern already exists for lifeCategory.

LOC: ~80–120 (vs prompt's ~20–50). Still smallest PR in the bundle. **Ships first** — its fix
shape stabilizes before any other PR touches sync surfaces.

---

## Item 1 — TaskMode web UI editor (GREEN)

### Premise verification

Confirmed: `web/src/features/tasks/TaskEditor.tsx` has `<select>` editors for **LifeCategory**
and **CognitiveLoad** (the latter shipped in PR #1084 web port) but **none for TaskMode**.
Type definition exists (`web/src/types/task.ts:129`: `export type TaskMode = 'WORK' | 'PLAY' |
'RELAX' | 'UNCATEGORIZED'`). Firestore mapper exists (`web/src/api/firestore/tasks.ts:200–201,
269`). Only the editor surface in TaskEditor.tsx is missing.

### Findings

Pattern to mirror is **CognitiveLoad** verbatim (canonical sibling, freshest):
```tsx
const COGNITIVE_LOAD_OPTIONS: { value: CognitiveLoad | ''; label: string }[] = [
  { value: '', label: 'Uncategorized' },
  { value: 'EASY', label: 'Easy' },
  ...
];
const [cognitiveLoad, setCognitiveLoad] = useState<CognitiveLoad | ''>('');
// + setCognitiveLoad in reset block + setCognitiveLoad in load-from-task block
```
Replace EASY/MEDIUM/HARD with WORK/PLAY/RELAX. ~50 LOC.

### Risk classification: GREEN

Pure additive. Pattern proven 1 day ago. No semantic surprises.

### Recommendation: PROCEED as PR-A

**Depends on PR-D landing first** so the round-trip works end-to-end at merge time. (Editor
without sync wiring is a half-finished implementation.) Tests: `TaskEditor.test.tsx` already
has CognitiveLoad option-list assertion to copy.

---

## Item 3 — CloudIdOrphanHealer extension (GREEN — already done)

### Premise verification — PREMISE WRONG

The prompt asserts "PR #1089 deferred orphan-healer extension for the 4 timeline-class
entity types." Reading `app/src/main/java/com/averycorp/prismtask/data/remote/CloudIdOrphanHealer.kt`
**lines 173–177** (constructor) and **lines 284–303** (`healOrphans` body):

```kotlin
// PrismTask-timeline-class scope, PR-1.
private val projectPhaseDao: ProjectPhaseDao,
private val projectRiskDao: ProjectRiskDao,
private val taskDependencyDao: TaskDependencyDao,
private val externalAnchorDao: ExternalAnchorDao,
...
healFamily("project_phases", "project_phase", fetcher) { ... }
healFamily("project_risks", "project_risk", fetcher) { ... }
healFamily("task_dependencies", "task_dependency", fetcher) { ... }
healFamily("external_anchors", "external_anchor", fetcher) { ... }
```

All 4 entity types **are already wired** into the healer. The "PrismTask-timeline-class scope,
PR-1" comment indicates this landed in PR #1085 (`31972dd`, May 3) — the timeline-class
foundation merge — not deferred.

### Findings

Per memory #22 (claims verified via git log): the prompt's deferral note was based on
PR #1089's timeline-comment description; the actual PR #1085 commit shipped both the
entities and the orphan-healer wiring in a single ship.

### Risk classification: GREEN

No work to do. Extension is complete and in production.

### Recommendation: STOP — no work needed

Close item 3 in F.7 immediately. No PR. Audit doc captures the finding for the F.7 close
trail.

---

## Item 5 — DayBoundary cleanup (YELLOW — defer, premise too narrow)

### Premise verification

Confirmed both files exist:
- `app/src/main/java/com/averycorp/prismtask/util/DayBoundary.kt` — **14 importers**
- `app/src/main/java/com/averycorp/prismtask/core/time/DayBoundary.kt` — **3 importers**

KDoc in core/time/DayBoundary.kt explicitly says: *"The legacy millis-based utility in
[com.averycorp.prismtask.util.DayBoundary] is kept for back-compat with hour-only callers
and will be migrated incrementally."*

### Findings — APIs are NOT semantically equivalent

**util.DayBoundary (millis-based, minute-aware):**
- `startOfCurrentDay(dayStartHour, now, dayStartMinute): Long`
- `endOfCurrentDay(dayStartHour, now, dayStartMinute): Long`
- `calendarMidnightOfCurrentDay(dayStartHour, now): Long`
- `calendarMidnightOfNextDay(dayStartHour, now): Long`
- `normalizeToDayStart(timestamp, dayStartHour): Long`
- `nextBoundary(dayStartHour, now, dayStartMinute): Long`
- `currentLocalDate(...) → LocalDate`, `currentLocalDateString(...) → String`
- `DAY_MILLIS` constant (used for arithmetic at call sites — see TaskListViewModel.kt:960–962:
  `startOfTomorrow = startOfToday + DayBoundary.DAY_MILLIS`)

**core.time.DayBoundary (Instant-based, no minute parameter):**
- `calendarDate(instant, zone): LocalDate`
- `logicalDate(instant, sodHour, sodMinute, zone): LocalDate`
- `logicalDayStart(instant, sodHour, sodMinute, zone): Instant`
- `nextLogicalDayStart(instant, sodHour, sodMinute, zone): Instant`
- `resolveAmbiguousTime(...)` — NLP-specific, no util equivalent

**The two APIs are not 1:1.** util's call sites do millis arithmetic against AlarmManager
schedules, DataStore stamps, widget tick budgets, and Room range queries. core.time has no
millis-returning function and no `DAY_MILLIS` analog.

### Migration shape options

**Option A — "absorb millis into core.time":** Add millis-returning fns to core.time. Drift
the canonical API to absorb the legacy. Inverts the cleanup intent.

**Option B — "Instant-ize all 14 callers":** Convert each call site from `Long` to `Instant`.
Touches AlarmManager scheduling (`reminderOffset` is Long), widget refresh budgets, DataStore
stamps. Estimated **~80+ touch sites** across schedulers, repositories, and ViewModels. Risky.

**Option C — "deprecate-with-shim, migrate incrementally":** Mark util.DayBoundary
`@Deprecated`, do nothing else. Leaves the same drift the KDoc already describes. No win.

### Risk classification: YELLOW

No data-loss risk; codebase compiles and works. The "drift" is a tidiness concern that
requires an API-design decision, not a mechanical migration. Within audit-first-mega's
defer-minimization spirit, the **right call is to defer with a concrete reframe**, not
ship a half-migration.

### Recommendation: DEFER

Reframe for the next audit window: **"SoD canonical API design — pick Option A vs B and ship
in a single coordinated sweep."** The current bundle's per-PR LOC budget cannot absorb
Option B safely; Options A and C are anti-cleanup. Defer is the correct outcome here, not
a failure.

---

## Item 2 — Timeline-class editor UI (GREEN — large but bounded)

### Premise verification

Confirmed: `app/src/main/java/com/averycorp/prismtask/ui/screens/projects/roadmap/ProjectRoadmapScreen.kt`
header docstring (lines 51–63): *"Read-only roadmap surface (audit § P10 option (b)…"*. Body
of the screen renders state via `Card` + `LazyColumn` + 3 row composables (`PhaseCard`,
`RiskRow`, `AnchorRow`); **no edit affordances** — no FABs, no edit menus, no delete callbacks.

`ProjectRoadmapViewModel.kt` exposes 0 add/update/delete/create functions (grep returned 0
matches).

### Findings — repos already have CRUD

Repositories exist for all 4 entity types:
- `ExternalAnchorRepository.kt`, `TaskDependencyRepository.kt` — confirmed.
- `ProjectPhaseRepository.kt` and `ProjectRiskRepository.kt` — not separate repos; CRUD lives
  on the parent `ProjectRepository` (via `MilestoneDao` / `ProjectPhaseDao` / `ProjectRiskDao`
  injected). Confirm during PR-B by reading ProjectRepository.

Sibling editor pattern: `app/src/main/java/com/averycorp/prismtask/ui/screens/automation/AutomationRuleEditScreen.kt`.
Single-screen edit with fields, validation, save/delete actions, theme-consistent. **This is
the canonical pattern to mirror** for the 4 entity types.

### Scope shape (locked)

4 editor surfaces, two-tier choice:
- **Phase + Risk + Dependency** — simple shape, dialog (BottomSheet/AlertDialog) suffices.
- **External Anchor** — sealed-variant (CalendarDeadline / NumericThreshold / BooleanGate)
  needs variant-aware editor. Heavier; consider a dedicated screen rather than dialog.

ViewModel: extend ProjectRoadmapViewModel with `addPhase / updatePhase / deletePhase` (and
analogs for the other 3). Sealed editor state for currently-open editor (mirrors how
TaskListViewModel manages its bottom-sheet states).

Cycle-detection for TaskDependency: client-side warning only on direct self-reference
(`fromTaskId == toTaskId`). Full graph cycle-detection deferred to a follow-on (out of scope
to avoid LOC blowout).

### Risk classification: GREEN

Largest item by LOC but lowest semantic risk: pure additive UI on top of repos that already
work. Compose previews + screen tests give fast feedback.

### Recommendation: PROCEED as PR-B (last)

LOC: ~300–500. Test surface: 4 new screen/dialog tests + ViewModel test additions.
**Ships last** so PR-D + PR-A's smaller, more architecturally consequential changes land
first and stabilize the sync surface area before bigger UI work hits.

---

## Item 6 — Web ProjectRoadmapScreen.tsx port (DEFERRED — JSX gate not satisfied)

### Pre-flight verification

Per the prompt's hard pre-condition: *"PrismTaskTimeline__1_.jsx (or equivalent) uploaded
to repo before Phase 1 audit runs."* Result of `find . -name "PrismTaskTimeline*.jsx"`:
**zero matches**. Only file matching `*Timeline*.jsx` repo-wide is none; the existing
`web/src/features/calendar/TimelineScreen.tsx` is the daily time-block view, unrelated.

### Recommendation: DEFER

HARD STOP fires per the prompt's gate. Item 6 stays in F.7 with the existing un-block
trigger ("operator uploads PrismTaskTimeline*.jsx"). When upload happens, audit Part F.2 +
F.3 from the original mega-prompt re-runs to pick the JSX-port vs build-from-scratch path.

---

## Cross-cutting findings

**G.1 — Counter-shape stability (items 4 + 5 both touch sync-adjacent code)**
Item 5 deferred → no SyncMapper conflict possible. PR-D owns the SyncMapper edit cleanly.

**G.2 — UI pattern lock (items 1 + 2 + 6)**
Item 6 deferred → only items 1 (web `<select>`) and 2 (Compose Dialog/Screen) remain. Different
platforms, no library conflict. Pattern decisions are independent.

**G.3 — Test surface**
- PR-D: extend SyncMapperTest with 2 round-trip cases (taskMode + cognitiveLoad). Pattern
  exists for lifeCategory at `app/src/test/java/com/averycorp/prismtask/domain/SyncMapperTest.kt`
  (17 `@Test` methods, lines 16–209).
- PR-A: extend `TaskEditor.test.tsx` with TaskMode option-list assertion. Mirror CognitiveLoad
  test added in PR #1084.
- PR-B: 4 new editor tests + ProjectRoadmapViewModel test additions.

**G.4 — Memory #22 verification (shipped vs claimed)**
Item 3 (CloudIdOrphanHealer) verified via direct file read + git log: shipped via PR #1085.
Prompt claim of "deferred" was wrong. Item 4's surprise finding (cognitive_load same gap as
taskMode) verified via cross-surface grep + git log of PR #1084 commit.

---

## PR sequencing — final plan

| Order | PR | Item | LOC | Why this position |
|---|---|---|---|---|
| 1 | PR-D | 4 (taskMode + cognitiveLoad sync) | ~80–120 | Smallest, most concrete, real bug, fan-out anchor. Sync surface stabilizes first. |
| 2 | PR-A | 1 (TaskMode web editor) | ~50–100 | Depends on PR-D for end-to-end correctness. Tiny + additive. |
| 3 | PR-B | 2 (Timeline editor UI) | ~300–500 | Largest, last so prior PRs stabilize main first. |

Items 3, 5, 6 do not ship in this bundle (verdicts above).

---

## Ranked improvements (wall-clock-savings ÷ implementation-cost)

| Rank | Item | Savings | Cost | Notes |
|---|---|---|---|---|
| 1 | **Item 4** (sync gap fix) | High — closes silent data-loss path for 2 fields × 4 surfaces | Low (~80–120 LOC, mechanical) | Real bug. Ship first. |
| 2 | **Item 1** (web TaskMode editor) | Medium — closes orthogonal-dimension UI asymmetry | Low (~50–100 LOC, copy CognitiveLoad pattern) | Web Firestore mapper already in place. |
| 3 | **Item 2** (timeline editor UI) | Medium — unlocks roadmap content authoring on Android | Medium-High (~300–500 LOC, 4 editors + ViewModel) | Largest. Bounded by sealed-variant ExternalAnchor scope. |

---

## Anti-patterns (flag, do not fix here)

- **DayBoundary dual-API drift (item 5)** — flagged in audit, deferred to a future "SoD
  canonical API design" pass. Don't ship a half-migration.
- **Sync-surface fan-out** (4 surfaces for every new task column) — every new orthogonal
  dimension carries a 4-file tax. Worth capturing as a checklist in `docs/SYNC_CONVENTIONS.md`
  in a future docs PR. Not in this bundle's scope.
- **Backend HTTP sync vs Firestore direct sync overlap** — two parallel sync pathways with
  near-identical mapper shape. Worth an architectural review in a future audit; not the
  cleanup-batch scope.

---

## Memory candidate

**Convention**: *"Every new orthogonal task-dimension column ships its sync wiring (4 surfaces:
SyncMapper, BackendSyncMappers, BackendSyncService, ApiModels) + its web TaskEditor surface
in the same PR as the migration."* — verified by item 4's broader-than-expected gap (PR #1061
+ PR #1084 both repeated the same omission across 4 surfaces). Memory currently 29/30 — file
only if Phase 4 confirms this is durable convention, not one-off.

---

## STOP-condition log

- ✅ **Item 6 JSX gate fired** → item 6 deferred, bundle reduced from 6 PRs to 5.
- ✅ **Item 4 broader sync gap fired** → cognitive_load co-fix bundled into PR-D, scope
  expanded but stayed within fan-out bundling rule (single coherent "fix sync wiring for
  orthogonal task dimensions" scope).
- ✅ **Item 3 premise wrong fired** → no work needed, item closes immediately.
- ✅ **Item 5 wider-than-feasible-cleanup** → defer with concrete reframe.
- Final bundle: **3 PRs** (PR-D, PR-A, PR-B), down from 6.

---

## Phase 2 entry

Per the original prompt's HARD STOP: "do not begin Phase 2 until operator approves Phase 1
verdict + sequencing." Audit doc commits, then waits for operator approval before fan-out.

Operator response to Phase 1: *"Add the fields, don't be afraid of expanding scope."* —
Phase 2 unblocked with explicit approval of PR-D's expanded scope.

---

## Phase 3 — Bundle summary (pre-merge, per memory #16)

3 implementation commits land on branch `claude/f7-architectural-cleanup-5F3qH` ahead of
PR open. Per the system-prompt branch policy ("DEVELOP all your changes on the designated
branch above"), they ship as one PR holding the bundle rather than 3 separate PRs.

| Commit | Item | Files | LOC | Status |
|---|---|---|---|---|
| `b630d19` | Phase 1 audit doc | `docs/audits/F7_ARCHITECTURAL_CLEANUP_BATCH_AUDIT.md` | +384 | ✅ |
| `62e45a3` | PR-D: sync fields | `SyncMapper.kt`, `BackendSyncMappers.kt`, `BackendSyncService.kt`, `ApiModels.kt`, `SyncMapperTest.kt` | +40 / -1 | ✅ |
| `27def09` | PR-A: web TaskMode editor | `TaskEditor.tsx`, `tasks.test.ts` | +93 | ✅ |
| `061b420` | PR-B: roadmap editors | `ProjectRoadmapViewModel.kt`, `ProjectRoadmapEditDialogs.kt`, `ProjectRoadmapScreen.kt`, `TaskDependencyRepository.kt`, `ProjectRoadmapViewModelTest.kt` | +1008 / -50 | ✅ |

**Total**: 4 commits, 12 files, ~1525 LOC. Roughly tracks the audit's per-PR estimates;
PR-B came in at the upper end of its 300–500 estimate (~600 net) once tests + Compose
boilerplate were in.

### Verification status (CI is the green gate; local Android build is unavailable on this
runner — Linux env without the Android SDK)

- **PR-D**: 4 sync surfaces wired symmetrically; `SyncMapperTest.task_orthogonalDimensions_roundTrip`
  asserts all 3 orthogonal dimensions round-trip. Pre-existing 17 SyncMapper tests
  unchanged. CI will catch any Kotlin-compile drift.
- **PR-A**: `TASK_MODE_OPTIONS` constant + `<select>` mirror the freshly-shipped Cognitive
  Load editor verbatim. Tests cover create + update payload shape for both new fields.
  Web Firestore round-trip already in place (PR #1061 type def, mapping in
  `web/src/api/firestore/tasks.ts`).
- **PR-B**: ViewModel test exercises validation guards (blank title, self-edge), trim
  semantics, add-vs-update routing, sealed-variant ExternalAnchor save, and cycle-rejection
  error mapping. Compose Preview + screen-level smoke unchanged from the read-only baseline
  except for the 5 new affordances.

### Scope deltas vs Phase 1

- **PR-D**: scope grew from "fix taskMode in SyncMapper only" to "fix taskMode +
  cognitiveLoad across SyncMapper + BackendSyncMappers + BackendSyncService + ApiModels."
  Operator-approved per the Phase 2 entry note. Cognitive Load was the audit's surprise
  finding (memory #18 quad sweep (e) sibling-primitive axis), and shipping it in the same
  commit kept the silent-data-loss surface uniform across both fields.
- **PR-A**: scope expanded slightly to also fill in the missing `cognitiveLoad`
  Firestore-mapper test cases (PR #1084 added the cognitiveLoad mapper but no tests).
  ~30 LOC added on top of the TaskMode editor work. Same operator-approved expansion
  spirit — closes the orthogonal-dimension test gap as a bonus.
- **PR-B**: TaskDependency editor implementation kept on the roadmap surface (rather than
  on the task editor where it more naturally belongs), to avoid a 4-file pattern split.
  Trade-off: the dependency UI is functional but minimal — single-edge add + delete.
  Per-task-blocker management remains a follow-on for the task editor surface.

### Items closed in F.7

| Item | Disposition | Trail |
|---|---|---|
| 4 (taskMode SyncMapper gap) | Shipped + cognitiveLoad co-fix | Commit `62e45a3` |
| 1 (web TaskMode editor) | Shipped + cognitiveLoad test gap fill | Commit `27def09` |
| 2 (Timeline-class editor UI) | Shipped (3 entity types fully editable; dependency add/delete on roadmap) | Commit `061b420` |
| 3 (CloudIdOrphanHealer extension) | Already done in PR #1085 — no work needed | Audit Item 3 finding |
| 5 (DayBoundary cleanup) | Deferred — wider API-design pass needed | Audit Item 5 verdict |
| 6 (Web ProjectRoadmapScreen.tsx port) | Deferred — JSX upload gate not satisfied | Pre-flight finding |

**F.7 net change**: 8 → 2 (item 5 reframed as "SoD canonical API design" follow-on, item 6
re-arms when JSX uploaded, plus 1 follow-on for per-task dependency management on the task
editor surface).

### Memory candidates

1. **PROBABLY YES**: *"Every new orthogonal task-dimension column ships its sync wiring
   (4 surfaces: SyncMapper, BackendSyncMappers, BackendSyncService, ApiModels) AND its
   web TaskEditor surface in the same PR as the migration."* — verified by both PR #1061
   and PR #1084 having repeated the same omission across 4 surfaces. Adding this to the
   migration checklist would have caught both bugs at code-review time. Memory at 29/30;
   this is durable enough to claim the slot.

2. **MAYBE**: *"audit-first-mega 'recon-first quad sweep' (e) sibling-primitive axis
   reliably surfaces sibling-class regressions."* — PR-D's broader-than-stated gap was
   surfaced exactly because the audit deliberately enumerated cognitive_load alongside
   taskMode under sibling-primitive. Worth capturing if memory #18 doesn't already cover
   it explicitly enough. Skip if redundant.

### Schedule for next audit

- F.7's 2 remaining items (item 5 SoD design, item 6 web port) ride forward with concrete
  unblock triggers. No new audit needed — they pick up the next time their gates are
  satisfied.
- The 1 follow-on (per-task dependency management on task editor surface) is a new F-bucket
  candidate; surface to operator for placement.

