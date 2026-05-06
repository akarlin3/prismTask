# F.5 Medication Hygiene Follow-Ons — Phase 1 Audit

**Scope.** Bundle four medication-flow hygiene follow-ons from the
`D_MEDICATION_ADD_CRASH` audit + PR #1141: F.5a slot-insert hardening,
F.5b `MedicationRefillViewModel` try/catch hygiene, F.5c Compose UI test
for the dialog gating change, F.5d cross-surface (Postgres + Room +
Firestore) duplicate-name reconciliation.

**Operator-locked scope.** Single bundle PR, all four surfaces. Phase 3 +
Phase 4 fire pre-merge per CLAUDE.md repo-convention override.

**Anchor pattern (PR #1141, SHA `37a18b1`).** Pre-flight `getByNameOnce`
guard → branches (active duplicate / archived unarchive / fresh insert)
→ outer `try/catch` routing to `_errorMessages: MutableSharedFlow<String>`
→ `MedicationScreen` `SnackbarHost` collects. Snackbar copy template:
`'A medication named "$NAME" already exists. Edit it instead, or pick a different name.'`

---

## A.1 PR #1141 pattern (the reusable template) (GREEN)

Verified the shipped fix shape directly from `MedicationViewModel.kt`
lines 312–446. Load-bearing pieces:

* **Pre-flight signature.** `medicationRepository.getByNameOnce(trimmed)`
  returns `MedicationEntity?` (nullable; archived rows included).
* **Branches.**
  * `existing == null` → `medicationRepository.insert(...)` returns new id.
  * `existing.isArchived` → `medicationRepository.update(existing.copy(...
    isArchived = false))`, reuse `existing.id` (preserves dose history).
  * else → emit snackbar message, `return@launch`.
* **Try/catch.** Outer `try { ... } catch (e: Exception) { Log.e(...);
  _errorMessages.emit("Couldn't save medication. Please try again.") }`.
* **Surface.** `MedicationScreen` collects `viewModel.errorMessages` into
  the existing `SnackbarHost`.
* **`updateMedication` rename collision.** Same pre-flight, but compares
  against `medication.id` so a no-op self-rename doesn't trigger.

Pattern is clean and reusable for any other ViewModel that writes to a
table with a user-input `UNIQUE` index.

## A.2 F.5a recon — Slot insert (GREEN — premise wrong, no work needed)

**Premise verification.** Audit prompt assumed
`MedicationSlotsViewModel.create` shares the PR #1141 crash class
because the slot table has a `name` unique index with
`OnConflictStrategy.ABORT`. **This premise is wrong.**

**Findings.**

* `MedicationSlotEntity.kt` lines 30–35 declare a single unique index:
  `Index(value = ["cloud_id"], unique = true)`. There is **no** unique
  index on `name`. Two active slots can be named "Morning" without
  violating any DB constraint.
* `MedicationSlotDao.insert` (line 51) does use
  `OnConflictStrategy.ABORT`, but `cloud_id` is `null` on local create
  (assigned post-sync), so a fresh insert can never conflict on the only
  unique column.
* `MedicationSlotsViewModel.create` (settings layer, lines 34–57) does
  bare `viewModelScope.launch { ... insertSlot(...) }` — same shape as
  the pre-PR-#1141 medication path — but the underlying DB write cannot
  throw `SQLiteConstraintException` from any user-input collision.

**Verdict.** The `SQLiteConstraintException` crash class doesn't exist
on slots. PR #1141 hardening pattern has nothing to land against here.

**Out-of-scope follow-up (NOT in this bundle).** Whether duplicate slot
*names* should be UX-prevented (two "Morning" slots is confusing on the
Today screen) is a product question, not a crash-class hygiene one.
Operator decision; deferred.

**Recommendation.** **STOP-no-work-needed** for F.5a. Documented in
Phase 4 handoff.

## A.3 F.5b recon — RefillViewModel (YELLOW → PROCEED)

**Premise verification.** Audit prompt: "RefillViewModel is safe today
(already does pre-check + update-or-insert) but lacks top-level
try/catch." **Confirmed.**

**Findings.**

* `MedicationRefillViewModel.addMedication` (lines 51–97) does
  `repository.getByNameOnce(trimmed)` pre-flight then either updates the
  existing row or inserts. Today the pre-check is exhaustive — no path
  reaches `insert` with a colliding name — so it cannot crash on the
  unique-name index.
* No `try/catch` anywhere in the file. Bare `viewModelScope.launch` in
  four methods: `addMedication`, `recordDailyDose`, `recordRefill`,
  `disableRefillTracking`.
* No `_errorMessages` SharedFlow on this ViewModel. The Refill screen
  has no Snackbar collector wired today.
* Plausible new exception sources if the path grows: any new repository
  call (e.g. backend pharmacy lookup), batch operations, alarm
  rescheduler hooks. Defense-in-depth is missing.

**Verdict.** Hygiene gap, not a crash today. The fix is mechanical:
mirror PR #1141's `errorMessages` SharedFlow + try/catch pattern; wire
`MedicationRefillScreen` to collect it.

**Recommendation.** **PROCEED.** ~50–70 LOC including a regression test.

## A.4 F.5c recon — Compose UI test (GREEN → PROCEED)

**Premise verification.** Dialog gating change shipped in PR #1141
(`MedicationEditorDialog.kt` line 250):
`enabled = name.isNotBlank() && (selections.isNotEmpty() || activeSlots.isEmpty())`.
Unit test coverage for the VM layer exists in
`MedicationViewModelAddMedicationTest.kt`. No Compose UI test asserts
the actual button state.

**Findings.**

* Existing Compose UI test convention: `app/src/androidTest/` with
  `androidx.compose.ui.test.junit4.createComposeRule()` and
  `@RunWith(AndroidJUnit4::class)`. Reference:
  `app/src/androidTest/java/com/averycorp/prismtask/ui/screens/review/WeeklyReviewsListScreenTest.kt`.
* `app/build.gradle.kts` line 377:
  `androidTestImplementation("androidx.compose.ui:ui-test-junit4")` is
  already on the classpath.
* `MedicationEditorDialog` is a pure Composable — takes
  `activeSlots: List<MedicationSlotEntity>`, `initialSelections`,
  `onConfirm`, `onCreateNewSlot` as parameters. No Hilt, no DB,
  amenable to direct `composeRule.setContent {...}` testing.
* TestDatabaseModule (memory #17) is **not required** here — the dialog
  has no Room or Hilt dependency. F.5c's test can be Hilt-free.
* Conventional location:
  `app/src/androidTest/java/com/averycorp/prismtask/ui/screens/medication/components/MedicationEditorDialogTest.kt`.

**Test scope (5 cases).**

1. Blank name + empty selections + activeSlots present → Save disabled.
2. Non-blank name + empty selections + activeSlots present → Save
   disabled (the regression-prevention case PR #1141 fixed).
3. Non-blank name + non-empty selections + activeSlots present → Save
   enabled.
4. Non-blank name + empty selections + activeSlots empty → Save enabled
   (bootstrap path — first med with zero slots).
5. Blank name + non-empty selections → Save disabled.

**Verdict.** Well-defined; fits the in-repo Compose-test convention.

**Recommendation.** **PROCEED.** ~80–120 LOC.

## A.5 F.5d recon — Cross-surface dedup (GREEN — Mode 1, paper-closure)

**Premise verification.** Memory #8 says medications live in three
storage layers (Postgres backend, Room device-local, Firestore-direct
sync). Audit prompt: verify all three agree on duplicate-name behavior
or surface silent divergence.

**Findings.**

* **Room** (`MedicationEntity.kt` lines 27–28):
  `Index(value = ["cloud_id"], unique = true)` and
  `Index(value = ["name"], unique = true)`. Local `name`-uniqueness is
  enforced.
* **Postgres** (`backend/alembic/versions/019_add_medication_entities_and_audit.py`):
  table `medications` has `Column("name", String(255), nullable=False)`
  and **only** `ix_medications_cloud_id` UNIQUE. There is **no**
  Postgres-level unique constraint on `name`. Same for
  `medication_slots` (cloud_id UNIQUE only).
* **Firestore**: no native unique constraints. Per
  `MedicationSyncMapper`, dedup is application-layer.

The three layers do **not** uniformly enforce `name`-uniqueness: Room
does, Postgres + Firestore do not. Naively this looks like Mode 2
silent divergence — a remote create on web (no Postgres-level
duplicate-name guard) could pull into Room and crash on the unique
constraint.

**But both pull paths already mitigate this**, application-layer:

* **Firestore-pull** (`SyncService.kt` lines 2495–2535): natural-key
  dedup before INSERT. Quote (lines 2500–2506): *"medications.name
  carries a UNIQUE index, so a plain INSERT throws
  SQLiteConstraintException when both devices ran the v53→v54 backfill
  independently and pulled each other's cloud_ids. Adopt the existing
  same-name local row instead; bind its cloud_id and apply
  last-write-wins."* Already shipped in P0 sync audit PR-B.
* **Backend-pull** (`BackendSyncService.kt` lines 580–600 +
  PR #1140 `bf3c292`): `medicationDao.getByCloudIdOnce(cloudId) ?:
  data.optString("name")?.let { medicationDao.getByNameOnce(it) }` —
  cloud_id-then-name fallback. PR #1140 commit message explicitly
  references the `medications.name` UNIQUE index as the reason for the
  fallback.
* **Slots**: Room has no `name` unique on `medication_slots`, so even
  in the absence of a sync-time dedup pass, no crash class exists.

**Verdict.** **Mode 1 (intentional architecture).** Postgres + Firestore
treat `name` as application-data with no DB-level uniqueness; Room
imposes a stricter local invariant; both pull paths enforce that
invariant before INSERT by deduping on `name`. The architecture is
intentional and already protected.

**Out-of-scope follow-up (NOT in this bundle).** Documenting this
intent in CLAUDE.md (memory #8 addendum) is a candidate. Wait-for-third-
data-point applies (PR #1140 + PR #1141 + this audit = three
data-points; arguably documentable now). Operator decision in Phase 4.

**Recommendation.** **DEFER (paper-closure).** No code changes.

## A.6 Sibling crash-class scan (DEFERRED)

Scanned all `Index(...unique = true)` declarations across
`app/src/main/java/com/averycorp/prismtask/data/local/entity/`. User-
input unique columns (excluding `cloud_id`):

* `MedicationEntity.name` — already fixed (PR #1141).
* `MedicationSlotEntity` — only `cloud_id`. Not user-input.
* `NlpShortcutEntity.trigger` — user-input. **No UI ViewModel exists**:
  `grep -r "NlpShortcut" app/src/main/java/com/averycorp/prismtask/ui/`
  returns zero hits. Only DAO, sync, and `DataExporter` consume it. No
  bare-launch insert path from a ViewModel; no crash-class bundle target.
* `MedicationRefillEntity.medication_name` — legacy entity. Per
  `MedicationRefillViewModel` doc-comment: *"the old
  `MedicationRefillRepository` + `MedicationRefillEntity` path is going
  away in Phase 2 cleanup."* Not actively written from a user-facing
  ViewModel today; same `getByNameOnce` pattern guards it.
* Composite-key uniques (TaskDependency `(blocker, blocked)`,
  SelfCareLog `(routine_type, date)`, MoodEnergyLog `(date,
  time_of_day)`, WeeklyReview `week_start_date`): all auto-generated or
  selection-UI-guarded. Not crash classes.

**Verdict.** No sibling crash-class targets to bundle. **DEFERRED**
without re-trigger criteria — would re-fire only if a new user-input
unique constraint lands.

---

## B. Implementation hypotheses

### B.1 F.5a — STOP-no-work-needed

No code change. Document the verdict in Phase 3 + Phase 4. Operator may
elect a separate UX dedup task at the slot-name layer; that's not this
bundle.

### B.2 F.5b — RefillViewModel try/catch

Mirror PR #1141 minimally:

```kotlin
private val _errorMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

fun addMedication(...) {
    if (name.isBlank()) return  // already guarded de-facto via blank-trim
    viewModelScope.launch {
        try {
            // existing pre-check + update-or-insert body
        } catch (e: Exception) {
            android.util.Log.e("MedicationRefillVM", "Failed to save medication", e)
            _errorMessages.emit("Couldn't save medication. Please try again.")
        }
    }
}
// Same wrap on recordDailyDose / recordRefill / disableRefillTracking
// with appropriate copy ("Couldn't record dose" / "Couldn't record refill"
// / "Couldn't update tracking").
```

`MedicationRefillScreen` collects `viewModel.errorMessages` into a
`SnackbarHost` — mirror the wiring in `MedicationScreen` (PR #1141
diff lines added at MedicationScreen.kt). ~50–70 LOC including:

* ViewModel wraps (×4 methods).
* Screen wires `LaunchedEffect` collector + `SnackbarHostState`.
* One regression test in
  `MedicationRefillViewModelTest.kt` (new file) pinning
  "repository throws → error surfaces, no crash."

### B.3 F.5c — Compose UI test

New file: `app/src/androidTest/java/com/averycorp/prismtask/ui/screens/medication/components/MedicationEditorDialogTest.kt`.

* `@RunWith(AndroidJUnit4::class)`, `@get:Rule createComposeRule()`.
* Pure Composable test — no Hilt, no DB, no `TestDatabaseModule` impact
  (memory #17 not relevant here).
* 5 test cases per A.4. Use `onNodeWithText("Save").assertIsEnabled()` /
  `assertIsNotEnabled()`.
* `onConfirm` / `onCreateNewSlot` / `onDismiss` are no-op lambdas (the
  test exercises gating, not callback wiring).
* ~100 LOC.

### B.4 F.5d — paper-closure

No code. Add a one-paragraph note in this audit doc's Phase 3 section
documenting the Mode 1 architecture for future-Claude reference. Do
not amend CLAUDE.md memory #8 in this bundle — that's a separate
documentation pass per memory #30 (don't auto-file deferred items).

---

## C. STOP-conditions evaluated

* **STOP-A** (drive-by already shipped): Partially fired — F.5a's
  premise was wrong (no crash class), so it closes without work.
* **STOP-B** (size > 1500 LOC): NOT fired. Aggregate Phase 2 ≈ 150–200
  LOC.
* **STOP-D** (cross-surface Mode 2/3): NOT fired. Mode 1 confirmed.
* **STOP-E** (Compose test scope balloon via TestDatabaseModule): NOT
  fired. Pure-Composable test, no DB.
* **STOP-F** (sibling crash-class bundling): NOT fired. No siblings.

---

## D. Premise verification (memory #22 bidirectional)

| # | Premise | Verified? | Where |
|---|---------|-----------|-------|
| D.1 | PR #1141 pattern shipped | YES | `MedicationViewModel.kt` 312–446 |
| D.2 | F.5a slot table has `name` unique index | **NO — premise wrong** | `MedicationSlotEntity.kt` 30–35 |
| D.3 | F.5b RefillViewModel safe today (no crash class) | YES | `MedicationRefillViewModel.kt` 51–97 |
| D.4 | F.5c gating change in main | YES | `MedicationEditorDialog.kt` 238–251 |
| D.5 | Memory #8 architecture (Postgres + Room + Firestore) | YES | + intentional Mode 1 |
| D.6 | Snackbar copy template reusable | YES (refill copy diverges by entity verb) | `MedicationViewModel.kt` 348–351 |

D.2 is the load-bearing surprise — it inverts the F.5a verdict from
"PROCEED with mirror fix" to "STOP-no-work-needed."

---

## E. Phase 2 scope (post-Phase-1)

* F.5a: 0 LOC (paper-closure).
* F.5b: ~50–70 LOC (VM wraps + screen wire + 1 test).
* F.5c: ~100 LOC (5 Compose test cases).
* F.5d: 0 LOC (Mode 1 paper-closure).

**Aggregate ≈ 150–170 LOC. Under STOP-B 1500-line ceiling.**

Single bundle PR per operator-locked scope.

---

## Ranked improvement table (wall-clock-savings ÷ cost)

| Rank | Item | Effort | Impact | Verdict |
|------|------|--------|--------|---------|
| 1 | F.5b RefillViewModel try/catch | LOW (~60 LOC) | Defense-in-depth on a screen with no crash today but no recovery if one lands | PROCEED |
| 2 | F.5c Compose UI test for dialog gating | LOW (~100 LOC) | Regression-pin the PR #1141 button-state fix at the rendered-UI layer | PROCEED |
| 3 | F.5a slot-insert hardening | n/a | Premise wrong — no crash class | STOP-no-work-needed |
| 4 | F.5d cross-surface dedup | n/a | Mode 1 — architecture intentional, both pull paths already dedup by name | DEFER (paper-closure) |

---

## Anti-patterns to flag (not in scope to fix)

* `MedicationRefillScreen` has no Snackbar collector wired today; the
  F.5b error surface adds it. Other Refill-adjacent screens (e.g.
  `MedicationLogScreen`) likely share this gap — out of scope, but
  worth a future hygiene sweep.
* `MedicationSlotEntity` lacking a `name`-unique index is intentional
  per repo design (slots can share names if user wants — e.g. "Morning"
  + "Morning (weekend)"). Not flagged for change.
* The `MedicationRefillEntity` legacy table is documented as "going
  away in Phase 2 cleanup" but hasn't gone. Not in F.5 scope.

---

## Open questions for operator

* **Q1 (F.5b copy):** Snackbar copy for refill-screen errors. Default
  proposal: `"Couldn't save medication. Please try again."` for
  `addMedication`, `"Couldn't record dose. Please try again."` for
  `recordDailyDose`, `"Couldn't record refill. Please try again."` for
  `recordRefill`, `"Couldn't update tracking. Please try again."` for
  `disableRefillTracking`. Proceeding with these unless told otherwise.
* **Q2 (F.5d documentation):** Whether to amend CLAUDE.md memory #8
  with the "name-uniqueness is Room-only / sync-paths dedup before
  INSERT" architecture note in this bundle, or defer to a separate
  documentation pass. Defaulting to defer per memory #30.
