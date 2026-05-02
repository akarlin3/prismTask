# Deferral Cleanup Bundle Audit

**Date:** 2026-05-02
**Scope:** 10 timeline-deferred items grouped into 5 sequenced bundles (A–E).
**Operating principle:** defer-minimization (memory #30) — each bundle either ships, closes-as-no-work, or defers with a written premise. No new timeline backlog items get created.
**Audit cap:** ~500 lines (per CLAUDE.md hard rule). Each bundle ≤ ~80 lines.

---

## Executive verdict

| Bundle | Items | Verdict |
|--------|-------|---------|
| A — Backend AI defense-in-depth | A.1 KDoc drift, A.2 api_key_length leak | A.1 **GREEN** (no work), A.2 **PROCEED** |
| B — Medication architecture | B.1 TimerVM ordering, B.2 conflict res, B.3 reactive observer | B.1 **PROCEED**, B.2 **DEFER**, B.3 **GREEN** (already shipped) |
| C — AIRoutes transition | C.1 BatchPreview plain composable | **PROCEED** (one-line clarifying comment) |
| D — Medication dup-notification | D.1 dual-scheduler arming | **PROCEED** |
| E — Phase I analytics polish | E.1 heatmap window, E.2 weekly notif schedule, E.3 title rename | All three **PROCEED** |

**PROCEED count:** 7 of 10. **GREEN-no-work:** 2. **DEFER:** 1. **Net deferred items closed:** 10/10 (GREEN and DEFER both remove from the backlog with written rationale).

**Cross-bundle dependency dropped.** The original prompt required Bundle B.3 to land before Bundle D.1 (so the new reactive observer wouldn't conflict with the canonical-scheduler picker). Recon shows B.3 already shipped via PR #1055 drive-by — the observer at `MedicationClockRescheduler.kt:190-192` was added by that PR. Bundle D can ship without sequencing constraints.

---

## Bundle A — Backend AI defense-in-depth

### A.1 — AiFeatureGateInterceptor KDoc-vs-code mismatch (GREEN)

**Premise verification:** Premise WRONG. The KDoc at `app/src/main/java/com/averycorp/prismtask/data/remote/api/AiFeatureGateInterceptor.kt:13-32` does not enumerate path prefixes. It refers to the constant: *"Adding a new Anthropic-touching backend route requires updating [AI_PATH_PREFIXES]"*. The constant at lines 94-106 lists `/ai/`, `/tasks/parse`, `/syllabus/parse`, `/integrations/gmail/scan`. There is no drift to fix — the KDoc is correctly pointed at the constant, not duplicating the list.

**Findings:** No drift. KDoc is documentation-by-reference, which is the better long-term shape than enumeration.

**Recommendation:** STOP-no-work-needed. Remove from F.1 backlog with this audit as the rationale.

### A.2 — /tasks/parse-debug api_key_length leak (RED → PROCEED)

**Findings:** Confirmed leak at `backend/app/routers/tasks.py:235-256`. The endpoint is admin-gated outside non-prod (line 242), but the response payload at line 253 returns `"api_key_length": len(api_key)`. This leaks key entropy / generation-pattern metadata.

```python
return {
    "api_key_length": len(api_key),  # ← LEAK
    "model": "claude-haiku-4-5-20251001",
    "anthropic_installed": anthropic_installed,
}
```

Side findings:
- `backend/app/services/nlp_parser.py:55-56` also logs/prints `api_key_length` to stdout. Server logs are not in the response payload but still surface the same metadata to anyone reading logs.
- No existing tests for `/tasks/parse-debug` in `backend/tests/`.

**Risk:** YELLOW (admin-gated in prod, but defense-in-depth says don't return key metadata at all).

**Recommendation:** PROCEED. Fix shape:
1. Replace `api_key_length` with a boolean `api_key_configured: bool` (true iff `len(api_key) > 0`). Tells operator whether the key is set without leaking its size.
2. Drop the `api_key_length` log line in `nlp_parser.py:55-56` for the same reason.
3. Add a unit test asserting the response no longer contains `api_key_length`.

**LOC estimate:** ~15 LOC + ~25 LOC test = ~40 LOC.

---

## Bundle B — Medication architecture

### B.1 — TimerViewModel sync-ordering bug (YELLOW → PROCEED)

**Findings:** Confirmed at `app/src/main/java/com/averycorp/prismtask/ui/screens/timer/TimerViewModel.kt:170-202`. The work-loop calls `syncWidgetState()` at line 179 (capturing `mode = WORK`), then notifies (line 185), then calls `onTimerCompleted()` at line 189 which mutates `mode = BREAK` and `isLongBreak = newCompleted % sessionsUntilLongBreak == 0` (lines 216-223). No sync follows the mutation, so the widget shows `mode = WORK` until the user takes another action — typically until they manually start the next session.

If `autoStartBreaks = true`, the next `start()` call (line 224) re-syncs naturally. If both auto-start flags are off (a common ND-friendly setup), the widget reads stale state indefinitely until the next user action.

**Risk:** YELLOW (cosmetic stale UI; no data loss).

**Recommendation:** PROCEED. Fix shape: add `syncWidgetState(); widgetUpdateManager.updateTimerWidget()` immediately after `onTimerCompleted()` at line 189-190. This guarantees the widget reflects the post-completion mode regardless of autostart settings.

**LOC estimate:** ~5 LOC + small unit test if practical.

### B.2 — Medication conflict resolution upgrade (DEFER)

**Findings:** Audit confirms asymmetric merge:
- Android `SyncService.kt:2150-2185` uses full-document LWW with `remoteUpdatedAt > localMed.updatedAt` and replaces the entire entity via `MedicationSyncMapper.mapToMedication`.
- Web `web/src/api/firestore/tasks.ts:178-234` and `habits.ts:137-149` use field-level merge.
- **Web `medications.ts` is read-only.** No `updateMedication()` / `createMedication()` exports exist (lines 11-14 explicitly comment "Web does not currently write to this collection").

**Premise re-evaluation:** the YELLOW only matters if web edits medications. Today web cannot edit medications, so the field-level merge upgrade has zero observable benefit until web's medication write surface lands.

**Recommendation:** DEFER (with written premise). The right time to add field-level medication merge is when web's medication editor ships — the merge code lives next to the writes that need it. Premature implementation now would require speculative API design and would rot before it's exercised.

**Closure:** Removed from F.2 backlog. Re-open as a Phase-precondition for the web medication-editor feature when that gets scheduled.

### B.3 — Medication reminderMode reactive observer (GREEN — drive-by)

**Premise verification:** Premise WRONG. Drive-by detection (`git log -p -S 'medicationDao.getAll()'`) shows PR #1055 (`fix(reminders): per-(med, slot) CLOCK alarm path`, May 2 2026) added the observer:

```kotlin
// MedicationClockRescheduler.kt:186-196 (post-PR-#1055)
fun start(scope: CoroutineScope = defaultScope) {
    medicationSlotDao.observeAll()
        .onEach { scope.launch { rescheduleAll() } }
        .launchIn(scope)
    medicationDao.getAll()                      // ← reminderMode-only changes auto-trigger
        .onEach { scope.launch { rescheduleAll() } }
        .launchIn(scope)
    medicationSlotOverrideDao.observeAll()
        .onEach { scope.launch { rescheduleAll() } }
        .launchIn(scope)
}
```

Per PR #1055's KDoc at lines 178-184: *"medication / slot / override edits are rare enough that even a small burst is fine"* — the observer is intentional and covers reminderMode-only changes via the `medicationDao.getAll()` Flow.

**Recommendation:** STOP-no-work-needed. Remove from F.2 backlog with PR #1055 as the closing reference.

---

## Bundle C — AIRoutes transition convention (PROCEED, micro)

### C.1 — BatchPreview plain composable (deliberate, document the intent)

**Findings:** `app/src/main/java/com/averycorp/prismtask/ui/navigation/routes/AIRoutes.kt` mixes `composable(...)` and `horizontalSlideComposable(...)` — recon counted 7 plain + 6 slide. **Plain `composable` is NOT an isolated outlier.** The same mixed pattern exists across `ModeRoutes.kt`, `NotificationRoutes.kt`, `SettingsRoutes.kt`, `TemplateRoutes.kt`, `FeedbackRoutes.kt`. The convention appears to be: modal / temporary-flow screens skip the slide; primary navigation uses the slide.

BatchPreview specifically (lines 98-116) is a modal preview-then-pop flow — fits the "no slide for modals" intent.

**Recommendation:** PROCEED with a one-line clarifying comment above the BatchPreview entry stating the intent. Don't extract a `modalComposable` helper — that would invite re-litigating every mixed callsite across the route files. A targeted comment closes this F.2 item without creating downstream churn.

**LOC estimate:** ~3 LOC.

---

## Bundle D — Medication duplicate-notification cleanup (RED → PROCEED)

### D.1 — Dual-scheduler arming for sync-pulled / legacy meds

**Findings:** Confirmed reproducible. Two schedulers operate on disjoint state:
- `MedicationReminderScheduler.kt` arms on `MedicationEntity.scheduleMode` + `timesOfDay` field. Request-code namespace `400_000+`.
- `MedicationClockRescheduler.kt` arms on `MedicationSlotEntity` rows linked via `MedicationSlotCrossRef`. Request-code namespaces `700_000+` (slot-level) and `800_000+` (per-med-slot).

`SyncService.kt:2142-2212` pulls **both** legacy fields (line 2145, via `MedicationSyncMapper.mapToMedication` writing `scheduleMode` + `timesOfDay`) **and** slot links (line 2196, rebuilding `MedicationSlotCrossRef` from the embedded `slotCloudIds`). When a medication arrives with both populated, both schedulers arm independently — namespaces don't collide at AlarmManager but the user receives duplicate notifications.

`NotificationProjector.kt:243` already filters legacy *projections* when slots exist — the projector knows slots are canonical. The schedulers just don't apply the same gating.

`TIME_OF_DAY_CLOCK` is duplicated:
- `NotificationProjector.kt:668` (private companion)
- `MedicationReminderScheduler.kt:250` (internal companion)

Same values, separate definitions.

**Risk:** RED (user-visible duplicate notifications).

**Recommendation:** PROCEED. Fix shape:
1. Add slot-aware guard at `MedicationReminderScheduler.scheduleForMedication()`: early-return if `medicationSlotDao.getSlotIdsForMedicationOnce(med.id).isNotEmpty()`. Mirrors the `NotificationProjector.kt:243` gating already in place.
2. Extract `TIME_OF_DAY_CLOCK` to a single shared constant (likely a top-level object in the `notifications/` package), reference it from both consumers.
3. Tests:
   - slot-only med → only slot scheduler fires
   - timesOfDay-only med (legacy / web) → only legacy scheduler fires
   - both populated (sync from web on a v1.3-era med) → only slot scheduler fires
   - shared constant correctly used by both consumers

**LOC estimate:** ~30 LOC fix + ~60 LOC tests = ~90 LOC.

---

## Bundle E — Phase I analytics polish (3 × PROCEED)

### E.1 — Heatmap window vs productivity range selector (YELLOW → PROCEED)

**Findings:** `ProductivityHeatmap.kt:50-52` defines fixed `WEEKS = 12` (84 days). `TaskAnalyticsViewModel.kt:97` exposes `_productivityRange` (7/30/60/90 days) controlling the line chart and time-tracking bar chart only. The heatmap composable receives no range parameter (`TaskAnalyticsScreen.kt:158`) — it always renders 12 weeks.

**Risk:** YELLOW (UX confusion only).

**Recommendation:** PROCEED with option **C** from the original prompt (label-based reconciliation). The heatmap's fixed 12-week window is a sensible product choice (smaller/larger windows hurt readability). Add a section header above the heatmap clarifying "Last 12 Weeks" so the user understands the range selector doesn't apply here.

Rationale for not picking option A (heatmap follows selector): the 12-week grid layout is hard-coded for a reason — fewer weeks waste vertical space, more weeks shrink cells below readability.

**LOC estimate:** ~5 LOC (header string + composable wiring).

### E.2 — Weekly summary notification preference read (RED → PROCEED)

**Findings:** `WeeklyAnalyticsWorker.schedule()` (lines 168-192) hard-codes Sunday 19:00 — comment at lines 179-180 says *"Hard-coded to Sunday 19:00 ... Configurability can come later if users actually ask for it."* But `AdvancedTuningPreferences.getWeeklySummarySchedule()` already exists (line 581) and is read by 4 other notification surfaces (NotificationWorkerScheduler at lines 103, 116, 130, 165). The weekly analytics worker is the lone outlier.

`NotificationWorkerScheduler.applyWeeklyAnalytics()` at line 177 calls `WeeklyAnalyticsWorker.schedule(context)` with no schedule parameter — that's where the preference needs to be read and threaded.

**Risk:** YELLOW (preference exists but is silently ignored).

**Recommendation:** PROCEED. Read the preference in `NotificationWorkerScheduler.applyWeeklyAnalytics()` and pass dayOfWeek/hour/minute into `WeeklyAnalyticsWorker.schedule()`. Update the worker's schedule companion to use the passed values rather than the Sunday-19:00 default. Keep Sunday 19:00 as the default fallback if the preference is unset.

**LOC estimate:** ~25 LOC + ~25 LOC test = ~50 LOC.

### E.3 — TaskAnalyticsScreen TopAppBar title (PROCEED, trivial)

**Findings:** Title is `"Task Analytics"` at `TaskAnalyticsScreen.kt:81`. Route name is `"task_analytics?projectId={projectId}"` (NavGraph.kt:235) — independent of title string. No deep links reference the title. The screen content covers tasks + habits + medications + heatmap + correlations — broader than its title implies.

**Recommendation:** PROCEED. Rename TopAppBar title to `"Productivity Dashboard"` (matches the actual content scope and the existing dashboard naming used elsewhere in the app). Route name stays as `task_analytics` for backwards compatibility.

**LOC estimate:** ~2 LOC.

---

## Cross-bundle conflict resolution

| Risk | Resolution |
|------|------------|
| B.3 + D.1 ordering (original concern) | **Dropped.** B.3 already shipped via PR #1055 drive-by. Bundle D can ship independently. |
| Bundle B.1 + Bundle D file overlap | None. B.1 touches `TimerViewModel.kt`; D.1 touches `MedicationReminderScheduler.kt` + `NotificationProjector.kt`. Disjoint. |
| Bundle E.3 rename + nav routes | None. Route key is `task_analytics`, independent of the title string. Verified `TaskRoutes.kt:89-99`. |
| Bundle A.2 KDoc fix narrowing gate | N/A — A.1 reclassified GREEN (no KDoc fix needed). A.2 only touches response payload, not gate logic. |

---

## Phase 2 PR plan (5 PRs, sequenced)

| Order | PR | Branch | LOC | Bundle |
|-------|-----|--------|-----|--------|
| 1 | AIRoutes BatchPreview clarifying comment | `chore/ai-routes-batch-preview-comment` | ~3 | C |
| 2 | Backend `/tasks/parse-debug` api_key leak fix | `fix/parse-debug-api-key-leak` | ~40 | A.2 |
| 3 | Phase I analytics polish (E.1 + E.2 + E.3) | `chore/analytics-phase-i-polish` | ~57 | E |
| 4 | TimerViewModel post-completion widget sync | `fix/timer-widget-post-completion-sync` | ~5 | B.1 |
| 5 | Medication dup-notification cleanup + shared constant | `fix/medication-dup-notification` | ~90 | D |

Bundles can ship as 5 independent PRs — no sequencing constraints remain. Original prompt's B.3 → D ordering rule is moot (B.3 GREEN).

---

## Phase 2 STOP-conditions

- Any PR exceeds 1.5× its LOC estimate → STOP, reassess scope.
- Any new architectural finding surfaces during a PR → bundle into the same PR if scope-coherent, else write up under "Phase 3 surprises" section here, do NOT auto-create a new timeline backlog item (memory #30).
- Bundle D's slot-aware guard test reveals a 3rd duplicate-notification path → STOP, audit-first re-run.
- Any Phase 2 PR introduces a regression caught by CI → fix in the same PR (no follow-up PRs).

---

## Anti-patterns flagged but not in scope

- **`backend/app/services/nlp_parser.py:55-56` API key length log.** Fix lands inside Bundle A.2 PR as a side-effect, not a separate item.
- **`MedicationIntervalRescheduler.start()` lacks medication Flow observer.** Recon noted this — the interval rescheduler doesn't observe `medicationDao.getAll()`. But all known callsites (MedicationViewModel, ReminderModeSettingsViewModel, BootReceiver, app launch) already invoke `intervalRescheduler.rescheduleAll()` explicitly when reminderMode flips at the global or per-med level. No observable bug today. Leaving as-is.
- **`TIME_OF_DAY_CLOCK` semantic vs the user's start-of-day setting.** The constant is wall-clock 08:00 / 13:00 / 18:00 / 21:00 — independent of `DayBoundary`. Probably correct (medication timing is wall-clock, not logical-day-anchored), but worth flagging if a future audit revisits the medication scheduler.

---

## Phase 2 will auto-fire after this doc commits.
