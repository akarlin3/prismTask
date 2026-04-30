# Language Leisure Category Audit

## Scope

Add a "Language" leisure category as a default option in onboarding, with
Italian / French / Spanish / German / Chinese as built-in selectable options.
Surface it alongside the existing Music and Flex slots in the template picker
so a new user can opt into a language practice rotation during setup.

## Premise

The user-facing intent maps cleanly onto the existing leisure architecture:

- "Leisure category" = `LeisureSlotId` enum value (today: `MUSIC`, `FLEX`).
- "Built-in options" = hard-coded `LeisureOption` lists
  (today: `DEFAULT_INSTRUMENTS`, `DEFAULT_FLEX_OPTIONS`).
- "Onboarding default" = `TemplatePickerContent` row + the
  `OnboardingViewModel.applyTemplateSelections` flow that converts the user's
  picks into `hiddenBuiltInIds` writes.

So "add Language with 5 languages" = add a third enum value with its own
default-options list and plumb it through the ~12 sites that currently
`when`-match on the 2-value enum.

## Findings

### Architecture: hybrid built-in + custom-section model

Built-in slots (MUSIC, FLEX) have:

- Dedicated `LeisureSlotId` enum entries
  (`app/src/main/java/com/averycorp/prismtask/data/preferences/LeisurePreferences.kt:23`).
- Dedicated columns in `LeisureLogEntity` (`music_pick`/`music_done`,
  `flex_pick`/`flex_done` —
  `app/src/main/java/com/averycorp/prismtask/data/local/entity/LeisureLogEntity.kt:22-29`).
- Dedicated repository methods
  (`setMusicPick`, `setFlexPick`, `toggleMusicDone`, … —
  `app/src/main/java/com/averycorp/prismtask/data/repository/LeisureRepository.kt:72-154`).
- Dedicated `defaultsFor(slot)` returning a hard-coded `LeisureOption` list
  (`app/src/main/java/com/averycorp/prismtask/ui/screens/leisure/LeisureViewModel.kt:172-191`).
- Dedicated DataStore preference keys per slot via `keysFor(slot)`
  (`LeisurePreferences.kt:431-452`).

User-added "custom sections" (added v1.6+) have:

- A JSON-blob `custom_sections` DataStore key.
- A JSON column `leisure_logs.custom_sections_state` for per-day pick/done.
- Generic `setCustomSectionPick`, `toggleCustomSectionDone` methods.
- No built-in option list — user adds their own activities.

The user's "Language with built-in options" maps to the **built-in slot
pattern**, not the custom-section pattern, because the requirement is for
hardcoded defaults that ship with the app.

### Touch sites for adding LANGUAGE as a third built-in slot

| File | Change |
|------|--------|
| `LeisureLogEntity.kt` | Add `language_pick`, `language_done` columns |
| `Migrations.kt` | New `MIGRATION_66_67` (additive `ALTER TABLE`); bump `CURRENT_DB_VERSION` to 67 |
| `SyncMapper.kt:551-580` | Add `languagePick` / `languageDone` to to/from-map |
| `LeisureRepository.kt:72-154` | Add `setLanguagePick`/`toggleLanguageDone`/`clearLanguagePick`; extend `resetToday`, `getDailyLeisureProgress`, `syncHabitCompletion` |
| `LeisurePreferences.kt:23` | Add `LANGUAGE` to enum |
| `LeisurePreferences.kt:36-57` | Add `LANGUAGE` branch to `defaultFor()` |
| `LeisurePreferences.kt:91-119` | Add `LANGUAGE_*_KEY` preference constants |
| `LeisurePreferences.kt:431-452` | Add `LANGUAGE` branch to `keysFor()` |
| `LeisureViewModel.kt:56-57` | Add `languageSlot` StateFlow |
| `LeisureViewModel.kt:94-123` | Add `LANGUAGE` branches to pickActivity/toggleDone/clearPick |
| `LeisureViewModel.kt:172-191` | Add `DEFAULT_LANGUAGE_OPTIONS`; extend `defaultsFor()` |
| `LeisureScreen.kt:57-62, 178-185` | Include `languageState` in slots; update label `when` |
| `LeisureSettingsViewModel.kt:37-38` | Add `languageState` flow |
| `LeisureSettingsScreen.kt:88-130, 466-475` | Render Language SlotEditor; update label `when` blocks |
| `TemplatePickerState.kt:16-91` | Add `languageIds: Set<String>` + `withLanguageToggled` |
| `TemplatePickerContent.kt:55-72` | Add a third Language `LeisureSectionCard` |
| `OnboardingViewModel.kt:212-222` | Add `applyLeisureSelection` call for `LANGUAGE` |
| `DailyEssentialsUseCase.kt:30, 75-76, 113-114, 160-205` | Add `LANGUAGE` to `LeisureKind`; thread `languageConfig` into `combineDailyEssentials`; expose `languageLeisure` card state |
| `TemplateBrowserViewModel.kt:44-45` | Add LANGUAGE call to mirror onboarding's apply flow |
| Tests | New `Migration66To67Test`; extend `LeisurePreferencesTest`, `SyncMapperTier2Test`, `TemplateSelectionsTest` |

`LifeModesScreen` exposes a single feature-level `leisureEnabled` toggle for
the whole subsystem, so it does not need per-slot work for this audit.

### Risk factors

- **Sync compat** — Firestore is schema-less (map-backed), so older clients
  reading a `leisure_logs` doc with new `languagePick` / `languageDone` keys
  silently ignore them. Newer clients reading a doc that predates the change
  fall back to `null`/`false` via the existing `as? String` / `as? Boolean`
  patterns. No compat break either direction.
- **DailyEssentialsUseCase combine** — `combine(...)` is wrapped in an
  `Array<Any?>` reader that hard-codes index positions
  (`args[7]` / `args[8]` for music/flex configs). Adding LANGUAGE bumps the
  arg list to 10 — every index in `combineDailyEssentials` shifts by
  whatever insertion order we pick. We'll mitigate by adding `language` as
  the last arg, so existing indices stay stable and only the new entry is
  appended.
- **`when` exhaustiveness** — Kotlin compiles-error any unhandled
  `LeisureSlotId.LANGUAGE` branch, so the compiler enforces that every
  call site is updated. This is a feature, not a bug — every miss surfaces
  at build time, not runtime.
- **Migration test pattern** — `exportSchema = false`, so existing tests
  hand-roll the v51 / v52 / v53 schemas via `SupportSQLiteOpenHelper`
  rather than using Room's `MigrationTestHelper`. The `Migration66_67`
  case is purely additive (two new nullable / boolean-default columns),
  so a single round-trip test (insert v66 row → migrate → read columns)
  is enough.
- **Onboarding test sparseness** — `OnboardingViewModelTest` currently only
  covers `SignInState` shape. Adding direct `applyTemplateSelections`
  coverage would be valuable long-term; for this audit we'll cover the
  language path via `LeisurePreferencesTest` (round-trip set/get of
  hidden-builtin state for the new slot) plus `TemplateSelectionsTest`
  (toggle semantics).

### Anti-patterns flagged but not fixed

- **`combine(...)` over `Array<Any?>` with positional index access** in
  `DailyEssentialsUseCase` is fragile against extension. A typed combine
  wrapper would prevent index drift. **Defer** — out of scope for this
  feature; the per-slot append pattern keeps it manageable until a future
  cleanup PR.
- **Repository methods that duplicate per-slot** (`setMusicPick` /
  `setFlexPick` / `setLanguagePick`) — a parametric `setBuiltInPick(slot,
  id)` form would be cleaner and would not require expanding when LANGUAGE
  is added. **Defer** — out of scope; we mirror the existing pattern to
  keep PR scope small.

## Risk classification

**GREEN** — single coherent feature addition. No architecture change. The
schema change is purely additive (two new nullable/boolean-default columns
on `leisure_logs`). Sync mapper updates preserve cross-platform compat by
using `as?` reads and writing `null` when the field is unset. The only
runtime risk is the `combineDailyEssentials` index drift, which we control
by appending the new arg last.

## Recommendation

**PROCEED**. Single bundled PR — this is one cohesive feature scope (one new
leisure slot + its plumbing through onboarding, settings, and the daily
essentials card). Estimated ~14 source files + 3 test files.

## Improvements ranked by wall-clock-savings ÷ implementation-cost

Single improvement; not a fan-out audit.

| Improvement | Implementation cost | Wall-clock savings (qualitative) |
|-------------|---------------------|----------------------------------|
| Add LANGUAGE leisure slot with 5 default languages, plumbed through onboarding template picker, leisure screen, leisure settings, and daily essentials card | Medium (~14 files + 3 tests, additive Room migration) | High — unblocks language-practice users opting in at first launch with no manual section-creation friction |
