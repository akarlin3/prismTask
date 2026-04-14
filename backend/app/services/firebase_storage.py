"""Firebase Admin SDK initialization and Storage helpers.

The service account key is loaded from one of:
  1. FIREBASE_SERVICE_ACCOUNT_KEY  — JSON string (for Railway / CI)
  2. FIREBASE_SERVICE_ACCOUNT_KEY_PATH — file path to a .json key file
"""

import json
import logging
from functools import lru_cache

import firebase_admin
from firebase_admin import credentials, storage

from app.config import settings

logger = logging.getLogger(__name__)

# Default bucket — override with FIREBASE_STORAGE_BUCKET env var
_DEFAULT_BUCKET = "prismtask-app.firebasestorage.app"


@lru_cache(maxsize=1)
def _get_firebase_app() -> firebase_admin.App:
    """Initialize and cache the Firebase Admin app.

    If the default app is already initialized (e.g. in tests or after a hot
    reload) we reuse it rather than calling initialize_app() again, which
    would raise ValueError.
    """
    # Reuse an existing default app if one was already registered.
    try:
        return firebase_admin.get_app()
    except ValueError:
        pass  # No app initialized yet — proceed with initialization below.

    key_json = settings.FIREBASE_SERVICE_ACCOUNT_KEY
    key_path = settings.FIREBASE_SERVICE_ACCOUNT_KEY_PATH

    if key_json:
        # Log a safe prefix so we can verify the env var is loaded without
        # exposing the full key in logs.
        logger.info(
            "FIREBASE_SERVICE_ACCOUNT_KEY loaded (first 20 chars): %s",
            key_json[:20],
        )
        try:
            info = json.loads(key_json)
        except json.JSONDecodeError as exc:
            raise RuntimeError(
                f"FIREBASE_SERVICE_ACCOUNT_KEY is not valid JSON: {exc}"
            ) from exc
        cred = credentials.Certificate(info)
    elif key_path:
        logger.info("Using FIREBASE_SERVICE_ACCOUNT_KEY_PATH: %s", key_path)
        cred = credentials.Certificate(key_path)
    else:
        logger.warning(
            "Neither FIREBASE_SERVICE_ACCOUNT_KEY nor FIREBASE_SERVICE_ACCOUNT_KEY_PATH "
            "is set; Firebase Storage will be unavailable."
        )
        raise RuntimeError(
            "Firebase credentials not configured. "
            "Set FIREBASE_SERVICE_ACCOUNT_KEY or FIREBASE_SERVICE_ACCOUNT_KEY_PATH."
        )

    bucket_name = settings.FIREBASE_STORAGE_BUCKET
    logger.info("Initializing Firebase Admin app with storage bucket: %s", bucket_name)
    return firebase_admin.initialize_app(cred, {"storageBucket": bucket_name})


def get_bucket():
    """Return the default Firebase Storage bucket."""
    _get_firebase_app()
    return storage.bucket()
