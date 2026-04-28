# Remove Medication Reminders From Start Screen — Audit

**Goal:** Remove all medication-reminder UI from the Today (start) screen, while
keeping the backend Medication feature, the Medication settings, refill
projections, and the existing notification-side `MedicationReminderScheduler`
intact.

**Scope chosen by user:** Surfaces #1 and #2 from the disambiguation menu —
the `MedicationCard` inside Daily Essentials AND the Habits-chip → `Medication`
route navigation. Surface #3 (`TodayBurnoutBadge` suggestion text) and the
notification scheduler are explicitly out of scope.

**Process:** Audit-first. Phase 1 = this document only. Phase 2 = removal PR(s)
without checkpoint gates (per `feedback_skip_audit_checkpoints.md`).

**Repo state at start of audit:** worktree
`audit/remove-medication-reminders-start-screen` cut from `origin/main` @
`826791d1` (`docs(audits): CI pathway audit — Phase 3 bundle summary
(post-merge) (#889)`).

---

## Phase 1 — Findings

### Item 1 — Daily Essentials `MedicationCard`

#### Premise

The Today screen's Daily Essentials section renders a "💊 Medication"
card with one row per dose-slot (e.g. `08:00 · Lipitor, Metformin`) and a
trailing checkbox that batch-marks every med in the slot. Tapping the row
opens a slot bottom sheet with per-dose checkboxes.

#### Findings

| File | Lines | Role |
|------|-------|------|
| `app/src/main/java/com/averycorp/prismtask/ui/screens/today/dailyessentials/cards/MedicationCard.kt` | 1–96 | Card composable + per-slot row composable. **Sole production caller**: `DailyEssentialsSection`. |
| `app/src/main/java/com/averycorp/prismtask/ui/screens/today/dailyessentials/cards/MedicationSlotBottomSheet.kt` | (full) | Slot detail sheet. **Sole caller**: `DailyEssentialsSection.kt:85` (`activeSlot?.let { ... MedicationSlotBottomSheet(...) }`). |
| `app/src/main/java/com/averycorp/prismtask/ui/screens/today/dailyessentials/DailyEssentialsSection.kt` | 32–52, 83–92, 121–127 | Owns the `activeSlot` `mutableStateOf`, declares `onToggleMedicationSlot` / `onToggleMedicationDose` actions, renders the card. |
| `app/src/main/java/com/averycorp/prismtask/domain/usecase/DailyEssentialsUseCase.kt` | 83–92, 109, 118, 131, 147, 184, 200, 206, 213–216, 244 | Defines `MedicationCardState`, `MedicationSlot`, `MedicationDose`; injects `MedicationStatusUseCase`; computes `medicationState` and folds it into `DailyEssentialsUiState.medication`. |
| `app/src/main/java/com/averycorp/prismtask/domain/usecase/MedicationStatusUseCase.kt` | (full) | Computes `dueDosesToday`. **Sole caller**: `DailyEssentialsUseCase`. |
| `app/src/main/java/com/averycorp/prismtask/domain/usecase/MedicationSlotGrouper.kt` | (full) | Groups doses into slots + `rowLabel` formatter. **Callers**: `DailyEssentialsUseCase` and `MedicationCard.kt:77`. |
| `app/src/main/java/com/averycorp/prismtask/ui/screens/today/TodayViewModel.kt` | 1115–1128, 1139–1188, 1190–1207 | `onMarkMedicationTaken`, `onMarkNextMedicationTaken`, `onToggleMedicationSlot`, `onToggleMedicationDose`, `markDoseTaken`. |
| `app/src/main/java/com/averycorp/prismtask/ui/screens/today/TodayScreen.kt` | 595–600 | Wires `onToggleMedicationSlot` / `onToggleMedicationDose` into `DailyEssentialsActions(...)`. |

#### Cross-surface dependency check

- **No widget references.** `widget/` has zero matches for `MedicationCard*`,
  `MedicationSlotBottomSheet`, or `onToggleMedication*`.
- **No tests directly assert MedicationCard / SlotBottomSheet rendering.**
  `app/src/test` and `app/src/androidTest` both return zero matches for
  `MedicationCard(`, `MedicationSlotBottomSheet`, `onToggleMedicationSlot`,
  `onToggleMedicationDose`, `onMarkMedicationTaken`, `onMarkNextMedicationTaken`.
- **`DailyEssentialSlotCompletionRepository` keeps its other callers.** The
  repository's `toggleSlot` is only used here for medication today, but the
  repo + materialized table also support morning / bedtime / housework slots —
  do not delete the repository. Just remove the medication call sites.
- **`MedicationStatusUseCase` and `MedicationSlotGrouper` go cold.** Both
  have only `DailyEssentialsUseCase` (and `MedicationCard.kt:77` for
  `MedicationSlotGrouper.rowLabel`) as production callers. Their tests
  (`MedicationStatusUseCaseTest`) become orphaned after the use case is
  removed.
- **`DailyEssentialsUiState.medication: MedicationCardState?`** — only read by
  `DailyEssentialsSection.kt:121` (`state.medication?.let { ... }`) and the
  Today VM's compact-header derivation chain (`dailyEssentials.value.medication?.nextDose`
  in `onMarkNextMedicationTaken`). Both go away.
- **`TodayViewModel.checkInSummaryFlow`** still references
  `medicationRefillRepository.observeAll()` to flag `hasMedications` for the
  morning-check-in banner subtitle (line 634). That is **not** the medication
  card — it's a banner phrasing helper ("You have 3 tasks, 2 habits, and
  medications to check in on"). **Out of scope** for this removal; the banner
  is not a "reminder."
- **`DailyEssentialsUiState.isEmpty`** uses `medication == null` as one of
  its emptiness predicates (line 118). Once the field is removed, the
  predicate simplifies. The `state.isEmpty` short-circuit is what hides the
  whole section + the empty-state hint when nothing is present, so this must
  not regress.

#### Risk classification

- **YELLOW.** Mechanical removal across ~8 files; well-bounded by the
  zero-test, zero-widget cross-surface check. The only subtle risk is
  inadvertently breaking the empty-state path on `DailyEssentialsUiState` when
  `medication == null` was carrying part of the predicate — handled by
  re-deriving `isEmpty` from the remaining fields.
- **No DB schema impact.** `daily_essential_slot_completions` is shared with
  morning/bedtime/housework; do not migrate it.
- **No sync impact.** `DailyEssentialSlotCompletionRepository` /
  `BackendSyncMappers` continue to round-trip the table.

#### Recommendation

**PROCEED.** Single-PR mechanical removal:

1. Delete `MedicationCard.kt`, `MedicationSlotBottomSheet.kt`,
   `MedicationStatusUseCase.kt`, `MedicationSlotGrouper.kt`,
   `MedicationStatusUseCaseTest.kt`.
2. Strip `medication`, `MedicationCardState`, `MedicationSlot`,
   `MedicationDose` types from `DailyEssentialsUseCase.kt`. Drop the
   `medicationStatusUseCase` constructor parameter.
3. Remove `onToggleMedicationSlot`, `onToggleMedicationDose`,
   `onMarkMedicationTaken`, `onMarkNextMedicationTaken`, and the private
   `markDoseTaken` from `TodayViewModel.kt`.
4. Strip the `medication?.let { MedicationCard(...) }` block from
   `DailyEssentialsSection.kt`, the `activeSlot` state + `MedicationSlotBottomSheet`
   call site, and the `onToggleMedicationSlot` / `onToggleMedicationDose`
   fields from `DailyEssentialsActions`.
5. Drop the matching `onToggleMedicationSlot` / `onToggleMedicationDose`
   wiring from `TodayScreen.kt:595–600`.
6. Re-derive `DailyEssentialsUiState.isEmpty` and the empty-state path so
   the section still hides when nothing is left to show.

---

### Item 2 — Habits-chip → `Medication` route navigation

#### Premise

When the medication built-in habit is enabled, the Today screen's Habits row
shows a "Medication" chip. Tapping the chip navigates to `MedicationScreen`
(`PrismTaskRoute.Medication.route`) instead of toggling the habit completion
the way an ordinary chip would.

#### Findings

`app/src/main/java/com/averycorp/prismtask/ui/screens/today/TodayScreen.kt:539–561`:

```kotlin
val route = when (hws.habit.name) {
    SelfCareRepository.MEDICATION_HABIT_NAME ->
        PrismTaskRoute.Medication.route
    SelfCareRepository.MORNING_HABIT_NAME ->
        PrismTaskRoute.SelfCare.createRoute("morning")
    // … other mode-task habits …
    else -> null
}
if (route != null) {
    navController.navigate(route)
} else {
    viewModel.onToggleHabitCompletion(hws.habit.id, hws.isCompletedToday)
}
```

#### Cross-surface dependency check

- **`HabitListScreen.kt:261`** has the same medication → route navigation
  branch but inside the dedicated Habits tab — out of scope (only Today /
  start-screen surfaces are being removed).
- **`MainActivity.kt:287`** + **`NavGraph.kt:283, 561`** + **`ModeRoutes.kt:60`**
  register and host `MedicationScreen`. Stay untouched.
- **`MedicationRefillScreen.kt`** and **`MorningCheckInScreen.kt`** also
  navigate to `Medication.route` — out of scope.
- **The chip itself**: removing the navigation branch does NOT remove the
  chip — it falls through to `viewModel.onToggleHabitCompletion(...)`, so
  the chip would start toggling the medication habit completion the way
  every other habit chip does. That is not a regression, but it is a
  behavior change worth flagging in the Phase 2 PR description.

#### Risk classification

- **GREEN.** Single-line removal in `TodayScreen.kt`. No tests reference
  the chip's medication-special-case branch. No schema, no sync impact.
- The chip-stays-but-toggles-instead behavior is the right default once
  the medication-specific UI is gone — it makes the chip indistinguishable
  from other habit chips, matching user expectations after the card is
  removed.

#### Recommendation

**PROCEED.** Drop the
`SelfCareRepository.MEDICATION_HABIT_NAME -> PrismTaskRoute.Medication.route`
arm of the `when` expression in `TodayScreen.kt`. Bundle into the same PR
as Item 1 (single coherent scope: "remove medication reminders from start
screen").

---

### Item 3 — `TodayBurnoutBadge` suggestion text *(deferred)*

The text "Turn on medication reminders so doses don't slip past you."
(`TodayBurnoutBadge.kt:150`) renders inside the `BurnoutDetailSheet` bottom
sheet, accessed via the burnout chip. The user explicitly excluded this
surface from scope (option 2 vs option 3 in the disambiguation). **DEFER.**

### Item 4 — Default-hide medication chip *(deferred)*

After Item 2 lands, the medication chip still appears in the Habits row by
default (`isMedicationEnabled` defaults to `true`) and behaves like any
other habit chip. If the intent is to hide the chip outright, that is a
default-flip + Settings-side change with broader blast radius (affects
the Habits tab, dashboard counts, and onboarding). The user did not ask
for this. **DEFER** — re-open if the user reports the chip still feels
like a "reminder."

### Item 5 — `checkInSummaryFlow` "medications" mention *(deferred)*

`TodayViewModel.kt:634` reads `medicationRefillRepository.observeAll()` to
add "medications" to the morning-check-in banner subtitle. This is a
phrasing helper for a banner, not a reminder UI. Out of scope; **DEFER.**

### Item 6 — `MedicationReminderScheduler` notifications *(deferred)*

Push-notification reminders for due doses live in
`notifications/MedicationReminderScheduler.kt`. They are not on the start
screen by definition; they fire as system notifications. Out of scope;
**DEFER.**

---

## Ranked improvement table (sorted by wall-clock-savings ÷ implementation-cost)

| # | Improvement | Files | Cost | Reach | Recommendation |
|---|-------------|-------|------|-------|----------------|
| 1 | Remove `MedicationCard` + bottom sheet from Daily Essentials | 8 main + 1 test | ~30 min | Today screen, all users | **PROCEED — Phase 2 PR** |
| 2 | Drop `Medication` route arm from Today's chip nav `when` | 1 main | ~2 min | Today screen, users with medication habit on | **PROCEED — bundle with #1** |
| 3 | Default-hide medication chip in Habits row | 1 pref + 1 VM | ~15 min | Today screen, default users | DEFERRED — not requested |
| 4 | Strip "medications" from check-in summary banner | 1 VM | ~5 min | Banner subtitle | DEFERRED — banner ≠ reminder |
| 5 | Update Burnout-badge "medication reminders" suggestion | 1 main | ~2 min | Burnout sheet | DEFERRED — not on start screen |

---

## Anti-pattern checklist (flag-only)

- **Don't delete `DailyEssentialSlotCompletionRepository` or its DAO/entity.**
  The materialized-slot pattern is shared with morning / bedtime / housework
  routines — only the medication call sites go.
- **Don't delete `MedicationRefillRepository` or `MedicationReminderScheduler`.**
  They are off-Today-screen.
- **Don't delete the `Medication` route registration in `NavGraph.kt` or
  `ModeRoutes.kt`.** `MedicationScreen` itself stays reachable from
  Settings, the Habits tab, the Refill screen, and the Morning Check-in.
- **Don't change `MEDICATION_HABIT_NAME` or the medication built-in habit
  identity.** Migration runners (`MedicationMigrationRunner`) and sync
  mappers depend on the name. The medication habit continues to exist; only
  its Today-screen navigation special-case goes.
- **Re-derive `DailyEssentialsUiState.isEmpty`** rather than leaving the
  `medication == null` predicate. Forgetting this means the empty-state
  hint never re-renders for users whose Daily Essentials are otherwise
  empty.
- **Skip-ci tokens in commit messages** (per
  `feedback_skip_ci_in_commit_message.md`) — none anywhere in this PR.

---

## Phase 2 plan

- **Branch**: `chore/remove-medication-reminders-start-screen` (single PR;
  Item 1 + Item 2 share scope).
- **Tests**:
  - `TodayViewModelTest`: existing tests assert the medication-disabled
    paths via `isMedicationEnabled()`. Sweep for assertions on
    `dailyEssentials.medication` / `onToggleMedicationSlot` /
    `onToggleMedicationDose` / `onMarkMedicationTaken` and delete or
    re-target. (Earlier grep showed zero test references to the removed
    handlers — verify on the PR diff.)
  - `DailyEssentialsUseCaseTest`: drop any medication-state assertions.
- **CI gate**: required Android unit tests + lint must stay green.
- **No `[skip ci]` in commit message.**
- **No CHANGELOG entry needed** unless explicitly user-facing — this is a
  UI removal of a feature already shipped, so add a short bullet under the
  Unreleased section.

---

## Phase 3 — Bundle summary (post-merge)

- **Phase 1 (audit doc)**: PR #890 — merged 2026-04-28T07:16:34Z as
  `3284a7eb`.
- **Phase 2 (implementation)**: PR #891 — merged 2026-04-28T07:53:53Z as
  `81ee5eeb`. Includes the `MedicationCard` removal, the Habits-chip
  route removal, deletion of `MedicationStatusUseCase`, `MedicationSlotGrouper`,
  and `DailyEssentialSlotCompletionRepository`, plus a CHANGELOG note
  under "Unreleased > Removed."

### Audit-vs-shipped delta

The Phase 1 anti-pattern list claimed
`DailyEssentialSlotCompletionRepository` was shared with morning / bedtime /
housework slots and should not be deleted. **That premise was wrong.**
On a fresh sweep before Phase 2, `slotCompletionRepository.toggleSlot`
turned out to have only two call sites in the codebase — both inside
the medication handlers being removed (`TodayViewModel.onToggleMedicationSlot`
and `onToggleMedicationDose`). Morning/bedtime/housework slots write
through `SelfCareDao` directly, not through this repository. The
repository therefore went dead with the medication card and was deleted
in Phase 2; the underlying DAO + Room table + sync mappers stay so the
sync layer keeps round-tripping historical
`daily_essential_slot_completions` rows.

The corresponding `DailyEssentialSlotCompletionDaoTest.virtualToMaterializedTransition`
case (which exercised the deleted repository) was dropped; the
`uniqueIndexPreventsDuplicateSlotPerDay` case stays so the schema
invariant the sync layer relies on remains pinned.

### Memory entry candidates

- **None added.** The wrong-premise on the repository's reach is a one-off
  miscount; the lesson ("verify caller count before recommending 'don't
  delete this'") is already captured by `feedback_audit_drive_by_migration_fixes.md`'s
  spirit (search before recommending). Adding another rule for it would
  duplicate.

### Schedule for next audit

- No follow-up scheduled. Items 3–6 (deferred surfaces — burnout-badge
  text, default-hide medication chip, check-in summary subtitle,
  notification scheduler) remain available to re-open if user feedback
  surfaces them.
