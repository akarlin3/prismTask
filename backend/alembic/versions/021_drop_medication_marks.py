"""Drop the orphan ``medication_marks`` table.

The medication time-logging chain (Android PRs #743, #744; web PR #745)
landed with `medication_marks` as a planned per-medication mark table.
The UI half ended up using ``medication_tier_states.intended_time``
for slot-granularity time editing instead, leaving ``medication_marks``
provisioned but never written by any production code path on Android,
web, or the backend itself.

Per ``docs/audits/PHASE_D_BUNDLE_AUDIT.md`` Item 3, this migration drops
the table to eliminate a sync-protocol footgun (a future client could
have started writing to it, round-tripping rows that no Android version
reads). The Android side is dropped in the parallel Room migration
``MIGRATION_63_64`` shipped in the same change.

Existing audit rows in ``medication_log_events`` with
``entity_type = "mark"`` are preserved — that table is append-only and
its history stays intact.

Revision ID: 021
Revises: 020
Create Date: 2026-04-25
"""

from alembic import op
import sqlalchemy as sa


revision = "021"
down_revision = "020"
branch_labels = None
depends_on = None


# Reuse the timezone-aware DateTime helper used by 019; SQLite drops
# tzinfo on the column type but Postgres preserves it.
_AWARE = sa.DateTime(timezone=True)


def upgrade() -> None:
    op.drop_index("ix_medication_marks_cloud_id", table_name="medication_marks")
    op.drop_table("medication_marks")


def downgrade() -> None:
    # Recreate the table and its unique index. This mirrors the original
    # 019 schema so a downgrade restores exactly what was dropped, even
    # though no production code ever wrote to it.
    op.create_table(
        "medication_marks",
        sa.Column("id", sa.Integer, primary_key=True),
        sa.Column(
            "user_id", sa.Integer,
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False, index=True,
        ),
        sa.Column("cloud_id", sa.String(64), nullable=True),
        sa.Column(
            "medication_id", sa.Integer,
            sa.ForeignKey("medications.id", ondelete="CASCADE"),
            nullable=False, index=True,
        ),
        sa.Column(
            "tier_state_id", sa.Integer,
            sa.ForeignKey("medication_tier_states.id", ondelete="CASCADE"),
            nullable=False, index=True,
        ),
        sa.Column("intended_time", _AWARE, nullable=True),
        sa.Column("logged_at", _AWARE, nullable=False),
        sa.Column("marked_taken", sa.Boolean, nullable=False, server_default=sa.true()),
        sa.Column("created_at", _AWARE, server_default=sa.func.now()),
        sa.Column("updated_at", _AWARE, server_default=sa.func.now(), onupdate=sa.func.now()),
        sa.UniqueConstraint(
            "user_id", "medication_id", "tier_state_id",
            name="uq_med_mark",
        ),
    )
    op.create_index(
        "ix_medication_marks_cloud_id",
        "medication_marks", ["cloud_id"], unique=True,
    )
