"""Firebase Admin SDK initialization and Storage helpers.

The service account key is loaded from one of:
  1. FIREBASE_SERVICE_ACCOUNT_KEY  — JSON string (for Railway / CI)
  2. FIREBASE_SERVICE_ACCOUNT_KEY_PATH — file path to a .json key file
"""

import json
import os
from functools import lru_cache

import firebase_admin
from firebase_admin import credentials, storage

# Default bucket — override with FIREBASE_STORAGE_BUCKET env var
_DEFAULT_BUCKET = "prismtask-app.firebasestorage.app"


@lru_cache(maxsize=1)
def _get_firebase_app() -> firebase_admin.App:
    """Initialize and cache the Firebase Admin app."""
    key_json = os.environ.get("FIREBASE_SERVICE_ACCOUNT_KEY")
    key_path = os.environ.get("FIREBASE_SERVICE_ACCOUNT_KEY_PATH")

    if key_json:
        info = json.loads(key_json)
        cred = credentials.Certificate(info)
    elif key_path:
        cred = credentials.Certificate(key_path)
    else:
        raise RuntimeError(
            "Firebase credentials not configured. "
            "Set FIREBASE_SERVICE_ACCOUNT_KEY or FIREBASE_SERVICE_ACCOUNT_KEY_PATH."
        )

    bucket_name = os.environ.get("FIREBASE_STORAGE_BUCKET", _DEFAULT_BUCKET)
    return firebase_admin.initialize_app(cred, {"storageBucket": bucket_name})


def get_bucket():
    """Return the default Firebase Storage bucket."""
    _get_firebase_app()
    return storage.bucket()
