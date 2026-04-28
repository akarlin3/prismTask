# Billing schema — audit (proposed migration #020 → re-anchored to #022,
# but the deeper finding is that the premise is wrong)

**Scope.** Audit the proposed alembic migration #020 for Google Play
subscription support: four tables (`play_purchase_tokens`,
`subscriptions`, `entitlements`, `rtdn_events`) plus the four PG enum
types behind them. Hard constraints: additive-only, idempotent,
RTDN-replayable, and reversible.

**Branch / worktree.** Originally proposed as
`feat/billing-schema-020`. **Not created** — Phase 1 hit the
wrong-premise STOP rule before any schema work began.

---

## Top-line: wrong premise — STOP-and-report

The audit prompt frames this as "Phase 1 (Play Console + GCP/Pub/Sub)
is complete. **This migration is the first code change of the billing
initiative**." Three independent findings disprove that framing:

1. **`backend/app/services/billing.py` (171 lines) already exists**
   and already verifies Google Play purchase tokens server-side via
   `androidpublisher` v3's `purchases.subscriptionsv2.get(packageName,
   token)`. It uses the *exact* package name (`com.averycorp.prismtask`)
   and the *exact* product IDs (`prismtask_pro_monthly`,
   `prismtask_pro_annual`) the prompt captures from Phase 1.
2. **`PATCH /auth/me/tier` already routes through it** — see
   `backend/app/routers/auth.py:168` (`update_tier`) which calls
   `validate_purchase(claimed_tier, purchase_token, product_id)` and
   only writes `current_user.tier = result.validated_tier` on `ok=True`.
   This is the "Phase 3 verify endpoint" the prompt proposes — already
   shipped, at least its v1.
3. **The entitlement record already exists denormalized on the user
   row.** `users.tier String(20)` (`models.py:~99`) plus `is_admin
   Boolean` plus the `User.effective_tier` property (admin → PRO,
   else → tier-or-FREE) is the current entitlement source-of-truth.
   The proposed `entitlements` table is **not** an additive extension
   of an empty design space — it's a *new parallel pattern* that would
   sit next to and conflict with the existing column.

This matches investigation item 6 verbatim: *"Phase 1 said this is
greenfield, but per memory #14 ('architectural memory goes stale
within days'), audit-verify rather than assume. If anything exists,
surface it."* So: surfacing.

The audit-first hard rule says wrong-premise STOP is "the one real
halt." Phase 2 fan-out **does not auto-fire** from this audit. The
user picks the next move (multiple-choice block at the bottom).

---

## Phase 1 — verified items

### Item 1 — Alembic chain state (RED — slot 020 already taken)

**Finding.** Current head is **`021_drop_medication_marks.py`**
(`revision = "021"`, `down_revision = "020"`, Create Date 2026-04-25,
PR #782, commit `dfbc1695`). Slot 020 is already taken by
`020_add_user_deletion_fields.py` (`revision = "020"`,
`down_revision = "019"`). Migration 019 is
`019_add_medication_entities_and_audit.py` (`revision = "019"`,
`down_revision = "018"`, 2026-04-23, PR #772 cited in prompt is
correct).

The prompt's "most recent migration: alembic 019" is wrong by two
revisions. Any new billing migration would land at slot **022** with
`down_revision = "021"`.

**Filename convention.** `<NN>_<snake_case_slug>.py` — zero-padded
two-digit slot, underscore separator, no hyphens, terse imperative
slug ("add_X", "drop_X", "migrate_X"). Revision strings are *also*
zero-padded ("019", "020", "021") — not slug-prefixed. Captured for
Phase 2 if it ever fires.

**Verdict.** RED on slot number. The slot fix is mechanical; the
deeper finding is the wrong-premise above, not this.

### Item 2 — `users.id` column type (GREEN)

**Finding.** `users.id = Column(Integer, primary_key=True)` —
autoincrement Integer, NOT UUID. Captured at `models.py:~85`. All FK
columns in the codebase (medications, habits, tasks, etc.) use
`Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False,
index=True`. Any new billing FK to `users.id` must follow that exact
shape.

**Verdict.** GREEN. No surprises; the prompt's listed alternatives
(UUID, BigInteger) are not in use.

### Item 3 — `Base` and model conventions (GREEN, with one caveat)

**Finding.**
- `Base` is declared inline at the top of `models.py` as
  `class Base(DeclarativeBase): pass` — no `from app.db.base_class
  import Base` indirection. Any new model must `from app.models import
  Base` (or be added to that same file).
- **All models live in one file** (`backend/app/models.py`, 677
  lines). There is no `app/models/` package, no domain split, no
  per-feature model file. The "single file" decision is load-bearing;
  splitting it now would be a separate refactor PR.
- `__tablename__` is **plural snake_case** (`users`, `medications`,
  `medication_slots`, `task_completions`).
- **No `TimestampMixin`** — every model inlines `created_at` and
  `updated_at` columns. Repetitive but consistent.

**Caveat.** Adding 4 new models to `models.py` pushes it from 677 to
~870+ lines. Memory #28 caps CLAUDE.md at 1500 lines but does not
explicitly cap `models.py`; the project convention is "one file" and
doesn't suggest splitting.

**Verdict.** GREEN.

### Item 4 — Enum convention verification (GREEN)

**Finding.** Six existing enum columns in `models.py` (lines 119, 138,
176, 239, 392, 406) use the exact pattern memory #13 prescribes:

```python
sa.Column(
    "field",
    sa.Enum(MyEnum, values_callable=lambda x: [e.value for e in x],
            name="my_enum_pg_type"),
    nullable=False,
)
```

Migration 019 also creates PG enum types via the same shape (e.g.
`medication_tier_states.tier` is a `String(20)` *not* a PG enum —
captured for completeness; the audit's prompt expects PG enum for
`subscription_state` etc., which is consistent with newer columns
elsewhere). No latent missing-`values_callable` bugs surfaced in
this scan.

**Verdict.** GREEN.

### Item 5 — FastAPI router mounting (GREEN)

**Finding.** All routers register with `app.include_router(<r>.router,
prefix="/api/v1")` in `backend/app/main.py:31-53` — that's the v1
prefix style. Two admin routers nest deeper at `/api/v1` via the
`app.routers.admin` package. There is **no** root-level mount.

`get_current_user` is at `backend/app/middleware/auth.py:14`, takes
an `HTTPBearer` token + `AsyncSession`, and returns a `User` row.
Every authed router uses `Depends(get_current_user)` — confirmed in
`auth.py`, `tasks.py`, `medications.py`. RTDN-style endpoints
(unauthenticated, JWT-verified by Google) **do not exist yet** —
Phase 3's `/billing/rtdn` would be the first, and would need a new
verification helper rather than reusing `get_current_user`.

A future billing router would mount as
`app.include_router(billing.router, prefix="/api/v1")` and define its
endpoints as `/billing/verify`, `/billing/rtdn`, etc.

**Verdict.** GREEN. But see top-line: `PATCH /auth/me/tier` already
*is* the verify endpoint, just by a different name and shape.

### Item 6 — Conflict scan (RED — billing already exists)

**Finding.** Grep over `backend/` for
`billing|subscription|entitlement|purchase|play_store|google_play`
(case-insensitive) returns 6 files:

| File | Relevance |
|------|-----------|
| `backend/app/services/billing.py` | **Direct conflict.** Server-side Play API verification, same product IDs, same package name. |
| `backend/app/routers/auth.py` | **Direct conflict.** `PATCH /auth/me/tier` already calls `validate_purchase()`. |
| `backend/app/schemas/auth.py` | Pydantic schema for tier-update body. Imports `purchase_token`, `product_id` fields. |
| `backend/app/middleware/rate_limit.py` | Coincidental "subscription" keyword. Not relevant. |
| `backend/app/routers/calendar.py` | Coincidental "subscription" (Pub/Sub). Not relevant. |
| `backend/tests/test_feedback.py` | Coincidental. Not relevant. |

The first three are the wrong-premise evidence. The proposed schema
is not greenfield — it's an *extension or replacement* of an existing
billing flow that already works for the simple "token → tier" case.

**Verdict.** RED. Top-line STOP rationale.

### Item 7 — Datetime + nullable + style conventions (GREEN)

**Finding.**
- `datetime` annotations use `Optional[datetime]` and `datetime |
  None` interchangeably — both appear in `models.py`. New code can
  pick either; pick the form that matches the rest of the file
  section it's in.
- **Legacy `Column(...)` style throughout** — not SQLAlchemy 2.0
  `Mapped[]` / `mapped_column()`. Migration 019 uses the same legacy
  style. Any new billing model must match (no mid-file 2.0-style
  introduction without a separate refactor PR).
- `nullable=False` is **explicit on every non-null column** —
  default-relying is not the convention.
- Datetime columns: `Column(DateTime(timezone=True),
  server_default=func.now(), onupdate=lambda: datetime.now(timezone.
  utc))` — note the lambda for `onupdate`, not the bare `func.now()`
  used in migration 019. The two coexist (model layer uses lambda,
  migration layer uses `sa.func.now()`); both work.
- `_AWARE = sa.DateTime(timezone=True)` is a migration-file-local
  helper used in 019 and 021. Reuse it in any new migration to keep
  the SQLite-vs-Postgres tzinfo behavior consistent.

**Verdict.** GREEN.

### Item 8 — Index naming convention (GREEN)

**Finding.** Pattern from migration 019:
- Single-column: `ix_<table>_<col>` (e.g. `ix_medications_cloud_id`)
- Composite multi-column: `ix_<scope>_<col1>_<col2>` (e.g.
  `ix_med_log_events_user_logged_at` — note the table-name shorthand
  to keep under PG's 63-char identifier limit)
- UNIQUE: `unique=True` parameter on the `op.create_index` call,
  same `ix_*` naming
- `UniqueConstraint`: `uq_<scope>` (e.g. `uq_med_tier_state`,
  `uq_med_mark`)
- Inline `index=True` on a `Column(...)` definition produces an
  alembic-default name; explicit `op.create_index` is preferred for
  readability.

**Verdict.** GREEN. Captured for any future Phase 2.

---

## Risk verdict

| Item | Verdict | Action |
|------|---------|--------|
| 1 | RED | Slot 020 → 022. Mechanical. |
| 2 | GREEN | Use `Integer` FK to `users.id`. |
| 3 | GREEN | Single-file, no mixin, plural tablenames. |
| 4 | GREEN | `Enum(values_callable=...)` pattern. |
| 5 | GREEN | `prefix="/api/v1"`, `Depends(get_current_user)`. |
| 6 | **RED** | **`services/billing.py` + `PATCH /auth/me/tier` already exist. Top-line STOP.** |
| 7 | GREEN | Legacy `Column(...)`, explicit `nullable=False`, `_AWARE` helper. |
| 8 | GREEN | `ix_<table>_<col>` / `uq_<scope>` naming. |

**Overall: RED.** The premise that this is greenfield Phase-1 schema
is wrong. The schema decision needs to be made *first*; only then
does Phase 2 have a coherent target.

---

## The design question the audit can't answer alone

The proposed schema (4 tables) layers a *richer* billing model on top
of an existing *simple* one. The choices, ordered by cost:

**A. Keep existing `User.tier` as source of truth. No new tables.**
   The tier-elevate flow already works for "user buys, server
   verifies, sets tier=PRO." Defer Phase-1 tables until they have
   concrete users — RTDN replay log, multi-product catalog, multi-
   device subscription chains.
   *Cost: ~0.* *Risk: cannot replay RTDN events; cannot audit who
   was on Pro when; cannot handle grace-period / on-hold without
   schema additions.*

**B. Add only `rtdn_events` (the append-only audit table).**
   The minimal change that gives RTDN replay + Pub/Sub idempotency
   without re-architecting the entitlement model. `User.tier` stays
   the source of truth. RTDN events correlate to users via
   `purchase_token` (lookup against Play API on demand) or via a new
   `purchase_token` column on `users`.
   *Cost: 1 table, 1 migration.* *Risk: schema half-step; if a future
   need surfaces "show user their billing history," they have to add
   `subscriptions` then.*

**C. Full proposed 4-table schema, but `entitlements` is dropped /
   reframed.** Use `User.tier` as the entitlement; `subscriptions` +
   `play_purchase_tokens` + `rtdn_events` are net-new. Add a small
   service-layer rule: `User.tier` is *derived* from active
   subscriptions, not authoritative. Trigger or materialized view
   (or just app-level write on every subscription state change) keeps
   them in sync.
   *Cost: 3 tables, 1 migration, plus a rule about who-writes-tier.*
   *Risk: `User.tier` and the new subscriptions table can diverge if
   the sync rule is missed in any code path.*

**D. Full proposed 4-table schema, `User.tier` deprecated.** New
   `entitlements` is the only entitlement record. `User.tier` stays
   for one release as a read-cached derived value, then drops in a
   later migration. `User.effective_tier` becomes a join through
   `entitlements`.
   *Cost: 4 tables, 1 schema migration, 1 follow-up
   tier-deprecation migration, code changes everywhere `User.tier` /
   `User.effective_tier` is read.* *Risk: any code reading `User.
   tier` during the transition gets stale data unless every code path
   is updated atomically with the migration.*

**Audit's recommendation: B.** Smallest delta that earns its keep —
the user *will* need RTDN audit / replay (regulatory + debugging
need that doesn't go away), and the existing `User.tier` flow is
actually load-bearing and works. Defer C/D until there's a concrete
forcing function (e.g. PrismTask Family Plan, or Stripe second
backend, or audit subpoena).

---

## Ranked improvements (assuming option B is picked)

| # | Improvement | Wall-clock | Risk | Ratio |
|---|-------------|-----------|------|-------|
| 1 | Migration 022 — `rtdn_events` + supporting enum + `users.purchase_token` column | 30 min | low | high |
| 2 | RTDN router scaffold (no logic) — `/api/v1/billing/rtdn` Pub/Sub Push receiver, JWT-verified, writes to `rtdn_events.processed=False` | 45 min | medium | high |
| 3 | Add `BILLING_*` env config slot to `app.config.settings` (Pub/Sub topic, push subscription, service account) | 15 min | low | medium |
| 4 | Tests: alembic round-trip, UNIQUE on `pubsub_message_id`, RTDN endpoint accepts a sample Pub/Sub envelope | 30 min | low | high |

If the user picks **A**, the work is just a notes-only PR (no schema
change) plus a memory entry capturing where billing already exists so
this audit doesn't fire again. If **C** or **D**, the wall-clock is
much higher and an additional sub-audit on the `User.tier` interaction
surface is warranted before any schema work.

---

## Anti-patterns surfaced

- **The "billing is greenfield" framing in the audit prompt** is the
  same shape as memory #14 ("architectural memory goes stale within
  days") warns about. The prompt's Phase-1-already-complete capture is
  accurate (Play Console + GCP setup); the *codebase* state captured
  is not. This is structurally the same mistake the
  `feedback_audit_drive_by_migration_fixes.md` memory captures — fixes
  hide in unrelated PRs. Here, an entire billing service hides in
  a "tier endpoint" PR.
- The exact file/PR that landed `services/billing.py` is not yet
  surfaced; would be worth a `git log -p -- backend/app/services/
  billing.py` if option C/D is picked, since the answer determines
  whether the existing service has known limits the schema design
  needs to absorb.

---

## Phase 4 — Claude Chat handoff

```markdown
**PrismTask billing schema audit — Phase 1 STOPPED on wrong premise.**

Audited: proposed alembic migration #020 introducing
`play_purchase_tokens`, `subscriptions`, `entitlements`, `rtdn_events`
tables for Google Play subscription support.

**Verdicts.**
- Items 1, 6: **RED**. Item 1 — head is `021_drop_medication_marks.py`
  not 019; the proposed slot would be 022 with `down_revision = "021"`.
  Item 6 — `backend/app/services/billing.py` (171 lines) and
  `PATCH /auth/me/tier` (`backend/app/routers/auth.py:168`) already
  verify Google Play purchases server-side using the *exact* product
  IDs (`prismtask_pro_monthly`, `prismtask_pro_annual`) and package
  name (`com.averycorp.prismtask`) the prompt captures. The "Phase 3
  verify endpoint" has effectively shipped its v1.
- Items 2-5, 7-8: **GREEN**. Conventions captured for whichever schema
  option the user picks.

**Shipped.** None. Phase 1 doc only —
`docs/audits/BILLING_SCHEMA_AUDIT.md`. Phase 2 did **not**
auto-fire because the wrong-premise STOP rule overrides the auto-fire
rule.

**Open question for the operator.** Which design does the new schema
extend / replace?
- (A) Keep `User.tier` as-is, no new tables. Defer.
- (B) Add only `rtdn_events`. Audit's recommendation — smallest delta,
  RTDN audit is a regulatory + debug need that doesn't go away.
- (C) Full 4-table schema with `User.tier` as derived value.
- (D) Full 4-table schema with `User.tier` deprecated.

**Non-obvious finding.** The existing `services/billing.py` does
*subscription-state-aware* validation (rejects `SUBSCRIPTION_STATE_
EXPIRED` etc.) but stores nothing — every call hits the Play
Developer API live. RTDN-driven schema (option B) is what unblocks
caching that.
```

---

## Phase 2 — NOT FIRED

Per the wrong-premise STOP, Phase 2 fan-out is **paused** until the
operator picks A/B/C/D above. The previously-proposed branch
(`feat/billing-schema-020`) and worktree are not created.

Once the operator picks, I will:
- Re-anchor the slot to **022** in whatever migration the option
  requires.
- Use worktree `../prismTask-billing-schema-022` and branch
  `feat/billing-schema-022`.
- Phase 2 audit doc length cap stays at ~500 lines; if option C/D
  pushes the implementation doc over that, I will split into
  batches.

(End of Phase 1 doc.)
