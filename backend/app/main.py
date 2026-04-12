from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers import ai, analytics, app_update, auth, dashboard, export, feedback, goals, habits, integrations, projects, search, sync, tags, tasks, templates

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


@app.get("/")
async def health_check():
    return {"status": "healthy", "service": "PrismTask API", "version": "0.2.0"}

allow_origins=[
    "http://localhost:5173",              # local dev
    "https://your-web-service.up.railway.app",  # Railway web (update with actual URL)
]