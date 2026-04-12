"""Add tier column to users table

Revision ID: 009
Revises: 008
Create Date: 2026-04-12
"""

from alembic import op
import sqlalchemy as sa

revision = "009"
down_revision = "008"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column("tier", sa.String(20), nullable=False, server_default="FREE"),
    )


def downgrade() -> None:
    op.drop_column("users", "tier")
