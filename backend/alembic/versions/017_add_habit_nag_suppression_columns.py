"""Add habit nag suppression override columns

Revision ID: 017
Revises: 016
Create Date: 2026-04-21
"""

from alembic import op
import sqlalchemy as sa

revision = "017"
down_revision = "016"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "habits",
        sa.Column(
            "nag_suppression_override_enabled",
            sa.Boolean,
            nullable=False,
            server_default="false",
        ),
    )
    op.add_column(
        "habits",
        sa.Column(
            "nag_suppression_days_override",
            sa.Integer,
            nullable=False,
            server_default="-1",
        ),
    )


def downgrade() -> None:
    op.drop_column("habits", "nag_suppression_days_override")
    op.drop_column("habits", "nag_suppression_override_enabled")
