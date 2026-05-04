# Web Canonical-Row Dedup Parity Audit

**Source prompt:** `cc_web_canonical_row_dedup_parity_audit_first.md` (May 4 2026 batch).
**Track entry:** F.6 Web Sync Hardening — canonical-row dedup parity (originally Jul 23 → Aug 17, pulled forward).
**Phase 1 cap:** 500 lines.
**Phase 3 + 4 fire pre-merge** per CLAUDE.md repo conventions (override skill default).

---

## Operator decision (locked)

Port Android's natural-key dedup pattern to web Firestore write paths so concurrent same-day completion writes from two devices collapse into a single doc instead of producing duplicate rows.

Estimated mechanical fix; Android's `SyncService.kt:1681-1693` cited as reference.

## TL;DR

- **Premise was partially wrong.** Android line numbers drifted (1681-1693 → 2022-2052) and, more importantly, Android's referenced code is a **pull-path Room dedup**, not a **write-path Firestore dedup**. Two-device race scenarios still produce 2 wire docs on Android — the dedup just absorbs them into 1 local Room row on pull.
- **Web is mostly already correct on the wire.** `checkInLogs.ts` and `moodEnergyLogs.ts` use a stricter, atomic pattern (deterministic doc ID + `setDoc(merge=true)`) that race-frees writes at the wire level.
- **Two web surfaces still violate the pattern** — `habits.ts toggleCompletion` and `batchApplier.ts applyHabitMutation` (`COMPLETE` branch). Both write habit_completions with random doc IDs and no atomic natural-key gate; the first has a TOCTOU pre-query window, the second has no check at all.
- **Bonus defect found** — `habits.ts toggleCompletion` queries on `completedDate` (epoch ms in local TZ) rather than `completedDateLocal` (TZ-neutral logical day key). A device in a different timezone computes a different epoch for the same logical day and silently fails to dedup.
- **Pattern A (deterministic doc ID + `setDoc(merge=true)`)** is the verdict — strictly stronger than Android's reference, internally consistent with the rest of the web Firestore module.
- **Single-PR scope.** No (e)-axis fan-out: every other completion-shaped surface is already correct or has a different defect class.

---

## Recon — Memory #18 quad sweep

### A.1 — Drive-by detection (GREEN)

```
git log -p -S "natural.*key" origin/main --oneline | head
→ d5c77b1 docs(audits): Phase 1 — Automation validation T2 + T4 failure triage (#1093)
git log -p -S "naturalKey" origin/main --oneline | head
→ (no output)
```

The only commit returned is the `AUTOMATION_VALIDATION_T2_T4_AUDIT.md` doc — it cites the existing Android `naturalKey` term but ships no web-side dedup work. No web natural-key dedup has shipped between the Apr 26 parity audit and today.

### A.2 — Parked-branch sweep (GREEN)

```
git branch -r | grep -iE "web.*dedup|web.*sync.*hardening|canonical.*row"
→ origin/claude/web-firestore-dedup-parity-t7sH2  (this audit's branch)
```

No parked branch with prior dedup work.

### A.3 — Android reference re-anchor (YELLOW — STOP-B partial fire)

Prompt cites `SyncService.kt:1681-1693`. Current state:

- Path is `app/src/main/java/com/averycorp/prismtask/data/remote/SyncService.kt` (3,839 lines), not `sync/`.
- Lines 1681-1693 in the current file are unrelated push-loop code; the cited dedup logic has migrated to **lines 2022-2052** under the `pullCollection("habit_completions")` block.
- A file-internal reference at `SyncService.kt:2548` itself says *"the habit_completions natural-key dedup at lines 1682–1693"* — indicating the audit-prompt drift came from stale internal documentation that hasn't been refreshed. (Worth a one-line follow-on after this audit, but not in scope.)

**Verbatim quote (re-anchored, lines 2022-2052):**

```kotlin
if (localId == null) {
    // mapToHabitCompletion always produces a non-null completedDateLocal
    // (either from the Firestore doc or derived from the epoch for
    // legacy docs), so no post-hoc re-normalization is needed.
    val completion = SyncMapper.mapToHabitCompletion(data, habitLocalId = habitLocalId, cloudId = cloudId)
    // Dedup by natural key (habitId, completedDateLocal) to avoid
    // duplicating completions seeded locally on both devices before sign-in.
    val existingByNaturalKey = completion.completedDateLocal?.let {
        habitCompletionDao.getByHabitAndDateLocal(habitLocalId, it)
    }
    if (existingByNaturalKey != null) {
        syncMetadataDao.upsert(
            SyncMetadataEntity(
                localId = existingByNaturalKey.id,
                entityType = "habit_completion",
                cloudId = cloudId,
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    } else {
        val newId = habitCompletionDao.insert(completion)
        syncMetadataDao.upsert(
            SyncMetadataEntity(
                localId = newId,
                entityType = "habit_completion",
                cloudId = cloudId,
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }
}
```

**Critical observation:** this is inside `pullCollection("habit_completions") { data, cloudId -> ... }`. It runs on the **pull path**, when applying a remote doc to local Room. It dedups **local Room rows**, not Firestore docs. Android's push path (line 411-412) writes with random doc IDs:

```kotlin
val docRef = userCollection("habit_completions")?.document() ?: continue
docRef.set(SyncMapper.habitCompletionToMap(completion, habitCloudId)).await()
```

`.document()` with no argument auto-generates a random ID. So **two Android devices completing the same habit on the same day produce 2 wire docs**; the pull-path dedup absorbs them into 1 local row on each device. The Apr 26 parity audit framing — *"Two devices completing the same task on the same day produce ONE Firestore doc, not two"* — is incorrect.

### A.4 — Web write-path enumeration

Inventory of every `addDoc` / `setDoc` site under `web/src/api/firestore/` plus completion-relevant call sites under `web/src/features/`:

| File | Surface | Key + write method | Atomic? | Status |
|---|---|---|---|---|
| `web/src/api/firestore/habits.ts:265` | `toggleCompletion` (habit_completions create) | pre-query `where('completedDate', '==', dateMs)` then `addDoc` | **NO — TOCTOU race + TZ bug** | RED |
| `web/src/api/firestore/habits.ts:179` | `createHabit` | `addDoc` | N/A — habits have no natural key | GREEN |
| `web/src/api/firestore/checkInLogs.ts:82` | `setCheckIn` | doc-id = `dateIso`, `setDoc(merge=true)` | YES | GREEN |
| `web/src/api/firestore/moodEnergyLogs.ts:122` | `createLog` | doc-id = `${dateIso}__${timeOfDay}`, `setDoc(merge=true)` | YES | GREEN |
| `web/src/api/firestore/focusReleaseLogs.ts:95` | `createLog` | `addDoc` | N/A — append-only timeline events keyed by `startedAt` ms; collisions infeasible | GREEN |
| `web/src/api/firestore/boundaryRules.ts:106` | `createRule` | `addDoc` | NO; but rules are user-declared singletons, not "events" | DEFERRED — different defect class |
| `web/src/api/firestore/projects.ts:94` | `createProject` | `addDoc` | N/A | GREEN |
| `web/src/api/firestore/tags.ts:58` | `createTag` | `addDoc` | N/A | GREEN |
| `web/src/api/firestore/tasks.ts:362` | `createTask` | `addDoc` | N/A — task itself, not completion | GREEN |
| `web/src/api/firestore/tasks.ts` (status update) | task completion | `updateDoc` on the task doc itself, sets `completedAt` field inline | YES — completion is a single field on a single doc, no separate completion record | GREEN |
| `web/src/api/firestore/medicationSlots.ts:182` | `createSlotDef` | `addDoc` | N/A — slot definitions, not events | GREEN |
| `web/src/api/firestore/userTemplates.ts:77,152` | habit/project template create | `addDoc` | N/A | GREEN |
| `web/src/features/batch/batchApplier.ts:297` | `applyHabitMutation` `COMPLETE` branch | `addDoc` (no pre-query at all) | **NO — no dedup at all** | RED |
| `web/src/features/batch/batchApplier.ts:574` | `undoHabitEntry` `SKIP` re-create | `addDoc` (intentional fresh ID per inline comment) | NO; semantically a re-create after undo, not a fresh completion | YELLOW — leave |

**No web mapper for `task_completions`.** Web encodes task completion as a `completedAt` epoch field on the task doc itself (`tasks.ts:187,233`) — a single doc, no possible duplication. The Apr 26 audit's mention of `task_completions` is moot for web; the surface doesn't exist as a separate collection.

### A.5 — Sibling-primitive (e) axis

Five candidate completion-shaped surfaces from the prompt:

- `medication_log_events` — Android-only collection. Web does not write to it; no equivalent exists in `web/src/api/firestore/`. **Out of scope.**
- `boundary_breaches` — neither Android nor web has a `boundary_breaches` Firestore collection. The Android `BoundaryEnforcer` keeps breach state in-memory / in Room, never pushed. Web's `boundaryRules` writes rule definitions, not breach events. **Out of scope.**
- `mood_energy_logs` — covered above; **already atomic via Pattern A.**
- `weekly_reviews` — neither Android `userCollection("weekly_reviews")` nor a web `weeklyReviews.ts` mapper exists. (Android `WeeklyReviewAggregator` runs locally; cloud sync is via the generic family path with its own naturalKeyLookup at `SyncService.kt:3012`, which is the PR #1077-class pattern.) **Out of scope on web — surface doesn't exist.**
- Other `(entity_id, date)` shapes — none surfaced beyond the ones listed.

**Result: no (e)-axis fan-out.** Every completion-shaped surface on web is either already correct, doesn't exist, or is the in-scope `habit_completions` write paths.

---

## Premise verification (memory #22 bidirectional)

- **D.1 — Android dedup at `SyncService.kt:1681-1693` exists.** ⚠️ Drifted. Re-anchored to lines 2022-2052 above. Verbatim quoted.
- **D.2 — Web has the divergence (no dedup).** ⚠️ Reframed. Web's wire-level state is **mostly correct**: 3 of 4 in-scope completion-shaped surfaces (`check_in_logs`, `mood_energy_logs`, task `completedAt` inline) already use stricter atomic patterns than Android. Only `habit_completions` (in `habits.ts` + `batchApplier.ts`) violates the canonical-row property. The Apr 26 audit's blanket "web doesn't dedup" claim is too broad.
- **D.3 — Apr 26 parity audit is the source.** ✅ Confirmed (memory entry #26).
- **D.4 — No prior PR has shipped web-side dedup.** ✅ Confirmed for the natural-key terminology specifically (A.1 sweep). The atomic deterministic-doc-ID pattern *was* used in `checkInLogs.ts` and `moodEnergyLogs.ts` shipping commits, but under different framing ("Mirrors Android's unique index", "doc-id is the ISO date so `setDoc(mergeable)` is idempotent"). Same architectural primitive, different vocabulary.
- **D.5 — PR #1077's `naturalKeyLookup` is for a different defect family.** ✅ Confirmed. PR #1077's `naturalKeyLookup` parameter on `pullCollection` is for cross-device same-template-config-row dedup (automation rules, refills, weekly reviews — see `SyncService.kt:2848-3041`). Different surfaces, different semantics. Not duplicate effort.

**Net premise:** the *spirit* of the request — "eliminate web wire-level duplicate completion docs that Android's local-Room layer absorbs but web's no-Room architecture exposes to the user" — is correct and worth shipping. The *letter* of the request ("mirror Android's write-path dedup at lines 1681-1693") needs reframing because Android's referenced code is pull-path Room dedup, not write-path Firestore dedup.

---

## Web-vs-Android wire-level behavior matrix

| Scenario | Android wire | Android local Room | Web wire (today) | Web UI (today) |
|---|---|---|---|---|
| Single device, complete once | 1 doc | 1 row | 1 doc | 1 row |
| Single device, double-tap (in-app race) | 1 doc | 1 row | 1 doc (pre-query gate works for same client) | 1 row |
| Two devices online, simultaneous click | 2 docs | 1 row each (pull dedups) | 2 docs (TOCTOU race wins both pre-queries) | 2 rows visible |
| Two devices offline, both complete, then sync | 2 docs | 1 row each (pull dedups) | 2 docs | 2 rows visible |
| Same logical day, different timezones | 2 docs | 1 row (`completedDateLocal` matches) | 2 docs (web pre-queries on `completedDate` epoch ms which differs by TZ — bonus bug) | 2 rows visible |

The Pattern-A fix collapses every "2 docs" cell into "1 doc" because the doc ID is the natural key — the second writer's `setDoc(merge=true)` updates the same doc rather than creating a sibling.

---

## Pattern verdict — Pattern A

Three candidates per the prompt, evaluated:

- **Pattern 1 (Android-style write-path dedup with transactions):** Firestore Web SDK supports `runTransaction`, but the natural pattern would be "query inside transaction → if found, update; else create." Firestore transactions can read individual docs by ID atomically, but a query (`where(...).get()`) inside a transaction is only valid if it reads at most one doc (per Firestore semantics: transactions can only commit if the data they read hasn't changed). Doable but heavy.
- **Pattern 2 (PR #1077-style pull-path dedup):** Web has no Room layer; pull-path dedup maps to "client-side read-coalesce in selectors." This solves the *display* problem but leaves wire duplicates. Useful as defense-in-depth but not the primary fix.
- **Pattern A (deterministic doc ID + `setDoc(merge=true)`):** **WINNER.** Already used by `checkInLogs.ts` and `moodEnergyLogs.ts` on web. Race-free because Firestore guarantees atomicity at the doc level: two parallel `setDoc(merge=true)` calls against the same path each commit, but the resulting doc has merged fields (last-write-wins per field). For habit_completions, all writers writing the same payload (`habitCloudId`, `completedDateLocal`, `completedDate`, `completedAt`) means the merge is a no-op — there is functionally one doc.

**Pattern A is strictly stronger than Android's reference** because it eliminates wire duplicates entirely, not just local-Room duplicates. Android continues to use random doc IDs + pull-path dedup; web after this PR will use deterministic doc IDs + atomic write-path dedup. The two architectures converge at the wire level but via different mechanisms — that's fine; the contract is "no two docs with the same natural key."

**Defense-in-depth: read-path coalesce.** The Pattern A fix only governs *new* writes. Existing duplicate docs already in Firestore from past races stay duplicated. Add coalesce-on-read in `habits.ts getCompletions` / `getAllCompletions` so the UI never sees them, then let the next `toggleCompletion`-triggered delete-all path absorb them on user toggle (current code at `habits.ts:269-272` already deletes all matching docs on the "removed" branch).

**Doc ID format:** `${habitCloudId}__${completedDateLocal}` — mirrors `moodEnergyLogs.ts:64-66` (`${dateIso}__${timeOfDay}`). Use `completedDateLocal` (TZ-neutral logical day, `YYYY-MM-DD`), not `completedDate` (epoch ms which differs across timezones for the same logical day).

---

## STOP-conditions evaluated

- **STOP-A** (web dedup already shipped) — **Did not fire.** No prior natural-key dedup work shipped specifically for `habit_completions`. Other surfaces happen to be already-atomic via the deterministic-doc-id pattern shipping under different vocabulary; that's prior-art, not prior-work for this scope.
- **STOP-B** (Android line drift) — **Partial fire.** Lines 1681-1693 → 2022-2052. Re-anchored above; verbatim quote captured. SyncService.kt's own internal comments still cite the old line numbers (one-line follow-on candidate, deferred).
- **STOP-C** (multi-surface (e)-axis fan-out) — **Did not fire.** A.5 found no completion-shaped surfaces beyond the in-scope `habit_completions` paths.
- **STOP-D** (Firestore Web SDK transaction infeasibility) — **N/A.** Pattern A doesn't use transactions.
- **STOP-E** (read-path coalescing makes wire duplicates harmless) — **Partial fire.** Android's local-Room layer collapses wire duplicates client-side; web has no equivalent and exposes them to the UI. The premise that "wire duplicates are harmless" holds for Android but **not** for web. Proceed with Pattern A; adding read-path coalesce as defense-in-depth handles legacy duplicates.

---

## Phase 2 scope

**Single PR, single branch (`claude/web-firestore-dedup-parity-t7sH2`).**

### Files touched

- `web/src/api/firestore/habits.ts`
  - `toggleCompletion`: replace pre-query + `addDoc` with deterministic-doc-id `${habitCloudId}__${completedDateLocal}` + `setDoc(merge=true)`. The "remove" branch keeps its existing `getDocs` + `deleteDoc` loop so legacy duplicate docs (different IDs, same natural key) get cleaned up on user toggle.
  - `getCompletions` and `getAllCompletions`: add read-path coalesce keyed on `(habit_id, date)` so legacy duplicates don't reach the UI.
  - LOC: ~+30 / -15.

- `web/src/features/batch/batchApplier.ts`
  - `applyHabitMutation` `COMPLETE` branch (line 292-316): replace `addDoc` with the same deterministic-doc-id `setDoc(merge=true)` shape. The undo log entry's `pre_state.completion_doc_id` field becomes the deterministic ID, which simplifies the `undoHabitEntry COMPLETE` `deleteDoc` (already correct).
  - `undoHabitEntry SKIP` re-create branch (line 564-577): switch to `setDoc(doc(habitCompletionsCol(uid), d.id), d.data)` to preserve the original doc ID and remain idempotent under re-undo races. (Minor improvement bundled because the touch is one-line.)
  - LOC: ~+10 / -8.

### Tests

- `web/src/api/firestore/__tests__/habits.test.ts` — update existing `toggleCompletion` tests for new write shape (assert `setDoc` not `addDoc`, assert deterministic doc ID format).
- New test: parallel `toggleCompletion` calls converge to a single doc (mock `setDoc` and assert single observed write target).
- New test: `getCompletions` coalesces on read when fed two docs with the same `(habit_id, date)`.
- New test: cross-timezone parity — two `toggleCompletion` calls with different timezone-derived `completedDate` epochs but identical `completedDateLocal` produce a single doc.
- `web/src/features/batch/__tests__/batchApplier.test.ts` (if exists; otherwise new) — extend coverage for the COMPLETE-then-undo round-trip with deterministic IDs.

LOC for tests: ~+120.

**Total PR LOC estimate: ~150-170 production + tests, ~well under any reasonable cap.**

### Memory #28 — fan-out bundling

No fan-out. Single PR. (e)-axis surfaced no additional in-scope work.

### Single-PR-per-branch — confirmed

Branch `claude/web-firestore-dedup-parity-t7sH2` is dedicated to this PR per memory #30.

---

## Deferred — NOT auto-filed (memory #30)

Surfaced findings that are **not** in this PR's scope. Operator decides whether to file as follow-ons or close.

1. **`SyncService.kt:2548` self-references stale lines `1682-1693`.** The actual dedup is at 2022-2052. One-line comment update; trivial. Re-trigger if future readers (or Claude Code sessions) get misled by the stale citation.
2. **Web's `boundary_rules` create path uses `addDoc` without natural-key dedup.** A user creating a `daily_task_cap` rule on two devices offline produces two siblings. Different defect class — rules are user-declared not auto-generated, so the failure mode is "user sees 2 of the same rule" rather than "user sees doubled completion count." Re-trigger if multi-device offline rule editing surfaces in user reports.
3. **Read-path coalesce could be applied generally** across `habits.ts subscribeToCompletions` and `batchApplier.ts` reads. Bundled the *write*-path fix only; readers that bypass `getCompletions` still see legacy duplicates until the next user-toggle delete-all loop runs. Re-trigger if duplicate-display reports persist after Phase 2 ships.
4. **`completedDate` epoch-ms field is now unused for natural-key matching** post-fix. Could be deprecated from the schema, but Android's older-client compat code (`SyncMapper.mapToHabitCompletion` falls back to deriving date from `completedDate` for legacy docs) means the field needs to keep being written. No cleanup possible without an Android-side schema bump first.

---

## Open questions for operator

None. Pattern verdict is clear, scope is naturally narrow, no STOP fired hard. Proceeding to Phase 2 immediately per audit-first auto-fire.

---

## Ranked improvement table

Sorted by wall-clock-savings ÷ implementation-cost (high to low):

| # | Improvement | Wall-clock savings | Impl cost | Verdict |
|---|---|---|---|---|
| 1 | `habits.ts toggleCompletion` → deterministic doc ID + `setDoc(merge=true)` | High — eliminates user-visible double-counted habit completions across multi-device, fixes TZ bug | ~30 LOC + tests | **PROCEED** |
| 2 | `batchApplier.ts applyHabitMutation COMPLETE` → same pattern | Medium — batch-mode complete is a less-common path, but parity matters | ~10 LOC | **PROCEED (bundled)** |
| 3 | `getCompletions` read-path coalesce | Medium — defense-in-depth for legacy wire-duplicates already in Firestore | ~15 LOC | **PROCEED (bundled)** |
| 4 | `SyncService.kt:2548` stale comment refresh | Negligible — cosmetic | ~1 LOC | DEFER (one-line follow-on) |
| 5 | `boundary_rules` natural-key dedup | Low — different defect class, rare scenario | ~30 LOC | DEFER |

---

## Anti-patterns avoided

- **Did not** auto-mirror Android's pull-path Room dedup on web. Web has no Room; the pattern doesn't transfer.
- **Did not** widen scope to non-completion-shaped surfaces (boundary_rules, focus_release_logs, etc.).
- **Did not** assume Firestore Web SDK transaction semantics match Android Firestore SDK. Picked the simpler atomic-merge path instead.
- **Did paraphrase-then-quote** Android's dedup. Verbatim quote captured at A.3 above; Phase 2 implementation will adapt the *contract* (natural-key idempotence) rather than the *mechanism* (pull-path lookup).

---

(Phase 2 implementation, Phase 3 bundle summary, and Phase 4 Claude Chat handoff appended below as work progresses.)
