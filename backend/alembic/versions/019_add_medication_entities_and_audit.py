"""Add medication entities (medications, medication_slots, medication_tier_states,
medication_marks) and the medication_log_events audit table.

Wires backend support for the medication time-logging feature: clients can now
push these entities through /sync/push, and the audit table records every
write for Data Safety disclosures + future debugging.

Revision ID: 019
Revises: 018
Create Date: 2026-04-23
"""

from alembic import op
import sqlalchemy as sa


revision = "019"
down_revision = "018"
branch_labels = None
depends_on = None


_AWARE = sa.DateTime(timezone=True)


def upgrade() -> None:
    op.create_table(
        "medications",
        sa.Column("id", sa.Integer, primary_key=True),
        sa.Column(
            "user_id", sa.Integer,
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False, index=True,
        ),
        sa.Column("cloud_id", sa.String(64), nullable=True),
        sa.Column("name", sa.String(255), nullable=False),
        sa.Column("dosage", sa.String(255), nullable=True),
        sa.Column("notes", sa.Text, nullable=True),
        sa.Column("is_active", sa.Boolean, nullable=False, server_default=sa.true()),
        sa.Column("created_at", _AWARE, server_default=sa.func.now()),
        sa.Column("updated_at", _AWARE, server_default=sa.func.now(), onupdate=sa.func.now()),
    )
    op.create_index(
        "ix_medications_cloud_id", "medications", ["cloud_id"], unique=True,
    )

    op.create_table(
        "medication_slots",
        sa.Column("id", sa.Integer, primary_key=True),
        sa.Column(
            "user_id", sa.Integer,
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False, index=True,
        ),
        sa.Column("cloud_id", sa.String(64), nullable=True),
        sa.Column("slot_key", sa.String(32), nullable=False),
        sa.Column("ideal_time", sa.String(5), nullable=True),
        sa.Column("drift_minutes", sa.Integer, nullable=False, server_default="30"),
        sa.Column("is_active", sa.Boolean, nullable=False, server_default=sa.true()),
        sa.Column("created_at", _AWARE, server_default=sa.func.now()),
        sa.Column("updated_at", _AWARE, server_default=sa.func.now(), onupdate=sa.func.now()),
    )
    op.create_index(
        "ix_medication_slots_cloud_id", "medication_slots", ["cloud_id"], unique=True,
    )

    op.create_table(
        "medication_tier_states",
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
            "slot_id", sa.Integer,
            sa.ForeignKey("medication_slots.id", ondelete="CASCADE"),
            nullable=False, index=True,
        ),
        sa.Column("log_date", sa.Date, nullable=False, index=True),
        sa.Column("tier", sa.String(20), nullable=False),
        sa.Column("tier_source", sa.String(20), nullable=False, server_default="computed"),
        sa.Column("intended_time", _AWARE, nullable=True),
        sa.Column("logged_at", _AWARE, nullable=False),
        sa.Column("created_at", _AWARE, server_default=sa.func.now()),
        sa.Column("updated_at", _AWARE, server_default=sa.func.now(), onupdate=sa.func.now()),
        sa.UniqueConstraint(
            "user_id", "medication_id", "log_date", "slot_id",
            name="uq_med_tier_state",
        ),
    )
    op.create_index(
        "ix_medication_tier_states_cloud_id",
        "medication_tier_states", ["cloud_id"], unique=True,
    )

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

    op.create_table(
        "medication_log_events",
        sa.Column("id", sa.Integer, primary_key=True),
        sa.Column(
            "user_id", sa.Integer,
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False, index=True,
        ),
        sa.Column("entity_type", sa.String(20), nullable=False),
        sa.Column("entity_cloud_id", sa.String(64), nullable=True, index=True),
        sa.Column("intended_time", _AWARE, nullable=True),
        sa.Column("logged_at", _AWARE, nullable=False, index=True),
        sa.Column("sync_received_at", _AWARE, server_default=sa.func.now(), nullable=False),
        sa.Column("operation", sa.String(20), nullable=False),
    )
    op.create_index(
        "ix_med_log_events_user_logged_at",
        "medication_log_events", ["user_id", "logged_at"],
    )


def downgrade() -> None:
    op.drop_index("ix_med_log_events_user_logged_at", table_name="medication_log_events")
    op.drop_table("medication_log_events")

    op.drop_index("ix_medication_marks_cloud_id", table_name="medication_marks")
    op.drop_table("medication_marks")

    op.drop_index("ix_medication_tier_states_cloud_id", table_name="medication_tier_states")
    op.drop_table("medication_tier_states")

    op.drop_index("ix_medication_slots_cloud_id", table_name="medication_slots")
    op.drop_table("medication_slots")

    op.drop_index("ix_medications_cloud_id", table_name="medications")
    op.drop_table("medications")
