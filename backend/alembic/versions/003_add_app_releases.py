"""Add app_releases table for self-update system

Revision ID: 003
Revises: 002
Create Date: 2026-04-06

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op

revision: str = "003"
down_revision: Union[str, None] = "002"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_table(
        "app_releases",
        sa.Column("id", sa.Integer(), primary_key=True),
        sa.Column("version_code", sa.Integer(), nullable=False, unique=True),
        sa.Column("version_name", sa.String(50), nullable=False),
        sa.Column("release_notes", sa.Text(), nullable=True),
        sa.Column("apk_url", sa.Text(), nullable=False),
        sa.Column("apk_size_bytes", sa.Integer(), nullable=True),
        sa.Column("sha256", sa.String(64), nullable=True),
        sa.Column("min_sdk", sa.Integer(), server_default="26"),
        sa.Column("created_at", sa.DateTime(), server_default=sa.func.now()),
        sa.Column("is_mandatory", sa.Boolean(), server_default="false"),
    )


def downgrade() -> None:
    op.drop_table("app_releases")
