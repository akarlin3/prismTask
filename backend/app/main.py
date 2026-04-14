import traceback
import urllib.parse

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text

from app.config import settings
from app.database import engine
from app.routers import ai, analytics, app_update, auth, dashboard, export, feedback, goals, habits, integrations, nd_preferences, projects, search, sync, tags, tasks, templates
from app.routers.admin import activity_logs as admin_activity_logs
from app.routers.admin import debug_logs as admin_debug_logs

app = FastAPI(
    title="PrismTask API",
    description="Hierarchical task management API with AI-powered NLP",
    version="0.2.0",
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
app.include_router(sync.router, prefix="/api/v1")
app.include_router(export.router, prefix="/api/v1")
app.include_router(feedback.router, prefix="/api/v1")
app.include_router(integrations.router, prefix="/api/v1")
app.include_router(nd_preferences.router, prefix="/api/v1")
app.include_router(admin_activity_logs.router, prefix="/api/v1")
app.include_router(admin_debug_logs.router, prefix="/api/v1")


@app.get("/")
async def health_check():
    return {"status": "healthy", "service": "PrismTask API", "version": "0.2.0"}


@app.get("/debug/db")
async def debug_db():
    """Temporary diagnostic endpoint for troubleshooting database connectivity."""
    db_url = settings.DATABASE_URL or ""
    parsed = urllib.parse.urlparse(db_url)
    password = parsed.password or ""
    result: dict = {
        "password_length": len(password),
        "password_repr": repr(password),
        "password_hex": password.encode().hex(),
        "url_ends_with_repr": repr(db_url[-10:]),
    }
    try:
        async with engine.connect() as conn:
            row = await conn.execute(text("SELECT 1"))
            result["db_ok"] = True
            result["db_result"] = row.scalar()
    except Exception as exc:
        result["db_ok"] = False
        result["error"] = f"{type(exc).__name__}: {exc}"
        result["traceback"] = traceback.format_exc()
    return result