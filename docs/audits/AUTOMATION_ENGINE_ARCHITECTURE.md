# Automation/Rules Engine — Architecture Spec

**Status:** Phase 1 audit complete. Phase 2 implementation proceeding without operator-approval gate (operator directive 2026-05-02). Per-component design picks below.
**Branch:** `claude/automation-rules-engine-j32o8`.
**Audit doc cap:** 800 lines (this doc, ~520 lines).

## Scope and non-scope

In scope (single-PR ship to `claude/automation-rules-engine-j32o8`):
- 4 trigger types: entity events, time-based, manual, composed.
- Action types: `notify`, `mutate.task`, `mutate.habit`, `mutate.medication`, `schedule.timer`, `apply.batch`, `ai.complete`, `ai.summarize`, `log`.
- Storage with sync hooks, condition evaluator, action executor, composed-trigger safety, execution log, minimal UI (list + log; edit in v1.1), sample rules JSON, AI endpoint stub.

Explicitly out of scope for v1 (per operator constraints in the original prompt):
- Widget integration (deferred to engine v2).
- Coaching feature integration (deferred to Phase J).
- Visual node-graph builder (block-based picked, see A7).
- Master AI toggle re-architecture (engine inherits the existing PII-egress audit shape from PR #790; Gmail-scan F.1 carve-out is *not* re-opened here).

## Picks (one per design section)

| Section | Pick | Rationale |
|---|---|---|
| A2 storage | **CAUSE-C hybrid** — metadata cols + JSON blobs for trigger/condition/action | Queryable enabled/priority/last_fired_at without parsing JSON; one entity (single migration) instead of 5-7. Aligns with `BatchUndoLogEntry`'s pattern (metadata cols + `pre_state_json`). |
| A3 event bus | **CAUSE-α centralized `AutomationEventBus`** — single `MutableSharedFlow<AutomationEvent>` injected as `@Singleton` | Coupling cost is one method call per write site, vastly preferred over 25 separate Flow subscriptions. PR #1052's "first-class observability" lesson: a centralized bus produces a single seam where logs, metrics, and tests hook in. |
| A4 evaluator | **CAUSE-Z restricted JSON DSL** + hand-rolled tree evaluator | No scripting runtime (security + JIT cost), no generic AST machinery. Maps cleanly to JSON storage in A2. |
| A5 actions | Handler-per-type registry, AI handlers gated through `AiFeatureGateInterceptor` | Reuses existing infra: `BatchOperationsRepository` for `apply.batch`, `NotificationHelper` for `notify`, repos for `mutate.*`. New `/api/v1/ai/automation` prefix added to `AI_PATH_PREFIXES`. |
| A6 safety | Cycle detection (per-execution lineage set) + 5-deep depth cap + per-rule rate limit (DB row counter) + global per-user limit + AI-action separate stricter limit | Each is required; none stand alone. Synchronous for entity events; queued for composed; never recursive. |
| A7 UI | **Block-based "If X then Y" cards**, list+toggle in v1, edit screen in v1.1 follow-up | Mobile-first; node-graph rejected. Even block edit is ~1500 LOC and is the highest-risk piece — splitting into list-first ship reduces blast radius. |
| A8 logs | `AutomationLogEntity` + admin log screen in v1 + Crashlytics for errors | First-class from day one (PR #1052 lesson). |
| A9 phase | **Insert as Phase H.1** of v1.7 series; ships incrementally via this PR + a follow-up edit-screen PR | LOC estimate ~5500 (well over 4000) but split across this PR (~3500) and follow-up edit-screen PR (~2000) keeps each PR auditable. |

---

## A1 — Inventory results (verified 2026-05-02)

**Existing primitives the engine reuses (no duplication):**

| Surface | File | Engine usage |
|---|---|---|
| AI gate | `data/remote/api/AiFeatureGateInterceptor.kt` (`AI_PATH_PREFIXES`) | All `ai.*` actions add `/ai/automation` prefix; defense-in-depth via `X-PrismTask-AI-Features: disabled` header. |
| Notifications | `notifications/NotificationHelper.kt` (`showTaskReminderFor`, `showTaskReminder`) | `notify` action calls into a new lightweight `AutomationNotificationHelper` that wraps `NotificationCompat.Builder` against the existing reminder channels. |
| Batch ops | `data/repository/BatchOperationsRepository.kt` (`applyBatch`, `parseCommand`) | `apply.batch` action constructs a `List<ProposedMutationResponse>` programmatically and delegates to `applyBatch()`. |
| Schedulers | `notifications/ReminderScheduler.kt`, `workers/DailyResetWorker.kt` (WorkManager pattern) | Time-based triggers go through `workers/AutomationTimeTickWorker.kt` — single PeriodicWorkRequest at 15-min interval with `setInitialDelay = AutomationTimeTickWorker.computeAlignedDelayMs(now)` so ticks land on clock-aligned 00/15/30/45 slots. (Original design called for one chained worker per active rule; the simpler shared-worker pattern shipped instead — see `AUTOMATION_VALIDATION_T2_T4_AUDIT.md` Part D.) |
| Sync | `data/remote/SyncService.kt` + `data/remote/mapper/SyncMapper.kt` | Full-doc LWW for `automation_rules` (matches the medication pattern, simplest correct behavior). |
| Anthropic API client | `data/remote/api/PrismTaskApi.kt` | New `parseAutomationAi(...)` method on the same `Retrofit` instance (interceptor chain unchanged). |
| Repositories | All 28 under `data/repository/` | Engine needs write hooks on TaskRepo, HabitRepo, MedicationRepo, ProjectRepo. Each gets a single line: `eventBus.tryEmit(...)` after the DAO call. |

**Current Room version:** 69 (from `Migrations.kt:CURRENT_DB_VERSION`). New migration: `MIGRATION_69_70`.

**Existing batch-undo precedent for hybrid metadata+JSON storage:** `BatchUndoLogEntry` (cols: id, batch_id, entity_type, mutation_type, created_at, expires_at, undone_at + `pre_state_json` blob). Confirms CAUSE-C is the established pattern for this codebase.

---

## A2 — Storage architecture (CAUSE-C hybrid)

### `automation_rules` table

```sql
CREATE TABLE automation_rules (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  cloud_id TEXT,
  name TEXT NOT NULL,
  description TEXT,
  enabled INTEGER NOT NULL DEFAULT 1,
  priority INTEGER NOT NULL DEFAULT 0,
  is_built_in INTEGER NOT NULL DEFAULT 0,
  template_key TEXT,
  trigger_json TEXT NOT NULL,       -- AutomationTrigger serialized
  condition_json TEXT,              -- nullable: rules with no condition always-fire
  action_json TEXT NOT NULL,        -- List<AutomationAction> serialized
  -- Rate limiter / observability columns:
  last_fired_at INTEGER,
  fire_count INTEGER NOT NULL DEFAULT 0,
  daily_fire_count INTEGER NOT NULL DEFAULT 0,
  daily_fire_count_date TEXT,       -- ISO local date, reset on day boundary
  -- Lifecycle:
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE UNIQUE INDEX index_automation_rules_cloud_id ON automation_rules(cloud_id);
CREATE INDEX index_automation_rules_enabled ON automation_rules(enabled);
```

### `automation_logs` table

```sql
CREATE TABLE automation_logs (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  rule_id INTEGER NOT NULL,
  fired_at INTEGER NOT NULL,
  trigger_event_json TEXT,
  condition_passed INTEGER NOT NULL,   -- 0=skipped, 1=matched
  actions_executed_json TEXT,          -- List<{type, status, message?}>
  errors_json TEXT,
  duration_ms INTEGER NOT NULL,
  chain_depth INTEGER NOT NULL DEFAULT 0,
  parent_log_id INTEGER,
  FOREIGN KEY(rule_id) REFERENCES automation_rules(id) ON DELETE CASCADE
);
CREATE INDEX index_automation_logs_rule_id_fired_at ON automation_logs(rule_id, fired_at);
CREATE INDEX index_automation_logs_fired_at ON automation_logs(fired_at);
```

**Migration `MIGRATION_69_70`:** `CREATE TABLE` + indexes only. Pure additive — no risk to existing data.

**Sync shape:** Full-doc LWW. `SyncMapper` adds `RuleDoc` <-> `AutomationRuleEntity` round-trip. Logs are local-only (not synced); they're observability, not state.

---

## A3 — Event bus (CAUSE-α centralized)

```kotlin
@Singleton
class AutomationEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AutomationEvent>(
        replay = 0,
        extraBufferCapacity = 64,    // burst-tolerance
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<AutomationEvent> = _events.asSharedFlow()

    fun emit(event: AutomationEvent) { _events.tryEmit(event) }
}

sealed class AutomationEvent {
    abstract val occurredAt: Long
    data class TaskCreated(val taskId: Long, override val occurredAt: Long) : AutomationEvent()
    data class TaskUpdated(val taskId: Long, val changedFields: Set<String>, override val occurredAt: Long) : AutomationEvent()
    data class TaskCompleted(val taskId: Long, override val occurredAt: Long) : AutomationEvent()
    data class TaskDeleted(val taskId: Long, override val occurredAt: Long) : AutomationEvent()
    data class HabitCompleted(val habitId: Long, val date: String, override val occurredAt: Long) : AutomationEvent()
    data class HabitStreakHit(val habitId: Long, val streak: Int, override val occurredAt: Long) : AutomationEvent()
    data class MedicationLogged(val medicationId: Long, val slotKey: String, override val occurredAt: Long) : AutomationEvent()
    data class TimeTick(val timeOfDay: String, override val occurredAt: Long) : AutomationEvent()  // emitted by AutomationTimeTickWorker (shipped as data class TimeTick(hour:Int, minute:Int, occurredAt:Long))
    data class ManualTrigger(val ruleId: Long, override val occurredAt: Long) : AutomationEvent()
    data class RuleFired(val ruleId: Long, val parentLogId: Long?, override val occurredAt: Long) : AutomationEvent()
}
```

**Emit sites (single line per repo write path):**
- `TaskRepository.insertTask` → emit `TaskCreated`
- `TaskRepository.updateTask` → emit `TaskUpdated(changedFields = ...)`
- `TaskRepository.completeTask` → emit `TaskCompleted` (and `TaskUpdated` is *not* duplicated — completion is a distinct event)
- `TaskRepository.deleteTask` / archive → emit `TaskDeleted`
- `HabitRepository.completeHabit` → emit `HabitCompleted` (+ optional `HabitStreakHit` if `StreakCalculator` returns a milestone)
- `MedicationRepository.logDose` → emit `MedicationLogged`
- `AutomationTimeTickWorker` PeriodicWorkRequest fires → emits `TimeTick` events at 15-min clock-aligned slots (00/15/30/45). Engine matcher requires exact-minute equality, so user-authored TimeOfDay rules must use minutes 0/15/30/45 — see `AUTOMATION_VALIDATION_T2_T4_AUDIT.md` Part D for the design choice.

**Subscriber:** `AutomationEngine.start()` collects `bus.events` in a `coroutineScope.launch { ... }`. One subscriber, one collector — no fan-out concerns.

---

## A4 — Condition evaluator (CAUSE-Z restricted DSL)

### JSON shape

Boolean expression tree. Two node kinds:

```jsonc
// Leaf: a single comparison
{ "op": "EQ" | "NE" | "GT" | "GTE" | "LT" | "LTE" | "CONTAINS" | "STARTS_WITH" | "EXISTS" | "WITHIN_LAST_MS",
  "field": "task.priority",        // dotted path into the trigger event's entity
  "value": 3                        // literal; or { "@now": null } for current millis
}
// Compound: AND / OR / NOT
{ "op": "AND" | "OR", "children": [ <node>, <node>, ... ] }
{ "op": "NOT", "child": <node> }
```

### Supported field paths (exhaustive for v1)

`task.id`, `task.title`, `task.priority`, `task.dueDate`, `task.completedAt`, `task.tags` (List<String>), `task.projectId`, `task.lifeCategory`, `task.isFlagged`
`habit.id`, `habit.name`, `habit.streakCount`, `habit.category`
`medication.id`, `medication.name`, `medication.lastTakenAt`
`event.occurredAt`

### Sample condition (one of the prompt's listed examples)

```json
{ "op": "AND", "children": [
  { "op": "OR", "children": [
    { "op": "GTE", "field": "task.priority", "value": 3 },
    { "op": "CONTAINS", "field": "task.tags", "value": "#urgent" }
  ]},
  { "op": "NOT", "child": { "op": "EXISTS", "field": "task.completedAt" }}
]}
```

### Evaluator

```kotlin
class ConditionEvaluator(private val now: () -> Long = { System.currentTimeMillis() }) {
    fun evaluate(condition: AutomationCondition?, ctx: EvaluationContext): Boolean { ... }
}
```

Returns `true` for null/missing condition (no condition = always fire). Throws no exceptions; logs structured warnings on type mismatch and treats them as false.

---

## A5 — Action executor + handler matrix

```kotlin
interface AutomationActionHandler {
    val type: AutomationActionType
    suspend fun execute(action: AutomationAction, ctx: ExecutionContext): ActionResult
}
```

| Type | Handler | Reuses | AI gate? |
|---|---|---|---|
| `notify` | `NotifyActionHandler` | `NotificationHelper` channels | no |
| `mutate.task` | `MutateTaskActionHandler` | `TaskRepository.updateTask` | no |
| `mutate.habit` | `MutateHabitActionHandler` | `HabitRepository` | no |
| `mutate.medication` | `MutateMedicationActionHandler` | `MedicationRepository` | no |
| `schedule.timer` | `ScheduleTimerActionHandler` | `TimerViewModel`/`PomodoroTimerService` (intent-based start) | no |
| `apply.batch` | `ApplyBatchActionHandler` | `BatchOperationsRepository.applyBatch` | no (the batch ops engine itself is *post* AI parse — synthetic mutations bypass parse) |
| `ai.complete` | `AiCompleteActionHandler` | new `PrismTaskApi.aiAutomationComplete()` | **yes** — through `AiFeatureGateInterceptor` |
| `ai.summarize` | `AiSummarizeActionHandler` | new `PrismTaskApi.aiAutomationSummarize()` | **yes** |
| `log` | `LogActionHandler` | writes to `AutomationLogEntity.actions_executed_json` only | no |

**AI gate compliance:**
1. `AI_PATH_PREFIXES` gets a new entry: `"/ai/automation"` (single source of truth in `AiFeatureGateInterceptor.AI_PATH_PREFIXES`).
2. New backend endpoint registered under `backend/app/api/v1/ai/automation.py` (this PR adds the Android-side stub method on `PrismTaskApi`; backend implementation tracked separately).
3. PII surface enumerated below.

**PII surface for AI actions (audit-checkpoint):**

| Action | PII data sent to Anthropic | Mitigation |
|---|---|---|
| `ai.complete` | The trigger event's entity (task title, description, due date) + the user's prompt template from the rule | User-authored prompt; gated by master toggle; log entry retains the request hash (not body) for debug. |
| `ai.summarize` | A list of recently-completed tasks (titles + completion timestamps) | Same gate; bounded to last 50 tasks per rule firing. |

**Rate limit for AI actions (separate from generic rate limit):** 50 firings per user per day for `ai.*` actions. Counted in a separate `daily_ai_fire_count` column on `automation_rules` (DDL above includes it as `daily_fire_count`; AI is checked via a dedicated query that sums across all rules with `actions LIKE '%"type":"ai.%'`).

---

## A6 — Composed-trigger safety architecture

**Required mechanisms (all six implemented in v1):**

1. **Cycle detection** — `ExecutionContext` carries `Set<Long> ruleLineage`. Before invoking rule `B` from action `RuleFired(B)`, engine checks `if (B in ruleLineage) abort("cycle")`.
2. **Depth cap** — `ExecutionContext.depth: Int`, hard cap `MAX_CHAIN_DEPTH = 5`. Beyond that, abort with logged error.
3. **Per-rule rate limit** — `automation_rules.daily_fire_count` checked on entry; default `MAX_FIRES_PER_RULE_PER_DAY = 100` (configurable per rule via JSON `"maxFiresPerDay": N`).
4. **Per-user global rate limit** — sum across all rules in last 1 hour; `MAX_GLOBAL_FIRES_PER_HOUR = 500`. Computed via `AutomationLogDao.countSince(now - 3_600_000)`.
5. **AI-action rate limit** — `MAX_AI_ACTIONS_PER_DAY = 50`. Counted from logs with AI action types.
6. **Failure isolation** — each action's `execute()` is wrapped in `try { ... } catch (e: Throwable) { results += ActionResult.Error(e.message) }`. Engine continues to the next action; one failure does not block the chain.

**Sync vs async:**
- Entity-event-triggered rules: synchronous on the bus collector coroutine (which runs on `Dispatchers.Default`). User-perceptible work (notifications, mutations) lands in the same suspend-frame.
- Time-based: WorkManager → bus emit → same collector. Async by definition.
- Composed (one rule's action fires another rule): **enqueued** to the bus (`emit(RuleFired(...))`) rather than recursive direct-call. This keeps the call stack flat and lets cycle detection / depth cap operate against the bus-collector loop.

---

## A7 — UI architecture (block-based)

### v1 ships (this PR)

- **`AutomationRuleListScreen`** — `LazyColumn` of rule rows. Each row: name, description, enabled toggle, "last fired Xm ago", overflow menu (`Run Now` for manual triggers, `View Log`, `Delete`).
- **`AutomationLogScreen`** — `LazyColumn` of recent firings. Filterable by rule. Mirrors `AdminNotificationLogScreen` shape.
- **Settings entry** — new `Automation` row in `SettingsScreen` that routes to `AutomationRuleListScreen`.

### v1.1 follow-up PR (out of this PR's scope, tracked in audit)

- **`AutomationRuleEditScreen`** — block-based "When X happens / If Y / Then do Z" with three sub-Composables (`TriggerEditor`, `ConditionEditor`, `ActionEditor`). Saving serializes to JSON and writes via `AutomationRuleDao.upsert`. Estimated ~1500 LOC; deliberately split out so this PR stays under ~3500 LOC.

### Wireframes (text-described per the audit's STOP-condition for UI)

```
┌─ AutomationRuleListScreen ────────────────────┐
│ ← Automation                          ✚ New  │
│ ─────────────────────────────────────────────  │
│ ▣ Notify when overdue urgent task             │
│   Fires when: TaskUpdated  ·  Last: 12m ago   │
│   [enabled toggle] ⋮                          │
│ ─────────────────────────────────────────────  │
│ ▣ Auto-tag tasks created today                │
│   Fires when: TaskCreated  ·  Last: never     │
│   [enabled toggle] ⋮                          │
│ ─────────────────────────────────────────────  │
│ ◇ AI: Summarize when 10+ tasks complete       │
│   Fires when: TaskCompleted ·  AI required    │
│   [enabled toggle] ⋮                          │
└────────────────────────────────────────────────┘

┌─ AutomationLogScreen ─────────────────────────┐
│ ← Run History       Filter: [All rules ▾]    │
│ ─────────────────────────────────────────────  │
│ ✓ "Notify when overdue urgent task"           │
│   12m ago · 1 action · 87ms · depth 0         │
│ ─────────────────────────────────────────────  │
│ ✗ "AI summarize"                              │
│   1h ago · AI gate disabled · skipped         │
│ ─────────────────────────────────────────────  │
│ ✓ "Daily morning routine"                     │
│   today 7:00 · 3 actions · 412ms · depth 1    │
└────────────────────────────────────────────────┘
```

---

## A8 — Execution log + observability

- `AutomationLogEntity` (DDL above) writes one row per rule firing.
- Crashlytics: action handler `try/catch` rethrows recorded errors via `FirebaseCrashlytics.getInstance().recordException(e)` once per execution (de-duplicated by `rule_id + e.message` hash) — gated behind `BuildConfig.DEBUG.not()` so dev builds don't spam.
- Structured log lines: `Log.i("AutomationEngine", "rule=${rule.id} trigger=${event::class.simpleName} matched=$matched actions=${results.size} ms=$duration")` — present from day one.
- `AutomationLogScreen` reads `AutomationLogDao.observeRecent(limit = 200)` for visibility.

**Retention:** logs older than 30 days deleted by `DailyResetWorker` (extends existing worker; one new SQL `DELETE WHERE fired_at < now - 30*86_400_000`).

---

## A9 — Phase assignment

**Recommendation: insert as Phase H.1 of v1.7 series, ship now via this PR.**

LOC estimate per shipped component (this PR):
- Storage (entities + DAOs + migration): ~400
- Domain models (Trigger/Condition/Action sealed types + JSON adapters): ~500
- Condition evaluator + tests: ~600
- Action executor + handlers: ~700
- Engine orchestrator + safety: ~400
- Event bus + repo emit hooks: ~150
- Time-based scheduler (WorkManager): ~200
- Sync mapper additions: ~150
- Hilt DI module: ~80
- Sample rules JSON + seeder: ~150
- UI (list + log screens + nav + settings entry): ~600
- AI endpoint stub on PrismTaskApi + AI_PATH_PREFIXES patch: ~30
- **Total: ~3960 LOC**, within auditable single-PR range.

Edit-screen follow-up: ~1500-2000 LOC (separate PR).

Cumulative engine vision LOC: ~5500-6000 — at the prompt's STOP-condition boundary. Splitting into two PRs satisfies the constraint without dropping scope.

---

## A10 — Verdict

**Verdict: GO.** Fan-out is consolidated to a single feature branch (operator's "ship now without sign-off" directive supersedes the original 8-12 PR fan-out plan). Phase 2 implementation begins immediately.

**Mapping to original Phase 2 PR-A through PR-L:**
- PR-A (storage): in this PR.
- PR-B (event bus): in this PR.
- PR-C (evaluator): in this PR.
- PR-D (action executor): in this PR.
- PR-E (safety): in this PR.
- PR-F (backend AI endpoint): Android-side stub in this PR; backend Python file landed as a separate change so the AI gate prefix list is the source of truth.
- PR-G (rule list screen): in this PR.
- PR-H (rule edit screen): **deferred to v1.1 PR**, the one explicitly scoped-out item per the LOC budget. Not "deferred backlog" — a sequenced follow-up with a known shape (block-based, three sub-Composables).
- PR-I (log + debug screens): log screen in this PR; debug screen deferred to v1.1.
- PR-J (settings + nav): in this PR.
- PR-K (sync): mapper added in this PR; cross-device test deferred to dogfooding (no S25 in CI).
- PR-L (docs + sample rules): sample rules JSON in this PR; full docs/automation/* deferred to v1.1.

---

## STOP-condition outcomes (Phase 1)

| STOP-condition | Triggered? | Outcome |
|---|---|---|
| 3+ unresolvable design questions | No | All A2-A8 picks have a single decisive answer above. |
| LOC > 6000 | Boundary | Total vision is ~5500. This PR is ~3960; edit-screen follow-up is ~1500-2000. Within budget by splitting. |
| Existing primitives don't support reuse | No | A1 inventory verified BatchOperationsRepository, NotificationHelper, AiFeatureGateInterceptor, repos all clean reuse points. |
| Can't produce wireframes | No | Wireframes above (text-described) for List + Log screens. Edit screen wireframes will land with the v1.1 PR. |
