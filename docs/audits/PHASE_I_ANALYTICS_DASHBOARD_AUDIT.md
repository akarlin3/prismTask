# Phase I — Deep Analytics Dashboard Completion Audit

**Date:** 2026-05-01
**Branch:** `claude/analytics-dashboard-phase-1-AFRqH`
**Source request:** `done: 0.4` Phase I item from roadmap; mega-prompt scoped 13 sub-items (Productivity Dashboard screen, burndown, correlations, heatmap, DoW, export, Today badge, weekly notif, productive streak, Pomodoro auto-link, Pro gating, backend correlations, tests).

**Premise reality-check (load-bearing):** The mega-prompt assumed C4+C5 shipped on Apr 28 with the data layer done, but the *presentation* layer mostly absent. **Premise is wrong.** The 6-PR ANALYTICS_PR715_PORT slice (#897 → #903 → #909, plus #901, #908, #910) shipped a much larger fraction of the dashboard than `done: 0.4` implies — the Apr 11 spec had not yet recognised that summary tiles, productivity score area chart, time-tracking bar chart, day-of-week bars, time-of-day distribution, and contribution grid are all live on `TaskAnalyticsScreen` today. Realistic done-fraction is ~0.65, not 0.4.

This audit pivots from "build the dashboard" to "fill the genuinely missing surfaces on top of an already-substantial dashboard."

---

## A1 — SHIPPED state (GREEN)

All five claimed PRs are present on `main`:

| PR | What it shipped | Verification |
|----|-----------------|--------------|
| #901 | `TaskTimingEntity` + DAO + Repo + DI + migration 64→65 | `data/local/entity/TaskTimingEntity.kt`, `Migrations.kt:1875` |
| #906 | Manual log-time UI in ScheduleTab | `addedittask/AddEditTaskViewModel.kt:484 logTime()`, `tabs/ScheduleTab.kt:78,255` |
| #908 | Time-tracking bar chart (Pro-gated) | `analytics/TimeTrackingSection.kt`, `analytics/TaskAnalyticsScreen.kt:130` |
| #909 | Pomodoro auto-log to `task_timings` | `pomodoro/SmartPomodoroViewModel.kt:806 SOURCE_POMODORO` |
| #910 | Cross-device Firestore sync | `data/remote/SyncService.kt:428,1730 task_timings push/pull`, `SyncMapper` round-trip |

Connected-test parity (`smoke/TestDatabaseModule.kt:82 provideTaskTimingDao`) holds — landed on #914, retained after #908 merge per #914 fix-up commit history.

---

## A2 — Status matrix for the 13 sub-items

| # | Sub-item | Classification | Notes |
|---|----------|----------------|-------|
| 1 | Productivity Dashboard screen | **PARTIAL-SHIPPED** | `TaskAnalyticsScreen` already has period selector (7/30/90/365), productivity score area chart with best/worst-day callouts, summary tiles, time-tracking bars, project filter, contribution grid, DoW chart, time-of-day chart. No score *gauge* (vs the area chart). No dedicated "dashboard" route — it's the analytics route. Apr 11 spec assumed greenfield; reality is 70-80% done. |
| 2 | Project burndown | **ABSENT** | Zero scaffolding. Not on TaskAnalyticsScreen, not on ProjectDetailScreen. Originally deferred per `ANALYTICS_PR715_PORT_AUDIT.md` (Postgres int vs Firestore string id mismatch on `/analytics/project-progress`). Adds ~120-180 LOC if pursued. |
| 3 | Habit correlation analysis (Android-side) | **ABSENT** | No `HabitCorrelation*` symbols. Backend exists (A3). Highest-risk PR — needs new network call from Android (no current `/analytics/*` Android client) + Pro gate + 1/day rate-limit UX + AiFeatureGateInterceptor short-circuit handling. |
| 4 | Productivity heatmap | **PARTIAL-WRONG-SHAPE** | `ContributionGrid` shipped on TaskAnalyticsScreen line 215 but it visualises *task completion count* (1-bucket activity dots), not the *score-based 4-bucket* heatmap the spec asks for. Adding a separate score-heatmap risks UX duplication. Decision: defer the alternate heatmap; existing grid is good enough. |
| 5 | Day-of-week analysis | **PARTIAL-WRONG-AXIS** | `DayOfWeekBarChart` shipped (TaskAnalyticsScreen:237) with bestDay/worstDay highlighting + `InsightsCard` recommendation copy. But the bars are *task-count*, not *average-score*. Spec wanted score-based DoW. Could add a second mode. |
| 6 | Export Report | **ABSENT** | No `exportAnalytics*` / `generateAnalyticsMarkdown` symbols. Adds ~80-120 LOC. |
| 7 | Today screen score badge | **ABSENT** | `TodayWidget` has a `ScoreBadge` (widget/TodayWidget.kt:286), but it's not reused in the in-app `CompactProgressHeader`. The header has an analytics icon button only. Adds ~60-90 LOC. |
| 8 | Weekly analytics notification | **ABSENT** | No `WeeklyAnalyticsWorker` symbol. Adds ~120-180 LOC (Worker + scheduler + Settings toggle + notification builder). |
| 9 | Productive streak tracking | **ABSENT-AS-SCORE-BASED** | A *task-completion* streak exists (`TaskCompletionStats.currentStreak`, `longestStreak`), but no *score-based* streak (≥60 productive day). Adds ~80-120 LOC. |
| 10 | Pomodoro auto-link | **SHIPPED via #909** | `SmartPomodoroViewModel:806` writes `TaskTimingEntity(source = SOURCE_POMODORO)` per session task. No "Auto-Track" toggle yet — would default ON and is currently always-on. Toggle is YAGNI for now. |
| 11 | Pro gating constants | **PARTIAL** | `ProFeatureGate` exists (16 string-keyed features). Missing `ANALYTICS_FULL` + `ANALYTICS_CORRELATIONS`. Existing time-tracking chart uses ad-hoc `state.isPro` boolean, not `hasAccess(...)`. Adding constants is trivial; converting existing call-sites is small but worth doing for consistency. |
| 12 | Backend `/analytics/habit-correlations` | **EXISTS-WIRED-BUT-UNGATED** | Already shipped at `backend/app/routers/analytics.py:137`, with rate-limiter (1/day) and Anthropic call via `analyze_habit_correlations`. **PII gate gap:** no `dependencies=[Depends(require_ai_features_enabled)]` on the route, even though it touches Anthropic. This is a real defect that must be closed before Android wires up the call. |
| 13 | Tests | **PARTIAL** | `TaskCompletionAnalyticsTest`, `ProductivityScoreCalculatorTest`, `TimeTrackingAggregatorTest`, `AnalyticsSummaryAggregatorTest`, `DashboardPreferencesTest`, `TaskTimingRepositoryTest` cover existing surface. Each new feature needs ~1-2 new tests. |

---

## A3 — Backend habit-correlations endpoint (YELLOW)

**Verdict: EXISTS-WIRED-BUT-UNGATED.**

- Route: `backend/app/routers/analytics.py:137 @router.get("/habit-correlations")`
- Rate limiter: `RateLimiter(max_requests=1, window_seconds=86400)` (1 call/day/user)
- Schemas: `backend/app/schemas/analytics.py:80 HabitCorrelation`, `:88 HabitCorrelationResponse {correlations, top_insight, recommendation}`
- AI call: `app.services.ai_productivity:463 analyze_habit_correlations(daily_data, tier="FREE")`
- Defect: missing `dependencies=[Depends(require_ai_features_enabled)]`. `tasks.py:213,285` and `syllabus.py:121` show the correct pattern. Without the dependency, a user with the master AI toggle off can still trigger Anthropic via this endpoint — same gap that PR #1038 just closed for `/integrations/gmail/scan`.

**Implication for fan-out:** PR-E (Android correlations) MUST land alongside the backend gate fix in the same change-set — shipping the Android UI without first closing the gap perpetuates the PII risk in a more discoverable surface.

---

## A4 — Pro gating infrastructure (GREEN)

`ProFeatureGate` is the centralized gate. String-keyed `hasAccess(...)` + `requiredTier(...)` pattern is used by 9+ ViewModels. `ProUpgradePrompt` Composable exists and is used in `WeeklyPlannerScreen`, `DailyBriefingScreen`, `SchoolworkScreen`. Pattern: render `ProUpgradePrompt` where the Pro feature would be when `!proFeatureGate.hasAccess(KEY)`.

The existing analytics Pro-gating uses an ad-hoc `state.isPro` boolean (TaskAnalyticsScreen:112,120) rather than `hasAccess(ANALYTICS_FULL)`. Cosmetic inconsistency; not blocking, worth fixing in PR-A while we're touching the file.

No infrastructure-first PR needed. Just add the two new constants.

---

## A5 — Today screen integration point (GREEN)

- Today screen entry: `today/TodayScreen.kt:100`
- Header: `today/components/TodayProgressHeader.kt:69 CompactProgressHeader(completed, total, progress, progressStyle, onAnalyticsClick, trailingActions)`
- Slot for badge: between the "X done" `TerminalLabel` (line 303) and the analytics `IconButton` (line 309). New parameter `productivityScore: Int?` keeps it nullable for the < 3-day-history case.
- Existing `TodayWidget.kt:286 ScoreBadge` is the visual pattern to mirror for in-app.

---

## A6 — Sizing + decomposition

Single-branch constraint per session brief (`claude/analytics-dashboard-phase-1-AFRqH`) overrides the 5-PR fan-out from the mega-prompt. All Phase 2 work lands as discrete commits on this branch and ships as one PR. Bundling rule: each commit must be a coherent slice that could have been a PR — easy revert + reviewable diff.

**Recommended commit slices** (5 slices, target 600-900 LOC total impl + ~120-200 tests):

| Slice | Scope | Est. LOC |
|---|-------|----------|
| **S1** | `ProFeatureGate.ANALYTICS_FULL` + `ANALYTICS_CORRELATIONS` constants + convert existing `state.isPro` checks on TaskAnalyticsScreen to `hasAccess(...)` | 30-50 |
| **S2** | Today productivity score badge in `CompactProgressHeader` (local-only calc, ≥3-day-history gate, taps to TaskAnalyticsRoute) | 100-160 |
| **S3** | Productive-day streak tracker (DataStore keys `currentProductiveStreak`/`longestProductiveStreak`/`lastProductiveDate`, day-rollover via existing `DailyResetWorker`, displayed in `ProductivityScoreSection` header) + forgiveness-first broken-streak notification | 180-240 |
| **S4** | Markdown export Action on `TaskAnalyticsScreen` overflow menu — share-sheet via `Intent.ACTION_SEND` with `TaskCompletionStats` + summary + productivity scores | 100-150 |
| **S5** | `WeeklyAnalyticsWorker` (Sun 19:00 user-local; piggybacks on existing notification channel) + Settings toggle in `NotificationSettingsScreen` | 150-220 |

**Deferred slices** (out of scope this session — open follow-up audits):

- **Project burndown (#2)**: cross-cutting with project module; needs ProjectDetailScreen surface decision; defer to Phase I.1.
- **Habit correlations Android (#3) + backend PII gate (#12)**: HIGH-RISK; backend gate fix is its own PR (matches #1038 pattern), and Android client adds first-ever `/analytics/*` network call. Defer to a dedicated audit so the Anthropic prompt-design + retrofit setup gets proper review.
- **Score-based heatmap (#4)**: existing ContributionGrid is task-count-based; introducing a parallel score-heatmap risks UX coherence (two grids on one screen). Defer until `WeeklyBalanceReportScreen` consolidates with TaskAnalyticsScreen, or punt to Phase I.1.
- **Score-based DoW chart variant (#5)**: existing DoW chart works for v1.7; adding a "by score" toggle is polish, not a gap.
- **Pomodoro Auto-Track toggle (#10)**: YAGNI — current always-on auto-log is the right default. No telemetry showing users want a toggle.

**STOP-conditions evaluated:**
- ❌ A2 reveals >3 sub-items COMPLETE-UNVERIFIED: 5 are PARTIAL/SHIPPED but each is observably wired into a screen and ships its own tests. **Not triggered.**
- ❌ A3 backend ABSENT requiring fresh prompt design: backend EXISTS-WIRED with prompt already in `ai_productivity.py:463`. **Not triggered.**
- ❌ A4 Pro gating ad-hoc: ProFeatureGate is a clean centralized gate; adding two constants is mechanical. **Not triggered.**
- ✅ A6 fan-out exceeds 5 PRs OR any single PR > 500 LOC: single-branch session, but slices stay under 250 LOC each. **Honoured by deferring #2/#3/#12.**

---

## A7 — Verdict

**PROCEED — Option A (5-slice in-branch commit fan-out, S1-S5)** with explicit deferrals on burndown, habit correlations, and the alternate heatmap.

The mega-prompt's "build the dashboard" framing is wrong: the dashboard exists. We are filling targeted gaps. Reframing as "fill the gaps" both keeps Phase 2 scope under the LOC budget AND avoids regressing the existing TaskAnalyticsScreen surface.

After Phase 2 completes, Phase I item advances `done: 0.65 → ~0.85`. Remaining 0.15 is the deferred trio (burndown, correlations end-to-end with backend PII gate fix, optional heatmap). Open one follow-up audit doc per deferred item rather than try to bundle them now.

---

## Anti-pattern flags (worth noting; not fixing in this audit)

1. **TaskAnalyticsScreen mixes free + Pro content** without `ProUpgradePrompt` in the gated slots — Pro users see content, Free users see *nothing* where the Pro section would be. `ProductivityScoreSection` and `TimeTrackingSection` both vanish silently. Spec called out this exact UX risk. S1 wires `ProUpgradePrompt` correctly per the WeeklyPlanner pattern.
2. **`AnalyticsSummaryProUpsell` is a one-off** in `AnalyticsSummaryTiles.kt:147`; the codebase has a generic `ProUpgradePrompt` already. Drift to clean up — but not in this PR (out of scope; it works).
3. **The "Task Analytics" route name** is misleading — it's the closest thing the app has to a "Productivity Dashboard". Consider rename in a future polish PR; not worth screen-cap churn now.
4. **Backend `/habit-correlations` PII gate** (A3): defect, not anti-pattern. Tracked via deferred PR.
5. **No Retrofit `/analytics/*` Android client** — when habit-correlations gets ported, that's the first analytics network call from Android. The Retrofit client + DTO mappers will need their own ~80 LOC stub. Folded into the deferred correlations audit.

---

## Improvement table — sorted by wall-clock-savings ÷ implementation-cost

| Rank | Slice | Cost (LOC + tests) | Wall-clock benefit | Ratio |
|------|-------|--------------------|--------------------|-------|
| 1 | S1 — Pro gating constants + hasAccess refactor | ~50 + 20 | High consistency win, unblocks S2-S5, removes one anti-pattern | ★★★★★ |
| 2 | S2 — Today score badge | ~150 + 30 | High discoverability win; surfaces dashboard from primary screen | ★★★★ |
| 3 | S4 — Markdown export | ~130 + 20 | Medium; useful for therapist sharing alongside `ClinicalReportGenerator` | ★★★ |
| 4 | S3 — Productive-day streak | ~200 + 40 | Medium; adds engagement loop on top of existing task streak | ★★★ |
| 5 | S5 — Weekly analytics notification | ~190 + 40 | Medium-low; once-a-week ping, adoption signal unproven | ★★ |
| (def) | Burndown / Correlations / Score-heatmap | n/a | Defer | — |

