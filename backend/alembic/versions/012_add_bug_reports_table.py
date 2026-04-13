"""Add bug_reports table

Revision ID: 012
Revises: 011
Create Date: 2026-04-13
"""

from alembic import op
import sqlalchemy as sa

revision = "012"
down_revision = "011"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "bug_reports",
        sa.Column("id", sa.Integer, primary_key=True),
        sa.Column("report_id", sa.String(64), unique=True, nullable=False, index=True),
        sa.Column(
            "user_id",
            sa.Integer,
            sa.ForeignKey("users.id", ondelete="SET NULL"),
            nullable=True,
            index=True,
        ),
        sa.Column("category", sa.String(50), nullable=False),
        sa.Column("description", sa.Text, nullable=False),
        sa.Column("severity", sa.String(20), nullable=False, server_default="MINOR"),
        sa.Column("steps", sa.Text, nullable=True),
        sa.Column("screenshot_uris", sa.Text, nullable=True),
        sa.Column("device_model", sa.String(255), nullable=True),
        sa.Column("device_manufacturer", sa.String(255), nullable=True),
        sa.Column("android_version", sa.Integer, nullable=True),
        sa.Column("app_version", sa.String(50), nullable=True),
        sa.Column("app_version_code", sa.Integer, nullable=True),
        sa.Column("build_type", sa.String(20), nullable=True),
        sa.Column("user_tier", sa.String(20), nullable=True),
        sa.Column("current_screen", sa.String(255), nullable=True),
        sa.Column("task_count", sa.Integer, nullable=True),
        sa.Column("habit_count", sa.Integer, nullable=True),
        sa.Column("available_ram_mb", sa.Integer, nullable=True),
        sa.Column("free_storage_mb", sa.Integer, nullable=True),
        sa.Column("network_type", sa.String(20), nullable=True),
        sa.Column("battery_percent", sa.Integer, nullable=True),
        sa.Column("is_charging", sa.Boolean, nullable=True),
        sa.Column("status", sa.String(20), nullable=False, server_default="SUBMITTED"),
        sa.Column("admin_notes", sa.Text, nullable=True),
        sa.Column("diagnostic_log", sa.Text, nullable=True),
        sa.Column("submitted_via", sa.String(20), nullable=True, server_default="backend"),
        sa.Column("created_at", sa.DateTime, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime, server_default=sa.func.now()),
    )


def downgrade() -> None:
    op.drop_table("bug_reports")
