# Backend Metrics Infra Audit — Prometheus + Grafana + Alerting

**Status:** Phase 1 mega-audit (audit-first-mega).  Phase 2 fan-out is HARD-BLOCKED on operator approval of the locks below.
**Scope:** Three sequential PRs that take `audit_emit_failures_total` from an in-process `dict[str, int]` to an operationally fillable Grafana panel + alert.
**Pairs with:** PR #773 (medication migration instrumentation; introduced the counter).
**Closes operational gap:** Phase E telemetry-watch JSX dashboard's `auditEmitFailures` field has no readable source today — this audit's three PRs fix that.

---

## Part A — Current backend state enumeration

### A.1 — Counter location + shape

The counter exists.  It is **not** a `prometheus_client.Counter`.

**Definition** — `backend/app/routers/sync.py:46`:

```python
audit_emit_failures_total: dict[str, int] = {}
```

**Increment site** — `backend/app/routers/sync.py:301-303`, inside `_emit_audit_events`:

```python
audit_emit_failures_total[entity_type] = (
    audit_emit_failures_total.get(entity_type, 0) + 1
)
```

The counter increments when a SAVEPOINT-wrapped insert of `MedicationLogEvent` raises.  Sync remains authoritative; audit is best-effort.  Each failure also emits a structured WARN log with `extra={"audit_emit_failed": True, "entity_type", "entity_cloud_id", "exception_class", "audit_emit_failures_total"}` — that structured-log shape is the contract the existing telemetry test (`backend/tests/test_audit_emit_telemetry.py`) pins.

**Label cardinality observed today:** one label, `entity_type`.  Currently the only entity that emits audit events is `tier_state` (per the test docstring — `medication_mark` was dropped).  The dict is keyed-and-not-bounded, so a future audited entity will add a key without code changes.

**Reset semantics:** in-process state, lost on restart.  Two-worker gunicorn means there are actually two independent counters — already a slight existing inaccuracy that PR-A's conversion will inherently fix (Prometheus client uses multiprocess mode or per-pid scrape; see PR-A scope below).

**Implication for PR-A:** the counter must be converted, not merely re-exported.  This expands PR-A scope by ~30-50 LOC over a "register existing counter" plan.

### A.2 — Existing `/metrics` endpoint

**None.**  `backend/app/main.py` registers 21 routers under `/api/v1/*` and a single `GET /` health check.  No `/metrics` route, no `prometheus_fastapi_instrumentator` import, no `make_asgi_app` mount, no `generate_latest` reference.  `requirements.txt` has zero prometheus packages.

### A.3 — Railway capabilities

`backend/railway.toml` + `backend/railway.json` declare a Dockerfile build, `bash start.sh` / `alembic upgrade head && gunicorn …` start command, healthcheck on `/`, on-failure restart.  `gunicorn.conf.py` configures 2 workers, `uvicorn.workers.UvicornWorker`, port from `$PORT`.

Railway as of 2026 supports public HTTP exposure of any path the app serves but does not run a Prometheus scraper internally.  Custom-metric collection is the app's responsibility — either expose `/metrics` publicly (or with auth) and let an external scraper hit it, or push metrics out via `remote_write`.  Both are viable on Railway.  Container-level CPU/memory are exposed in Railway's own dashboard; they don't conflict with app-level metrics because they're a different metric namespace.

### A.4 — Existing observability surfaces — STOP-CONDITION CHECK

**No existing observability surface.**  Greps for `sentry_sdk`, `datadog`, `honeycomb`, `opentelemetry`, `structlog` against `backend/**/*.py` returned **zero** matches.  Only `logging.config.fileConfig` is imported (in Alembic's `env.py`), which is the stock Python stdlib logger — not a structured surface.

The two "Cloud Logging" mentions in `sync.py:42` and `sync.py:304` are **comments**, not wiring — they describe a hypothetical GCP log-based metric.  No GCP Cloud Logging client is installed or initialized.

**STOP-condition does not fire.**  Multi-PR plan proceeds.

---

## Part B — Grafana hosting decision (PR-B premise)

### B.1 — Option enumeration

| # | Option | Cost | Setup effort | Notes |
|---|--------|------|--------------|-------|
| i | Grafana Cloud free tier | $0/mo | Account + API key | 10k active series, 50GB logs, 14-day retention. Sufficient for sole-user + early beta. |
| ii | Self-host Grafana on Railway | ~$5/mo Railway add-on | Container + persistent volume + Prometheus alongside | Adds operational coherence but 2x infra. |
| iii | Self-host on Fly.io / Render | ~$5-10/mo | Comparable to (ii), different PaaS | Splits hosting providers. |
| iv | Managed Datadog / New Relic | $15+/mo | Account + agent install | Exceeds cost ceiling. |

### B.2 — Decision

**Grafana Cloud free tier (option i).**  Rationale:

- Sole-user phase + early beta = effectively zero metric volume. 10k active series is two orders of magnitude over what one counter + a handful of HTTP histograms will produce.
- Railway hosts only the backend; adding a self-host Grafana adds a second deployment surface to maintain.  Not worth it at this volume.
- Cost-floor matches operator's bridge-job-pending budget concern.
- Migration to self-host (option ii) is a follow-up item at a future trigger (free tier limits hit, or operator wants longer retention than 14 days).  Filed in Phase 4 closure.

### B.3 — Hosted-Grafana auth implications

Grafana Cloud offers two ingestion shapes:

1. **Hosted scraping** — Grafana Cloud's managed agents scrape a public URL using a bearer token.  The backend exposes `/metrics`, gates it on a token in `Authorization: Bearer <token>`, and Grafana Cloud is configured (in the Grafana UI / via Terraform) to scrape that URL with that token.
2. **`remote_write` push** — backend uses Prometheus client's `remote_write` (or Grafana Alloy as a sidecar) to push metric samples to Grafana Cloud's Prometheus push endpoint.

**Pick: hosted scraping (option 1).**  Justification under D.2 below.

---

## Part C — Alerting routing (PR-C premise)

### C.1 — Notification channel

Operator is solo dev; no Slack workspace, no on-call paging.  Phone push is via stock Android notifications, not Pushover/Pushbullet.

**Pick: email-to-self via Grafana Cloud's built-in SMTP contact point.**  Zero infrastructure cost, zero new accounts, deliverable to the same Gmail inbox the operator already monitors.  No SMS escalation at this stage — adding Twilio (~$5/mo + per-SMS) is unjustified pre-bridge-job.

### C.2 — Alert thresholds + windows

| Severity | Metric | Condition | Window | Rationale |
|----------|--------|-----------|--------|-----------|
| P0 | `audit_emit_failures_total` (any label) | `rate(...[5m]) > 0` | 5 min | Counter is supposed to stay flat. Any non-zero rate is a real signal, not a noise threshold. |
| P1 | HTTP 5xx | `sum(rate(http_requests_total{code=~"5.."}[5m])) / sum(rate(http_requests_total[5m])) > 0.01` | 5 min | 1% 5xx rate is the conventional infra alert. Catches deploy regressions. |
| P2 | AI endpoint latency | `histogram_quantile(0.95, sum(rate(http_request_duration_seconds_bucket{handler=~"/api/v1/ai/.*"}[10m])) by (le)) > 5` | 10 min | Anthropic outage / quota exhaustion shows up here first. |

P1 and P2 require the `prometheus_fastapi_instrumentator` HTTP middleware that PR-A already adds (otherwise `http_requests_total` and `http_request_duration_seconds_bucket` don't exist).  They aren't gating; they're "since we have the middleware, may as well alert on the standard infra signals."

### C.3 — Alert delivery shape

Single contact point, no escalation chain.  P0 + P1 fire immediately; P2 fires after 10-min sustained breach.  All three deliver to the same email.  No paging tier — operator self-monitors email.

---

## Part D — Cross-PR architecture risks (LOCKS)

### D.1 — Counter shape LOCK

The following must be identical in PR-A's code, PR-B's dashboard query, and PR-C's alert rule.  **Any change requires re-running Phase 1.**

```text
metric name:    audit_emit_failures_total
type:           Counter (monotonic, resets on process restart)
labels:         entity_type (string; current values: "tier_state")
unit:           events
help text:      "Number of best-effort audit-emit savepoint failures since process start"
namespace:      none (top-level; medication-prefix would imply medication-only and
                future audited entities will reuse this counter)
```

PR-A converts the existing `dict[str, int]` to `prometheus_client.Counter("audit_emit_failures_total", "...", labelnames=["entity_type"])` and replaces the dict-bump with `audit_emit_failures_total.labels(entity_type=...).inc()`.  The structured-log line stays — `prometheus_client` does not replace structured logs; the two surfaces co-exist.

The existing test `backend/tests/test_audit_emit_telemetry.py` reaches into `sync_router.audit_emit_failures_total` directly and expects a dict.  PR-A must rewrite those assertions to use the Counter's `_value.get()` or a `CollectorRegistry` snapshot.  This is in-scope for PR-A.

### D.2 — Push vs pull architecture LOCK

**Pick: PULL.**  Backend exposes `/metrics` publicly, gated on `Authorization: Bearer $METRICS_SCRAPE_TOKEN`.  Grafana Cloud's hosted scraper pulls the URL on a 60-second interval.

Rationale:

- Pull is the Prometheus-native pattern; tooling assumes it.
- Pull avoids a long-running background task in the gunicorn workers (push would need either a thread per worker or a sidecar agent on Railway, both more moving parts than a passive endpoint).
- The gunicorn 2-worker setup means push would need either (a) `prometheus_client`'s multiprocess mode + a single push goroutine, or (b) two independent push streams whose samples Grafana would have to deduplicate. Pull resolves this with `prometheus_client`'s standard multiprocess collector + per-worker scrape, all handled by the library.
- Hosted scraping is in Grafana Cloud's free-tier feature set as of 2026.

The decision sets PR-A's library: **`prometheus_client>=0.20`** for the Counter type + **`prometheus-fastapi-instrumentator>=7.0`** for the HTTP middleware + `/metrics` exposition.  No `remote_write` configuration, no Grafana Alloy sidecar.

### D.3 — Secret management LOCK

Single secret: `METRICS_SCRAPE_TOKEN`.  Lives as a Railway env var; `.env.example` carries a placeholder; `app/config.py` reads it via `pydantic-settings` (already the pattern for every other secret in this app).  The `/metrics` route checks `Authorization: Bearer ...` against the env var; mismatch → 401.

Grafana Cloud's scrape config stores the same token in its agent's secret store.  Operator action between PR-A and PR-B: copy the Railway-generated token into Grafana Cloud's UI when wiring the scrape job.  PR-B's runbook documents this step.

No secret-rotation automation — manual rotate on suspected compromise.  Acceptable at this volume.

### D.4 — Cost ceiling LOCK

| Line item | Monthly cost |
|-----------|--------------|
| Grafana Cloud free tier | $0 |
| Email contact point (Gmail, no extra SMTP) | $0 |
| Railway delta (no new service, just env vars) | $0 |
| **Total** | **$0/mo** |

Far below the $15/mo audit ceiling.  No re-scope.  Operator's bridge-job-pending budget is unaffected.

---

## Part E — Phase 2 PR sequencing plan

### PR-A — Backend `prometheus_client` Counter + `/metrics` endpoint

**Files:**
- `backend/requirements.txt` — add `prometheus_client>=0.20,<1.0` and `prometheus-fastapi-instrumentator>=7.0,<8.0`
- `backend/app/routers/sync.py` — convert `audit_emit_failures_total` from `dict[str, int]` to `prometheus_client.Counter` with `entity_type` label; rewrite `_emit_audit_events` increment to use `.labels(...).inc()`
- `backend/app/main.py` — instantiate `Instrumentator()` to wire HTTP middleware + register a `/metrics` route via the instrumentator's `expose()` helper, gated on bearer token
- `backend/app/config.py` — add `metrics_scrape_token: str = ""` with a `pydantic-settings` field (empty default disables the endpoint in local dev)
- `backend/.env.example` — add `METRICS_SCRAPE_TOKEN=` placeholder
- `backend/tests/test_audit_emit_telemetry.py` — rewrite assertions to read from the Counter (use `prometheus_client.REGISTRY.get_sample_value(...)`) instead of dict access; clear via `Counter._metrics.clear()` or equivalent
- `backend/tests/test_metrics_endpoint.py` — **new** — assert (a) unauth GET /metrics returns 401, (b) auth'd GET returns 200 with `text/plain; version=0.0.4`, (c) the response body contains `audit_emit_failures_total`

**LOC estimate:** 80–150 (counter conversion 30, instrumentator wiring 15, route + auth dependency 25, test rewrite 30, new test 40)

**Audit-first cap for PR-A's micro-audit:** ~150 lines.  Re-verifies A.1 + A.2 + D.1 + D.2 still hold on current main.

**Per-PR Phase 3 verification:**
```bash
cd backend && pytest tests/test_audit_emit_telemetry.py tests/test_metrics_endpoint.py -v
# Local smoke:
METRICS_SCRAPE_TOKEN=local-test uvicorn app.main:app &
curl http://localhost:8000/metrics                           # → 401
curl -H "Authorization: Bearer local-test" http://localhost:8000/metrics | grep audit_emit_failures_total
```

### PR-B — Grafana Cloud dashboard + scrape config

**Files:**
- `infra/grafana/dashboards/audit-emit.json` — dashboard JSON (single-stat for current value, time series for rate, table broken down by `entity_type` label)
- `docs/observability/setup.md` — operator runbook: how to create the Grafana Cloud account, generate the scrape token, configure the hosted scrape job to hit Railway's `/metrics`, import the dashboard JSON
- `backend/.env.example` — already has the placeholder from PR-A; PR-B does not re-edit

**Operator action required between PR-A merge and PR-B start:**
1. Create Grafana Cloud free-tier account (one-time)
2. In Grafana Cloud, create a hosted scrape job pointing at `https://<railway-app-host>/metrics` with `METRICS_SCRAPE_TOKEN` as bearer
3. Generate the scrape token, set it as `METRICS_SCRAPE_TOKEN` in Railway env vars
4. Confirm Grafana Cloud's "Explore" view sees `audit_emit_failures_total` samples coming in

PR-B's audit re-verifies these steps are complete before producing dashboard JSON.

**LOC estimate:** 50–100 (dashboard JSON ~60–80, runbook ~30)

**Audit-first cap for PR-B's micro-audit:** ~150 lines.

**Per-PR Phase 3 verification:**
- Manual: import `audit-emit.json` in Grafana Cloud; verify panels render and show live value
- Trigger a controlled audit-emit failure in staging (Railway dev env or local with metrics pointed at Grafana Cloud) and verify the counter increments visibly in the dashboard

### PR-C — Alert rules + email contact point

**Files:**
- `infra/grafana/alerts/audit-emit-failures.yaml` — three alert rules (P0/P1/P2 from C.2)
- `docs/observability/alerts.md` — runbook: what each alert means, first-response steps (check Railway logs for `audit_emit_failed=true`, check `/api/v1/sync` recent error rate, identify `entity_type` from the alert label)

**Operator action required before PR-C starts:**
- In Grafana Cloud, configure email contact point pointing at operator's Gmail
- (Done via Grafana Cloud UI; no code change required)

**LOC estimate:** 30–80

**Audit-first cap for PR-C's micro-audit:** ~150 lines.

**Per-PR Phase 3 verification:**
- Manual: in Grafana Cloud, use "test" button on each alert rule to dry-fire
- End-to-end: trigger sustained controlled audit-emit failure (≥1 failure inside a 5-min window); confirm P0 alert fires and email lands in operator's inbox within 5 min of breach

---

## Part F — Verdict + go/no-go

**Verdict: GO on the 3-PR plan.**

All Part A current-state findings are consistent with the multi-PR architecture.  No STOP-condition fires:

- A.1 confirms the counter exists (PR #773's premise still holds 8 days later — drift sweep clean).
- A.2 + A.4 confirm greenfield — no observability surface to extend, no shortcut to single-PR.
- A.3 confirms Railway supports the chosen pull architecture.
- D.4 confirms total cost is $0/mo, far inside the $15/mo ceiling.

**HARD STOP for Phase 2:** operator must approve this audit doc before any Phase 2 PR opens.  All three PRs reference the locks above; changing a lock invalidates all three plans.

---

## Phase 4 banking (deferred until all 3 PRs ship)

- Memory update candidate: "PrismTask backend metrics: `prometheus_client` Counter + Grafana Cloud hosted-scrape PULL + email alerts.  Counter shape and namespace conventions locked in `BACKEND_METRICS_INFRA_AUDIT.md`.  Future custom metrics follow the same pattern (Counter/Histogram → `/metrics` (already exposed) → Grafana panel → alert rule)."  Eviction candidate to be picked at Phase 4 closure.
- F.5 backlog item to file at closure: "Self-host Grafana migration trigger — fires when Grafana Cloud free tier exhausts (10k series or 50GB logs) or when retention beyond 14 days is needed."  Per memory #30 defer-minimization, not auto-filed pre-ship.
- Final commit footer (per task spec): `closes operational gap from PR #773; Phase E telemetry watch JSX dashboard auditEmitFailures field now operationally fillable.`

---

## Operator-facing decision summary (paste-ready)

If you only read one section, read this one.  Approve or redirect each lock:

1. **Counter conversion** — convert `dict[str, int]` → `prometheus_client.Counter`, label `entity_type`. (PR-A scope expansion vs "register existing counter".)
2. **Hosting** — Grafana Cloud free tier, $0/mo. (Self-host Railway is option ii, $5/mo, deferred to a future trigger.)
3. **Architecture** — PULL: backend exposes `/metrics` with bearer token, Grafana Cloud hosted scraper pulls. (Push via `remote_write` is rejected.)
4. **Auth** — single `METRICS_SCRAPE_TOKEN` env var; manual rotation.
5. **Notification** — email-to-self via Grafana Cloud SMTP.
6. **Alerts** — 3 rules (P0 audit-emit-failures-rate-nonzero, P1 5xx > 1%, P2 AI p95 > 5s).
7. **Cost** — $0/mo total.
8. **PR sequence** — A → B → C, each gated on its own micro-audit; PR-A and PR-B require an operator action between them (Grafana Cloud account + token).

Reply with "approve" to unblock Phase 2, or call out which lock to change and I'll re-run Phase 1 against the new constraint.
