# WEB_TASK_DEPENDENCY_PARITY_AUDIT.md

**Date:** 2026-05-04
**Branch:** `claude/web-task-dependency-parity-hxjm0`
**Scope:** F.8a — port Android TaskDependency blocker management UI to web TypeScript.
**Verdict:** **STOP-and-reframe.** Phase 1 only. Phase 2 does NOT auto-fire. Premise is wrong on two load-bearing points; operator decision required before any code change.

---

## TL;DR

The prompt's framing assumes:

1. PR #1097 shipped Android `TaskDependency` blocker management on the **Organize tab** of the task editor (~376 LOC).
2. The web port is "mirror that Organize tab section."
3. Asymmetry-flag: shipping editor-only on web "would mirror the asymmetry we're closing on Android."

Both (1) and (3) are factually wrong against the current `main` codebase:

- **PR #1097 does not exist.** The highest-numbered merged PR on `main` is **#1094** ("F.7 architectural cleanup batch — sync fields, web TaskMode editor, roadmap editors"). PR numbers in the recent log: #1085, #1090, #1091, #1093, #1094. There is no #1095, #1096, or #1097.
- **`OrganizeTab.kt` has no Blockers section.** A direct `grep` for `Blocker|blocker|addBlocker|removeBlocker|TaskDependency` against `app/src/main/java/com/averycorp/prismtask/ui/screens/addedittask/` returns **zero hits**. The Organize tab today contains: Project, Tag, Parent task indicator, Life Category, Task Mode, Cognitive Load — no Blockers.
- **`AddEditTaskViewModel.kt` has no `addBlocker` / `removeBlocker` / `TaskDependency` references.** Same zero-hit result.
- The **only** Android TaskDependency UI surface today is `ProjectRoadmapScreen.kt` + `ProjectRoadmapEditDialogs.kt` + `ProjectRoadmapViewModel.kt` (introduced in PR #1085 timeline-class foundation, extended in PR #1094 F.7 cleanup). It renders edges as `DependencyRow` cards inside the project roadmap — `app/.../roadmap/ProjectRoadmapScreen.kt:463-495`.

So the actual asymmetry is the opposite of what the prompt describes:

- **Android today:** TaskDependency UI lives **only on the project roadmap surface**, NOT on the task editor.
- **Web today:** zero TaskDependency surface anywhere.
- **F.7 web roadmap port (`ProjectRoadmapScreen.tsx`):** does not exist. `find web/src -iname "*roadmap*"` returns zero. `grep -rn "ProjectRoadmap\|project_phases\|project_risks\|external_anchors" web/src/` returns zero.

Building "Blockers section on web `TaskEditor.tsx`" today would create a **new** cross-platform asymmetry (web has dependency editor on the task editor; Android does not) instead of closing one.

Per audit-first hard rule: "STOP-and-report on wrong premises is the one real halt." Phase 2 is paused pending operator decision. Recommendations enumerated in § Recommendation.

---

## A. Recon findings (memory #18 quad sweep)

### A.1 Drive-by detection (GREEN — no parked web work)

```
git log -p -S "TaskDependency\|taskDependency\|task_dependencies" origin/main --oneline | head -20
```

Results: only Android-side TaskDependency commits. The most recent is PR #1094 (F.7), which extended `TaskDependencyRepository.getAllOnce()` and added Edit/Delete icons to `ProjectRoadmapScreen.kt`. **No web-side TaskDependency commits exist on `main`.**

### A.2 Parked-branch sweep (GREEN — only this branch)

```
git branch -r | grep -iE "web.*depend|web.*block|task.*editor.*web|claude/web"
```

Result: **single match** — the active branch `origin/claude/web-task-dependency-parity-hxjm0` (this session). No prior web TaskDependency work has been parked.

### A.3 Shape-grep — verify "zero web surface" premise (GREEN — confirmed zero)

```
grep -rn "TaskDependency\|task_dependencies\|taskDependency" web/src/   # → zero
find web/src -iname "*ependency*" -o -iname "*locker*"                   # → zero
ls web/src/api/firestore/                                                # no taskDependencies.ts
ls web/src/types/                                                        # no taskDependency.ts
```

The PR #1097 claim that web has "zero TaskDependency surface" is the one premise that **is** still accurate — but only by accident, not by virtue of any planned sequencing.

### A.4 Web task editor canonical file (GREEN — single surface, 1,274 LOC)

```
find web/src -iname "*editor*" -o -iname "*edittask*" -o -iname "*taskform*"
→ web/src/features/tasks/TaskEditor.tsx        (1,274 LOC)
→ web/src/features/medication/MedicationSlotEditor.tsx
→ web/src/features/templates/UserTemplateEditors.tsx
→ web/src/features/templates/TemplateEditorModal.tsx
```

`web/src/features/tasks/TaskEditor.tsx` is the canonical task editor. It is a single component (no modal-vs-page split). Sibling sections present today (per inline grep): Tags (line 1071), and via the `TaskEditor.tsx` shape, the existing pickers established by PR #1094 (TaskMode, etc.).

### A.5 Sibling-primitive (e) axis — missing-on-web pickers (YELLOW — surface, do not auto-bundle)

The prompt's quad-sweep (e) axis flagged checking other Android task-editor sections that may be missing on web. Inventory:

| Android `OrganizeTab.kt` section | Web `TaskEditor.tsx` parity? | Source-of-truth ref |
|---|---|---|
| Project selector | YES | `TaskEditor.tsx` project selector |
| Tag selector | YES | `TaskEditor.tsx:1071` |
| Parent task indicator | unknown — out of scope here | n/a |
| Life Category | YES (PR #1094 era) | `web/src/types/task.ts:126` `LifeCategory` enum |
| Task Mode | YES (PR #1094) | `web/src/types/task.ts:128` `TaskMode` enum |
| Cognitive Load | YES (PR #1094) | `web/src/types/task.ts:131` `CognitiveLoad` enum |
| **Blockers / Dependencies** | **NO** — but Android does NOT have it on the editor either | only in `ProjectRoadmapScreen.kt` |

No actionable missing-on-web sibling pickers surfaced. The (e) axis confirms the editor pickers are at parity post-#1094.

### A.6 F.7 web roadmap port status (RED — unstarted)

```
find web/src -iname "*roadmap*"                                                       # → zero
grep -rn "ProjectRoadmap\|project_phases\|project_risks\|external_anchors" web/src/    # → zero
```

PR #1094's title includes "roadmap editors" but those changes are in **Android** (`ProjectRoadmapScreen.kt`, `ProjectRoadmapEditDialogs.kt`). The web roadmap port has **not** started. This is the load-bearing finding for the Option A/B/C decision.

---

## B. Web current state for TaskDependency (DEFINITIVE)

| Layer | Web today | Android today |
|---|---|---|
| Type definition | none | `TaskDependencyEntity` (`data/local/entity/`) |
| Firestore mapper | none | `SyncMapper.taskDependencyToMap` / `mapToTaskDependency` (`data/remote/mapper/SyncMapper.kt:339-363`) |
| Sync wiring | none | `SyncService.kt` (push line 502, pull line 2199) |
| Cycle guard | none | `DependencyCycleGuard.kt` (DFS, MAX_DEPTH=10_000, treats self-edges as cycles) |
| Read API | none | `TaskDependencyRepository` |
| Write API | none | `TaskDependencyRepository.addDependency` / `removeDependency` |
| Editor UI surface | **none** | **none** — Android editor also has no Blockers section |
| Roadmap UI surface | none (no roadmap port) | `ProjectRoadmapScreen.kt:463-495` `DependencyRow`, plus add-flow in `ProjectRoadmapViewModel.kt:250-256` |

**Cross-platform parity status:** web is one tier behind Android. To close the gap the way Android closed it, web needs the **roadmap surface**, not an editor surface.

---

## C. Premise verification (memory #22 bidirectional)

| ID | Claim | Verdict | Evidence |
|---|---|---|---|
| D.1 | "PR #1097 shipped Android TaskDependency UI on Organize tab" | **WRONG** | PR #1097 does not exist. Max PR = #1094. `OrganizeTab.kt` has zero blocker references. |
| D.2 | Web has zero TaskDependency surface today | RIGHT | A.3 grep confirms |
| D.3 | Web `Task` type matches Firestore schema (no breaking change needed) | RIGHT | `web/src/types/task.ts:4-60` round-trips Android schema via `tasks.ts` mapper |
| D.4 | No prior PR shipped web TaskDependency work | RIGHT | A.1 + A.2 both empty |
| D.5 | F.7 web roadmap port status | UNSTARTED | A.6 confirms zero web roadmap files |
| (extra) | "PR #1097 filed F.8a/F.8b/F.8c follow-ons during its audit" | UNVERIFIABLE | PR #1097 does not exist — these follow-on item IDs cannot be traced to any merged audit doc on `main` |

Two of six premises are wrong. The prompt's framing of the asymmetry (D.1) and the implicit framing of "F.8a was filed by PR #1097's audit" cannot stand.

---

## D. STOP-conditions evaluated

- **STOP-A (web work already shipped):** no fire. Web has zero TaskDependency surface. (Premise verified — but for the wrong reason: the work is unstarted, not deferred.)
- **STOP-B (web Firestore wiring partially shipped):** no fire. Nothing exists on web.
- **STOP-C (F.7 web roadmap port shipped or in-flight):** **PARTIAL FIRE.** F.7 is **unstarted**, not in-flight. The prompt's "Option C path (no asymmetry)" is the path of least surprise: ship F.7 first, then this PR ships dependency editor (or dependency lanes inside the roadmap, mirroring Android) as follow-up.
- **STOP-D (LOC estimate materially differs):** the 150-300 LOC estimate is for "editor-only Blockers picker." Bundling with F.7 (Option B) would balloon to ~1,500+ LOC (roadmap screen + viewmodel + edge picker + cycle guard + types + mapper + tests). Single-PR-per-branch convention strain confirmed.
- **STOP-E (web UI library missing primitives):** not evaluated in detail because the prior STOPs preempt it. Web uses Tailwind + custom components; the existing tag-picker in `TaskEditor.tsx:1071-1144` proves the search-then-pick pattern is achievable.
- **STOP-F (quad-sweep finds multiple missing-on-web pickers):** no fire. Editor pickers are at parity post-#1094.
- **NEW STOP — premise wrong (audit-first hard rule):** **FIRES.** The Android Organize tab Blockers section the prompt asks us to mirror does not exist. The cycle-guard port and Firestore mapper claims are still tractable, but the framing ("mirror the editor") cannot be honored.

---

## E. Recommendation

**Halt Phase 2.** Three operator-eligible paths:

### Path 1 (RECOMMENDED) — Defer F.8a until F.7 web roadmap port lands

Rationale: this most faithfully mirrors how Android closed the gap. Android shipped TaskDependency UI by adding `DependencyRow` to `ProjectRoadmapScreen.kt`, not by adding it to the task editor. The web equivalent is to first build `web/src/features/projects/ProjectRoadmapScreen.tsx`, then add the dependency lanes inside it. This sequences cleanly:

1. Spin up an F.7 prompt (separate session) that ports `ProjectRoadmapScreen.kt` + `ProjectRoadmapEditDialogs.kt` + `ProjectRoadmapViewModel.kt` to web.
2. F.8a (this prompt) becomes a follow-up that adds dependency lanes inside the new web roadmap surface (mirroring Android `DependencyRow`).
3. Total work is split across two PRs that each fit single-PR-per-branch convention.

Drawback: F.8a doesn't ship in this session. Operator pre-decided F.8a above F.7 in the prompt's framing — but that decision was based on a wrong premise (assuming PR #1097 already shipped editor-only on Android, so web could mirror it cheaply). With the corrected premise, F.7 sequencing is the cheapest path to true parity.

### Path 2 — Build editor-only on web AND back-port to Android in same session

Rationale: if operator genuinely wants the editor surface, the symmetric move is to **add a Blockers section to Android `OrganizeTab.kt`** in the same PR-bundle. That would close the actual current asymmetry (Android editor has no blockers surface; web is about to add one) by closing it on both platforms simultaneously.

Estimated scope: ~150-300 LOC web + ~200-400 LOC Android (`OrganizeTab.kt` Blockers section + `AddEditTaskViewModel.kt` add/remove handlers + 5 cycle-guard test cases on Android too — though the cycle guard already exists on Android). Total ~400-700 LOC across two platforms.

Strain: violates single-PR-per-branch (two platforms in one PR), and adds an editor surface on Android that the platform has not requested. Defensible only if operator says "yes, both surfaces, both platforms."

### Path 3 — Build editor-only on web, accept new cross-platform asymmetry

Rationale: ship the web Blockers picker in `TaskEditor.tsx` per the original prompt scope. Accept that this creates a new asymmetry (web has dependency editor on task editor; Android does not).

Drawback: directly inverts the prompt's own asymmetry-avoidance principle ("shipping editor-only on web would mirror the asymmetry we're closing on Android" — which presupposed Android was closing the gap; Android is not). This is the cheapest path in LOC but the most expensive in semantic drift between the two clients. It also pre-commits the web editor to a UI shape (editor-tab Blockers picker) that Android may later choose **not** to adopt, leaving the web editor stranded as the only place the surface exists.

### Operator decision required

Defaulting to **Path 1**. Phase 2 does NOT auto-fire. Awaiting operator confirmation:

> "Web TaskDependency parity wants the **roadmap** surface, not the editor surface. Recommend deferring F.8a until F.7 (web roadmap port) lands. Confirm path?"

---

## F. Implementation hypothesis (UNCHANGED — preserved for whichever path operator picks)

If operator picks **Path 1**: this section becomes the F.8a follow-up scope after F.7 lands.
If operator picks **Path 2** or **Path 3**: this section is the web-side scope. Android-side scope (Path 2 only) is enumerated separately in § E.

### F.1 Web type definition (verdict: GREEN)

```typescript
// web/src/types/taskDependency.ts
export interface TaskDependency {
  id: string;
  blockerTaskId: string;
  blockedTaskId: string;
  createdAt: number;
}
```

Mirrors Android `TaskDependencyEntity`. Field names match the Firestore document shape that `SyncMapper.taskDependencyToMap` writes:

```kotlin
"localId" to dependency.id,
"blockerTaskCloudId" to blockerTaskCloudId,
"blockedTaskCloudId" to blockedTaskCloudId,
"createdAt" to dependency.createdAt
```

**Important:** Firestore stores `blockerTaskCloudId` / `blockedTaskCloudId` (string cloud IDs), NOT Android's local `Long` IDs. On web, `Task.id` is already the Firestore document ID (per `web/src/api/firestore/tasks.ts:48`), so web naturally uses cloud IDs everywhere. No translation layer needed.

### F.2 Firestore sync layer (verdict: GREEN)

`web/src/api/firestore/taskDependencies.ts`:
- `listTaskDependencies(uid): Promise<TaskDependency[]>` — read all
- `subscribeTaskDependencies(uid, cb): Unsubscribe` — real-time listener (matches existing `web/src/api/firestore/tasks.ts:onSnapshot` pattern)
- `createTaskDependency(uid, blockerId, blockedId): Promise<string>` — addDoc, returns new doc ID
- `deleteTaskDependency(uid, depId): Promise<void>` — deleteDoc

No update API — edges are immutable per Android semantics. Mirror existing Firestore-direct pattern in `web/src/api/firestore/tasks.ts` and `web/src/api/firestore/tags.ts`.

### F.3 Cycle guard port (verdict: GREEN — direct port from Kotlin)

Direct line-by-line port of `DependencyCycleGuard.kt:31-66`:

```typescript
// web/src/utils/dependencyCycleGuard.ts
const MAX_DEPTH = 10_000;

export function wouldCreateCycle(
  edges: ReadonlyArray<{ blockerTaskId: string; blockedTaskId: string }>,
  blocker: string,
  blocked: string
): boolean {
  if (blocker === blocked) return true;
  if (edges.length === 0) return false;

  const outgoing = new Map<string, string[]>();
  for (const edge of edges) {
    const list = outgoing.get(edge.blockerTaskId) ?? [];
    list.push(edge.blockedTaskId);
    outgoing.set(edge.blockerTaskId, list);
  }

  const visited = new Set<string>();
  const stack: string[] = [blocked];
  let depth = 0;
  while (stack.length > 0) {
    depth++;
    if (depth > MAX_DEPTH) return false;
    const current = stack.pop()!;
    if (visited.has(current)) continue;
    visited.add(current);
    const children = outgoing.get(current);
    if (!children) continue;
    for (const child of children) {
      if (child === blocker) return true;
      stack.push(child);
    }
  }
  return false;
}
```

Test parity: 5 cases mirroring `app/src/test/java/.../DependencyCycleGuardTest.kt` (self-edge, two-node, transitive, diamond, accept).

### F.4 React component shape (verdict: depends on path)

- **Path 1:** `web/src/features/projects/ProjectRoadmapScreen.tsx` (new file, follow-up after F.7 lands) renders dependency lanes inside the roadmap, mirroring Android `ProjectRoadmapScreen.kt:463-495`.
- **Path 2 / Path 3:** Add Blockers section to `web/src/features/tasks/TaskEditor.tsx`, mirroring the existing tag-picker pattern at `TaskEditor.tsx:1071-1144`. Edit-mode-only (matches Android constraint that edges FK to saved task IDs).

### F.5 Test coverage (verdict: GREEN)

- Cycle guard: 5 cases (self-edge, two-node, transitive, diamond, accept) in `web/src/utils/__tests__/dependencyCycleGuard.test.ts`
- Firestore CRUD round-trip: in `web/src/api/firestore/__tests__/taskDependencies.test.ts`
- Component render tests: empty + populated state, depending on which surface lands

---

## G. Phase 2 scope (BLOCKED — awaiting operator)

Phase 2 would have written:

| File | LOC est | Path |
|---|---|---|
| `web/src/types/taskDependency.ts` | ~25 | all paths |
| `web/src/api/firestore/taskDependencies.ts` | ~80 | all paths |
| `web/src/utils/dependencyCycleGuard.ts` | ~45 | all paths |
| `web/src/utils/__tests__/dependencyCycleGuard.test.ts` | ~85 | all paths |
| `web/src/api/firestore/__tests__/taskDependencies.test.ts` | ~60 | all paths |
| `web/src/features/tasks/TaskEditor.tsx` (Blockers section) | ~150 | Paths 2, 3 |
| `web/src/features/projects/ProjectRoadmapScreen.tsx` | ~600 | Path 1 (full F.7 port) |
| `app/.../OrganizeTab.kt` (Blockers section) | ~200 | Path 2 only |
| `app/.../AddEditTaskViewModel.kt` (add/remove blocker) | ~100 | Path 2 only |

Total per path:
- **Path 1 (Phase 2 = wait for F.7):** ~295 LOC types/sync/guard, then ~600+ LOC F.7 elsewhere, then ~150 LOC dependency-on-roadmap follow-up
- **Path 2 (web + Android editor parity):** ~745 LOC across two platforms, plus tests on both
- **Path 3 (web editor only):** ~445 LOC, all on web

---

## H. Asymmetry analysis

The prompt's asymmetry-flag note is built on the false premise that Android closed an editor-roadmap asymmetry by adding the editor surface. Re-baselined:

- **Pre-PR-#1085 era:** no TaskDependency anywhere on either platform. Symmetric (both empty).
- **Post-PR-#1085 era (today):** Android has dependency UI on the roadmap. Web has nothing. Asymmetry = "Android has roadmap dependency UI; web has none."
- **Post-Path-1 ship:** F.7 ports roadmap to web. F.8a follow-up adds dependency lanes inside it. **Asymmetry closed cleanly.**
- **Post-Path-2 ship:** both platforms get editor-tab Blockers sections AND retain the roadmap surface (Android existing, web from a future F.7). Symmetric on both surfaces.
- **Post-Path-3 ship:** web has dependency UI on the editor; Android has it on the roadmap. **Cross-platform asymmetry maintained, just inverted.** Operator must accept this.

The prompt's "Default per Phase 1 evidence: probably Option C (sequencing)" instinct was right, but the rationale needs updating: the reason to sequence isn't "to preserve single-PR-per-branch convention while avoiding asymmetry" — it's "the actual Android source-of-truth surface for TaskDependency is the roadmap, so web parity needs the roadmap surface, full stop."

---

## I. Deferred (NOT auto-filed per memory #30)

If the operator picks Path 1, items to track later:

- F.7 web `ProjectRoadmapScreen.tsx` port (separate prompt; this audit cannot scope it)
- F.8a follow-up: add dependency lanes inside the new web roadmap (mirrors Android `ProjectRoadmapScreen.kt:463-495`)
- F.8b — per-task Phase picker on Android `OrganizeTab.kt` (independent from this audit; the prompt mentioned this but it's an Android-side gap, not a web-side gap)
- F.8c — "Dependents" view (no roadmap analog; independent from this audit)

If the operator picks Path 2, additional Android scope:

- Add Blockers section to `app/.../OrganizeTab.kt` (~200 LOC)
- Wire add/remove blocker handlers in `app/.../AddEditTaskViewModel.kt` (~100 LOC)
- Tests for the new editor-tab handlers (~5-10 cases)

---

## J. Open questions for operator

1. **Path choice.** Path 1 (defer until F.7 lands), Path 2 (web + Android editor parity), or Path 3 (web editor only, accept inverted asymmetry)?
2. **Source for the F.8a item itself.** The prompt cites "F.8a deferred from PR #1097." If F.8a is real (e.g., tracked in a private operator note), what was its actual filing context? Otherwise this F.8a item is itself questionable scope.
3. **Roadmap parity prerequisite.** Does the operator agree that the cleanest closure of cross-platform parity for TaskDependency UI is the **roadmap** surface, not the editor surface? If yes, Path 1 is the obvious recommendation; if no, Path 2 is the only way to close the gap symmetrically.

---

## K. Process notes (audit-first hard rule application)

Per the audit-first skill: "STOP-and-report on wrong premises is the one real halt. If a premise turns out wrong, stop and report rather than rationalizing scope."

Two premises were materially wrong (D.1 and the implicit "F.7 in-flight"). The cycle guard port, Firestore mapper, and 5-test parity remain perfectly tractable — but the **placement** of the resulting UI is what's contested, and that requires operator input.

This audit doc is the deliverable for Phase 1. Phase 2 is paused. Phase 3 (bundle summary) and Phase 4 (Claude Chat handoff) will be appended after operator decides path.

---

## Appendix — verbatim recon outputs

```
$ git log --all --oneline | grep -oE "#[0-9]+" | sort -t'#' -k2 -n | tail -5
#1085
#1090
#1091
#1093
#1094
```

```
$ grep -rn "blocker\|Blocker" app/src/main/java/com/averycorp/prismtask/ui/screens/addedittask/
(no output)
```

```
$ find web/src -iname "*roadmap*" -o -iname "*ependency*" -o -iname "*locker*"
(no output)
```

```
$ grep -rn "TaskDependency\|task_dependencies\|taskDependency" web/src/
(no output)
```

```
$ ls web/src/api/firestore/
__tests__  aiPreferences.ts  boundaryRules.ts  checkInLogs.ts
converters.ts  focusReleaseLogs.ts  habits.ts  index.ts
medicationPreferences.ts  medicationSlots.ts  medications.ts
moodEnergyLogs.ts  projects.ts  tags.ts  tasks.ts  userTemplates.ts
```

(no `taskDependencies.ts`)

```
$ grep -n "Blocker\|blocker\|dependency\|Dependency" \
    app/src/main/java/com/averycorp/prismtask/ui/screens/addedittask/tabs/OrganizeTab.kt
(no output)
```

---

## Phase 3 — Bundle summary (PRE-MERGE per CLAUDE.md)

### PRs opened
- **PR #1119** — `docs(audits): Phase 1 — Web TaskDependency parity (STOP-and-reframe)`
  - Branch: `claude/web-task-dependency-parity-hxjm0`
  - Commit: `1ec5b9c`
  - Net change: +393 lines, 1 file (`docs/audits/WEB_TASK_DEPENDENCY_PARITY_AUDIT.md`)
  - State: open, ready for review (not draft)

### PRs merged
None. Phase 2 paused per audit-first hard rule.

### Measured impact
- F.8a item closure: **0 → 0** (path not yet picked; cannot close without operator decision)
- Asymmetry status: **documented, not closed**
- Wall-clock saved by NOT shipping wrong scope: estimated 4-8 hours of code that would have shipped a stranded surface (Path 3 outcome)

### Memory entry candidates
- **Default:** no memory edit. The pattern "STOP-and-report on wrong premises" is already memory-encoded (audit-first hard rule).
- **Possible candidate:** capture the cycle-guard-port-from-Kotlin pattern as a durable template for future client-side validation ports — but only after Phase 2 actually ships and the pattern is validated.
- **Anti-pattern flag:** when a prompt cites a PR number, **always verify the PR exists** before scoping work that mirrors its claimed deliverable. The audit-first drive-by sweep (`git log -p -S`) catches this, but only if the PR-existence check is run early.

### Schedule for next audit
None scheduled. Re-audit only triggers if operator picks Path 1 (then a new prompt for F.7 web roadmap port is needed) or Path 2/3 (this prompt's Phase 2 unblocks).

---

## Phase 4 — Claude Chat handoff

See the fenced block at the end of the session output.

