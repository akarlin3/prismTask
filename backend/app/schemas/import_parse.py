"""Pydantic schemas for the AI-powered import parsing endpoints.

POST /api/v1/tasks/parse-import   — simple todo list / JSX schedule
POST /api/v1/tasks/parse-checklist — comprehensive course/syllabus import
"""
from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field

# Max bytes of pasted content accepted by the AI import endpoints.
# Claude Haiku tokenises roughly at ~4 chars/token, so 50k chars is
# ~12.5k input tokens per call — a reasonable cap for a single paste.
MAX_PARSE_CONTENT_LENGTH = 50_000


# ---------------------------------------------------------------------------
# parse-import  (mirrors ClaudeParserService / TodoListParser schema)
# ---------------------------------------------------------------------------

class ParseImportRequest(BaseModel):
    content: str = Field(..., min_length=1, max_length=MAX_PARSE_CONTENT_LENGTH)


class ParsedImportItem(BaseModel):
    title: str
    description: Optional[str] = None
    dueDate: Optional[str] = None  # YYYY-MM-DD
    priority: int = 0
    completed: bool = False
    subtasks: list[ParsedImportItem] = []


# Self-referential model requires an explicit rebuild call.
ParsedImportItem.model_rebuild()


class ParseImportResponse(BaseModel):
    name: Optional[str] = None
    items: list[ParsedImportItem]


# ---------------------------------------------------------------------------
# parse-checklist  (mirrors ChecklistParser / ComprehensiveImportResult schema)
# ---------------------------------------------------------------------------

class ParseChecklistRequest(BaseModel):
    content: str = Field(..., min_length=1, max_length=MAX_PARSE_CONTENT_LENGTH)


class ParsedChecklistTask(BaseModel):
    title: str
    description: Optional[str] = None
    dueDate: Optional[str] = None  # YYYY-MM-DD
    priority: int = 0
    completed: bool = False
    tags: list[str] = []
    estimatedMinutes: Optional[int] = None
    # F.8: refs phases[].name when source clearly groups this task under
    # a phase / week / sprint heading. Resolved client-side after phase
    # IDs are known.
    phaseName: Optional[str] = None
    subtasks: list[ParsedChecklistTask] = []


ParsedChecklistTask.model_rebuild()


class ParsedCourseInfo(BaseModel):
    code: str
    name: str


class ParsedProjectInfo(BaseModel):
    name: str
    color: str
    icon: str


class ParsedTagInfo(BaseModel):
    name: str
    color: Optional[str] = None


class ParsedProjectPhase(BaseModel):
    name: str
    description: Optional[str] = None
    startDate: Optional[str] = None  # YYYY-MM-DD
    endDate: Optional[str] = None  # YYYY-MM-DD
    orderIndex: int = 0


class ParsedProjectRisk(BaseModel):
    title: str
    description: Optional[str] = None
    # LOW | MEDIUM | HIGH — string preserved verbatim for client-side mapping.
    level: str = "MEDIUM"


class ParsedExternalAnchor(BaseModel):
    title: str
    # calendar_deadline | numeric_threshold | boolean_gate
    type: str = "calendar_deadline"
    # Phase title that anchors this. Refs phases[].name; resolved client-side.
    phaseName: Optional[str] = None
    targetDate: Optional[str] = None  # YYYY-MM-DD


class ParsedTaskDependency(BaseModel):
    # Refs tasks[].title; resolved to IDs client-side after task insert.
    blockerTitle: str
    blockedTitle: str


class ParseChecklistResponse(BaseModel):
    course: ParsedCourseInfo
    project: ParsedProjectInfo
    tags: list[ParsedTagInfo]
    tasks: list[ParsedChecklistTask]
    # F.8 extensions. Default empty so existing callers (schoolwork import)
    # are unaffected; project-import callers read them when populated.
    phases: list[ParsedProjectPhase] = []
    risks: list[ParsedProjectRisk] = []
    externalAnchors: list[ParsedExternalAnchor] = []
    taskDependencies: list[ParsedTaskDependency] = []
