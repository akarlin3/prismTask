# PR-A micro-audit — `prometheus_client` Counter + `/metrics` endpoint

**Parent:** [`BACKEND_METRICS_INFRA_AUDIT.md`](BACKEND_METRICS_INFRA_AUDIT.md) (Phase 1 mega-audit, merged via #1090).
**Scope cap:** ~150 lines (this doc).
**Premise lock owners:** D.1 (counter shape), D.2 (PULL), D.3 (single token), all from the parent.

---

## Re-verification of parent locks

### A.1 — counter still `dict[str, int]`

`backend/app/routers/sync.py:46` still reads:

```python
audit_emit_failures_total: dict[str, int] = {}
```

No drift since the parent audit. PR-A's job is the conversion.

### A.2 — `/metrics` still absent

`grep -n "/metrics\|prometheus_client\|prometheus_fastapi_instrumentator" backend/app/main.py` → zero hits. `requirements.txt` has zero prometheus packages. Greenfield holds.

### D.1 — counter shape lock

```text
metric name:    audit_emit_failures_total
type:           Counter (monotonic, resets on process restart)
labels:         entity_type
help text:      Number of best-effort audit-emit savepoint failures since process start
```

Unchanged.

### D.2 — PULL with bearer auth

Unchanged. `/metrics` endpoint, `prometheus-fastapi-instrumentator` middleware, single bearer-token gate.

### D.3 — `METRICS_SCRAPE_TOKEN` env var

Single secret, lives in `app/config.py` via `pydantic-settings` like every other secret today. Matches the existing pattern (`JWT_SECRET_KEY`, `ANTHROPIC_API_KEY`, `INTEGRATION_ENCRYPTION_KEY` all use the same shape). Empty default disables the endpoint in dev — same convention as `JWT_SECRET_KEY` having an empty default and dev-only auto-generated fallback.

---

## File-by-file delta

### `backend/requirements.txt`
Append two lines:
```
prometheus_client>=0.20,<1.0
prometheus-fastapi-instrumentator>=7.0,<8.0
```
Both libraries are pure Python (no C extensions); the slim Docker base image needs no extra `apt` packages.

### `backend/app/config.py`
Add one field to `Settings`:
```python
METRICS_SCRAPE_TOKEN: str = ""
```
Empty default → `/metrics` returns 503 if hit (endpoint registered but scrape token unset). This avoids leaking metrics from a misconfigured prod deploy where the operator forgot to set the env var.

### `backend/.env.example`
Append:
```
METRICS_SCRAPE_TOKEN=  # set to a random token; configure same value in Grafana Cloud scrape job
```

### `backend/app/routers/sync.py`
Convert the dict to a `prometheus_client.Counter`:
```python
from prometheus_client import Counter

audit_emit_failures_total = Counter(
    "audit_emit_failures_total",
    "Number of best-effort audit-emit savepoint failures since process start",
    labelnames=["entity_type"],
)
```
Replace the dict-bump in `_emit_audit_events` (lines 301-303) with:
```python
audit_emit_failures_total.labels(entity_type=entity_type).inc()
```
The structured-log line keeps emitting all four `extra` fields — but `audit_emit_failures_total` (the per-entity count) now needs to be read from the Counter:
```python
current_count = audit_emit_failures_total.labels(entity_type=entity_type)._value.get()
# OR (cleaner): use a CollectorRegistry sample lookup
```
The structured-log `audit_emit_failures_total` field is a snapshot of the per-entity count for grep-friendly Cloud Logging filters; it should remain an `int`. PR-A keeps the field but reads from the Counter's `_value.get()` rather than the dict.

### `backend/app/main.py`
Wire the instrumentator + bearer-gated `/metrics` route:
```python
from fastapi import Depends, HTTPException, Request, status
from prometheus_fastapi_instrumentator import Instrumentator

_instrumentator = Instrumentator(
    should_group_status_codes=True,
    should_ignore_untemplated=True,
    excluded_handlers=["/metrics"],  # don't self-instrument
)
_instrumentator.instrument(app)

def _require_scrape_token(request: Request) -> None:
    if not settings.METRICS_SCRAPE_TOKEN:
        raise HTTPException(status_code=503, detail="metrics endpoint not configured")
    auth = request.headers.get("authorization", "")
    if auth != f"Bearer {settings.METRICS_SCRAPE_TOKEN}":
        raise HTTPException(status_code=401, detail="invalid scrape token")

@app.get("/metrics")
async def metrics(_: None = Depends(_require_scrape_token)) -> Response:
    return Response(
        content=generate_latest(),
        media_type="text/plain; version=0.0.4; charset=utf-8",
    )
```
Imports `settings` from `app.config` (already imported at line 4). Adds `Response` from `fastapi.responses` and `generate_latest` from `prometheus_client`.

### `backend/tests/test_audit_emit_telemetry.py`
The existing test does `sync_router.audit_emit_failures_total.clear()` and `sync_router.audit_emit_failures_total.get("tier_state") == 1`. Both assume a dict. Rewrite to use `prometheus_client.REGISTRY.get_sample_value("audit_emit_failures_total", {"entity_type": "tier_state"})`. Reset between tests via `audit_emit_failures_total.clear()` (Counter has its own `clear()` since 0.20). The structured-log assertion (record carries `audit_emit_failures_total` int field) stays — it asserts the int read from `Counter._value.get()`.

### `backend/tests/test_metrics_endpoint.py` (new)
Three test cases:
1. `GET /metrics` with no header → 401
2. `GET /metrics` with bad token → 401
3. `GET /metrics` with correct token → 200, body contains `audit_emit_failures_total`, `Content-Type` starts with `text/plain`

The fixture sets `settings.METRICS_SCRAPE_TOKEN = "test-scrape-token"` for the duration of the test (and restores afterward). Use `monkeypatch.setattr(settings, "METRICS_SCRAPE_TOKEN", ...)`.

A fourth case for the empty-token 503 path is included for parity — production behavior when operator forgets to set the env var.

---

## LOC accounting

| File | +LOC | Notes |
|------|------|-------|
| requirements.txt | +2 | two pinned deps |
| config.py | +1 | one Settings field |
| .env.example | +1 | one placeholder line |
| sync.py | ~+10 / -3 | Counter import + def replaces dict; one .inc() replaces dict-bump; one ._value.get() replaces dict-read |
| main.py | ~+25 | instrumentator wiring + /metrics route + auth dep + 2 imports |
| test_audit_emit_telemetry.py | ~+15 / -10 | rewrite assertions for Counter shape |
| test_metrics_endpoint.py | +60 | new file, 4 test cases + fixture |
| **Total** | **~115 LOC net add** | inside parent estimate of 80-150 |

---

## Phase 3 verification (per-PR, fires pre-merge)

Local:
```bash
cd backend && pytest tests/test_audit_emit_telemetry.py tests/test_metrics_endpoint.py -v
```

Smoke:
```bash
METRICS_SCRAPE_TOKEN=local-test uvicorn app.main:app &
curl -i http://localhost:8000/metrics                          # → 401
curl -i -H "Authorization: Bearer wrong" http://localhost:8000/metrics  # → 401
curl -s -H "Authorization: Bearer local-test" http://localhost:8000/metrics | grep -E '^audit_emit_failures_total|^http_request'
```

CI: lint-and-test (backend pytest) covers the unit tests. No new CI hooks needed.

---

## STOP-conditions for PR-A

- If `prometheus-fastapi-instrumentator` is incompatible with the installed FastAPI version (0.136.1), STOP and re-pick the dep range.
- If `Counter._value.get()` API is private and not stable in `prometheus_client>=0.20`, STOP and rewrite the structured-log field source to use `REGISTRY.get_sample_value(...)` (slower but public API).
- If the `/metrics` body exceeds Railway's response-size limits at any point in the lifetime, STOP and re-scope to a metric-allowlist (drop default histograms).
- If the conversion changes test ordering / shared state in a way that breaks unrelated tests, STOP and isolate the registry per test (use `CollectorRegistry()` instances, not the default global).

---

## Verdict

GO. All parent locks hold. Implementation diff is mechanical, contained inside `sync.py` + `main.py` + `config.py` + 2 test files + 2 one-line config files. ~115 LOC net add, comfortably inside the 80-150 estimate from the parent audit's Part E.
