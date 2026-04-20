# Reminders Test Runbook (v1.4.0 Pass 1)

Ten on-device scenarios covering the reminders feature end-to-end.
Target devices: **Samsung Galaxy S25 Ultra** and **one Pixel** (Pixel 7
or later). Run every scenario on both unless the "Device" column
specifies one.

Each scenario lists preconditions, steps, expected outcome, and a
checklist to mark during execution.

Unless noted otherwise, after granting permissions the app should be
installed fresh (uninstall + reinstall) so the permission / onboarding
flow replays from a clean state.

---

## Legend

- ☐ = unchecked
- ☑ = pass
- ☒ = fail (add a note below the box with `adb logcat` snippet or a
  short description)
- **SoD** = Start of Day
- **BO** = Battery Optimization

---

## Scenario 1 — Task reminder happy path

**Device(s):** S25 Ultra + Pixel

**Preconditions:**
- POST_NOTIFICATIONS granted.
- Battery-optimization exemption granted.
- Device clock within ±1 minute of wall clock.

**Steps:**
1. Open Add Task, title "Runbook S1".
2. Schedule tab → due date = today, due time = current time + 3 minutes.
3. Schedule tab → reminder = "At due time" (0 ms offset).
4. Save.
5. Lock the screen.

**Expected:** heads-up notification fires within ±10 seconds of the due
time. Tapping opens PrismTask on the Today screen. Notification has a
**Complete** action button.

**Checklist:**
- ☐ S25 Ultra: notification fired on time
- ☐ S25 Ultra: Complete action marked the task complete
- ☐ Pixel: notification fired on time
- ☐ Pixel: Complete action marked the task complete

---

## Scenario 2 — Task reminder with lead-time offset

**Device(s):** S25 Ultra + Pixel

**Preconditions:** permissions granted, app foregrounded once.

**Steps:**
1. Create task "Runbook S2" due today, due time = now + 20 minutes.
2. Set reminder offset = 15 minutes before.
3. Save. Lock screen.

**Expected:** notification fires 5 minutes after saving (20 − 15).

**Checklist:**
- ☐ S25 Ultra: fired at due-time minus 15 minutes
- ☐ Pixel: fired at due-time minus 15 minutes

---

## Scenario 3 — Cancel-on-complete (single + bulk + subtask)

**Device(s):** S25 Ultra (primary) — spot-check on Pixel.

**Preconditions:** permissions granted.

**Steps (single):**
1. Create task "S3-single" due now + 2 min with a 0 ms reminder.
2. Immediately mark it complete via the task card swipe.

**Expected:** no notification at the 2-minute mark.

**Steps (bulk):**
3. Create 3 tasks "S3-bulk-1/2/3" each due now + 2 min with a 0 ms reminder.
4. Enter multi-select mode, select all 3, tap the Complete bulk action.

**Expected:** no notifications at the 2-minute mark for any of the 3.

**Steps (bulk UNDO):**
5. On the bulk-complete snackbar, tap **UNDO**.

**Expected:** the 3 tasks flip back to incomplete AND their reminders
fire at the 2-minute mark. Verify alarms are rearmed via `adb shell
dumpsys alarm | grep prismtask`.

**Steps (subtask):**
6. Create a parent "S3-parent" and a subtask "S3-child" with a reminder
   due now + 2 min on the subtask only.
7. Check off the subtask.

**Expected:** subtask's reminder does not fire. Parent's state is
unchanged (PrismTask has no auto-parent-completion).

**Checklist:**
- ☐ Single: reminder did not fire
- ☐ Bulk: no reminders fired for the 3 tasks
- ☐ Bulk UNDO: reminders were rearmed and fired
- ☐ Subtask: subtask reminder did not fire; parent untouched

---

## Scenario 4 — Recurring task reminder rolls over to next occurrence

**Device(s):** S25 Ultra + Pixel

**Preconditions:** permissions granted.

**Steps:**
1. Create task "S4-recur" with recurrence = Daily, due today at now + 2
   min, reminder offset = 0 ms.
2. Wait for the reminder to fire.
3. Complete from the notification action.
4. Open PrismTask and locate the newly-inserted next-day instance of
   "S4-recur".

**Expected:**
- Today's reminder fired and completed the instance.
- A new "S4-recur" task exists for tomorrow with the same reminder
  offset.
- `adb shell dumpsys alarm | grep prismtask` shows an alarm registered
  for the next-day trigger (not just the completed one).

**Checklist:**
- ☐ S25 Ultra: next-day recurrence inserted with reminder
- ☐ S25 Ultra: alarm rearmed (verified via dumpsys)
- ☐ Pixel: next-day recurrence inserted with reminder
- ☐ Pixel: alarm rearmed

---

## Scenario 5 — POST_NOTIFICATIONS denial explainer

**Device(s):** S25 Ultra + Pixel

**Preconditions:** fresh install.

**Steps:**
1. Complete onboarding.
2. When prompted for POST_NOTIFICATIONS, tap **Deny**.
3. Settings → Notifications.

**Expected:**
- The red "Notifications Blocked" banner appears at the top of the
  Notifications screen.
- Tapping **Allow** re-prompts the system permission dialog.
- Tapping **Open Settings** opens the OS App Info / Notifications
  screen for PrismTask.
- Returning to the Notifications screen after granting in Settings
  makes the banner disappear automatically (ON_RESUME recheck).
- Scheduling a task reminder does not crash the app.

**Checklist:**
- ☐ S25 Ultra: banner shown, Allow works, Open Settings works
- ☐ S25 Ultra: banner disappears on return after granting
- ☐ Pixel: banner shown, Allow works, Open Settings works
- ☐ Pixel: banner disappears on return after granting

---

## Scenario 6 — Samsung battery-optimization guidance

**Device(s):** S25 Ultra only.

**Preconditions:** fresh install on a Samsung device.

**Steps:**
1. Complete onboarding; grant POST_NOTIFICATIONS.
2. Observe the battery-optimization dialog (one-time, Samsung-only).
3. Tap **Open Settings** → exempt PrismTask.
4. Return to PrismTask, open Settings → Notifications.

**Expected during onboarding:**
- Dialog appears with two paragraphs — the exemption pitch and the
  Samsung "Put Unused Apps to Sleep" / "Deep Sleeping Apps" guidance.
- **Open Settings** deep-links to the system's battery-optimization
  exempt screen for PrismTask.

**Expected in Settings → Notifications:**
- The persistent battery-optimization banner is absent (because the
  device is now exempt).
- Revoke exemption via system Settings → Battery → Unrestricted →
  toggle PrismTask back to Optimized.
- Return to Notifications screen — banner reappears with Samsung
  sleep-list copy and a **Battery Settings** button that opens
  `Settings.ACTION_SETTINGS`.

**Checklist:**
- ☐ Onboarding dialog shows sleep-list paragraph
- ☐ Open Settings deep-link works
- ☐ Banner reappears when exemption is revoked
- ☐ Banner Samsung copy includes sleep-list wording
- ☐ Battery Settings button opens system Settings

---

## Scenario 7 — Summary workers end-to-end (5 workers)

**Device(s):** S25 Ultra + Pixel.

**Preconditions:** all toggles enabled (default). Pro tier enabled for
AI-gated workers (debug tier override in Settings).

**Steps:**
1. Settings → Notifications. Confirm all five toggles show ON:
   **Daily Briefing**, **Evening Summary**, **Weekly Summary**,
   **Balance Alerts**, **Re-engagement**.
2. Toggle each OFF then back ON, watch for stack traces in logcat.
3. Adb-force-run each worker:
   ```
   adb shell am broadcast -a com.averycorp.prismtask.RUN_WORKER --es worker briefing
   ```
   (or use WorkManager's test scheduler / the long-press debug panel if
   implemented). Otherwise set the system clock forward and observe
   natural triggers.
4. For **OverloadCheckWorker**: create 15+ work-tagged tasks to push
   the balance ratio above threshold; wait for the 4 PM trigger.
5. For **ReengagementWorker**: cancel by toggling OFF (to avoid the
   2-day absence requirement); verify it stops scheduling via
   `adb shell dumpsys jobscheduler | grep prismtask`.

**Expected:**
- Each of the 5 notifications posts under its own channel
  (`prismtask_briefing`, `prismtask_evening_summary`,
  `prismtask_weekly_summary`, `balance_alerts`,
  `prismtask_reengagement`).
- Toggling OFF removes the unique work from WorkManager; toggling ON
  re-enqueues with `ExistingPeriodicWorkPolicy.UPDATE`.

**Checklist:**
- ☐ Daily Briefing fired at configured morning hour
- ☐ Evening Summary fired at configured evening hour
- ☐ Weekly Summary fired on a forced run
- ☐ Overload Check fired with a hot balance state
- ☐ Re-engagement scheduled after 2 days of absence (log check)
- ☐ Toggling OFF → `dumpsys jobscheduler` no longer shows the job
- ☐ Toggling ON → re-registered cleanly

---

## Scenario 8 — Permission-aware worker behavior

**Device(s):** S25 Ultra.

**Preconditions:** fresh install, deny POST_NOTIFICATIONS.

**Steps:**
1. Deny POST_NOTIFICATIONS during onboarding.
2. Force-run **BriefingNotificationWorker** (or wait for its
   scheduled time).
3. Inspect logcat for `SecurityException` + worker result.

**Expected:**
- Worker runs its data-gathering (backend API call, etc).
- `NotificationManager.notify()` is wrapped in try/catch; worker still
  returns `Result.success()` (not `failure` or `retry`).
- No crash in logcat.
- Repeat for Evening, Reengagement, WeeklyHabitSummary —
  OverloadCheckWorker already had this wrapping.

**Checklist:**
- ☐ Briefing worker: no crash, `Result.success()`
- ☐ Evening worker: no crash, `Result.success()`
- ☐ Reengagement worker: no crash, `Result.success()`
- ☐ Weekly habit summary: no crash, `Result.success()`

---

## Scenario 9 — Habit reminder (interval mode)

**Device(s):** S25 Ultra + Pixel.

**Preconditions:** permissions granted.

**Steps:**
1. Create habit "S9-med" with:
   - Repeat reminder after logging = ON
   - Interval = 1 hour
   - Times per day = 2
2. Save.
3. Tap **Log** action on the first notification (or log via the habit card).

**Expected:**
- First reminder fires ~1 hour after save (subject to Android doze).
- Logging the habit triggers the next reminder ~1 hour later (dose 2 of 2).
- After dose 2, no further reminders until tomorrow.
- Notification subtitle shows "Dose 1 of 2" / "Dose 2 of 2".

**Checklist:**
- ☐ S25 Ultra: first reminder fired
- ☐ S25 Ultra: log triggered next reminder
- ☐ S25 Ultra: final dose did not re-arm
- ☐ Pixel: first reminder fired
- ☐ Pixel: log triggered next reminder
- ☐ Pixel: final dose did not re-arm

---

## Scenario 10 — Habit reminder (daily time mode)

**Device(s):** S25 Ultra + Pixel.

**Preconditions:** permissions granted.

**Steps:**
1. Create habit "S10-daily" with:
   - Daily Reminder = ON
   - Time = current wall-clock + 3 min
   - Repeat after logging = OFF
2. Save. Lock device.

**Expected:** notification fires at the chosen time.

**Additional checks after the first fire:**
3. Wait past the fire time. Confirm a new alarm is registered for
   tomorrow at the same time via
   `adb shell dumpsys alarm | grep prismtask` (request code offset
   `habitId + 900_000`).
4. Edit the habit → toggle Daily Reminder OFF → Save. Confirm the
   registered alarm is gone from `dumpsys alarm`.
5. Edit the habit → toggle Daily Reminder back ON → Save. Confirm
   the alarm is re-registered.
6. Reboot the device. Unlock. Confirm the alarm is re-registered
   (BootReceiver → `rescheduleAllDailyTime`).

**Checklist:**
- ☐ S25 Ultra: fired at configured time
- ☐ S25 Ultra: tomorrow's alarm registered after fire
- ☐ S25 Ultra: disable cancels the alarm
- ☐ S25 Ultra: re-enable re-registers the alarm
- ☐ S25 Ultra: alarm survives reboot
- ☐ Pixel: fired at configured time
- ☐ Pixel: tomorrow's alarm registered after fire
- ☐ Pixel: disable cancels the alarm
- ☐ Pixel: re-enable re-registers the alarm
- ☐ Pixel: alarm survives reboot

---

## Reporting template

After completing the runbook, summarize results with:

```
Device: <S25 Ultra / Pixel model>
OS build: <Android 14/15 + patch level>
PrismTask build: <versionCode>
Scenarios passed: X / 10
Failures: <list with scenario number + notes>
```

Attach `adb bugreport` zips for any scenario marked ☒.
