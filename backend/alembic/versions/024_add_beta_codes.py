"""Add ``beta_codes`` and ``beta_code_redemptions`` for beta-tester
Pro-unlock codes.

Per ``docs/audits/D_SERIES_BETA_TESTER_UNLOCK_CODES_AUDIT.md`` (Phase 1,
verdict B.1). Two tables: a code definition table and an account-bound
redemption table. ``beta_code_redemptions.user_id`` FKs to ``users.id``
(matching every other user-keyed table) so cascading user deletes also
sweep redemptions.

The redemption row carries a snapshot of ``grants_pro_until`` at
redemption time so that revoking the code (``beta_codes.revoked_at``)
does NOT retroactively invalidate existing redemptions — current
redeemers keep their grant until their snapshot expires.

Revision ID: 024
Revises: 023
Create Date: 2026-05-05
"""

from alembic import op
import sqlalchemy as sa


revision = "024"
down_revision = "023"
branch_labels = None
depends_on = None


_AWARE = sa.DateTime(timezone=True)


def upgrade() -> None:
    op.create_table(
        "beta_codes",
        sa.Column("code", sa.String(64), primary_key=True),
        sa.Column("description", sa.String(500), nullable=True),
        sa.Column(
            "valid_from", _AWARE,
            server_default=sa.func.now(), nullable=False,
        ),
        sa.Column("valid_until", _AWARE, nullable=True),
        sa.Column("grants_pro_until", _AWARE, nullable=True),
        sa.Column("max_redemptions", sa.Integer, nullable=True),
        sa.Column(
            "redemption_count", sa.Integer,
            server_default=sa.text("0"), nullable=False,
        ),
        sa.Column("revoked_at", _AWARE, nullable=True),
        sa.Column(
            "created_at", _AWARE,
            server_default=sa.func.now(), nullable=False,
        ),
    )

    op.create_table(
        "beta_code_redemptions",
        sa.Column("id", sa.Integer, primary_key=True),
        sa.Column(
            "code", sa.String(64),
            sa.ForeignKey("beta_codes.code"), nullable=False,
        ),
        sa.Column(
            "user_id", sa.Integer,
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False, index=True,
        ),
        sa.Column(
            "redeemed_at", _AWARE,
            server_default=sa.func.now(), nullable=False,
        ),
        sa.Column("grants_pro_until", _AWARE, nullable=True),
        sa.UniqueConstraint("code", "user_id", name="uq_beta_redeem_code_user"),
    )


def downgrade() -> None:
    op.drop_table("beta_code_redemptions")
    op.drop_table("beta_codes")
