# Schoolwork — edit/delete courses audit

**Date:** 2026-04-28
**Branch:** `docs/audits/schoolwork-course-edit-delete` (audit only — no Phase 2 fan-out)
**Scope (one line):** "There should be a way to edit and delete courses in Schoolwork mode."
**Verdict:** STOP — premise wrong. Both flows already shipped. One discoverability follow-up flagged for the user to triage.

## Item 1 — Edit and delete courses in Schoolwork mode (GREEN)

### Premise verification — WRONG

The user's premise is that edit and delete are missing. They are not. Both
exist in code today, on `main`:

| Flow | Where | File:line |
|---|---|---|
| Add course | `+` FAB on Schoolwork screen → `AddEditCourse` route (no id) | `SchoolworkScreen.kt:280-286`, `NavGraph.kt:147-150` |
| **Edit course** | Tap course card in "Courses & Assignments" → expand → "Edit" → `AddEditCourse` route with id | `SchoolworkScreen.kt:373-375`, `749-751` |
| **Delete course (trash icon)** | Daily checklist row trash icon → confirm dialog | `SchoolworkScreen.kt:563-570`, `387-409` |
| **Delete course (text button)** | Course management card → expand → "Delete" | `SchoolworkScreen.kt:752-758` |
| Persist edit | `repository.updateCourse(existing.copy(...))` | `AddEditCourseViewModel.kt:65-89`, `SchoolworkRepository.kt:52-55` |
| Persist delete | `repository.deleteCourse(id)` (sync-tracked) | `SchoolworkViewModel.kt:225-227`, `SchoolworkRepository.kt:57-60` |

The "Edit Course" / "Add Course" title swap on `AddEditCourseScreen` is
already present (`AddEditCourseScreen.kt:101`), and the
`AddEditCourseViewModel` correctly seeds the form from the course record
on edit (`AddEditCourseViewModel.kt:36-46`).

PR history corroborates: `00acdc11 feat(schoolwork): inline remove-course
button in Daily Course Work (#834)` added the daily-row trash icon. The
"Courses & Assignments" management card with Edit/Delete TextButtons has
been present since the schoolwork feature landed.

### Findings — discoverability friction (not a code gap)

There is a real UX subtlety hiding inside the wrong premise:

1. The top section of the Schoolwork screen, "Daily Course Work"
   (`SchoolworkScreen.kt:340-355`), only exposes a **trash icon** — no
   edit affordance. This is where the user spends most of their time.
2. The "Courses & Assignments" management section is rendered *below* the
   daily checklist (`SchoolworkScreen.kt:359-379`) and each course is
   **collapsed by default** — the user must scroll past the daily section
   AND tap a course card to expand it before Edit/Delete TextButtons
   appear. Nothing on the collapsed row signals that expanding reveals
   editing affordances.
3. With many courses (which is exactly the case the import flow produces)
   the daily section can fill the viewport and the management section is
   below the fold.

This is the most likely reason the user perceived edit/delete as
missing — they live one tap deeper than is obvious.

### Risk classification — GREEN (feature exists, fully wired)

The functional capability exists, persists correctly, and is sync-tracked.
There is no data-integrity or correctness risk.

### Recommendation — STOP-no-PR

Per the audit-first hard rule on wrong premises: stop and report. Do not
auto-fan-out Phase 2 PRs. The wrong premise is decisive: building "an edit
and delete flow" would duplicate code that already exists.

The discoverability follow-up below is a separate question. It should be a
deliberate UX call by the user, not auto-fired by this audit. Surfacing it
here, deferred, instead of treating it as a found PROCEED, because:

- The cost of guessing wrong is real (every UX nudge costs vertical space
  on a screen the user has already optimized).
- The user's exact words were "edit and delete" — not "make edit and
  delete more discoverable."
- If they meant the literal capability, this audit answers it (it
  exists). If they meant discoverability, they should pick from the
  options below rather than have me pick.

## Anti-patterns flagged (no fix recommended)

- `SchoolworkScreen.kt:345-353` and `:364-379` render the same `courses`
  list **twice** in the same `LazyColumn` — once as `CourseCheckItem`
  rows, once as `CourseCard` rows. This is intentional (different
  affordances per section), but it's worth knowing if either section
  evolves: changes to "the course row" must be made in both places.
  Not worth a refactor today; flagging for future drift.

## Improvement ranking (savings ÷ cost)

| # | Improvement | Estimated cost | Estimated savings | Ratio | Verdict |
|---|---|---|---|---|---|
| 1 | (DEFER — user decision) Add an Edit affordance to the daily checklist row, OR auto-expand the first course card in "Courses & Assignments", OR add an "Edit course" overflow menu to the daily row | 1–2 hr (Compose-only, no data-layer change) | Closes the "I can't find edit" perception that triggered this audit | High if the user confirms this was the real complaint; zero if not | DEFER pending user direction (see § "Open question" in Phase 4 handoff) |

Nothing else found — no other PROCEED, STOP-no-work-needed, or DEFER items.

---

## Phase 2 — Skipped

Premise was wrong; no Phase 2 PRs generated. If the user confirms the
discoverability follow-up is worth pursuing, that becomes its own scoped
task with normal Phase 1/2 treatment (or a one-off PR — it's small enough
either way).

## Phase 3 — Bundle summary

- **Merged PRs from this audit:** 0.
- **Re-baselined wall-clock estimate:** N/A.
- **Memory entry candidates:** none. This is a one-off discoverability
  observation — the lesson ("verify the feature really is missing before
  fanning out") is already covered by the existing audit-first hard rule
  on wrong premises.
- **Next audit:** none scheduled. If the user confirms the
  discoverability follow-up, scope it as its own task.
