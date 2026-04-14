"""Pydantic schemas for the AI-powered import parsing endpoints.

POST /api/v1/tasks/parse-import   — simple todo list / JSX schedule
POST /api/v1/tasks/parse-checklist — comprehensive course/syllabus import
"""
from __future__ import annotations

from typing import Optional

from pydantic import BaseModel


# ---------------------------------------------------------------------------
# parse-import  (mirrors ClaudeParserService / TodoListParser schema)
# ---------------------------------------------------------------------------

class ParseImportRequest(BaseModel):
    content: str


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
    content: str


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
