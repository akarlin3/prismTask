from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers import ai, analytics, app_update, auth, calendar, daily_essentials, dashboard, export, feedback, goals, habits, integrations, medications, nd_preferences, projects, search, syllabus, sync, tags, tasks, templates
from app.routers.admin import activity_logs as admin_activity_logs
from app.routers.admin import debug_logs as admin_debug_logs

# Single source of truth for the backend API version. Keep in sync with
# the version reported in /health_check and exposed via the OpenAPI schema.
API_VERSION = "0.2.0"

app = FastAPI(
    title="PrismTask API",
    description="Hierarchical task management API with AI-powered NLP",
    version=API_VERSION,
    debug=settings.debug,
)

_cors_origins = settings.effective_cors_origins
_has_wildcard = "*" in _cors_origins

app.add_middleware(
    CORSMiddleware,
    allow_origins=_cors_origins,
    allow_credentials=not _has_wildcard,  # credentials require explicit origins
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type"],
)

app.include_router(ai.router, prefix="/api/v1")
app.include_router(analytics.router, prefix="/api/v1")
app.include_router(app_update.router, prefix="/api/v1")
app.include_router(auth.router, prefix="/api/v1")
app.include_router(goals.router, prefix="/api/v1")
app.include_router(projects.router, prefix="/api/v1")
app.include_router(dashboard.router, prefix="/api/v1")
app.include_router(tasks.router, prefix="/api/v1")
app.include_router(tags.router, prefix="/api/v1")
app.include_router(habits.router, prefix="/api/v1")
app.include_router(templates.router, prefix="/api/v1/templates", tags=["templates"])
app.include_router(search.router, prefix="/api/v1")
app.include_router(syllabus.router, prefix="/api/v1")
app.include_router(sync.router, prefix="/api/v1")
app.include_router(export.router, prefix="/api/v1")
app.include_router(feedback.router, prefix="/api/v1")
app.include_router(integrations.router, prefix="/api/v1")
app.include_router(calendar.router, prefix="/api/v1")
app.include_router(nd_preferences.router, prefix="/api/v1")
app.include_router(daily_essentials.router, prefix="/api/v1")
app.include_router(medications.router, prefix="/api/v1")
app.include_router(admin_activity_logs.router, prefix="/api/v1")
app.include_router(admin_debug_logs.router, prefix="/api/v1")


@app.on_event("startup")
async def _start_calendar_sync_scheduler() -> None:
    try:
        from app.services.calendar_periodic_sync import start_scheduler

        start_scheduler()
    except Exception as exc:  # noqa: BLE001
        import logging

        logging.getLogger(__name__).warning(
            "Failed to start calendar sync scheduler: %s", exc
        )


@app.on_event("shutdown")
async def _stop_calendar_sync_scheduler() -> None:
    try:
        from app.services.calendar_periodic_sync import stop_scheduler

        stop_scheduler()
    except Exception:  # noqa: BLE001
        pass


@app.get("/")
async def health_check():
    return {"status": "healthy", "service": "PrismTask API", "version": API_VERSION}