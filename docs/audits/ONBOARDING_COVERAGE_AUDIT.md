# Onboarding Coverage Audit

**Scope.** Audit whether `OnboardingScreen.kt` (9 pages) + `TierOnboardingSheet`
ask the user about every *major* setting/mode/feature before the app silently
enables it. Compare against the ~90 user-toggleable preferences cataloged
across `app/src/main/java/com/averycorp/prismtask/data/preferences/` and
the settings sub-screens.

## Premise verification (the one place this matters)

The literal request — "comprehensively go through every major setting and
mode" — would, taken absolutely, become a 30+-page onboarding pager.
~90 user-facing toggles exist. That is the wrong shape: drop-off would
spike, and toggles like "Briefing Read-Aloud" (`NotificationPreferences:473`)
or "Watch Haptic Intensity" (`:544`) carry no real consent value.

**Reinterpret the spirit, not the letter.** What actually matters is
informed consent for:

1. **Default-ON behavior the user never asked for** — daily/evening/weekly
   notification streams, Life Modes (5 enabled flags, all default-ON),
   voice input + AI features, Morning Check-In banner, Work-Life Balance bar,
   Eisenhower auto-classify, Forgiveness streaks. This is the largest gap.
2. **Privacy-sensitive opt-ins** — mic permission, AI calls, mood logging,
   Calendar/Drive scopes. Currently surfaced as just-in-time permission
   prompts or buried in Settings.
3. **First-use config that gates the app's core experience** — Start-of-Day
   (currently a separate post-onboarding modal), notification permission,
   life-mode pick (currently silently ALL-ON).

Items that do NOT belong in onboarding: per-widget config (already has its
own per-instance UI), custom sounds, escalation chains, Boundary rules,
Pomodoro durations, Smartwatch haptic intensity, Mood-logging weights
(POWER-tier Advanced Tuning), individual collaboration channel toggles.
Defer those to first-use of the relevant feature.

The audit therefore evaluates *grouped consent screens*, not 1:1 toggle
coverage. Each PROCEED item below adds **at most one onboarding page**.

## Baseline coverage (what's asked today)

| Asked today | Where | Status |
|---|---|---|
| UI complexity tier | `TierOnboardingSheet.kt` | GREEN |
| Theme palette | `ThemePickerPage` | GREEN |
| Brain Mode (ADHD/Calm/Focus & Release) | `BrainModePage` | GREEN |
| Routine steps (morning/bedtime/housework) + leisure music/flex picks | `TemplatesPage` → `applyTemplateSelections` | YELLOW (seeds *steps*, NOT the per-mode `*_ENABLED` flags) |
| Optional Google sign-in | `WelcomePage` + `SetupPage` | GREEN |
| First task | `SetupPage` | GREEN (icebreaker, fine to keep) |
| Start-of-Day | **Post-onboarding** modal at `MainActivity.kt:379-398` | YELLOW (works, but lives outside onboarding) |
| Notification permission | Banner inside `NotificationsScreen` + `MainActivity` resume rationale | YELLOW (never proactively asked during onboarding) |
| Calendar scope | Just-in-time on connect attempt in `CalendarScreen` | GREEN (correct UX, no onboarding change needed) |
| Mic permission | First-use of voice input | YELLOW (voice input itself defaults ON without consent) |

## Items audited

### A — Permissions & privacy

**A1. POST_NOTIFICATIONS permission (RED).** `MainActivity` requests it on
resume if missing; the user can have skipped onboarding without ever seeing
*why* notifications matter. With briefings/summaries/overload-alerts all
default-ON, denying the permission silently breaks 7 features. Recommend a
proactive onboarding page that frames the request before triggering it.
**PROCEED** — fold into Notifications & Briefings page (B).

**A2. Microphone + voice input (RED).** `VoicePreferences.VOICE_INPUT_ENABLED`
defaults ON (`VoicePreferences:31`), as do `VOICE_FEEDBACK_ENABLED` and
`CONTINUOUS_MODE_ENABLED`. Mic permission is requested only on first
voice-input use, but the *enabled* flags default-on without consent.
**PROCEED** — fold voice opt-in into Privacy & Permissions page (P).

**A3. AI features master toggle (YELLOW).** `AiFeaturePrefs.enabled`
defaults to ON (`UserPreferencesDataStore.kt:137`) — every backend AI call
(NLP parsing, briefings, Eisenhower classification, Pomodoro coaching) is
authorized by this single flag without explicit user opt-in. Privacy-sensitive
in some jurisdictions/contexts. **PROCEED** — fold into Privacy & Permissions
page.

**A4. Mood/Energy logging (GREEN).** Defaults OFF in
`AdvancedTuningPreferences`; surfaced only behind POWER-tier UI. Users who
care can find it. No onboarding gap. **STOP — no work needed.**

### B — Default-ON notification streams

`NotificationPreferences.kt` defaults each to `true` at the indicated line:

- `dailyBriefingEnabled` (`:250`)
- `eveningSummaryEnabled` (`:257`)
- `weeklySummaryEnabled` (`:264`) + `weeklyTaskSummaryEnabled` (`:271`)
- `overloadAlertsEnabled` (`:278`)
- `streakAlertsEnabled` (`:419`)
- `reengagementEnabled` (`:285`)
- `weeklyReviewAutoGenerateEnabled` + `weeklyReviewNotificationEnabled` (`:629`,`:636`)

**Verdict: RED.** Eight notification streams fire by default with zero
consent. **PROCEED** — single Notifications & Briefings page with grouped
checkboxes ("Daily morning briefing", "Daily evening summary",
"Weekly review", "Overload / streak alerts", "Re-engagement nudges").
Combined with the POST_NOTIFICATIONS permission ask (A1).

Individual collaboration channel toggles (`collabAssignedEnabled` etc.,
`:491-519`) are **DEFER** — they only matter once a project is shared, so
ask at first share-event.

### C — Life Modes default-ON without consent (RED)

`HabitListPreferences` defaults all five enabled flags to `true`:
`SELF_CARE_ENABLED:108`, `MEDICATION_ENABLED:112`, `SCHOOL_ENABLED:116`,
`LEISURE_ENABLED:120`, `HOUSEWORK_ENABLED:140`.

The current `TemplatesPage` lets the user pick *steps* inside each routine
(applied via `selfCareRepository.seedSelfCareSteps`), but the per-mode
`*_ENABLED` flag is never written from onboarding — so even if the user
deselects every morning-routine step, the Self-Care section still appears.

A user who doesn't take medication still gets a Medication mode card on
Today + a Medication section in Habits + medication notifications in the
"all on by default" notification set.

**PROCEED** — add a "What to track" page that maps the 5 enabled flags
to user-friendly toggles. Page should run *before* `TemplatesPage` so that
templates only show pickers for the modes the user kept enabled. The
existing template picks become opt-in within each enabled mode.

### D — Default-OFF integrations the user is never offered

**D1. Google Calendar sync (YELLOW).** OFF by default. The connect attempt
in `CalendarScreen` correctly triggers the scope grant just-in-time, but
many users never discover the feature. **PROCEED** — single "Connect your
calendar?" page with one button → opens the existing `CalendarScreen`
connect flow. Skip-friendly.

**D2. Google Drive backup (YELLOW).** Same shape as D1 — OFF by default,
discoverable only via Settings → Data & Backup. **PROCEED** — fold
into the same "Connect" page as D1, two cards.

**D3. Smart defaults (`TaskDefaults.smartDefaultsEnabled`, default OFF,
`UserPreferencesDataStore:47`).** Niche; no clear user mental model
without seeing tasks first. **DEFER** to first-use suggestion banner.

**D4. Boundary rules / WLB / Burnout (DEFER).** Configuration is rich and
context-dependent; one onboarding screen would not capture intent. Better
surfaced via a "Did you know?" card inside the Wellbeing screen on first
visit.

**D5. Smartwatch sync (DEFER).** Only relevant if a watch is paired;
checking pairing state mid-onboarding adds latency for zero value.

### E — Accessibility (default-OFF, never surfaced)

`A11yPreferences` defaults all OFF: `REDUCE_MOTION:31`,
`HIGH_CONTRAST:35`, `LARGE_TOUCH_TARGETS:39`. Users who need them today
must hunt through `Settings → Accessibility`.

**Verdict: YELLOW (RED for affected users).** **PROCEED** — single
Accessibility page with three switches + skip. Bundling makes this a
1-page cost, ~50 lines of Compose. High discoverability win.

### F — Configurations the audit explicitly defers

| Item | Why deferred |
|---|---|
| Pomodoro durations / coaching | Defaults are sensible; first-use prompt handles intent. |
| Widget setup | Each widget already has per-instance config UI on add. |
| Dashboard section visibility / collapse | Defaults work; user changes only after seeing them. |
| Swipe actions (left/right) | Defaults match user expectation (right=complete, left=delete). |
| Notification profile + escalation chains | Advanced, depends on knowing what alerts feel like first. |
| Custom sounds + vibration patterns | Per-profile, advanced; defer to profile config. |
| Daily Essentials (housework + schoolwork pick) | Depends on which Life Modes the user kept (C); ask at first Today-render once those modes are known. |
| Subscription tier choice | The free tier is functional; Pro-gated features show a paywall card on tap. |
| Habit nag suppression (7d default) | Affects only edge cases; no consent signal needed. |
| Forgiveness streaks (default ON) | Behavior is forgiving — failure mode is "more lenient than expected", not "surprising data exposure". |

These defer-items will be re-evaluated after Phase 2 ships, NOT folded
into onboarding without further evidence.

### G — Existing post-onboarding gates worth folding in

**G1. Start-of-Day picker (YELLOW).** Lives at `MainActivity.kt:379-398`,
fires after `setOnboardingCompleted()` if `hasSetStartOfDay == false`. It's
already mandatory and can't be skipped. The split (onboarding flow ends,
modal pops) costs a context jump for the user. **PROCEED** — add a "Day
Setup" page late in onboarding (between BrainMode and Setup), and remove
the post-onboarding modal once the page sets `hasSetStartOfDay`.

  Migration risk: existing v1.4.0+ users whose flag is already `true`
  must skip the new page (already handled by reading `hasSetStartOfDay`
  state). Fresh installs get the in-flow page only.

**G2. Notification permission rationale.** Currently a banner inside
`NotificationsScreen` + a re-check on `MainActivity.onResume`.
**PROCEED** — fold the proactive ask into the Notifications & Briefings
page (B); leave the banner as a deny-recovery affordance.

**G3. Battery optimization, exact alarm dialogs.** Already correctly
just-in-time. **STOP — no onboarding change.**

### H — Other UX clarifications

**H1. `HabitsPage` is purely informational (YELLOW).** No opt-in. With
forgiveness streaks defaulting ON and habit nag suppression defaulting
to 7 days, an active page that asks "How forgiving should your streak
be?" would be a small addition with high discoverability win.
**PROCEED** — small add to existing Habits page (no new page, just
an interactive card).

**H2. `SmartTasksPage`, `NaturalLanguagePage`, `ViewsPage` — informational
only (GREEN).** They sell features. Keep as-is — they pace the
opt-in pages so the flow doesn't feel like a permissions barrage.

**H3. Skip button on every page until SetupPage (GREEN).** Already in
place at `OnboardingScreen.kt:140-151`. Preserves user-in-control. New
pages must inherit the same Skip affordance.

## Improvement table — ranked by wall-clock-savings ÷ cost

Cost: L = ≤200 lines of new Compose + 1 unit test; M = 200-400 lines + ViewModel wiring; H = >400 lines.

| Rank | Item | Verdict | Cost | Notes |
|---|---|---|---|---|
| 1 | **Life Modes opt-in page (C)** | PROCEED | M | Highest value; gates 5 default-ON modes the user never asked for. Must run before TemplatesPage so templates only show enabled modes. |
| 2 | **Notifications & Briefings page (B + A1)** | PROCEED | M | 8 default-ON streams + permission ask. One page, grouped checkboxes. |
| 3 | **Privacy & Permissions page (A2 + A3)** | PROCEED | M | Voice input + AI master toggle + mood logging informational link. |
| 4 | **Day Setup page (G1)** | PROCEED | L | Fold the existing post-onboarding modal into the pager. Net page count change: 0 (one removed, one added). |
| 5 | **Connect (Calendar + Drive) page (D1 + D2)** | PROCEED | L | Two cards on one page; skip-friendly. |
| 6 | **Accessibility quick-set page (E)** | PROCEED | L | Three switches + skip. |
| 7 | **HabitsPage interactive card (H1)** | PROCEED | L | Forgiveness slider + streak tolerance choice on existing page; no new page. |
| 8 | **TemplatesPage filter (depends on #1)** | PROCEED | L | Only show routine pickers for modes the user kept enabled. |
| — | Boundary rules, Pomodoro coaching, Smart defaults, etc. | DEFER | — | Listed in F. Re-evaluate post-Phase 2. |

**Phase 2 page count delta:** +6 new pages, −1 removed modal (G1).
9 → 15 onboarding pages. Each ≤30 sec to dismiss with Skip; Skip
button stays available except on SetupPage. Each new page is also
*independently shippable* — Phase 2 can land them as separate PRs.

## Anti-patterns flagged (don't necessarily fix)

- **22 individual notification toggles in onboarding** — would create
  notification fatigue *during onboarding itself*. Group them (B).
- **Pre-checking permissions for the user** — never auto-accept
  permissions on the user's behalf; always show the system dialog.
- **Defaulting all 5 Life Modes ON** — surprising for users who only
  want a subset. Fix in C, but the underlying defaults stay ON for
  *upgrades*; only fresh installs go through the new page.
- **Silently enabling 8+ notification streams** — fix in B.
- **Voice input enabled by default before mic permission exists** — the
  enabled flag does nothing until the permission grants, but the data
  model invites surprising behavior on permission grant. Fix in A2.
- **`!adhdSelected.not()` toggle dance** in `OnboardingScreen.kt:721`,
  `:741`, `:762` — readable but odd. Out of audit scope; flag for a
  separate cleanup if the cards get touched.

## Reference: file inventory used

- Onboarding flow: `app/src/main/java/com/averycorp/prismtask/ui/screens/onboarding/{OnboardingScreen,OnboardingViewModel,TierOnboardingSheet}.kt`
- Settings hub: `app/src/main/java/com/averycorp/prismtask/ui/screens/settings/SettingsScreen.kt`
- Preferences: `app/src/main/java/com/averycorp/prismtask/data/preferences/{NotificationPreferences,HabitListPreferences,VoicePreferences,A11yPreferences,UserPreferencesDataStore,NdPreferencesDataStore,LeisurePreferences,MorningCheckInPreferences,TaskBehaviorPreferences,ThemePreferences}.kt`
- Post-onboarding gate: `app/src/main/java/com/averycorp/prismtask/MainActivity.kt:379-398`

---

(Phase 2 implementation summary will be appended below once PRs land.)
