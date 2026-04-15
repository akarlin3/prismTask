"""Google Play Billing server-side receipt verification.

The Android client reports a successful purchase to the backend via
``PATCH /auth/me/tier``. Before elevating the user's tier, the backend
must verify the purchase token with the Google Play Developer API so a
malicious client cannot simply POST ``{"tier": "ULTRA"}`` and get a free
upgrade.

If ``GOOGLE_PLAY_SERVICE_ACCOUNT_KEY`` (or the ``_PATH`` variant) is not
configured, ``validate_purchase`` fails closed in production and is
permissive in development so local testing is not blocked.
"""
from __future__ import annotations

import json
import logging
import os
from dataclasses import dataclass
from typing import Optional

from app.config import settings

logger = logging.getLogger(__name__)


# Map of Google Play product SKUs to the tier they unlock. The SKUs must
# match those registered in the Play Console for the PrismTask app.
PRODUCT_TIER_MAP: dict[str, str] = {
    "prismtask_pro_monthly": "PRO",
    "prismtask_pro_yearly": "PRO",
    "prismtask_premium_monthly": "PREMIUM",
    "prismtask_premium_yearly": "PREMIUM",
    "prismtask_ultra_monthly": "ULTRA",
    "prismtask_ultra_yearly": "ULTRA",
}

ANDROID_PACKAGE_NAME = "com.averycorp.prismtask"


@dataclass
class ValidationResult:
    ok: bool
    detail: str
    validated_tier: Optional[str] = None


def _service_account_info() -> Optional[dict]:
    raw = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_KEY")
    if raw:
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            logger.error("GOOGLE_PLAY_SERVICE_ACCOUNT_KEY is not valid JSON")
            return None
    path = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_KEY_PATH")
    if path and os.path.exists(path):
        try:
            with open(path) as f:
                return json.load(f)
        except (OSError, json.JSONDecodeError) as exc:
            logger.error("Failed to read service account file %s: %s", path, exc)
            return None
    return None


def validate_purchase(
    claimed_tier: str,
    purchase_token: Optional[str],
    product_id: Optional[str],
) -> ValidationResult:
    """Validate a Google Play purchase receipt and return the tier it unlocks.

    - Downgrades to ``FREE`` are always allowed (cancellation/refund flow).
    - All other tier changes require a purchase_token + product_id that the
      Google Play Developer API confirms as valid.
    - In dev (``ENVIRONMENT != "production"``) without credentials
      configured, the check is skipped to avoid blocking local testing.
      This MUST never apply in production — operators must configure
      ``GOOGLE_PLAY_SERVICE_ACCOUNT_KEY`` before deploying.
    """
    claimed_tier = (claimed_tier or "").upper()
    if claimed_tier == "FREE":
        return ValidationResult(ok=True, detail="Downgrade to FREE", validated_tier="FREE")

    if not purchase_token or not product_id:
        return ValidationResult(
            ok=False,
            detail="purchase_token and product_id are required for paid tiers",
        )

    expected_tier = PRODUCT_TIER_MAP.get(product_id)
    if expected_tier is None:
        return ValidationResult(ok=False, detail=f"Unknown product_id: {product_id}")
    if expected_tier != claimed_tier:
        return ValidationResult(
            ok=False,
            detail=(
                f"Product {product_id} unlocks {expected_tier}, "
                f"but client claimed {claimed_tier}"
            ),
        )

    sa_info = _service_account_info()
    if sa_info is None:
        if settings.is_production:
            logger.error(
                "Google Play service account key is not configured — "
                "rejecting tier elevation in production."
            )
            return ValidationResult(
                ok=False,
                detail="Server is not configured to validate purchases",
            )
        logger.warning(
            "Google Play service account key missing — skipping validation "
            "(dev environment only)"
        )
        return ValidationResult(ok=True, detail="dev-skip", validated_tier=expected_tier)

    try:
        # Imported lazily so the test suite can run without google-api-python-client
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
    except ImportError:
        logger.error(
            "google-api-python-client is not installed; cannot validate "
            "Play purchase. Install with `pip install google-api-python-client`."
        )
        return ValidationResult(
            ok=False,
            detail="Server is not configured to validate purchases",
        )

    try:
        credentials = service_account.Credentials.from_service_account_info(
            sa_info,
            scopes=["https://www.googleapis.com/auth/androidpublisher"],
        )
        service = build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)
        response = (
            service.purchases()
            .subscriptionsv2()
            .get(packageName=ANDROID_PACKAGE_NAME, token=purchase_token)
            .execute()
        )
    except Exception as exc:  # noqa: BLE001 — Google client raises many exception types
        logger.error("Google Play purchase validation failed: %s", exc)
        return ValidationResult(ok=False, detail="Purchase validation failed")

    subscription_state = response.get("subscriptionState", "")
    acknowledged = response.get("acknowledgementState", "")
    # Active states per Google Play docs.
    active_states = {
        "SUBSCRIPTION_STATE_ACTIVE",
        "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    }
    if subscription_state not in active_states:
        return ValidationResult(
            ok=False,
            detail=f"Subscription is not active: {subscription_state}",
        )
    if acknowledged == "ACKNOWLEDGEMENT_STATE_PENDING":
        logger.info("Subscription pending acknowledgement for product %s", product_id)

    line_items = response.get("lineItems", [])
    matched = any(item.get("productId") == product_id for item in line_items)
    if not matched:
        return ValidationResult(
            ok=False,
            detail="Purchase token does not contain the claimed product",
        )

    return ValidationResult(ok=True, detail="Validated", validated_tier=expected_tier)
