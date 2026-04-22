# Phase A — Two-Device Sign-In Verification Runbook

**Scope:** Confirm that after Phase 2 (Migration_51_52 + Fix A/B/C) and
Fix D (local Room collapse), signing out + signing back in across S25
Ultra and the Pixel emulator under multiple orderings produces **zero
re-duplication** in Room and Firestore.

**Target build:** `v1.4.27` (versionCode `671`), branch
`claude/bootreceiver-defensive-init` @ `e9a8c0f5`, Room DB version `53`.
**Currently installed on both devices:** `v1.4.25` (build `669`).
Reinstall `v1.4.27` before running — older builds invalidate the
initialUpload one-shot guard (Fix A) and the `restoreCloudIdFromMetadata`
path (Phase 2.5) that this runbook depends on.

**Account under test:** `averykarlin3@gmail.com` (UID
`2BgZcCXnBgdmAAVjM0KD5eCHgGo2`).

**Devices:**
- S25 Ultra — `adb -s adb-R5CXC2CR4MT-Lm6SCP._adb-tls-connect._tcp`
- Pixel emulator — `emulator-5554`

---

## Expected post-Fix-D canonical Room row counts

These are the target row counts after Fix D + sync. Both devices must
converge to these numbers under every ordering tested below.

| Table              | Expected rows |
|--------------------|--------------:|
| tasks              |            19 |
| tags               |             3 |
| projects           |             2 |
| habits             |            12 |
| task_completions   |            12 |
| task_templates     |             8 |
| habit_completions  |             0 |
| habit_logs         |             0 |
| courses            |             2 |
| course_completions |             3 |
| leisure_logs       |             3 |
| self_care_logs     |            38 |
| self_care_steps    |            36 |
| milestones         |             0 |
| task_tags          |             0 |

**Quarantine table:** `task_completions_quarantine` holds 2,353 null-taskId
rows. This row count must NOT change during the sign-in tests — the
quarantine table is local-only and never uploaded.

Source: `docs/PHASE_3_FIX_D_PLAN.md` §Summary (2026-04-21 dry-run).

---

## Expected Firestore doc counts (post-Firestore-cleanup)

Firestore cleanup is a separate Phase 3 step after Fix D. If Firestore
cleanup has run, doc counts must match Room row counts above. If
Firestore cleanup has NOT yet run, doc counts will be much higher
(tags ~2,165, tasks ~4,850, habits ~26 etc per the audit snapshot in
`PHASE_3_FIX_D_PLAN.md`). The critical runbook invariant in either
case: **sign-in must not INCREASE Firestore doc counts by even 1.**

Run the Firestore audit before and after each scenario:

```bash
python audit/firestore_audit.py --step schema > /tmp/fs_before.txt
# ... run sign-in scenario ...
python audit/firestore_audit.py --step schema > /tmp/fs_after.txt
diff /tmp/fs_before.txt /tmp/fs_after.txt
```

Expected diff: empty (byte-identical counts).

---

## Expected `PrismSync` log events on sign-in

Tag: `PrismSync` (from `PrismSyncLogger.kt`). Capture via:

```bash
adb -s <device> logcat -c
# ... sign in ...
adb -s <device> logcat -d -s PrismSync:V > /tmp/prismsync-<device>-<scenario>.log
```

**Per-sign-in expected sequence** (after `v1.4.27` lands):

1. `auth.signIn.success` — sign-in succeeds
2. `restoreCloudIdFromMetadata.start` — Phase 2.5 backfill kicks off
3. `restoreCloudIdFromMetadata.complete` with `backfilled=<N>` — N
   non-null cloud_ids restored from `sync_metadata`
4. `initialUpload.skipped.alreadyRan` — Fix A one-shot guard trips
   because this account previously uploaded from this install (preferences
   flag `initial_upload_completed=true` survived sign-out)
   **OR** (on fresh install) `initialUpload.start` → `initialUpload.complete`
5. `pullRemoteChanges.start` — pull kicks off
6. Per-collection: `pullRemoteChanges.<collection>.start` →
   `pullRemoteChanges.<collection>.complete` with `inserted=`, `updated=`,
   `skipped.rowHasCloudId=` counts
7. `pullRemoteChanges.complete`
8. `realtime.<collection>.listenerAttached` (multiple, one per synced collection)

**Anti-signals — these MUST NOT appear:**

- `initialUpload.complete` on the 2nd or subsequent sign-in of the same
  account on the same install (Fix A should skip it).
- Any `Duplicate ignored: …` lines from SyncMapper.
- `PrismSync` ERROR or WARN level entries naming `cloud_id` as null
  during upload (Fix C should have rejected those rows earlier).
- `pullRemoteChanges` running concurrently with `initialUpload` (Fix B
  serializes them via a mutex — if you see interleaved start/complete
  pairs across both, Fix B regressed).

---

## Pre-flight

Run on BOTH devices in parallel. Terminal 1 = emulator, Terminal 2 = S25.

### 1. Confirm installed build

```bash
# Terminal 1
adb -s emulator-5554 shell \
  "dumpsys package com.averycorp.prismtask | grep -E 'versionName|versionCode' | head -5"
# Terminal 2
adb -s adb-R5CXC2CR4MT-Lm6SCP._adb-tls-connect._tcp shell \
  "dumpsys package com.averycorp.prismtask | grep -E 'versionName|versionCode' | head -5"
```

Both must report `versionName=1.4.27`, `versionCode=671`. Currently
each reports `1.4.25` / `669` — rebuild + install via
`./gradlew installDebug` (or GitHub CI artifact) before proceeding.

### 2. Dump current Room counts on each device

**Precondition:** app force-stopped, so `-wal` is checkpointed.

```bash
# Terminal 1
adb -s emulator-5554 shell am force-stop com.averycorp.prismtask
adb -s emulator-5554 shell "run-as com.averycorp.prismtask \
  sqlite3 databases/averytask.db \
  'SELECT \"tasks\", COUNT(*) FROM tasks
    UNION ALL SELECT \"tags\", COUNT(*) FROM tags
    UNION ALL SELECT \"projects\", COUNT(*) FROM projects
    UNION ALL SELECT \"habits\", COUNT(*) FROM habits
    UNION ALL SELECT \"task_completions\", COUNT(*) FROM task_completions
    UNION ALL SELECT \"task_templates\", COUNT(*) FROM task_templates
    UNION ALL SELECT \"habit_completions\", COUNT(*) FROM habit_completions
    UNION ALL SELECT \"habit_logs\", COUNT(*) FROM habit_logs
    UNION ALL SELECT \"courses\", COUNT(*) FROM courses
    UNION ALL SELECT \"course_completions\", COUNT(*) FROM course_completions
    UNION ALL SELECT \"leisure_logs\", COUNT(*) FROM leisure_logs
    UNION ALL SELECT \"self_care_logs\", COUNT(*) FROM self_care_logs
    UNION ALL SELECT \"self_care_steps\", COUNT(*) FROM self_care_steps
    UNION ALL SELECT \"milestones\", COUNT(*) FROM milestones
    UNION ALL SELECT \"task_tags\", COUNT(*) FROM task_tags;'"
# Terminal 2 — same, replace -s with the S25 serial
```

On `sqlite3` not available in the shell, pull the DB and query host-side:

```bash
adb -s <device> exec-out run-as com.averycorp.prismtask \
  cat databases/averytask.db > /tmp/<device>.db
sqlite3 /tmp/<device>.db "<the SELECT above>"
```

Record the starting counts per device in the results table below.

### 3. Firestore baseline

```bash
python audit/firestore_audit.py --step schema > /tmp/fs_baseline.txt
cat /tmp/fs_baseline.txt
```

Record the per-collection counts. Any scenario that increases these is
a BLOCKER.

---

## Scenarios

### S1 — Sign out both → sign in emulator-first → S25-second

**Steps:**
1. On BOTH devices, sign out via Settings → Account → Sign Out.
2. Force-stop both apps:
   ```bash
   adb -s emulator-5554 shell am force-stop com.averycorp.prismtask
   adb -s adb-R5CXC2CR4MT-Lm6SCP._adb-tls-connect._tcp shell am force-stop com.averycorp.prismtask
   ```
3. Clear logcat on both.
4. Launch the app on emulator-5554, sign in as
   `averykarlin3@gmail.com`. Wait for the PrismSync sequence to
   complete (see §"Expected PrismSync events"). Capture logcat.
5. Record post-sign-in Room counts on emulator.
6. Launch the app on S25 Ultra, sign in as
   `averykarlin3@gmail.com`. Wait for the PrismSync sequence. Capture
   logcat.
7. Record post-sign-in Room counts on S25.
8. Re-run Firestore audit, diff against baseline.

**Pass criteria:**
- Both devices land at the canonical row counts.
- Firestore audit diff is empty (no new docs).
- Both logcat captures contain `initialUpload.skipped.alreadyRan` OR a
  single complete `initialUpload.start → .complete` pair (only on truly
  fresh install — if sign-out didn't wipe the install-scoped pref flag,
  skipped is expected).
- No `Duplicate ignored` or `cloud_id` WARN lines.

### S2 — Sign in S25-first → emulator-second (ordering reversed)

Same as S1 but reverse the device order in steps 4-7.

**Pass criteria:** identical to S1.

### S3 — Concurrent sign-in (both at once)

**Steps:**
1. Sign out + force-stop both devices.
2. Start both logcat captures.
3. Tap Sign In on BOTH devices within 3 seconds of each other (wall-clock —
   script it if needed via `adb shell input tap <x> <y>`).
4. Wait for both to complete.
5. Record Room counts on both and re-run Firestore audit.

**Pass criteria:**
- Same canonical counts on both devices.
- Firestore diff empty.
- Fix B's mutex serializes per-device; **cross-device** concurrent
  sign-in is not a single-mutex scenario (two physical clients, two
  independent Fix-A preference flags, two independent upload paths).
  Therefore on a fresh install it is expected that both devices emit
  `initialUpload.start` — Firestore merge semantics via `cloud_id`
  presence must be the de-dup line of defense, not Fix A. Confirm
  Firestore count diff is still 0 even when both devices upload
  concurrently.
- If either device emits `PrismSync` ERROR, BLOCKER.

### S4 — Sign out one while the other is signed in

**Steps:**
1. Start from steady state (both signed in, canonical Room counts).
2. On S25, sign out. Do NOT touch emulator.
3. Wait 60 seconds. Record emulator Room counts — must be unchanged.
4. Re-run Firestore audit — doc counts must be unchanged.
5. On S25, sign back in as same account. Capture logcat.
6. Record S25 Room counts.

**Pass criteria:**
- Emulator counts unchanged in step 3.
- S25 returns to canonical counts after step 5.
- S25's logcat shows `initialUpload.skipped.alreadyRan` (install-scoped
  flag survived sign-out); emulator's logcat is silent (no listener
  churn from S25's sign-out).

### S5 — Fresh install sign-in (the hardest case)

**Steps:**
1. Uninstall PrismTask on emulator:
   ```bash
   adb -s emulator-5554 uninstall com.averycorp.prismtask
   ```
2. Reinstall `v1.4.27` APK. Grant POST_NOTIFICATIONS, dismiss
   onboarding, sign in.
3. Wait for sync to complete. Record Room counts.
4. Re-run Firestore audit.

**Pass criteria:**
- Emulator Room counts match canonical targets (hydrated from Firestore
  via `pullRemoteChanges`).
- `initialUpload.start` DID fire on this install (install-scoped flag
  was wiped by uninstall) but `initialUpload.complete` must show zero
  new docs uploaded because local Room was empty at that time, AND no
  local row was created with a null cloud_id (Fix C rejects upload of
  rows without cloud_id — on a fresh install there are no such rows).
- Firestore doc counts unchanged — no re-duplication from a fresh client.

This is the scenario that tonight's Phase 2 work is designed to make
safe. If any of S1-S4 pass but S5 regresses, the regression is in the
initial-upload code path (Fix A/B/C) and is a BLOCKER.

---

## Results table

Copy this block per scenario run and fill in:

```
Scenario:         <S1 / S2 / S3 / S4 / S5>
Started:          <ISO-8601>
Emulator counts:  tasks=<N>, tags=<N>, projects=<N>, habits=<N>,
                  task_completions=<N>, task_templates=<N>,
                  habit_completions=<N>, habit_logs=<N>,
                  courses=<N>, course_completions=<N>,
                  leisure_logs=<N>, self_care_logs=<N>,
                  self_care_steps=<N>, milestones=<N>, task_tags=<N>
S25 counts:       <same schema>
Firestore delta:  <empty | list of (collection, before→after)>
PrismSync:        <emulator log path>
                  <S25 log path>
Result:           <PASS | BLOCKER: <reason>>
```

---

## No-re-duplication verification

The single bottom-line check after all five scenarios:

```bash
adb -s emulator-5554 exec-out run-as com.averycorp.prismtask \
  cat databases/averytask.db > /tmp/em-final.db
adb -s adb-R5CXC2CR4MT-Lm6SCP._adb-tls-connect._tcp exec-out \
  run-as com.averycorp.prismtask cat databases/averytask.db > /tmp/s25-final.db

# Row counts must match on both devices AND match the canonical
# targets. Anything higher is a re-duplication.
for t in tasks tags projects habits task_completions task_templates \
         habit_completions habit_logs courses course_completions \
         leisure_logs self_care_logs self_care_steps milestones task_tags; do
  em=$(sqlite3 /tmp/em-final.db "SELECT COUNT(*) FROM $t")
  s25=$(sqlite3 /tmp/s25-final.db "SELECT COUNT(*) FROM $t")
  echo "$t: emulator=$em, s25=$s25"
done
```

Expected output exactly:
```
tasks: emulator=19, s25=19
tags: emulator=3, s25=3
projects: emulator=2, s25=2
habits: emulator=12, s25=12
task_completions: emulator=12, s25=12
task_templates: emulator=8, s25=8
habit_completions: emulator=0, s25=0
habit_logs: emulator=0, s25=0
courses: emulator=2, s25=2
course_completions: emulator=3, s25=3
leisure_logs: emulator=3, s25=3
self_care_logs: emulator=38, s25=38
self_care_steps: emulator=36, s25=36
milestones: emulator=0, s25=0
task_tags: emulator=0, s25=0
```

Any row where emulator ≠ s25 is a BLOCKER (cross-device desync).
Any row where the value exceeds the canonical target is a BLOCKER
(re-duplication).

---

## Cannot-be-executed-remotely note

The Claude Code environment can drive adb against both devices but
cannot log into a Google account on either. Every step labeled
"sign in" requires the user to tap through Google Sign-In UI on the
physical device. `adb shell input tap` can navigate screens but cannot
drive the Chrome Custom Tab OAuth flow that Credential Manager opens
on sign-in. The runbook is therefore written to be hand-driven; adb
is used for state inspection and log capture, not for the sign-in
action itself.
