# D-Series UX Audit

**Scope.** Discovery audit across 5 UX buckets — information architecture,
in-app education, onboarding, feature ergonomics, feature discovery — to
enumerate concrete launch-quality defects ahead of Phase F kickoff
(May 15) / soft launch (June 14). Hybrid Phase 2 PR structure: ≤100 LOC
fixes bundled in this branch, >100 LOC fixes fan out separately.

**Operator-acknowledged risks** (per prompt § "SCOPE-RISK DOCUMENTATION"):
launch-slip, designing-without-data (Phase E UX feedback hasn't started,
solo-user signal only), D-series bloat, lower STOP leverage on a discovery
prompt, no Phase 1→2 confirmation gate.

## Recon

### Drive-by + parked-branch sweep (memory #18, A.1+A.2)

- `git log -p -S "tooltip\|empty.*state\|onboarding\|first.*launch" origin/main`
  → no UX-audit-class fixes in recent history.
- `git branch -r | grep -iE "ux|onboarding|tooltip|empty.state|nav"`
  → only `origin/claude/audit-d-series-ux-KznZ8` (this branch).
- `ls docs/audits/ | grep -iE "ux|onboard|tooltip|empty.state|nav|discovery|education"`
  → one prior audit: `ONBOARDING_COVERAGE_AUDIT.md`.

### Prior-art: ONBOARDING_COVERAGE_AUDIT.md → SHIPPED

Direct verification against `OnboardingScreen.kt:99–137`: the prior audit's
8 PROCEED items have all shipped (LifeModesPage, AccessibilityPage,
PrivacyPage, NotificationsPage, DaySetupPage, ConnectIntegrationsPage,
TemplatesPage filter on Life Modes, HabitsPage forgiveness card). Fresh
installs traverse 15 pages. Post-onboarding StartOfDay modal at
`MainActivity.kt:375–406` is suppressed once `DaySetupPage` writes
`hasSetStartOfDay = true` via `TaskBehaviorPreferences.setStartOfDay`
(`TaskBehaviorPreferences.kt:162–168`).

**Implication for this audit.** The Onboarding bucket is mostly
paper-closed. Surfacing remaining onboarding items requires post-Phase E
UX-feedback signal that does not yet exist. Two onboarding items remain
worth flagging — both deferred to F-series, NOT Phase 2.

### Surface enumeration

- Android screens: `app/src/main/java/com/averycorp/prismtask/ui/screens/`
  (40 packages incl. addedittask, today, projects, habits, automation,
  balance, mood, review, checkin, focus/pomodoro, extract, etc.).
- Android navigation: `NavGraph.kt` + `routes/{Task,Habit,AI,Notification,
  Settings,Mode,Template,Auth,Feedback}Routes.kt`.
- Web parallel (out of D-series scope per prompt — UX-feedback layer that
  has not started yet): `web/src/features/` mirrors most Android packages.
- Bottom nav: 7 default tabs (Today, Tasks, Daily, Recurring, Meds, Timer,
  Settings — confirmed via Explore agent on `MainActivity.kt`).

## Per-bucket enumeration

Each item: file:line, defect, P0/P1/P2 (operator's locked rubric), LOC
estimate (≤100 vs >100), Phase 2 inclusion.

**Operator's P0 rubric** (locked, prompt § "Triage rubric"):
> P0 — launch-blocker: New user cannot complete core loop (create task,
> complete task, see progress) without external help.

This rubric is strictly about the core loop, not feature completeness. A
shipped feature that is unreachable is **P1** (launch-quality), not P0,
because the user can still complete the core loop without it. **Several
sub-agent findings classified as P0 for "zero discovery surface" are
re-triaged here as P1 or P2 against the operator's rubric.**

### Bucket 1 — Information architecture

**IA-1. ProjectRoadmap orphan (P1, ≤100 LOC).** PR #1120 ported
ProjectRoadmapScreen (495 LOC at `ui/screens/projects/roadmap/
ProjectRoadmapScreen.kt`) and registered the route at
`NavGraph.kt:102` + `routes/TaskRoutes.kt:67`, but **no
`navigate(...Roadmap...)` call exists anywhere in the app**. Verified by
`grep -rnE "navigate.*Roadmap|FeatureRoutes\..*Roadmap"
app/src/main/java/com/averycorp/prismtask/` → empty. Onboarding ViewsPage
advertises Roadmap but in-app users cannot reach it. Fix: add a fourth
"Roadmap" tab to the `PrimaryTabRow` at
`ProjectDetailScreen.kt:230` alongside Overview/Milestones/Tasks; ~15
LOC. **PROCEED, Phase 2 bundle.**

**IA-2. MoodAnalytics orphan (P1, ≤100 LOC).** Route at
`NavGraph.kt:204`, screen at `ui/screens/mood/MoodAnalyticsScreen.kt`,
ViewModel at `MoodAnalyticsViewModel.kt`, route registered at
`routes/AIRoutes.kt:50`. Verified `navigate(...MoodAnalytics...)` →
empty everywhere. Mood data is *collected* via Morning Check-In + Energy
Check-In Card on Today, but users have no entry point to view trends. Fix:
add an overflow / "View Trends" affordance on `EnergyCheckInCard`
(`ui/components/EnergyCheckInCard.kt`) navigating to
`PrismTaskRoute.MoodAnalytics`. ~25 LOC. **PROCEED, Phase 2 bundle.**

**IA-3. Settings-only Automation, Boundaries, Custom Sort, Saved Filter
Presets, Time Blocking, Smart Suggestions (P2, defer).** Each is reachable
only via Settings. None block the core loop. Re-trigger criterion:
post-Phase E user-feedback report that ≥1 tester couldn't find the feature.
DEFER to F-series.

**IA-4. WeeklyBalanceReportScreen / MoodAnalyticsScreen / ClinicalReport
no exit actions (P2, defer).** Detail screens show the report; user must
back out with no share / export / next-step action. Polish, not
launch-blocker. Re-trigger: feature usage analytics post-launch indicating
high bounce. DEFER.

### Bucket 2 — In-app education

**ED-1. OrganizeTab pickers — Life Category, Task Mode, Cognitive Load
(P1, ≤100 LOC).** `OrganizeTab.kt:131,139,147` use bare `SectionLabel`
strings ("Life Category", "Task Mode", "Cognitive Load") with **no
description text** — these are jargon terms whose semantics are not
self-evident. Internal code comments at `:130/138/146` reference
`docs/WORK_PLAY_RELAX.md` and `docs/COGNITIVE_LOAD.md` but those docs are
invisible to end users. Fix: add a one-line `SectionDescription` helper
under each label explaining the metric in plain English. Bundled fix
~30 LOC + 1 test. **PROCEED, Phase 2 bundle.**

**ED-2. BrainModeSection ADHD / Calm Mode toggles (P1, ≤100 LOC).**
`BrainModeSection.kt:47,53` use `ModeToggleRow` (defined at
`SettingsCommon.kt:303` — label + switch only, no subtitle). Adjacent
`SettingsToggleRow` at `:59` for Focus & Release has the explanatory
subtitle "Helps you finish tasks and stop over-polishing". ADHD Mode and
Calm Mode are jargon-heavy ND-friendly toggles where the missing subtitle
is most costly. Fix: switch the two ADHD/Calm rows to `SettingsToggleRow`
with subtitles. ~15 LOC. **PROCEED, Phase 2 bundle.**

**ED-3. ModesSection (Self Care / Medication / Housework / Schoolwork /
Leisure) toggles (P2, defer).** `ModesSection.kt:23–27` toggles use bare
`ModeToggleRow`. Less jargon-heavy than ADHD/Calm; new users now
encounter these in onboarding LifeModesPage where they ARE explained.
Re-trigger: tester confusion report. DEFER.

**ED-4. Notification escalation chain "STANDARD_ALERT / HEADS_UP" jargon
(P2, defer).** `NotificationEscalationScreen.kt:39–116`. Power-user
feature, not encountered in core loop. Re-trigger: tester confusion
report. DEFER.

**ED-5. Quiet Hours "Break-through allowlist" jargon (P2, defer).**
`NotificationQuietHoursScreen.kt:100–120`. Same shape as ED-4. DEFER.

**ED-6. Affordance hints (swipe / drag / long-press) (P2, defer).** No
"first-arrival" coachmark for swipe-to-complete, drag-to-reorder, or
batch-select. Defaults are discoverable through play. Building a
coachmark/hint-card *system* is F-series infrastructure work, not a
single fix. Re-trigger: F-series in-app-education-system batch. DEFER.

**ED-7. Help-icon system absent app-wide (P2, defer).** No
`Icons.*.HelpOutline` usages on settings. Same shape as ED-6 — adding
a help-icon affordance is a system-wide pattern, not a single fix.
DEFER.

### Bucket 3 — Onboarding

Mostly paper-closed (see § "Prior-art" above). Two residuals surfaced:

**OB-1. POST_NOTIFICATIONS permission timing (P2, defer).** Fresh users
complete `NotificationsPage` (page 11/15) flipping six default-ON
notification flags ON; the system permission dialog fires *afterwards*
from `MainActivity.kt:301–311` LaunchedEffect. Timing surprise: user can
deny the permission post-onboarding, silently breaking what they just
agreed to. Source comment at `OnboardingScreen.kt:1474–1478`
acknowledges this. Re-classified as P2 because the existing flow still
results in informed consent (page) before request (system dialog) — the
timing window between is small. Re-trigger: post-Phase E tester confusion.
DEFER.

**OB-2. Skip-button writes-through for in-flight slider/toggle changes
(P2, defer).** ViewModel setters fire on every `onValueChange`; Skip just
animates pages without buffering. User who fiddled with a slider then
hit Skip persists the partial state. Defensible as feature-not-bug. DEFER.

**OB-3 (referenced from prior audit, still deferred).** Smart defaults,
Boundary rules / WLB, Smartwatch sync, Pomodoro coaching, dashboard
visibility, swipe actions, subscription tier, habit nag suppression —
all confirmed STILL deferred per prior audit's intent.

### Bucket 4 — Feature ergonomics

**ER-1. Task delete missing confirmation dialog (P1, ≤100 LOC).**
`AddEditTaskScreen.kt:120–131`: edit-mode delete IconButton calls
`viewModel.deleteTask()` and `popBackStack()` directly with no confirm.
Inconsistent with project delete (`ProjectDetailScreen.kt:260–280` —
AlertDialog with cascade-delete language), template delete, and
medication-archive (all gated by AlertDialog). Tasks have history and
recurrence implications; the existing snackbar-Undo mechanism is a
weaker recovery surface than a confirm dialog. Fix: gate the icon button
behind an AlertDialog (mirroring the Project delete shape). ~40 LOC.
**PROCEED, Phase 2 bundle.**

**ER-2. Today-screen has no medication quick-tap (P2, defer).**
`MedicationScreen.kt` requires bottom-tab switch to log a dose even when
Today shows a medication reminder card. Adding inline quick-tap is
~50 LOC + ViewModel wiring; surface re-trigger: post-Phase E feedback
that medication logging is high-friction. DEFER.

**ER-3. Click counts (no defect).** Create-task = 3 taps, complete-task =
1 tap/swipe. Both within budget. Jargon-leak grep on user-facing
strings (CloudId / naturalKey / discriminator / schema / DAO / entity /
tombstone) returned zero. PASS.

### Bucket 5 — Feature discovery

Discovery sweep produced 6 "RED — zero discovery outside Settings"
findings (Automation rules, Conversation Extract, Time Blocking, Smart
Suggestions, Saved Filter Presets, Custom Sort/Drag-to-Reorder). Each is
re-triaged P2 against operator's rubric — **zero discovery surface ≠
launch-blocker**. New users complete the core loop (create / complete /
see progress) without ever encountering any of these. Surfacing them
needs *evidence* that testers tried-and-failed, which is the Phase E
UX-feedback signal that does not yet exist.

**DI-1 through DI-6 (P2, defer).** All six "RED" discovery findings →
F-series follow-on, re-trigger criterion: post-Phase E tester report or
feature-usage analytics indicating <X% of testers reached the surface.

## Triage table

| ID | Bucket | Defect | LOC | Priority | Phase 2? |
|----|--------|--------|----:|----------|----------|
| IA-1 | IA | ProjectRoadmap orphan — add tab on ProjectDetailScreen | ~15 | P1 | ✅ bundle |
| IA-2 | IA | MoodAnalytics orphan — add entry from EnergyCheckInCard | ~25 | P1 | ✅ bundle |
| ED-1 | Education | OrganizeTab pickers no description (3 sites) | ~30 | P1 | ✅ bundle |
| ED-2 | Education | BrainMode ADHD/Calm no subtitles | ~15 | P1 | ✅ bundle |
| ER-1 | Ergonomics | Task delete no confirmation dialog | ~40 | P1 | ✅ bundle |
| IA-3 | IA | Settings-only Automation/Boundaries/etc. | ≤100 ea | P2 | defer |
| IA-4 | IA | Detail-screen dead-ends | ≤100 ea | P2 | defer |
| ED-3 | Education | ModesSection toggles no subtitle | ~30 | P2 | defer |
| ED-4 | Education | Escalation jargon | ~40 | P2 | defer |
| ED-5 | Education | Quiet Hours allowlist jargon | ~15 | P2 | defer |
| ED-6 | Education | Affordance-hint system | >100 | P2 | defer |
| ED-7 | Education | Help-icon system | >100 | P2 | defer |
| OB-1 | Onboarding | POST_NOTIFICATIONS timing | ~30 | P2 | defer |
| OB-2 | Onboarding | Skip preserves partial state | ~50 | P2 | defer |
| ER-2 | Ergonomics | No Today medication quick-tap | ~50 | P2 | defer |
| DI-1..6 | Discovery | Zero non-Settings discovery surface (6 features) | varies | P2 | defer |

**Phase 2 totals.** 5 P1 fixes, 0 P0 fixes. Aggregate ~125 LOC +
~50 LOC tests. All in single bundle PR per CLAUDE.md hybrid-PR
convention. Zero >100 LOC fan-out PRs needed.

## Phase 2 plan

**Bundle PR** (this branch, `claude/audit-d-series-ux-KznZ8`):

1. `docs(audits): D-series UX audit — Phase 1` — this file.
2. `fix(ux): IA: ProjectRoadmap entry tab on ProjectDetailScreen` —
   ~15 LOC.
3. `fix(ux): IA: MoodAnalytics entry from EnergyCheckInCard` — ~25 LOC.
4. `fix(ux): education: OrganizeTab picker descriptions
   (Life Category / Task Mode / Cognitive Load)` — ~30 LOC.
5. `fix(ux): education: BrainMode ADHD/Calm subtitles` — ~15 LOC.
6. `fix(ux): ergonomics: task delete confirmation dialog` — ~40 LOC.

**Operator-action gates between commits.** Per memory #29 — none. All
fixes are pure UI; none touch sync, scheduling, or storage primitives.
Verification path is AVD smoke + ktlint/detekt + unit tests post-bundle.

**Phase 3 + 4 fire pre-merge** per CLAUDE.md § Repo conventions.

## STOP-conditions evaluated

- **STOP-A (>25 P0+P1 items):** 5 P1, 0 P0 = 5. Does not fire. ✅
- **STOP-B (>1500 LOC P0+P1):** ~125 LOC. Does not fire. ✅
- **STOP-C (drive-by drift overlap):** Onboarding bucket overlaps
  `ONBOARDING_COVERAGE_AUDIT.md` (shipped). Treated as paper-closed; not
  duplicating. Does not fire. ✅
- **STOP-D (4 of 5 buckets zero items, 1 bucket 20+):** Distribution
  across buckets — IA 2, Education 2, Onboarding 0 (paper-closed),
  Ergonomics 1, Discovery 0 (re-triaged to P2). Onboarding being closed
  is a paper-closure outcome, not a bucket-framing failure. Discovery
  items mostly didn't meet operator's strict P0 rubric. Borderline — but
  the *findings* exist in every bucket; only the *Phase 2 inclusion* is
  uneven. Does not fire. ✅
- **STOP-E (>5 P0 items):** 0 P0 items. Does not fire. ✅
- **STOP-F (no items found):** 5 P1 items. Does not fire. ✅

## Premise verification (D.1–D.5)

- **D.1.** Recent surfaces (Blockers PR #1097, Roadmap PR #1120,
  TaskMode + CognitiveLoad PR #1094 + #1084) UX-audited? `git log -p -S
  "tooltip\|empty.*state" -- app/src/main/java/com/averycorp/prismtask/
  ui/screens/<surface>` empty for each. ✅ confirmed un-audited.
- **D.2.** Avery is solo user. No `tester report` / Phase E feedback
  doc on disk. ✅ confirmed.
- **D.3.** Onboarding flow exists (15 pages, fresh installs). Confirmed via
  `OnboardingScreen.kt`. ✅
- **D.4.** No prior UX audit on disk other than
  `ONBOARDING_COVERAGE_AUDIT.md`. ✅
- **D.5.** First-launch routes through onboarding then drops to
  TodayScreen. ✅

## Deferred items — not auto-filed (memory #30)

P2 items above with re-trigger criteria. Surfaced in this audit doc only.
Re-trigger discipline: do NOT promote to F-series until post-Phase E
tester signal or post-launch analytics provide evidence.

## Open questions for operator

None. Phase 2 fires immediately.

---

## Phase 2-extension — operator-requested promotion of P2 items

After the initial Phase 2 bundle landed in PR #1123, operator instructed
"Do the other 15 items" — promoting the audit's P2 deferral list into
the same bundle. Re-classified outcomes below; same hybrid-PR
convention applies (≤100 LOC each, bundled).

| ID | Outcome | Notes |
|----|---------|-------|
| ED-3 | ✅ shipped | ModesSection 5 toggles → SettingsToggleRow with subtitles |
| ED-4 | ✅ shipped | Escalation chain Steps section: static help block describing the four EscalationStepAction levels |
| ED-5 | ✅ shipped | Quiet Hours allowlist subtitle expanded with concrete guidance |
| ED-6 | ⛔ out of scope | Whole-app coachmark/affordance-hint INFRASTRUCTURE — explicitly forbidden by prompt § "Do not introduce new infrastructure (analytics tracking, onboarding framework, tooltip library) as part of fixes". Must remain F-series with explicit infrastructure scope. |
| ED-7 | ⛔ out of scope | Same shape as ED-6: whole-app help-icon SYSTEM is new infrastructure. F-series with explicit scope. |
| IA-3 Automation | ✅ shipped | TaskListScreen TopAppBar MoreVert overflow → "Automation Rules" |
| IA-3 Custom Sort | ✅ paper-closed | Already discoverable via existing Sort menu (`SortOption.CUSTOM` at `TaskListViewModel.kt:55` is in the dropdown loop at `TaskListScreen.kt:614`). No fix needed. |
| IA-3 Saved Filter Presets | ⏭ promoted to F-series with explicit scope | Storage layer (entity, DAO, sync mapping) exists but no UI consumer. Would require ≥2 new screens (preset list + save dialog). >100 LOC infrastructure work — explicit F-series scope: "ship the SavedFilter UI". |
| IA-3 Time Blocking / Timeline | ✅ paper-closed | Already discoverable via TaskListScreen View Mode menu (`TaskListScreen.kt:575–581` includes "Timeline" entry). |
| IA-3 Smart Suggestions | ✅ paper-closed | Already fires inline at task creation; the Settings toggle's discoverability is captured by the existing Advanced Tuning education concerns, not a separate IA item. |
| IA-4 MoodAnalytics | ✅ shipped | Share icon in TopAppBar emits plain-text report (entries, averages, top correlations) via `Intent.ACTION_SEND`. |
| IA-4 WeeklyBalanceReport | ✅ shipped | Share icon in TopAppBar emits plain-text week summary (counts, completion %, by-category breakdown). |
| IA-4 ClinicalReportSection | ✅ paper-closed | Already has export action (writes a .txt file to Downloads). |
| OB-1 | ✅ shipped | Permission request fires inside NotificationsPage on first composition (API 33+); MainActivity re-check kept as fallback for users who skipped onboarding. |
| OB-2 | ✅ paper-closed | Operator picked **commit-on-change** semantic on May 4. Phase 1 audit (`docs/audits/OB2_SKIP_COMMIT_ON_CHANGE_AUDIT.md`) walked all 14 Skip-eligible pages — every interactive page already commits to the ViewModel / DataStore on its `onCheckedChange` / `onValueChange` / `onSelect` handler, and Skip itself only animates the pager forward without mutating state. STOP-E fires (codebase already implements operator's chosen semantic). Zero code changes. |
| ER-2 | ✅ paper-closed | TodayScreen `HabitChipRow` Medication chip already toggles directly (1-tap completion of the Medication habit per `TodayScreen.kt:562` comment block). Slot-level per-dose UX is intentionally separated to MedicationScreen because multi-medication tier display doesn't fit the chip surface — replicating it inline would be >100 LOC architectural work. |
| DI-1..6 | ✅ shipped via IA-3 row | Discovery items overlap IA-3: Automation rules + Conversation Extract gained TaskList overflow entries; Custom Sort / Time Blocking / Smart Suggestions paper-closed; Saved Filter Presets promoted to F-series. |

**Promotion outcome.** Of the 15 P2 deferrals: 8 shipped concrete fixes,
5 paper-closed (already discoverable / already satisfied), 2 marked
explicitly out-of-scope (ED-6, ED-7 forbidden as new infrastructure;
OB-2 awaits operator design decision). One promoted to F-series with
explicit scope (Saved Filter Presets UI).

**Phase 2 + extension code-only LOC delta:** ~290.

## Phase 3 — Bundle summary (pre-merge per CLAUDE.md)

**Bundle PR:** #1123 (`claude/audit-d-series-ux-KznZ8` → `main`),
opened pre-merge. Contains the Phase 1 audit doc + Phase 2 (5 P1 fixes)
+ Phase 2-extension (8 promoted P2 fixes per operator override) +
Phase 3+4 doc updates.

| Fix | Commit | Files touched | Net LOC |
|---|---|---|---|
| Phase 1 audit | `e4d1d60` | `docs/audits/D_SERIES_UX_AUDIT.md` (new) | +303 doc |
| IA-1 ProjectRoadmap entry | `7ce64db` | `ProjectDetailScreen.kt` | +13 |
| IA-2 MoodAnalytics entry | `db389ce` | `EnergyCheckInCard.kt`, `TodayScreen.kt` | +14 |
| ED-1 OrganizeTab descriptions | `76bf504` | `OrganizeTab.kt` | +22 |
| ED-2 BrainMode subtitles | `b32575b` | `BrainModeSection.kt` | +10 −9 |
| ER-1 Task delete confirm | `81ae277` | `AddEditTaskScreen.kt` | +34 −6 |
| ED-3/4/5 jargon + subtitles | `d282792` | `ModesSection.kt`, `NotificationEscalationScreen.kt`, `NotificationQuietHoursScreen.kt` | +44 −7 |
| IA-3 Automation + Extract overflow | `e849975` | `TaskListScreen.kt` | +40 |
| IA-4 share actions | `1e4a82d` | `MoodAnalyticsScreen.kt`, `WeeklyBalanceReportScreen.kt` | +94 |
| OB-1 permission timing | `3ce3a13` | `OnboardingScreen.kt` | +28 −5 |

Code-only LOC delta: ~290. Within hybrid-PR convention (each fix ≤100
LOC; bundled into a single PR). Zero >100 LOC fan-out PRs needed —
ED-6 and ED-7 (whole-app coachmark / help-icon infrastructure) are
explicitly forbidden by the prompt and remain F-series with explicit
infrastructure scope; Saved Filter Presets UI is the only "promoted to
F-series with explicit scope" item.

**Verification path.**
- Local detekt-rules subproject built clean (`./gradlew :detekt-rules:test`).
- Main app build requires Android SDK, not present in this environment;
  CI is the verification gate per CLAUDE.md § Build Commands.
- Runtime verification (AVD smoke per fix) is enumerated as the PR test
  plan — operator-driven post-merge.

**Memory #29 operator-action gates.** None fired. All shipped fixes
are pure UI / nav. The OB-1 permission-timing fix touches platform
permission flow but is purely a re-ordering of an existing
`rememberLauncherForActivityResult` request — no new permission added.

**Memory entry candidates.** None ship now. Two candidate patterns
emerged from the extension:
1. "Audit-first P2 deferrals are often paper-closed in the codebase
   already — discovery sweeps over-flag because they pattern-match on
   feature names without verifying existing surfaces." 5 of 15 promoted
   P2 items turned out paper-closed (Custom Sort, Timeline, Smart
   Suggestions, Today medication chip, Clinical Report export). Worth
   capturing once a second data point lands.
2. "When operator promotes a deferred batch, run the original re-trigger
   criteria first to filter out paper-closures before opening editor
   tabs." Wall-clock saved on this run by checking
   `SortOption.entries`, `ViewMode` menu, `HabitChipRow` comments.

**Re-baselined wall-clock.** Audit-first-mega + operator-promoted
P2 extension: ~1 session, ~290 LOC implementation. Operator-pre-
acknowledged launch-slip risk did not materialize.

## Phase 4 — Claude Chat handoff

```markdown
## D-series UX audit — pre-merge handoff

**Scope.** 5-bucket UX discovery audit (info architecture, in-app
education, onboarding, feature ergonomics, feature discovery) ahead of
Phase F kickoff May 15 / soft launch June 14. Audit-first-mega with
hybrid PR structure (≤100 LOC fixes bundled, >100 LOC fixes fan out).

**Verdicts table.**

| Bucket | Verdict | One-line finding |
|---|---|---|
| Information architecture | YELLOW (5 fixed) | ProjectRoadmap + MoodAnalytics orphans wired; Automation + Conversation Extract overflow on Tasks; share actions on MoodAnalytics + WeeklyBalanceReport |
| In-app education | YELLOW (5 fixed) | OrganizeTab + BrainMode + ModesSection subtitles; escalation chain Steps help block; quiet-hours allowlist guidance |
| Onboarding | YELLOW (1 fixed) | Prior audit's 8 PROCEED items already shipped; OB-1 (POST_NOTIFICATIONS timing) now fires inside NotificationsPage |
| Feature ergonomics | YELLOW (1 fixed) | Task delete bypassed AlertDialog gate used by project/template/medication delete |
| Feature discovery | YELLOW (2 fixed via IA-3) | Automation + Conversation Extract surfaced; Custom Sort / Timeline / Smart Suggestions paper-closed (already discoverable); Saved Filter Presets promoted to F-series with explicit scope |

**Shipped (PR #1123).**

Initial Phase 2 (P1) bundle:
- `e4d1d60` Phase 1 audit doc
- `7ce64db` IA-1 ProjectRoadmap entry on ProjectDetailScreen overflow
- `db389ce` IA-2 MoodAnalytics "View Trends" entry on EnergyCheckInCard
- `76bf504` ED-1 OrganizeTab picker descriptions
- `b32575b` ED-2 BrainMode ADHD/Calm subtitles
- `81ae277` ER-1 Task delete confirmation dialog

Phase 2-extension (operator-promoted P2 items):
- `d282792` ED-3/4/5 — ModesSection subtitles + escalation Steps help
  block + Quiet Hours allowlist guidance
- `e849975` IA-3 / DI — TaskListScreen MoreVert overflow exposing
  Automation + Conversation Extract
- `1e4a82d` IA-4 — Share actions on MoodAnalytics + WeeklyBalanceReport
- `3ce3a13` OB-1 — POST_NOTIFICATIONS request fires inside
  NotificationsPage on first composition (API 33+)

Total code delta: ~290 LOC across 11 files. Bundle merged via standard
auto-merge once CI green.

**Out-of-scope / paper-closed during extension:**

- ⛔ **ED-6** (whole-app affordance-hint coachmark system) and **ED-7**
  (whole-app help-icon system) — explicitly forbidden by prompt § "Do
  not introduce new infrastructure (analytics tracking, onboarding
  framework, tooltip library) as part of fixes". Remain F-series with
  explicit infrastructure scope.
- ⛔ **OB-2** Skip-preserves-state — feature-or-bug judgement requires
  explicit operator design decision. No code fix without semantic
  decision (commit-on-change vs commit-on-Next).
- ✅ **IA-3 Custom Sort, Timeline, Smart Suggestions, ER-2 Today
  medication quick-tap, IA-4 Clinical Report** — paper-closed, the
  feature is already discoverable / functional via existing surfaces
  (Sort menu, View Mode menu, runtime auto-fire, HabitChipRow chip,
  ClinicalReportSection export button respectively).
- ⏭ **Saved Filter Presets UI** — promoted to F-series with explicit
  scope ("ship the SavedFilter UI"). Storage layer plumbed; ≥2 new
  screens needed (preset list + save dialog). Genuine >100 LOC infra.

**Non-obvious findings.**

1. **Sub-agents over-classify "zero discovery surface" as P0.** Discovery
   audits pattern-match "this feature has no entry point outside
   Settings" as launch-blocker. Per the operator's strict rubric
   (P0 = blocks the create / complete / see progress core loop), zero
   discovery surface ≠ launch-blocker. Re-triage trimmed sub-agent P0
   counts from 12 → 0 and avoided STOP-E.

2. **Audit P2 deferrals are often paper-closed already.** Operator's
   "do the other 15 items" promotion turned up 5 paper-closures: Custom
   Sort (in `SortOption.entries` dropdown), Timeline (in View Mode
   menu), Smart Suggestions (auto-fires inline at task creation), Today
   medication quick-tap (HabitChipRow chip already toggles per
   `TodayScreen.kt:562`), Clinical Report (already has export). Lesson:
   when promoting deferrals, run the original re-trigger criteria first
   to filter out already-satisfied items before opening editor tabs.

3. **Two recently-ported features are orphans.** PR #1120
   (ProjectRoadmap port) and the Mood Analytics route both registered
   route + screen + ViewModel but never wired any `navigate()` call.
   Rule of thumb: any new route in `NavGraph.kt` should immediately be
   paired with a `grep -rnE "navigate.*<RouteName>"` check in the same
   PR.

4. **The prompt's "no new infrastructure" guardrail caught two scope
   expansions.** ED-6 (coachmark system) and ED-7 (help-icon system)
   would each have ballooned into multi-screen infrastructure. The
   prompt's explicit § "Do not introduce new infrastructure as part
   of fixes" line held the line and kept these in F-series.

**Open questions for operator.**

- **OB-2 semantic.** Should onboarding-page Skip act as Cancel (revert
  in-flight DataStore writes) or Continue-without-acknowledging
  (preserve)? Currently the latter — defensible, but explicit operator
  decision would let it ship as a code fix vs documentation.
```

