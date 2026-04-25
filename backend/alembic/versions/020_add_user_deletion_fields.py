"""Add user deletion-pending fields for in-app account deletion with 30-day grace.

Soft-delete model: users mark themselves pending via POST /auth/me/deletion.
Sign-in within ``deletion_scheduled_for`` reverts to active. Sign-in after the
window triggers permanent deletion (CASCADE on User clears dependents; backend
also calls Firebase Admin to delete the Auth record). The ``deletion_initiated_from``
column is kept for support triage so we know whether a user requested deletion
from the Android app, the web app, or via email.

Revision ID: 020
Revises: 019
Create Date: 2026-04-25
"""

from alembic import op
import sqlalchemy as sa


revision = "020"
down_revision = "019"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column("deletion_pending_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.add_column(
        "users",
        sa.Column("deletion_scheduled_for", sa.DateTime(timezone=True), nullable=True),
    )
    op.add_column(
        "users",
        sa.Column("deletion_initiated_from", sa.String(20), nullable=True),
    )
    # Used by the lazy-cleanup sweep ("WHERE deletion_scheduled_for < now()")
    # and by the sign-in guard ("SELECT deletion_scheduled_for WHERE id = ?").
    op.create_index(
        "ix_users_deletion_scheduled_for",
        "users",
        ["deletion_scheduled_for"],
    )


def downgrade() -> None:
    op.drop_index("ix_users_deletion_scheduled_for", table_name="users")
    op.drop_column("users", "deletion_initiated_from")
    op.drop_column("users", "deletion_scheduled_for")
    op.drop_column("users", "deletion_pending_at")
