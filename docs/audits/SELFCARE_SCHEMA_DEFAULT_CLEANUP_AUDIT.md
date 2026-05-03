# SelfCareLogEntity schema default cleanup — Phase 1 audit

**Scope:** Drive-by cleanup proposed in PR #1067 audit § non-obvious findings:
remove the Kotlin constructor default `selectedTier: String = "solid"` from
[`SelfCareLogEntity`](../../app/src/main/java/com/averycorp/prismtask/data/local/entity/SelfCareLogEntity.kt)
on the theory that the default is now dead — every writer explicitly passes
`selectedTier` post-PR #1067, so the implicit fallback never fires at
runtime. Goal: convert silent fallback into compile-time enforcement so
future callers can't forget the field.

**Verdict:** **STOP — no work needed.** The premise is wrong. The default is
load-bearing for the v3 backup-restore path. Do not remove.

**Audit references:** PR #1067 (`d6a60a6` — default-tier preference applied
on Today/Habits + post-toggle), PR #999 (initial default-tier feature),
[`DataImporter` KDoc lines 152–167](../../app/src/main/java/com/averycorp/prismtask/data/export/DataImporter.kt#L152-L167).

---

## Item 1 — Remove Kotlin default `selectedTier = "solid"` (RED — premise wrong)

### Premise verification (FAILS)

PR #1067 audit's non-obvious finding said: *"all writers (toggleStep,
setTierForTime, insertLog) explicitly pass selectedTier from the resolved
preference, so the schema default is never reached at runtime."* That
statement enumerated the **active write paths** correctly but missed a
fourth class of caller: the v3 generic-import path in
[`DataImporter.importSelfCareLogs`](../../app/src/main/java/com/averycorp/prismtask/data/export/DataImporter.kt#L687).

```kotlin
val default = SelfCareLogEntity(routineType = routineType, date = date)
val merged = mergeEntityWithDefaults(default, obj)
selfCareDao.insertLog(merged.copy(id = 0))
```

This pattern — **construct an entity with Kotlin defaults, then overlay the
JSON keys present in the backup file** — is the explicitly-documented
forward-compat mechanism for v3 backups. From the `DataImporter` class KDoc:

> Each entity JSON object is overlaid onto a freshly-constructed default
> instance via `mergeEntityWithDefaults`. This means any fields added to
> an entity **after** the file was exported automatically get their Kotlin
> constructor default values on import — so old backups keep working as
> the schema evolves.

So the Kotlin default for `selectedTier` is **deliberately** the source of
truth for legacy backup files that pre-date the field. Removing the default
forces a decision the v3 importer was specifically designed to avoid making
(make `selectedTier` nullable? hard-code `"solid"` at the importer call
site? error on legacy backups?). All three options regress the schema-
evolution invariant the v3 format was built around.

### Findings (recon-first quad sweep)

**A0(a) drive-by since PR #1067:** `git log -p -S selectedTier --since=2026-05-01`
on the entity returns no output. PR #1067 (`d6a60a6`, 1 day old) is the
last touch. No drive-by fix has shipped.

**A0(b) parked-branch sweep:** Only `claude/remove-selfcare-default-HlFBh`
(this branch). No conflicting in-flight work.

**A0(c) `SelfCareLogEntity(` constructor call sites:**

| Site | Passes `selectedTier`? | Notes |
| --- | --- | --- |
| `SelfCareRepository.kt:491` (`setTier` insert) | ✓ explicit | from `tier` arg |
| `SelfCareRepository.kt:508` (`logTier` insert) | ✓ explicit | from `tier` arg |
| `SelfCareRepository.kt:593` (`toggleStep` insert) | ✓ explicit | `resolveStartingTier()` |
| `SelfCareRepository.kt:819` (`setTierForTime` insert) | ✓ explicit | `resolveStartingTier()` |
| `SyncMapper.kt:653` (`mapToSelfCareLog`) | ✓ explicit | mapper-level `?: "solid"` fallback (orthogonal) |
| **`DataImporter.kt:687` (`importSelfCareLogs`)** | **✗ relies on default** | v3 generic-import "freshly-constructed default instance" pattern |
| `SelfCareRepositoryConflictTest.kt:88,111,128,235,266` | mixed (3 of 5 omit) | test fixture convenience |
| `SyncMapperTier2Test.kt:26,61,269` | mixed (2 of 3 omit) | test fixture convenience |

The single production omitter (`DataImporter`) is the load-bearing one —
it's not a slip, it's the documented design. Test omitters are convenience.

**A0(e) sibling-primitive axis:** `grep 'selectedTier\s*=\s*"'` across
`data/local/entity/` returns only the entity itself. No copies of the magic
string elsewhere in entities. (The `SyncMapper.kt:658` `?: "solid"` is the
one and only mirror, and it lives in the mapper layer where that
double-defaulting is intentional protection against malformed Firestore
payloads.)

### Risk classification

**RED.** The proposed fix would silently break v3 backup restore for any
backup file produced before `selectedTier` was added (PR #999 era and
earlier). The field would become `null` after `mergeEntityWithDefaults` if
the type was made nullable, or the importer would need a hard-coded
`"solid"` literal. Either way, the "compile-enforcement is the win" framing
inverts: the importer is the exact site where we **want** Kotlin's default
to silently fill in missing fields. Compile-enforcement here surfaces a
non-bug as a bug.

### Recommendation

**STOP — no work needed. Remove from D5 backlog.**

The dead-default thesis was correct for the *active* write paths but missed
the schema-evolution path. The default's continued existence is the
designed behavior, not a code smell. Filing this as DEFER would imply
"come back later" — but there's no later: as long as the v3 backup format
is supported, the default has to stay. Better to close cleanly.

---

## Anti-pattern flag (informational, not an action)

The double-defaulting between
[`SelfCareLogEntity.selectedTier = "solid"`](../../app/src/main/java/com/averycorp/prismtask/data/local/entity/SelfCareLogEntity.kt#L24)
and [`SyncMapper.mapToSelfCareLog ?: "solid"`](../../app/src/main/java/com/averycorp/prismtask/data/remote/mapper/SyncMapper.kt#L658)
is a soft DRY violation — two literal `"solid"` strings have to stay in sync.
Not worth fixing on its own (mapper-level fallback is correct defensive
posture against malformed Firestore data), but worth flagging if a
`SelfCareTiers` constants module ever lands.

## Improvement table (sorted by wall-clock-savings ÷ implementation-cost)

Empty by design. The single audited item is STOP-no-work-needed. Phase 2
will not fire.

## Phase 3 placeholder

No Phase 2 PRs to bundle. Phase 3 will record the verdict and close the
audit.
