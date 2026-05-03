# Cognitive Load (Easy/Challenge Balance) — Phase 1 Audit

**Scope:** Verify the 10 premises (P1–P10) and 10 investigation items for a
proposed *new orthogonal task dimension* — start-friction / cognitive-load-
to-start — peer to TaskMode (PR #1061) and forgiveness-first.

**Branch:** `claude/audit-cognitive-load-wRkKu`
**Date:** 2026-05-03
**Repo HEAD:** `c97cbca chore: bump to v1.8.22 (build 820)`
**App version on `main`:** `1.8.22` (`app/build.gradle.kts:22`)
**Room version on `main`:** `CURRENT_DB_VERSION = 71` (`Migrations.kt:2142`)

---

## TL;DR — PROCEED (3-tier default)

**Verdict: PROCEED. Phase 2 fires.**

All ten premises survive contact with `main`. PR #1061 (TaskMode, merged
2026-05-02) is a near-perfect template — Cognitive Load is the same
mega-PR shape with a different enum, different keyword tables, and a
different tie-break order. The only audit-time decision the operator
genuinely owns is the tier count (P8): the audit recommends **3-tier
(EASY / MEDIUM / HARD)** as it minimizes copy-paste cost vs PR #1061
and mirrors TaskMode's `WORK / PLAY / RELAX / UNCATEGORIZED` shape
exactly. Mid-stream pivot to 2/4/continuous costs only the enum +
keyword tables; everything else (column, migration, classifier scaffold,
balance tracker, NLP hashtag plumbing, selector UI, web parity, tests)
is invariant.

Two design calls deferred to the implementation PR's commit messages:

1. **Bidirectional imbalance surfacing (P4).** TaskMode is purely
   descriptive; LifeCategory has `isOverloaded` + `OverloadCheckWorker`.
   Audit recommends the **TaskMode descriptive-only shape** for v1:
   surface the bar, let the user read both directions, no notifications.
   `CognitiveLoadOverloadCheckWorker` is an additive follow-up if user
   research justifies it. Matches WPR's "describes the split, does not
   prescribe one" rule.

2. **Cross-axis tracker generalization (item 5).** `BalanceTracker` and
   `ModeBalanceTracker` are nearly identical. Audit considered a
   generalized `MultiAxisBalanceTracker<T : Enum<T>>` but **recommends
   against refactoring inside this PR**: WPR set the three-parallel-
   trackers precedent eight days ago; refactor blast radius is high.
   Defer to its own PR if the maintenance cost ever bites.

**Process incidents flagged for Phase 4:**
- PM mega-PR mentioned in brief is not visible on `main` HEAD. If it
  lands v71→v72 first, Cognitive Load becomes v72→v73; cross-session
  coordination not coupled per operator.
- Two `DayBoundary.kt` files exist (`util/` and `core/time/`); both
  referenced from production. Out of scope, flagging only.

---

## Premise verification

### P1 — TaskEntity has no cognitive-load column today (GREEN)

`TaskEntity.kt` (126 lines, 25 columns) has dimensions for **importance**
(`priority: Int`), **time** (`estimated_duration: Int?`,
`scheduled_start_time`), **life domain** (`life_category: String?`,
line 95), **reward type** (`task_mode: String?`, line 105), and
**urgency × importance** (`eisenhower_quadrant`, line 74). None capture
*how much friction stands between the user and starting* — the closest
field is `priority`, but priority is "should I prioritize this?" not
"how hard is it to begin?". A 30-second "send tax doc to accountant" is
low priority but high friction; a 45-minute "watch the show I love" is
no priority but zero friction. Confirmed: cognitive load is genuinely
greenfield.

### P2 — Orthogonal to LifeCategory, TaskMode, Eisenhower (GREEN)

See § Orthogonality matrix below. The 4-axis classification of eight
representative tasks (including the prompt's "draft a difficult email
to a recommender" pivot) does not collapse any two axes. No RED finding
of the WPR-Premise-4 shape.

### P3 — "Bridge" framing is narrative-only; formal = start-friction (GREEN)

The bridge framing (Eisenhower asks should-I; WPR asks what-back; CL
asks how-hard-to-start) is a memorable narrative hook. The **formal
definition is load-bearing for orthogonality** — if implementors drift
back to "bridge" as canonical, the dimension stops being independent of
its endpoints. Audit guards this by: column name `cognitive_load`; enum
name `CognitiveLoad`; formal start-friction definition first in
`docs/COGNITIVE_LOAD.md` with the bridge framing in a § "Why this
dimension" sidebar.

### P4 — Bidirectional imbalance is the failure mode (YELLOW — design call)

On `main`: TaskMode is **descriptive-only** (no overload / worker /
notification — `docs/WORK_PLAY_RELAX.md:65–69`). LifeCategory is
**prescriptive** (`BalanceState.isOverloaded`, `OverloadCheckWorker`
daily 4 PM, quiet-hours-aware notification when WORK over target).

Prompt's P4 reads prescriptive ("all-easy = procrastination, all-hard
= burnout = failures") but frames CL as a peer to TaskMode (descriptive).
**Audit recommends descriptive-only in v1**: WPR's "describes the split,
does not prescribe one" rule cross-applies; notifying "you're
procrastinating" is the guilt-by-app failure forgiveness-first prevents;
threshold picking without user research is fragile. The bar leans red
visually on lopsidedness — signal without notification. Adding a worker
later is additive, non-breaking, no migration. YELLOW because the
literal P4 reads more prescriptive — surface to operator if they want
LifeCategory's shape instead.

### P5 — Forgiveness-first composition (GREEN, no core change)

`DailyForgivenessStreakCore.kt:36–40` takes `Set<LocalDate>`,
`LocalDate today`, `ForgivenessConfig` — zero entity-dimension
awareness. A streak that breaks because the user did 4 easy instead of
2 easy + 2 hard is structurally impossible (the load dimension never
enters the function). Per-mode strictness was deferred from PR #1061;
**same deferral here**. If per-load strictness is wanted later, it
composes at the call-site picking the `ForgivenessConfig`. No core
refactor needed.

### P6 — SoD-aware via DayBoundary (GREEN)

PR #1060 (`c9113b1`) fixed `BalanceTracker.cutoff()` to respect
`dayStartHour` / `dayStartMinute`. `ModeBalanceTracker` (PR #1061,
`77e1291`) inherited the fix. `CognitiveLoadBalanceTracker` mirrors
`cutoff(...)` verbatim except for the enum it ratios over.

### P7 — Migration v71 → v72 (GREEN, with caveat)

`Migrations.kt:2142` confirms `CURRENT_DB_VERSION = 71`. Template is
`MIGRATION_70_71` (lines 2123–2140, 17 lines). **Caveat:** PM mega-PR
mentioned in brief is not visible on `main`. If PM lands v71→v72
first, this PR re-bases to v72→v73 — only the migration body / KDoc /
`ALL_MIGRATIONS` registration change. No semantic conflict.

### P8 — Tier count deferred to audit (GREEN — recommendation made)

See § Tier count tradeoff table below. Audit recommends **3-tier
(EASY / MEDIUM / HARD + UNCATEGORIZED)** as the default for Phase 2.
Mid-stream pivot to 2/4/continuous costs only the enum + keyword tables
+ tests; everything else (column, migration, classifier scaffold,
balance tracker, NLP, UI selector, web parity) is invariant.

### P9 — Classifier scaffold + tie-break EASY > MEDIUM > HARD (GREEN)

Mirrors `TaskModeClassifier`'s `RELAX > PLAY > WORK` shape:
"never inflate the prescriptive read when ambiguous". For TaskMode that
means "don't fabricate Work obligations". For Cognitive Load it means
"don't make a task look harder than it is, because over-classifying as
HARD triggers procrastination preemptively". Sound, mirrors precedent.

`TaskModeClassifier.kt:69–70` is the literal line to clone:
```
private val TIE_BREAK_ORDER: List<TaskMode> =
    listOf(TaskMode.RELAX, TaskMode.PLAY, TaskMode.WORK)
```
becomes:
```
private val TIE_BREAK_ORDER: List<CognitiveLoad> =
    listOf(CognitiveLoad.EASY, CognitiveLoad.MEDIUM, CognitiveLoad.HARD)
```

### P10 — Web parity: field + sync + Firestore mirror, no UI editor (GREEN)

`web/src/types/task.ts:107–108` already declares
`TaskMode = 'WORK' | 'PLAY' | 'RELAX' | 'UNCATEGORIZED'`.
`web/src/api/firestore/tasks.ts:95–106` declares `parseTaskMode(...)`
with case-list validation; lines 75 / 185–187 / 248–249 round-trip the
field with omit-on-null semantics to avoid clobbering Android-side state
(parity bug PR #836 lesson). Same exact shape for `CognitiveLoad` —
adds a type alias, a `parseCognitiveLoad(...)`, three round-trip lines.
**No web UI editor**, per the WPR A6 deferral pattern documented in
`docs/audits/WORK_PLAY_RELAX_AUDIT.md` Premise 6.

---

## Investigation items

### Item 1 — Verify P1 by elimination (GREEN)

Closest existing fields and why each fails the start-friction definition:

| Field on `TaskEntity`        | What it captures                  | Why ≠ cognitive load        |
|------------------------------|-----------------------------------|------------------------------|
| `priority: Int` (0–4)         | "Should I do this first?"        | Importance, not friction     |
| `estimated_duration: Int?`    | Time commitment in minutes        | A 2-min task can be hi-friction |
| `eisenhower_quadrant`         | Urgency × importance grid         | Doesn't ask "easy to start?" |
| `life_category`               | Subject domain (Work / Health …)  | Friction is dimension-free   |
| `task_mode`                   | Reward type (output / fun / rest) | Mode is reward; load is start |

### Item 2 — Orthogonality matrix (GREEN)

Eight representative tasks, four axes:

| # | Task                                              | LifeCategory | TaskMode | Eisenhower    | CognitiveLoad |
|---|---------------------------------------------------|--------------|----------|---------------|---------------|
| 1 | "Draft difficult email to recommender"           | PERSONAL     | WORK     | Q2 (imp/¬urg) | HARD          |
| 2 | "Reply 'thanks' to mom's text"                    | PERSONAL     | WORK     | Q4 (¬imp/¬urg)| EASY          |
| 3 | "30-min PT knee exercises"                       | HEALTH       | WORK     | Q2            | MEDIUM        |
| 4 | "Pickup basketball with friends"                  | HEALTH       | PLAY     | Q4            | EASY          |
| 5 | "Pay overdue electric bill (5 min, dread it)"    | PERSONAL     | WORK     | Q1 (imp/urg)  | HARD          |
| 6 | "Two-week vacation flight booking"               | PERSONAL     | WORK     | Q2            | HARD          |
| 7 | "10-min morning stretch"                         | SELF_CARE    | RELAX    | Q4            | EASY          |
| 8 | "Start writing the novel"                         | PERSONAL     | PLAY     | Q2            | HARD          |

No two columns are functions of each other. Tasks 2 and 5 share
LifeCategory + TaskMode + Eisenhower (¬urg-but-Q1 differs trivially);
they differ only in CognitiveLoad. Tasks 6 and 8 share everything except
TaskMode (Work vs Play) and LifeCategory choice. The dimension is
genuinely independent.

### Item 3 — DailyForgivenessStreakCore composability (GREEN)

`DailyForgivenessStreakCore.kt:36–40` signature takes
`Set<LocalDate>`, `LocalDate today`, `ForgivenessConfig`. No reference
to entity, dimension, or task fields. **Zero changes needed** for
Cognitive Load streak composition. Per-load strictness (analogous to
WPR's per-mode strictness, deferred from PR #1061) is itself deferred —
audit doc only, not auto-filed as a timeline item.

### Item 4 — Classifier shape (GREEN, exact clone)

`TaskModeClassifier.kt` is 105 lines. `LifeCategoryClassifier.kt` shares
the same shape. Cognitive Load classifier is a character-substitution
clone:

- Class name: `CognitiveLoadClassifier`
- Map: `Map<CognitiveLoad, List<String>>`
- Tie-break: `[EASY, MEDIUM, HARD]`
- `withCustomKeywords(custom: CognitiveLoadCustomKeywords)`: same
  three-CSV pattern as `TaskModeCustomKeywords`

Default keyword starter set (from `docs/COGNITIVE_LOAD.md`, mirroring
WPR's vocab list):

- **Easy:** quick, brief, simple, send, reply, confirm, check, glance,
  skim, file, archive, clean, tidy, clear, sort, dust, water,
  refill, restock
- **Medium:** review, edit, compose, draft, organize, prepare, plan,
  schedule, book, register, pay, log, summarize, transcribe
- **Hard:** start, write, create, build, design, research, decide,
  call, negotiate, confront, learn, debug, refactor, investigate,
  diagnose, finish-the, draft-difficult, present

Note "finish-the" / "draft-difficult" are intentionally hyphenated
multi-word seeds (the classifier's `containsWholeWord(...)` already
handles hyphenated tokens via the dash-as-non-letter rule at
`TaskModeClassifier.kt:62`).

### Item 5 — `CognitiveLoadBalanceTracker` composition (GREEN, no refactor)

`ModeBalanceTracker.kt` (145 lines) is the verbatim template.
`BalanceTracker.kt` (181 lines) is the prior precedent.
`CognitiveLoadBalanceTracker` adds a *third* parallel tracker.

Audit considered generalizing to
`MultiAxisBalanceTracker<T : Enum<T>>` with `tracked: List<T>`,
`fromStorage: (String?) -> T`, `extract: (TaskEntity) -> T`. This would
collapse three classes (~470 LOC total) into one (~200 LOC + per-axis
adapters). **Recommended against in this PR**:

- WPR set the three-parallel-trackers precedent on 2026-05-02. Refactor
  blast radius is non-trivial (callers in `TodayViewModel`,
  `MorningCheckInViewModel`, `WeeklyBalanceReportViewModel`,
  `OverloadCheckWorker`).
- `BalanceTracker` carries `isOverloaded` (line 24) which `ModeBalanceTracker`
  does not. A single generic class would have to express overload as
  optional-per-axis, which adds API surface that only one of three
  axes uses.
- "Don't refactor on the way through" — surfaced for operator's own
  refactor PR if maintenance cost bites later.

### Item 6 — Migration LOC estimate + template path (GREEN)

`MIGRATION_70_71` is exactly 17 lines (`Migrations.kt:2123–2140`). The
v71→v72 clone is also ~17 lines:

```kotlin
val MIGRATION_71_72 = object : Migration(71, 72) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `tasks` ADD COLUMN `cognitive_load` TEXT")
    }
}
```

Plus: bump `CURRENT_DB_VERSION` 71→72, append `MIGRATION_71_72` to
`ALL_MIGRATIONS`, and add `Migration71To72Test` (instrumentation,
~80 LOC, mirrors `Migration70To71Test` 1:1).

**Default value:** `NULL` (which `CognitiveLoad.fromStorage(...)` reads
as `UNCATEGORIZED`). No retroactive auto-classification — same rule as
PR #1061.

### Item 7 — Firestore sync mirror (GREEN, exact pattern)

PR #1061's web changes (verified in working tree):

- `web/src/types/task.ts`: lines 38–41 (`task_mode?` field), 70–76
  (`taskMode?` on Create), 99–100 (Update), 107–108 (type alias).
- `web/src/api/firestore/tasks.ts`: line 17 (import), 75
  (`task_mode: parseTaskMode(...)` on read), 95–106 (`parseTaskMode`
  function), 139–140 (`taskMode?` in Create signature), 183–187
  (`doc.taskMode = ...` on Create with omit-on-null), 248–249
  (Update). Total: ~25 added lines on web, single file.

Cognitive Load mirrors lines-for-lines. **One-to-one substitution**.

### Item 8 — Tier count tradeoff table

| Tiers | Shape                            | Pros                                     | Cons                                              | LOC vs PR #1061 baseline |
|-------|----------------------------------|------------------------------------------|---------------------------------------------------|---------------------------|
| 2     | EASY / HARD (+ UNCATEGORIZED)    | Smallest UI; matches "Easy/Challenge" framing name; binary classifier easiest to AI-infer | Loses MEDIUM nuance; balance bar is two-state (lopsided or balanced); doesn't mirror WPR's 3-tier — every parallel surface diverges | −10% (one fewer keyword bucket, fewer chips) |
| **3** | **EASY / MEDIUM / HARD (+ UNC.)** | **Perfect template clone of WPR; even 1/3 target split; tie-break EASY>MEDIUM>HARD reads cleanly; classifier output type identical to TaskMode** | **Slightly more UX surface than 2-tier; "MEDIUM" can feel unmotivated UX-wise — but "what if a task is mid-friction?" is a real UX answer** | **~100% (perfect clone)** |
| 4     | TRIVIAL / EASY / MEDIUM / HARD   | Adds "I'll do it on autopilot" tier; matches medication's 4-tier UI from PR #832 | Even target = 25% which is awkward (no natural "ideal mix"); chip count grows; classifier vocab grows; web type alias grows | +15% (extra bucket) |
| Cont. | 1–5 slider                       | Maximum granularity; matches `priority`'s 0–4 scale | Slider not chips → new UI primitive; classifier output is regression not classification → inference path 2× harder; balance ratio over 5 buckets is visually noisy; web type stops being a discriminated union | +40% (new UI primitive, classifier rework, balance display rework) |

**Recommendation: 3-tier.** Lowest copy-paste cost vs PR #1061; gives
the user EASY / MEDIUM / HARD which maps onto every existing balance
surface without redesign; tie-break EASY>MEDIUM>HARD is naturally
intelligible. Phase 2 fires with 3-tier unless the operator
course-corrects mid-stream.

### Item 9 — Web parity scope (GREEN)

Same as Item 7: type alias + parser + omit-on-null write on Create &
Update. Two files, ~25 lines added. No editor UI surface.

### Item 10 — Backend AI inference deferred (GREEN)

`backend/app/middleware/ai_gate.py:30` defines
`require_ai_features_enabled` (PR #788/#790; line-of-defense for direct
API callers and the future iOS client). `Depends(require_ai_features_enabled)`
is the wire-up pattern. Cognitive Load AI inference is **deferred to a
follow-up backend PR** — audit doc only, not auto-filed. The on-device
classifier scaffold lands in this PR; the backend route lands when
operator scopes it. Defer-minimization principle (memory #30) honored.

---

## Phase 2 PR plan (mega-PR, single coherent scope)

Mirrors PR #1061's shape exactly. Estimated total diff: ~600–800 net
added lines, ~10 added files, ~10 touched files.

**New files:**
- `app/.../domain/model/CognitiveLoad.kt` (~50 LOC, enum + companion)
- `app/.../domain/usecase/CognitiveLoadClassifier.kt` (~105 LOC)
- `app/.../domain/usecase/CognitiveLoadBalanceTracker.kt` (~145 LOC)
- `app/.../ui/theme/CognitiveLoadColors.kt` (~25 LOC)
- `app/src/test/.../CognitiveLoadClassifierTest.kt` (~120 LOC)
- `app/src/test/.../CognitiveLoadBalanceTrackerTest.kt` (~120 LOC)
- `app/src/androidTest/.../Migration71To72Test.kt` (~80 LOC)
- `docs/COGNITIVE_LOAD.md` (~150 LOC philosophy doc, mirrors WPR)

**Touched files (additive only):**
- `data/local/database/Migrations.kt` — `MIGRATION_71_72`,
  `CURRENT_DB_VERSION = 72`, append to `ALL_MIGRATIONS`.
- `data/local/entity/TaskEntity.kt` — new `cognitive_load: String?`
  column with KDoc.
- `data/preferences/AdvancedTuningPreferences.kt` —
  `CognitiveLoadCustomKeywords` data class + Flow + setter.
- `domain/usecase/NaturalLanguageParser.kt` — `#easy-load` /
  `#medium-load` / `#hard-load` hashtag regex + `ParsedTask.cognitiveLoad`.
- `domain/usecase/ParsedTaskResolver.kt` — round-trip the new field.
- `data/repository/TaskRepository.kt` — `addTask(...)` gains a
  defaulted `cognitiveLoad: String? = null` parameter.
- `ui/screens/addedittask/AddEditTaskViewModel.kt` — `cognitiveLoad`
  state, `cognitiveLoadManuallySet`, `onCognitiveLoadChange(...)`,
  `resolveCognitiveLoadForSave()`, load + save + reset wiring.
- `ui/screens/addedittask/tabs/OrganizeTab.kt` — `CognitiveLoadSelector`
  composable (Auto + 3 chips).
- `data/export/DataExporter.kt`, `DataImporter.kt` — round-trip
  `cognitiveLoadKeywords` next to `taskModeKeywords`.
- `web/src/types/task.ts` — `CognitiveLoad` type alias + 3 field
  declarations.
- `web/src/api/firestore/tasks.ts` — `parseCognitiveLoad(...)` + read +
  Create/Update writes.

**Out of scope this PR (deferred, audit doc only):**
- Backend Haiku route + `Depends(require_ai_features_enabled)` wire-up.
- Web UI editor surface (mirrors WPR A6 deferral).
- `CognitiveLoadOverloadCheckWorker` (descriptive-only in v1 per P4).
- Per-load streak strictness defaults composing with
  `DailyForgivenessStreakCore` (mirrors WPR's deferred per-mode
  strictness).
- Habit / project / medication classification by load (TaskEntity-only
  this PR, mirrors PR #1061 scoping decision).
- `CognitiveLoad` chip on widgets, Today balance bar, weekly report
  screen — these are the natural follow-ups but each adds UX surface
  that should be designed once load data exists in the DB.
- `MultiAxisBalanceTracker<T>` refactor — operator's own PR if /
  when it bites.

---

## Wrong-premise summary

| # | Premise (paraphrased)                                  | Status   |
|---|--------------------------------------------------------|----------|
| 1 | TaskEntity has no cognitive-load column                  | GREEN |
| 2 | Orthogonal to LifeCategory / TaskMode / Eisenhower       | GREEN (matrix above) |
| 3 | Bridge framing is narrative-only; formal = start-friction| GREEN (audit guards drift) |
| 4 | Bidirectional imbalance prevention                       | YELLOW (recommend descriptive-only v1) |
| 5 | Forgiveness-first composition without core change        | GREEN |
| 6 | SoD-aware via DayBoundary / PR #1060 pattern             | GREEN |
| 7 | Migration v71→v72 (PM mega-PR may reorder)               | GREEN (caveat flagged) |
| 8 | Tier count deferred — audit recommendation made          | GREEN (3-tier) |
| 9 | Tie-break EASY > MEDIUM > HARD                           | GREEN |
| 10| Web field + sync, no UI editor; backend route deferred   | GREEN |

---

## Recommendation matrix

Sorted by wall-clock-savings ÷ implementation-cost, descending.

| # | Item                                                                                  | Cost  | Savings/Value                                              | Recommendation |
|---|---------------------------------------------------------------------------------------|-------|------------------------------------------------------------|----------------|
| 1 | Ship Cognitive Load mega-PR mirroring PR #1061 with 3-tier EASY/MEDIUM/HARD            | medium (~600–800 LOC, mostly clone) | New orthogonal axis with no future-proofing debt; Phase F window slack | **PROCEED (Phase 2 fires)** |
| 2 | Surface bidirectional-imbalance design call (descriptive vs prescriptive) to operator  | trivial (this audit) | Avoids guilt-by-notification regression                     | **PROCEED** (this doc) |
| 3 | Defer `CognitiveLoadOverloadCheckWorker` until user research                           | n/a | Smaller PR, additive later                                  | **DEFER** (audit doc only) |
| 4 | Defer per-load streak strictness composing with `DailyForgivenessStreakCore`           | n/a | Mirrors WPR per-mode-strictness deferral                    | **DEFER** (audit doc only) |
| 5 | Defer backend Haiku route + AI gate wire-up                                            | n/a | Mirrors PR #1061 backend deferral                           | **DEFER** (audit doc only) |
| 6 | Defer habit / project / medication per-load tagging                                    | n/a | TaskEntity-only mirrors PR #1061                            | **DEFER** (audit doc only) |
| 7 | Defer `MultiAxisBalanceTracker<T>` refactor                                            | n/a | Three duplications small enough; refactor blast radius high | **DEFER** (operator-owned) |
| 8 | Defer web UI editor (read/write field only ships)                                      | n/a | Mirrors WPR A6 precedent                                    | **DEFER** (audit doc only) |
| 9 | Coordinate v71→v72 ordering with PM mega-PR                                            | n/a | Cross-session not coupled per operator                      | **FLAG** (process incident) |

---

## Anti-patterns surfaced (worth flagging, not necessarily fixing)

- The prompt re-derives audit boilerplate (skip-checkpoints, defer-
  minimization, Phase 4 summary structure) inline rather than
  referencing the existing `audit-first` skill. This is fine when the
  scope diverges from the skill — but here the prompt's process is
  identical to the skill's defaults. Future scope docs can shrink ~15
  lines by referencing the skill instead of restating it.
- The prompt's P4 frames bidirectional imbalance as "the failure mode",
  which reads more prescriptive than the recommended descriptive-only
  landing. WPR's "describes, does not prescribe" rule is load-bearing
  and cross-applies — the audit reframes rather than rationalizing.
- "Bridge between Eisenhower and WPR" is a genuinely good narrative
  framing for `docs/COGNITIVE_LOAD.md`'s opening hook, but if it leaks
  into the formal definition (the column comment, the enum KDoc, the
  `parseCognitiveLoad(...)` doc), the dimension stops being independent.
  The implementation PR should land the formal definition first and
  defer the bridge framing to the philosophy doc only.
- Two `DayBoundary.kt` files exist (`util/` and `core/time/`). Out of
  scope for this audit, but worth a focused dedup PR before Phase F if
  another audit notices it.

---

## Phase 2 status

**FIRING.** Single mega-PR per the WPR template:

- Branch: `claude/audit-cognitive-load-wRkKu` (already this branch)
- Squash-merge auto-merge via `gh pr merge --auto --squash`
- Required CI green
- No `[skip ci]` in commit messages
- Tier count: **3-tier (EASY / MEDIUM / HARD + UNCATEGORIZED)** unless
  operator course-corrects mid-stream
- Surfacing: descriptive-only (TaskMode shape, not LifeCategory
  prescriptive shape) for v1
- Defers documented above; not auto-filed as timeline items per
  defer-minimization principle

---

## Phase 3 — bundle summary

(Appended pre-merge per CLAUDE.md § Repo conventions
"Audit-first Phase 3 + 4 fire pre-merge".)

**PRs opened in this session:**

| PR | Title | LOC | Scope vs original prompt | CI status (at handoff) |
|----|-------|-----|---------------------------|-------------------------|
| #1084 | feat(load): Cognitive Load — Phase 1 audit + EASY/MEDIUM/HARD orthogonal task dimension + un-defer follow-ons | ≈+2,300 net adds across 33 files (audit + impl + un-defer batch) | Single mega-PR. Original scope: TaskEntity-only, descriptive-only v1, 3-tier EASY/MEDIUM/HARD with tie-break EASY>MEDIUM>HARD, web field+sync (no UI editor), no backend Haiku route, no overload worker. **Operator override mid-PR ("nothing deferred if possible") expanded scope to land six previously-deferred items in additional commits — see § Operator overrides during Phase 1 → Phase 2 transition.** | Pending — multi-batch CI runs across audit-only, impl, and un-defer commits. |

**Measured impact:** not measurable until merge (no migration, classifier,
or balance computation runs in production until v1.8.x ships). The
columns + classifier + tracker + selector all default to UNCATEGORIZED
on first roll-out, so existing users see zero behavior change until
they manually tag tasks.

**Re-baselined wall-clock-per-PR estimate:** the WPR template (PR #1059
audit + PR #1061 implementation) shipped over two days. Cognitive Load
shipped audit + implementation in a single session because the template
was already in place — confirms the "second copy of an established
shape costs ~half the first" heuristic. Future orthogonal-dimension
PRs (e.g. an Energy / Time-of-Day / Mood axis if scoped) should plan
on the same single-session shape.

**Operator overrides during Phase 1 → Phase 2 transition:**

1. "**Phase 3 + 4 fire pre-merge from now on, every time.**" Captured in
   `CLAUDE.md § Repo conventions`. Override of audit-first skill default.
2. "**I want nothing deferred if possible.**" Mid-PR scope expansion.
   Landed six previously-deferred items in a follow-on commit:
   - `/ai/cognitive-load/classify_text` backend Haiku route + AI gate.
   - `CognitiveLoadOverloadCheckWorker` (bidirectional prescriptive,
     0.80 threshold per tier — flips audit P4 design call).
   - `TodayCognitiveLoadSection` + Today screen wiring.
   - Weekly Balance Report load section.
   - `TaskEditor.tsx` web UI editor (Cognitive Load `<select>`).
   - `NotificationWorkerScheduler` co-scheduling alongside the
     LifeCategory overload worker.

   Items still deferred *with explicit reasoning surfaced to operator:*
   - **`DayBoundary.kt` dedup** — mid-flight 17-file API migration with
     semantic difference (millis legacy vs LocalDate canonical); the
     codebase explicitly notes the migration is "incremental". Not a
     same-PR un-defer.
   - **`MultiAxisBalanceTracker<T>` refactor** — audit explicitly
     recommended against (refactor blast radius mid-feature). Operator
     override would override the audit's own recommendation; flagged
     for confirmation rather than land.
   - **Habit / project / medication per-load classification** — none of
     the three entities carry ANY orthogonal axis today. Pioneering the
     axis across three entity hierarchies in one PR would be multi-day
     work with high regression risk on built-in habit reconciliation,
     project-detail joins, and medication slot logic.
   - **Per-load streak strictness** — streaks live on habits/projects,
     not tasks. Blocked on the habit-classification defer above.

**Memory entry candidates:**

1. **Audit-first Phase 3 + 4 pre-merge override** — captured in
   `CLAUDE.md` § Repo conventions. Pre-merge handoff is now standard.
2. **Mega-PR template clones cost ~half the original.** When PR #N
   establishes a parallel-axis pattern (orthogonal column + classifier +
   balance tracker + NLP + UI selector + web parity), PR #N+1 cloning
   the same shape with a different enum is closer to a same-session
   landing than a multi-day landing. Anti-pattern: scoping such a clone
   as a multi-PR fan-out — it expands wall-clock without de-risking,
   because the template is already proven.
3. **Operator-override defer-minimization.** "I want nothing deferred"
   is a meaningful override of the audit-first skill's defer-minimization
   default. The right response is to triage defers into "feature defer
   landable in this session" vs "refactor / pioneering scope explicitly
   recommended against by the audit". Land the first set; surface the
   second with reasoning rather than auto-overriding the audit.

**Schedule for next audit:** none currently scoped. The defer list
below seeds Phase F+1 candidates if the operator wants follow-ups.

**Defers post-operator-override (audit doc only — not auto-filed as
timeline items per defer-minimization principle):**

- ~~Backend Haiku route~~ — **landed** as `/ai/cognitive-load/classify_text`.
- ~~Web UI editor~~ — **landed** in `TaskEditor.tsx` (`<select>` next to
  Life Category picker).
- ~~`CognitiveLoadOverloadCheckWorker`~~ — **landed** (bidirectional 0.80
  threshold; flips audit P4 to prescriptive per operator override).
- ~~Today balance bar / weekly report load section~~ — **landed** in
  `TodayCognitiveLoadSection` + `WeeklyBalanceReportScreen.CognitiveLoadSection`.
- **Habit / project / medication classification by load** — still
  deferred. None of the three entities carries any orthogonal axis
  today; multi-entity pioneering scope.
- **Per-load streak strictness** — still deferred. Blocked on
  habit/project per-load defer (streaks live on those entities).
- **`MultiAxisBalanceTracker<T>` refactor** — still deferred (audit
  recommended against; refactor blast radius mid-feature).
- **`DayBoundary.kt` dedup** — still deferred (in-progress 17-file API
  migration with semantic difference between millis and LocalDate
  layers; explicitly incremental in the codebase's own KDoc).

**Phase F kickoff impact:** Cognitive Load ships before the May 15
kickoff if PR #1084 merges in the next 12 days. If CI flakes or
review surfaces a re-scope, falls back to v1.8.x post-kickoff at the
same shape WPR landed (v1.8.18, post-its-own-audit-STOP override).

---

## Phase 4 — Claude Chat handoff summary

(Emitted at PR-open time per CLAUDE.md § Repo conventions
"Audit-first Phase 3 + 4 fire pre-merge". Block printed in this
session's terminal; copy-paste into a fresh Claude.ai conversation
to pick up the thread cold.)
