# D-Series Beta-Tester Unlock Codes — Phase 1 Audit

**Scope:** Ship beta-tester Pro-unlock codes — backend Postgres table + admin
CLI + FastAPI redemption endpoint + Android code-entry UI + tier-gating
wire-up so redeemed accounts get Pro features for the code's TTL (or
perpetually if no TTL set), all in a single PR.

**Operator decisions (locked):** Bucket 4 only (Bucket 1 broader trial-extension
deferred), account-bound redemption (one redemption per `(code, account)`,
N accounts may redeem the same code), backend-validated, per-code expiration
(TTL or perpetual), admin CLI in scope, single PR, D-series launch-gate
placement.

**Risk acknowledgement:** Operator accepts launch-slip risk, multi-surface
single-PR risk, and the tier-gating + backend-infra premise unknowns
documented below.

---

## Recon findings (A.1–A.5)

### A.1 Drive-by detection — clean

```
git log --all --oneline -S "coupon" / "unlock_code" / "redeem" / "tester_code"  →  no matches
git log --all --oneline -S "entitlement"  →  4 hits, all billing/RTDN-audit work (PRs #920, #921, #891)
```

No prior coupon, unlock-code, redemption, or beta-tester scaffolding.
Greenfield.

### A.2 Parked-branch sweep — clean

`git branch -a | grep -iE "coupon|unlock|entitlement|trial.code|beta.code|tester|redeem"`
returns only this audit's branch. No in-flight work.

### A.3 Tier-gating recon (LOAD-BEARING) — server-authoritative pattern exists

**Backend** (`backend/app/models.py:118-123`):

```python
class User(Base):
    tier = Column(String(20), nullable=False, server_default="FREE")
    is_admin = Column(Boolean, nullable=False, default=False, server_default="0")

    @property
    def effective_tier(self) -> str:
        if self.is_admin:
            return "PRO"
        return self.tier or "FREE"
```

`/api/v1/auth/me` returns `effective_tier` (`backend/app/routers/auth.py:183`,
schema `backend/app/schemas/auth.py:39-45`). This is the **single
aggregation point**. Adding beta-code logic = extending this property +
keeping the rest of the chain unchanged.

**Android client** (`app/.../data/billing/BillingManager.kt:79-80, 322-344`):

The client tier signal is a `StateFlow<UserTier>` of `{FREE, PRO}` —
**boolean only**. There is, however, an `_isAdmin: StateFlow<Boolean>`
override channel: `applyEffectiveTier()` forces `PRO` when `_isAdmin.value`
is true regardless of the underlying Google Play state.

`BackendSyncService.checkAdminStatus()`
(`app/.../data/remote/sync/BackendSyncService.kt:124-142`) already calls
`/auth/me`, parses `isAdmin`, and feeds it to
`billingManager.setAdminStatus(userInfo.isAdmin)`. The pipe from server
to client is plumbed.

**`UserInfoResponse`** (`app/.../data/remote/api/ApiModels.kt:40-47`)
already deserializes `effective_tier` (string) — but nothing on the
client currently reads that field; only `isAdmin` is consumed.

**Verdict on STOP-B:** Does NOT fire. Server-side time-bounded entitlement
is supported via the `effective_tier` property — beta-code logic can be
added there with `valid_until` checks against `redemption.grants_pro_until`.
Client side does NOT need a local TTL countdown: it polls `/auth/me` per
session, and when the server-side window expires, `effective_tier`
returns to `FREE` automatically. **No new entitlement system required.**

The `_isAdmin` override pattern is the architectural template for adding
a parallel `_isBetaPro` lever (≤30 LOC, mirrors existing setter).

`ProStatusPreferences.tierExpiresAt` exists but is wired to Google Play
renewal windows (30d / 365d) — repurposing it would conflate billing
state with beta-code state. Cleaner to add a separate beta-pro signal.

### A.4 Backend infrastructure recon — full pattern set exists

| Premise | Evidence | Status |
|---|---|---|
| Alembic migration framework | `backend/alembic/versions/`, 23 migrations, latest `022_add_rtdn_events.py` | GREEN |
| Admin-auth pattern | `backend/app/middleware/admin.py` defines `require_admin`, used in `routers/admin/{activity_logs,debug_logs}.py` and `routers/feedback.py` | GREEN |
| Admin endpoint convention | Routers under `app/routers/admin/` with `prefix="/admin/<resource>"`, `tags=["admin"]` | GREEN |
| CLI script convention | `/scripts/set_admin.py` (argparse + asyncio + SQLAlchemy, sets `User.is_admin = True`) | GREEN |
| User identity | `User.id: Integer` is the FK target on every user-keyed table; `firebase_uid: String(255)` is the Firebase mapping but not used as FK anywhere | GREEN |
| Existing entitlement source | `backend/app/services/billing.py:30-33` validates Google Play purchase tokens against `PRODUCT_TIER_MAP`; `services/billing.py` is THE entitlement validator | GREEN |
| RTDN audit trail | `rtdn_events` table (migration 022) — append-only audit, not in scope here | INFO |

**Verdict on STOP-C:** Does NOT fire. CLI convention is established
(`/scripts/`, argparse, asyncio + SQLAlchemy session via
`settings.DATABASE_URL`). Beta-codes CLI mirrors `scripts/set_admin.py`.

**Verdict on STOP-D:** Does NOT fire. Postgres is the entitlement store
(`User.tier`, `User.is_admin`); no Firestore/Firebase entitlement state.

### A.5 Sibling-primitive sweep — empty

```
grep -rn "referral|promo_code|gift_card|voucher|invitation_code"
  app/src/main/java/com/averycorp/prismtask/ backend/app/  →  no matches
```

No existing redemption primitive to extend. **STOP-E does not fire.**
Greenfield as a primitive — operator-locked at Bucket 4.

---

## Premise verification (D.1–D.5)

| ID | Premise | Outcome |
|---|---|---|
| D.1 | Pro tier exists, tier-gating exists | **CONFIRMED.** `UserTier.{FREE,PRO}` enum + `ProFeatureGate` use-case + `User.tier` column. |
| D.2 | Backend is FastAPI + Postgres + Alembic | **CONFIRMED.** `backend/app/main.py`, 23 alembic migrations. |
| D.3 | Backend uses Firebase UIDs as identity | **REFINED.** `User.firebase_uid` exists for sign-in linking, but `User.id: Integer` is the FK across all user-keyed tables. **Beta redemptions FK to `users.id`, not Firebase UID.** |
| D.4 | No prior coupon/unlock-code work | **CONFIRMED.** Drive-by + branch sweep clean. |
| D.5 | Time-bounded access supported in tier-gating | **CONFIRMED via server-authoritative pattern.** `effective_tier` is a single Python property; extending it with a `valid_until > now()` check on a new `beta_code_redemptions` join is the smallest delta. Client trusts the result. |

**STOP-and-report status: clean.** No premise turned out wrong. Proceeding.

---

## Implementation hypothesis verdicts

### B.1 Postgres schema (GREEN, with corrections vs prompt's straw schema)

Corrections vs prompt B.1:
- `code TEXT` → `VARCHAR(64)` (matches existing string-PK convention).
- `valid_from / valid_until / grants_pro_until / redeemed_at / created_at`
  must be `DateTime(timezone=True)` (post-migration 018 convention; mixing
  naive timestamps in this codebase fails the `_as_utc` normalization
  guard noted in `backend/app/routers/auth.py:47-54`).
- Redemption FK target is `users.id INTEGER` (not Firebase UID) — every
  existing user-keyed table uses this FK; consistency wins.
- BIGSERIAL → `Integer` autoincrement primary key (matches every other
  table's PK type in `models.py`).
- Add an index on `beta_code_redemptions.user_id` so the
  `effective_tier` lookup ("does this user have an active redemption?")
  hits an index, not a full scan.

Final shape (will be Alembic 023 — note 023 is already taken by
`023_add_task_progress_percent.py`, so this lands as **024**):

```sql
CREATE TABLE beta_codes (
    code VARCHAR(64) PRIMARY KEY,
    description VARCHAR(500),
    valid_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    valid_until TIMESTAMPTZ,                  -- NULL = open-ended
    grants_pro_until TIMESTAMPTZ,             -- NULL = perpetual Pro
    max_redemptions INTEGER,                  -- NULL = unlimited
    redemption_count INTEGER NOT NULL DEFAULT 0,
    revoked_at TIMESTAMPTZ,                   -- NULL = active
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE beta_code_redemptions (
    id SERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL REFERENCES beta_codes(code),
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    redeemed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    grants_pro_until TIMESTAMPTZ,             -- snapshot at redemption
    UNIQUE (code, user_id)
);
CREATE INDEX idx_beta_code_redemptions_user_id
  ON beta_code_redemptions(user_id);
```

### B.2 FastAPI redemption endpoint (GREEN)

New router: `backend/app/routers/beta_codes.py`, mounted under `/api/v1`.

```
POST /api/v1/beta/redeem
  body: { "code": "EARLY-BIRD-2026" }
  auth: Depends(get_current_user)              # Firebase JWT, NOT require_ai_features_enabled
  response 200: { "granted": true, "pro_until": "2026-06-15T..." | null }
  errors:
    400 — code does not exist
    400 — code revoked OR code outside valid_from..valid_until window
    409 — already redeemed by this user
    410 — redemption_count >= max_redemptions
```

Auth: `Depends(get_current_user)`, NOT `require_ai_features_enabled`
(that's an AI-gate concern, separate). Concurrency: wrap the
`UPDATE beta_codes SET redemption_count = redemption_count + 1`
in the same transaction as the `INSERT INTO beta_code_redemptions` so a
race between two simultaneous redemptions can't blow past `max_redemptions`.
Use `SELECT ... FOR UPDATE` on the code row.

### B.3 Admin CLI (GREEN)

`/scripts/beta_codes.py` mirroring `/scripts/set_admin.py` shape: argparse
subcommands, `asyncio.run`, `create_async_engine(settings.DATABASE_URL)`.

```
python scripts/beta_codes.py issue --code "EARLY-BIRD-2026" \
       --description "Closed beta cohort 1" \
       --valid-until 2026-06-15 \
       --grants-pro-until 2026-09-01 \
       --max-redemptions 50

python scripts/beta_codes.py list [--active-only]
python scripts/beta_codes.py revoke --code "EARLY-BIRD-2026"
```

Revoke = set `revoked_at = NOW()`. Existing redemptions stay valid
(per anti-pattern: do not introduce revocation cascades).

### B.4 Android UI (YELLOW — Compose verbosity calibration)

New screen: `ui/screens/settings/BetaCodeRedemptionScreen.kt`. Reachable
from `SettingsScreen.kt` via a new `SettingsNavRow` (likely placed under
the existing Subscription section).

Single text field + "Redeem" button + state machine
(idle / loading / success / error). Account-bound: hide the row entirely
if `authManager.currentUser` is null (the API call would 401).

LOC estimate (with PR #1097's 2× Compose calibration):
- Screen Composable: ~180 LOC
- ViewModel + state machine: ~80 LOC
- Settings nav row + entry point: ~15 LOC

### B.5 Tier-gating wire-up (GREEN — best path is server-authoritative)

Server: extend `User.effective_tier` to be async-aware OR provide a
parallel `compute_effective_tier(user, db)` function that does:

```python
if user.is_admin: return "PRO"
if active_beta_redemption_exists(user.id, db): return "PRO"
return user.tier or "FREE"
```

`active_beta_redemption_exists` = `EXISTS (SELECT 1 FROM beta_code_redemptions WHERE user_id = $1 AND (grants_pro_until IS NULL OR grants_pro_until > NOW()))`.

Note: the current `effective_tier` is a sync `@property`. Either (a) keep
it for the admin-only fast path and compute the beta-code check inside
the `/auth/me` handler before serializing, OR (b) change it to a service-
layer function. Option (a) is the smaller diff; recommend it.

Client: extend `BackendSyncService.checkAdminStatus()` to also derive a
beta-pro signal from `effective_tier` vs `is_admin`:

```kotlin
val info = api.getMe()
billingManager.setAdminStatus(info.isAdmin)
val betaPro = info.effectiveTier == "PRO" && !info.isAdmin
                  && info.tier == "FREE"
billingManager.setBetaProStatus(betaPro)
```

Add `_isBetaPro: StateFlow<Boolean>` lever in `BillingManager` mirroring
the `_isAdmin` lever; `applyEffectiveTier()` checks both.

### B.6 Sync wiring (GREEN — reuses existing `/auth/me` polling)

No new sync infra. Server is authoritative; the existing
`checkAdminStatus()` call (already invoked on app launch + post-sign-in
per `BackendSyncService.kt:115-122`) carries the new beta-pro signal.
The Android-side cache (`ProStatusPreferences.tierExpiresAt`) is NOT
extended — beta-pro state is server-checked per session, not cached
locally with a TTL.

### B.7 Test coverage (GREEN — enumerated)

Backend (`backend/tests/test_beta_codes.py`, new):
- `test_redeem_happy_path_with_ttl` — sets `grants_pro_until`, verifies effective_tier=PRO before, FREE after expiry.
- `test_redeem_happy_path_perpetual` — `grants_pro_until=NULL`, effective_tier=PRO indefinitely.
- `test_redeem_unknown_code_400`
- `test_redeem_revoked_code_400`
- `test_redeem_outside_valid_window_400` (before `valid_from` AND after `valid_until`)
- `test_redeem_double_redemption_409`
- `test_redeem_max_redemptions_410`
- `test_redeem_concurrent_does_not_exceed_max` — race regression; uses asyncio gather + `FOR UPDATE`.
- `test_effective_tier_includes_beta_pro` — extends existing `test_admin_effective_tier.py`.
- `test_effective_tier_admin_overrides_expired_beta` — admin still PRO even after beta expiry.

CLI (`backend/tests/test_beta_codes_cli.py`, new):
- `test_cli_issue_creates_code`
- `test_cli_list_active_only_filters_revoked`
- `test_cli_revoke_marks_revoked_at` — and verifies existing redemptions still grant PRO.

Android (`app/src/test/.../BetaCodeViewModelTest.kt`, new):
- State-machine transitions: idle → loading → {success | error variants per HTTP code}.
- Mocked API per error path (400 unknown, 400 revoked, 409 already, 410 capped, network failure).

### Aggregate LOC estimate

| Surface | LOC |
|---|---:|
| Alembic migration 024 | ~80 |
| `models.py` additions (BetaCode, BetaCodeRedemption) | ~40 |
| `effective_tier` extension + `/auth/me` handler tweak | ~25 |
| New `routers/beta_codes.py` | ~140 |
| New `services/beta_codes.py` (redeem logic, FOR UPDATE) | ~80 |
| Backend tests (endpoint + CLI + effective_tier) | ~280 |
| `scripts/beta_codes.py` CLI | ~140 |
| Android `ApiModels.kt` (RedeemRequest/Response) | ~20 |
| Android `PrismTaskApi.kt` (one new endpoint) | ~5 |
| Android `BillingManager` `_isBetaPro` lever | ~30 |
| Android `BackendSyncService` extension | ~15 |
| Android `BetaCodeRepository` | ~50 |
| Android `BetaCodeViewModel` + state machine | ~80 |
| Android `BetaCodeRedemptionScreen` (Compose, 2× cal) | ~180 |
| Android `SettingsScreen` nav row + strings | ~25 |
| Android tests (ViewModel, state machine) | ~120 |
| **Total** | **~1310** |

Below STOP-F's ~1500 ceiling. Single-PR feasible per operator
pre-confirmation.

---

## STOP-conditions evaluated

| STOP | Trigger | Status |
|---|---|---|
| A | Drive-by surfaces prior unlock work | NOT FIRED — clean. |
| **B** | Tier-gating supports only boolean access (no time-bounded) | **NOT FIRED** — server-authoritative `effective_tier` provides time-bounded access without client-side changes. |
| C | No admin-auth / no Alembic / no CLI convention | NOT FIRED — all three exist. |
| D | Entitlements live in Firestore, not Postgres | NOT FIRED — `User.tier` + Postgres. |
| E | Sibling redemption primitive should be extended | NOT FIRED — none exist. |
| F | Aggregate Phase 2 LOC > ~1500 | NOT FIRED — estimate ~1310. |
| G | Active PRs touching tier-gating / `*PreferencesSyncService` | Clear at audit time. Re-check pre-merge. |

---

## Memory #15 conventions check

Memory #15 (orthogonal-dim shipping): new sync-relevant primitive ships
server + client + sync wiring in one PR. This audit's scope honors that:
backend table + endpoint + CLI + Android UI + tier-gating wire-up all in
one branch. Web UI is **explicitly excluded** (operator-locked, not a
quad-sweep miss — there is no web tier-gating surface that needs the
beta-pro signal: web is read-only mirror of Android Pro features per
existing parity convention).

---

## Phase 2 scope (auto-fires)

Phase 2 implements every PROCEED below. No checkpoint. PR `feat/beta-tester-unlock-codes`
on a fresh worktree branched from latest `main`.

| # | Item | Files | Verdict |
|---|---|---|---|
| 1 | Alembic migration 024 (`beta_codes` + `beta_code_redemptions`) | `backend/alembic/versions/024_add_beta_codes.py` | PROCEED |
| 2 | SQLAlchemy models | `backend/app/models.py` | PROCEED |
| 3 | `effective_tier` extension + `/auth/me` handler tweak | `backend/app/models.py`, `backend/app/routers/auth.py` | PROCEED |
| 4 | Redemption service helper (FOR UPDATE concurrency) | `backend/app/services/beta_codes.py` (new) | PROCEED |
| 5 | Redemption router + schema | `backend/app/routers/beta_codes.py`, `backend/app/schemas/beta_codes.py` (new); register in `main.py` | PROCEED |
| 6 | Backend tests (endpoint + effective_tier + CLI) | `backend/tests/test_beta_codes.py`, `backend/tests/test_beta_codes_cli.py` (new) | PROCEED |
| 7 | Admin CLI script | `scripts/beta_codes.py` (new) | PROCEED |
| 8 | Android API models + endpoint | `data/remote/api/ApiModels.kt`, `data/remote/api/PrismTaskApi.kt` | PROCEED |
| 9 | `BillingManager._isBetaPro` lever + `applyEffectiveTier` | `data/billing/BillingManager.kt` | PROCEED |
| 10 | `BackendSyncService.checkAdminStatus` extension | `data/remote/sync/BackendSyncService.kt` | PROCEED |
| 11 | `BetaCodeRepository` | `data/repository/BetaCodeRepository.kt` (new) | PROCEED |
| 12 | `BetaCodeViewModel` + state machine | `ui/screens/settings/BetaCodeViewModel.kt` (new) | PROCEED |
| 13 | `BetaCodeRedemptionScreen` (Compose) | `ui/screens/settings/BetaCodeRedemptionScreen.kt` (new) | PROCEED |
| 14 | Settings nav row + nav graph entry + strings | `ui/screens/settings/SettingsScreen.kt`, `ui/navigation/NavGraph.kt`, `res/values/strings.xml` | PROCEED |
| 15 | Android unit tests | `app/src/test/.../BetaCodeViewModelTest.kt` (new) | PROCEED |

---

## Open questions for operator

1. **Code-redemption surface placement.** Does the entry point belong
   under Settings → Subscription (tightest fit, keeps tier-related actions
   colocated) or Settings → About (since beta tester ≈ closed-program
   identity)? Recommendation: Subscription section, immediately below the
   debug-tier override.

2. **Admin CLI hosting.** `scripts/beta_codes.py` will be runnable
   locally against `DATABASE_URL` — the same way `scripts/set_admin.py`
   is. Production runs go via Railway shell. No CI exposure needed
   unless operator wants smoke validation in `backend-ci`. Recommendation:
   no CI hook; pre-launch admin tooling.

3. **Code-format validation.** Anti-pattern list says "do not validate
   code format." Confirming: codes accepted as any non-empty string up
   to `VARCHAR(64)` (which fits `EARLY-BIRD-2026`-style codes plus much
   longer admin-issued tokens). Operator confirms?

---

## Deferred (NOT auto-filed)

- **Bucket 1 broader trial-extension** — re-trigger: operator requests
  trial-period UI work post-launch.
- **Web UI for code entry** — re-trigger: operator wants beta-tester
  unlock on web; currently web has no tier-gating surface needing it.
- **Per-code analytics / telemetry on redemptions** — anti-pattern list
  excludes; re-trigger: operator wants redemption funnel metrics.
- **Rate limiting on `/beta/redeem`** — anti-pattern list excludes; beta
  scale doesn't need it. Re-trigger: post-launch abuse signal.
- **Code-revocation cascade** — anti-pattern excludes; re-trigger: legal
  request to revoke specific user's access.

---

## Anti-patterns flagged

- **Do not** repurpose `ProStatusPreferences.tierExpiresAt` for beta-code
  TTL. That field is wired to Google Play renewal windows; conflating
  the two state machines guarantees a bug where billing-renewal logic
  steps on beta-code state (or vice versa). Add a separate signal.
- **Do not** elevate `User.tier` itself to "PRO" on redemption. The
  `tier` column is the Google-Play-validated state; mutating it from a
  beta-code path breaks the paid-vs-promotional distinction the billing
  audit (`docs/audits/BILLING_SCHEMA_AUDIT.md`) was careful to preserve.
  Beta-pro is computed via `effective_tier`, not stored as `tier`.
- **Do not** skip the `SELECT ... FOR UPDATE` on `beta_codes` in the
  redeem path. Without it, two concurrent redemptions can both observe
  `redemption_count = max_redemptions - 1` and both succeed.
- **Do not** make the Android client trust a locally cached
  `grants_pro_until` for offline tier inference. When the user is
  offline post-expiry, the lever should silently fall back to whatever
  Google Play says. Trust the server on every connected fetch.
- **Do not** put the redeem endpoint behind `require_ai_features_enabled`
  or any AI-gate. Beta-tester onboarding precedes AI feature usage.
- **Do not** cascade revocation onto existing redemptions. Revoke marks
  the code unusable for *future* redemptions only; current redeemers
  keep their grant until `grants_pro_until`.

---

## Phase 2 — Implementation (auto-fires after this Phase 1 lands)

Per audit-first convention, Phase 2 fires automatically. Branch:
`feat/beta-tester-unlock-codes` (worktree off latest `main`). PR opens
ready-for-review. Phase 3 + 4 emit pre-merge per CLAUDE.md.
