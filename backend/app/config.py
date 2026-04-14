import secrets

from pydantic import field_validator
from pydantic_settings import BaseSettings


def _generate_dev_secret() -> str:
    """Generate a random secret for development. Production must set JWT_SECRET_KEY explicitly."""
    return secrets.token_urlsafe(64)


class Settings(BaseSettings):
    DATABASE_URL: str = "postgresql+asyncpg://averytask:averytask@localhost:5432/averytask"
    JWT_SECRET_KEY: str = ""
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 15
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7
    ANTHROPIC_API_KEY: str = ""
    DEPLOY_API_KEY: str = ""
    FIREBASE_SERVICE_ACCOUNT_KEY: str = ""
    FIREBASE_SERVICE_ACCOUNT_KEY_PATH: str = ""
    FIREBASE_STORAGE_BUCKET: str = "prismtask-app.firebasestorage.app"
    ENVIRONMENT: str = "dev"
    CORS_ORIGINS: list[str] = [
        "*",
        "http://localhost:5173",
        "https://web-prismtask-production.up.railway.app",
        "https://app.prismtask.app",
    ]

    @field_validator("CORS_ORIGINS", mode="before")
    @classmethod
    def _parse_cors_origins(cls, v: object) -> object:
        if isinstance(v, str):
            return [origin.strip() for origin in v.split(",") if origin.strip()]
        return v

    @property
    def is_production(self) -> bool:
        return self.ENVIRONMENT == "production"

    @property
    def debug(self) -> bool:
        return not self.is_production

    # These origins are always allowed in production regardless of CORS_ORIGINS env var.
    # This prevents an accidental CORS_ORIGINS=* env var from locking out the web app.
    _REQUIRED_PRODUCTION_ORIGINS: list[str] = [
        "https://app.prismtask.app",
        "https://web-prismtask-production.up.railway.app",
    ]

    @property
    def effective_cors_origins(self) -> list[str]:
        if self.is_production:
            origins = [o for o in self.CORS_ORIGINS if o != "*"]
            for origin in self._REQUIRED_PRODUCTION_ORIGINS:
                if origin not in origins:
                    origins.append(origin)
            return origins
        return self.CORS_ORIGINS

    def get_jwt_secret(self) -> str:
        if self.JWT_SECRET_KEY:
            return self.JWT_SECRET_KEY
        if self.is_production:
            raise RuntimeError("JWT_SECRET_KEY must be set in production")
        # Auto-generate for local dev only (tokens won't persist across restarts)
        if not hasattr(self, "_dev_secret"):
            object.__setattr__(self, "_dev_secret", _generate_dev_secret())
        return self._dev_secret  # type: ignore[attr-defined]

    model_config = {"env_file": ".env", "extra": "ignore"}


settings = Settings()
