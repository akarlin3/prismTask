# Medication Tab SoD-reset + Log absolute-timestamp Invariant Audit

**Scope:** Two-property invariant for the medication surface:

1. **Property 1 — Tab resets at Start-of-Day.** Medication-tab "today" views
   must rotate to the new logical day at the user's configured SoD threshold,
   not at calendar midnight. This is the property
   `docs/audits/MEDICATION_SOD_BOUNDARY_AUDIT.md` shipped via PR #798's
   `LocalDateFlow` / `useLogicalToday` helpers.

2. **Property 2 — Log shows absolute timestamps.** Dose-history rows must
   render the actual wall-clock moment the dose was taken, never the
   SoD-shifted `taken_date_local` grouping field. PR #887's
   `formatTimeWithDateIfDifferent` provides row-level disambiguation
   when the wall-clock day differs from the day-card grouping.

**Together** the two properties ensure: a dose taken at 02:00 wall-clock
April 29 (under SoD = 04:00, logical day = April 28) appears on
April 28's Tab card AND shows `Apr 29 · 2:00 AM` on the April 28 day
card in the Log — never `Apr 28 · 2:00 AM` (would mislabel the
calendar date) or bare `2:00 AM` (would hide the cross-day gap).

**Optimization target:** Reliability — verification-first, fix-second.

**Repo state:** Branch `docs/medication-sod-log-invariant-audit` cut from
`main` @ `af24f4ce` (post-PR #904).

---

## Phase 1 — Verification

### Item 1 — Medication-surface inventory

Every Kotlin source file under `app/src/main/java/com/averycorp/prismtask/`
that touches medications was classified by view shape:

| Surface | File | Shape |
|---|---|---|
| Medication Tab | `ui/screens/medication/MedicationScreen.kt`, `…/MedicationViewModel.kt` | Tab/today-view |
| Medication Log | `ui/screens/medication/MedicationLogScreen.kt`, `…/MedicationLogViewModel.kt` | Log/history |
| Medication Refills | `ui/screens/medication/MedicationRefillScreen.kt`, `…/MedicationRefillViewModel.kt` | Schedule/upcoming |
| Time Edit Sheet | `ui/screens/medication/components/MedicationTimeEditSheet.kt` | Edit/create |
| Bulk Mark Dialog | `ui/screens/medication/components/BulkMarkDialog.kt` | Edit/create (no times displayed) |
| Editor Dialog | `ui/screens/medication/components/MedicationEditorDialog.kt` | Edit/create |
| Slot Editor (Settings) | `ui/screens/settings/MedicationSlotsViewModel.kt`, `…/sections/medication/*` | Edit/create |
| Reminder Mode Settings | `…/sections/medication/MedicationReminderModeSettingsViewModel.kt` | Edit/create |
| Morning Check-In meds step | `ui/screens/checkin/MorningCheckInViewModel.kt` (medications block) | Tab/today-view (legacy refill table) |
| Today Screen — `hasMedications` flag | `ui/screens/today/TodayViewModel.kt` (line 629) | Tab/today-view (boolean only) |
| Clinical Report | `domain/usecase/ClinicalReportGenerator.kt` (medication section) | Schedule/historical |

Repository / persistence layer (`data/repository/MedicationRepository.kt`,
`data/local/entity/MedicationDoseEntity.kt`) is out of scope for both
properties — it stores the underlying truth (`takenAt` absolute long,
`takenDateLocal` ISO logical date) that the UI layer derives from.

### Item 2 — Property 1 (SoD reset) verification

Per Tab/today-view-shaped finding:

| Finding | Status | Evidence |
|---|---|---|
| `MedicationViewModel.todayDate` (line 115) | **GREEN** | Backed by `localDateFlow.observeIsoString(taskBehaviorPreferences.getStartOfDay())` — the canonical PR #798 pattern. |
| `MedicationViewModel.todaysDoses` (line 123) | **GREEN** | `flatMapLatest` off `todayDate`; re-keys when SoD boundary crosses. |
| `MedicationViewModel.todaysTierStates` (line 127) | **GREEN** | Same shape as `todaysDoses`. |
| `MedicationViewModel.slotTodayStates` (line 137) | **GREEN** | `combine` over the SoD-aware flows above. |
| `TodayViewModel.hasMedications` (line 629) | **GREEN** | Boolean derived from `medicationRefillRepository.observeAll().map { it.isNotEmpty() }` — independent of any logical date. |
| `MorningCheckInViewModel.medications` (line 89) | **YELLOW** | Reads from the legacy `MedicationRefillRepository` (documented as "going away in Phase 2 cleanup" — `MedicationRefillViewModel.kt:24`) and pairs with a transient `_medicationDoses: MutableStateFlow<Set<Long>>`. The set does not observe `LocalDateFlow`, so a check-in screen kept open across an SoD boundary would not reset its "taken this morning" flags. **Practical impact: nil** — `MorningCheckInResolver` gates the screen via `lastCompletedDate` and the ViewModel scope dies between sessions. Flagged for future-proofing only. |

**Regression-gate test:** `app/src/test/java/com/averycorp/prismtask/ui/screens/medication/MedicationTodayDateRefreshTest.kt`
exists and pins all three Property 1 contracts:

- `todayDate_advancesReactively_whenWallClockCrossesSoDBoundary`
- `todayDate_initialEmission_respectsUserSoD_notZero`
- `todayDate_doesNotReEmit_whenWallClockAdvancesWithinSameLogicalDay`

The file's docstring notes it has been *rewritten* in the
PR #798 audit-first template style (passing = bug fixed), so a
regression that re-introduces the snapshot pattern fails the suite.

### Item 3 — Property 2 (absolute timestamps) verification

Per Log/history-shaped finding:

| Finding | Status | Evidence |
|---|---|---|
| `MedicationLogScreen.DoseRow` (line 310) | **GREEN** | Renders `formatTimeWithDateIfDifferent(dose.takenAt, groupedDate)`. `takenAt` is the absolute long; `groupedDate` is the SoD-grouped LocalDate used only as a comparison anchor. |
| `MedicationLogScreen.SlotTierEntryRow` (line 365) | **GREEN** | Same helper, fed `entry.displayTime = intendedTime ?: loggedAt`. Both source fields are absolute longs from `MedicationTierStateEntity`. |
| `MedicationScreen.MedicationDoseRow` per-med inline label (line 530) | **GREEN** | `SimpleDateFormat("h:mm a").format(Date(takenAt))` — straight wall-clock format off the per-med absolute `takenAt`. |
| `MedicationScreen.takenTimeLabel` slot card (line 682) | **GREEN** | Formats `intendedTime` / `loggedAt` (both absolute) via `h:mm a`. Surfaces both sides of `isBacklogged` when relevant — preserves the gap rather than collapsing to one wall-clock value. |
| `MedicationLogViewModel` grouping (line 59, 64) | **GREEN** | Uses `takenDateLocal` / `logDate` for the day-card key only — never displayed as a time. The Log screen's `groupedDate` parsed from `day.date` is fed back into `formatTimeWithDateIfDifferent` purely as a comparison anchor. |
| `ClinicalReportGenerator.medicationSection` (line 132) | **GREEN** | Renders `med.lastRefillDate` (absolute long) via `dateFormat.format(Date(it))`. No SoD shift. |

**Regression-gate tests:**

- `app/src/test/java/com/averycorp/prismtask/ui/screens/medication/MedicationLogTimeFormatTest.kt`
  pins `formatTimeWithDateIfDifferent` across five cases: same-day, post-SoD
  early-morning, late-SoD prior-day, year-boundary, and null grouped-date
  fallback.
- `app/src/test/java/com/averycorp/prismtask/ui/screens/medication/TakenTimeLabelTest.kt`
  pins the slot-card `takenTimeLabel` helper across seven cases: nothing
  taken, missing timestamps, `loggedAt` fallback, `intendedTime` preference,
  backlogged-shows-both-moments, override-without-meds, and SKIPPED-suppression.

**No instances** of `taken_date_local` / `takenDateLocal` being displayed
as a time-of-day were found. The field only appears as a grouping key in
`MedicationLogViewModel` and an index column in `MedicationDoseEntity` —
both correct uses.

---

## Phase 1 — Recommendation

**ALL-CORRECT** with one **YELLOW anti-pattern** flagged for awareness.

Both Property 1 and Property 2 hold across every in-scope medication
surface. The canonical PR #798 (`LocalDateFlow.observeIsoString`) and
PR #887 (`formatTimeWithDateIfDifferent`) patterns are correctly applied
on the Tab and Log screens respectively, with regression-gate tests in
place that follow the audit-first template (passing = invariant holds).

### Anti-patterns flagged (no fix proposed)

1. **`MedicationTimeEditSheet` composes user-picked HH:mm with wall-clock
   day, not logical day.** When the user long-presses a slot's tier chip
   in the SoD-boundary window (e.g. wall-clock 02:00 April 29 with
   SoD = 04:00 → logical day = April 28) and picks an early-morning time
   like 08:00, the sheet composes "April 29 08:00" and caps to `now`
   (April 29 02:00). The intended record is "I took my April 28 morning
   slot at 08:00" → April 28 08:00. The current cap-to-now masks the
   forward-day overflow but silently produces an April 29 timestamp on
   an April 28 slot. The sheet's docstring (line 38–39) explicitly notes
   "Cross-day backlogging is intentionally out of scope for v1; long-tail
   'log yesterday's dose' happens via the medication log screen" — so
   this is a documented v1 design limitation, not an undocumented bug.
   Resolving it requires plumbing the logical day into the sheet and
   extracting a testable compose helper. Out of scope for verification
   audit; revisit if Phase F testers report confusion.

2. **`MedicationRefillViewModel.medications` forecast is a one-shot
   snapshot.** `RefillCalculator.forecast(med, now)` runs inside
   `observeActive().map { … }` with `now = System.currentTimeMillis()`
   captured at upstream emission. If the user keeps the Refills screen
   open across midnight, "X days remaining" is stale until `observeActive`
   re-emits (typically next medication change). Schedule/upcoming-shaped,
   not a Property 1 violation, but a near neighbour. Low impact (off
   by 1 day at worst) and self-corrects on any med edit.

3. **`MorningCheckInViewModel._medicationDoses`** — see Item 2 above.

### Wall-clock-savings ÷ implementation-cost ranking

| Improvement | WC-savings (per quarter) | Cost | Ratio |
|---|---|---|---|
| Audit doc as durable reference | ~30 min (avoids re-deriving findings) | 0 (already produced) | ∞ |
| Fix `MedicationTimeEditSheet` cross-day compose | ~5 min (Phase F bug-report triage) | 60–90 min (helper + test + plumb logical day) | low |
| Fix `MedicationRefillViewModel` forecast staleness | ~0 min (no reports) | 30 min | low |

The audit-doc-as-durable-reference is by far the highest-ratio output;
nothing else in the table justifies a Phase 2 PR right now.

---

## Phase 2 — Implementation

**No Phase 2 PRs.** ALL-CORRECT recommendation closes the loop on the
two-property invariant. The three anti-patterns above are documented
here as durable evidence for future Phase F bug triage; none rise to
the threshold of a regression that warrants shipping a fix without an
observed user report.

This audit doc itself (filed under `docs/audits/`, the canonical
location) is the deliverable.

---

## Reference

- **PR #798** — `MedicationScreen` SoD bug + `LocalDateFlow` /
  `useLogicalToday` helper (canonical Property 1 pattern).
- **`docs/audits/MEDICATION_SOD_BOUNDARY_AUDIT.md`** — the original
  PR #798 audit; this audit closes the loop on the same surface
  three months later.
- **`docs/audits/UTIL_DAYBOUNDARY_SWEEP_AUDIT.md`** — closed
  2026-04-26 with mixed per-caller outcome; relevant because the
  medication sweep was one of the surfaces it triaged.
- **PR #885** — clock harness extraction for `util/DayBoundary`
  (clock-injection pattern useful for Property 1 testing).
- **PR #887** — `formatTimeWithDateIfDifferent` for Log row-level
  disambiguation (canonical Property 2 pattern).
- **PR #830** — medication slot tier-marking (template for
  unit-test density on slot-related changes).
- **PRs #851 / #855** — medications natural-key dedup (sync surface
  not touched by this audit; preserved by recommendation NIL).
