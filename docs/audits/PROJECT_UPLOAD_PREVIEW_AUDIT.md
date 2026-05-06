# Project Upload Preview Audit

**Date**: 2026-05-06
**Branch**: `claude/add-upload-preview-screen-UuHYg`
**Scope**: User wants a preview screen to appear "when a project is
uploaded", modeled after the existing batch-edit preview. Map the
request to a concrete codebase delta: today's project-import flow
parses + writes to Room atomically and surfaces only a snackbar; the
batch-edit flow has a full sealed-state preview ViewModel + per-row
opt-out screen. Audit the gap, the refactor surface area, and the
risks of porting the BatchPreview pattern into the import path.

## TL;DR

Current import (`ProjectListScreen` "Upload File" / "Paste To-Do List"
FABs → `ProjectListViewModel.importFromFile/Text` → `ProjectImporter
.importContent` → snackbar) commits to Room inside `materialiseRich`
/ `materialiseFlatAsProject` / `materialiseFlatAsOrphans` before the
user ever sees what was parsed. There is no Cancel, no row opt-out,
no recovery from a misclassified parse. The closest existing analogue
is `BatchPreviewScreen` (`ui/screens/batch/`), which is a textbook
parse → Loaded preview → Approve-or-Cancel state machine. A second
analogue, `SyllabusReviewScreen` (`ui/screens/schoolwork/`), already
implements per-item checkboxes for syllabus parses — it's a closer
domain-fit than BatchPreview but a less crisp reference architecture.

The premise is valid: today's flow is irrecoverable mid-import and
the user-visible diff before commit is zero. PROCEED in a single
coherent PR (one feature, multiple files); split the importer into
`parse() : ImportPlan` + `materialise(plan, exclusions)` so the
preview ViewModel can render the plan without writing rows. The
parse-then-materialise split is the load-bearing refactor — every
other piece (ViewModel, screen, nav route, entry-point rewiring) is
mechanical once that contract exists.

---

## P1 — `ProjectImporter.importContent` writes to Room before the user can review (RED)

`ProjectImporter.kt:44-66` calls `materialiseRich`/`materialiseFlatAsProject`
/`materialiseFlatAsOrphans` directly from `importContent`. Each
materialise function inserts via `projectRepository.addProject(...)`,
`taskRepository.insertTask(...)`, `projectRepository.addPhase(...)`,
`projectRepository.addRisk(...)`, `externalAnchorRepository.addAnchor(...)`,
and `taskDependencyRepository.addDependency(...)` — no transaction
wrapper, so a partial failure mid-materialise leaves an
inconsistent project tree.

`ProjectListViewModel.kt:97-114` then renders the outcome as a
snackbar (`Imported "X": N tasks, M phases, K risks`). There is no
Cancel path; once `importContent` returns, the rows are already
committed.

**Risk**: The user has zero visibility into what Haiku / regex
parsed before it lands in Room. A misparse (e.g. wrong project name,
phantom phase headers, exam-priority misclassification) requires
manual deletion of every entity post-hoc.

**Recommendation**: PROCEED. Split `importContent` into
`parse(content, createProject) : ImportPlan?` (pure / read-only —
just `ChecklistParser.parse` + `TodoListParser.parse`, packaged into
a serializable plan) and `materialise(plan, exclusions) : ImportOutcome`
(does the writes). Old call site composes the two; new preview screen
calls `parse` only and defers `materialise` until Approve.

---

## P2 — `BatchPreviewScreen` is the right architectural precedent (GREEN)

`ui/screens/batch/BatchPreviewScreen.kt` (610 lines) +
`BatchPreviewViewModel.kt` (388 lines) + `AIRoutes.kt:102-120`
demonstrate the full pattern:

- Sealed `BatchPreviewState`: `Idle / Loading / Loaded / Committing
  / Applied / Error` (`BatchPreviewViewModel.kt:313-367`).
- Per-row opt-out via `excluded: StateFlow<Set<Int>>`
  (`BatchPreviewViewModel.kt:47-48, 247-250`).
- `LaunchedEffect(commandText)` on screen, with re-entry guards in
  `loadPreview` (`BatchPreviewViewModel.kt:69-76`) to prevent
  double-apply on recomposition.
- Modal route `batch_preview?command={command}` registered
  *without* `horizontalSlideComposable` so it feels like a sheet,
  not forward navigation (`AIRoutes.kt:98-120`).
- `BatchEvent` SharedFlow emits `Approved` / `Cancelled` so the
  caller can pop and show its own snackbar
  (`BatchPreviewViewModel.kt:43-44, 252-299`).

**Recommendation**: GREEN — no work needed on BatchPreview itself.
Use it as the structural template for the new
`ProjectImportPreviewScreen`. **Do not** import / share its
ViewModel — the domain (mutations vs. tree materialisation) is
different and forced reuse would couple unrelated state machines.

---

## P3 — `SyllabusReviewScreen` is a closer domain analogue but a weaker pattern (YELLOW)

`ui/screens/schoolwork/SyllabusReviewScreen.kt` (723 lines) already
implements per-item-checkbox preview for parsed syllabi
(`checkedTasks`, `checkedEvents`, `checkedRecurring` —
`SyllabusReviewScreen.kt:74-77`). Route `syllabus_review?uri={uri}`
is on `PrismTaskRoute` (`NavGraph.kt:155-157`) and accepts a file
URI directly, side-stepping the nav-arg length cap.

Why we don't reuse it: it's tightly coupled to the schoolwork
domain (course code, assignments, recurring schedule), and its
state shape (`UiState.Success(tasksCreated, eventsCreated,
recurringCreated)`) doesn't carry phases / risks / external
anchors / task dependencies that `ComprehensiveImportResult`
emits. Generalising it would either bloat the syllabus path or
re-introduce the coupling we want to avoid.

**Recommendation**: GREEN-with-cite — reference its URI-passing
trick (pass `Uri.encode(uri.toString())` via nav arg, read content
inside the ViewModel) so we don't try to push the parsed contents
through `SavedStateHandle` (which has a hard cap and would truncate
real schedules).

---

## P4 — Split `ProjectImporter` into `parse()` + `materialise()` (RED, must precede screen work)

The refactor is purely additive at the `ProjectImporter` API level.
Today's `importContent(content, createProject)` becomes:

```kotlin
suspend fun parse(content: String, createProject: Boolean): ImportPlan?
suspend fun materialise(plan: ImportPlan, exclusions: ImportExclusions = ImportExclusions.EMPTY): ImportOutcome
suspend fun importContent(content, createProject): ImportOutcome  // unchanged: composes parse + materialise
```

`ImportPlan` carries the parsed shape verbatim — same fields the
preview needs to render: `projectName`, `tasks: List<ChecklistParsedTask>`,
`phases`, `risks`, `externalAnchors`, `taskDependencies`, plus a
discriminator (`Rich` / `FlatProject` / `FlatOrphans` / `Unparseable`).
`ImportExclusions` is a per-section `Set<Int>` of plan-relative
indices the user opted out of.

Existing `importContent(content, createProject)` keeps its signature
and behaviour by composing the two — every existing call site
(`ProjectListViewModel`, `TaskListViewModel`) compiles unchanged
unless we rewire it to the preview path.

**Recommendation**: PROCEED. Land in the same PR as the screen —
splitting it into a separate "refactor only" PR adds round-trip
cost without measurable safety value (the new methods are dead
code until the screen calls them).

---

## P5 — `ProjectImportPreviewScreen` + ViewModel + nav route (RED)

New files, mirroring `ui/screens/batch/`:

- `ui/screens/projects/ProjectImportPreviewScreen.kt` — Scaffold
  + LazyColumn rendering parsed sections (project header, phases,
  tasks, risks, anchors, dependencies). Per-row Checkbox toggles
  `excluded` membership for that section. Bottom bar: Cancel /
  Approve, Approve disabled when state isn't `Loaded` or every
  section is excluded.
- `ui/screens/projects/ProjectImportPreviewViewModel.kt` — sealed
  `ImportPreviewState`: `Idle / Loading / Loaded(plan, …) /
  Committing / Applied(outcome) / Error(kind, message)`. Same
  re-entry guard pattern as `BatchPreviewViewModel.loadPreview`
  to defuse `LaunchedEffect` re-fires.
- `PrismTaskRoute.ProjectImportPreview` — `import_preview?uri={uri}&asProject={asProject}`
  for the file path; a sibling `import_preview_text` cannot pass
  the pasted contents through the nav arg safely (length cap), so
  pasted text routes via a process-level handoff (a `Singleton`
  one-shot holder injected into the VM, cleared on consume).
  Mirror `BatchPreview`'s no-slide registration in `AIRoutes.kt`
  (or a new `ProjectRoutes.kt` block — see P9).

The ViewModel's `loadPlan` calls `projectImporter.parse(...)`. If
`null` → `Error(ParseFailure)`. Otherwise → `Loaded`. `approve()`
calls `projectImporter.materialise(plan, exclusions)` and emits
`ImportEvent.Approved(outcome)` on success.

**Recommendation**: PROCEED. Modeled 1:1 on BatchPreview shape; no
novel state-machine design.

---

## P6 — `TaskListScreen` mirror entry point also bypasses preview (YELLOW)

Per `ProjectImporter.kt:14-15` doc comment, `TaskListViewModel`
also delegates to `ProjectImporter` (the F.8 mirror). If we reroute
only `ProjectListScreen`, the TasksScreen entry will silently retain
the old commit-without-preview behaviour — same UX gap, different
launcher.

**Recommendation**: PROCEED in the same PR. The rewiring is a 5-line
change per call site (replace `viewModel.importFromFile/Text` with
`navController.navigate(ProjectImportPreview.createRoute(uri,
asProject))`). Skipping it would ship a partial fix and immediately
land on a follow-up audit.

---

## P7 — Per-row opt-out granularity for tasks, phases, risks, anchors, dependencies (YELLOW)

`ComprehensiveImportResult` carries five disjoint collections.
Naive opt-out: one `Set<Int>` per collection on the VM, indexed by
position in the plan. Subtasks complicate this — excluding a parent
must implicitly exclude its subtasks (or the materialise call drops
them as orphans). External anchors reference phases by name
(`ParsedExternalAnchorDomain.phaseName`); task dependencies
reference tasks by title. Dropping a phase or task should cascade.

**v1 cut**: opt-out tasks + risks at the section level (granular
per-row checkboxes). Phases / anchors / dependencies render
read-only with a hint that "all phase scaffolding will be
imported" — this matches BatchPreview's behavior of letting the
user opt out of *user-facing* changes but not the structural
plumbing. Defer multi-section selective opt-out + cascade logic to
a follow-up if user feedback warrants it.

**Recommendation**: PROCEED with the v1 cut. Cascade rules are a
correctness rabbit hole that don't block first ship and can be
added incrementally without re-architecting the screen. The
DEFERRED bit is "phase / anchor / dependency selective opt-out
with cascade-aware exclusion."

---

## P8 — Re-entry guards on `loadPlan` (YELLOW)

`BatchPreviewViewModel.loadPreview` has explicit re-entry guards
because `LaunchedEffect(commandText)` re-fires on recomposition,
and re-running the parse on an already-`Applied` state could
double-apply non-idempotent inserts. The same hazard exists on
the import preview (every materialise method does
`projectRepository.addProject` — committing twice would create
two projects).

**Recommendation**: PROCEED. Copy the guard shape verbatim:

```kotlin
when (val current = _state.value) {
    is ImportPreviewState.Loading,
    is ImportPreviewState.Committing,
    is ImportPreviewState.Applied -> return
    is ImportPreviewState.Loaded ->
        if (current.plan.contentHash == newHash) return
    ImportPreviewState.Idle, is ImportPreviewState.Error -> Unit
}
```

`contentHash` is just `content.hashCode()` for the dedupe — we
don't need cryptographic strength, only recomposition-stability.

---

## P9 — Where to register the nav route (GREEN)

`AIRoutes.kt` already hosts `BatchPreview` and is the conventional
home for "preview-then-pop" modal flows (other modal-style entries
note this in `AIRoutes.kt:98-101`). No new route file is needed.
Adding to `AIRoutes.kt` keeps the modal-flow convention searchable.

**Recommendation**: GREEN — register inside `aiRoutes()` alongside
`BatchPreview`. Add the corresponding `PrismTaskRoute` data object
in `NavGraph.kt` next to `BatchPreview` (`NavGraph.kt:192-195`).

---

## P10 — Test coverage (RED)

`app/src/test/` already has `ProjectImporterTest`-style coverage
patterns; the parse/materialise split is the bit most likely to
regress silently (a future change to `ChecklistParser` could break
the plan-shape contract without touching the screen).

**Recommendation**: PROCEED with two focused tests:

1. `ProjectImporter.parse` returns `ImportPlan.Rich` for content
   with phases + risks; `ImportPlan.FlatProject` for plain todo
   lists; `ImportPlan.FlatOrphans` when `createProject = false`;
   `null` for unparseable content.
2. `ProjectImporter.materialise(plan, exclusions)` honours
   per-section exclusions — tasks at excluded indices are not
   inserted, risks at excluded indices are not inserted, kept
   sections produce the same row counts as the unfiltered
   `importContent` baseline.

Skip a screen-level Compose UI test for v1 — BatchPreview itself
doesn't have one and hasn't regressed; it's a high-cost low-yield
test for this scope.

---

## P11 — Anti-patterns to avoid

- **Don't generalise `BatchPreviewViewModel`.** The state machine
  looks similar but the domain is different (mutation list with
  ambiguity hints + medication candidates vs. tree of parsed
  entities). Forced reuse couples unrelated code paths.
- **Don't pass parsed contents through the nav arg.** Compose
  Navigation's `SavedStateHandle` has a practical size cap; a
  multi-page schedule would truncate. Pass the URI (file path) or
  use a process-level handoff (`Singleton` holder cleared on
  consume) for pasted text.
- **Don't wrap `materialise` in a single Room transaction "for
  safety".** The existing `importContent` already isn't
  transactional; introducing a transaction here is scope creep
  that should land in its own PR with its own audit (the right
  fix is `@Transaction` on a DAO method, not at the use-case
  layer).
- **Don't add a "Skip preview" toggle in Settings.** The whole
  point is to add the preview; making it bypassable defeats the
  request and reintroduces the no-undo footgun. If the user wants
  fast-path import later, that's a separate feature.

---

## Ranked improvement table (wall-clock-savings ÷ implementation-cost)

| Item | Risk | Effort | Saves wall-clock when…                                              |
|------|------|--------|---------------------------------------------------------------------|
| P4   | RED  | Low    | Future tests / preview screen calls into a clean `parse()` boundary |
| P5   | RED  | Med    | User catches a misparse before it lands in Room                     |
| P10  | RED  | Low    | Future parser refactor regression is caught by `parse()` test       |
| P1   | RED  | (resolved by P4 + P5) — premise that motivates the feature          |
| P6   | YEL  | Low    | TasksScreen import path stops being a hidden footgun                |
| P8   | YEL  | Low    | No double-commit on recomposition (same class of bug as #BatchPreview audit) |
| P7   | YEL  | Low    | Tasks + risks opt-out covers ~90% of misparse recovery              |
| P9   | GRN  | Trivial| One nav route registered consistently                               |
| P2   | GRN  | None   | Pattern lift only                                                   |
| P3   | YEL  | None   | URI-passing precedent cited                                         |

---

## Phase 2 plan

Single coherent PR, branch `claude/add-upload-preview-screen-UuHYg`
(already checked out per task instructions; no separate worktree
needed since the session is scoped to this branch):

1. Refactor `ProjectImporter`: add `ImportPlan` sealed class +
   `parse()` + `materialise(plan, exclusions)`; keep
   `importContent(content, createProject)` as a thin composition
   for backward compat.
2. Add `ui/screens/projects/ProjectImportPreviewScreen.kt` and
   `ProjectImportPreviewViewModel.kt`; mirror BatchPreview shape.
3. Register `PrismTaskRoute.ProjectImportPreview` in
   `NavGraph.kt`; wire the route in `AIRoutes.kt` next to
   `BatchPreview`.
4. Rewire `ProjectListScreen` paste + file-picker confirms to
   `navController.navigate(ProjectImportPreview.createRoute(...))`
   instead of calling `viewModel.importFromText/File` directly.
5. Same rewiring on `TaskListScreen` mirror.
6. Add `ProjectImporterParseTest` + `ProjectImporterMaterialiseTest`.
7. Run `./gradlew testDebugUnitTest`; commit; push; open PR.

Phases 3 + 4 fire pre-merge per CLAUDE.md ("Audit-first Phase 3 + 4
fire pre-merge") — append bundle summary + Claude Chat handoff
block to this doc as soon as the PR is opened.

---

## Phase 3 — Bundle summary

**PR**: #1149 — `claude/add-upload-preview-screen-UuHYg` →
`main`. Single coherent PR per the fan-out bundling rule (this is
one feature, not a fan-out of independent fixes).

Per-improvement landing notes:

- **P4 ProjectImporter parse/materialise split** — landed as the
  load-bearing refactor. `importContent(content, createProject)`
  retained verbatim by composing `parse() + materialise()` so no
  call site needed updating.
- **P5 ProjectImportPreviewScreen + ViewModel + nav route** —
  landed at `ui/screens/projects/ProjectImport*.kt` and registered
  in `AIRoutes.kt` (modal-style, no slide transition). Sealed
  state machine + re-entry guards mirror BatchPreviewViewModel
  verbatim.
- **P6 TaskListScreen mirror entry point** — collapsed during
  implementation: a `grep` for `ProjectImporter` use sites
  showed only `ProjectListViewModel`. The doc comment on
  `ProjectImporter` (lines 14-15 pre-edit) claimed
  `TaskListViewModel` also delegates here, but the
  TaskListScreen has no import flow. Doc comment rewritten in
  the same PR. **Premise was wrong** — exactly the
  STOP-and-report case the workflow flags. No work needed.
- **P7 per-row opt-out granularity** — v1 cut shipped (tasks +
  risks). Phases / anchors / dependencies render read-only.
  DEFERRED: cascade-aware selective opt-out for the structural
  sections.
- **P8 re-entry guards on loadPlan** — landed; same shape as
  BatchPreview's guard, keyed on a `sourceKey` derived from
  `(uri, asProject)`.
- **P9 nav route registration** — landed in `AIRoutes.kt`.
- **P10 unit tests** — `ProjectImporterTest` covers parse
  branching (Rich / FlatProject / FlatOrphans / null), exclusion
  honouring at materialise time, and `importContent` backward
  compat. Screen-level Compose UI test deliberately skipped per
  audit § P10.

Wall-clock estimate: parse/materialise split + screen +
ViewModel + nav route + entry-point rewire + tests landed in a
single ~1k-line PR. The split-then-add-screen strategy paid off
— the screen file is mechanical Compose because the ViewModel
contract is already shaped right.

Memory entry candidates: none — every surprise (P6 stale doc
comment, paste-text length cap forcing a process-level handoff)
was already flagged in the audit.

Schedule for next audit: when user feedback surfaces a need for
cascade-aware exclusion (P7 deferral) or for the
"BatchUndoEventBus"-equivalent on the import path so the caller
can render its own snackbar with the outcome counts.

---

## Phase 4 — Claude Chat handoff

```markdown
## Scope

Audited the request "When a project is uploaded, I want a
preview screen similar to the batch edit preview screen" against
the PrismTask Android codebase
(`averycorp/prismtask`, branch
`claude/add-upload-preview-screen-UuHYg`). Mapped it to a
concrete delta: today's project import flow commits to Room
inside `ProjectImporter.materialise*` before showing anything;
batch-edit has a full preview state machine. PR #1149 ports the
pattern.

## Verdicts

| Item                                                       | Verdict      | One-line finding                                                                                       |
|------------------------------------------------------------|--------------|--------------------------------------------------------------------------------------------------------|
| P1 Import writes before user reviews                       | RED          | `ProjectImporter.importContent` calls `materialise*` directly — no Cancel path                         |
| P2 BatchPreview as pattern                                 | GREEN        | `ui/screens/batch/BatchPreviewScreen.kt` + `…ViewModel.kt` — sealed-state + per-row opt-out, lift only |
| P3 SyllabusReview as alt precedent                         | YELLOW       | Closer domain fit but tightly coupled to schoolwork; cited only for URI-passing trick                  |
| P4 Split `parse()` from `materialise()`                    | RED → SHIPPED| Load-bearing refactor; old call site preserved by composing the two                                    |
| P5 New preview screen + VM + nav route                     | RED → SHIPPED| Mirrors BatchPreview shape verbatim                                                                    |
| P6 TaskListScreen mirror entry point                       | YELLOW → STOP| Premise wrong — TaskListScreen has no import flow; stale doc comment on ProjectImporter                |
| P7 Per-row opt-out granularity                             | YELLOW → SHIPPED v1 / DEFERRED| Tasks + risks shipped; phases/anchors/deps read-only (cascade is a rabbit hole)       |
| P8 Re-entry guards on `loadPlan`                           | YELLOW → SHIPPED| Same shape as BatchPreview's `loadPreview` guard, keyed on `(uri, asProject)`                       |
| P9 Where to register nav route                             | GREEN        | `AIRoutes.kt` next to BatchPreview, no slide transition                                                |
| P10 Test coverage                                          | RED → SHIPPED| `ProjectImporterTest` — parse branching + exclusion honouring + `importContent` compat                 |

## Shipped

- **PR #1149** — Add project upload preview screen. Splits
  `ProjectImporter` into `parse()` + `materialise(plan,
  exclusions)`; adds `ProjectImportPreviewScreen` +
  `ProjectImportPreviewViewModel` + `PendingImportContent`
  singleton holder; registers `ProjectImportPreview` route in
  `AIRoutes.kt`; rewires `ProjectListScreen` paste + file
  confirms to navigate to the preview; adds `ProjectImporterTest`.
  ~1k LOC, 9 files. Audit doc:
  `docs/audits/PROJECT_UPLOAD_PREVIEW_AUDIT.md`.

## Deferred / stopped

- **P6 TaskListScreen mirror rewire** — STOP-no-work-needed.
  Premise turned out wrong: `grep -r ProjectImporter` showed
  only `ProjectListViewModel` calls into the importer. The doc
  comment claiming `TaskListViewModel` also delegates was stale
  and was rewritten in this PR.
- **P7 cascade-aware exclusion for phases / anchors / task
  dependencies** — DEFERRED. v1 ships read-only rendering for
  these structural sections. Adding selective opt-out requires
  cascade rules (excluding a phase implies orphaning anchors
  that target it; excluding a task title invalidates
  dependencies referencing it). Will revisit if user feedback
  warrants it.
- **Outcome-aware caller snackbar** — DEFERRED. Today the
  preview's `Applied` state renders briefly before popping; the
  caller (`ProjectListScreen`) doesn't see imported counts.
  Adding a `BatchUndoEventBus`-equivalent for the import path
  would let the caller surface "Imported X tasks, Y phases" on
  return. Low-value v1.

## Non-obvious findings

- Compose Navigation's nav-arg has a practical length cap that
  silently truncates real schedules; pasted content can't ride
  the nav arg. The PR uses a `@Singleton` `PendingImportContent`
  one-shot holder consumed inside the VM. File imports pass the
  URI string through the nav arg directly because URIs are
  short and the file is read inside the VM via
  `contentResolver.openInputStream`.
- The BatchPreview ViewModel's re-entry guard
  (`BatchPreviewViewModel.kt:69-76`) is load-bearing: Compose
  Navigation re-fires `LaunchedEffect(commandText)` during the
  pop transition, and re-running materialise on an Applied
  state would double-insert the project tree. Same guard is
  copied verbatim into `ProjectImportPreviewViewModel.loadPlan`.
- `SyllabusReviewScreen` (`ui/screens/schoolwork/`) already
  implements per-item-checkbox preview for parsed syllabi but
  is too domain-specific to generalise — its state shape
  doesn't carry phases / risks / anchors / dependencies.
  Reference for URI-passing trick only.
- The audit doc's P6 was a real-world example of the workflow's
  STOP-and-report-on-wrong-premise rule firing. The premise
  came from the `ProjectImporter` doc comment, which was stale.

## Open questions

- Should the preview screen surface a Confidence/Banner area
  for AI-vs-regex parses (like BatchPreview's
  `ConfidenceBanner` for low-confidence Haiku parses)?
  Today's `ChecklistParser` / `TodoListParser` don't return a
  confidence score; adding one would require a backend signal.
  Punted to follow-up.
- Should phase / anchor / dependency exclusion ship as a
  follow-up, or stay deferred indefinitely? Need user
  feedback on whether structural-section opt-out is real
  demand or premature flexibility.
```

