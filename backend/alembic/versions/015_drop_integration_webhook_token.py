"""Drop unused webhook_token column from integration_configs

The column was populated on creation but never read anywhere in the
codebase. Dropping it removes dead state and a stale unique index.

Revision ID: 015
Revises: 014
Create Date: 2026-04-17
"""

from alembic import op
import sqlalchemy as sa


revision = "015"
down_revision = "014"
branch_labels = None
depends_on = None


def upgrade() -> None:
    with op.batch_alter_table("integration_configs") as batch_op:
        batch_op.drop_column("webhook_token")


def downgrade() -> None:
    op.add_column(
        "integration_configs",
        sa.Column("webhook_token", sa.String(length=64), nullable=True, unique=True),
    )
