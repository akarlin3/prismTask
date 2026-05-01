# Data Safety Form Notes

Use these answers when filling out the Google Play Console data safety form.

## Overview
- **Does the app collect or share any user data?** Yes
- **Is all collected data encrypted in transit?** Yes (HTTPS)
- **Can users request data deletion?** Yes (account deletion feature in-app)

## Data Types Collected

### Personal Information
| Data Type | Collected | Shared | Purpose | Optional |
|-----------|-----------|--------|---------|----------|
| Email address | Yes | No | Account creation, authentication, collaboration invites | Required for sync/collaboration features |
| Name | Yes | No | Display name in shared projects | Optional |

### Health & Wellness (v1.4.0+)
| Data Type | Collected | Shared | Purpose | Optional |
|-----------|-----------|--------|---------|----------|
| Mood & energy ratings | Yes | No | Mood analytics, correlation with productivity; stored on-device only | Yes — feature opt-in |
| Medication tracking data | Yes | No | Refill forecasting and adherence reminders; stored on-device only | Yes — feature opt-in |
| Morning check-in responses | Yes | No | Daily wellness context; stored on-device only | Yes — feature opt-in |
| Clinical report content | No | No | User-initiated export only; never transmitted without explicit share action | N/A |

All health and wellness data is stored exclusively on-device in the local Room
database. None of this data is transmitted to Firebase, the FastAPI backend, or
Anthropic. The clinical report export is a user-initiated action (explicit share
intent) and is never automatically uploaded.

### App Activity
| Data Type | Collected | Shared | Purpose | Optional |
|-----------|-----------|--------|---------|----------|
| App interactions | Yes | No | Local usage analytics for smart suggestions | Not transmitted off device |
| In-app search history | No | No | N/A | N/A |

### App Info and Performance
| Data Type | Collected | Shared | Purpose | Optional |
|-----------|-----------|--------|---------|----------|
| Crash logs | No | No | N/A | N/A |
| Diagnostics | No | No | N/A | N/A |

### Financial Info
| Data Type | Collected | Shared | Purpose | Optional |
|-----------|-----------|--------|---------|----------|
| Purchase history | Yes (via Google Play) | No | Subscription management | Managed entirely by Google Play Billing |

## Data Shared with Third Parties

### Anthropic (Claude AI)
- **What data:** Task titles, descriptions, and metadata; medication names (id + name only — not dosage, frequency, or prescriber) when AI batch NLP commands operate on a medication; for Gmail integration only, email subjects/snippets/sender addresses/dates from the inbox window the user scans (email bodies and attachments are not sent)
- **Purpose:** AI-powered task categorization (Eisenhower), focus planning (Pomodoro), NLP parsing, AI briefing/planner features, and Gmail-to-task suggestion extraction
- **User control:** Users can opt out via Settings → AI Features → "Use Claude AI for advanced features." When off, the Android client and the FastAPI backend both block Anthropic-touching endpoints (including Gmail scans) with HTTP 451
- **Data processing:** Processed by Anthropic's API, subject to Anthropic's data retention policy (30 days standard, up to 2 years if flagged for Trust & Safety review)
- **Health data:** Mood, energy, dose history, and check-in data is **never** sent to Anthropic (medication *names* are, only when invoked via batch NLP commands)

### Firebase (Google)
- **What data:** Email, task and habit data (when cloud sync is enabled)
- **Purpose:** Authentication and cross-device sync
- **User control:** Users can use the app fully offline without Firebase

## Security Practices
- Data encrypted in transit: **Yes** (HTTPS for all network communication)
- Data can be deleted: **Yes** (account deletion removes all cloud data)
- Data can be exported: **Yes** (JSON export v5 with full entity set; CSV tasks export)
- Health/wellness data: stored on-device only; not included in cloud sync

## Additional Notes
- The app works fully offline — no data leaves the device unless the user explicitly enables cloud features
- AI features are opt-in (Pro subscription required)
- Wellness features (mood, check-in, medication, clinical report) are entirely on-device
- No advertising SDKs or tracking pixels
- No data sold to third parties
