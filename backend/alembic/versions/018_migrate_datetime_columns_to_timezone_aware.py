"""Migrate all datetime columns to timezone-aware (TIMESTAMP WITH TIME ZONE)

Existing naive values are assumed to be UTC (convention enforced throughout the
codebase). The USING clause re-interprets each naive value as UTC so Postgres
records the same wall-clock instant rather than shifting by server local offset.

Revision ID: 018
Revises: 017
Create Date: 2026-04-21
"""

from alembic import op
import sqlalchemy as sa

revision = "018"
down_revision = "017"
branch_labels = None
depends_on = None

_NAIVE = sa.DateTime()
_AWARE = sa.DateTime(timezone=True)


def _to_aware(table: str, column: str) -> None:
    op.alter_column(
        table,
        column,
        type_=_AWARE,
        existing_type=_NAIVE,
        postgresql_using=f"{column} AT TIME ZONE 'UTC'",
    )


def _to_naive(table: str, column: str) -> None:
    op.alter_column(
        table,
        column,
        type_=_NAIVE,
        existing_type=_AWARE,
        postgresql_using=f"{column} AT TIME ZONE 'UTC'",
    )


def upgrade() -> None:
    # users
    _to_aware("users", "created_at")
    _to_aware("users", "updated_at")

    # goals
    _to_aware("goals", "created_at")
    _to_aware("goals", "updated_at")

    # projects
    _to_aware("projects", "created_at")
    _to_aware("projects", "updated_at")

    # tags
    _to_aware("tags", "created_at")

    # tasks
    _to_aware("tasks", "completed_at")
    _to_aware("tasks", "eisenhower_updated_at")
    _to_aware("tasks", "created_at")
    _to_aware("tasks", "updated_at")

    # attachments
    _to_aware("attachments", "created_at")

    # habits
    _to_aware("habits", "created_at")
    _to_aware("habits", "updated_at")

    # habit_completions
    _to_aware("habit_completions", "created_at")

    # task_templates
    _to_aware("task_templates", "last_used_at")
    _to_aware("task_templates", "created_at")
    _to_aware("task_templates", "updated_at")

    # project_members
    _to_aware("project_members", "joined_at")

    # project_invites
    _to_aware("project_invites", "created_at")
    _to_aware("project_invites", "expires_at")

    # activity_logs
    _to_aware("activity_logs", "created_at")

    # task_comments
    _to_aware("task_comments", "created_at")
    _to_aware("task_comments", "updated_at")

    # app_releases
    _to_aware("app_releases", "created_at")

    # suggested_tasks
    _to_aware("suggested_tasks", "extracted_at")
    _to_aware("suggested_tasks", "created_at")
    _to_aware("suggested_tasks", "updated_at")

    # integration_configs
    _to_aware("integration_configs", "last_scan_at")
    _to_aware("integration_configs", "created_at")
    _to_aware("integration_configs", "updated_at")

    # calendar_sync_settings
    _to_aware("calendar_sync_settings", "last_sync_at")
    _to_aware("calendar_sync_settings", "created_at")
    _to_aware("calendar_sync_settings", "updated_at")

    # bug_reports
    _to_aware("bug_reports", "created_at")
    _to_aware("bug_reports", "updated_at")

    # nd_preferences
    _to_aware("nd_preferences", "created_at")
    _to_aware("nd_preferences", "updated_at")

    # daily_essential_slot_completions
    _to_aware("daily_essential_slot_completions", "taken_at")
    _to_aware("daily_essential_slot_completions", "created_at")
    _to_aware("daily_essential_slot_completions", "updated_at")


def downgrade() -> None:
    # daily_essential_slot_completions
    _to_naive("daily_essential_slot_completions", "updated_at")
    _to_naive("daily_essential_slot_completions", "created_at")
    _to_naive("daily_essential_slot_completions", "taken_at")

    # nd_preferences
    _to_naive("nd_preferences", "updated_at")
    _to_naive("nd_preferences", "created_at")

    # bug_reports
    _to_naive("bug_reports", "updated_at")
    _to_naive("bug_reports", "created_at")

    # calendar_sync_settings
    _to_naive("calendar_sync_settings", "updated_at")
    _to_naive("calendar_sync_settings", "created_at")
    _to_naive("calendar_sync_settings", "last_sync_at")

    # integration_configs
    _to_naive("integration_configs", "updated_at")
    _to_naive("integration_configs", "created_at")
    _to_naive("integration_configs", "last_scan_at")

    # suggested_tasks
    _to_naive("suggested_tasks", "updated_at")
    _to_naive("suggested_tasks", "created_at")
    _to_naive("suggested_tasks", "extracted_at")

    # app_releases
    _to_naive("app_releases", "created_at")

    # task_comments
    _to_naive("task_comments", "updated_at")
    _to_naive("task_comments", "created_at")

    # activity_logs
    _to_naive("activity_logs", "created_at")

    # project_invites
    _to_naive("project_invites", "expires_at")
    _to_naive("project_invites", "created_at")

    # project_members
    _to_naive("project_members", "joined_at")

    # task_templates
    _to_naive("task_templates", "updated_at")
    _to_naive("task_templates", "created_at")
    _to_naive("task_templates", "last_used_at")

    # habit_completions
    _to_naive("habit_completions", "created_at")

    # habits
    _to_naive("habits", "updated_at")
    _to_naive("habits", "created_at")

    # attachments
    _to_naive("attachments", "created_at")

    # tasks
    _to_naive("tasks", "updated_at")
    _to_naive("tasks", "created_at")
    _to_naive("tasks", "eisenhower_updated_at")
    _to_naive("tasks", "completed_at")

    # tags
    _to_naive("tags", "created_at")

    # projects
    _to_naive("projects", "updated_at")
    _to_naive("projects", "created_at")

    # goals
    _to_naive("goals", "updated_at")
    _to_naive("goals", "created_at")

    # users
    _to_naive("users", "updated_at")
    _to_naive("users", "created_at")
