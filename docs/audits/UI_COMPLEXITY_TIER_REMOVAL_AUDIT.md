# UI Complexity Tier Removal — Phase 1 Audit

**Scope.** Remove the BASIC / STANDARD / POWER `UiComplexityTier` system in favor
of unconditionally exposing the full ("Power User") feature set. Everything
that is currently gated `if (uiTier.isAtLeast(STANDARD))` should become
unconditional; the tier picker, onboarding sheet, and selection chip should be
deleted; the three tier-specific test files should be deleted.

**Premise verification.** Confirmed against `main` (commit `c6f24916`).
`UiComplexityTier` is a real three-tier enum (`BASIC` / `STANDARD` / `POWER`)
introduced in `0f8bd5f7` ("feat: Add UI complexity tiers (Basic/Standard/Power)
with progressive disclosure"). Persisted in `UserPreferencesDataStore` only —
**no Firestore / `GenericPreferenceSyncService` registration**, so no backend
schema impact. Independent of `NdPreferences` (which is three independent
boolean modes, not a level system) and independent of `ProStatusPreferences`
(Free / Pro paywall).

ND Brain Mode is **not** in scope: it is per-feature toggles, not a level.
Pro tier is **not** in scope: it is paywall, not progressive disclosure.

Surface-area summary (counted from `grep -rn "isAtLeast\|UiComplexityTier"
app/src`): **181 references** across `app/src/main` + `app/src/test` +
`app/src/androidTest`. Per-screen gate counts:

| File | `STANDARD`+ gates | `== BASIC` checks |
|---|---|---|
| `ui/screens/settings/SettingsScreen.kt` | 6 | 0 |
| `ui/screens/addedittask/AddEditTaskScreen.kt` | 7 | 0 |
| `ui/screens/today/TodayScreen.kt` | 2 | 0 |
| `ui/screens/tasklist/TaskListScreen.kt` | 1 | 1 |
| **Total UI gates** | **16** | **1** |

Plus a 17th "negative" gate in `AddEditTaskScreen.kt:436` (`if
(!uiTier.isAtLeast(STANDARD))`) that renders a "More Options Available in
Standard Mode" hint — that whole branch is dead under POWER and should be
removed, not unwrapped.

---

## Items

### 1. `UiComplexityTier` domain model + `isAtLeast` extension (RED — delete)

`app/src/main/java/com/averycorp/prismtask/domain/model/UiComplexityTier.kt`
(38 lines): enum with three entries + companion `fromName()` + free function
`UiComplexityTier.isAtLeast(minimum)`. Once gates are removed nothing references
this file. **Delete entire file.**

### 2. `UserPreferencesDataStore` tier flow + setter + onboarding flag (RED — delete)

`app/src/main/java/com/averycorp/prismtask/data/preferences/UserPreferencesDataStore.kt`
- L13 import.
- L214: `KEY_UI_TIER = stringPreferencesKey("ui_complexity_tier")`.
- L215: `KEY_TIER_ONBOARDING_SHOWN = booleanPreferencesKey("tier_onboarding_shown")`.
- L246–248: `val uiComplexityTier: Flow<UiComplexityTier>`.
- L251–253: `val tierOnboardingShown: Flow<Boolean>`.
- L499–501: `suspend fun setUiComplexityTier(tier)`.
- L504–506: `suspend fun markTierOnboardingShown()`.

Action: delete all six. Leave the persisted DataStore keys themselves in place
on disk for existing installs — Android `Preferences` storage tolerates orphan
keys, and we do not need a migration to clear them.

### 3. `TierOnboardingSheet` + NavGraph hookup (RED — delete)

- `app/src/main/java/com/averycorp/prismtask/ui/screens/onboarding/TierOnboardingSheet.kt`
  (99 lines): full bottom-sheet composable — delete entire file.
- `app/src/main/java/com/averycorp/prismtask/ui/navigation/NavGraph.kt:522–539`:
  the conditional `TierOnboardingSheet` block inside `MainTabs` plus the
  `tierSheetDismissed` local state — delete the whole block.
- `app/src/main/java/com/averycorp/prismtask/ui/screens/settings/SettingsViewModel.kt`
  L177–195: `uiComplexityTier` StateFlow, `setUiComplexityTier()`,
  `tierOnboardingShown` StateFlow, `markTierOnboardingShown()` — delete.

### 4. `UiComplexitySection` settings section (RED — delete)

`app/src/main/java/com/averycorp/prismtask/ui/screens/settings/sections/UiComplexitySection.kt`
(180 lines): the "Experience Level" section + downgrade confirmation dialog +
Help-Me-Choose advisory blocks. **Delete entire file.**

### 5. `UiTierCard` shared component (RED — delete)

`app/src/main/java/com/averycorp/prismtask/ui/components/UiTierCard.kt`: tier
selection card used by both `UiComplexitySection` and `TierOnboardingSheet`.
Once both deletions land, no callers remain. **Delete entire file.**

### 6. `SettingsScreen` — drop AssistChip + 6 gates (RED — ungate)

`app/src/main/java/com/averycorp/prismtask/ui/screens/settings/SettingsScreen.kt`
- L42–43: drop `UiComplexityTier` + `isAtLeast` imports.
- L85: drop `val uiTier by viewModel.uiComplexityTier.collectAsStateWithLifecycle()`.
- L150–175: drop the `actions = { AssistChip(...) }` tier pill in the top app bar.
- L198–207: drop the `UiComplexitySection(...)` LazyColumn item entirely (this
  is the "Experience Level — always first" item).
- L234, L244, L259, L275, L302, L332: the 6 `if (uiTier.isAtLeast(STANDARD)) {
  ... }` blocks. **All bodies stay; the wrapping `if` goes.** These guard:
  Advanced Appearance, Layout & Navigation, Global Defaults, Life Modes +
  Medication Slots, Productivity group (AI Features / Brain Mode / Wellbeing),
  Integrations group (Calendar). All become unconditional.

### 7. `AddEditTaskScreen` — drop 7 gates + the BASIC hint (RED — ungate)

`app/src/main/java/com/averycorp/prismtask/ui/screens/addedittask/AddEditTaskScreen.kt`
- L88–89: drop `UiComplexityTier` + `isAtLeast` imports.
- L188: drop `val uiTier by viewModel.uiTier.collectAsStateWithLifecycle()`.
- L303 (Due Time), L322 (Reminder), L364 (Recurrence), L387 (Project),
  L397 (Tags), L454 (Attachments — keep the `viewModel.isEditMode` check):
  unwrap each `if (uiTier.isAtLeast(STANDARD)) { ... }` to keep its body.
- L436–451: delete the entire `if (!uiTier.isAtLeast(STANDARD)) { ... "More
  Options Available in Standard Mode" }` block. Dead under POWER.

### 8. `TodayScreen` — drop 2 gates (RED — ungate)

`app/src/main/java/com/averycorp/prismtask/ui/screens/today/TodayScreen.kt`
- L54: drop `isAtLeast` import.
- L108: drop `val uiTier`.
- L347 (balance bar visibility — note: this is `&& uiTier.isAtLeast(...)`,
  drop the conjunct): `workLifeBalancePrefs.showBalanceBar &&
  uiTier.isAtLeast(STANDARD)` → `workLifeBalancePrefs.showBalanceBar`.
- L412 (quick action chips — Briefing/Planner/Eisenhower): unwrap, body
  becomes unconditional.

### 9. `TaskListScreen` — drop filter gate + BASIC sort filter (RED — ungate)

`app/src/main/java/com/averycorp/prismtask/ui/screens/tasklist/TaskListScreen.kt`
- L77: drop `isAtLeast` import.
- L130: drop `val uiTier`.
- L452: unwrap the `if (uiTier.isAtLeast(STANDARD))` around the Filter
  IconButton — filter button always visible.
- L589–597: replace the `visibleSortOptions` filter block with
  `val visibleSortOptions = SortOption.entries`. The `basicSortOptions`
  local set (`DUE_DATE`, `PRIORITY`, `CREATED`) goes away.

### 10. ViewModels — drop `uiTier` StateFlow (RED — delete)

Four viewmodels expose `uiTier` for screen consumption:

- `ui/screens/settings/SettingsViewModel.kt` L177–186 (covered by Item 3).
- `ui/screens/today/TodayViewModel.kt` L93–98.
- `ui/screens/tasklist/TaskListViewModel.kt` L89–94.
- `ui/screens/addedittask/AddEditTaskViewModel.kt` L79–84.

After Item 6–9 land, all four are unread. **Delete each `uiTier` StateFlow +
its `userPreferencesDataStore.uiComplexityTier` reference.**

### 11. DataExporter / DataImporter (YELLOW — strip from new exports, keep importer no-op)

Two backup-format fields:

- `data/export/DataExporter.kt:437–438`: `userPrefs.addProperty(
  "uiComplexityTier", ...)` and `addProperty("tierOnboardingShown", ...)`.
  Drop both — new exports stop emitting them.
- `data/export/DataImporter.kt:1234–1243`: `importUiTierPrefs(userPrefs)`
  function. **Delete the function and its caller.** Older backup files that
  still carry these fields will be silently ignored, which is the correct
  behavior — Gson does not throw on unknown JSON keys at the consumer level
  here. Verified by reading the surrounding `runCatching` pattern: keys are
  fetched defensively, never required.
- `data/export/DataImporter.kt:49`: drop the `UiComplexityTier` import.

Risk: nil. There is no JSON schema versioning that would reject extra
fields, and the importer already tolerates missing fields throughout (see
the `?.takeIf { !it.isJsonNull }` pattern repeated across the file).

### 12. Tests — delete 3 unit test files (RED — delete)

- `app/src/test/java/com/averycorp/prismtask/domain/model/UiComplexityTierTest.kt`
- `app/src/test/java/com/averycorp/prismtask/data/preferences/UiComplexityTierDataStoreTest.kt`
- `app/src/test/java/com/averycorp/prismtask/ui/UiComplexityTierRegressionTest.kt`

All three are about the enum + DataStore round-trip + ordinal comparison —
they have no value once the type is gone. Delete entirely.

`app/src/androidTest/java/com/averycorp/prismtask/GenericPreferenceSyncServiceEmulatorTest.kt`
showed up in the initial grep but contains zero references to
`uiComplexityTier` (verified: `grep -n uiComplexityTier ...` returned empty).
The match was on a substring of an unrelated preference name. **Untouched.**

### 13. Backend sync (GREEN — no work)

Verified `GenericPreferenceSyncService` and the preference sync spec
(`data/remote/sync/PreferenceSyncSpec.kt`, `di/PreferenceSyncModule.kt`) do
not register `KEY_UI_TIER` or `KEY_TIER_ONBOARDING_SHOWN`. There is no
Firestore field, no remote schema, and no cross-device migration concern.

### 14. Documentation (GREEN — no work)

`grep -rn UiComplexityTier docs/` returned no hits. CLAUDE.md does not
mention UI Complexity Tier anywhere (it does mention "UI Complexity" once in
the v1.4 ND-modes paragraph, but that line is describing `NdFeatureGate +
NdPreferences` — the unrelated ND system). No CLAUDE.md edit needed.

### 15. Existing-install behavior (DEFERRED — accept orphan keys)

Existing installs that have `ui_complexity_tier = "BASIC"` or `"STANDARD"`
written to disk will keep that key indefinitely. Since nothing reads the key
after this lands, this is harmless. Writing a one-shot DataStore migration to
clear the keys is not worth the complexity for ~2 dead string entries. If a
user later exports their data, those keys simply won't appear in the new
JSON shape. **No action.**

---

## Ranked improvement table (savings ÷ cost)

Single-PR removal — there isn't a meaningful ordering since the items are
mutually dependent (you can't ungate a screen while keeping the enum;
you can't keep the section while removing the type). Bundled into one PR.

| # | Item | Cost | Savings | Bundle |
|---|---|---|---|---|
| 1 | Delete `UiComplexityTier.kt` + extension | XS | unblocks deletion of 5 files | A |
| 2 | Drop tier flows from `UserPreferencesDataStore` | XS | -2 keys, -2 flows, -2 setters | A |
| 3 | Delete `TierOnboardingSheet` + NavGraph hook | XS | -99 LOC + cleaner first launch | A |
| 4 | Delete `UiComplexitySection` | XS | -180 LOC + drops downgrade dialog | A |
| 5 | Delete `UiTierCard` | XS | -shared component | A |
| 6 | Ungate `SettingsScreen` (6 sites + chip + section) | S | core UX simplification | A |
| 7 | Ungate `AddEditTaskScreen` (7 sites + hint) | S | richer task editor by default | A |
| 8 | Ungate `TodayScreen` (2 sites) | XS | balance bar + chips always on | A |
| 9 | Ungate `TaskListScreen` (1 + sort filter) | XS | full filter/sort always available | A |
| 10 | Drop `uiTier` from 4 viewmodels | XS | -4 unused StateFlows | A |
| 11 | Strip exporter/importer tier fields | XS | smaller backup payload | A |
| 12 | Delete 3 tier unit-test files | XS | -tests for deleted code | A |

**Single bundle (A):** one PR titled `chore: remove UI complexity tier
system, expose Power User experience by default`. Branch: `chore/remove-ui-tiers`.

The fan-out bundling rule (audit-first.md) prefers N small PRs **unless**
they're a single coherent scope; here every change is "delete the tier
system," and splitting risks intermediate broken-build commits (e.g. a PR
that deletes the enum but keeps `isAtLeast` callers cannot compile). One PR.

---

## Anti-patterns flagged (not necessarily fixed here)

- **`UiComplexitySection` shows a downgrade-confirmation dialog
  ("Some settings you've configured may be hidden until you switch back")**
  — this UX exists *because* the tier hides settings, not because settings
  are removed. With the tier gone, no settings hide. The dialog goes away
  for free. Note for future: progressive-disclosure systems generally need
  this kind of warning, which is one signal that the abstraction adds UX
  cost beyond the LOC count.
- **The "More Options Available in Standard Mode" hint in `AddEditTaskScreen`
  L436** is a textbook "we're hiding things from you, here's a label
  about it" anti-pattern. Removing the tier removes the need for the hint.
- **The Settings header `AssistChip` showing the current tier name** was
  a constant visual reminder of the tier system; removing it cleans up the
  Settings header significantly.

---

## Stop-and-report check

No premise was wrong. `UiComplexityTier` exists, the gate sites exist as
documented, and there is no backend coupling that would block deletion.
Proceeding to Phase 2 in the same session per audit-first protocol.

---

## Phase 3 — Bundle summary

**Shipped as one PR.** Branch `chore/remove-ui-tiers`, commit `cdebf2fb`,
PR [#952](https://github.com/averycorp/prismTask/pull/952), auto-merge
(squash) armed.

**Diffstat:** 21 files changed, +452 / −1,262 (net **−810 LOC**). Removed
5 source files + 3 unit test files; modified 11 source files; added 1
audit doc.

**Verification:**
- `./gradlew compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew testDebugUnitTest` — BUILD SUCCESSFUL.
- Manual smoke deferred to post-merge.

**Re-baselined wall-clock estimate:** the bundled-PR shape was correct.
Per-item cost was XS–S; the real cost driver was multi-line-edit /
surrogate-pair friction inside `SettingsScreen.kt`, which a Python
script-based unwrap of the gate blocks resolved. Future "delete a
progressive-disclosure system" work should default to a script that
unwraps `if (gate) { body }` blocks instead of hand-editing each `if`.

**Memory candidates considered, none promoted:** no surprising or
non-obvious facts emerged that aren't already discoverable via `git log`
or by reading the code directly.

**Schedule for next audit:** none. The only follow-up is post-merge
manual UX smoke; not worth a routine audit slot.

## Phase 3 — Anti-pattern reminders

- **Multi-line `if` gates are easy to write and hard to remove.** When a
  feature gate wraps 5–10 lines of UI builder calls, the call site works
  fine but every refactor pays a brace-counting tax. If a future
  progressive-disclosure axis is reintroduced, prefer composition (e.g.
  `tieredItem(min: Tier) { ... }` LazyListScope helper) over scattered
  `if (tier.isAtLeast(X)) { ... }` blocks at each call site.
- **Tier persistence pattern was inconsistent.** UI tier wrote a
  `ui_complexity_tier` string key directly on `UserPreferencesDataStore`.
  Most other feature gates live in dedicated `*Preferences.kt` data
  classes. Future tier-like features should land in the `*Preferences`
  pattern, not directly on `UserPreferencesDataStore`.
