# Analytics C4 / C5 — Time-Tracking Design Follow-Up

**Parent audit**: `docs/audits/ANALYTICS_PR715_PORT_AUDIT.md` (Subset C)
**Date**: 2026-04-28
**Trigger**: Premise refinement #3 surfaced during slice 3 implementation —
Android Room has no `actualDuration` data anywhere. C4 (time-tracking
aggregator) and C5 (time-tracking bar chart) are blocked.
**Status**: design doc only, no code changes.

## Premise refinement detail

The parent audit assumed Android had per-task time-tracking equivalent to
the backend's `Task.actual_duration` column. Investigation confirmed:

- **No `actual_minutes` / `actualDuration` column** on `TaskEntity`. Only
  `estimated_duration: Int?` exists.
- **No `task_timings` / `pomodoro_session` / `work_session` Room entity**.
  No `*Timing*` / `*Session*` files under `data/local/entity/`.
- **`PomodoroTimerService` runs** but does not persist session completions.
- **`PomodoroSessionResponse`** in `data/remote/api/ApiModels.kt` is a
  *remote*-API model (AI-suggested sessions from `/ai/pomodoro/plan`), not a
  logged history.
- **`CLAUDE.md` "time tracking per task"** line in the baseline-features
  paragraph is aspirational — no entity backs it.

C4's job is to aggregate `actual_minutes` by project / tag / priority / day
with accuracy comparison vs. estimate. Without source data, the aggregator
returns empty rows and the chart shows only "no data."

## Three viable paths

### Path 1 — Add `actual_minutes: Int?` column to `TaskEntity`

**Architecture**: single nullable column on the existing `tasks` table.
Manual entry via a new "Log time" sheet on the task editor. No session
history.

**Implementation**:
- Migration N → N+1: `ALTER TABLE tasks ADD COLUMN actual_minutes INTEGER`
- `TaskEntity.actualMinutes: Int?`
- `TaskRepository.updateActualMinutes(taskId, minutes)` + sync surface (one
  more column in the sync push payload)
- "Log time" UI: `+15m` / `+30m` / `+1h` / custom chip row on the editor;
  writes to `actualMinutes`.
- Unit tests for migration + repository
- Tier B snapshot test for "Log time" sheet (per edge-case audit)

**LOC est**: 250-400. **Wall-clock**: 2-3 days standalone.

**Pros**:
- Minimal schema change (one column)
- Cheapest path to unblock C4/C5
- C4/C5 plug in directly

**Cons**:
- No history (can't see WHEN time was logged — just the cumulative total)
- No granularity (one user can't have multiple sessions per task)
- Last-write-wins on edits (destructive)
- Pomodoro auto-log integration is awkward (would have to sum into the
  scalar column, losing per-session info)

### Path 2 — New `TaskTimingEntity` table

**Architecture**: proper time-tracking entity. New table:

```sql
CREATE TABLE task_timings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cloud_id TEXT UNIQUE,
    task_id INTEGER NOT NULL,
    started_at INTEGER,        -- nullable for manual log-only
    ended_at INTEGER,          -- nullable for manual log-only
    duration_minutes INTEGER NOT NULL,
    source TEXT,               -- 'manual' | 'pomodoro' | 'timer'
    notes TEXT,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);
CREATE INDEX idx_task_timings_task_id ON task_timings(task_id);
CREATE INDEX idx_task_timings_created_at ON task_timings(created_at);
```

Multiple entries per task. `PomodoroTimerService` can auto-insert on session
end. Manual "Log time" inserts one entry.

**Implementation**:
- Migration: `CREATE TABLE` + indices
- `TaskTimingEntity` + `TaskTimingDao` + `TaskTimingRepository`
- Sync surface: 10th entity for `SyncService` (`task_timing`) + sync-tracker
- "Log time" UI sheet (insert manual entry)
- `PomodoroTimerService` integration (insert on stop) — net-new
- DAO query for aggregations: `SELECT project_id, SUM(duration_minutes) FROM task_timings JOIN tasks ON ... GROUP BY ...`
- Tests: DAO + sync-roundtrip + UI snapshot

**LOC est**: 800-1500. **Wall-clock**: 4-6 days standalone.

**Pros**:
- Session history visible (can show timeline / list of work intervals)
- Pomodoro auto-population path is clean
- Per-session edits / deletes possible
- DB-level aggregations are trivial via `GROUP BY`
- Survives the "user switches devices" sync question correctly

**Cons**:
- Substantial migration + new DAO surface
- Sync surface expansion (10th entity) — touches the cross-device sync code
  the Phase F gate is currently YELLOW on (Test 3 re-verification pending)
- 4-6 days wall-clock is a feature in itself, not a slice of an analytics port

### Path 3 — Defer C4/C5 entirely (recommended)

**Decision**: ship only C1 + C2 + C3 to Phase F. Defer C4/C5 to a sprint
after time-tracking ships as its own focused feature.

**Effective Subset C delivery**:
- ✅ C1 — summary tile row (merged, PR #897)
- 🚧 C2 — productivity domain (PR #898 open, auto-merge enabled)
- 🚧 C3 — productivity chart (PR #899 open, auto-merge enabled)
- ❌ C4 — DEFERRED
- ❌ C5 — DEFERRED

Subset C effectively becomes "Subset B+ with summary tile row" — three
PRs covering the highest-value web parity items (summary header, daily
productivity chart with trend). Time-tracking remains a Phase I item with
this design doc as the architectural plan.

**LOC est**: 0 added. **Wall-clock**: 0.

**Pros**:
- Zero Phase F risk addition
- Time-tracking gets its own design + review cycle (it's a feature, not a
  port slice)
- Phase F gate stays GREEN-pending-Test-3 instead of YELLOW (no sync-surface
  expansion)
- When time-tracking ships, C4 + C5 become a clean 2-3 day port (aggregator
  + chart only)

**Cons**:
- Subset C scope reduces from 5 PRs to 3 PRs
- Time-tracking bar chart from web PR #715 is missing on Android until a
  later sprint

## Wall-clock fit vs Phase F window

| Path | C4+C5 effort | Total Phase F effort | Window fit |
|---|---|---|---|
| Path 1 (actual_minutes col) | 4-5 d (data + UI + C4 + C5) | ~13-15 d cumulative (incl. C1-C3 already in flight) | RED — exceeds ~10d available after Test 3 + medication buffer |
| Path 2 (TaskTimingEntity) | 7-9 d | ~16-18 d cumulative | RED — exceeds window |
| Path 3 (DEFER) | 0 d | ~9-10 d cumulative | GREEN — fits with buffer |

## Recommendation

**Path 3 — Defer C4/C5 entirely.**

Reasons:

1. **Phase F window is RED already** per the parent audit. Adding 4-9 days
   of *net-new data-layer + UI* (not a port — a feature) blows the budget.
2. **Time-tracking is its own feature, not a port slice.** Pulling it
   piggyback under analytics buries its design under analytics scope. It
   deserves its own audit / brainstorm / design pass.
3. **Subset B+ is already a meaningful Phase F deliverable.** C1+C2+C3 cover
   the summary header + productivity score chart — the highest-value web
   parity items. Time-tracking is the third-most-important feature and
   skipping it doesn't undermine the launch.
4. **Sync surface stability.** Phase F gate is YELLOW pending Test 3. Path 2
   adds a 10th sync entity which directly touches that surface. Risky.
5. **Future cost is small.** When time-tracking ships separately, C4 + C5
   collapse to ~2-3 days of port work (the data layer is already in place;
   only aggregator + chart remain).

## If proceeding anyway — minimal-risk path

If user wants time-tracking + C4 + C5 in Phase F regardless:

- **Pick Path 1** (single column, no session history). Cheapest unblock.
- **Skip Pomodoro auto-log.** Manual "Log time" only — defer auto-log to
  post-Phase F.
- **Bundle C4 + C5 + the data-layer change in ONE PR**, not three. Saves
  ~30 min of CI cycles per round. Justified because they're a tightly
  coupled scope (no value in shipping the data column without the chart, or
  the aggregator without the UI).
- **No new sync entity.** Add `actual_minutes` to the existing task sync
  payload — minimal sync-surface delta.
- **Tier A property-based test** for the aggregator (per edge-case audit
  pattern). Skip Tier B snapshot test for the chart UI (defer per memory
  feedback_skip_audit_checkpoints — Tier B isn't blocking).

Under that compressed shape, C4+C5 can land in 3-4 days bundled. Still
RED-tight against Phase F, but at least mathematically possible.

## C4 / C5 design (whichever option lands)

Once time-tracking data exists, the implementation plan is:

### C4 — `TimeTrackingAggregator` use case

```kotlin
enum class TimeTrackingGroupBy { PROJECT, TAG, PRIORITY, DAY }

data class TimeTrackingEntry(
    val groupName: String,
    val totalMinutes: Int,
    val taskCount: Int,
    val avgMinutesPerTask: Double,
    val estimatedTotal: Int,
    val accuracyPct: Double,
)

data class TimeTrackingResponse(
    val entries: List<TimeTrackingEntry>,
    val totalTrackedMinutes: Int,
    val totalEstimatedMinutes: Int,
    val overallAccuracyPct: Double,
    val mostTimeConsumingProject: String?,
    val mostAccurateEstimates: String?,
)

@Singleton
class TimeTrackingAggregator @Inject constructor() {
    fun compute(
        startDate: LocalDate,
        endDate: LocalDate,
        zone: ZoneId,
        groupBy: TimeTrackingGroupBy,
        // Path 1: completedTasks: List<TaskEntity>  (with actualMinutes)
        // Path 2: timings: List<TaskTimingEntity> joined with tasks
        completedTasks: List<TaskEntity>,
        projectsById: Map<Long, ProjectEntity>,
        tagsByTaskId: Map<Long, List<String>>,
    ): TimeTrackingResponse
}
```

Mirror `backend/app/services/analytics.py::compute_time_tracking_stats`
exactly: same accuracy formula `100 - min(|actual - est| * 100 / est, 100)`,
same `most_time_consuming_project` / `most_accurate_estimates` selection.

### C5 — `TimeTrackingSection` composable

- Header: total tracked + accuracy summary + most-time-consuming-project
- Group-by filter chips: `Project / Tag / Priority / Day`
- Compose Canvas bar chart: one bar per group, side-by-side estimated vs
  actual. Per-bar accuracy coloring (`≥80% → success` green, `≥50% → accent`,
  `else → destructive` red).
- Tap-bar tooltip with formatted minutes (`${h}h ${m}m`).

LOC est (post time-tracking data layer):
- C4 aggregator: 200-350 LOC + tests
- C5 chart UI: 350-550 LOC

## Decision matrix

| # | Option | Phase F LOC | Wall-clock | Phase F fit | Phase I cost |
|---|---|---|---|---|---|
| 1 | Path 3 — DEFER C4/C5 | 0 | 0 | GREEN | 2-3 d (port only) |
| 2 | Path 1 — actual_minutes col + bundled C4+C5 | 700-1100 | 3-4 d | RED-tight | minimal |
| 3 | Path 1 — actual_minutes col, separate PRs | 700-1100 | 4-5 d | RED-exceed | minimal |
| 4 | Path 2 — TaskTimingEntity | 1500-2500 | 7-9 d | RED-exceed | minimal |

**Ranked by `Phase-F-value × Phase-F-fit ÷ risk`:**

1. **Path 3 (DEFER)** — best ratio, only loses Subset C scope marginally
2. **Path 1 bundled** — only viable if user accepts RED-tight schedule risk
3. **Path 1 separate** — strictly worse than bundled (more CI cycles)
4. **Path 2** — only if user prioritizes long-term architecture over Phase F

## Outstanding questions for user

1. Is time-tracking on the Phase F launch surface a hard requirement, or
   nice-to-have?
2. If user picks Path 1: is "Log time" a Pro feature, free feature, or
   tier-aware? (web has `time_tracking` under analytics which is Pro-gated;
   the action of *logging* time isn't currently gated.)
3. Pomodoro session auto-log: should it ship in the same PR or as a follow-up?
   (My pick: follow-up — Pomodoro changes touch a different code surface and
   shouldn't bundle with analytics.)
