"""Pydantic schemas for the integrations API."""

from datetime import date, datetime
from typing import Optional

from pydantic import BaseModel


class SuggestedTaskResponse(BaseModel):
    id: int
    source: str
    source_id: str
    source_title: str
    source_url: Optional[str] = None
    suggested_title: str
    suggested_description: Optional[str] = None
    suggested_due_date: Optional[date] = None
    suggested_priority: Optional[int] = None
    suggested_project: Optional[str] = None
    suggested_tags: Optional[list[str]] = None
    confidence: float
    status: str
    extracted_at: datetime
    created_at: datetime

    model_config = {"from_attributes": True}


class SuggestionAcceptOverrides(BaseModel):
    title: Optional[str] = None
    description: Optional[str] = None
    due_date: Optional[date] = None
    priority: Optional[int] = None
    project: Optional[str] = None


class SuggestionBatchRequest(BaseModel):
    accept: list[int] = []
    reject: list[int] = []


class SuggestionAcceptResponse(BaseModel):
    suggestion_id: int
    task_id: int
    task_title: str


class GmailScanResponse(BaseModel):
    scanned: int
    new_suggestions: int
    suggestions: list[SuggestedTaskResponse]
