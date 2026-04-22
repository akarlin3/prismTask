# Phase A Device Testing Runbook

**Scope:** Phase A closeout validation for PrismTask v1.4.27. Eleven
on-device scenarios exercising reminders (Pass 1) and the Phase 2 sync
pipeline on the two target devices.

**Target build:** `1.4.27` (versionCode `671`), branch
`claude/bootreceiver-defensive-init` @ `e9a8c0f5`, Room DB version `53`.

**Target devices:**
- **Samsung Galaxy S25 Ultra** — `SM-S938U1`
  (`adb -s adb-R5CXC2CR4MT-Lm6SCP._adb-tls-connect._tcp`)
- **Pixel emulator** — `emulator-5554` (Android 14+, SDK `sdk_gphone64_x86_64`)

**Reference runbook:** [`REMINDERS_TEST_RUNBOOK.md`](REMINDERS_TEST_RUNBOOK.md)
is the canonical per-scenario source. This document layers Phase A
framing on top (installed-build check, triage template, stop-ship
criteria, session scheduling) and does not duplicate the
precondition / step / expected / checklist blocks line-for-line — refer
to that doc for each scenario body.

---

## Pre-flight (run once before Day 1)

1. **Confirm installed build on both devices.** Anything older than
   v1.4.27 invalidates Phase A testing.
   ```bash
   adb -s emulator-5554 shell \
     "dumpsys package com.averycorp.prismtask | grep -E 'versionName|versionCode' | head -5"
   adb -s adb-R5CXC2CR4MT-Lm6SCP._adb-tls-connect._tcp shell \
     "dumpsys package com.averycorp.prismtask | grep -E 'versionName|versionCode' | head -5"
   ```
   Expected: `versionName=1.4.27`, `versionCode=671`. If older, rebuild
   + reinstall before continuing.

2. **Uninstall + reinstall both devices** so onboarding replays from
   clean state (Scenario 5, 6 depend on this).
   ```bash
   adb -s <device> uninstall com.averycorp.prismtask
   adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. **Device clock ±1 min of wall clock** on both devices. Confirm via
   `adb shell date`.

4. **Battery-optimization exemption** granted on both devices (Settings
   → Battery → Unrestricted → PrismTask). Verify on S25 via
   `adb shell dumpsys deviceidle whitelist | grep prismtask`.

5. **POST_NOTIFICATIONS granted** on both devices (re-granted after
   each clean install).

6. **Capture baseline logcat** for comparison:
   ```bash
   adb -s <device> logcat -c
   ```

---

## Schedule (Phase A Wed-Sat, Apr 22-25)

| Day | Scenarios | Runtime budget |
|---|---|---|
| Wed (Day 1) | 1, 2, 3, 4 — core task reminders | 90 min |
| Thu (Day 2) | 5, 6, 9, 10 — permissions + habit reminders | 90 min |
| Fri (Day 3) | 7, 8 — summary workers + permission-aware | 120 min |
| Sat (Day 4) | **11 only** — overnight Doze; results first thing Sun AM | setup 10 min, 8h wait |

---

## Scenario bodies

For each scenario below: preconditions, exact steps, expected outcome,
and the per-device checklist live in
[`REMINDERS_TEST_RUNBOOK.md`](REMINDERS_TEST_RUNBOOK.md). Phase A
additions only are captured here.

### Scenario 1 — Task reminder happy path
See `REMINDERS_TEST_RUNBOOK.md` §Scenario 1. Phase A addition: after
the notification fires, capture `adb shell dumpsys alarm | grep prismtask`
and confirm the alarm is removed post-fire (not just marked as fired).

### Scenario 2 — Task reminder with lead-time offset
See §Scenario 2. No Phase A addition.

### Scenario 3 — Cancel-on-complete
See §Scenario 3. Phase A addition: for the bulk-UNDO sub-scenario,
confirm the re-registered alarms survive a 30-second app background by
putting PrismTask in the recents list and then relocking.

### Scenario 4 — Recurring task reminder rolls over
See §Scenario 4. Phase A addition: verify the newly-inserted next-day
task's `cloud_id` is populated post-sync (not null) — this exercises
the Phase 2 Migration_51_52 backfill path through the recurrence
rollover code path.
```bash
adb -s <device> shell \
  "run-as com.averycorp.prismtask sqlite3 databases/prismtask.db \
    'SELECT id,title,cloud_id,due_date FROM tasks ORDER BY id DESC LIMIT 5'"
```

### Scenario 5 — POST_NOTIFICATIONS denial explainer
See §Scenario 5. No Phase A addition.

### Scenario 6 — Samsung battery-optimization guidance
**S25 only.** See §Scenario 6. No Phase A addition.

### Scenario 7 — Summary workers end-to-end
See §Scenario 7. Phase A addition: for `WeeklyHabitSummaryWorker`
specifically, confirm the pre-rename cleanup migration ran at most once
by checking that `weekly_habit_summary_migration_run` preference flag
is set after first launch:
```bash
adb -s <device> shell \
  "run-as com.averycorp.prismtask ls files/datastore/"
```
The `notifications.preferences_pb` file should exist. Toggling the
"Weekly Habit Summary" switch OFF/ON should not re-trigger the
migration (`adb logcat | grep WeeklyHabitSummaryMigration` should show
no further entries after the first launch).

### Scenario 8 — Permission-aware worker behavior
See §Scenario 8. No Phase A addition.

### Scenario 9 — Habit reminder (interval mode)
See §Scenario 9. No Phase A addition.

### Scenario 10 — Habit reminder (daily-time mode)
See §Scenario 10. Phase A addition: after triggering reboot via
`adb reboot`, explicitly confirm `BootReceiver` did NOT crash — per
the `BootReceiver Hilt test crash` memory entry, the defensive
`EntryPointAccessors.fromApplication` guard landed in `e9a8c0f5`.
Check:
```bash
adb -s <device> logcat -d | grep -E 'BootReceiver|IllegalStateException'
```
Expected: either no BootReceiver output (normal path) or
`"Hilt EntryPoints unavailable on boot; skipping reschedule"` (benign
warn). **Any `IllegalStateException` crash log is a ship-blocker.**

### Scenario 11 — Overnight Doze survival test
See §Scenario 11. Phase A addition: run only after all 1-10 pass.
If any of 1-10 fail with a ship-blocker, skip 11 and re-run the full
set after the fix lands.

Scheduling note: on Sat night, create the test tasks no earlier than
10:30 PM local and target the reminder for 7:00-7:15 AM Sun. Lock both
devices, set them face-down on a stationary surface, off charger. Do
not touch them until Sun AM.

---

## Triage template

For every failure, record one row below and categorize:

| # | Device | Scenario | Symptom | Logcat line (first frame) | Category |
|---|---|---|---|---|---|
|   |   |   |   |   |   |

**Categories:**
- **BLOCKER** — Ship-blocker. Notification silently drops, crashes on
  fire, data loss, permission path broken, BootReceiver crash,
  cross-device desync after sync (Scenario 7 + Part 4 runbook).
  Must fix before v1.4.27 ships.
- **PASS-2** — Defer to Reminders Pass 2 (v1.4.x or v1.5.0). Edge
  cases: non-exact alarm fallback precision, Samsung sleep-list
  reappearance after long idle, Calendar fuzzy-match collision,
  follow-up-nag timing drift.
- **COSMETIC** — Non-functional. Typo, missing translation, banner
  copy tweak, spacing.

A failure is BLOCKER by default. Demote to PASS-2 only with a written
justification in the row's "Symptom" column.

---

## Reporting template

After completing the runbook, summarize results with:

```
Branch:       claude/bootreceiver-defensive-init @ e9a8c0f5
Build:        v1.4.27 (671)
DB:           v53
S25 Ultra:    Android <X>, patch <YYYY-MM>
Pixel emu:    API <N>, image <system-image>
Scenarios:    <N>/11 passed
Blockers:     <list of # from triage table>
Pass-2s:      <list>
Cosmetics:    <list>
Overnight:    Started <ISO-8601>, fired <ISO-8601>, delta <±N min>
```

Attach full `adb bugreport` zips for every row tagged BLOCKER.

---

## Cannot-be-executed-remotely note

The Claude Code environment can drive both devices via adb (including
the S25 Ultra over TLS adb), but cannot:

- Physically observe the screen for visual-only verifications
  (banner styling, dialog wording, icon glyph correctness).
- Log into a Google account on either device (required for any
  sync-dependent verification; covered separately in
  [`PHASE_A_TWO_DEVICE_SIGNIN_VERIFICATION.md`](PHASE_A_TWO_DEVICE_SIGNIN_VERIFICATION.md)).
- Wait 8 wall-clock hours unattended for Scenario 11.
- Trigger the BOOT_COMPLETED receiver without a real `adb reboot`
  followed by unlock + foreground — which on the S25 over TLS adb
  drops the connection.

Scenarios that can be partially automated from adb:
- Scenarios 1-4: `adb shell am start`, `input text`, `input tap`,
  then `dumpsys alarm` verification. Manual screen observation still
  required for the actual notification fire.
- Scenario 7: `adb shell cmd jobscheduler run`, `dumpsys jobscheduler`.
  Fully automatable except for the per-channel notification visual
  verification.
- Scenario 8: deny POST_NOTIFICATIONS via
  `adb shell pm revoke com.averycorp.prismtask android.permission.POST_NOTIFICATIONS`,
  then force-run worker, grep logcat for `SecurityException` + `Result.success`.
  Fully automatable.

These are covered in the companion
[`PHASE_A_DEVICE_TESTING_ADB_AUTOMATION.md`](PHASE_A_DEVICE_TESTING_ADB_AUTOMATION.md)
sidecar if present, otherwise run manually.
