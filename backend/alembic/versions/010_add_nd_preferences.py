"""Add neurodivergent preferences table

Revision ID: 010
Revises: 009
Create Date: 2026-04-12
"""

from alembic import op
import sqlalchemy as sa

revision = "010"
down_revision = "009"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "nd_preferences",
        sa.Column("id", sa.Integer, primary_key=True),
        sa.Column(
            "user_id",
            sa.Integer,
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
            unique=True,
            index=True,
        ),
        # Top-level mode toggles
        sa.Column("adhd_mode_enabled", sa.Boolean, nullable=False, server_default="false"),
        sa.Column("calm_mode_enabled", sa.Boolean, nullable=False, server_default="false"),
        # Calm Mode sub-settings
        sa.Column("reduce_animations", sa.Boolean, nullable=False, server_default="false"),
        sa.Column("muted_color_palette", sa.Boolean, nullable=False, server_default="false"),
        sa.Column("quiet_mode", sa.Boolean, nullable=False, server_default="false"),
        sa.Column("reduce_haptics", sa.Boolean, nullable=False, server_default="false"),
        sa.Column("soft_contrast", sa.Boolean, nullable=False, server_default="false"),
        # ADHD Mode sub-settings
        sa.Column("task_decomposition_enabled", sa.Boolean, nullable=False, server_default="false"),
        sa.Column("focus_guard_enabled", sa.Boolean, nullable=False, server_default="false"),
        sa.Column("body_doubling_enabled", sa.Boolean, nullable=False, server_default="false"),
        sa.Column("check_in_interval_minutes", sa.Integer, nullable=False, server_default="25"),
        sa.Column("completion_animations", sa.Boolean, nullable=False, server_default="false"),
        sa.Column("streak_celebrations", sa.Boolean, nullable=False, server_default="false"),
        sa.Column("show_progress_bars", sa.Boolean, nullable=False, server_default="false"),
        sa.Column("forgiveness_streaks", sa.Boolean, nullable=False, server_default="false"),
        # Timestamps
        sa.Column("created_at", sa.DateTime, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime, server_default=sa.func.now(), onupdate=sa.func.now()),
    )


def downgrade() -> None:
    op.drop_table("nd_preferences")
