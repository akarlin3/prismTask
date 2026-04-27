# Android ↔ Web Parity Audit (Phase F)

**Date:** 2026-04-26
**Trigger:** Phase F public launch kickoff is 2026-05-15 (19 days from this audit).
**Owner:** akarlin3
**Pattern:** Audit-first, single doc, **deadline-realism check is the load-bearing decision.**

**Working tree state:** local `main` at `ea4ffa27` (post #831 scorecard refresh).
Web `package.json` reports `"version": "1.6.0"` (the prompt's "v1.5.2" reference is stale — web has shipped intervening Pomodoro+/Weekly Review/Mood/Check-In/Boundaries/Batch features in the v1.5.2→v1.6.0 window). Android `versionName = 1.7.16` (build 716).

**Scope:** 15 surfaces, two axes per surface (sync + feature). Widgets excluded by design.

**Outcome of Phase 1 (preview):** 🛑 **STOP. Deadline-realism check fires.** Raw SHIP-BEFORE-MAY-15 count is ~17 distinct items after dedup (vs the 8+ STOP threshold). Section 17 below contains the recommended re-triage Avery should approve before any Phase 2 fan-out begins.

---

## Methodology

For each surface:
- **Axis A — Sync parity:** entity inventory, sync wiring, canonical-row dedup, migration handling, conflict resolution.
- **Axis B — Feature parity:** user-facing actions, web equivalence, UX divergence, settings parity.
- **Triage per gap:** SHIP-BEFORE-MAY-15 / DEFER-TO-G.0 / ACCEPT-AS-DIVERGENCE.

Citations: `path/to/file.kt:NNN` style. Where a "feature exists" claim was checked, callers were verified via grep.

---

## Surface 1 — Medication tracking

### Axis A — Sync parity

**Android entities (Room, all sync-wired):**
- `MedicationEntity` — `app/src/main/java/com/averycorp/prismtask/data/local/entity/MedicationEntity.kt:24-94`
- `MedicationSlotEntity` — `…/MedicationSlotEntity.kt:30-62`
- `MedicationSlotCrossRef` — `…/MedicationSlotCrossRef.kt:19-45`
- `MedicationSlotOverrideEntity` — `…/MedicationSlotOverrideEntity.kt:20-61`
- `MedicationDoseEntity` — `…/MedicationDoseEntity.kt:34-72`
- `MedicationTierStateEntity` — `…/MedicationTierStateEntity.kt:25-84`
- `MedicationRefillEntity` — `…/MedicationRefillEntity.kt:17-57`

**Web persistence:**
- `MedicationSlotDef` (Firestore-only) — `web/src/api/firestore/medicationSlots.ts:84-101`, collection `users/{uid}/medication_slot_defs`
- `MedicationTierState` (Firestore-only, **per-slot aggregate, not per-med**) — `medicationSlots.ts:103-124`, collection `users/{uid}/medication_tier_states`, deterministic doc id `${dateIso}__${slotKey}` (`medicationSlots.ts:228-242`)
- `MedicationReminderModePreferences` — `medicationPreferences.ts:26-34`, doc `users/{uid}/medication_preferences/global`
- Slot-completion rows via REST → backend `daily_essential_slot_completions` table

**Sync wiring status:**
- Android: ✅ All 6 medication entity families wired in `SyncService.kt` (push 932-1003, 1104-1119, 1210-1281, 1372-1442; pull 1950-2151). `BuiltInMedicationReconciler` and `CloudIdOrphanHealer.kt:335-396` cover dedup.
- Web: ❌ Disjoint Firestore-native universe. Web reads/writes `medication_slot_defs` + `medication_tier_states` + `medication_preferences` directly, NOT through `api/sync.ts`. Crucially uses **different collection name** (`medication_slot_defs` vs Android `medication_slots`) and **different doc-id scheme** for the same-named `medication_tier_states` collection.

**Canonical-row dedup:**
- Android `habit_completions` natural-key dedup at `SyncService.kt:1681-1693` is the only one of its kind.
- Android medication families have **no analogous natural-key dedup pass** — rely on unique indexes that throw `SQLiteConstraintException` rather than dedup-merge.
- Web's `${dateIso}__${slotKey}` doc-id makes its own writes idempotent across web devices, but **does not interoperate with Android's auto-id+cloud_id scheme**.

**Migration handling:**
- Android: versioned schema, current v64. Migrations 53→54 (collapse self-care into top-level meds), 58→59 (slots), 59→60 (tier states), 60→61 (clock vs interval reminder mode), 62→63 (intended_time + medication_marks), 63→64 (drops orphan medication_marks).
- Web: no versioned schema. `normalizeTier` at `medicationSlots.ts:66-80` is the only schema-evolution helper. New fields handled inline with `??` fallbacks.

**Conflict resolution:**
- Android: last-write-wins by `updatedAt` (e.g., `SyncService.kt:1968, 2001, 2090, 2135`); doses are append-only.
- Web: `setDoc(..., { merge: true })` everywhere; bulk path uses `writeBatch` for atomicity.

**Sync gaps:**
| # | Gap | Triage | Reason |
|---|-----|--------|--------|
| M-S1 | `medication_tier_states` schema collision: Android writes `{medicationCloudId, slotCloudId, logDate, …}` w/ auto-ids; web writes `{slotKey, dateIso, tier, …}` w/ deterministic id `${dateIso}__${slotKey}`. **Tier-state changes do not round-trip cross-platform** | DEFER-TO-G.0 | Schema bridge requires shape mapping AND migration of existing user docs. `MedicationScreen.tsx:303-305` empty-state copy ("Set up your medication schedule on Android") + `MedicationReminderModeSection.tsx:103-108` banner already pre-warn users. |
| M-S2 | `medication_slot_defs` (web) vs `medication_slots` (Android) — collection-name divergence; slot configs do not cross platforms | DEFER-TO-G.0 | Reconciliation requires picking one canonical key shape (web's `slot_key` string vs Android's `Long id`). Per-platform docs must be reconciled. |
| M-S3 | No natural-key dedup for medication entities on Android pull (vs `habit_completions`); two devices creating same medication pre-sign-in collide on unique index | DEFER-TO-G.0 | Bounded risk — `BuiltInMedicationReconciler` covers built-ins; user-named collisions are rare and surface as toast not corruption. |
| M-S4 | Web meds writes completely outside Android `SyncService` machinery (no sync_metadata, no batch push, no orphan healing) | ACCEPT-AS-DIVERGENCE | Web is fully Firestore-native by design — no Room peer to layer sync_metadata onto. |
| M-S5 | Refill metadata has no web representation | ACCEPT-AS-DIVERGENCE | Refill is a notification-delivery feature; web has no notification path until Phase G Web Push. |
| M-S6 | `medication_slot_overrides` + `medication_doses` (with `is_synthetic_skip`) absent on web | ACCEPT-AS-DIVERGENCE | Web intentionally took slot-aggregate shortcut to ship without backend changes. |

### Axis B — Feature parity

**Android user actions:** add/edit/archive med (1), edit slots+reminder mode (2), per-med dose toggle (3), per-slot tier mark w/ auto-compute + override (4), skipped + synthetic-skip dose (5), set/clear `intended_time` (6), bulk-mark via batch w/ 24h durable undo (7), reminder mode CLOCK/INTERVAL (8), refill tracking (9), medication log/history view (10), clinical report export (11).

**Web equivalence:** 1 ABSENT, 2 DIVERGENT (different collection), 3 ABSENT, 4 PARTIAL (no auto-compute), 5 PARTIAL (no synthetic-skip), 6 EQUIV, 7 PARTIAL (no batch_id correlation, no batch-history undo), 8 PARTIAL (settings-only; banner says "Android-only delivery"), 9-11 ABSENT.

**Feature gaps:**
| # | Gap | Triage |
|---|-----|--------|
| M-F1 | No medication CRUD on web | ACCEPT-AS-DIVERGENCE (already in product copy) |
| M-F2 | No per-med dose toggle on web | ACCEPT-AS-DIVERGENCE (slot-aggregate model) |
| M-F3 | No auto-tier compute on web | ACCEPT-AS-DIVERGENCE (no per-med doses to compute from) |
| M-F4 | Web reminder-mode settings round-trip web↔web only — Android does not read web's Firestore prefs. Banner copy at `MedicationReminderModeSection.tsx:106-107` ("Settings sync to Firestore so your phone picks them up") is **factually false** | **SHIP-BEFORE-MAY-15** (1-line copy fix) OR DEFER-G.0 (Android consumption) |
| M-F5 | No refill tracking on web | ACCEPT-AS-DIVERGENCE |
| M-F6 | No medication log/history view on web | DEFER-TO-G.0 |
| M-F7 | No clinical report export on web | DEFER-TO-G.0 |
| M-F8 | Web bulk-mark misses 24h durable undo + batch_id correlation | DEFER-TO-G.0 |
| M-F9 | Two-source-of-truth on Med screen: backend `daily_essential_slot_completions` for "taken" toggles + Firestore for tier states | DEFER-TO-G.0 |

### Surface 1 triage summary
- SHIP-BEFORE-MAY-15: **1** (M-F4 if treated as copy fix)
- DEFER-TO-G.0: **6**
- ACCEPT-AS-DIVERGENCE: **6**
- ✅ At parity: **NO** (intentionally Android-primary with slot-aggregate web companion)

### Wrong-premise flags
1. The premise "MedicationScreen.tsx actually goes through `api/sync.ts`" was **false**. It uses `dailyEssentialsApi` (REST) for slot completions and direct Firestore for tier states.
2. Premise "firestore modules might be dead/unwired" was **false**. `medicationSlots.ts` and `medicationPreferences.ts` have 5 live consumers each.
3. The in-product banner claim "Settings sync to Firestore so your phone picks them up" is **factually false today**.

---

## Surface 2 — Habits + streaks

### Axis A — Sync parity

**Entity inventory:**
- Android: `habits`, `habit_completions`, `habit_logs` (bookable), `habit_templates` — `data/local/entity/Habit*Entity.kt`
- Web Firestore: `users/{uid}/habits` + `users/{uid}/habit_completions` (`web/src/api/firestore/habits.ts:22-32`); `users/{uid}/habit_templates` (`userTemplates.ts:33-40`).
- **Web has NO `habit_logs` collection** — bookable habits/per-day notes are not synced.

**Sync wiring:**
- Android push: `SyncService.kt:283-365`. Pull: `:1669-1764`.
- Web: realtime `onSnapshot` listeners (`habits.ts:240-259`); no `cloud_id` dedup.

**Canonical-row dedup:**
- Android: `cloud_id` unique index on every habit table; `BuiltInHabitReconciler.kt` collapses cloud-pulled built-in dupes.
- Web: doc id IS the cloud id. **No dedup logic** — race-prone if a habit is created on both devices simultaneously.

**Schema/migration parity:**
- Android v64 has 35+ habit-related fields including `is_built_in`, `template_key`, `source_version`, `is_user_modified`, `is_detached_from_template`, `track_booking`, booking fields, `today_skip_*`, `nag_suppression_*`, `show_streak`, `reminder_times_per_day`, `reminder_interval_millis`, `has_logging`, `track_previous_period`, `completed_date_local` (v50, timezone-neutral).
- Web `habits.ts:36-52` reads ~10 fields. Write path (`habits.ts:81-107`) **hardcodes `isBookable: false`, `trackBooking: false`, `hasLogging: false`** — a web edit clobbers Android-side bookable/built-in state.

**Conflict resolution:**
- Android: `updated_at` LWW + `BuiltInHabitVersionRegistry`.
- Web: blind write `updatedAt: Date.now()`. Doesn't honor `is_user_modified` or `is_detached_from_template`.

**Sync gaps:**
| # | Gap | Triage |
|---|-----|--------|
| H-S1 | Web reads/writes ~10 of 35+ habit fields; round-trip drops 25 fields | **SHIP-BEFORE-MAY-15** (data-loss bug) |
| H-S2 | Web `habits.ts:81-107` hardcodes booking/logging fields false; web edit destroys Android state | **SHIP-BEFORE-MAY-15** (data-loss bug) |
| H-S3 | No `habit_logs` collection on web | DEFER-TO-G.0 |
| H-S4 | Web doesn't write `completedDateLocal` (timezone-neutral day key from v50); completions can drift across DST | **SHIP-BEFORE-MAY-15** |
| H-S5 | No `BuiltInHabitReconciler` equivalent on web | DEFER-TO-G.0 |
| H-S6 | No version-check / merge UI for built-in habit template updates on web | DEFER-TO-G.0 |

### Axis B — Feature parity

| Action | Status |
|---|---|
| Habit CRUD | parity |
| Toggle completion | parity |
| **Daily streak** | **DIVERGENT** — Android forgiveness-first (`DailyForgivenessStreakCore.kt`), web `streaks.ts:62-193` is **STRICT consecutive** |
| Weekly streak | parity |
| Contribution grid | parity |
| Bookable habits + booking history | gap (web absent) |
| Built-in habit identity & versioning | gap |
| Weekly summary notification | platform-divergent (acceptable) |
| Habit follow-up nag | platform-divergent (acceptable) |
| Per-habit Today-skip windows | gap |
| Habit analytics | parity |

**Feature gaps:**
| # | Gap | Triage |
|---|-----|--------|
| H-F1 | Web habit streak is STRICT vs Android forgiveness-first; same habit shows different streaks across devices | **SHIP-BEFORE-MAY-15** (user-trust killer) |
| H-F2 | Bookable habits unsupported on web | DEFER-TO-G.0 |
| H-F3 | Built-in habit identity ignored on web | DEFER-TO-G.0 |
| H-F4 | Per-habit Today-skip windows hidden from web editor | DEFER-TO-G.0 |
| H-F5 | Notification-related habit fields not editable on web | ACCEPT-AS-DIVERGENCE |

### Surface 2 triage summary
- SHIP-BEFORE-MAY-15: **4** (H-S1, H-S2, H-S4, H-F1)
- DEFER-TO-G.0: **5**
- ACCEPT-AS-DIVERGENCE: **1**
- ✅ At parity: **NO**

### Wrong-premise flags
- Premise "web has habit streaks at parity" — **false**. Web is strict; Android is forgiveness-first. The shared `DailyForgivenessStreakCore` was extracted specifically for cross-feature reuse; web didn't follow.

---

## Surface 3 — Tasks (incl. subtasks/tags/recurrence)

### Axis A — Sync parity

**Entities:**
- Android: `tasks` (`TaskEntity.kt:34-116`, 38 columns), `task_tag_cross_ref`, `task_completions`, `task_templates`, `attachments`, subtasks via `parent_task_id` self-FK.
- Web Firestore: `users/{uid}/tasks` (single collection — subtasks have `parentTaskId`), `users/{uid}/tags`. **No web write to `task_completions`.**
- Web ALSO has REST `tasks.ts` (backend-mediated) — parallel data path.

**Sync wiring:**
- Android push: `SyncService.kt:365-396` (tasks), `:397-426` (task_completions), `:426-450` (task_templates).
- Pull order: `SyncService.kt:1518-1519` documents projects→tags→habits→tasks→task_completions→habit_completions→habit_logs→milestones→task_templates.
- Web: `subscribeToTasks` (`tasks.ts:255-264`) — but **never called from any UI** (verified — see Surface 6).

**Canonical-row dedup:** Android `cloud_id` + `CloudIdOrphanHealer.kt`; web relies entirely on Firestore doc id.

**Schema/migration parity (the data-loss minefield):**
- Web write of an existing task **erases**: `due_time` (`tasks.ts:97` writes `dueTime: null`), `isFlagged` (`:104` writes `false`), `lifeCategory` (`:105` writes `null`), `eisenhower_reason`, `archived_at`, `source_habit_id`, `userOverrodeQuadrant` (v57), Focus-Release fields.

**Conflict resolution:** Both LWW by `updated_at`; no vector clocks.

**Sync gaps:**
| # | Gap | Triage |
|---|-----|--------|
| T-S1 | Web `tasks.ts:104-105` always writes `isFlagged: false, lifeCategory: null` — round-trip nukes Android user state | **SHIP-BEFORE-MAY-15** (data loss) |
| T-S2 | Web `tasks.ts:97` always writes `dueTime: null` on create | **SHIP-BEFORE-MAY-15** (data loss) |
| T-S3 | Web doesn't read/write `userOverrodeQuadrant` (v57); Android Eisenhower auto-classifier will re-overwrite user web moves | **SHIP-BEFORE-MAY-15** |
| T-S4 | Web has no write path to `task_completions`; web analytics REST `analytics.ts` requires backend data fed by the dead `syncApi` REST path | **SHIP-BEFORE-MAY-15** (gates analytics) |
| T-S5 | Web ignores Focus-Release per-task overrides | DEFER-TO-G.0 |
| T-S6 | `archived_at` not written by web — web archive invisible to Android | DEFER-TO-G.0 |
| T-S7 | `source_habit_id` not preserved on web edits — habit-spawned tasks lose linkage | DEFER-TO-G.0 |
| T-S8 | Two parallel data paths on web (Firestore + backend REST `tasks.ts`/`sync.ts`); `web/src/api/sync.ts` has **zero callers** (verified by grep) | **SHIP-BEFORE-MAY-15** (architecture decision needed) |

### Axis B — Feature parity

| Action | Status |
|---|---|
| Create/edit/delete | parity |
| Subtasks | parity (verified `TaskEditor.tsx:397-419`) |
| Recurrence | partial — field passed through; engine on web unverified |
| Reminders | platform-divergent (acceptable) |
| Tags M2M | parity |
| Bulk edit / batch ops | parity (see Surface 4) |
| Task templates | parity (different backends) |
| **Flagged tasks** | **broken on web** — read but not written (silently dropped) |
| **Life-category chips** | **broken on web** — read but not written |
| Eisenhower quadrant | partial — no `userOverrodeQuadrant` |
| Focus-Release fields | divergent |
| **Due time of day** | **broken on web** — read OK, write hardcoded null |

**Feature gaps:**
| # | Gap | Triage |
|---|-----|--------|
| T-F1 | Flagged-task toggle silently dropped on any web edit | **SHIP-BEFORE-MAY-15** |
| T-F2 | Life-category cannot be set/changed from web (Work-Life Balance is a tentpole) | **SHIP-BEFORE-MAY-15** |
| T-F3 | Due-time-of-day cannot be set from web | **SHIP-BEFORE-MAY-15** |
| T-F4 | Task templates use REST backend on web vs Firestore on Android — disjoint stores | DEFER-TO-G.0 |
| T-F5 | Focus-Release per-task overrides not in web editor | ACCEPT-AS-DIVERGENCE |

### Surface 3 triage summary
- SHIP-BEFORE-MAY-15: **8** (T-S1/2/3/4/8, T-F1/2/3) — **but most can be bundled into one "preserve-on-write" PR per file**
- DEFER-TO-G.0: **3**
- ACCEPT-AS-DIVERGENCE: **1**
- ✅ At parity: **NO**

### Wrong-premise flags
- "web sync HTTP fallback" exists but `syncApi` has **zero production callers** (verified). It's dead code; the analytics path that would need it is broken end-to-end.

---

## Surface 4 — AI Quick-Add (NLP + multi-task + batch ops)

### Axis A — Sync parity

- `batch_undo_log` (Android v58) is **device-local by design** — no `cloud_id`. CLAUDE.md confirms.
- Web: `batchStore.ts:17-20` defines `UNDO_WINDOW_MS = 24h`, persists batches as Zustand state in memory/localStorage. **No backend persistence** — refresh loses undo window.

**Sync gaps:**
| # | Gap | Triage |
|---|-----|--------|
| Q-S1 | Web batch undo doesn't persist across browser refresh | DEFER-TO-G.0 |
| Q-S2 | Cross-device batch history unshareable — by design | ACCEPT-AS-DIVERGENCE |

### Axis B — Feature parity

Both platforms: local NLP fallback (Android `NaturalLanguageParser.kt:1-544`, web `utils/nlp.ts:56-254`), backend AI parse (`/tasks/parse`), batch-intent detection, `/ai/batch-parse`, batch preview screen, batch applier, Pro gate, template `/name` autocomplete.

| Gap | Triage |
|---|---|
| Q-F1 | Multi-task paste (newline-separated) absent on web | DEFER-TO-G.0 |
| Q-F2 | Batch undo browser-session-scoped on web | DEFER-TO-G.0 |
| Q-F3 | Web local parser doesn't extract `due_time` even when it parses "at 3pm" — couples to T-S2 | **SHIP-BEFORE-MAY-15** (joint with T-S2) |

### Surface 4 triage summary
- SHIP-BEFORE-MAY-15: **1** (Q-F3, joint)
- DEFER-TO-G.0: **3**
- ACCEPT-AS-DIVERGENCE: **1**
- ✅ At parity: **mostly yes** — best of the productivity surfaces. PR #772 batch-ops template propagated cleanly.

---

## Surface 5 — Pomodoro+ focus

### Axis A — Sync parity

**No persisted entity on either platform.** Android `PomodoroSession` is runtime state in `SmartPomodoroViewModel.kt:77-82` + `PomodoroTimerService.kt`. Web `PomodoroScreen.tsx` uses React `useState`. Both call `POST /ai/pomodoro-plan`.

**No sync gaps.**

### Axis B — Feature parity

| Action | Status |
|---|---|
| AI plan generation | parity |
| AI coaching (pre/break/recap) | parity (`PomodoroCoachPanel.tsx:16-22`) |
| Foreground service / lock-screen countdown | web-by-platform (browser kills tab→timer) |
| Energy-aware session sizing | web missing (Android `EnergyAwarePomodoro`) |
| Good-Enough timer release | web has it in `FocusReleaseScreen` (different surface) |
| Session history | parity (both ephemeral) |
| Empty-plan handling | parity |

**Feature gaps:**
| Gap | Triage |
|---|---|
| Web has no foreground/background continuation | ACCEPT-AS-DIVERGENCE |
| `EnergyAwarePomodoro` sizing absent on web | DEFER-TO-G.0 |

### Surface 5 triage summary
- SHIP-BEFORE-MAY-15: **0**
- DEFER-TO-G.0: **1**
- ACCEPT-AS-DIVERGENCE: **1**
- ✅ At parity: **largely yes**

---

## Surface 6 — Cross-device sync infrastructure (the big one)

### Sync wiring matrix (entity-by-entity)

Citations: Android `app/src/main/java/com/averycorp/prismtask/data/remote/SyncService.kt`. Web `web/src/api/firestore/*.ts`.

| Entity | Android sync | Web sync | Web mechanism | Triage |
|---|---|---|---|---|
| `tasks` | ✅ (push 376, pull 1604, listener 2791) | ✅ (`tasks.ts`) | Firestore + `subscribeToTasks` | At parity (schema-divergent — see Surface 3) |
| `task_completions` | ✅ | ❌ | — | **SHIP-BEFORE-MAY-15** (analytics history split-brain) |
| `task_tag_cross_ref` | ✅ (embedded array) | PARTIAL | inline `tagIds` | At parity |
| `projects` | ✅ | ✅ | Firestore + `subscribeToProjects` | At parity (schema-divergent) |
| `milestones` | ✅ | ❌ | — | DEFER-TO-G.0 |
| `tags` | ✅ | ✅ | Firestore + `subscribeToTags` | At parity |
| `attachments` | ✅ | ❌ | — | DEFER-TO-G.0 |
| `habits` | ✅ | ✅ | Firestore + `subscribeToHabits` | At parity (schema-divergent — see Surface 2) |
| `habit_completions` | ✅ | ✅ | Firestore + `subscribeToCompletions` | At parity |
| `habit_logs` | ✅ | ❌ | — | DEFER-TO-G.0 |
| `habit_templates` | ✅ | ✅ getDocs only | no listener | DEFER-TO-G.0 |
| `task_templates` | ✅ | ❌ (only built-ins via `userTemplates`) | — | DEFER-TO-G.0 |
| `project_templates` | ✅ | ✅ no listener | — | DEFER-TO-G.0 |
| `nlp_shortcuts` | ✅ | ❌ | — | DEFER-TO-G.0 |
| `saved_filters` | ✅ | ❌ | — | DEFER-TO-G.0 |
| `notification_profiles` | ✅ | ❌ | — | ACCEPT-AS-DIVERGENCE (web no native notifications) |
| `custom_sounds` | ✅ | ❌ | — | ACCEPT-AS-DIVERGENCE |
| `self_care_logs/steps` | ✅ | ❌ | — | ACCEPT-AS-DIVERGENCE (deprecated by v53→v54) |
| `leisure_logs` / `courses` / `assignments` / `study_logs` | ✅ | ❌ | — | DEFER-TO-G.0 (Android-only features) |
| `medications` | ✅ | ❌ (web stores meds embedded in slot rows via REST) | backend `/daily-essentials/*` | **SHIP-BEFORE-MAY-15** OR formalize divergence |
| `medication_doses` | ✅ | ❌ | — | DEFER-TO-G.0 |
| `medication_slots` | ✅ | ✅ but **DIFFERENT collection name** `medication_slot_defs` | Firestore + `subscribeToSlotDefs` | **SHIP-BEFORE-MAY-15** (collection-name divergence) |
| `medication_slot_overrides` | ✅ | ❌ | — | DEFER-TO-G.0 |
| `medication_tier_states` | ✅ | ✅ same name, **doc-id scheme differs** (`${dateIso}__${slotKey}`) | random vs deterministic | **SHIP-BEFORE-MAY-15** (cross-platform two-row coexistence) |
| `medication_log_events` | local-only audit log | ❌ | — | ACCEPT-AS-DIVERGENCE |
| `medication_refills` | ✅ | ❌ | — | DEFER-TO-G.0 |
| `daily_essential_slot_completions` | ✅ Firestore + REST | ❌ Firestore — REST `/daily-essentials/slots` | backend POST | **SHIP-BEFORE-MAY-15** (split-brain architecture) |
| `boundary_rules` | ✅ | ✅ trimmed schema (3 rule types only) | Firestore (no listener) | At parity (verify Android-only rule types) |
| `check_in_logs` | ✅ | ✅ no listener | Firestore | **SHIP-BEFORE-MAY-15** (no real-time → streak diverges) |
| `mood_energy_logs` | ✅ | ✅ no listener; **no `(date, time_of_day)` uniqueness guard** | Firestore | **SHIP-BEFORE-MAY-15** (uniqueness/dedup) |
| `focus_release_logs` | ✅ | ✅ no listener | Firestore | DEFER-TO-G.0 |
| `weekly_reviews` | ✅ | ❌ (web computes on-demand only) | — | DEFER-TO-G.0 |
| `usage_logs` | local-only | ❌ | — | ACCEPT-AS-DIVERGENCE |
| `sync_metadata` | local-only | ❌ | — | ACCEPT-AS-DIVERGENCE |
| `calendar_sync` | external (Google Calendar) | ❌ | — | ACCEPT-AS-DIVERGENCE |
| `batch_undo_log` | local-only by design | local-only | — | ACCEPT-AS-DIVERGENCE |
| Web-only `medication_preferences` doc | NO Android equivalent | ✅ | Firestore + `subscribeToReminderModePreferences` | DEFER-TO-G.0 |

### Canonical-row dedup
- Android: `cloud_id` columns on syncable entities (v52→v56). `SyncMetadataDao.getLocalId(cloudId, entityType)` maps; pull checks before insert. `habit_completions` has additional `(habitId, completedDateLocal)` natural-key dedup.
- Web: **NONE.** Firestore doc id IS the cloud id. No local index, no dedup pass — dupes only happen if two tabs `addDoc()` simultaneously.
- **Critical divergence**: `medication_tier_states` — Android writes random docIDs + separate `cloud_id`; web writes deterministic `${dateIso}__${slotKey}`. **Two devices writing the same day/slot create parallel rows that never reconcile.**

### Conflict resolution
- Android: LWW by `updatedAt` timestamp (`SyncService.kt:1543-1547` and similar).
- Web: **NONE.** Web overwrites Firestore on every `updateTask()` (`tasks.ts:227-237`). No timestamp guard; whichever client writes last wins regardless of staleness.
- Backend `/sync/push`: serial apply with allowlists (`backend/app/routers/sync.py:83-127`); no `If-Unmodified-Since`.

### Migration handling
- Android: Room v64 with 63 named migrations.
- Web: **NO IDB/offline DB.** Verified zero `indexedDB`/`Dexie`/`openDB` matches in `web/src`. Schema = whatever Firestore happens to contain. Each Android migration adding a column is an implicit silent contract web doesn't validate.

### Sign-out / resetAppData hygiene
- Android sign-out (Settings path): `SettingsViewModel.onSignOut()` calls `authManager.signOut()` only. Does NOT stop Firestore listeners. Auth-screen path (`AuthViewModel.onSignOut()` `:183-192`) DOES stop listeners. **Settings path is buggy vs AuthScreen path.**
- Android resetAppData: clears `sync_metadata` IFF data is reset, but `initialUploadDone` flag NOT cleared.
- Web sign-out: `authStore.ts:200-213` clears localStorage + Firebase. No explicit unsubscribe — but those subscribes are never called from any UI (see below), so moot.

### Real-time listener parity — **MAJOR GAP**
- Android: `SyncService.startRealtimeListeners()` (`:2788-2853`) attaches `addSnapshotListener` to **34 collections** with deletion handling.
- Web: 7 `subscribeTo*` functions are **defined** but **never called from any component or App.tsx** (verified: `App.tsx` only runs `initFirebaseAuthListener` on mount). All web data fetches are imperative `getDocs()` triggered by navigation. **Web has zero live data; cross-device changes only appear after manual page refresh.**

### Sync infra triage summary

- **SHIP-BEFORE-MAY-15: 9 items**
  1. Wire web's `subscribeTo*` from App.tsx (~30-line fix unblocking 5+ entity types live sync)
  2. `medication_slots` collection-name divergence
  3. `medication_tier_states` doc-id scheme divergence
  4. `medications` + `medication_doses` not on web (or formalize divergence)
  5. `daily_essential_slot_completions` split-brain
  6. `task_completions` not on web
  7. `check_in_logs` no real-time on web (streak diverges)
  8. `weekly_reviews` not stored on web (or formalize)
  9. Web has no `cloud_id`/timestamp conflict resolution

- **DEFER-TO-G.0: 11 items**
- **ACCEPT-AS-DIVERGENCE: 8 items**
- ✅ At parity: **NO** — even the "covered" 4 entities have no real-time wiring on App.tsx, missing schema fields, no LWW conflict resolution, no `cloud_id` dedup. Cross-device sync is **essentially non-functional today** for anything but blind last-write-wins on Firestore.

### Wrong-premise flags
- "Web sync is a 27-line stub" — **partially false**. `api/sync.ts` is the stub (with zero callers); `web/src/api/firestore/*.ts` (13 files, ~1500 lines) IS the real web sync surface. But the conclusion stands — web sync is far thinner than Android.
- `index.ts` re-exports being limited to 4 modules is misleading — direct imports work, 7+ firestore modules are used.

### Top-level recommendation

**"Web sync parity with Android" is NOT achievable in 19 days.** Bringing web up to Android-equivalent sync would require:
- A `cloud_id` reconciliation layer on web
- LWW timestamp guards on every web write
- Wire `subscribeTo*` from `App.tsx` (~30 lines, easy win)
- 25+ new firestore modules to cover un-synced entities (3-4 weeks)
- Reconcile `medication_slots` ↔ `medication_slot_defs` collision (migration affects every existing user)
- Reconcile daily-essentials Android-Firestore vs web-REST split-brain

**Three highest-impact wins worth shipping (recommended for the partial-parity-with-deferral approach):**
1. Wire `subscribeTo*` from App.tsx (~1-2 days). Live sync for tasks/habits/projects/tags/medication_slots.
2. Fix `medication_slots` collection-name divergence (~3 days incl. Android-side migration).
3. Add LWW timestamp guard to web writes (~2 days).

Defer everything else with explicit "Web is a single-device session viewer for X feature" callouts in the Phase F test brief.

---

## Surface 7 — Account management

### Axis A — Sync parity
- Android `AuthManager.kt:33-113` (Credential Manager + Firebase + Google Identity). Sign-out (`:87-97`) clears `sync_metadata`.
- Account deletion fully built (`AccountDeletionService.kt:55-332`): Firestore `deletion-pending` mark, backend POST `/auth/me/deletion`, Room wipe, DataStore file-level wipe of all 29 enumerated pref files (`:300-330`).
- Web `authStore.ts:111-136`: Firebase popup + dual-token. Logout (`:200-213`) clears localStorage + Firebase. **Does NOT clear local IndexedDB / Firestore offline cache.**
- Account deletion (`DeleteAccountModal.tsx` + `api/auth.ts:43-57`): two-step typed-DELETE (PR #783). Mirrors `deletion_pending_at` to Firestore.

**Sync gaps:**
| Gap | Triage |
|---|---|
| Web logout doesn't clear local IndexedDB / Firestore offline cache | DEFER-TO-G.0 |
| **Restore-pending takeover screen on web sign-in absent** | **SHIP-BEFORE-MAY-15** (privacy/data-integrity: pending-deletion user signing in to web silently overwrites the deletion mark) |
| Permanent-purge expiry handling on web | DEFER-TO-G.0 (backend cron is the actual purge enforcer) |

### Axis B — Feature parity

| Action | Status |
|---|---|
| Google sign-in | parity |
| Email/password | INTENTIONAL DIVERGENCE (web-only) |
| Sign-out | functional parity |
| Account deletion | parity (PR #783) |
| Restore within grace | gap on web |
| Multi-device sign-in | parity |

**Feature gaps:**
| Gap | Triage |
|---|---|
| Web has no `RestorePending` UI on sign-in | **SHIP-BEFORE-MAY-15** |
| Web change-password button (`SettingsScreen.tsx:938-955`) is a fake — toasts success unconditionally with no API call | **SHIP-BEFORE-MAY-15** (misleading UX) |
| Web "edit name" (`:729-732`) is also a fake | DEFER-TO-G.0 (cosmetic; name comes from Google profile) |

### Surface 7 triage summary
- SHIP-BEFORE-MAY-15: **2** (restore-pending UI + fake change-password)
- DEFER-TO-G.0: **3**
- ACCEPT-AS-DIVERGENCE: **1** (email/password web-only)
- ✅ At parity: **partial** — core sign-in/sign-out/delete works; restore + change-password do not

---

## Surface 8 — Settings

### Axis A — Sync parity

- Android: 19 DataStore files registered for cross-device sync via `GenericPreferenceSyncService` in `di/PreferenceSyncModule.kt:54-74`. Includes `task_behavior` (carries `day_start_hour`/`day_start_minute`) and `user_prefs` (carries `KEY_AI_FEATURES_ENABLED`).
- Web: `settingsStore.ts:33-81` — single `prismtask_settings` localStorage key. **No Firestore push, no cross-device sync.** Theme (`themeStore.ts`) and a11y (`a11yStore.ts`) also localStorage-only. Only `onboardingStore.ts:65-80` writes to Firestore.

**Sync gaps:**
| Gap | Triage |
|---|---|
| **Web settings completely device-local** — toggles on Chrome show defaults on Firefox | **SHIP-BEFORE-MAY-15** (parity claim is broken) |
| **`startOfDayHour` divergence** — Android stores `task_behavior_prefs/day_start_hour` (synced); web stores `prismtask_settings.startOfDayHour` (local). Setting SoD=4 on Android does not propagate to web | **SHIP-BEFORE-MAY-15** (PR #722/#798 unified the helper but not the setting itself) |

### Axis B — Feature parity

35+ Android settings sections vs ~10 web sections. Most Android sections are advanced/Android-specific (escalation, custom sounds, voice, shake, debug). Real parity ask is ~12 sections, most at parity or in-progress.

| Setting class | Triage |
|---|---|
| Theme picker (4 themes) | At parity |
| Compact mode | SYNC GAP (above) |
| SoD config | **SHIP-BEFORE-MAY-15** (sync broken) |
| AI feature gate UI | **SHIP-BEFORE-MAY-15** (no web UI — see Surface 9) |
| Boundaries / Accessibility / Subscription | At parity |
| Notification profiles + escalation + custom sounds | ACCEPT-AS-DIVERGENCE |
| Voice / Shake / Debug tier / Brain Mode | ACCEPT-AS-DIVERGENCE |
| Forgiveness streak knobs | DEFER-TO-G.0 |
| Timer / Pomodoro prefs | DEFER-TO-G.0 |
| Keyboard shortcuts modal / Install PWA | INTENTIONAL DIVERGENCE (web-only) |

### Surface 8 triage summary
- SHIP-BEFORE-MAY-15: **2** (web settings sync infrastructure; AI opt-out tracked under Surface 9)
- DEFER-TO-G.0: **3**
- ACCEPT-AS-DIVERGENCE: **8**
- ✅ At parity: theme, boundaries, accessibility, delete-account, subscription, medication slot editor

### Wrong-premise flags
- The "35 Android sections vs 10 web sections" framing is misleading — most Android sections are advanced/Android-specific. Real parity ask ~12 sections, most landing.
- Web settings store has the structural problem (localStorage-only), not individual sections. Fix the persistence layer once → most parity claims resolve.

---

## Surface 9 — Privacy + Data Safety

### Axis A — Sync parity

- Android: `KEY_AI_FEATURES_ENABLED` lives in `user_prefs` DataStore which IS in `PreferenceSyncModule:72`. Synced to Firestore.
- Web: **No equivalent preference, no UI, no Firestore write/read.** Zero references to AI-features header/flag in web codebase (verified via grep).
- Backend interceptor (Android): `AiFeatureGateInterceptor.kt:64-90` short-circuits `/ai/`, `/tasks/parse`, `/syllabus/parse` paths client-side with synthetic 451.

**Sync gaps:**
| Gap | Triage |
|---|---|
| **Web has no AI opt-out at all** — disabling on Android does NOT stop web's `/ai/*` calls (`api/ai.ts:147-207` — 9 distinct AI endpoints, no gate) | **SHIP-BEFORE-MAY-15 (P0 privacy)** — PR #790 shipped Android side; web is the open egress |
| Web doesn't read the synced flag | **SHIP-BEFORE-MAY-15** (defense-in-depth) |

### Axis B — Feature parity

| Surface | Triage |
|---|---|
| **AI-features master toggle** | **SHIP-BEFORE-MAY-15** — Android has it (`AiFeaturesScreen.kt` + `AiSection.kt:39-51`); web has none |
| **Anthropic egress disclosure** | **SHIP-BEFORE-MAY-15** — Android has full disclosure (`AiSection.kt:28-37`); web has only privacy-policy link in About |
| Privacy policy / ToS links | At parity (`AboutSection.tsx:14-21`) |
| Data export (JSON+CSV) | At parity |
| Data import (JSON merge/replace) | At parity |
| Delete-all-data (local) | DEFER-TO-G.0 — web `SettingsScreen.tsx:1011-1066` toasts success without doing the work |
| Data Safety form (Play) | ACCEPT-AS-DIVERGENCE (Android-only artifact) |

### Surface 9 triage summary
- **SHIP-BEFORE-MAY-15: 3** (web AI opt-out toggle, web Anthropic disclosure surface, web client respect of synced flag)
- DEFER-TO-G.0: **1** (fake "Delete All Data" button)
- ACCEPT-AS-DIVERGENCE: **1**
- ✅ At parity: privacy/ToS links, export/import

### Wrong-premise flags
- The note that PR #790 shipped cross-platform privacy — only the Android side shipped + web account-deletion. **Web AI opt-out parity is unfinished.** This is a Play-Store-risk-class gap.

---

## Surface 10 — Daily essentials / Today screen

### Axis A — Sync parity

- Android: `DailyEssentialSlotCompletionEntity` (table `daily_essential_slot_completions`), unique `(date, slot_key)` + `cloud_id`. Wired into both Firestore (`SyncService.kt:532, 2310`) AND backend HTTP (`BackendSyncService.kt:55, 385, 685`).
- Web: backend HTTP only (`web/src/api/dailyEssentials.ts`). No Firestore module.

**Sync gaps:**
| Gap | Triage |
|---|---|
| Dual path means two writers — possible race if web POSTs while Android Firestore push hasn't propagated | DEFER-TO-G.0 (overlaps Surface 6 split-brain) |
| **Web cannot generate virtual slots** — `MedicationSlotList.tsx:75-76`: "Web does not derive virtual slots the way Android does". Web users without an Android device see empty Daily Essentials forever | **SHIP-BEFORE-MAY-15** (verify whether empty-state copy at `:130-134` is the accepted answer) |

### Axis B — Feature parity

| Action | Status |
|---|---|
| Today section: progress ring + counts | parity |
| Habit chips | parity |
| Overdue/Today/Upcoming sections | parity |
| Daily Essentials 7-card layout | web ~10% of Android scope (only Medication card) |
| Plan-For-Today sheet | web missing |
| Balance bar (`BalanceTracker`, `LifeCategory`) | web missing entirely (no `features/balance/` directory) |
| Burnout badge | web partial (boundary banner exists) |
| Self-care nudge | web missing |
| Section reorder + visibility (`DashboardPreferences`) | web missing |
| Morning check-in card | parity |
| Logical-day SoD via `useLogicalToday` | parity (PR #798 compliant) |
| AI Briefing teaser | parity |

**Feature gaps:**
| Gap | Triage |
|---|---|
| Web missing 6/7 Daily Essentials cards | DEFER-TO-G.0 |
| Plan-For-Today sheet absent on web | DEFER-TO-G.0 |
| Balance bar / `LifeCategory` / `BalanceTracker` absent on web | DEFER-TO-G.0 |
| `DashboardPreferences` absent on web | DEFER-TO-G.0 |
| Web users without Android show empty medication slots | **SHIP-BEFORE-MAY-15** (verify) |

### Surface 10 triage summary
- SHIP-BEFORE-MAY-15: **1** (verify or document)
- DEFER-TO-G.0: **4**
- ACCEPT-AS-DIVERGENCE: **0**
- ✅ At parity: **~50%** — core Today feed yes, full wellness-overlay no

---

## Surface 11 — Morning check-in flow

### Axis A — Sync parity

- Android: `CheckInLogEntity`, `check_in_logs` table, unique `(date)` + `cloud_id`. Synced (`SyncService.kt:505, 2254`).
- Web: Firestore-native via `web/src/api/firestore/checkInLogs.ts`. **Doc-id = ISO date** (`:42-44`), `setDoc(merge)` (`:82`). Same Firestore path as Android.
- **Doc-id scheme divergence:** Android writes via Firestore-generated doc IDs; web writes via deterministic `dateIso`. Means Android-pushed and web-pushed docs for the same date can coexist as two cloud rows. Android's `(date)` UNIQUE index could throw on next pull.

**Sync gaps:**
| Gap | Triage |
|---|---|
| Doc-id divergence + Android UNIQUE constraint risk | **SHIP-BEFORE-MAY-15** (verification + harden) |
| Web doesn't write `cloud_id` field into doc | DEFER-TO-G.0 (CloudIdOrphanHealer mitigates after pull) |

### Axis B — Feature parity

| Action | Status |
|---|---|
| Toggle medications/tasks/habits | partial (web has subset; 3 of 6 steps) |
| Streak compute (forgiveness-first) | parity (`web/src/utils/checkInStreak.ts:5-14` mirrors Android) |
| 90-day history surface | web missing |
| MOOD_ENERGY step inline | web divergence (separate `MoodScreen` only) |
| BALANCE step | web missing |
| CALENDAR step | web missing |
| Auto-prompt before 11am threshold | web missing |
| `useLogicalToday(startOfDayHour)` | parity (PR #798 compliant) |

**Feature gaps:**
| Gap | Triage |
|---|---|
| Web check-in is checkboxes-only; missing mood/energy, balance, calendar steps | DEFER-TO-G.0 |
| No 11am auto-prompt threshold on web | DEFER-TO-G.0 |
| No history visualization on web | DEFER-TO-G.0 |

### Surface 11 triage summary
- SHIP-BEFORE-MAY-15: **1** (doc-id coexistence risk — verify)
- DEFER-TO-G.0: **4**
- ACCEPT-AS-DIVERGENCE: **0**
- ✅ At parity: **partial** — core write+streak parity yes, full step-flow no

### Wrong-premise flags
- The user-task brief's "wellness modules might not be wired" — **false**. `checkInLogs.ts`, `moodEnergyLogs.ts`, `focusReleaseLogs.ts` are imported via deep paths by 11+ callers.

---

## Surface 12 — Mood / energy logging

### Axis A — Sync parity

- Android: `MoodEnergyLogEntity`, `mood_energy_logs` table, unique `(date, time_of_day)` + `cloud_id`. Synced (`SyncService.kt:512, 2268`).
- Web: Firestore-native via `web/src/api/firestore/moodEnergyLogs.ts`. Auto-generated doc id (`:97`); query by `dateIso` field. **No uniqueness guard on `(dateIso, timeOfDay)`** — two web logs same slot will fail Android Room insert.

**Sync gaps:**
| Gap | Triage |
|---|---|
| Web `createLog` lacks `(date, time_of_day)` UNIQUE constraint; Android pull risks orphaned cloud row | **SHIP-BEFORE-MAY-15** (query-then-update or deterministic doc id) |

### Axis B — Feature parity

| Action | Status |
|---|---|
| Log mood + energy + notes + time-of-day | parity |
| Per-day rollup chart | parity |
| Avg / best / worst day stats | parity |
| Trend (up/down/stable) | parity |
| **Pearson correlation with tasks/habits/medication adherence/burnout** (`MoodCorrelationEngine`) | **web missing** — TODO at `moodAnalytics.ts:9` explicitly defers to Phase G |
| Delete log entry | parity |
| Time range selector (7/30/90d) | parity |

**Feature gaps:**
| Gap | Triage |
|---|---|
| `MoodCorrelationEngine` not on web | DEFER-TO-G.0 (already explicitly deferred) |

### Surface 12 triage summary
- SHIP-BEFORE-MAY-15: **1** (uniqueness/dedup)
- DEFER-TO-G.0: **1**
- ACCEPT-AS-DIVERGENCE: **0**
- ✅ At parity: **yes** for logging + basic analytics; correlation deferred

---

## Surface 13 — Weekly review

### Axis A — Sync parity

- Android: `WeeklyReviewEntity`, `weekly_reviews` table, unique `(week_start_date)` + `cloud_id`. Synced (`SyncService.kt:526, 2296`).
- Web: **None.** `WeeklyReviewScreen.tsx` recomputes locally each session via `weeklyAggregator.ts`; calls `POST /ai/weekly-review`. Never writes a `WeeklyReview` doc. No `web/src/api/firestore/weeklyReviews.ts`.

**Sync gaps:**
| Gap | Triage |
|---|---|
| Web doesn't persist `WeeklyReview` rows; Android-generated reviews never load on web | DEFER-TO-G.0 |
| Web cannot show Android-generated AI insights and vice versa | DEFER-TO-G.0 |

### Axis B — Feature parity

| Action | Status |
|---|---|
| Local aggregation | parity (`weeklyAggregator.ts:4-10` documents the parity intent) |
| Backend AI narrative call | parity |
| **Persist + browse historical reviews** | **web missing** |
| **Auto-generation on schedule** | **web missing** (no worker) |
| Forgiveness/streak logic | parity (mirrored in `checkInStreak.ts`) |
| Free-tier local fallback | parity |

**Feature gaps:**
| Gap | Triage |
|---|---|
| No persisted history list on web | DEFER-TO-G.0 |
| No background generation worker on web | DEFER-TO-G.0 |

### Surface 13 triage summary
- SHIP-BEFORE-MAY-15: **0**
- DEFER-TO-G.0: **4**
- ACCEPT-AS-DIVERGENCE: **0**
- ✅ At parity: **partial** — generation + display yes, persistence + history no

---

## Surface 14 — Voice input

### Axis A — Sync parity
- `VoicePreferences` (`app/.../data/preferences/VoicePreferences.kt:15-53`) is local-only on Android (DataStore `voice_prefs`, no `cloud_id`, no Firestore sync).
- No voice-command history persisted anywhere.

### Axis B — Feature parity
- **Android voice surface (all present):** `VoiceInputManager.kt` (SpeechRecognizer wrapper, RMS, partial transcripts, continuous mode), `VoiceCommandParser.kt`, `TextToSpeechManager.kt`, `VoiceInputButton.kt` + `QuickAddBar.kt:197,283` (mic + long-press hands-free), `VoiceInputSection.kt`, `RECORD_AUDIO` perm in `QuickAddBar.kt:90-92`.
- **Web voice surface:** None. Grep for `SpeechRecognition|webkitSpeech|speechSynthesis|dictation` returns **zero results**.

### Documented divergence shape
- **Already explicitly documented**: `docs/WEB_PARITY_GAP_ANALYSIS.md:311` lists "No voice/speech integration" in SLICE 1 "Not in scope".
- **No dead/orphan UI on web** that references voice (no broken icon, no "Coming soon" voice button).
- NLP runs both client- and server-side, so text-input parity is achievable independent of voice.
- Intentional and reasonable: Web Speech API browser support is patchy; continuous hands-free is much weaker outside a native app.

### Surface 14 triage summary
- SHIP-BEFORE-MAY-15: **0**
- DEFER-TO-G.0: **0**
- ACCEPT-AS-DIVERGENCE: **1** (entire voice surface)
- ✅ At parity: **n/a** (intentional divergence)

### Wrong-premise flags
None. Light suggestion (NOT a fix-now item): a one-line "Voice input is Android-only by design" added to `docs/WEB_PARITY_GAP_ANALYSIS.md` § Summary would make it ironclad for Phase F testers.

---

## Surface 15 — Notifications

### Axis A — Sync parity
- `NotificationProfileEntity` (`app/.../entity/NotificationProfileEntity.kt:32-35`, table `reminder_profiles`) has `cloud_id` (v54→v55). Quiet hours serialized as JSON inside the row. `CustomSoundEntity` also has `cloud_id`.
- **Web has NO notification-profile reads/writes.** Grep for `notificationProfile|NotificationProfile|QuietHours` in `web/src/` returns **zero**. Profile rows sync Android↔Android only; web is non-participant by design.

### Axis B — Feature parity

| Feature | Android | Web |
|---|---|---|
| Task reminders | `ReminderScheduler` + `AlarmManager` + `BootReceiver` | `web/src/utils/notifications.ts:42-66` defines `scheduleReminder`/`cancelReminder` — **zero callers** (verified). `requestNotificationPermission` wired into Settings only. |
| Medication reminders | full scheduler family | None. Settings can edit defaults — explicitly labels "Reminder delivery is currently Android-only" (`MedicationReminderModeSection.tsx:104`) |
| Habit reminders | full | None |
| Escalation chains | `EscalationScheduler` | None |
| Custom sounds + vibration | `SoundResolver`, `VibrationAdapter`, `CustomSoundEntity`, `VibrationPatterns` | None |
| Daily digest / briefing | `BriefingNotificationWorker`, `EveningSummaryWorker`, `WeeklyHabitSummaryWorker`, `WeeklyTaskSummaryWorker`, `WeeklyReviewWorker`, `ReengagementWorker` | None. Backend has `apscheduler` but only `calendar_periodic_sync` is registered. No briefing/digest cron, no FCM-out, no email-out. |
| Profiles + quiet hours | `NotificationProfile*` + `QuietHoursDeferrer` + `ProfileAutoSwitcher` | None |
| Permission gate | `POST_NOTIFICATIONS` runtime perm; `ExactAlarmHelper` | `Notification.requestPermission()` button — but currently a no-op |
| PWA infrastructure | n/a | `manifest.json` (standalone) + `sw.js` (shell cache only — no `push`/`notificationclick` schedule handler beyond focus-window stub at `:67-80`). No FCM web client. No VAPID. |

### Documented divergence shape
- **Already documented:**
  - `docs/WEB_PARITY_GAP_ANALYSIS.md:252,257` (Web Push out of scope)
  - `README.md:129` ("v1.7+ Medication reminders: Web Push delivery" — explicitly future)
  - `MedicationReminderModeSection.tsx:104` (in-product Android-only banner)
- **Recommended Phase F test-brief callout:** testers should expect medication / habit / task reminders to fire on the Android device only.

### Surface 15 triage summary
- SHIP-BEFORE-MAY-15: **0** (the dead `scheduleReminder` is invisible foot-gun)
- DEFER-TO-G.0: **0** (Web Push roadmap item already explicitly v1.7+/Phase G)
- ACCEPT-AS-DIVERGENCE: **1** (entire notification-delivery surface)
- ✅ At parity: **partial-by-design** (entity sync ✅; delivery ❌; settings UI for medication ✅)

### Wrong-premise flags
- **Minor: dead `scheduleReminder` in `web/src/utils/notifications.ts`** is a future-contributor foot-gun. Not a fix-now item; a one-line `// TODO: replace with Web Push (see README v1.7+ roadmap)` comment would prevent it.
- No web feature links to a notification-dependent flow that silently fails (verified — high-risk dead-UI candidate `MedicationReminderModeSection` has the explicit Android-only banner).

---

## Surface 16 — Onboarding

### Axis A — Sync parity

- Android: `OnboardingPreferences.kt:31-60` DataStore booleans. Synced via `PreferenceSyncModule:66` (in `onboarding_prefs` set).
- Web: `onboardingStore.ts:25-83` writes `onboardingCompletedAt` to Firestore at `users/{uid}` (top-level field).
- **Cross-device implication:** Android writes go to `users/{uid}/prefs/onboarding_prefs.has_completed_onboarding`; web writes go to `users/{uid}.onboardingCompletedAt`. **TWO DIFFERENT FIRESTORE LOCATIONS.** A user completing onboarding on Android does not satisfy the web check, and vice versa.

**Sync gaps:**
| Gap | Triage |
|---|---|
| **Onboarding completion stored at different Firestore paths** — user signing in to second device sees onboarding again | **SHIP-BEFORE-MAY-15** (per `onboardingStore.ts:6-21` doc this was supposed to be account-wide) |
| Battery optimization prompt is Android-only | ACCEPT-AS-DIVERGENCE |

### Axis B — Feature parity

| Page | Triage |
|---|---|
| Welcome (with sign-in shortcut) | DEFER-TO-G.0 (web has no sign-in shortcut here) |
| Theme picker (4 themes) | At parity |
| Smart tasks intro | At parity |
| Natural language demo | DEFER (animated vs static) |
| Habits intro | DEFER (animated vs static) |
| **Templates picker** | DEFER-TO-G.0 (web shows bullets only — `OnboardingScreen.tsx:32-39` documents this) |
| Views intro | At parity |
| **Brain Mode picker** | ACCEPT-AS-DIVERGENCE (web disclaimer at `:232-238`) |
| Setup (sign-in + first task) | DEFER-TO-G.0 |
| Skip flow | At parity |
| Permission asks | ACCEPT-AS-DIVERGENCE |

### Surface 16 triage summary
- SHIP-BEFORE-MAY-15: **1** (Firestore path mismatch)
- DEFER-TO-G.0: **4**
- ACCEPT-AS-DIVERGENCE: **2**
- ✅ At parity: theme + smart tasks + views + skip

### Wrong-premise flags
- The author of `onboardingStore.ts` was aware Android uses a different path. Don't try to "sync" between the two paths — pick which one wins (recommend web's top-level field; less Android churn to add a backfill read).

---

## 17. Cross-cutting findings + deadline-realism check

### 17.1 Cross-surface patterns (high signal — surfaced in multiple surfaces)

**P1 — Web write paths systematically destroy Android-only fields.** Same root-cause shape across Surfaces 2 (habits) and 3 (tasks). Pattern: `web/src/api/firestore/{tasks,habits}.ts` build-whole-doc instead of preserving unknown fields. Fix shape: switch to `setDoc({...}, {merge: true})` or explicit field-whitelist `updateDoc`. **One PR per file.**

**P2 — Web has no real-time listener wiring.** Single high-leverage fix in `App.tsx` (~30 lines) unblocks live sync for tasks/habits/projects/tags/medication_slots — entities that ALREADY have web Firestore code.

**P3 — Web sync layer is missing LWW guards + cloud_id dedup + migration model.** Architectural gap; can't be fully closed in 19 days. Mitigations require ~3-4 weeks per estimate.

**P4 — Privacy parity is incomplete.** PR #790 shipped Android side + web account-deletion; web AI opt-out + Anthropic disclosure are still open. Play-Store-risk-class.

**P5 — Web settings sync architecture is broken** — localStorage-only despite the cross-device parity claim. `startOfDayHour` SoD setting demonstrates this — PR #722/#798 unified the helper, not the underlying setting persistence.

**P6 — Doc-id divergence between platforms** for `medication_tier_states` and `check_in_logs` creates two-row coexistence risk. Different shape per surface.

**P7 — Onboarding/account-deletion paths have small-but-load-bearing UX gaps:** restore-pending takeover absent on web, fake change-password/delete-all toasts, onboarding completion path mismatch. Each is small; sum is real.

### 17.2 Total ship-before-May-15 inventory (deduplicated)

| # | Item | Origin |
|---|------|--------|
| 1 | Wire `subscribeTo*` from `App.tsx` (live sync for 5+ entity types) | Surface 6 |
| 2 | Web tasks.ts merge-on-write (preserve `dueTime`, `isFlagged`, `lifeCategory`, `userOverrodeQuadrant`, `archived_at`, `source_habit_id`, Focus-Release fields) | Surface 3 (T-S1/2/3, T-F1/2/3) |
| 3 | Web habits.ts merge-on-write (preserve booking, built-in identity, Today-skip, etc.) | Surface 2 (H-S1/2) |
| 4 | Web `completedDateLocal` parity (timezone-neutral day key) | Surface 2 (H-S4) |
| 5 | Web habit streak forgiveness-first (port `DailyForgivenessStreakCore`) | Surface 2 (H-F1) |
| 6 | Web `task_completions` write path → Firestore | Surface 3 (T-S4), Surface 6 |
| 7 | Web AI opt-out toggle + flag-respect interceptor | Surface 9 |
| 8 | Web Anthropic egress disclosure surface | Surface 9 |
| 9 | Web settings persistence → Firestore (mirror to `users/{uid}/prefs/user_prefs`) | Surface 8 |
| 10 | Web `startOfDayHour` cross-platform sync | Surface 8 |
| 11 | Web `RestorePending` UI on sign-in | Surface 7 |
| 12 | Remove or wire fake change-password / fake delete-all on web | Surfaces 7, 9 |
| 13 | Onboarding completion path unification | Surface 16 |
| 14 | `medication_slots` collection-name reconciliation | Surfaces 1, 6 |
| 15 | `medication_tier_states` doc-id scheme reconciliation | Surfaces 1, 6 |
| 16 | `check_in_logs` doc-id coexistence guard | Surface 11 |
| 17 | `mood_energy_logs` `(dateIso, timeOfDay)` uniqueness guard | Surface 12 |
| 18 | Daily essentials web-bootstrap (virtual slots OR document) | Surface 10 |
| 19 | `daily_essential_slot_completions` split-brain decision | Surfaces 6, 10 |
| 20 | Dead `web/src/api/sync.ts` architecture decision (delete vs wire) | Surface 3 (T-S8) |
| 21 | LWW timestamp guards on web writes | Surface 6 |
| 22 | `medications` + `medication_doses` web sync OR formalize divergence | Surface 6 |
| 23 | M-F4 Medication reminder-mode banner copy fix (1-line) | Surface 1 |

**Raw count: 23 items.**

### 17.3 🛑 Deadline-realism check

The user-prompt threshold is **8+ items → STOP and surface to Avery**. Current count is **23 distinct SHIP-BEFORE-MAY-15 items** post-dedup.

Even with aggressive bundling (e.g., items 2+3+5 as a "preserve-on-write + forgiveness-streak" trio in 2 PRs, items 7+8 as a single privacy PR, items 11+12 as one account-UX PR), the floor is **~12-14 PRs** for the launch-blocking subset. Plus the 19-day window already has Phase F prep eating bandwidth (kickoff checklist, runbook execution, on-device verification of widget release, cross-device-tests RED state still unresolved per `CONNECTED_TESTS_FLAKE_TRIAGE_2026-04-26.md`).

**Verdict:** Full SHIP-BEFORE-MAY-15 inventory is incompatible with the 19-day window. Re-triage required before Phase 2 can begin.

### 17.4 Recommended re-triage (Avery to approve)

#### Tier A — Privacy / data-integrity / data-loss (must ship; Play-Store risk if absent)

| # | Item | Estimated effort |
|---|------|------------------|
| 1 | Web AI opt-out toggle + Anthropic disclosure + sync-flag interceptor (items 7+8 bundled) | ~1 day (1 PR) |
| 2 | Web tasks.ts + habits.ts merge-on-write (items 2+3 bundled — same root-cause shape) | ~1.5 days (2 PRs, one per file) |
| 3 | Web `RestorePending` UI on sign-in (item 11) | ~0.5 day (1 PR) |
| 4 | Remove fake change-password & fake delete-all on web (item 12) | ~0.5 day (1 PR) |
| 5 | Onboarding completion path unification (item 13) | ~0.5 day (1 PR) |

**Tier A total: ~4 days, 6 PRs.** All small-to-medium scope, no migrations.

#### Tier B — High-leverage cross-device wins (strong recommend if Tier A finishes early)

| # | Item | Estimated effort |
|---|------|------------------|
| 6 | Wire `subscribeTo*` from `App.tsx` (item 1) — unblocks live sync for already-implemented entities | ~1-2 days (1 PR) |
| 7 | Web habit streak forgiveness-first (item 5) — user-trust killer | ~0.5 day (1 PR) |
| 8 | Web `completedDateLocal` parity (item 4) — DST drift fix | ~0.5 day (1 PR) |
| 9 | `mood_energy_logs` uniqueness guard (item 17) | ~0.5 day (1 PR) |
| 10 | M-F4 medication reminder-mode banner copy fix (item 23) | ~5 minutes (1 PR) |

**Tier B total: ~3-4 days, 5 PRs.**

#### Tier C — Defer to G.0 with explicit divergence documentation in Phase F test brief

All medication-collection-name + tier-state-doc-id reconciliation (items 14, 15, 22), `task_completions` web sync (item 6), daily essentials split-brain (items 18, 19), web settings full sync infrastructure (items 9, 10), check-in doc-id coexistence (item 16), dead `sync.ts` decision (item 20), LWW timestamp guards (item 21), and all Surface-6 DEFER-TO-G.0 items.

**Rationale:** The medication divergence is already pre-warned in product copy. The settings localStorage limitation is invisible to single-device users (the bulk of Phase F testers). The cross-device sync architectural work is genuinely 3-4 weeks per the Surface 6 estimate.

**Tier C documentation requirement:** add a "Known divergences for Phase F" section to the test brief (`docs/audits/PHASE_F_REGRESSION_SWEEP_METHODOLOGY.md` or the Phase F kickoff checklist) so testers know what to expect.

### 17.5 Tier-A-and-B totals

- 11 PRs
- ~7-8 days of work (one engineer, sequential)
- ~5-6 days if 2 PRs in parallel via worktrees (per memory entry #16)

This fits the 19-day window with breathing room for Phase F prep, on-device verification, and the cross-device-tests RED resolution.

### 17.6 Phase F readiness gate (recommendation)

If Avery approves the Tier A + B re-triage:
- Web is **partial-parity** with documented divergence
- All Tier A items shipped (privacy + data-loss + data-integrity blockers cleared)
- Tier B nice-to-haves shipped where time permits
- Tier C items explicitly documented in test brief

If Avery wants something different (e.g., "ship Tier A only, defer everything else"), that's a smaller scope and probably the safer call given Phase F kickoff timing.

### 17.7 Memory entry candidates (flag for Avery; do not auto-add)

1. **Web sync architecture is structurally thinner than Android** — `api/sync.ts` is a 27-line stub with zero callers; real sync is via `web/src/api/firestore/*.ts` direct Firestore writes with no `cloud_id` dedup, no LWW, no real-time listeners wired. Worth a permanent "web is single-device session viewer for non-tier-A surfaces" reminder.
2. **Acceptable platform divergences worth memorializing**: voice input (mobile-only by design), notification delivery (Web Push deferred to Phase G), advanced settings sections (escalation/custom sounds/shake/Brain Mode/voice/debug-tier — all Android-only by design).
3. **Process learnings about parity-audit shape**: two-axis (sync + feature) per surface caught interactions like "the feature exists on web but the firestore module is wired to a different collection name" that single-axis would miss. Worth keeping the framework for future audits.

---

## Phase 1 conclusion

Phase 1 audit complete. **STOPPING for Avery's re-triage decision** before any Phase 2 fan-out.

The deadline-realism check fires hard: 23 raw SHIP-BEFORE-MAY-15 items vs the 8-item STOP threshold. Tier A (5 PRs, ~4 days) is the recommended absolute minimum. Tier A + B (11 PRs, ~7-8 days) is the recommended target. Everything else explicitly DEFER-TO-G.0 with divergence documented in the Phase F test brief.

Awaiting Avery's call on which tier to execute in Phase 2.
