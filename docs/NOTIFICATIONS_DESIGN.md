# PrismTask Notifications — Cross-Platform Design

Companion design document for the customizable notification system landed in
v1.4.0 and extended in v1.6.0 with medication reminder modes. The Android
implementation shipped in this branch covers every domain below; this
document captures the information architecture, data model, and platform
contracts so the same behavior can be re-created on iOS, web, and desktop
with consistent semantics across devices.

> **v1.6.0 addition: Medication reminder modes.** Medication reminders now
> support two delivery modes — **CLOCK** (fixed slot times, the v1.4–v1.5
> behavior) and **INTERVAL** (every N minutes since the most recent dose).
> A three-level resolver picks the effective mode for each medication at
> reminder time:
>
> 1. The medication's own override (`medications.reminder_mode`)
> 2. The slot's override (`medication_slots.reminder_mode`)
> 3. The global default (stored in `UserPreferencesDataStore` as
>    `medicationReminderModeFlow`, default `CLOCK`)
>
> NULL at any level means "inherit the next level down." Interval minutes
> are always clamped to `[60, 1440]`; an INTERVAL resolution that lacks a
> value at every level falls back to the global default minutes.
>
> A separate `MedicationIntervalRescheduler` owns AlarmManager
> registrations for INTERVAL-mode slots and per-medication overrides
> (request-code namespaces `+500_000` for slots, `+600_000` for med
> overrides), distinct from the CLOCK-mode scheduler at `+400_000`. The
> rescheduler observes `MedicationDoseDao.observeMostRecentDoseAny()` and
> reschedules all INTERVAL alarms on every dose emission, so the cadence
> re-anchors after every taken dose. Synthetic-skip doses
> (`is_synthetic_skip = 1`) act as scheduling anchors when the user
> explicitly skips a slot — they show up in the dose stream but are
> filtered out of the human-facing log UI.
>
> Web reminder *delivery* is not yet implemented (Web Push lands in a
> future release); the web app reads and writes the same `reminder_mode`
> + `reminder_interval_minutes` fields on Firestore so settings sync to
> the user's phone.

---

## 1. Settings Information Architecture

```
Settings
└── Notifications (hub)
    ├── Active Profile  ────────────→  Profiles list
    │                                    ├── <Profile>                  (tap → edit)
    │                                    └── Auto-switch rules          (per profile)
    ├── What You're Alerted About
    │   ├── Notification Types        (per-surface toggles)
    │   ├── Daily Briefing            (times / tone / sections / TTS)
    │   ├── Streak & Gamification    (opt-out, milestone lead)
    │   └── Collaborator Updates     (digest mode, per-event toggles)
    ├── How You're Alerted
    │   ├── Sound                     (picker + custom uploads + volume + fades)
    │   ├── Vibration & Haptics      (preset, intensity, repeat, custom recorder)
    │   ├── Visual Display            (display mode, badges, toast position, contrast)
    │   └── Lock Screen & Badges     (visibility, accent colors)
    ├── Timing
    │   ├── Quiet Hours               (schedule, DoW, priority override)
    │   ├── Snooze & Re-Alerts       (snooze presets, re-alert interval + attempts)
    │   └── Escalation Chain          (gentle → standard → loud → full-screen)
    ├── Devices
    │   └── Smartwatch                (sync mode, watch volume, watch haptics)
    └── Preview & Test
        └── Preview live; fire test notification; simulate escalation
```

Progressive disclosure principle: every screen ships with **sensible defaults**
(built-in profiles like Default, Work, Focus, Weekend, Sleep, Travel) so casual
users never have to open a sub-screen. Power users can drill in per domain and
override at the per-task level when needed.

---

## 2. Data Model

Persistence straddles two layers:

1. **Room** (`app/src/main/java/com/averycorp/prismtask/data/local/entity/`):
   - `NotificationProfileEntity` — the canonical profile blob (table
     `reminder_profiles`, preserved for schema continuity)
   - `CustomSoundEntity` — user-uploaded audio metadata
2. **DataStore** (`app/src/main/java/com/averycorp/prismtask/data/preferences/NotificationPreferences.kt`):
   - Active profile id + per-category overrides
   - Global toggles (every per-surface flag lives here so per-type muting stays
     snappy without a profile round-trip)
   - Briefing schedule / tone / sections
   - Collaborator digest mode
   - Watch sync mode, watch volume, watch haptic intensity
   - Visual: badge mode, toast position, high-contrast notifications
   - Snooze durations CSV

### Profile schema (abbreviated)

| Column                        | Type   | Notes                                  |
|-------------------------------|--------|----------------------------------------|
| `id`                          | INTEGER PK |                                  |
| `name`                        | TEXT   | Unique-per-user                        |
| `offsets_csv`                 | TEXT   | e.g. "86400000,3600000,0"             |
| `urgency_tier`                | TEXT   | `low` \| `medium` \| `high` \| `critical` |
| `sound_id`                    | TEXT   | catalog id, `__system_default__`, `__silent__`, or `custom_<rowid>` |
| `sound_volume_percent`        | INT    | 0–100                                  |
| `sound_fade_in_ms` / `..out`  | INT    | 0–5000                                 |
| `silent`                      | BOOL   | explicit silent override               |
| `vibration_preset`            | TEXT   | `single` / `double` / `triple` / `long` / `sos` / `heartbeat` / `wave` / `custom` / `none` |
| `vibration_intensity`         | TEXT   | `light` / `medium` / `strong`          |
| `vibration_repeat_count`      | INT    | 1–10                                   |
| `vibration_continuous`        | BOOL   | repeat until interacted                |
| `custom_vibration_pattern_csv`| TEXT?  | long[] CSV for CUSTOM preset           |
| `display_mode`                | TEXT   | `standard` / `persistent` / `full_screen` / `minimal` |
| `lock_screen_visibility`      | TEXT   | `show_all` / `app_name` / `hidden`     |
| `accent_color_hex`            | TEXT?  | optional `#RRGGBB`                     |
| `badge_mode`                  | TEXT   | `off` / `total` / `per_category` / `priority` |
| `toast_position`              | TEXT   | desktop/web only                       |
| `escalation_chain_json`       | TEXT?  | JSON from `NotificationProfileResolver` |
| `quiet_hours_json`            | TEXT?  | JSON (window + DoW + break-through set) |
| `snooze_durations_csv`        | TEXT   | e.g. "5,15,30,60"                      |
| `re_alert_interval_minutes`   | INT    | 1–60                                   |
| `re_alert_max_attempts`       | INT    | 1–10                                   |
| `watch_sync_mode`             | TEXT   | `mirror` / `watch_only` / `differentiated` / `disabled` |
| `watch_haptic_preset_key`     | TEXT   | preset key, defaults to `single`       |
| `auto_switch_rules_json`      | TEXT?  | list of trigger rules (time/day/focus/calendar/location) |

Custom sound rows:

| Column              | Type    | Notes                          |
|---------------------|---------|--------------------------------|
| `id`                | INTEGER PK |                              |
| `name`              | TEXT    | display label                  |
| `original_filename` | TEXT    | from picker                    |
| `uri`               | TEXT    | points into `filesDir/notification_sounds/` |
| `format`            | TEXT    | `mp3` / `wav` / `m4a` / `ogg`  |
| `size_bytes`        | INTEGER | ≤ 10 MB (enforced at import)   |
| `duration_ms`       | INTEGER | ≤ 30 s                         |
| `created_at`        | INTEGER |                                |

### Inheritance resolver

Effective delivery for a fired notification is resolved:

```
task override > category override > active profile > built-in default
```

The `NotificationProfileResolver` produces a `NotificationProfile` data class
that every platform helper consumes — this is the single shape shared across
Android, iOS, web, and desktop UIs.

### Sync strategy

- Profiles + preferences write to Firestore through the existing
  `SyncService` / `BackendSyncService` pipeline under per-user documents:
  - `users/{uid}/notification_profiles/{id}`
  - `users/{uid}/notification_preferences` (DataStore snapshot)
  - `users/{uid}/custom_sounds/{id}` + binary stored in Firebase Storage.
- Per-device overrides (e.g. watch-only haptics) are partitioned by
  `deviceId` so switching a phone doesn't spam another signed-in device.
- Conflict resolution: last-writer-wins with a `profile_updated_at`
  field, matching the existing entity sync convention.

---

## 3. Platform-Specific Implementation Notes

### Android (this release)

- **Channels**: one channel per profile signature under
  `NotificationHelper.ensureProfileChannel`. Channels are immutable for
  sound/vibration/importance, so profile changes mint a new channel id
  and delete stale ones.
- **Full-screen**: `USE_FULL_SCREEN_INTENT` is requested in the manifest
  (already present). Android 14+ restricts grant to calendar / alarm
  apps by default — PrismTask requests the permission at first use
  via `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`.
- **Exact alarms**: `ExactAlarmHelper` already bridges
  `SCHEDULE_EXACT_ALARM` (Android 12) vs `USE_EXACT_ALARM` (Android 13+).
- **DND**: `setBypassDnd(true)` is applied on the active profile's
  channel when `silent = false` AND `urgency_tier = critical`. Otherwise
  `canBreakThrough` in `QuietHoursWindow` is the cross-platform source
  of truth.
- **Battery optimization**: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is
  already declared; we surface a onboarding-style nudge (existing
  infrastructure) the first time a user enables escalation.
- **Focus modes**: Android "Modes" integration via
  `NotificationManager.getAutomaticZenRules()` — read-only check used by
  `ProfileAutoSwitcher` to flip to a low-intrusion profile.

### iOS

- **Framework**: `UNUserNotificationCenter`. Built-in sounds ship as
  `UNNotificationSound(named:)`; custom uploads land in the app's
  `Library/Sounds` directory (system requirement). Sound file **must**
  be ≤ 30 s per Apple; the 30-second cap in `CustomSoundEntity` is
  chosen so a single validated file works on both platforms.
- **Critical Alerts**: `UNAuthorizationOptions.criticalAlert` gated by
  Apple entitlement; only profiles with `urgency_tier = critical` pass
  through DND. We request the entitlement via a normal App Store
  provisioning profile (documented requirement).
- **Focus integration**: `INFocusStatus` + `INFocusStatusCenter`
  observers feed `ProfileAutoSwitcher.Context.inOsFocusMode`.
- **Badges**: `UNMutableNotificationContent.badge` is an `NSNumber`;
  `BadgeMode.PER_CATEGORY` is emulated by maintaining a local counter
  per category and setting the badge to the active category's count.
- **Escalation**: scheduled via
  `UNTimeIntervalNotificationTrigger` instances; cancellation via
  `removePendingNotificationRequests(withIdentifiers:)` using the same
  `<taskId>-<stepIndex>` identifier convention.
- **Lock-screen visibility**: iOS lock-screen is always on; `HIDDEN`
  maps to `.setValue(.none, forKey: "lockscreenVisibility")` equivalent
  (`shouldIncludeContent` disabled) and routes the notification to the
  notification center with no preview.

### Web

- **Framework**: `Notification` API + Web Push (VAPID) for background
  delivery from the backend.
- **Silent override**: `Notification.silent = true` (Chrome/Firefox
  only; Safari ignores).
- **Sound**: Web Notification API has no sound parameter; the service
  worker plays sounds via `new Audio(url).play()` on receipt for
  foreground tabs. Background tabs fall back to the OS notification
  sound.
- **Vibration**: `Notification.vibrate` accepts a pattern array — we
  emit `VibrationPatterns.patternFor` output directly with repeat count
  applied in JS.
- **Display mode**: the toast UI is rendered inside the PrismTask web
  client when the tab is focused (honoring `toast_position`), falling
  back to a native notification when hidden.
- **Full-screen takeover** + **persistent banner** require the Web tab
  to be open; when closed, degrade to a standard Web Push notification.
- **Permissions**: `Notification.requestPermission()` gated on an
  in-app button; we persist the state across sessions via the same
  DataStore-backed settings blob (synced from the mobile client).

### Desktop

| OS      | API                                            | Notes                                |
|---------|------------------------------------------------|--------------------------------------|
| macOS   | `NSUserNotificationCenter` (old) / `UNUserNotificationCenter` (new)         | Same iOS model; Critical Alerts unavailable. Focus modes via `INFocusStatus`. |
| Windows | Windows Runtime `ToastNotificationManager`     | Adaptive toasts support full-screen via `Scenario="Alarm"`. |
| Linux   | `libnotify` / D-Bus `org.freedesktop.Notifications` | `hints.urgency = 2` for CRITICAL.  |

All three platforms render `toast_position` via the PrismTask desktop
client's own surface (Electron or Tauri-rendered) for rich modes, with
the native APIs used for background delivery.

---

## 4. Smartwatch Architecture

```
                       (phone) NotificationHelper
                                      │
                       ┌──────────────┴──────────────┐
                       │                             │
             watch_sync_mode = mirror          watch_sync_mode = differentiated
                       │                             │
               sameBuilder emits         forks into (watchBuilder) with
               to phone + watch          watch_haptic_preset_key + watchVolumePercent
```

- **Apple Watch**: notifications originate on the phone via the
  paired-device bridging API; when `watch_sync_mode = watch_only` we
  set `NotificationCompat.Builder.setLocalOnly(false)` and request the
  phone suppress its own buzz via `Notification.BADGE_ICON_NONE`. On
  iOS, `hints.interruptionLevel` maps to `.timeSensitive` for CRITICAL
  so the Watch escalates independently.
- **Wear OS**: emitted through `NotificationCompat.WearableExtender`;
  quick-actions (Complete, Snooze, Dismiss, Open-on-Phone) attach via
  `addAction(...)` with distinct pending intents.
- **Watch-specific haptics**: Apple Watch "taps" map onto our
  `VibrationPreset` via a lookup table; Wear OS honors the same
  `VibrationEffect` waveform since Android-based watches share the
  vibrator API.
- **Watch-specific settings** live in `watch_sync_mode`,
  `watch_volume_percent`, `watch_haptic_intensity`, + the per-profile
  `watch_haptic_preset_key`.

---

## 5. Edge Cases & Fallbacks

| Scenario                                     | Behavior |
|----------------------------------------------|----------|
| Notification permission denied               | Settings hub shows a persistent banner with a deep link to system settings. All schedulers record the attempt to diagnostics. |
| Full-screen intent not granted (Android 14)  | Escalation chain downgrades `FULL_SCREEN` step to `LOUD_VIBRATE`. |
| Exact-alarm permission revoked               | `ExactAlarmHelper` falls back to inexact alarms and logs a warning; escalation delays become ±5 min. |
| Battery optimization kills background work   | `BootReceiver` reschedules on next boot. Reengagement worker detects >48h absence and surfaces a one-time prompt. |
| Custom sound file deleted from storage       | `SoundResolver` falls back to system-default URI; no crash. |
| Clock change (timezone / DST)                | `ReminderScheduler` recomputes triggers from dueDate on next sync. Escalation cancels + reschedules. |
| Offline                                      | All preferences stored locally in DataStore/Room; cloud sync retries on connectivity restore. |
| Conflicting device overrides                 | Last-writer-wins via `updated_at`; per-device override fields are merged by device id. |
| Profile deleted while referenced as active   | DataStore's `activeProfileId` falls through `profiles.firstOrNull()`, then to `NotificationProfile.builtInDefault()`. |
| Migration failure (39 → 40)                  | Column additions are idempotent via defaults; if Room detects mismatch it throws at startup — covered by `DatabaseEntityRegistrationTest`. |

---

## 6. Accessibility

- **Screen readers**: every toggle and slider declares a
  `contentDescription`. Profile previews announce the resolved sound
  name + vibration pattern via TalkBack / VoiceOver. The test-fire
  button announces "Test notification posted" after sending.
- **Reduced motion**: the hub and sub-screens honor the existing
  `A11yPreferences.reduceMotion` flag — navigation transitions
  degrade to fades, the live preview button removes the pulsing
  animation.
- **High contrast skin**: `high_contrast_notifications` toggles the
  builder's color accent to a high-contrast palette and forces
  `VISIBILITY_PUBLIC` irrespective of the profile setting.
- **Hearing impaired**: silent profiles + stronger vibration + full
  screen takeover are the documented path; each "silent" profile
  automatically enables vibration unless the user explicitly opts out.
- **Font scaling**: every text node uses `MaterialTheme.typography.*`
  so the existing font scale preference propagates.
- **Keyboard navigation**: every interactive row is a `clickable`
  `Row` and therefore focusable by default; the focus order matches
  reading order.

---

## 7. Test Surface

New unit tests landed in this change set:

- `VibrationPatternsTest` — preset patterns, CSV round-trip, repetition
- `EscalationChainTest` — absolute-offsets math, tier filtering, max attempts
- `NotificationProfileResolverTest` — JSON round-trips, legacy backfill, malformed input
- `ProfileAutoSwitcherTest` — time-of-day / day-of-week / OS focus rules
- `ReminderSchedulerQuietHoursTest` — quiet-hours integration (pure helper)
- `EscalationSchedulerTest` — request-code stability / collision avoidance
- `NotificationPreferencesExtendedTest` — all v1.4.0 DataStore additions
- `CustomSoundEntityTest` — id format, parse round-trip, size/duration caps

Existing tests retained / updated:

- `NotificationProfileTest` (renamed from `ReminderProfileTest`) — built-in templates
- `DatabaseEntityRegistrationTest` — 32-entity registry including `NotificationProfileEntity` + `CustomSoundEntity` + `FocusReleaseLogEntity`
- `HiltDependencyGraphTest` — expected DAO accessor list updated

All tests are pure JUnit; no Android instrumentation required. CI will run
them on push.
