# PrismTask-Timeline-Class — Phase 1 Audit (STOP)

**Scope**: extend `ProjectEntity` with five capabilities so PrismTask itself
can be managed as a project inside PrismTask: multi-phase grouping,
dependencies, risk levels, external anchors, per-item progress percent.

**Verdict**: **STOP and REFRAME**. Scope is feasible but not as a single
mega-PR — see Recommendation. Three premises (P4, P9, P10) reframed; one
process incident (duplicate `DayBoundary` objects in two packages); JSX
worst-case reference file is not in the repo so P11 worst-case is
estimated rather than verified.

**Audit baseline**: branch `claude/audit-prismtask-timeline-6w6jd`, head
`c97cbca` (`chore: bump to v1.8.22 (build 820)`). DB version 71
(`Migrations.kt:2142`). Date 2026-05-03; Phase F kickoff hard gate is
May 15. Audit recommends ship post-kickoff at v1.8.x (parallels WPR
landing at v1.8.18).

---

## P1 — `ProjectEntity` exists (GREEN)

`app/src/main/java/com/averycorp/prismtask/data/local/entity/ProjectEntity.kt:12`.
Fields enumerated: `id`, `cloudId`, `name`, `description`, `color`,
`icon`, `themeColorKey`, `status`, `startDate`, `endDate`, `completedAt`,
`archivedAt`, `createdAt`, `updatedAt`. Unique index on `cloud_id`.

Firestore mirror in `data/remote/mapper/SyncMapper.kt:123` (`projectToMap`)
emits 13 fields — same as the entity minus `cloud_id` (which IS the doc
key). Milestones live as a child collection per project
(`SyncMapper.kt:168`).

`MilestoneEntity` (`data/local/entity/MilestoneEntity.kt:31`): `id`,
`cloudId`, `projectId` (FK CASCADE), `title`, `isCompleted`,
`completedAt`, `orderIndex`, `createdAt`, `updatedAt`. Confirms the v1.4
Projects feature shipped milestones but not phases.

## P2 — Five capabilities not yet implemented (GREEN)

Searched `app/src/main/java/com/averycorp/prismtask` for
`phase_id|ProjectPhase|risk_level|RiskEntity|blocks_task|blocked_by|external_anchor|ExternalAnchor|progress_percent|progressPercent`.

Single false-positive: `TodayViewModel.kt:493` declares a UI-side
`val progressPercent: StateFlow<Float>` for the Today ring — this is a
computed completion ratio, not a per-task fractional progress field.
Five capabilities are confirmed greenfield.

## P3 — Dependency graph (YELLOW, 59 consumers)

`grep -rln 'ProjectEntity\b'` under `app/src/main/java/com/averycorp/prismtask`
returns **59 files**. Categorized:

- **Read-only readers (~38)**: filter panels, batch edit, project pickers,
  `MoveToProjectSheet`, `OrganizeTab`, search, today/week/month views,
  task-card chips. Schema-additive changes (new nullable columns) need
  zero changes here.
- **Writers (~12)**: `AddEditProjectViewModel`, `ProjectListViewModel`,
  `AddEditTaskViewModel`, `TimelineViewModel`, `TaskListViewModel`,
  `DataImporter`, `SyncService`, `SyncMapper`, `BackendSyncMappers`,
  `PrismTaskDatabase`, `CloudIdOrphanHealer`, `CanonicalOnboardingSync`.
  These need explicit handling for new fields in writes / mappers.
- **Computers (~9)**: backend `compute_project_burndown`
  (`backend/app/services/analytics.py:337`), web
  `utils/projectBurndown.ts:54`, `ProjectProgressPanel.tsx`,
  `TaskAnalyticsViewModel`, `MonthViewModel`, `WeekViewModel`,
  `MoodAnalyticsViewModel`, `analytics.py` router, `ProjectListViewModel`
  progress aggregator. The burndown family is the highest-risk consumer
  for any binary-vs-fractional change (see P9).

## P4 — Migration ordering (REFRAMED)

Scope assumed Cognitive Load mega-PR runs in parallel. **It has not
started.** `git log --all --oneline | grep -i "cognitive\|cog.?load"`
returns zero commits, no branches, no tags. No PR # is allocated against
that scope in `git log --all --oneline | head -200`.

Implication: this PR is unambiguously **v71 → v72**, with no
cross-session coordination required. The `app/build.gradle.kts:22` /
`Migrations.kt:2142` check the scope doc requires can be skipped — the
git evidence is decisive.

This is one of the three reframes; not a hard blocker but the scope
doc's process narrative around "two parallel sessions, coordinate via
`build.gradle.kts`" should be retired in operator memory (see Phase 4).

## P5 — Cycle detection precedent (GREEN)

`AutomationEngine.kt:73` (`handleEvent`) carries `lineage: Set<Long>`
through the recursive event handler; `AutomationEngine.kt:84-88` checks
`if (rule.id in lineage)` and aborts before re-firing. Pairs with
`MAX_CHAIN_DEPTH` depth cap (`AutomationEngine.kt:79-82`).

For project dependencies the equivalent pattern: when adding a "X blocks
Y" edge, walk the existing edges looking for Y → … → X. If found, reject
the write. Pure DFS over the dep table; no engine recursion needed.
Mirror, don't copy. Estimated: 25-line `DependencyCycleGuard` object,
~40-line unit test.

## P6 — Forgiveness composition (GREEN)

`DailyForgivenessStreakCore.kt:36` is a pure activity-set walker — takes
`Set<LocalDate>` of "met" days, returns `StreakResult`. Its design
(`DailyForgivenessStreakCore.kt:1-20`) explicitly anticipates project +
habit reuse.

Recommended composition for blocked tasks: **third state, not
streak-affecting**. A task whose dependency is unmet is neither overdue
(don't break streak) nor met (don't count). Surface as
`TaskState.BlockedByDependency` in the domain layer; the
`DailyForgivenessStreakCore` activity-date set is built by upstream
filters — those filters drop blocked tasks from BOTH the "met" and
"missed" pools. Pure additive at the call site of
`HabitRepository.calculateProjectStreak` and friends; zero changes
inside `DailyForgivenessStreakCore`.

## P7 — SoD usage (YELLOW, duplicate DayBoundary)

**Process incident**: two `DayBoundary` objects exist —
`util/DayBoundary.kt` (15 importers, the canonical one) and
`core/time/DayBoundary.kt` (3 importers, including the newly-landed
`QuickRescheduleFormatter.kt`, `DayBounds.kt`, and not yet imported
elsewhere). Both implement `startOfCurrentDay`. PR #1060 (BalanceTracker
SoD fix) used `util.DayBoundary`.

Phase 2 implementation rule: any phase-date / external-anchor-date logic
in this scope MUST use `util.DayBoundary.startOfCurrentDay(...)` to stay
consistent with the dominant import. The duplication itself is not in
scope here; flag for a separate cleanup PR (see Anti-Patterns).

## P8 — `ExternalAnchor` shape (GREEN, sealed class recommended)

Three variants: `CalendarDeadline(epochMs)`, `NumericThreshold(metric,
op, value)`, `BooleanGate(gateKey, expectedState)`.

Recommendation: **sealed class + Gson polymorphic adapter** mirroring
`AutomationJsonAdapter.kt:33-35` (registers `TriggerAdapter`,
`ConditionAdapter`, `ActionAdapter` with `type` discriminator at
`AutomationJsonAdapter.kt:96`). This keeps the storage layer to a single
`anchor_json: TEXT` column on the new `external_anchors` table without
union-type schema gymnastics. The Gson adapter pattern is already
proven (Automation Engine ships in production via PR #1056).

Tradeoff vs three separate entities: query patterns are mostly "all
anchors for project P" — uniform payload reads cleaner. The per-variant
field set is small enough that subclassing is overkill.

## P9 — Per-item progress percent (RED, cascade is wide)

`grep -rln '\.isCompleted'` returns **60+ consumer files** spanning
QuickAdd, Today, Tasklist, Subtasks, Search, MonthView, WeekView,
Archive, ProjectDetail, AddEdit, MorningCheckIn, MoodAnalytics,
HabitList, BuiltInHabit, settings, and the analytics + burndown family.

Scope's three options:

- **(a) Fractional only on tasks under projects, binary elsewhere** —
  smallest blast radius. Adds nullable `progress_percent INTEGER` (0-100,
  NULL = legacy/binary) on `tasks`. Burndown reads `progress_percent ?:
  (if isCompleted then 100 else 0)`. ~6 files actually need new
  branches; the other 54+ continue reading `isCompleted` unchanged.
  **Recommended.**
- **(b) Fractional everywhere with default 0/100** — touches every
  consumer for backwards compatibility wrappers. Cascade is the full
  60+. Out of scope for a single PR.
- **(c) Fractional only on phase-grouped tasks** — adds a "is this task
  in a phase?" check to every progress reader; couples P9 to the phase
  shape decision. Worst of both worlds.

Operator pick required at end of Phase 1. Audit recommends (a).

## P10 — Web parity / dogfood UI (REFRAMED)

`PrismTaskTimeline__1_.jsx` referenced in scope is NOT in the repo
(`find . -name 'PrismTaskTimeline*' -type f` returns empty). It's a
project-knowledge-only artifact that the audit cannot read.

Implication for option (c) — "migrate the JSX into the app as the
dogfood UI" — operator would need to upload the JSX into the repo first.
Without it, option (c) is undefined work, not estimable.

`web/src/features/calendar/TimelineScreen.tsx` already exists but is the
daily-time-block timeline (peer of the in-app `TimelineScreen.kt`), not
a phase-grouped Gantt. **Naming collision risk**: any new "PM timeline"
surface needs a different name (e.g., `ProjectRoadmapScreen`) to avoid
overloading "timeline". Process incident; flag in Phase 4.

Recommendation: **option (a) — field + sync + Firestore mirror only, no
new UI in this PR**. Matches WPR A6 precedent (PR #1061). Defer
view-mode UI to a follow-up PR; defer JSX migration until operator
uploads the file.

## P11 — Firestore quota (YELLOW, estimated)

Cannot read `PrismTaskTimeline__1_.jsx` (see P10). Estimating from the
prompt's description of "PrismTask's own timeline" (~12 phases × 5-15
items + 7-9 risks + ~10-20 anchors):

- ~120-180 phase items + ~15 phases + 9 risks + 20 anchors ≈ ~165-225
  documents if each becomes its own doc.
- A single project doc carrying all phases as an embedded array would
  be on the order of 50-150 KiB — well under the 1 MiB Firestore
  document limit. Embedding is feasible.
- However, Firestore writes the whole document on any field change.
  Embedded arrays mean every phase-item edit rewrites the entire
  project. With ~150 items in flight, that's ~150 KiB write per edit —
  costly under burst load.

Recommendation: **subcollection from day 1**.
`projects/{p}/phases/{ph}` as a subcollection; phase items as a flat
collection (`tasks`) with `phase_id` foreign key, like the existing
`milestones` mirror at `SyncMapper.kt:168`. Risks and external anchors
also as subcollections (`projects/{p}/risks/{r}`,
`projects/{p}/anchors/{a}`). No 1 MiB cliff, no rewrite-the-world
problem. Adds ~80 LOC to `SyncService.kt` push + pull paths per child
type.

## P12 — AI integration (DEFERRED)

Explicit out-of-scope per scope doc. Defer to Phase G. Audit confirms no
scaffold hooks needed in this PR — `domain/automation/` is already
event-bus-driven (`AutomationEvent.kt`); a future
`AutomationEvent.ProjectPhaseAdvanced` event slots into the existing
sealed class without schema changes.

---

## Investigation items 1-12 — short answers

1. P1+P2 verified above.
2. P3 dependency graph: 59 files, 38 readers / 12 writers / 9 computers.
3. **Burndown** (`backend/app/services/analytics.py:337-415` +
   `web/src/utils/projectBurndown.ts:54`): pure binary `status == DONE`
   check. Adding fractional progress requires either (a) update
   burndown to read `progress_percent` with binary fallback, or (b)
   keep binary as burndown source-of-truth. Recommended (a) with a
   backwards-compatible `completed_cumulative` shape: `sum(p_i / 100)`
   with `p_i = 100` for legacy `isCompleted = true` rows. Web + backend
   need synchronized changes.
4. **Project templates** (`ProjectTemplateEntity.kt:25` + web
   `userTemplates.ts`): orthogonal to ProjectEntity per the entity
   docstring. Templates do NOT need a version bump unless a future
   feature wants templates that pre-set phases. Out of scope here.
5. **Cycle detection precedent**: `AutomationEngine.kt:73-86` lineage +
   depth cap. Mirror as `DependencyCycleGuard` DFS. ~25 LOC + tests.
6. **Forgiveness composition**: `TaskState.BlockedByDependency` third
   state in domain layer; upstream filters drop blocked tasks from
   activity-date sets. Zero changes in `DailyForgivenessStreakCore`.
7. **`ExternalAnchor` sealed class** with `CalendarDeadline` /
   `NumericThreshold` / `BooleanGate` variants + Gson polymorphic
   adapter mirroring `AutomationJsonAdapter.kt:33-35`. Single
   `anchor_json TEXT` column. ~150 LOC including tests.
8. **Per-item progress percent**: option (a) recommended. Adds nullable
   `progress_percent INTEGER` on `tasks`; ~6 files actually need
   branches.
9. **Phase shape**: column `current_phase_id` on Project + child
   `ProjectPhaseEntity` table with FK CASCADE (mirrors
   `MilestoneEntity` from PR's earlier work). Per-phase items via
   `tasks.phase_id` FK (additive nullable column). NOT a join table —
   phases are owned by exactly one project.
10. **Schema migration plan** (rollback shown after main):

```kotlin
val MIGRATION_71_72 = object : Migration(71, 72) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Project phases
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `project_phases` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `cloud_id` TEXT,
              `project_id` INTEGER NOT NULL,
              `title` TEXT NOT NULL,
              `description` TEXT,
              `color_key` TEXT,
              `start_date` INTEGER,
              `end_date` INTEGER,
              `version_anchor` TEXT,
              `version_note` TEXT,
              `order_index` INTEGER NOT NULL DEFAULT 0,
              `created_at` INTEGER NOT NULL,
              `updated_at` INTEGER NOT NULL,
              FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_project_phases_cloud_id` ON `project_phases` (`cloud_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_phases_project_id` ON `project_phases` (`project_id`)")

        // Risk register
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `project_risks` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `cloud_id` TEXT,
              `project_id` INTEGER NOT NULL,
              `title` TEXT NOT NULL,
              `level` TEXT NOT NULL,             -- LOW / MEDIUM / HIGH
              `mitigation` TEXT,
              `resolved_at` INTEGER,
              `created_at` INTEGER NOT NULL,
              `updated_at` INTEGER NOT NULL,
              FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_project_risks_cloud_id` ON `project_risks` (`cloud_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_risks_project_id` ON `project_risks` (`project_id`)")

        // External anchors (calendar / numeric / boolean — sealed class via JSON)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `external_anchors` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `cloud_id` TEXT,
              `project_id` INTEGER NOT NULL,
              `phase_id` INTEGER,
              `label` TEXT NOT NULL,
              `anchor_json` TEXT NOT NULL,       -- ExternalAnchor sealed class payload
              `created_at` INTEGER NOT NULL,
              `updated_at` INTEGER NOT NULL,
              FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE,
              FOREIGN KEY(`phase_id`) REFERENCES `project_phases`(`id`)
                ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_external_anchors_cloud_id` ON `external_anchors` (`cloud_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_external_anchors_project_id` ON `external_anchors` (`project_id`)")

        // Task dependencies (X blocks Y)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `task_dependencies` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `cloud_id` TEXT,
              `blocker_task_id` INTEGER NOT NULL,
              `blocked_task_id` INTEGER NOT NULL,
              `created_at` INTEGER NOT NULL,
              FOREIGN KEY(`blocker_task_id`) REFERENCES `tasks`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE,
              FOREIGN KEY(`blocked_task_id`) REFERENCES `tasks`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_task_dependencies_pair` ON `task_dependencies` (`blocker_task_id`, `blocked_task_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_dependencies_blocked_task_id` ON `task_dependencies` (`blocked_task_id`)")

        // Per-task fractional progress (P9 option a)
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `progress_percent` INTEGER")
        // Phase membership (nullable; legacy tasks unaffected)
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `phase_id` INTEGER REFERENCES `project_phases`(`id`) ON DELETE SET NULL")
    }
}
```

   Rollback strategy (no `MIGRATION_72_71` because Room migrations are
   forward-only): if the migration corrupts a tester's device, the
   recovery path is the same as PR #1056 — fall back to the destructive
   rebuild (`fallbackToDestructiveMigration`) gated by a one-time
   rebuild flag in `BuiltInSyncPreferences`. Tester data restores from
   Firestore on next pull. Phase 2 PR description must spell out this
   recovery path explicitly.

11. **Web parity scope**: option (a) — field + sync + Firestore mirror,
    no new UI in this PR. Defer view-mode + JSX dogfood (option b/c) to
    follow-up. Rationale: scope is already 4-PR-stack territory; adding
    web UI doubles the surface.

12. **Firestore quota analysis**: subcollection-from-day-1 strategy.
    `projects/{p}/phases/{ph}`, `projects/{p}/risks/{r}`,
    `projects/{p}/anchors/{a}`, `projects/{p}/dependencies/{d}`. Mirror
    the milestones precedent (`SyncMapper.kt:168`).

---

## Recommendation — Mega-PR scope discipline

The 5-capability ship target is operator's preferred outcome but, per
the scope doc's own escape hatch, "not the contract." Reference data
points:

- WPR (PR #1061): 1221 insertions, 22 files, 1 ALTER COLUMN.
- Automation Engine (PR #1056): 3285 insertions, 44 files, 2 new tables.
- This scope: 4 new tables + 2 ALTER COLUMNs + 4 sync mirror payloads +
  domain models + cycle guard + sealed class + burndown algorithm
  update. Lower bound estimate: **3500-5000 insertions**, ~50 files.

Single-PR ship is at-or-above Automation Engine's ceiling. Audit
recommends a **4-PR stack**, all on `claude/audit-prismtask-timeline-*`
worktrees, sequenced:

| # | Title | Schema | Est LOC |
|---|---|---|---|
| 1 | Phases + risks foundation (entities, DAOs, repos, sync mirror, DI) | `project_phases` + `project_risks` + 2 `tasks` ALTERs (`phase_id`, `progress_percent`) | ~1400 |
| 2 | Task dependencies + cycle guard + `BlockedByDependency` state | `task_dependencies` | ~900 |
| 3 | External anchors sealed class + Gson adapter + sync mirror | `external_anchors` | ~700 |
| 4 | Burndown + analytics update for fractional progress (web + backend) | none | ~600 |

Phase F kickoff (May 15) gate: PR-1 + PR-3 are achievable by kickoff;
PR-2 + PR-4 ship post-kickoff at v1.8.x. **Kickoff gate is hard; the
operator's escape clause is taken.**

Operator overrides required before Phase 2 fan-out can fire:

- **O1**: Confirm 4-PR stack vs single mega-PR (audit recommends 4-PR).
- **O2**: P9 option a/b/c (audit recommends a).
- **O3**: P10 option a/b/c (audit recommends a).
- **O4**: Confirm post-kickoff ship at v1.8.x (audit recommends yes).

Phase 2 has zero PROCEED items until O1-O4 are answered. The audit doc
itself is the deliverable for this session.

---

## Ranked improvement table (wall-clock-savings ÷ implementation-cost)

| Rank | Item | Savings | Cost | Notes |
|---|---|---|---|---|
| 1 | Lock in subcollection-from-day-1 (P11) | High — avoids 1 MiB cliff refactor later | Low — design-time decision | Costs nothing now; saves 2-3 days if hit later |
| 2 | Pick P9 option (a) | High — keeps cascade to ~6 files instead of 60+ | Low — design-time decision | Single biggest scope-reducer |
| 3 | 4-PR stack vs single mega | High — each PR independently reviewable | Medium — coordination overhead | Mirrors WPR/Automation precedent |
| 4 | Sealed class `ExternalAnchor` + Gson adapter | Medium — single column, single sync mirror | Low — pattern is proven (PR #1056) | Avoids 3-entity proliferation |
| 5 | `TaskState.BlockedByDependency` third state | Medium — clean streak/score composition | Low — pure-domain layer addition | Avoids touching `DailyForgivenessStreakCore` |
| 6 | Defer JSX dogfood UI until file uploaded | Medium — undefined work removed from scope | Zero | File is project-knowledge-only |

## Anti-patterns flagged (not necessarily fixed in this PR)

- **Duplicate `DayBoundary` objects** (`util/DayBoundary.kt` and
  `core/time/DayBoundary.kt`) — separate cleanup PR. This audit's
  Phase 2 implementations must use `util.DayBoundary` (15 importers,
  PR #1060 precedent) and not introduce new `core.time` imports.
- **Naming collision** — existing in-app `TimelineScreen.kt` is a daily
  time-block view; web `TimelineScreen.tsx` mirrors it. Any phase-Gantt
  surface must use a distinct name (`ProjectRoadmapScreen` proposed).
- **`ProjectTemplateEntity` vs the v1.4 Projects feature** — already
  documented as orthogonal in
  `ProjectTemplateEntity.kt:15-21`. Audit confirmed; no action needed.
- **Scope-doc claim "Cognitive Load mega-PR is running in parallel"** —
  no git evidence (P4). Update operator memory to drop the
  cross-session coordination assumption for this scope.

---

## Phase 2 status

**Skipped this session.** All 12 investigation items return STOP-and-
reframe (4 require operator overrides O1-O4) or DEFER (P12 explicitly
out-of-scope). Per `/audit-first` rules, Phase 2 fires automatically
*for PROCEED items only*; with zero PROCEED items, the audit doc is the
deliverable. Phase 3 + Phase 4 close out below.

## Phase 3 — Bundle summary

No PRs merged this session. Audit doc is the artifact.

**Memory entry candidates** (only the surprising / non-obvious):

- The Cognitive Load mega-PR scope reference in operator's prompts is
  not yet started — for any new scope that claims "the X mega-PR is
  running in parallel," verify with `git log --all` before relying on
  the cross-session coordination model.
- `util.DayBoundary` vs `core.time.DayBoundary` duplication exists and
  is already drifting (3 importers on the new path, 15 on the old).
  Cleanup PR is overdue.
- `PrismTaskTimeline__1_.jsx` is a project-knowledge artifact, not a
  repo file. Any audit/scope that depends on its contents must surface
  this as a blocker (option-c paths cannot be costed without it).

**Schedule for next audit**: after operator answers O1-O4, re-run
`/audit-first` against PR-1 (phases + risks foundation) as a focused
500-line audit with full schema + sync + DAO test plan. Estimated audit
write time: ~30 min.

