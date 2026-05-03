# Leisure: "Adding an option to a custom leisure category deletes the
# category" — audit

**Scope.** User-reported bug: in the Leisure customization UI, adding an
activity to a user-created custom section makes the section disappear.
Goal: locate the regression, classify risk, and ship a fix or — if the
data layer is verifiably correct — surface the bug claim to the user
with concrete next-step repro questions and ship a regression-test
hardening PR so the audited path stays green going forward.

**Branch / worktree.** `fix/leisure-add-activity-keeps-section`,
worktree at `C:/Projects/prismTask-leisure-add-activity-fix`.

---

## Phase 1

### Item 1 — `LeisurePreferences.addCustomSectionActivity` (GREEN)

**Finding.** The data-layer write path is correct on inspection AND
under test:

```kotlin
suspend fun addCustomSectionActivity(sectionId: String, label: String, icon: String) {
    val trimmedLabel = label.trim()
    val trimmedIcon = icon.trim()
    if (trimmedLabel.isEmpty() || trimmedIcon.isEmpty()) return
    context.leisureDataStore.edit { prefs ->
        val current = readCustomSections(prefs)
        val updated = current.map { section ->
            if (section.id != sectionId) return@map section
            val activity = CustomLeisureActivity(
                id = "custom_${sectionId}_${System.currentTimeMillis()}",
                label = trimmedLabel,
                icon = trimmedIcon
            )
            section.copy(customActivities = section.customActivities + activity)
        }
        prefs[CUSTOM_SECTIONS_KEY] = gson.toJson(updated)
    }
}
```

Logic preserves every section in `current` (the early-return inside
the `map` returns the unchanged section) and only appends a new
activity to the matching section's `customActivities`. The list is
serialized back via `gson.toJson(updated)` — no list truncation, no
section omission.

A Robolectric repro test was added in
`app/src/test/java/com/averycorp/prismtask/data/preferences/LeisurePreferencesTest.kt`:

```kotlin
@Test
fun addCustomSectionActivity_preservesSection_andAppendsActivity() = runTest {
    val sectionId = prefs.addCustomSection("Reading", "📖")
    prefs.addCustomSectionActivity(sectionId, "Books", "📕")
    val sections = prefs.getCustomSections().first()
    assertEquals(1, sections.size)
    val section = sections.single()
    assertEquals(sectionId, section.id)
    assertEquals("Reading", section.label)
    assertEquals(1, section.customActivities.size)
    assertEquals("Books", section.customActivities.single().label)
}
```

**Result:** `BUILD SUCCESSFUL` — the section is preserved, the
activity is appended.

**Verdict.** GREEN. The data-layer premise (that the round-trip
mutation deletes the section) is **wrong** at this layer.

### Item 2 — `LeisureSettingsScreen` UI flow (GREEN, code-level)

**Finding.** The Settings-screen Add-Activity flow is straightforward
and correct on inspection:

```kotlin
addActivityForCustomSection?.let { sectionId ->
    val section = customSections.firstOrNull { it.id == sectionId }
    AddActivityDialog(
        category = section?.label ?: "Section",
        onDismiss = { addActivityForCustomSection = null },
        onConfirm = { label, icon ->
            viewModel.addCustomSectionActivity(sectionId, label, icon)
            addActivityForCustomSection = null
        }
    )
}
```

`addActivityForCustomSection` and `removeCustomSectionConfirm` are
**independent** state vars — clicking "Add Activity" cannot route
through the "Remove Section" branch by composable-state aliasing. The
"Remove Section" button additionally requires a confirmation dialog
("Remove?" / "Cancel"), so accidental tap-target overlap on the
adjacent button is not a silent-deletion path.

The same dialog is also used from the public-facing `LeisureScreen`
(`onRequestAdd = { showAddDialog = state.key }`) and routes through
`LeisureViewModel.addActivity` → the same
`leisurePreferences.addCustomSectionActivity` covered in Item 1.

**Verdict.** GREEN at code-review level. The UI cannot be repro'd
from this audit (no device session attached) — see Item 4 for the
device-side follow-up.

### Item 3 — Sync layer (`GenericPreferenceSyncService`) (YELLOW)

**Finding.** `leisure_prefs` is registered for cross-device sync via
`PreferenceSyncModule.kt`:

```kotlin
PreferenceSyncSpec("leisure_prefs", context.leisureDataStore),
```

`GenericPreferenceSyncService.applyRemote` skips remote payloads whose
`__pref_device_id` matches the local `SyncDeviceIdProvider` UUID
(self-echo guard). That UUID is persisted in `sync_device_prefs`,
which is **not** itself synced — so it's fresh on every install /
data-clear.

Two scenarios where a remote-pull could plausibly *appear* to delete
a freshly-added section, neither of which Item 1's repro test can
catch:

1. **Reinstall race.** User clears data on device A; new UUID issued.
   User re-creates section X locally, then *adds an activity*. If a
   pre-clear Firestore payload still exists for this account that
   does NOT contain X, and the sync runs between the
   `addCustomSection` write and the `addCustomSectionActivity` write,
   `applyRemote` overwrites `custom_sections` with the stale remote
   value — X disappears, including the activity the user just added.
   The user sees "adding deleted the category."

2. **Multi-device race.** User on device A creates section X, syncs.
   On device B, `addCustomSection` writes a NEW section Y (different
   id, different timestamp). When B's push lands at A, the merged
   payload is "[X, Y]" only if device-side merge is implemented.
   Looking at `PreferenceSyncSerialization.applyRemote` (typed-key
   overwrite, no merge), the second device's payload **replaces**
   A's `custom_sections` whole-cloth.

These shapes match the user's symptom ("adding deletes the
category"). The repro test in Item 1 cannot reach them — it runs in
an isolated Robolectric context with no Firestore.

**Verdict.** YELLOW. The data layer is verifiably correct in
isolation, but the sync layer's typed-key whole-cloth overwrite is a
*plausible* mechanism for the user's report. Cannot be confirmed
without (a) the user's device count + sync timing or (b) an
instrumented multi-device test (covered by `Fuzz06`-style sync
integration tests for medications, but no equivalent exists for the
leisure preference subset — see Item 4).

### Item 4 — Repro details needed from the user (DEFERRED)

**Finding.** The bug claim cannot be reduced further from
code-inspection alone. The data-layer test in Item 1 passes; the
UI-layer code in Item 2 is straightforward; the sync-layer hypothesis
in Item 3 is plausible but unconfirmed.

The audit-first workflow's "STOP-and-report on wrong premises" rule
applies here: the data-layer premise was wrong (the round-trip
preserves the section). Before guessing further, surface the Phase 4
handoff to the user with three concrete questions:

1. **Device count.** One device or multiple? Does the bug repro on a
   single device with no sync (sign out / local-only mode)?
2. **Sync timing.** Did the user recently reinstall, sign in/out, or
   clear app data?
3. **Where in the UI?** The Leisure customization screen
   (`LeisureSettingsScreen` "Add Activity" button per section) or the
   Leisure Mode screen (`LeisureScreen` "+" affordance on a section)?

**Verdict.** DEFERRED on a code change for the bug itself —
PROCEEDING on the regression test (Item 1's hardening PR) so the
audited green-path stays green even as future PRs touch the file.

---

## Ranked improvements

| # | Improvement | Wall-clock | Risk | Ratio |
|---|-------------|-----------|------|-------|
| 1 | Ship Item 1's regression test as `fix/leisure-add-activity-keeps-section` | 5 min | low | high |
| 2 | Surface Phase 4 handoff with three repro questions | 2 min | none | high |
| 3 | (Deferred) Sync-layer `Fuzz`-style integration test for `leisure_prefs` round-trip | ~2 hr | medium | medium |

## Anti-patterns surfaced

- **`gson.fromJson` without `runCatching` + sanitization on a synced
  preference key**: PR #867 + #870 already established the
  `runCatching` + `.sanitized()` pattern for `CustomLeisureSection`.
  The audit re-confirms it's load-bearing; any future synced-prefs
  type should follow the same shape.

---

## Phase 2

PR shipped:

- **#918** — `fix(leisure): regression test — addCustomSectionActivity preserves section`
  Adds a Robolectric round-trip test verifying the user-reported
  symptom does not occur at the data layer. No production code
  change. Hardening only. Squash-merged at 2026-04-28T19:55:40Z
  (commit `04a2c0e0`).

The sync-layer integration test (Item 3) is **deferred** pending the
user's Phase 4 answers — if the bug is single-device, the integration
test is wrong scope; if it's multi-device, it becomes the right next
PR.

## Phase 3 — Bundle summary

**Shipped (1 PR).**

| PR | Scope | Impact |
|----|-------|--------|
| [#918](https://github.com/Akarlin3/PrismTask/pull/918) | Robolectric regression test for `addCustomSectionActivity` round-trip | Locks the data-layer green-path; future PRs touching `LeisurePreferences` get an early signal if the round-trip ever truncates a section. |

**Deferred.**

- **Sync-layer integration test (Item 3 / Improvement #3).** Scope
  depends on the user's Phase 4 answers. If the bug is single-device
  / no-sync, the integration test is wrong scope and the audit closes
  GREEN with no further work. If the bug is multi-device, the next PR
  is a `Fuzz`-style integration test for `leisure_prefs` round-trip
  through `GenericPreferenceSyncService` — the `Fuzz06` shape is the
  template, but only after Fuzz06 itself stabilizes (it's currently
  quarantined per #914 — same generic-prefs LWW interleave family).

**Re-baselined wall-clock estimate.** Phase 1 + the regression-test PR
shipped in ~30 min wall-clock end-to-end (audit-doc + test + commit +
push + auto-merge SQUASH). The deferred sync integration test would
add ~2h if the user's repro answers point at the multi-device path.

**Memory candidates.** None worth saving. The
`runCatching` + `.sanitized()` pattern for synced JSON-encoded
preferences is already covered by past PRs (#867, #870) and is
re-confirmed here, not newly surfaced. The "STOP-and-report on wrong
premises" lesson is already in the audit-first skill.

**Schedule for next audit.** Driven by user's Phase 4 answer — see
the handoff block below. No standing follow-up audit is scheduled.

## Phase 4 — Claude Chat handoff

(Emitted at the end of the run.)

---

## Phase 1 — Batch 3 (single-device persistent repro re-audit, branch `claude/audit-leisure-section-loss-DIc4T`)

**Trigger.** Operator follow-on May 3 2026. Bug re-reproduced with new
diagnostic facts: **single device**, no recent sign-out / reinstall /
clear-data, repros from BOTH the customization screen and the Leisure
Mode screen "+" affordance, and **persists across force-stop +
relaunch** (DataStore-level, not in-memory drift).

**Premise framing.** PR #1080 is **open, not yet merged** on
`claude/fix-leisure-category-bug-auK1O` (commit `ec3553e`,
`refs/pull/1080/head`). Its defensive guard adds
`if (current.none { it.id == id }) return@edit` to
`addCustomSectionActivity`, `removeCustomSectionActivity`, and
`updateCustomSection`. Batch 3 reasons about the bug *as if PR #1080
were merged* — the guard is a strict no-op-skip so its presence cannot
itself delete a section, but it changes which downstream hypotheses
are still live.

### A0 — Recon-first quad sweep (memory #18 + sibling-primitives axis)

| Axis | Result |
|------|--------|
| (a) drive-by since PR #1080 | No newer leisure fix shipped. PR #1080 is open, not merged. Highest merged PR is #1077. |
| (b) parked branches | Only `claude/fix-leisure-category-bug-auK1O` (PR #1080) and the audit branch itself. |
| (c) shape-grep `custom_sections` writes | Six writers, all in `LeisurePreferences.kt`. No `DataExporter`/`DataImporter` touch (they only handle `customMusicActivities` / `customFlexActivities`, not the section list). No backup/restore touch. |
| (d) both-screen entry paths | `LeisureScreen` "+" → `LeisureViewModel.addActivity` → `leisurePreferences.addCustomSectionActivity`. `LeisureSettingsScreen` "Add Activity" → `LeisureSettingsViewModel.addCustomSectionActivity` → same mutator. **Identical write path.** |
| (e) sibling primitives | `grep -rln "fun sanitized" data/preferences/` returns only `LeisurePreferences.kt`. No reusable "stale-read-then-write" anti-pattern fix elsewhere to mine. |

### Item 5' — Enumerate every `custom_sections` write path (GREEN, complete)

Exhaustive list:

1. `LeisurePreferences.addCustomSection` (creates)
2. `LeisurePreferences.removeCustomSection` (deletes — explicit user "Remove Section" + confirmation)
3. `LeisurePreferences.updateCustomSection` (PR #1080 adds defensive guard)
4. `LeisurePreferences.addCustomSectionActivity` (PR #1080 adds defensive guard)
5. `LeisurePreferences.removeCustomSectionActivity` (PR #1080 adds defensive guard)
6. `LeisurePreferences.clearAll` — only called from `SettingsViewModel:1489` under `options.preferencesAndSettings` reset (explicit user action — would also wipe themePreferences/dashboardPreferences/etc., very visible)
7. `GenericPreferenceSyncService.applyRemote` whole-cloth overwrite — **ruled out by single-device repro**
8. `AccountDeletionService.kt:311` — wipes `leisure_prefs` file as part of account-delete sequence (explicit destructive user action)

**No third-party writer found.** Both UI screens converge on the same five mutators. Path #6/#7/#8 are explicit user actions inconsistent with the symptom.

### Item 6' — `sanitized()` over-aggressive hypothesis (RED, refuted by code analysis)

`CustomLeisureSection.sanitized()` (LeisurePreferences.kt:323) drops a
section **only when `id == null`**. label/emoji fall back to safe
defaults (`"Section"` / `"✨"`); `customActivities = emptyList()` is
preserved (no "section needs ≥1 activity" filter); `durationMinutes` /
`gridColumns` are coerced into bounds. `addCustomSection` always sets
a non-null id (`"custom_section_${System.currentTimeMillis()}"`,
LeisurePreferences.kt:355). For sanitization to drop a freshly-added
section, gson would have to nullify a populated `id` field on
round-trip — implausible for the stable schema and not seen in any
test artifact.

**Verdict.** Code-only analysis says NO. The over-aggressive
sanitization hypothesis cannot explain a freshly-added section
disappearing on a single device.

### Item 7' — Reactive observer chain / read-modify-write race (RED, refuted by code analysis)

All five mutators perform `readCustomSections(prefs)` inside a single
`leisureDataStore.edit { … }` block. DataStore `edit { … }` is atomic
per-key and serializes mutations, so concurrent writes cannot
interleave. The two `getCustomSections()` consumers
(`LeisureViewModel`, `LeisureSettingsViewModel`) and the two
repository reads (`LeisureRepository.getDailyLeisureProgress`,
`syncHabitCompletion`) are **read-only** — no reactive write loop
surfaced. The Compose `combine { sections, log → … }` chains are also
pure projections; nothing observes the flow and writes back.

**Verdict.** Code-only analysis says NO. No race or self-triggered
write loop in the read-modify-write cycle.

### Item 8' — `addCustomSectionActivity` cannot delete a section (NEW, RED for the user-claimed mechanism)

With or without PR #1080's guard:

- **Without guard (current main):** `current.map { section → if (section.id != sectionId) return@map section else section.copy(customActivities = section.customActivities + activity) }` returns the unchanged list when `sectionId` doesn't match (the activity is silently dropped, but every section is preserved). When `sectionId` matches, the activity is appended and every other section is preserved.
- **With guard (PR #1080):** if `sectionId` is missing, the entire write is skipped. Existing on-disk sections are unchanged.

**In neither path does this mutator delete a section.** The
user-claimed mechanism ("adding an activity deletes the section") is
**inconsistent with the data-layer contract** — the reported symptom
must be caused by a different action on the same screen, by a
different code path entirely, or by visual misattribution.

### A4 — Recommended logcat verification protocol

Before any Phase 2 fix, capture the on-device behaviour:

```bash
adb logcat -c && adb logcat \
  '*:S' AndroidRuntime:E DataStore:V LeisurePrefs:V Compose:E
```

(Add a temporary `Log.d("LeisurePrefs", "...")` to each `edit { }`
block in `LeisurePreferences` — entry, after `readCustomSections`, and
after the assignment — for the capture window only. Strip before
landing any fix.)

Then perform the operator's reported sequence:
1. Open Leisure Mode screen — note section is visible.
2. Tap "+" on the section, add activity "X".
3. Confirm in dialog.
4. Force-stop the app.
5. Relaunch.
6. Open Leisure Mode screen — observe section state.

**Diagnostic decision tree based on logcat output:**
- If logcat shows `addCustomSectionActivity` `edit { }` block fired and `readCustomSections` returned a list **already missing** the section → root cause is upstream (a prior write deleted the section, or sanitize-on-read dropped it). Continue tracing the prior write.
- If logcat shows `addCustomSectionActivity` `edit { }` fired with the section present and writeback contains the section → bug is downstream (read-side sanitization on next session, or render-time filter). Re-audit the read path.
- If logcat shows **no** `addCustomSectionActivity` entry between dialog-confirm and disappearance → the dialog's confirm didn't fire, or it routed through a different code path. Re-audit the UI binding.
- If logcat shows `removeCustomSection` firing unexpectedly → mis-tap or rogue invocation; trace the call stack.

### Verdict — HARD STOP per audit prompt

All three primary hypotheses (A1 unaudited write path / A2 sanitized
over-aggressive / A3 reactive observer chain) come back **negative on
code-only analysis**. Per the prompt's explicit STOP-condition:

> **HARD STOP if all 3 hypotheses (A1/A2/A3) come back inconclusive.**
> Escalate to operator with logcat capture + audit findings rather
> than ship a guess.

**Phase 2 does NOT auto-fire.** The defer-minimization principle
(memory #30) and the prompt's "no more defensive guards" constraint
together rule out shipping speculative code. The honest finding is:

- The data-layer contract for `addCustomSectionActivity` is provably
  preservation-only — it cannot delete a section. PR #918's regression
  test (still green) locks this in.
- PR #1080's defensive guard is correct and worth landing on its own
  merits, but cannot be the root-cause fix for the reported symptom
  because the unguarded mutator already cannot delete a section.
- The single-device persistent repro means the loss happens via
  **some action other than `addCustomSectionActivity`**, OR via
  runtime state (DataStore corruption, OS-level file truncation,
  Compose mis-binding to wrong section id) that static code review
  cannot surface.

### Recommended next step (operator action, not Phase 2)

1. **Land PR #1080 on its own merits** (defensive guard + tests). It
   shrinks the cone of possible causes and removes spurious DataStore
   emissions, which makes future logcat traces cleaner. It does not
   resolve this bug, and the audit doc should not pretend it does.
2. **Capture the logcat trace per A4 protocol** on a fresh AVD with
   the temporary debug logging patch. Save the full session log,
   including the pre-add and post-relaunch reads.
3. **Re-open Phase 1 batch 4 with the logcat artifact attached.**
   With concrete on-device traces, the diagnostic tree above
   collapses to a single confirmed branch and Phase 2 becomes a
   one-PR scope.

If a new hypothesis surfaces during logcat capture (e.g., logcat shows
`removeCustomSection` firing right after the add — a UI mis-binding),
that branch becomes Phase 1 batch 4's primary lead; the audit doc here
captures the negative results so batch 4 doesn't re-litigate them.

## Phase 2 — Batch 3

**Not implemented.** HARD STOP fired per audit prompt — all three
primary hypotheses negative on code-only analysis, no confirmed root
cause to fix. Shipping a speculative defensive guard would duplicate
PR #1080 (already on the table) and violate the prompt's "no more
defensive guards" constraint.

## Phase 3 — Bundle summary (batch 3)

**Shipped.** None. Audit-only batch.

**Deferred / blocked.**

- **Root-cause fix.** Blocked on operator-supplied logcat from the
  A4 protocol. Re-opens as Phase 1 batch 4 with concrete on-device
  evidence.
- **Sync-layer merge-by-id (Item 3' from batch 2).** Stays DEFERRED.
  Single-device repro confirmed by the operator means the sync
  hypothesis is wrong scope for this bug — but the multi-device
  whole-cloth overwrite remains a real latent risk and should be
  unblocked when (a) Crashlytics signals it, or (b) a multi-device
  repro lands independently.

**Memory candidates.** None.

**Schedule for next audit.** Driven by operator's logcat capture.

## Phase 1 — Batch 2 (re-audit, branch `claude/fix-leisure-category-bug-auK1O`)

User re-reported the same symptom on a fresh task without supplying the
device-count / sync-timing repro details deferred at end of batch 1.
Re-running the audit on the current `main` snapshot:

### Item 1' — `LeisurePreferences.addCustomSectionActivity` (GREEN, re-confirmed)

`addCustomSectionActivity_preservesSection_andAppendsActivity` (PR #918,
commit `04a2c0e0`) still passes locally and in CI. The data-layer
round-trip remains verifiably correct. **Premise unchanged: the bug is
not in the data-layer write path.**

### Item 2' — `addCustomSectionActivity` no-op write when target missing (YELLOW → PROCEED)

**Finding (new).** When `sectionId` is not in the current section list
(e.g. because a sync pull wiped it between dialog-open and
dialog-submit), the existing code still writes `gson.toJson(updated)`
back to DataStore — `updated` is bit-for-bit identical to `current`,
but the assignment still triggers a `Preferences` emission. Same shape
in `removeCustomSectionActivity` and `updateCustomSection`. None of
these mutate observable state when the target is missing, but the
spurious write makes ordering-sensitive sync-fingerprint logic noisier
than it needs to be and complicates root-cause traces.

**Verdict.** YELLOW → PROCEED on a defensive guard. Skipping the write
when the target is missing is strictly an information-preserving
change (no behavioural divergence on the happy path) and lets the
sync layer's last-pushed-fingerprint cache do its job without churn.
Does not by itself fix the reported symptom — see Item 3' — but
removes one source of confusion from the trace.

### Item 3' — Sync-layer overwrite (YELLOW, kept-DEFERRED with reasoning)

**Re-confirmed.** `GenericPreferenceSyncService.applyRemote` still
overwrites `custom_sections` whole-cloth via
`PreferenceSyncSerialization.applyTyped` (`stringPreferencesKey(name)`
write). The `__pref_device_id` self-echo guard prevents same-device
clobber on the happy path, but multi-device and reinstall-race paths
remain wide open.

**Why still DEFERRED (vs. PROCEED).** A real merge-by-id fix requires
either (a) a per-spec `MergeStrategy` hook in `PreferenceSyncSpec` so
`leisure_prefs.custom_sections` opts into element-merge while everything
else keeps LWW, or (b) a wholesale schema migration that splits
`custom_sections` into per-section preference keys. Both are
multi-PR scopes and risk regressions elsewhere (theme prefs, sort
prefs, dashboard prefs all share the same generic sync pipeline).
Without a confirmed multi-device repro from the user, the
risk/wall-clock ratio is unfavourable.

**Verdict.** YELLOW, DEFERRED again. The defensive guard in Item 2'
is the strict subset that ships safely without the user's repro
details.

### Item 4' — Regression test for missing-section guard (PROCEED)

Robolectric repro: pre-seed `custom_sections` with two real sections,
call `addCustomSectionActivity` with an unknown id, assert no write
occurred (both sections unchanged, no spurious emission). Same shape
for `removeCustomSectionActivity` and `updateCustomSection`. Locks in
the Item 2' guard against future drift.

---

## Phase 2 (batch 2)

Implemented in this branch (`claude/fix-leisure-category-bug-auK1O`):

1. `LeisurePreferences.addCustomSectionActivity` — early-returns
   from the `edit` block when `sectionId` is not in `current`.
2. `LeisurePreferences.removeCustomSectionActivity` — same guard.
3. `LeisurePreferences.updateCustomSection` — same guard.
4. New regression tests in `LeisurePreferencesTest`:
   - `addCustomSectionActivity_skipsWriteWhenSectionMissing`
   - `removeCustomSectionActivity_skipsWriteWhenSectionMissing`
   - `updateCustomSection_skipsWriteWhenSectionMissing`

The merge-by-id sync-layer fix (Item 3') stays DEFERRED.

## Phase 3 — Bundle summary (batch 2)

**Shipped (this branch).**

| Change | Scope | Impact |
|--------|-------|--------|
| Defensive guard + tests | `LeisurePreferences` mutating methods skip writes when target missing | Removes spurious DataStore emissions on stale-id mutation paths; locks the defensive contract against future drift. |

**Still deferred.**

- **Sync-layer merge-by-id (Item 3').** Needs either user-confirmed
  multi-device repro or a `MergeStrategy` hook in `PreferenceSyncSpec`
  so the change can land scoped to `leisure_prefs.custom_sections`
  rather than generically.

**Memory candidates.** None worth saving — the "skip the write when
the target is missing" pattern is a one-off mitigation, not a
generalisable rule.

**Schedule for next audit.** Driven by user's eventual Phase 4 answers
(device count + sync timing) or by independent Crashlytics signal that
points at sync-layer overwrite.
