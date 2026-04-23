# Phase G — Web Parity Prompt Template

**Purpose:** fill-in-the-blank prompt for the Phase G execution agent(s).
This is the companion to `docs/WEB_PARITY_GAP_ANALYSIS.md` — the audit
document is the analysis; this file is how you invoke the work.

---

## Current state

Pre-Phase-G, six high-leverage slices landed:

- **NLP batch ops** ([PR #711](https://github.com/akarlin3/prismTask/pull/711))
  — `/ai/batch-parse` wired with BatchPreview + 30s undo + 24h Settings
  history. Scope: TASK RESCHEDULE / DELETE / COMPLETE / PRIORITY_CHANGE /
  PROJECT_MOVE, HABIT COMPLETE / SKIP / ARCHIVE / DELETE, PROJECT ARCHIVE /
  DELETE. Deferred: TAG_CHANGE, MEDICATION.
- **Onboarding + 4 named themes** ([PR #712](https://github.com/akarlin3/prismTask/pull/712))
  — Cyberpunk / Synthwave / Matrix / Void color tokens, theme picker,
  9-page onboarding wizard, per-account Firestore completion flag.
  Deferred: per-theme typography, shape, decorative personality flags.
- **Daily briefing + weekly planner** ([PR #714](https://github.com/akarlin3/prismTask/pull/714))
  — `/briefing` wires `POST /ai/daily-briefing`, `/planner` wires
  `POST /ai/weekly-plan` with a preferences drawer. Both Pro-gated; the
  weekly-plan 429 rate-limit has a readable toast.
- **Analytics dashboard** ([PR #715](https://github.com/akarlin3/prismTask/pull/715))
  — `/analytics` wires `GET /analytics/summary` + `productivity-score` +
  `time-tracking` + `habit-correlations` (Recharts area + bar charts). Uses
  `Promise.allSettled` for graceful per-endpoint failure. Skips
  `project-progress` (backend expects int ID; web has string Firestore IDs).
- **Conversation extraction** ([PR #717](https://github.com/akarlin3/prismTask/pull/717))
  — `/extract` wires `POST /ai/tasks/extract-from-text`. Textarea paste
  (10k chars) + candidate toggles + commit to Firestore. Pro-gated.
- **Pomodoro+ coaching** ([PR #718](https://github.com/akarlin3/prismTask/pull/718))
  — self-contained `PomodoroCoachPanel` on `PomodoroScreen` wires
  `POST /ai/pomodoro-coaching` across all three triggers (pre_session /
  break_activity / session_recap) inferred from the existing
  `SessionPhase`. Pro-gated.

All six primary backend AI endpoints (batch-parse, daily-briefing,
weekly-plan, time-block, weekly-review, extract-from-text,
pomodoro-plan, pomodoro-coaching, eisenhower) are now wired on web —
the only AI endpoint remaining unwired is `eisenhower/classify_text`,
a text-only variant of the existing Eisenhower flow.

The web app's data-access split (Firestore-direct for tasks / projects /
habits / tags; backend REST for AI / dashboard / daily-essentials / auth /
admin / export) is documented in `docs/WEB_PARITY_GAP_ANALYSIS.md` §1.

---

## Phase G remaining scope

Ordered by leverage — pick slices in this order unless product priorities
change. Each is sized so it can ship as one PR.

### Track A — Backend-ready (web only; ship first)

Rough sub-total: **6–10 working days** (was 9–13 before slices 5–6
shipped).

1. **Task editor tabbed parity** — add Schedule + Organize tabs to
   `features/tasks/TaskEditor.tsx` to match Android's `addedittask/tabs/`.
   ~M, ~2 days.
2. **Today polish** — SoD prompt section + dashboard layout customization
   (section order + visibility via DashboardPreferences equivalent).
   Consider also embedding a mini briefing card that links to `/briefing`.
   ~S, ~1 day.
3. **Medication screen + tier picker + Settings editor** — dedicated
   `/medication` route + Settings section mirroring Android's
   `MedicationScreen.kt` and `DailyEssentialsSettingsSection.kt`, consuming
   the existing `/daily-essentials/*` endpoints. ~M, ~2–3 days.
4. **Templates parity** — habit templates + project templates (task
   templates already exist). Add modal editors and quick-use shortcuts.
   ~M, ~2 days.
5. **Settings sections** — port ~22 Android settings sections: Accessibility
   polish, GoogleCalendar, DailyEssentialsSettings, AboutSection,
   DashboardSection, AI overrides, SubscriptionSection polish, plus smaller
   toggles. Parallelizable across multiple PRs. ~L, ~3–5 days.
6. **Theme polish (slice 2 follow-up)** — per-theme typography (Chakra
   Petch / Rajdhani / Share Tech Mono / Space Grotesk via Google Fonts),
   shape (radius / chipShape / density), decorative personality flags
   (brackets, terminal, editorial, sunset) via a `ThemedCard`-style
   component. ~M–L, ~3–5 days.
7. **TAG_CHANGE batch mutation** — enable web task↔tag Firestore
   persistence (decision: embed tag IDs on task doc vs. separate
   cross-ref collection), then flip the applier in
   `features/batch/batchApplier.ts` from deferred to apply. ~M with
   persistence included, ~1 day once persistence exists.
8. **Analytics project-progress (needs backend change first)** — web
   projects use string Firestore IDs, backend `/analytics/project-progress`
   takes an int. Backend must accept string IDs (Track B item) before
   this web slice is shippable. ~S once unblocked.
9. **Eisenhower classify_text** — small unwired AI variant for text-only
   classification without a task ID. ~S, <1 day.

### Track B — Backend work required (bounce to a separate prompt first)

Needs new FastAPI endpoints + SQL schema before the web UI can wire up.
Rough sub-total: **8–12 working days** of combined backend + web work.

1. **Mood & energy tracking** — needs `mood_energy_logs` tables + endpoints
   (`POST /mood-energy-logs`, `GET /mood-energy-logs?range=…`,
   correlation analysis). Android's `MoodCorrelationEngine` is the target.
2. **Morning check-in + streak** — needs `check_in_logs` tables +
   endpoints. Android's `MorningCheckInResolver` is the target.
3. **Boundaries + BoundaryEnforcer** — needs `boundary_rules` tables +
   enforcement service.
4. **Notification profiles + custom sounds + escalation** — needs multiple
   tables; also needs a Web Push story (web cannot mirror local alarm
   scheduling — this will be a different UX from Android).
5. **NLP shortcuts + saved filters** — needs small tables; low-priority.
6. **Medication refills + clinical report generator** — needs
   `medications` + `medication_refills` + `ClinicalReportGenerator`
   equivalent.
7. **Work-Life balance** — needs `life_category` exposed cleanly on the
   backend task schema; the AI batch context already references it.

---

## Workflow constraints

These are load-bearing and caused the two pre-Phase-G slices to ship cleanly.
Keep them:

- **One slice per PR.** Do not bundle. Each PR should be independently
  reviewable, auto-mergeable, and small enough that rolling it back is
  cheap.
- **No backend changes from a web prompt.** If a slice needs a new endpoint,
  bounce to a backend prompt first, then come back.
- **No Android changes from a web prompt.** Same reason. The one exception
  is if the Android source is wrong (e.g. a stale enum value) — flag it,
  don't silently edit.
- **Stack on existing Firestore direct-write path for task / project /
  habit / tag data.** The backend REST client at `src/api/tasks.ts` exists
  but is dormant; don't revive it in Phase G unless explicitly asked. Any
  new mutation should go through `src/api/firestore/*.ts`.
- **Pro gating via `useProFeature`.** Follow slice 1's pattern — on gated
  intent, open `ProUpgradeModal`.
- **Testing expectation matches slice 1/2.** Every slice should land with:
  - vitest unit tests for stores / reducers / pure helpers
  - vitest API wrapper test with `vi.hoisted` mock of axios / Firestore
  - a one-test Playwright spec that at minimum verifies the route redirects
    correctly when unauthed (regression guard for lazy-loaded routes)
- **CHANGELOG Unreleased entry** under the `### Web` subsection with a PR
  link.
- **Auto-merge after CI green.** CI runs lint + vitest + build + Playwright
  E2E. Don't force-merge without CI.

---

## Prompt shape

When picking up a slice from Track A, use this prompt shape. Fill in
the bracketed bits:

> You are continuing the web parity push. Read
> `docs/WEB_PARITY_GAP_ANALYSIS.md` and this file
> (`docs/WEB_PARITY_PHASE_G_PROMPT_TEMPLATE.md`) first — they carry the
> context and the constraints.
>
> **Slice:** [slice name from Track A above]
> **Scope:**
>   - [list what's in scope by file path + component name]
>   - [list what's NOT in scope — explicitly deferred]
> **Endpoints consumed:** [e.g. `POST /ai/daily-briefing`,
> `GET /analytics/summary`]
> **Data integration path:** [Firestore direct / backend REST / both]
> **Pro-gated?** [yes / no + reason]
> **Estimated size:** ~[N] LOC across [M] new files + [K] edits
>
> Follow the workflow constraints section of the template. Open a PR
> titled `feat(web): [slice name] — slice [N] of Phase G`. Ship with
> tests and a CHANGELOG entry.

For Track B (backend-blocked) slices, split into two prompts — one for
the backend work, one for the web follow-up — and link them as blocked
/ blocking.

---

## Do not

- Do not attempt multiple Track A slices in one prompt. Prior attempts
  produced shallow stubs that compiled but didn't work.
- Do not regenerate the entire SettingsScreen in a single slice. The
  ~22 Android sections naturally split into 3–4 themed PRs
  (Accessibility bundle, AI bundle, Integrations bundle, etc.).
- Do not introduce a new state library (Redux Toolkit, Jotai, etc.) —
  stay on Zustand.
- Do not rewrite `NLPInput.tsx` into a separate `QuickAddBar`. Slice 1
  intentionally extended the existing component so batch + single-task
  flows share one intake point. Add to it, don't split it.
- Do not change the priority-scale mapping on web. Web uses
  1=urgent..4=low; Android uses 0=none..4=urgent. `converters.ts`
  handles the round-trip. Any new code that sends priority to the
  backend goes through `webToAndroidPriority`.
- Do not move web tasks off Firestore-direct onto backend REST as part
  of a parity slice. That is a data-model migration, not a parity
  question, and needs its own prompt.

---

## When you're done

Update `docs/WEB_PARITY_GAP_ANALYSIS.md` with DONE markers on the shipped
rows, update the "remaining-gaps" subtotal, and append a short entry to
this file under the `## Shipped in Phase G` heading below with PR links.

---

## Shipped in Phase G

*(empty — update this section as Phase G work lands)*
