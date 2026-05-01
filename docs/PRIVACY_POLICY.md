# Privacy Policy

**PrismTask**
**Developer:** AveryCorp (Avery Karlin)
**Contact:** privacy@prismtask.app
**Effective Date:** 2026-05-01

---

## Introduction

This Privacy Policy describes how AveryCorp ("we," "us," or "our") collects, uses, and protects your information when you use the PrismTask mobile application ("the App"). We are committed to protecting your privacy and being transparent about our data practices.

## Information We Collect

### Account Data

When you sign in with Google, we collect:

- **Email address** — used for authentication, cloud sync, and collaboration features
- **Display name** — used for identification in shared projects and collaboration

Account creation is optional. You can use the App fully offline without creating an account.

### Task Data

- **Tasks, projects, tags, subtasks, habits, and templates** — the content you create within the App
- This data is stored locally on your device by default
- If you enable cloud sync (Pro feature), this data is also stored on our servers

### Usage Data

- **App usage patterns** — used locally for smart suggestions (tag and project recommendations based on your habits)
- **AI feature interactions** — when you use AI features (Pro), task titles, descriptions, and metadata are sent to our AI provider for processing

### Calendar Data

- **Google Calendar events** — read and written with your explicit permission via the Google Calendar API, used for calendar sync functionality

## How We Store Your Data

### Local Storage

- All task data is stored in a local Room database on your device
- This is the primary storage method and works fully offline
- Local data is encrypted via Android's default device encryption

### Cloud Storage (Pro Feature)

- **Firebase Firestore** — used for cross-device sync when you enable cloud sync
- **Firebase Authentication** — used for secure account management
- **Google Drive** — used for backup/restore when you initiate it (Pro feature)

### Backend Server

- **FastAPI server hosted on Railway** — used for AI features (task parsing, Eisenhower categorization, Pomodoro planning), collaboration, and template sync
- Only accessed when you use Pro features that require server communication

## Third-Party Services

We use the following third-party services:

### Firebase (Google)

- Authentication and cloud sync
- Subject to [Google's Privacy Policy](https://policies.google.com/privacy)

### Anthropic (Claude AI)

- Task parsing, Eisenhower categorization, Pomodoro planning, daily/weekly briefings, time-blocking, batch NLP commands, conversation extraction, syllabus parsing, and Gmail-to-task extraction (all Pro features)
- The following data is sent to Anthropic's API when you use the corresponding feature:
  - Task titles, descriptions, due dates, priorities, projects, tags, and life-category labels
  - Habit names and project names referenced in AI prompts
  - Medication names (id + name only — not dosage, frequency, or prescriber) when you invoke an AI batch NLP command on a medication (e.g. "Skip my Adderall today")
  - Free text you submit to AI surfaces (natural-language commands, syllabus PDF text, conversation extraction text)
  - **Gmail integration only:** when you trigger a Gmail inbox scan (Pro + opt-in), email subjects, snippets, sender addresses, and message dates from the inbox window you scanned are sent to Anthropic for task extraction. Email bodies and attachments are not sent.
- Anthropic does not train its models on inputs (Anthropic Commercial Terms § B). Anthropic standard API retention is 30 days, extending up to 2 years if a request is flagged for Trust & Safety review
- You can disable all Anthropic processing in Settings → AI Features → "Use Claude AI for advanced features." When disabled, the app makes no Anthropic calls and the AI-powered features (including Gmail scan) become unavailable
- Subject to [Anthropic's Privacy Policy](https://www.anthropic.com/privacy)

### Google Calendar API

- Two-way calendar event sync with your explicit permission
- Subject to [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy)

### Railway

- Backend server hosting for AI features and collaboration
- Subject to [Railway's Privacy Policy](https://railway.app/legal/privacy)

## Data Sharing

- **Collaboration:** Task data in shared projects is visible to project members you invite
- **AI processing:** Task metadata, medication names (for batch NLP commands), and — when you opt into Gmail integration — email subjects/snippets/sender addresses are sent to Anthropic for AI features (Pro only)
- **We do not sell your data to third parties**
- **We do not use your data for advertising**
- **We do not share your data with third parties for their marketing purposes**

## Your Rights

You have the following rights regarding your data:

- **Export your data** — use the built-in JSON/CSV export feature to download all your data at any time
- **Delete your account** — delete your account and all associated cloud data from within the App
- **Revoke Google Calendar access** — remove calendar permissions at any time through your Google Account settings
- **Use offline** — use the App fully offline without any data leaving your device
- **Opt out of AI features** — AI features are optional; your data is only sent to Anthropic when you actively use AI features

## Children's Privacy

PrismTask is not directed at children under the age of 13. We do not knowingly collect personal information from children under 13. If we become aware that we have collected personal information from a child under 13, we will take steps to delete that information promptly. If you believe we have collected information from a child under 13, please contact us at privacy@prismtask.app.

## Data Security

We take reasonable measures to protect your information:

- **JWT authentication** for secure API access
- **HTTPS** for all network communication
- **Local data encryption** via Android's default device encryption
- **Firebase security rules** to protect cloud-stored data

## Data Retention

- **Local data** is retained on your device until you delete it or uninstall the App
- **Cloud data** is retained until you delete your account or request deletion
- **AI processing data** is subject to Anthropic's data retention policy

## Changes to This Policy

We may update this Privacy Policy from time to time. We will notify you of any material changes by posting the new Privacy Policy within the App or via email. Your continued use of the App after changes are posted constitutes your acceptance of the updated policy.

## Governing Law

This Privacy Policy is governed by the laws of the State of New York, United States, without regard to its conflict of law provisions.

## Contact Us

If you have questions or concerns about this Privacy Policy or our data practices, please contact us at:

**Email:** privacy@prismtask.app
**Developer:** AveryCorp (Avery Karlin)
