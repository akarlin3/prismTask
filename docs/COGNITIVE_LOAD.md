# Cognitive Load — start-friction philosophy

PrismTask classifies every task on **four** independent dimensions:
**Life Category** (what is this task *about*?), **Task Mode** (what
does it *give the user back*?), **Eisenhower Quadrant** (urgency ×
importance — *should I do this?*), and **Cognitive Load** (how hard
is it *to start*?).

This doc is about cognitive load. It exists so that when we add a
chip, write a prompt, draw a chart, or copy a setting, we share one
definition of what these labels mean.

---

## The three loads

| Load       | Means                                          | Examples                                                            |
|------------|------------------------------------------------|----------------------------------------------------------------------|
| **Easy**   | Low start-friction. The user can begin without a deliberate decision. | Reply "thanks" to a text, archive yesterday's drafts, water the plant |
| **Medium** | Moderate start-friction. Needs a beat of attention before starting. | Review the PR, compose the standup notes, prepare the meeting agenda |
| **Hard**   | High start-friction. Begin*ning* is the work — the activity itself may be short or long. | Draft the difficult email, debug the listener leak, start the novel  |

A fourth value — **Uncategorized** — is the default for any task the
user hasn't tagged and the auto-classifier hasn't matched.
Uncategorized tasks behave the same as today in lists and counts but
do not contribute to load-balance ratios. (Same shape as
`LifeCategory.UNCATEGORIZED` and `TaskMode.UNCATEGORIZED`.)

## The formal definition is start-friction

Cognitive Load measures the friction *between the user and starting
the task*, not the duration of the task, not its importance, not its
reward type. **The bridge framing below is a narrative hook for new
readers — the formal definition is start-friction.**

A 30-second "send tax doc to accountant" can be HARD if it carries
dread. A 90-minute "watch the show I love" can be EASY because there
is zero friction to beginning.

## Why this dimension (the bridge — narrative only)

Eisenhower asks *should I do this?* Work/Play/Relax asks *what does
this give me back?* Cognitive Load asks *how hard is it to start?*

The bridge framing is useful for explaining the dimension to new
users. It is **not** the formal definition. If the bridge framing
leaks into the canonical definition (the column comment, the enum
KDoc, the API doc), the dimension stops being independent of its
endpoints. Treat the bridge as marketing copy; treat start-friction
as semantics.

## Cognitive Load is orthogonal to the other axes

A task carries all four dimensions. None is derived from another.
Examples:

| Title                              | Life category | Mode  | Eisenhower    | Load    | Why                                                    |
|------------------------------------|---------------|-------|---------------|---------|--------------------------------------------------------|
| Reply "thanks" to mom's text       | PERSONAL      | WORK  | Q4            | EASY    | Trivial start; subject = personal; produces output     |
| Draft difficult email to recommender | PERSONAL    | WORK  | Q2            | HARD    | Dread is the work; subject = personal; output          |
| Pickup basketball with friends     | HEALTH        | PLAY  | Q4            | EASY    | Social, recurring, no decision needed                  |
| Start writing the novel            | PERSONAL      | PLAY  | Q2            | HARD    | Beginning is the work; subject = personal; enjoyment   |
| Pay overdue electric bill          | PERSONAL      | WORK  | Q1            | HARD    | 5 min, but high friction even when urgent              |
| 30-min PT knee exercises           | HEALTH        | WORK  | Q2            | MEDIUM  | Routine but requires getting changed and starting      |
| 10-min morning stretch             | SELF_CARE     | RELAX | Q4            | EASY    | Routine, low friction                                  |

The product team uses this matrix when writing copy: *"is this thing
about a category, a mode, a quadrant, or a load?"*. Never collapse
two columns.

## What load is **not**

- **Not duration.** A short task can be HARD and a long one EASY.
- **Not importance.** Eisenhower's already that.
- **Not difficulty of the activity.** Climbing a 5.10 route is
  effortful — but if the user is already at the gym, beginning is
  EASY.
- **Not a recommendation.** PrismTask describes the split. It does
  not lecture the user about adding more EASY or HARD tasks.
- **Not a permanent identity.** A task tagged HARD today can become
  EASY tomorrow once the user has started a draft. The classifier
  always defers to the user's latest manual override.

## Descriptive, not prescriptive

PrismTask renders cognitive-load ratios and trends. It does not
notify the user about their balance.

- ✅ "Your week was 70% Easy, 20% Medium, 10% Hard."
- ✅ "Tomorrow you have 2 Hard tasks scheduled and 4 Easy tasks."
- ❌ "You're procrastinating — schedule a Hard task."
- ❌ "Too much Hard this week — pick an Easy task."

Bidirectional imbalance is real (all-easy = procrastination via
avoidance; all-hard = burnout via no recovery wins) — but the
*signal* is the bar, not a notification. The user reads "this week
was 90% easy" and decides. PrismTask never lectures about the
direction.

This rule applies everywhere load appears: balance bar, weekly
report, AI coaching, widget surfaces, settings copy. If a copy
change reads as a value judgment about the user's load split, rewrite
it.

## Forgiveness-first composition

Cognitive Load streaks (if they ship later) compose with the existing
forgiveness-first streak core (see
[`FORGIVENESS_FIRST.md`](FORGIVENESS_FIRST.md)). The
`DailyForgivenessStreakCore` is dimension-agnostic — it takes a plain
`Set<LocalDate>` of activity days. A streak that breaks because the
user did 4 easy tasks instead of 2 easy + 2 hard would be a
regression; the core's signature makes that regression structurally
impossible.

Per-load strictness defaults (analogous to mode-aware streak
strictness in `WORK_PLAY_RELAX.md` § Streak strictness) are
**deferred** — the v1 PR ships the column, classifier, balance
tracker, NLP, UI selector, and web parity, but no streak hooks.

## Inference rules (auto-classifier)

The cognitive-load auto-classifier follows the same shape as
`TaskModeClassifier` / `LifeCategoryClassifier`: keyword-based,
offline, with user-supplied custom keywords from Settings → Advanced
Tuning. When the AI Features toggle is on, a future Claude Haiku NLP
path can also suggest a load from title + notes; the suggestion is
always overrideable.

Default keyword vocabulary (starter set; expandable via custom
keywords):

- **Easy:** quick, brief, simple, reply, confirm, check, glance,
  skim, archive, clean, tidy, clear, sort, dust, water, refill,
  restock, trivial
- **Medium:** review, edit, compose, draft, organize, prepare,
  schedule, book, register, log, summarize, transcribe, tidy-up,
  follow-up
- **Hard:** start, create, build, design, research, decide,
  negotiate, confront, debug, refactor, investigate, diagnose,
  present, interview, rewrite, difficult, tough, blocker, refuse,
  complicated

When two loads tie on keyword count, the classifier prefers
**Easy → Medium → Hard** in that order. This is the "never inflate
difficulty" bias — over-classifying as HARD triggers procrastination
preemptively. Mirrors `TaskModeClassifier`'s
`RELAX > PLAY > WORK` "lean toward the restorative read" bias on the
inverted axis.

## NLP hashtags

Load hashtags use a `-load` suffix to avoid colliding with the
existing LifeCategory hashtags (`#work` etc) and TaskMode hashtags
(`#work-mode` etc):

- `#easy-load`
- `#medium-load`
- `#hard-load`

A task's text can carry all three dimensions:
`Pickup basketball #health #play-mode #easy-load`.

## Ratio surfacing

`CognitiveLoadBalance` is computed the same way as `BalanceTracker`
and `ModeBalanceTracker`:

- Window: last 7 days for the current ratio, last 28 days for rolling.
- Day boundary: SoD-aware (after the v1.8 `BalanceTracker` SoD fix in
  PR #1060).
- Excluded: tasks with load = Uncategorized, tasks before the cutoff.
- Output: ratio per load (sums to ~1.0), total tracked count, dominant
  load.

The Today balance bar's load section (when added) shows load
alongside life category and mode. The Weekly Balance Report adds a
load section beneath the existing category and mode sections. This
v1 PR ships the column + sync + tracker + selector but **does not**
add the bar / report UI surfaces — those come in a follow-up once
load data exists in the DB.

## Defaults & migration semantics

- Existing tasks have load = `null` (Uncategorized) after migration.
  No retroactive classification.
- Auto-classification only applies to new tasks created after the
  feature ships, so a user's archived history is not silently
  re-tagged.
- The user can manually tag any historical task at any time.
- The Today balance bar's load section will be hidden until the user
  has tagged at least one task with a load (mirrors how the
  LifeCategory and TaskMode bars gate on `totalTracked > 0`).

## What this doc does not cover

- The schema layout, migration shape, and exact column types are in
  the implementation PR's commit messages and `Migrations.kt` KDoc.
- Picker UI placement and copy strings live in the implementation
  PR's diff.
- AI prompt templates would live in `backend/app/routers/` and
  `backend/app/services/` next to the existing classifiers; the
  backend Haiku route is **deferred** to a follow-up PR per the audit.
- Load integration with Pomodoro / Eisenhower / habits / projects /
  medications is intentionally not scoped here — those features
  remain orthogonal until a future PR documents how load interacts
  with them.
- Bidirectional overload notifications (`OverloadCheckWorker` clone)
  are **deferred** — v1 is descriptive-only. See
  `docs/audits/COGNITIVE_LOAD_AUDIT.md` for the design call.
