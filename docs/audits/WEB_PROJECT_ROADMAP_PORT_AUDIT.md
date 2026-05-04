# Web ProjectRoadmap Port — Phase 1 Audit

Closes the F.7 deferred "Web ProjectRoadmapScreen.tsx port" item (see
`F7_ARCHITECTURAL_CLEANUP_BATCH_AUDIT.md` § Item 6, table row 6 deferred
pending JSX upload). Operator picked the alternative un-block path
("build the web port from scratch") on 2026-05-04. This audit ports the
Android `ProjectRoadmapScreen` (PR #1085 + #1094) to a web TypeScript +
React surface, mirroring read + edit shape.

Branch: `claude/port-roadmap-web-ib0xY`. Single PR per memory #30.

---

## Operator decision (un-block trigger)

F.7's deferred un-block was: *"operator uploads PrismTaskTimeline*.jsx
OR picks separate web port from scratch."* The repo `find . -name
"PrismTaskTimeline*.jsx"` is **empty** (only the unrelated
`themesets/screen-*.jsx` mocks exist), so the JSX-mirror path is not
satisfied. Operator's prompt selects the second alternative explicitly:
"separate web port from scratch ... ~200-400 LOC web-only."

We use the Android `ProjectRoadmapScreen` (`app/src/main/java/...
/ui/screens/projects/roadmap/ProjectRoadmapScreen.kt`) as the structural
spec, not a JSX file.

---

## Recon findings (A.1–A.4)

### A.1 Drive-by detection (GREEN)

`git log -p -S "ProjectRoadmap" origin/main` surfaces three Android
commits and *zero* web ones:

- `31972dd` — feat(projects): timeline-class foundation — phases + risks
  + task progress (PR #1085, Android only).
- `7060a2f` — F.7 architectural cleanup batch — sync fields, web TaskMode
  editor, roadmap editors (PR #1094). Adds `ProjectRoadmapEditDialogs.kt`
  + `ProjectRoadmapViewModel.kt`. Web changes in this PR are unrelated
  (TaskMode editor on the existing web TaskEditor, not roadmap).
- `d588d53` — chore version bump.

No web roadmap source files exist. STOP-A clear.

### A.2 Parked-branch sweep (GREEN)

`git branch -r | grep -iE "web.*roadmap|roadmap.*web|roadmap.*tsx"`
returns only the current branch `origin/claude/port-roadmap-web-ib0xY`.
No orphan/parked work to rescue. STOP-A clear.

### A.3 Shape-grep (definitive enumeration of web current state)

| Surface | Web today | Source / verification |
|---|---|---|
| `web/src/types/task.ts` | `progress_percent?: number \| null` exists (line 59); `phase_id` is **missing** | `grep -n "progress_percent\|phase_id" web/src/types/task.ts` |
| `web/src/types/project.ts` | Lifecycle fields (`status`, `due_date`) only; no roadmap fields | Read of file |
| `web/src/types/` for ProjectPhase / ProjectRisk / ExternalAnchor / TaskDependency | **All 4 missing** | `grep -rn "ProjectPhase\|ProjectRisk\|ExternalAnchor\|TaskDependency" web/src/` ⇒ 0 results |
| `web/src/api/firestore/` | `projects.ts`, `tasks.ts`, others — **no** roadmap mappers | `ls web/src/api/firestore/` |
| `web/src/utils/projectBurndown.ts` | Already mirrors backend `compute_project_burndown` and reads `progress_percent` (PR-4 of timeline-class scope) | `find web/src -name "projectBurndown*"` + read |
| `web/src/features/projects/` | `ProjectListScreen.tsx`, `ProjectDetailScreen.tsx` only — no roadmap surface | `ls web/src/features/projects/` |
| `web/src/routes/index.tsx` | No `/projects/:id/roadmap` route | Read of file |

**Definitive verdict:** the web side has **zero** ProjectRoadmap UI and
**zero** Firestore wiring for the 4 new entities. The only piece of the
underlying data model that landed previously is `Task.progress_percent`
(used by `projectBurndown.ts`). `phase_id` was never added to web's
`Task` type or the Firestore mapper. STOP-B is clear *with one premise
correction* — see § Premise verification D.2.

### A.4 Sibling-primitive (e) axis quad-sweep

Surfaces with similar "list + detail + edit dialog" shape:

- `ProjectDetailScreen.tsx` — current project detail page. Could grow a
  "Roadmap" button to navigate into the new screen. **Surface, do not
  auto-expand** — operator can opt in after seeing the new screen.
- `TaskEditor.tsx` (extended in PR #1094 PR-A for TaskMode) — could
  surface task dependencies + phase picker per Android pattern.
  **Surface, do not auto-expand** — Android places phase/dependency
  affordances on the roadmap surface, not the task editor.
- No web equivalent to a "calendar/timeline of project phases" exists —
  this port creates the first one.

Per audit-first anti-patterns, none auto-included.

---

## Web current state for the 5 new entities (definitive)

| Entity | Web type | Web Firestore mapper | Web UI |
|---|---|---|---|
| `ProjectPhase` | ❌ missing | ❌ missing | ❌ missing |
| `ProjectRisk` | ❌ missing | ❌ missing | ❌ missing |
| `ExternalAnchor` (sealed) | ❌ missing | ❌ missing | ❌ missing |
| `TaskDependency` | ❌ missing | ❌ missing | ❌ missing |
| `Task.phase_id` | ❌ missing (only `progress_percent` shipped) | ❌ not in `tasks.ts` mapper | n/a |

Six discrete additions. No partial-state cleanup needed.

---

## Implementation hypothesis verdicts

### B.1 Web types (PROCEED with one correction) (GREEN)

Operator's prompt hypothesizes `ExternalAnchor` variants are *"URL /
file / note"*. The **actual** Android sealed class
(`domain/model/ExternalAnchor.kt`) has three variants:

```kotlin
data class CalendarDeadline(epochMs: Long)
data class NumericThreshold(metric: String, op: ComparisonOp, value: Double)
data class BooleanGate(gateKey: String, expectedState: Boolean)
```

We mirror Android exactly — not the prompt's hypothesis. JSON
discriminator is `type` with values `calendar_deadline` /
`numeric_threshold` / `boolean_gate` (see
`ExternalAnchorJsonAdapter.kt:30`). Web sealed-equivalent is a
discriminated union on a `kind` (or `type`) field; we use `type` so
JSON round-trip with Android needs zero translation.

**Files:**
- `web/src/types/projectPhase.ts` (new)
- `web/src/types/projectRisk.ts` (new)
- `web/src/types/externalAnchor.ts` (new — discriminated union)
- `web/src/types/taskDependency.ts` (new)
- `web/src/types/task.ts` (edit — add `phase_id?: string | null`)

LOC estimate: ~120 (mostly the discriminated union + RiskLevel /
ComparisonOp string-literal types).

### B.2 Firestore sync layer (PROCEED) (GREEN)

**Critical architectural finding (resolves STOP-E):** Android's
`SyncMapper` *comments* call these "child subcollection under a
project," but the **actual** upload code in `SyncService.kt:279, 302,
328, 517` writes them as **top-level** collections under
`users/<uid>/<collection>/`. The parent-project link is a `projectCloudId`
field on each doc; the parent-phase link on anchors is `phaseCloudId`;
dependency endpoints are `blockerTaskCloudId` + `blockedTaskCloudId`.

Web doc IDs **are** Firestore cloud IDs (web has no `sync_metadata`
indirection layer — see `web/src/api/firestore/converters.ts` and
existing `tasks.ts` / `projects.ts` mappers). So when web writes:

- `projectCloudId: project.id` — `project.id` *is* the cloud doc ID.
- `phaseCloudId: phase.id` likewise.
- `blockerTaskCloudId: task.id` likewise.

This means cross-platform round-tripping is byte-equivalent: Android
writes the cloud IDs into these fields; web's `id` is that same cloud
ID. No translation layer needed.

**Files (each ~100 LOC, mirrors `tasks.ts` / `projects.ts` shape):**
- `web/src/api/firestore/projectPhases.ts` (new)
- `web/src/api/firestore/projectRisks.ts` (new)
- `web/src/api/firestore/externalAnchors.ts` (new — JSON-encodes the
  variant before write, decodes after read; uses the same
  `calendar_deadline` / `numeric_threshold` / `boolean_gate`
  discriminator strings)
- `web/src/api/firestore/taskDependencies.ts` (new)
- `web/src/api/firestore/tasks.ts` (edit — round-trip `phaseId` field
  on `taskCreateToDoc` + `taskUpdateToDoc`, with omit-on-null semantics
  per the PR #836 parity audit)

Each file exposes: `getXxxByProject(uid, projectId)`, `getXxx(uid, id)`,
`createXxx(uid, data)`, `updateXxx(uid, id, data)`, `deleteXxx(uid,
id)`, `subscribeToXxx(uid, projectId, callback)`.

### B.3 React component shape (PROCEED) (GREEN)

Mirror Android `ProjectRoadmapScreen.kt` section structure:

```
ProjectRoadmapScreen
├── Back nav + project title
├── Section: Phases (count)
│   ├── PhaseCard (one per phase) with embedded task list +
│   │   per-task progress bar (uses progress_percent || isDone ? 1 : 0)
│   └── + button → PhaseEditDialog
├── Section: Unphased Tasks (only if any exist)
├── Section: Risks (count)
│   └── + button → RiskEditDialog
├── Section: External Anchors (count)
│   └── + button → AnchorEditDialog
└── Section: Dependencies (count)
    └── + button → DependencyAddDialog
```

**Files:**
- `web/src/features/projects/ProjectRoadmapScreen.tsx` (~180 LOC main
  page — orchestrates loading + section rendering)
- Sub-components inline as helpers (PhaseCard, TaskRow, RiskRow,
  AnchorRow, DependencyRow) — keep in same file to mirror Android
  layout, ~150 LOC.

Reuses existing `Card`, `Button`, `Modal`, `Spinner`, `EmptyState`,
`ProgressBar` from `web/src/components/ui/`.

### B.4 Edit UI (PROCEED) (GREEN)

Mirror Android `ProjectRoadmapEditDialogs.kt` — 4 dialogs:

- `PhaseEditDialog` — title, description, version anchor (start/end
  date intentionally pass-through-existing on Android first cut; web
  matches that to keep parity)
- `RiskEditDialog` — title, level (LOW/MEDIUM/HIGH dropdown), mitigation
- `AnchorEditDialog` — label + variant picker (DATE / METRIC / GATE) +
  per-variant fields. State buffers retained when flipping variants per
  Android UX.
- `DependencyAddDialog` — two task pickers (blocker, blocked) +
  client-side cycle-guard check before submit.

**File:**
- `web/src/features/projects/ProjectRoadmapDialogs.tsx` (~250 LOC) —
  uses existing `web/src/components/ui/Modal.tsx` to keep the modal
  pattern consistent with `ProjectDetailScreen.tsx`.

### B.5 Cycle guard (PROCEED) (GREEN — addition vs prompt scope)

Android has `domain/usecase/DependencyCycleGuard.kt` (DFS over outgoing
edges, `MAX_DEPTH=10_000`). Web needs a TypeScript port for the
client-side check used by the dependency picker before write —
otherwise web can write a cycle that Android-only enforcement would
miss for the round-trip window.

**File:**
- `web/src/utils/dependencyCycleGuard.ts` (~40 LOC, pure function:
  `wouldCreateCycle(edges, blocker, blocked): boolean`)

Not in the prompt's B.6 enumeration but called out in the prompt's
Phase 1 § B.6 ("cycle prevention test, mirroring `DependencyCycleGuard`
pattern client-side") — promoting to its own file so the test target is
unambiguous.

### B.6 Routing (PROCEED) (GREEN)

Android route key: `PrismTaskRoute.ProjectRoadmap` with `projectId` arg.
Web route: `/projects/:id/roadmap` — added under the AppShell-protected
group in `web/src/routes/index.tsx`, lazy-loaded matching the
`ProjectDetailScreen` pattern. Entry point: small "Roadmap" button on
`ProjectDetailScreen.tsx` next to the existing "Edit" / "Delete" buttons
(noted as a one-line UI add — does not expand scope into the project
detail screen otherwise).

**Files:**
- `web/src/routes/index.tsx` (edit — add `lazy()` import + route
  registration, ~3 lines)
- `web/src/features/projects/ProjectDetailScreen.tsx` (edit — add
  Roadmap nav button, ~6 lines)

### B.7 Test coverage (PROCEED) (GREEN)

Following web test conventions in `web/src/api/firestore/__tests__/*.test.ts`
(vi.hoisted Firestore mocks, write-payload assertions):

- `web/src/api/firestore/__tests__/projectPhases.test.ts` — round-trip +
  `projectCloudId` write
- `web/src/api/firestore/__tests__/projectRisks.test.ts` — round-trip +
  level enum default
- `web/src/api/firestore/__tests__/externalAnchors.test.ts` — variant
  round-trip for all 3 anchor types + malformed-JSON returns null
- `web/src/api/firestore/__tests__/taskDependencies.test.ts` —
  round-trip + immutable-no-update assertion
- `web/src/utils/__tests__/dependencyCycleGuard.test.ts` — empty graph,
  self-edge, simple cycle, transitive cycle, MAX_DEPTH safety

Skip per-component render tests for the page itself (no existing
project-detail render test in the repo to mirror; visual smoke is
covered by Phase 3 manual nav). LOC ~300 across all test files.

---

## STOP-conditions evaluated

- **STOP-A** (recon finds prior web shipping): clear. A.1 + A.2 + A.3
  all empty.
- **STOP-B** (partial wiring): clear. The only partial is
  `Task.progress_percent` already on web (used by burndown), with
  `phase_id` missing. Re-scoped: `phase_id` add is a one-line type +
  one-line mapper edit, not a full sub-task.
- **STOP-C** (sibling surfaces): two surfaces noted (ProjectDetailScreen
  link, TaskEditor dependency picker). Surfaced. Operator can pre-approve
  bundling; default per anti-pattern is do-not-expand. Recommendation:
  ship roadmap surface only this PR; ProjectDetailScreen Roadmap-nav
  button is the *one* required cross-surface edit (it's the entry point,
  no scope expansion).
- **STOP-D** (LOC estimate): prompt estimated 200-400 LOC. Rough sum:

  | File | LOC |
  |---|---|
  | 4 type files + Task edit | ~120 |
  | 4 Firestore mappers + tasks edit | ~450 |
  | Page component | ~330 |
  | Edit dialogs | ~250 |
  | Cycle guard | ~40 |
  | Routing + ProjectDetail nav button | ~10 |
  | Tests | ~300 |
  | **Total** | **~1,500 LOC** |

  **Materially exceeds 200-400 estimate.** Surfacing for operator visibility.
  Cause: prompt's hypothesis missed the Firestore-mapper depth (~450 LOC
  alone — each mapper is fully implemented, not a thin wrapper). The
  test scope adds another ~300. Page + dialogs are dense Compose-style
  flat structure.

  **Recommendation: PROCEED, single PR.** Splitting to multiple PRs
  doesn't help — the four mappers + page + dialogs are mutually
  dependent (page can't render without mappers; dialogs can't write
  without mappers; mappers can't be tested without types). All-or-nothing
  cohort.

- **STOP-E** (architectural divergence): clear with finding. Android
  *says* "subcollection under project" in mapper comments but *implements*
  flat top-level collections with `projectCloudId` discriminator. Web
  mirrors the implementation, not the comment. This is the kind of
  insight worth capturing in a memory entry — see Phase 4.

---

## Premise verification (D.1–D.5)

- **D.1** (Android `ProjectRoadmapScreen` shipped): ✅ verified at
  `app/src/main/java/com/averycorp/prismtask/ui/screens/projects/roadmap/ProjectRoadmapScreen.kt`
  via PR #1085 + #1094.
- **D.2** (Web `Task` has `progress_percent` + `phase_id`): ⚠️ **PARTIAL
  — PREMISE WRONG**. `progress_percent` is present
  (`web/src/types/task.ts:59`); `phase_id` is **NOT**. The audit prompt's
  premise is incorrect on this point. Adding `phase_id` to the web Task
  type is a required precondition for the roadmap surface to render
  task-under-phase. Captured as part of B.1 / B.2 above. No further
  scope change.
- **D.3** (`utils/projectBurndown.ts` exists on web): ✅ verified at
  `web/src/utils/projectBurndown.ts`. Already reads `progress_percent`.
- **D.4** (Web has zero ProjectRoadmap UI today): ✅ verified by A.3.
- **D.5** (No prior PR shipped this): ✅ verified by A.1 + A.2.

---

## Phase 2 scope (final)

Single PR on branch `claude/port-roadmap-web-ib0xY`. Estimated ~1,500
LOC vs prompt's 200-400 estimate (see STOP-D rationale). All files
listed in B.1–B.7 above.

Ordering:
1. Types (B.1) — no dependencies on other Phase-2 files.
2. Cycle guard (B.5) — pure function, no dependencies.
3. Firestore mappers (B.2) — depend on types.
4. Tests for mappers + cycle guard (B.7) — depend on mappers.
5. Edit dialogs (B.4) — depend on types.
6. Page component (B.3) — depends on mappers + dialogs.
7. Routing + ProjectDetail nav button (B.6) — depends on page.

Phase 3 verification per CLAUDE.md "audit-first Phase 3 + 4 fire
pre-merge": web-lint-and-test workflow GREEN, type-check passes, no
`any`. Cross-platform parity test (data created on web visible on
Android, and vice versa) is manual / non-blocking — Android side already
has the entities, so the round-trip is purely a Firestore-shape match
question that the mapper round-trip tests answer.

---

## Open questions for operator

None blocking. Recommendation defaults applied:

- Routing path: `/projects/:id/roadmap` — chosen to match
  `/projects/:id` URL hierarchy. No redirect needed.
- Modal library: existing `web/src/components/ui/Modal.tsx` per
  ProjectDetailScreen pattern (sibling consistency).
- "Roadmap" button placement on ProjectDetailScreen: alongside Edit /
  Delete buttons in the project header. One-button add, no layout
  change.
- Date pickers in PhaseEditDialog: deferred (Android also defers — see
  `ProjectRoadmapEditDialogs.kt:84-87` comment). Web matches. Date
  fields pass through existing values on edit.

---

## Deferred (NOT auto-filed per memory #30)

- **TaskEditor dependency picker on web**: Android places dependency
  affordances exclusively on the roadmap surface, not the task editor.
  Web matches. Re-trigger: operator wants per-task dependency editing
  outside the roadmap context.
- **ProjectDetailScreen "show progress bar with fractional units"
  enhancement**: web's project detail currently uses count-of-done /
  count-of-total; could weight by `progress_percent`. Not required for
  roadmap port. Re-trigger: operator wants project-detail progress bar
  to match roadmap surface's fractional progress.
- **Phase date pickers**: Android and web both ship without inline
  date pickers in the phase editor. Re-trigger: a shared web date-picker
  component lands and both editors adopt it.

---

## Anti-patterns to avoid (from prompt)

- ✅ Not auto-filing every Phase 1 finding — STOP-D LOC discrepancy is
  surfaced for operator visibility, not auto-actioned.
- ✅ Not widening scope to non-roadmap web surfaces without pre-approval
  — only ProjectDetailScreen nav-button add is in scope (it's the entry
  point).
- ✅ Phase 2 will not start until this audit doc lands — but per
  audit-first Phase 2 auto-fires after Phase 1, no operator gate.
- ✅ Not assuming Android UI structure transfers 1:1 — using Modal +
  Tailwind utility classes per existing web patterns, not Compose
  Material 3.
- ✅ Not duplicating burndown logic — `utils/projectBurndown.ts` already
  reads `progress_percent`; no edits needed there.

---

## Phase 3 — Bundle summary (post-implementation, pre-merge)

Phase 2 landed in a single commit on `claude/port-roadmap-web-ib0xY`
(SHA `1872624`), 20 files changed, +2,846 LOC. Phase 1 estimated
~1,500 LOC; actual is ~1.9x the estimate. Cause: the page component
(`ProjectRoadmapScreen.tsx`) and dialog file
(`ProjectRoadmapDialogs.tsx`) are denser than projected because the
existing `Modal` + `Select` primitives still need per-section row
components inline (no shared "row with edit/delete chrome" primitive
exists on web). Splitting the row primitives into a shared component is
a follow-on, not blocking.

Verification gates (all GREEN locally):
- `npm run lint` — clean (no errors, no warnings on the new files).
- `npx tsc -b --noEmit` — clean.
- `npm run test:run` — 491 / 491 pass; the 36 new tests are 5 from
  `projectPhases.test.ts`, 7 from `projectRisks.test.ts`, 10 from
  `externalAnchors.test.ts`, 4 from `taskDependencies.test.ts`, and 10
  from `dependencyCycleGuard.test.ts`. Existing `tasks.test.ts`
  payload-shape tests still green — confirms the omit-on-undefined edits
  for `phaseId` / `progressPercent` didn't regress the merge semantics.

Per-improvement table:

| Item | File(s) | PR | LOC | Status |
|---|---|---|---|---|
| 4 web types + Task.phase_id | `web/src/types/{projectPhase,projectRisk,externalAnchor,taskDependency,task}.ts` | TBD | ~280 | shipped |
| Dependency cycle guard | `web/src/utils/dependencyCycleGuard.ts` | TBD | ~55 | shipped |
| 4 Firestore mappers + tasks edit | `web/src/api/firestore/{projectPhases,projectRisks,externalAnchors,taskDependencies,tasks}.ts` | TBD | ~620 | shipped |
| Page component | `web/src/features/projects/ProjectRoadmapScreen.tsx` | TBD | ~620 | shipped |
| Edit dialogs | `web/src/features/projects/ProjectRoadmapDialogs.tsx` | TBD | ~530 | shipped |
| Routing + nav button | `web/src/routes/index.tsx` + `ProjectDetailScreen.tsx` | TBD | ~10 | shipped |
| Tests | 5 test files | TBD | ~430 | shipped (491/491 green) |

**Memory-entry candidates** (only if surprising / non-obvious):

1. **Android SyncMapper "subcollection" comments lie about layout** —
   the Kotlin doc-strings on `projectPhaseToMap` / `projectRiskToMap` /
   `externalAnchorToMap` say "child subcollection under a project," but
   the implementation in `SyncService.kt:279, 302, 328, 517` writes them
   as TOP-LEVEL `users/<uid>/<collection>/` collections discriminated by
   a `projectCloudId` field. A future audit of any cross-platform port
   must read the SyncService upload code, not the SyncMapper comments.
   Worth capturing if the operator hasn't already filed it.
2. **Web doc.id IS Android's `cloudId`** — there's no per-platform ID
   translation table on web. When mirroring an Android cloud_id-using
   field, web writes `<entity>.id` directly. Already implicit in
   existing mappers but easy to miss when porting a new entity family.

**Re-baselined wall-clock estimate:** Single-session port of an
Android Compose surface (~500 LOC view + ~370 LOC dialogs + ~270 LOC
view-model) translates to ~2.8x LOC on web (~2,400) plus tests
(~430). Useful for estimating future Android→web ports.

Schedule for next audit: F.7 close-out fully unblocked.

---

## Phase 4 — Claude Chat handoff summary

Emitted as a fenced markdown block in the session-output for paste into
a fresh Claude Chat thread. Not duplicated in this doc to keep the file
single-source-of-truth scoped to repo history; see the session log.
