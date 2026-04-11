"""Add suggested_tasks and integration_configs tables

Revision ID: 008
Revises: 007
Create Date: 2026-04-11
"""

from alembic import op
import sqlalchemy as sa

revision = "008"
down_revision = "007"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "suggested_tasks",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("source", sa.String(20), nullable=False),
        sa.Column("source_id", sa.String(255), nullable=False),
        sa.Column("source_title", sa.String(500), nullable=False),
        sa.Column("source_url", sa.Text(), nullable=True),
        sa.Column("suggested_title", sa.String(500), nullable=False),
        sa.Column("suggested_description", sa.Text(), nullable=True),
        sa.Column("suggested_due_date", sa.Date(), nullable=True),
        sa.Column("suggested_priority", sa.Integer(), nullable=True),
        sa.Column("suggested_project", sa.String(255), nullable=True),
        sa.Column("suggested_tags_json", sa.Text(), nullable=True),
        sa.Column("confidence", sa.Float(), nullable=False, server_default="0.0"),
        sa.Column("status", sa.String(20), nullable=False, server_default="pending"),
        sa.Column("extracted_at", sa.DateTime(), server_default=sa.func.now()),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.func.now()),
        sa.UniqueConstraint("user_id", "source", "source_id", name="uq_user_source_source_id"),
    )

    op.create_table(
        "integration_configs",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("user_id", sa.Integer(), sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True),
        sa.Column("source", sa.String(20), nullable=False),
        sa.Column("is_enabled", sa.Boolean(), server_default="false"),
        sa.Column("config_json", sa.Text(), nullable=True),
        sa.Column("last_scan_at", sa.DateTime(), nullable=True),
        sa.Column("scan_frequency_minutes", sa.Integer(), server_default="120"),
        sa.Column("webhook_token", sa.String(64), nullable=True, unique=True),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.func.now()),
        sa.UniqueConstraint("user_id", "source", name="uq_user_integration_source"),
    )


def downgrade() -> None:
    op.drop_table("integration_configs")
    op.drop_table("suggested_tasks")
