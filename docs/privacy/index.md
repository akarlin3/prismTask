---
title: Privacy Policy
description: How PrismTask collects, uses, and protects your data.
---

# PrismTask — Privacy Policy

**Effective date:** 2026-04-24
**Last updated:** 2026-04-24

## Who we are

PrismTask is developed and operated by **Avery Karlin**, a solo developer based in the United States.

- Contact for privacy requests: [privacy@prismtask.app](mailto:privacy@prismtask.app)
- Web app: [https://app.prismtask.app](https://app.prismtask.app)
- Source code: [https://github.com/akarlin3/prismTask](https://github.com/akarlin3/prismTask)

This policy describes what data PrismTask collects when you use the Android app (package ID `com.averycorp.prismtask`) or the web app at `app.prismtask.app`, how that data is used, where it is stored, and what rights you have over it.

## The short version

- PrismTask works fully offline. If you never sign in, no data ever leaves your device.
- If you sign in with Google, your tasks, habits, and related data sync across your devices via Firebase Firestore. We do not sell or share that data.
- Natural-language task parsing and AI features (Eisenhower, Pomodoro planner, daily briefing, weekly review) send the relevant text through our backend to Anthropic's Claude API. Those features are optional.
- Crash reports are collected via Firebase Crashlytics to keep the app stable. There is no advertising SDK, no analytics tracker, and no data reseller in the loop.
- You can export all of your data as JSON/CSV from the app at any time. To delete all of your synced data, email `privacy@prismtask.app` (in-app one-tap account deletion is in active development).

## What we collect

### Information you provide directly

- **Google account email and display name** when you sign in with Google Sign-In. Required only if you opt into cloud sync.
- **Content you enter in the app:** task titles, descriptions, due dates, projects, tags, subtasks, habits, habit completions, Pomodoro session metadata, mood and energy logs, morning check-in notes, weekly review notes, medication names and dose history, boundary rules, notification profile settings, custom sounds, chat messages with the in-app coaching assistant, and any other content you create.

### Information collected automatically

- **Firebase Installation ID and Crashlytics install UUID** — used by the Firebase SDKs for core operation and crash triage.
- **Crash reports and diagnostic traces** — collected via Firebase Crashlytics when the app crashes or encounters a non-fatal error. These include your Firebase UID (so we can reproduce a bug in context), the stack trace, device model, OS version, and app state at the time of the crash.
- **Purchase entitlement state** — Google Play Billing tells the app whether you are on the Free or Pro tier. PrismTask never sees card or payment details.

### Information we **do not** collect

- We do not collect your location (no `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`).
- We do not access your contacts, SMS, call history, photos, or files.
- We do not use any third-party advertising or analytics SDKs beyond Firebase Crashlytics for crash diagnostics.

## How we use your data

- **Core functionality:** store your tasks/habits/etc., run reminders and Pomodoro sessions, compute streaks and analytics, and keep everything synchronized across your signed-in devices.
- **AI-powered features (opt-in):** when you use natural-language quick-add, the Eisenhower matrix auto-classification, AI time blocking, the daily briefing, the weekly review aggregator, or the smart Pomodoro planner, the relevant text is sent through our backend to Anthropic's Claude API (Claude Haiku for fast NLP; Claude Sonnet for planner/review). Anthropic processes requests under [zero-retention terms](https://www.anthropic.com/legal). If you are signed out or AI features are disabled, the app falls back to a local regex parser and no external call is made.
- **Crash diagnostics:** Firebase Crashlytics uses crash reports to help us fix bugs.
- **Voice input (opt-in):** when you tap the microphone, Android's `SpeechRecognizer` processes your speech via Google Speech Services and returns a transcript. PrismTask does not record or retain audio.
- **Calendar sync (opt-in):** when you connect Google Calendar in Settings, the app reads and writes events to your own Google Calendar account.
- **Cross-device sync (opt-in):** when you sign in, your data is stored in Firestore keyed to your Firebase UID. Only your signed-in devices can read it.

## Where your data is stored

- **On your device:** Android Room SQLite database inside the app sandbox. The database name is `prismtask.db`.
- **Firebase Firestore:** in the Google Cloud **`nam5` multi-region** (United States). Firebase project ID: `averytask-50dc5`.
- **Firebase Auth:** Google global infrastructure.
- **Firebase Crashlytics:** Google global infrastructure; retention follows [Firebase's data retention policy](https://firebase.google.com/support/privacy).
- **PrismTask backend (FastAPI):** hosted on [Railway](https://railway.app) in the United States. The backend receives task content for NLP parsing and AI prompts for AI features, forwards them to Anthropic, and returns the structured response. Request payloads are not persisted; access logs contain only routine metadata.
- **Anthropic:** Claude API servers in the United States. Zero-retention per Anthropic's API terms.

## Third-party processors

| Processor | Role | What they see |
|---|---|---|
| Google LLC (Firebase Auth, Firestore, Crashlytics, Cloud Storage, Sign-In, Speech Services, Calendar API, Play Billing) | Authentication, cloud sync, crash diagnostics, voice transcription, calendar sync, billing | Your account email, synced content, crash traces, voice audio during recognition, Calendar events you choose to sync, purchase tokens |
| Anthropic, PBC (Claude API) | AI-feature processing | Text you submit for parsing or AI analysis (task titles, project context, prompt strings) |
| Railway Corporation | Backend hosting | HTTPS traffic in transit to the FastAPI backend |

No processor listed above is authorized to sell or resell your data. We do not share data with advertisers, data brokers, or analytics firms.

## Your rights

You can, at any time:

- **Export your data** via Settings → Data → Export. This produces a JSON file with all your entities and preferences, plus an optional CSV export of tasks.
- **Import a prior backup** via Settings → Data → Import with either merge or replace semantics.
- **Sign out** from Settings → Account to stop syncing further changes to the cloud. Previously synced data remains in Firestore until you request deletion.
- **Clear local data** via Android Settings → Apps → PrismTask → Storage → Clear storage. This wipes the on-device Room database and DataStore preferences.
- **Request deletion of your account and all synced data** by emailing [privacy@prismtask.app](mailto:privacy@prismtask.app). We will wipe your Firestore user collection and your Firebase Auth record within 30 days. An in-app one-tap account deletion is in active development and will replace this email path; this section will be updated when it ships.

If you are a resident of the EU/UK (GDPR), California (CCPA/CPRA), or another jurisdiction with data-subject rights, you also have:

- A right to access the data we hold about you.
- A right to correct inaccurate data.
- A right to portability (fulfilled by the JSON export above).
- A right to object to or restrict processing.
- The right to lodge a complaint with your local data protection authority.

To exercise any of these rights, email [privacy@prismtask.app](mailto:privacy@prismtask.app).

## Children

PrismTask is intended for users aged **18 and older**. We do not knowingly collect data from children under 18. If you believe a child under 18 has created an account, contact [privacy@prismtask.app](mailto:privacy@prismtask.app) and we will delete the account and its data.

## Data security

- All network traffic uses HTTPS/TLS.
- `network_security_config.xml` on Android denies cleartext traffic.
- Firebase Auth handles credential storage; PrismTask never sees your Google password.
- Sensitive local preferences are stored via `androidx.security:security-crypto` encrypted DataStore.
- Cloud data access is scoped to your Firebase UID via Firestore Security Rules; no other user can read or write your collection.

## Changes to this policy

When this policy changes, the "Last updated" date at the top of this page changes and a new entry is added to the changelog below. We will not materially expand data collection without updating this page; if we ever do, we will notify active users in-app.

## Changelog

_No revisions yet. Initial version published 2026-04-24._
