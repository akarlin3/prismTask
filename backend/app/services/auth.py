from datetime import datetime, timedelta, timezone

import jwt
from passlib.context import CryptContext

from app.config import settings
from app.services.firebase_storage import _get_firebase_app

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(password: str) -> str:
    return pwd_context.hash(password)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    return pwd_context.verify(plain_password, hashed_password)


def create_access_token(data: dict) -> str:
    to_encode = data.copy()
    expire = datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire, "type": "access"})
    return jwt.encode(to_encode, settings.get_jwt_secret(), algorithm=settings.JWT_ALGORITHM)


def create_refresh_token(data: dict) -> str:
    to_encode = data.copy()
    expire = datetime.now(timezone.utc) + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS)
    to_encode.update({"exp": expire, "type": "refresh"})
    return jwt.encode(to_encode, settings.get_jwt_secret(), algorithm=settings.JWT_ALGORITHM)


def decode_token(token: str) -> dict | None:
    try:
        payload = jwt.decode(token, settings.get_jwt_secret(), algorithms=[settings.JWT_ALGORITHM])
        return payload
    except (jwt.InvalidTokenError, jwt.ExpiredSignatureError):
        return None


def verify_firebase_token(id_token: str) -> dict | None:
    """Verify a Firebase ID token and return the decoded claims.

    Returns None if verification fails (invalid token, expired, etc.).
    """
    from firebase_admin import auth as firebase_auth

    try:
        _get_firebase_app()
        decoded = firebase_auth.verify_id_token(id_token)
        return decoded
    except Exception:
        return None


def delete_firebase_user(uid: str) -> bool:
    """Permanently delete a Firebase Auth user record by UID.

    Used by the post-grace permanent-deletion path. Returns True on success
    (including the not-found case — already deleted is the desired terminal
    state). Returns False on any other failure so the caller can decide
    whether to retry; the caller logs the exception.
    """
    from firebase_admin import auth as firebase_auth

    try:
        _get_firebase_app()
        firebase_auth.delete_user(uid)
        return True
    except firebase_auth.UserNotFoundError:
        return True
    except Exception:
        return False
