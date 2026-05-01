"""AI-feature opt-out gate (PII egress audit, 2026-04-26).

The PrismTask Android client owns the *primary* gate: when the user has
disabled AI features in Settings → AI Features, the
``AiFeatureGateInterceptor`` short-circuits Anthropic-touching requests
on the device, so they never reach the backend.

This module is the *defense-in-depth* layer for direct API callers
(e.g. the future iOS client, the web client, third-party integrations,
or a buggy Android build that omitted the interceptor). When the caller
voluntarily sends ``X-PrismTask-AI-Features: disabled``, this dependency
returns 451 Unavailable For Legal Reasons before the route handler runs,
so no Anthropic call is made.

This is *not* server-side persistence of the user's preference — that
would require a User-model column and a sync route. It's an opt-out
header the caller has to set themselves. True server-side enforcement
is on the post-launch hardening roadmap (audit Section 7).
"""

from fastapi import Header, HTTPException, status

# Header name + sentinel value matched against by callers (Android,
# future iOS, web). Keep in sync with
# ``app/src/main/java/com/averycorp/prismtask/data/remote/api/AiFeatureGateInterceptor.kt``.
HEADER_NAME = "X-PrismTask-AI-Features"
HEADER_VALUE_DISABLED = "disabled"


async def require_ai_features_enabled(
    x_prismtask_ai_features: str | None = Header(default=None, alias=HEADER_NAME),
) -> None:
    """FastAPI dependency that rejects requests carrying the AI opt-out header.

    Wire as a router-level dependency on every router whose endpoints
    egress user data to Anthropic. As of 2026-05-01 that is:
      - app.routers.ai (the AI feature router — Eisenhower, Pomodoro,
        briefing, planner, time-block, weekly review, extract,
        Pomodoro coaching, batch parse)
      - the parse-import / parse-checklist endpoints in app.routers.tasks
      - the parse_syllabus endpoint in app.routers.syllabus
      - the scan_gmail_inbox endpoint in app.routers.integrations
        (added 2026-05-01 to close the gap surfaced by
        ``cowork_outputs/pii_leak_surface_reaudit_REPORT.md`` —
        the original PR #788/#790 enumeration missed it because the
        Gmail integration landed before that audit was scoped)

    When the caller has not opted out, this is a no-op.

    Maintenance note: any new route that egresses user data to Anthropic
    MUST be added to (a) this list, (b) the route decorator's
    ``dependencies=[Depends(require_ai_features_enabled)]``, and (c) the
    Android client's ``AI_PATH_PREFIXES`` in
    ``AiFeatureGateInterceptor.kt`` so the client short-circuits the
    request before it hits the network.
    """
    if x_prismtask_ai_features is None:
        return
    if x_prismtask_ai_features.strip().lower() == HEADER_VALUE_DISABLED:
        raise HTTPException(
            status_code=status.HTTP_451_UNAVAILABLE_FOR_LEGAL_REASONS,
            detail=(
                "AI features are disabled for this client. Re-enable them "
                "in PrismTask Settings → AI Features to use this endpoint."
            ),
        )
