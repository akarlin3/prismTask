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


class ParseChecklistResponse(BaseModel):
    course: ParsedCourseInfo
    project: ParsedProjectInfo
    tags: list[ParsedTagInfo]
    tasks: list[ParsedChecklistTask]
