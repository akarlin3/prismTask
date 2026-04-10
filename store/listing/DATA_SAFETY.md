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
- **What data:** Task titles, descriptions, and metadata
- **Purpose:** AI-powered task categorization (Eisenhower) and focus planning (Pomodoro)
- **User control:** Users can opt out by not using AI features (Pro-only features)
- **Data processing:** Processed by Anthropic's API, subject to Anthropic's data retention policy

### Firebase (Google)
- **What data:** Email, task data (when cloud sync is enabled)
- **Purpose:** Authentication and cross-device sync
- **User control:** Users can use the app fully offline without Firebase

## Security Practices
- Data encrypted in transit: **Yes** (HTTPS for all network communication)
- Data can be deleted: **Yes** (account deletion removes all cloud data)
- Data can be exported: **Yes** (JSON/CSV export feature built into the app)

## Additional Notes
- The app works fully offline — no data leaves the device unless the user explicitly enables cloud features
- AI features are opt-in (Pro subscription required)
- No advertising SDKs or tracking pixels
- No data sold to third parties
