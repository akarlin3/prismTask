"""Add task_templates table

Revision ID: 004
Revises: 003
Create Date: 2026-04-09

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "004"
down_revision: Union[str, None] = "003"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "task_templates",
        sa.Column("id", sa.Integer(), primary_key=True, index=True),
        sa.Column(
            "user_id",
            sa.Integer(),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            index=True,
        ),
        sa.Column("name", sa.String(255), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("icon", sa.String(10), nullable=True),
        sa.Column("category", sa.String(100), nullable=True),
        sa.Column("template_title", sa.String(255), nullable=True),
        sa.Column("template_description", sa.Text(), nullable=True),
        sa.Column("template_priority", sa.Integer(), nullable=True),
        sa.Column(
            "template_project_id",
            sa.Integer(),
            sa.ForeignKey("projects.id", ondelete="SET NULL"),
            nullable=True,
        ),
        sa.Column("template_tags_json", sa.Text(), nullable=True),
        sa.Column("template_recurrence_json", sa.Text(), nullable=True),
        sa.Column("template_duration", sa.Integer(), nullable=True),
        sa.Column("template_subtasks_json", sa.Text(), nullable=True),
        sa.Column("is_built_in", sa.Boolean(), server_default="false"),
        sa.Column("usage_count", sa.Integer(), server_default="0"),
        sa.Column("last_used_at", sa.DateTime(), nullable=True),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.func.now()),
    )


def downgrade() -> None:
    op.drop_table("task_templates")
