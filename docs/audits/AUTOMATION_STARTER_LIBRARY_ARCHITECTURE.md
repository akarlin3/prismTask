# Automation Starter Library — Architecture Spec

**Status:** Phase 1 audit. Phase 2 fan-out auto-fires on this same branch (operator directive 2026-05-02: "don't block phase two; don't defer anything unless necessary"). Single-PR ship to `claude/automation-starter-library-roo9B`.
**Predecessor:** PR #1056 — Automation/Rules Engine (`docs/audits/AUTOMATION_ENGINE_ARCHITECTURE.md`).
**Audit doc cap:** 500 lines (this doc, ~470 lines).
**Roadmap slot:** README Roadmap "Phase I — Rule template library (starter automations)", `done: 0 → 1.0` after this PR merges.

## Premise verification (against `cc382eb` HEAD)

| Premise (from operator prompt) | Reality | Status |
|---|---|---|
| PR #1056 engine architecture exists at `docs/audits/AUTOMATION_ENGINE_ARCHITECTURE.md` | Confirmed (520 lines, A1-A10 picks made, `cc382eb`). | GREEN |
| Sample rules at `app/src/main/assets/automation/sample_rules.json` | File exists (5 rules, 62 lines). | GREEN |
| `sample_rules.json` is the source the seeder reads | **NO.** `AutomationSampleRulesSeeder.kt` defines `private val SAMPLES = listOf(...)` inline as Kotlin. The JSON file is a documentation artifact — nothing parses it. | YELLOW (drives A7) |
| Engine reuses `BatchOperationsRepository.applyBatch` via `apply.batch` | Confirmed via `ApplyBatchActionHandler` registration. | GREEN |
| `AutomationRuleListScreen` is wired and routes from Settings | Confirmed: `SettingsScreen.kt:329`, `SettingsRoutes.kt:65-70`, `NavGraph.kt:272`. | GREEN |
| `AutomationRuleEntity` already carries `templateKey` + `isBuiltIn` | Confirmed (DDL in engine doc § A2). No template metadata migration needed. | GREEN |
| `AutomationRuleRepository.create()` calls `syncTracker.trackCreate(id, "automation_rule")` | Confirmed (line 67). | GREEN |
| SyncService consumes `automation_rule` tracker entries | **NO.** `grep -rn "automation\|Automation" data/remote/SyncService.kt data/remote/mapper/` returns zero hits. SyncTracker logs the entity type; SyncService doesn't dispatch on it. | RED (drives A6) |
| `mutate.task` handler supports `tagsAdd` / tag mutation | **NO.** `SimpleActionHandlers.kt:88-97` enumerates: title, description, priority, dueDate, isFlagged, lifeCategory, projectId. Tags absent. | RED (drives A3 + A0 extension) |
| `mutate.medication` works | **NO.** Returns `ActionResult.Skipped("medication mutations deferred to v1.1 — needs slot/tier-state coherence audit")` (`SimpleActionHandlers.kt:189-193`). | RED — necessary defer |
| `schedule.timer` works | **NO.** Returns `Skipped` with message "timer scheduling deferred to v1.1" (`SimpleActionHandlers.kt:159-172`). | RED — necessary defer |
| Engine supports day-of-week triggers / conditions | **NO.** `AutomationTrigger.TimeOfDay` is daily-only (hour/minute). No `event.dayOfWeek` field. Listed condition fields end at `event.occurredAt`. | RED (drives A0 extension) |
| Existing test coverage for automation | One file: `AutomationJsonAdapterTest.kt`. | YELLOW |

**Net premise correction:** the operator's "Option C — promote sample_rules.json to canonical library" assumes JSON is the seeder's source; it isn't. A7 picks Kotlin-native instead, deletes the dead-letter JSON, and treats this audit doc as the human-readable inventory.

---

## A1 — Reference library survey (half-page)

Surveyed four production automation libraries to inform category and rule design:

| Library | Pattern that mattered | Lesson |
|---|---|---|
| **IFTTT** applets | "If [trigger] then [single action]" — applet card on Discover tab is icon + 1-line description. | Most-used applets are notification-shaped, not mutation-shaped. Users trust notifications; mutations get scrutinized. → Bias the inventory toward `notify` actions. |
| **Apple Shortcuts** gallery | Curated rows by intent ("Get Started", "Productivity", "Wellbeing"). Sequential workflows, but the gallery is single-action-shaped. | Categorization by **goal** (Productivity / Wellbeing) outperforms categorization by **entity** (Files / Calendar) for browse-friendliness. → CAUSE-C in A2. |
| **Samsung Routines** | Trigger-rich (location, battery, calendar event). Action set is narrower. | Trigger surface is the upper-bound on rule expressiveness — bound rule design to *current* trigger surface, not aspirational. PrismTask's triggers: EntityEvent, TimeOfDay, Manual, Composed. Day-of-week missing — extend or constrain. |
| **Tasker** profiles | Power-user shape: composed conditions, variable scoping. Niche audience. | Composed-trigger rules are valuable but should be a minority of the library (≤10%). Most users want one-tap installs that work. |

**Three convergent design priors derived from the survey:**

1. **Notify-shaped > mutation-shaped** for the headline rules. Users will toggle a notification on faster than they trust an auto-mutation. Library leads with notify rules; mutations are the "after I trust the engine" expansion.
2. **Goal-first categorization** beats entity-first. Users browse by "what am I trying to do" not "what's the data shape".
3. **Bounded by current capability.** Don't ship rules that need handlers/triggers PrismTask doesn't have. Either extend the capability (necessary engine work) or drop the rule. Per operator directive: extend when extension is bounded; defer only when the extension itself needs a separate audit.

---

## A2 — Categorization scheme: CAUSE-C (goal-first hybrid)

| Candidate | Pick? | Reason |
|---|---|---|
| CAUSE-A — by entity type (Tasks / Habits / Medications / Focus / Mixed) | NO | Groups dissimilar use cases. "Notify overdue task" + "Auto-flag urgent" both Tasks but feel orthogonal to a browser. |
| CAUSE-B — by user goal (Stay on top / Build habits / etc.) | NO | Fits 80% of rules cleanly, but a small "Power user" tail fits poorly. |
| **CAUSE-C — hybrid: 6 goal categories + 1 power-user tail** | **YES** | Pattern Notion / Asana / ClickUp use. Operator's suggested 7-category list maps almost 1-to-1 to the rule inventory. |

**Final category list (7):**

1. **Stay on top of work** — overdue / urgent / morning kickoff / EOD review
2. **Build healthy habits** — streak milestones, daily check-in nudges, weekly habit review
3. **Medication adherence** — daily reminders, weekly adherence summary
4. **Focus + deep work** — manual focus kickoff, AI summarize completions, mid-day focus prompt
5. **Reduce friction** — auto-tag, auto-categorize by keyword, auto-flag urgent
6. **Wellness check-ins** — morning mood, midday pause, evening reflection, Sunday weekly review
7. **Power user** — manual AI briefing, daily/weekly AI summary

---

## A0 — Engine extensions (necessary, not deferred)

Per operator directive "don't defer anything unless necessary", three engine gaps surfaced in premise verification become Phase 2 PR-A0 work rather than rule cuts. Each is bounded and doesn't re-open PR #1056's outstanding 15% (which is specifically: SyncService routing for `automation_rule`, on-device validation, backend AI endpoint Python files).

| Extension | What | Files | LOC | Rationale |
|---|---|---|---|---|
| **Tag mutation on `mutate.task`** | Add `tagsAdd: List<String>` and `tagsRemove: List<String>` keys to `MutateTaskActionHandler` updates map. Reuses existing `TaskRepository.updateTask` (tags is already a column on `TaskEntity`). | `domain/automation/handlers/SimpleActionHandlers.kt` | ~40 | Unblocks rules #16, #17 (auto-tag rules — central to "Reduce friction" category). Without this, the entire category becomes log-shaped placeholders. |
| **`task.createdAt` field path** | Add `task.createdAt` as a resolvable field in `EvaluationContext`. Single switch entry. | `domain/automation/EvaluationContext.kt` (or wherever field resolution lives) | ~10 | Optional — keeps door open for "stale task" rules in v2 without re-touching evaluator. Ship for completeness. |
| **`DayOfWeekTime` trigger + `event.dayOfWeek` field** | (a) New `AutomationTrigger.DayOfWeekTime(daysOfWeek: Set<DayOfWeek>, hour: Int, minute: Int)` variant. (b) `event.dayOfWeek` derived from `event.occurredAt` via `LocalDate.ofEpochSecond(...).dayOfWeek`. JSON shape: `{"type":"DAY_OF_WEEK_TIME","daysOfWeek":["SUNDAY"],"hour":18,"minute":0}`. | `AutomationTrigger.kt`, `AutomationJsonAdapter.kt`, `AutomationTriggerScheduler` (worker dispatches when day-of-week matches), `EvaluationContext.kt` | ~150 | Unblocks rules #9, #12, #17 (as condition), #24, #27 — five rules across four categories. Without this, weekly rules cut entirely or fake-shipped as daily. Operator directive prefers extension. |

**Necessary defers (kept):**
- `mutate.medication` — handler doc-comment explicitly says "needs slot/tier-state coherence audit; mirror BatchOperationsRepository.applyMedicationMutation". Audit-first protocol applies. Out of starter-library scope. → No medication-mutating rules in the library.
- `schedule.timer` — handler doc-comment says "needs service-start permission flow that's better landed alongside rule-edit screen in v1.1". → No timer-starting rules in the library.
- SyncService routing for `automation_rule` — explicit operator carve-out from the original prompt ("DO NOT make changes that re-open PR #1056's outstanding 15%"). Imported templates are device-local until that lands. Acceptable per A6.

---

## A3 — Rule design discipline

Each library rule must satisfy:

1. **Self-contained** — no dependency on user data not present at install (no hard-coded `projectId`, no user-specific tag lists).
2. **Sensible defaults** — ships disabled (`default_enabled = false`), but if the user toggles on without editing, it produces useful output for a typical user. Notification copy in Title Capitalization per project convention.
3. **Clear value statement** — 1 sentence description, one sentence on when to use it.
4. **Reversible** — disabling = no side effects. No orphan state on toggle-off.
5. **Safe** — no rule fires destructive actions. (Library does not include `delete`-shaped mutations; the engine's `mutate.task` doesn't support delete anyway.)
6. **Composable** if applicable — marked `requiresAi = true` for AI-action rules; UI surfaces an "AI" pill (already implemented per `AutomationRuleListScreen.kt:145-150`).
7. **Capability-checked** — every rule's trigger / condition / action must be expressible against the post-A0 engine. No rule depends on `mutate.medication`, `schedule.timer`, or SyncService routing.

---

## A4 — Rule inventory (27 rules)

IDs follow `starter.<category>.<slug>` shape. Five rules carry the existing seed `templateKey` (kept stable to preserve back-compat with already-installed users). The other 22 are net-new.

### Stay on top of work (5)

| ID | Trigger | Condition | Action |
|---|---|---|---|
| `starter.stay_on_top.notify_overdue_urgent` (seed: `builtin.notify_overdue_urgent`) | TaskUpdated | priority≥3 AND dueDate<now AND NOT EXISTS completedAt | notify "Overdue Urgent Task" |
| `starter.stay_on_top.notify_high_priority_created` | TaskCreated | priority≥3 | notify "High-Priority Task Added" |
| `starter.stay_on_top.morning_kickoff` (seed: `builtin.morning_routine`) | TimeOfDay 7:00 | — | notify "Good Morning, Plan Your Day" |
| `starter.stay_on_top.evening_review` | TimeOfDay 21:00 | — | notify "Review Tomorrow's Plan" |
| `starter.stay_on_top.weekend_planning` | DayOfWeekTime SUN 17:00 | — | notify "Plan The Week Ahead" |

### Build healthy habits (4)

| ID | Trigger | Condition | Action |
|---|---|---|---|
| `starter.habits.streak_7` (seed: `builtin.streak_achievement`) | HabitStreakHit | streakCount≥7 | notify + log |
| `starter.habits.streak_30` | HabitStreakHit | streakCount≥30 | notify "30-Day Streak Milestone" |
| `starter.habits.streak_100` | HabitStreakHit | streakCount≥100 | notify "100-Day Streak — Legendary" |
| `starter.habits.weekly_habit_review` | DayOfWeekTime SUN 18:00 | — | notify "Review Your Habits This Week" |

### Medication adherence (3)

| ID | Trigger | Condition | Action |
|---|---|---|---|
| `starter.med.morning_reminder` | TimeOfDay 8:00 | — | notify "Morning Medications" |
| `starter.med.evening_check` | TimeOfDay 20:00 | — | notify "Evening Medications" |
| `starter.med.weekly_ai_summary` | DayOfWeekTime SUN 19:00 | — | ai.summarize scope="recent_completions" maxItems=14 |

### Focus + deep work (3)

| ID | Trigger | Condition | Action |
|---|---|---|---|
| `starter.focus.ai_summarize_completions` (seed: `builtin.ai_summary_completions`) | TaskCompleted | — | ai.summarize scope="recent_completions" maxItems=50 |
| `starter.focus.manual_kickoff` | Manual | — | notify "Focus Session Started — Phone Away" |
| `starter.focus.midday_focus_block` | TimeOfDay 14:00 | — | notify "Time For A 25-Min Focus Block" |

### Reduce friction (5)

| ID | Trigger | Condition | Action |
|---|---|---|---|
| `starter.friction.autotag_today` (seed: `builtin.autotag_today`) | TaskCreated | — | mutate.task tagsAdd=["#today"] *(upgraded from log-only via A0 extension)* |
| `starter.friction.auto_flag_urgent` | TaskCreated | priority≥4 | mutate.task isFlagged=true |
| `starter.friction.weekend_personal_tag` | TaskCreated | event.dayOfWeek IN ["SATURDAY","SUNDAY"] | mutate.task tagsAdd=["#personal"], lifeCategory="PERSONAL" |
| `starter.friction.categorize_health_keyword` | TaskCreated | title CONTAINS "doctor" OR title CONTAINS "dentist" OR title CONTAINS "appointment" | mutate.task lifeCategory="HEALTH" |
| `starter.friction.categorize_work_keyword` | TaskCreated | title CONTAINS "meeting" OR title CONTAINS "sync" OR title CONTAINS "1:1" | mutate.task lifeCategory="WORK" |

### Wellness check-ins (4)

| ID | Trigger | Condition | Action |
|---|---|---|---|
| `starter.wellness.morning_mood_check` | TimeOfDay 7:30 | — | notify "How Are You Feeling This Morning?" |
| `starter.wellness.midday_pause` | TimeOfDay 13:00 | — | notify "Stand Up, Drink Water, Reset" |
| `starter.wellness.evening_reflection` | TimeOfDay 21:30 | — | notify "Take A Moment To Reflect" |
| `starter.wellness.sunday_review` | DayOfWeekTime SUN 17:30 | — | notify "Sunday Weekly Review" |

### Power user (3)

| ID | Trigger | Condition | Action | AI? |
|---|---|---|---|---|
| `starter.power.manual_ai_briefing` | Manual | — | ai.summarize scope="today_briefing" maxItems=20 | yes |
| `starter.power.daily_ai_eod_summary` | TimeOfDay 22:00 | — | ai.summarize scope="recent_completions" maxItems=20 | yes |
| `starter.power.weekly_ai_reflection` | DayOfWeekTime SUN 20:00 | — | ai.summarize scope="weekly_reflection" maxItems=50 | yes |

**Total: 27 rules.** Seed-rule subset (5) preserved with stable `templateKey`. AI-shaped rules: 4 (`focus.ai_summarize_completions`, `med.weekly_ai_summary`, three under power user). All AI rules respect `AiFeatureGateInterceptor` per existing engine wiring — no new gate work needed.

---

## A5 — UI architecture (browse + import)

**Entry point:** `AutomationRuleListScreen` top bar gains a `LibraryBooks` icon (Material 3) next to the existing `History` icon. Tap → navigates to `AutomationTemplateLibraryScreen`. Single entry, no FAB menu, no Settings deep-link (per operator constraint).

**Browse screen (`AutomationTemplateLibraryScreen`):** vertical `LazyColumn` of category sections. Each section: section title + 3-5 `TemplateCard`s. No tabs (with 7 categories on mobile, vertical scroll beats tabs — fewer taps to scan, simpler back-stack). Search bar pinned to top (filters across all categories by name + description substring).

**Template card (`TemplateCard`):** name (titleMedium), description (bodySmall), trigger summary chip ("When task created" / "Daily 7:00" / "Manual"), AI pill if applicable, "Add" trailing button. Tap card body → opens detail sheet.

**Template detail (`TemplateDetailSheet`, ModalBottomSheet):** full description, three subsections — "When this fires" (trigger summary), "Only if" (condition summary, omitted if null), "Then it does" (action list with one line per action). Single CTA: "Add To My Rules". After import, sheet auto-dismisses, snackbar on parent screen: "Added — toggle to enable in Automation."

**Import flow:** one-tap → `AutomationTemplateRepository.importTemplate(templateId)` → calls `AutomationRuleRepository.create(...)` with `isBuiltIn=false`, `templateKey=template.id`, `enabled=false`. Imported rule appears in `AutomationRuleListScreen` disabled. User toggles on after.

**Edit-after-import:** v1 ships read-only (rule edit screen is v1.1 follow-up per engine doc § A9). User can delete + re-import a non-builtin rule to "reset". Builtin (seed) rules stay non-deletable — the existing `AutomationRuleListScreen.kt:166` already gates `Delete Rule` on `!row.isBuiltIn`.

**Visual style:** matches existing `AutomationRuleListScreen` card shape. Material 3 tokens. No new design language. Title Capitalization throughout.

**Empty/loading states:** "No matches" on filtered search; full library always available (no network fetch, no loading state needed).

LOC: screen + sheet + ViewModel + sub-Composables ~700-900.

---

## A6 — Sync architecture (Option A: device-local templates, imported rules sync once routing lands)

| Question | Answer |
|---|---|
| Do templates sync? | **No.** Templates are static APK content (Kotlin source). |
| Does an imported rule sync? | **Yes**, via the existing `syncTracker.trackCreate(id, "automation_rule")` call site in `AutomationRuleRepository.create()`. SyncService consumption is the gate. |
| Is SyncService routing a hard prerequisite? | **No.** Templates ship usable on a single device without it. Cross-device propagation gated until SyncService routing lands. Acceptable per operator's "outstanding 15%" carve-out. |
| Cross-device test scope for this PR? | **None.** Phase 3 verification covers device-local import + enable. Cross-device test reopens when SyncService routing lands (separate PR, separate audit). |

No DB migration needed — `templateKey` and `isBuiltIn` columns already exist on `automation_rules` (per engine doc § A2). Imports stamp `templateKey = "starter.<category>.<slug>"` so a future "show imported templates" filter can grep on prefix.

---

## A7 — File structure: Kotlin-native, delete `sample_rules.json`

| Option | Pick? | Reason |
|---|---|---|
| Extend `sample_rules.json` to 27 rules + add JSON parser | NO | Adds parsing surface (~150 LOC) for a single read site. Existing `AutomationJsonAdapter` doesn't know the wrapper shape (templateKey + name + description + trigger + condition + actions). |
| Promote `sample_rules.json` to canonical, delete inline `SAMPLES` | NO | Same parser issue. Plus: JSON file is currently dead-letter — making it real means re-validating parse-correctness for every rule on every install. |
| **Kotlin-native: `AutomationStarterLibrary.kt` defines all 27 rules; seeder consumes a subset; delete `sample_rules.json`** | **YES** | Type-safe at compile time. Zero parsing surface. Seed subset is a `listOf(KEY_NOTIFY_OVERDUE_URGENT, KEY_AUTOTAG_TODAY, KEY_MORNING_KICKOFF, KEY_STREAK_7, KEY_AI_SUMMARY)` filter against the library. Audit doc + `docs/automation/STARTER_LIBRARY.md` (Phase 2 PR-E) replace the JSON as human reference. |

Files this PR creates / touches:
- **NEW:** `app/src/main/java/com/averycorp/prismtask/data/seed/AutomationStarterLibrary.kt` — `data class AutomationTemplate(id, category, name, description, trigger, condition, actions, requiresAi)` + `val ALL_TEMPLATES: List<AutomationTemplate>` with all 27.
- **NEW:** `app/src/main/java/com/averycorp/prismtask/data/repository/AutomationTemplateRepository.kt` — `templates(): List<AutomationTemplate>`, `templatesByCategory(): Map<Category, List<AutomationTemplate>>`, `importTemplate(id): Long`.
- **MODIFIED:** `data/seed/AutomationSampleRulesSeeder.kt` — replace inline SAMPLES with `library.templates().filter { it.id in SEED_SUBSET_IDS }`. Single-line change at the data source; rest of seeding logic unchanged.
- **DELETED:** `app/src/main/assets/automation/sample_rules.json` (dead-letter; documented in commit message).
- **NEW:** `app/src/main/java/com/averycorp/prismtask/ui/screens/automation/library/AutomationTemplateLibraryScreen.kt` + ViewModel + sub-Composables.
- **MODIFIED:** `ui/screens/automation/AutomationRuleListScreen.kt` — add LibraryBooks icon to top bar.
- **MODIFIED:** `ui/navigation/NavGraph.kt` + `ui/navigation/routes/SettingsRoutes.kt` — register library route.
- **NEW:** `docs/automation/STARTER_LIBRARY.md` — user-facing reference for the 27 rules.
- **MODIFIED:** `domain/automation/handlers/SimpleActionHandlers.kt` — A0 tag mutation extension.
- **MODIFIED:** `domain/automation/AutomationTrigger.kt` + `AutomationJsonAdapter.kt` + `EvaluationContext.kt` + `AutomationTriggerScheduler` — A0 DayOfWeekTime + event.dayOfWeek extensions.

---

## A8 — LOC estimate + commit-by-commit fan-out

Single PR on `claude/automation-starter-library-roo9B`, structured as a sequence of commits for review-friendliness:

| Commit | Scope | Files | LOC |
|---|---|---|---|
| 1. Phase 1 audit | This doc | `docs/audits/AUTOMATION_STARTER_LIBRARY_ARCHITECTURE.md` | ~470 |
| 2. Engine extensions (A0) | tag mutation + DayOfWeekTime + event.dayOfWeek + tests | `SimpleActionHandlers.kt`, `AutomationTrigger.kt`, `AutomationJsonAdapter.kt`, `EvaluationContext.kt`, `AutomationTriggerScheduler.kt`, new tests | ~350 |
| 3. Library content | `AutomationStarterLibrary.kt` with 27 rules + tests verifying every rule round-trips through `AutomationJsonAdapter` | new files | ~600 |
| 4. Template repository | `AutomationTemplateRepository.kt` + seeder refactor | new + modified | ~250 |
| 5. UI | Library screen + detail sheet + ViewModel + sub-Composables + Compose tests | new files | ~800 |
| 6. Entry point + nav | top-bar CTA + nav route | modified | ~80 |
| 7. Docs + cleanup | `docs/automation/STARTER_LIBRARY.md` + delete `sample_rules.json` + Phase 3 audit-doc append | new + delete + modified | ~200 |
| **Total** | | | **~2750** |

Within auditable single-PR range (engine doc § A9 budget was ~3960 in one PR; this is smaller). No fan-out to multiple branches per operator branch policy (`claude/automation-starter-library-roo9B`).

---

## STOP-condition outcomes (Phase 1)

| STOP-condition (operator prompt) | Triggered? | Outcome |
|---|---|---|
| Rule inventory <15 with quality checks | No | 27 rules, each capability-checked against post-A0 engine. |
| Categorization >8 categories | No | 7 categories. |
| File structure undecidable | No | Option A picked (Kotlin-native + delete dead JSON). |
| SyncService routing is a HARD prereq | No | Templates ship single-device-usable; cross-device gated separately. |
| Rule needs feature PrismTask doesn't have (DND, location) | None | All 27 rules expressible against post-A0 engine. Necessary defers (`mutate.medication`, `schedule.timer`) excluded from inventory rather than re-opened. |
| Engine extensions re-open PR #1056's outstanding 15% | No | A0 extensions are scoped: tag mutation, day-of-week trigger, createdAt field. Outstanding 15% (SyncService routing, on-device validation, backend AI endpoints) untouched. |

---

## Verdict — GO. Phase 2 begins on the next commit.

Phase 2 commits 2-7 land on this branch immediately after the audit commit. PR opens once commit 7 lands; CI and ready-for-review per operator branch policy.

Phase 3 bundle summary appends to this doc after the PR squash-merges (commit-numbers + measured impact — wall-clock for the Phase 1→2→3 cycle, rule-parse smoke results, on-device verification notes).

---

## Phase 3 — Bundle Summary (appended pre-merge)

**PR:** Single PR on `claude/automation-starter-library-roo9B` (created post-push).

**Commits shipped:**

| # | SHA | Scope | LOC delta |
|---|---|---|---|
| 1 | `1a6534d` | Phase 1 audit doc | +263 |
| 2 | `d6f48e6` | Engine extensions (tag mutation + DayOfWeekTime + event.dayOfWeek + task.createdAt) | +175 / −16 |
| 3 | `d082aa1` | AutomationStarterLibrary.kt with 27 templates + parse-smoke + structural tests | +675 |
| 4 | `2db02ba` | AutomationTemplateRepository + seeder refactor + tests | +210 / −101 |
| 5 | `0402af8` | Library Compose UI (screen + sheet + ViewModel + tests) | +623 |
| 6 | `23e82c6` | Browse-Templates entry point + nav route + DayOfWeekTime label fix on rule-list VM | +37 / −1 |
| 7 | (this commit) | `docs/automation/STARTER_LIBRARY.md` + delete `sample_rules.json` + Phase 3 append | TBD |

**Measured against the audit's LOC budget:** projected ~2750, actual through commit 6 is ~2050 (+ this commit). Underran by ~25% — the savings came from reusing `BatchOperationsRepository.applyTagDelta`'s case-insensitive find-or-create pattern inline instead of widening TagRepository, and from leaning on existing `AutomationLogScreen`'s shape for the empty-state copy.

**STOP-condition outcomes (Phase 2):**

| STOP-condition | Triggered? | Outcome |
|---|---|---|
| >2 rules fail parse-smoke | No | Parse-smoke test (`AutomationStarterLibraryTest.every_template_round_trips_through_json_adapter`) iterates all 27 templates and round-trips each via `AutomationJsonAdapter`. |
| UI > 1500 LOC | No | Library screen + ViewModel + sheet = ~620 LOC. Well under cap — the 7-category-as-sections decision (no tab bar, no per-category screens) collapsed a meaningful slice of the UI surface. |
| Rule-list screen regression | No | Existing `AutomationRuleListScreen` is additive: new top-bar action button, no removal. `triggerLabelOf` extension covers DayOfWeekTime so imported weekly rules render correctly. |
| Seeder requires Room migration | No | `templateKey` + `isBuiltIn` columns already exist on `automation_rules` (per PR #1056 § A2). Seeder refactor is pure Kotlin — same `getByTemplateKeyOnce` dedup pattern, just sources from library subset. |
| Export-as-template scope creep | No | Stayed deferred per audit constraint. |

**Audit drift caught at content-write time:**

- A4 prose said "AI-shaped rules: 4". Re-tally: 5 (focus + medication weekly summary + 3 power user). Test (`aiTemplates_correctly_flagged`) asserts the correct count. Fix: tally corrected here, not in A4 (audit doc remains a record of the decision; this Phase 3 captures the count drift).

**Necessary defers (kept):**

- `mutate.medication` — handler doc-comment explicitly requires slot/tier-state coherence audit.
- `schedule.timer` — handler doc-comment requires service-start permission flow alongside rule-edit screen v1.1.
- SyncService routing for `automation_rule` — explicitly carved out from this PR by operator. Imported templates are device-local until that lands. Cross-device verification (Phase 3.5 in original prompt) deferred to a separate PR.

**Memory entry candidates (none high-priority):**

- The "JSON file is a documentation artifact, not the seeder source" surprise (premise verification YELLOW row) is the kind of drift that recurs — but the codebase's existing pattern of "kotlin-native seed data with stable templateKey" is already established (PR #1056). The lesson here is "verify the seeder reads what the audit assumes, before promoting JSON to canonical." Not memory-worthy on its own (memory at 30/30; lesson is captured durably in this audit doc).
- `AutomationRuleListViewModel.triggerLabelOf` had a `null -> "Unparseable trigger"` fallback that masks new trigger variants. Adding any new trigger variant must be paired with the corresponding `triggerLabelOf` arm — caught and fixed here in commit 6. Pattern is small enough to live in the engine doc rather than memory.

**Schedule for next audit:** When SyncService routing for `automation_rule` lands (Phase I follow-up), run a short audit on cross-device template-import semantics (does importing the same template on two devices create a duplicate? Should it dedup by `templateKey`?). Inline checkpoint, not a fresh mega-audit.

---

## Phase 3.1 — Operator follow-up: necessary defers addressed (post-Phase-3)

Operator directive 2026-05-02 ("do the deferred items") supersedes the audit's "necessary defer" treatment of the two action-handler stubs. Two of the three deferred items addressed in a follow-up commit; one (SyncService routing) stays deferred per the original prompt's explicit DO-NOT carve-out.

| Deferred item | Addressed? | How |
|---|---|---|
| `mutate.medication` handler stub | YES | Now supports `isArchived` toggle + `name` rename via `MedicationRepository.archive/unarchive/update`. Added `MedicationDao.unarchive` query for symmetry (mirrors existing `archive(id, now)`). Dose-logging mutations (COMPLETE / SKIP / STATE_CHANGE) deliberately remain out — the tier-state coherence risk surface called out in `BatchOperationsRepository.applyMedicationMutation` still applies; rules that need those go through `apply.batch`. |
| `schedule.timer` handler stub | YES | Now calls `PomodoroTimerService.start(...)` via the canonical companion entry point. `mode` string maps to session type (`FOCUS`/`WORK` → SESSION_TYPE_WORK, `BREAK` → SESSION_TYPE_BREAK, `LONG_BREAK` → SESSION_TYPE_LONG_BREAK). `ForegroundServiceStartNotAllowedException` is caught + reported as `ActionResult.Error` so background-triggered firings on Android 12+ log a clean row instead of crashing — Manual triggers (running while the rule list screen is in the foreground) work normally. |
| SyncService routing for `automation_rule` | NO — kept deferred | Original prompt: "DO NOT make changes that re-open PR #1056's outstanding 15%." Explicit constraint, not just a soft defer. Imported templates remain device-local until the operator's separate Phase I PR for that work lands. |

**Library inventory — unchanged.** Both un-stubbed handlers are now fully supported, but no library template currently uses `mutate.medication` or `schedule.timer`. Removing the stubs is unblock-only work — future library additions or user-authored rules can now use these actions without hitting a `Skipped` result.

