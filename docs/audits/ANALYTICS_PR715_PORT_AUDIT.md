# Deep Analytics Dashboards — Android Port of Web PR #715

**Phase**: 1 (audit only, no code changes)
**Date**: 2026-04-28
**Optimization target**: Ship to Phase F launch surface by 2026-05-15 OR honestly classify as not-shippable.
**Window**: 17 calendar days (Apr 28 → May 15) MINUS Test 3 verification + medication bug fixes.

## Recommendation (lead)

**COMPROMISED-SCOPE-PROCEED with Subset A** (summary tile row only) **OR DEFER to Phase I**.

The full PR #715 port is **not feasible** in the May 15 window. The web→Android port pattern flips structurally because Android computes analytics client-side from Room while web wires backend HTTP endpoints — so almost every "wired endpoint" line in the web PR maps to "new Android client-side aggregator" instead of "new HTTP wrapper."

Two viable Phase F options:

1. **Subset A — Summary tile row only** (~200-300 LOC, 1-2 days, low risk). Ports the 4-tile summary header into the existing TaskAnalyticsScreen as a Pro-gated section. Defers everything else to Phase I.
2. **DEFER entire port to Phase I** with this audit as the architectural plan. Phase I starts in Sep 2026 pre-architected.

**My pick: 2 (DEFER)** — because Phase F gate is YELLOW pending Test 3 re-run, the existing 3 free-tier analytics screens create UX inconsistency with a Pro-gated new screen, and the wall-clock margin against Phase F is too thin to absorb the inevitable premise refinement. Subset A is the next-best if we want *something* in Phase F.

---

## Item 1 — Web PR #715 reference inventory

**Premise**: PR #715 is "web Slice 4 — Analytics dashboard," a port-source for the Android Phase I/F item.

**Verification**: PR #715 lives in *this* repo (the monorepo holds Android `app/`, FastAPI `backend/`, and React `web/`). Merge SHA: `d494971b37ed4d958c0fad630e86e2543b9bf893` on 2026-04-23.

**Per-file inventory** (8 files, 857 LOC additions, 0 deletions):

| File | LOC | Category | Notes |
|---|---|---|---|
| `web/src/features/analytics/AnalyticsScreen.tsx` | 559 | UI | Recharts AreaChart + BarChart, useState/useMemo state mgmt, Pro-gating via `useProFeature` |
| `web/src/types/analytics.ts` | 138 | Types | TS interfaces mirroring `backend/app/schemas/analytics.py` |
| `web/src/api/__tests__/analytics.test.ts` | 91 | Tests | 4 API wrapper test cases |
| `web/src/api/analytics.ts` | 41 | API | 3 axios wrappers: `productivityScore`, `timeTracking`, `habitCorrelations` |
| `CHANGELOG.md` | 16 | Doc | n/a |
| `web/e2e/analytics.spec.ts` | 8 | Tests | Playwright stub |
| `web/src/components/layout/Sidebar.tsx` | 2 | Nav | One nav link |
| `web/src/routes/index.tsx` | 2 | Nav | One route registration |

**Library dependencies**:
- `recharts` (already a web dep) — Area + Bar charts via `<ResponsiveContainer>`/`<AreaChart>`/`<BarChart>`/`<XAxis>`/`<YAxis>`/`<Tooltip>`/`<CartesianGrid>`/`<Legend>`/`<Cell>`
- `lucide-react` (icons), `date-fns` (date math), `sonner` (toasts) — all existing deps
- `@/hooks/useProFeature` — existing Pro-gate hook
- `@/components/shared/ProUpgradeModal` — existing upgrade modal

**Backend endpoints wired** (from `backend/app/routers/analytics.py`, all SQLAlchemy/Postgres-backed, originally shipped in PR #242):

| Endpoint | Wired in #715? | Backing |
|---|---|---|
| `GET /analytics/summary` | yes (via existing `dashboardApi.getAnalyticsSummary`) | `compute_summary` over Postgres |
| `GET /analytics/productivity-score` | yes | `compute_daily_productivity_scores` (4-component breakdown: task_completion, on_time, habit_completion, estimation_accuracy) |
| `GET /analytics/time-tracking` | yes | `compute_time_tracking_stats` with group_by ∈ {project, tag, priority, day} |
| `GET /analytics/habit-correlations` | yes | `app.services.ai_productivity.analyze_habit_correlations` — **AI service dependency** + rate-limited 1/day/user |
| `GET /analytics/project-progress` | **NOT WIRED** in #715 | Postgres `int` project_id vs Firestore `string` doc-id mismatch — punted to PR #730 (slice 16) which adds 578 LOC including `ProjectProgressPanel.tsx` (230) + `projectBurndown.ts` client-side compute (132) + tests (164) |

**Data layer shape**: Web computes nothing. All compute is server-side Postgres SQL via SQLAlchemy. Web uses `Promise.allSettled` so a failing endpoint doesn't kill the whole dashboard.

**Findings**:
- Substantive code is the screen (559) + types (138) + API wrappers (41) = ~738 LOC.
- The screen is heavy on Recharts JSX, which is dense — 1 chart ≈ 50 LOC of declarative JSX. Compose Canvas equivalents typically run 1.5-2.5× longer per chart.
- Habit correlations require an AI service call, daily rate-limited.

**Risk classification**: GREEN (the inventory itself is non-controversial; risk surfaces in Items 2-3).

---

## Item 2 — Android architectural fit

**Premise verification — FAILED (premise refinement #1)**: The audit scope assumed a fresh Android port. Reality: Android already ships **3 analytics screens** (1,281 LOC) that compute client-side from Room. They never call the `/analytics/*` backend endpoints.

**Existing Android analytics surface**:

| File | LOC | Approach |
|---|---|---|
| `ui/screens/analytics/TaskAnalyticsScreen.kt` | 541 | Compose **Canvas** custom-drawn line chart, `ContributionGrid` heatmap, day-of-week + hour-of-day distributions, period selector (`AnalyticsPeriod` enum: 7/30/90/365 days) |
| `ui/screens/analytics/TaskAnalyticsViewModel.kt` | 91 | Combines `TaskCompletionRepository.getCompletionStats(days)` / `getProjectStats(projectId, days)` Flows from Room |
| `ui/screens/habits/HabitAnalyticsScreen.kt` | 336 | Canvas charts |
| `ui/screens/habits/HabitAnalyticsViewModel.kt` | 113 | Room-backed Flows |
| `ui/screens/mood/MoodAnalyticsScreen.kt` | 200 | Canvas-free composition |
| `ui/screens/mood/MoodAnalyticsViewModel.kt` | (small) | `MoodCorrelationEngine` use case |
| `widget/ProductivityWidget.kt` | (separate) | Glance home-screen widget |

**Routes** (`ui/navigation/routes/{TaskRoutes,HabitRoutes,AIRoutes}.kt`): all 3 screens are already registered and reachable. None are Pro-gated today (free tier).

**Pro-gating mechanism** (`domain/usecase/ProFeatureGate.kt`): `ProFeatureGate.hasAccess(feature)` returns `Boolean` from `BillingManager.userTier`. Used in 15 files (Today, Pomodoro, Eisenhower, BatchOperations, SyncService, Reengagement, Briefing, etc.) but NOT in `screens/analytics/` today.

**Chart library decision**: existing pattern is **Compose Canvas custom-drawn** (`Canvas`, `Path`, `drawScope`). No `vico` / `MPAndroidChart` / chart-library dep in `app/build.gradle.kts`. Adding a chart library would diverge from the established pattern for ~3 new charts; staying with Canvas is the lower-risk path but adds LOC per chart.

**Per-component port complexity**:

| #715 component | Android existing? | Port classification |
|---|---|---|
| Range selector (7/30/90d) | YES — `AnalyticsPeriod` enum (7/30/90/365) | TRIVIAL — reuse |
| 4-tile summary row (Today / Week / Streak / Habits) | NO consolidated row; Today screen has progress ring + per-section counts; `TaskCompletionStats` carries `completionRate`/`onTimeRate` | MEDIUM — new aggregator + tile composables, ~150-300 LOC |
| Productivity score area chart | NO — Android has no daily productivity score domain logic | COMPLEX — new use case (4-component score), new Canvas area-chart composable, ~600-900 LOC |
| Time-tracking bar chart (group-by project/tag/priority/day) | Per-task time tracking exists (`TaskEntity.actualMinutes`); no aggregate chart, no group-by aggregator | COMPLEX — new aggregator + bar chart, ~500-800 LOC |
| Habit correlations (AI-generated) | NO — `MoodCorrelationEngine` exists but is mood↔task, not habit↔productivity | BLOCKED-BY-LIBRARY (AI service) OR REWRITE (deterministic Pearson correlation client-side) — ~400-700 LOC if rewritten deterministic |
| Project burndown (deferred in #715, added in #730) | NO — `ProjectRepository` carries `ProjectWithProgress`/`ProjectDetail` projections but no burndown timeseries | COMPLEX — out of scope for #715 port |
| Pro-gating wrap | YES — `ProFeatureGate` available | TRIVIAL — wrap composable, ~20-40 LOC |
| Navigation | YES — existing routes | TRIVIAL — add 1 route |

**Performance concern**: PR #715's web client fetches one HTTP response with pre-aggregated data. Android equivalent would aggregate 6-12 months of `TaskCompletionEntity` / `HabitLogEntity` / `TaskEntity.actualMinutes` rows in memory. For users with 100K+ rows, ~1-2s aggregation on mid-range devices is plausible. Mitigation: cap to 90d window for chart queries (which #715 already does), pre-aggregate via DAO `GROUP BY` queries where possible.

**Findings**:
- The web→Android port is **not a 1:1 file mapping**. The ~838 LOC of substantive web code maps to a much larger Android delta because client-side compute logic must be re-implemented.
- Existing 3 free analytics screens create a **UX consistency risk** — adding a new Pro-gated "Analytics" surface alongside the existing free `TaskAnalyticsScreen` creates a confusing two-tier experience unless the new dashboard subsumes/replaces the existing screens.
- Backend endpoints (`/analytics/*`) are **not a viable port path** for Android: Android syncs to Firestore (per CLAUDE.md "Cloud sync: Firebase Firestore"), and the FastAPI/Postgres data isn't authoritative for Android-originating tasks/habits without a backend sync that doesn't exist for these read-heavy queries. The analytics endpoint backing Postgres tables would be empty or stale for Android-only users.

**Risk classification**: YELLOW — port shape is structurally feasible client-side but at significant LOC cost and with a UX-coherence risk.

---

## Item 3 — Scope feasibility

**Realistic LOC estimate** (Android port of #715, client-side compute, Compose Canvas charts):

| Component | Web LOC | Android LOC est. | Multiplier rationale |
|---|---|---|---|
| Types/data classes | 138 | 80-120 | Kotlin data classes are denser than TS interfaces |
| Repository/use case (compute) | 0 (server-side) | 400-700 | Productivity score calc + time-tracking aggregator + (optional) habit correlator |
| Screen UI + Canvas charts | 559 | 700-1200 | Compose Canvas more verbose than Recharts JSX; ~3 charts |
| ViewModel | 0 (web hooks) | 150-250 | Hilt-injected, StateFlow.combine, period/groupBy state |
| Navigation glue | 2 | 5-15 | One new route |
| Pro gating | (existing hook) | 20-40 | Wrap composable + branch |
| Tests (DAO + unit + ViewModel) | 99 | 300-500 | Memory: edge-case Tier A property-based tests for data layer (per #882/#886), unit tests for chart math, snapshot tests Tier B (NOT yet shipped) |
| **Total** | **~838** | **~1,655 - 2,825 LOC** | |

That's a **2-3.5× multiplier**, on the high end of the 1.5-3× range the audit scope predicted.

**Wall-clock estimate** (single dev, sequential):

| PR | Scope | Days (low-high) |
|---|---|---|
| Setup PR — Pro-gate wrapper, route registration, no new chart lib | ~50 LOC | 0.5 |
| Productivity score domain layer + tests | `ProductivityScoreCalculator` use case, 4-component breakdown matching backend formula | 1.5-2 |
| Time-tracking aggregator + tests | `TimeTrackingAggregator` use case with group_by | 1-1.5 |
| Habit correlations (deterministic, no AI) + tests | `HabitProductivityCorrelator` — Pearson correlation client-side | 1.5-2 |
| Summary tile row repo + composable | Reuse existing data, new tile composable | 0.5-1 |
| Productivity area chart UI | Canvas-based, period selector, best/worst day callouts | 1.5-2 |
| Time-tracking bar chart UI + group-by | Canvas-based, accuracy coloring | 1.5-2 |
| Habit correlations list UI | Composable list + recommendation callout | 1-1.5 |
| Integration: navigation + Pro gate + state | Wire-up | 0.5-1 |
| Manual verification on S25 Ultra + AVD | Touch verification of all 4 sections | 0.5-1 |
| **Total** | | **9.5 - 14 days** |

**Available window**: 17 days (Apr 28 → May 15).

**Subtractions** (per scope doc + Phase F gate state):
- Test 3 sync verification re-run: 1-2 days
- Medication bug fixes ongoing buffer: 2-3 days
- Phase F kickoff prep / final regression sweep: 1-2 days
- Per memory feedback_skip_audit_checkpoints + audit-first track record (15/16): expect 1 premise refinement → 1-2 days slip
- Pre-existing in-flight work on `docs/ci-audit-phase-3-summary` and other branches: small but nonzero

**Realistic available**: ~10-13 days.

**Verdict**: high-end estimate (14 days) **exceeds** high-end available (13 days). Low-end estimate (9.5 days) just fits with **zero buffer**. Per scope-doc rule: "If high estimate exceeds available days minus Test 3 verification + medication bug fixes buffer, the port is **NOT FEASIBLE for Phase F**."

**Risk classification**: RED — full-scope port does not fit Phase F window with safety margin.

---

## Item 4 — Implementation plan

(Conditional — only the subset options below are actionable in Phase F.)

### Subset A — Summary tile row only (RECOMMENDED if any Phase F work)

Smallest valuable subset. Adds the 4-tile header to the existing `TaskAnalyticsScreen` as a Pro-gated section.

| PR | Branch | Scope | LOC est | Verification | Days |
|---|---|---|---|---|---|
| A1 | `feat/analytics-summary-tiles` | New `AnalyticsSummary` data class + repo aggregator on `TaskCompletionRepository` (today/week/streak/habits-7d/habits-30d). Compose tile row composable. Wire into `TaskAnalyticsScreen` top, Pro-gated via `ProFeatureGate.hasAccess(...)`. Unit tests for aggregator + ViewModel state. | 200-300 | CI green + manual touch on S25 Ultra | 1-2 |

**Bundling**: single PR.
**Sequence**: standalone — no dependencies.

### Subset B — Productivity score chart only

Highest-novelty single feature. New domain logic + new chart.

| PR | Branch | Scope | LOC est | Verification | Days |
|---|---|---|---|---|---|
| B1 | `feat/analytics-productivity-domain` | `ProductivityScoreCalculator` use case (4-component breakdown). Unit tests + property-based per Tier A pattern (#882/#886). | 250-400 | CI green | 1.5-2 |
| B2 | `feat/analytics-productivity-chart` | Canvas area-chart composable + range selector + best/worst-day callouts + ViewModel wiring. Pro-gated section in `TaskAnalyticsScreen`. | 450-700 | CI green + manual touch on S25 Ultra | 2-2.5 |

**Bundling**: B1 + B2 separate (data layer first per audit-first protocol; B2 depends on B1).
**Sequence**: B1 → B2 strict dependency.

### Subset C — Full port minus AI correlations (NOT RECOMMENDED for Phase F)

| PR | Branch | LOC est | Days |
|---|---|---|---|
| C1 | `feat/analytics-summary-tiles` | 200-300 | 1-2 |
| C2 | `feat/analytics-productivity-domain` | 250-400 | 1.5-2 |
| C3 | `feat/analytics-productivity-chart` | 450-700 | 2-2.5 |
| C4 | `feat/analytics-timetracking-domain` | 200-350 | 1-1.5 |
| C5 | `feat/analytics-timetracking-chart` | 350-550 | 1.5-2 |
| **Total** | | 1,450 - 2,300 | **7-10 days** |

This skips habit correlations (the AI/Pearson re-implementation) and the project burndown (#730 scope) entirely. Wall-clock: 7-10 days, fits the **low-end** window only — still no buffer.

---

## Item 5 — Risk classification

| Risk dimension | Subset A | Subset B | Subset C | Full port |
|---|---|---|---|---|
| **Sync surface** (P0 #850-#856 regression) | LOW — uses existing repos | LOW — domain-layer additive | LOW-MEDIUM — touches existing repos additively | MEDIUM |
| **Medication surface** (PR #887 SoD regression) | NONE — orthogonal | NONE — orthogonal | LOW — time-tracking aggregator may interact with `actualMinutes` if medication tasks have estimates | LOW |
| **Performance** (mid-range device, 100K+ rows) | LOW — small read | MEDIUM — 90d Flow aggregation per recompose; mitigate via DAO `GROUP BY` | MEDIUM-HIGH — multiple aggregators | HIGH |
| **Library** | NONE — no new dep | NONE — Canvas only | NONE | LOW |
| **UX coherence** (existing free analytics + Pro analytics) | MEDIUM — new Pro tile in free screen confuses tier story | HIGH — Pro-gated section in free screen worsens tier confusion | HIGH | HIGH |
| **Schedule** (Phase F May 15) | GREEN — 1-2 days | YELLOW — 3.5-4.5 days | RED — 7-10 days, no buffer | RED — 9.5-14 days, exceeds window |
| **Testing burden** (Tier A + Tier B per memory) | LOW | MEDIUM | HIGH | HIGH |

**Single biggest risk: UX coherence** (called out in Item 2). Adding any Pro-gated analytics to a screen that already has free analytics creates a launch-time tier surprise. Subset A is the least bad shape because it adds *one* tile row above existing free content; users see an upgrade prompt without losing existing functionality.

**Habit-correlations specific risk** (Subset C+): backend uses `app.services.ai_productivity` which calls Claude/OpenAI. Android equivalent options:
1. Call backend endpoint — blocked because Android isn't pushing data to Postgres (premise refinement #2).
2. Reimplement deterministic (Pearson correlation client-side, no AI) — ~400-700 LOC, but the recommendation text + interpretation phrasing ("doing X correlates with Y% higher productivity") needs a templating layer.
3. Add direct Anthropic API call from Android — adds API key surface to client app, increases attack surface, requires usage-cost gate. Not advisable.

Decision needed if Subset C is pursued.

---

## Decision matrix (ranked by wall-clock-savings ÷ implementation-cost)

| # | Option | Phase F LOC | Wall-clock | Phase F value | Phase I unblock | Risk | Recommendation |
|---|---|---|---|---|---|---|---|
| 1 | **DEFER** — full port to Phase I, audit doc as plan | 0 | 0 | 0 | HIGH (architecture pre-validated, ~50% of Phase I prep done) | NONE | **★ My pick** |
| 2 | Subset A — summary tile row | 200-300 | 1-2 d | LOW-MEDIUM | LOW (still leaves 80% of #715 to port) | LOW | Acceptable if user wants Phase F deliverable |
| 3 | Subset B — productivity score chart | 700-1100 | 3.5-4.5 d | MEDIUM | LOW-MEDIUM | YELLOW | Marginal — eats Phase F buffer |
| 4 | Subset C — summary + productivity + time-tracking | 1,450-2,300 | 7-10 d | HIGH | LOW (most #715 features) | RED | Not recommended for Phase F |
| 5 | Full port (incl. correlations + burndown) | 1,655-2,825 | 9.5-14 d | HIGHEST | NONE | RED | Not feasible for Phase F |

Rankings ordered by `(value × Phase-I-unblock) ÷ (cost × risk)`. Option 1 dominates because the audit doc *is* the Phase I prep deliverable: when Phase I starts in Sep 2026, the architectural decisions (client-side compute, Compose Canvas, deterministic correlation, Pro-gating UX redesign) are already made.

---

## Anti-patterns flagged (worth surfacing, not necessarily fixing)

1. **Existing analytics screens are not Pro-gated** despite analytics being a Pro-tier surface in the pricing model (per CLAUDE.md `ProFeatureGate` + `BillingManager` two-tier). This is a pre-existing tier-coherence gap that any Pro-gated analytics PR will collide with. Resolving it is its own audit (rename/replace existing free `TaskAnalyticsScreen` vs. consolidate into a new Pro-gated dashboard) — out of scope here.

2. **Habit-correlations endpoint is rate-limited 1/day/user** server-side. If Android ever calls it, UX must surface the 86400s window, otherwise users will see "loading…" → 429 → confused. Not a Phase F problem because Subset A/B don't touch it.

3. **`/analytics/project-progress` Postgres-int-vs-Firestore-string ID mismatch** (deferred in PR #715, slice 16 in PR #730 fixes it client-side on web). Same mismatch will hit Android if backend-driven analytics is ever attempted — confirmed by the risk entry on scope-doc line 621.

4. **Web `Promise.allSettled` partial-failure pattern** is good — Android port should use the same shape (`coroutineScope` + `awaitAll` with per-call try/catch) so a single failing aggregator doesn't blank the whole dashboard.

5. **Recharts `<Cell>` per-bar coloring** maps to Compose Canvas as `drawRect` per bar with conditional fill — straightforward but worth noting for the chart implementer.

6. **`ProductivityWidget`** already exists. The new productivity-score chart should consider whether its data shape can feed the widget (one source of truth) or should be independent. Not blocking, but worth a 1-line decision in Subset B.

---

## Phase I impact

**If DEFER chosen** (recommended): Phase I "Deep analytics dashboards" item stays at done: 0, but with `docs/audits/ANALYTICS_PR715_PORT_AUDIT.md` attached as the architectural plan. Phase I work in Sep 2026 starts pre-architected:
- Chart library decision: Canvas (validated)
- Data layer: client-side compute via new use cases (validated)
- Correlation algorithm: deterministic Pearson client-side (decision pending)
- Pro-gating UX: requires resolution of existing free analytics screen tier coherence
- Estimated Phase I duration: 9.5-14 days → **same window as available Phase F window minus buffer**, so Phase I should comfortably absorb it.

**If Subset A chosen**: Phase I item drops to done: ~0.15. Phase I scope shrinks ~1 day (the summary tile row already shipped). Marginal benefit.

**If Subset C chosen** (not recommended): Phase I item drops to done: ~0.7. Phase I scope shrinks ~7-10 days. But the schedule risk in Phase F is RED.

---

## Phase 2 / Phase 3

**Phase 2** is intentionally NOT planned beyond the Subset A skeleton above. If user picks Subset A, the single PR shape is documented in Item 4 and can proceed without a separate Phase 2 doc.

**Phase 3** (bundle summary) deferred — only relevant if any Phase 2 PRs land. If DEFER is chosen, this audit *is* the bundle summary for the "we evaluated and rejected for window reasons" outcome.

---

## Memory-entry candidates

(Per memory `feedback_skip_audit_checkpoints.md`: only flag surprising / non-obvious things.)

1. **Web→Android port multiplier flips when web is server-rendered**: web LOC undercount Android port size when web fetches HTTP-pre-aggregated data and Android must aggregate client-side. Multiplier on top of the 1.5-3× verbose-Compose ratio: an additional 1.3-1.8× for the "translate server SQL to client Kotlin" axis. Combined effective multiplier: 2-5×. Worth saving as a feedback memory.

2. **Pre-existing free-tier analytics screens are a tier-coherence trap** for any new Pro-gated analytics work. Worth a project memory.

Both are noted only — not actionable until user approves DEFER vs. Subset A.

---

## Outcome distribution check vs. scope-doc prediction

Scope doc predicted: 40% COMPROMISED-SCOPE-PROCEED / 35% NOT-SHIPPABLE / 20% PROCEED full / 5% UNEXPECTED.

Audit landed in **NOT-SHIPPABLE** (35% bin) primarily because of two premise refinements:
1. Android already has 3 analytics screens — port isn't greenfield, has UX-coherence risk.
2. Web's server-side compute pattern doesn't translate; Android port size is 2-3.5× web LOC instead of the predicted 1.5-3×.

Per scope doc memory expectation ("16-of-16 audit-first track record predicts at least one premise refinement during Phase 1"): **two refinements**, both load-bearing. Track record continues at 17-of-17 after this audit.
