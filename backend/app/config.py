from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    DATABASE_URL: str = "postgresql+asyncpg://averytask:averytask@localhost:5432/averytask"
    JWT_SECRET_KEY: str = "dev-secret-key-change-in-production"
    JWT_ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 15
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7
    ANTHROPIC_API_KEY: str = ""
    DEPLOY_API_KEY: str = ""
    ENVIRONMENT: str = "dev"
    CORS_ORIGINS: list[str] = ["*"]

    @property
    def is_production(self) -> bool:
        return self.ENVIRONMENT == "production"

    @property
    def debug(self) -> bool:
        return not self.is_production

    @property
    def effective_cors_origins(self) -> list[str]:
        if self.is_production:
            return [o for o in self.CORS_ORIGINS if o != "*"]
        return self.CORS_ORIGINS

    model_config = {"env_file": ".env", "extra": "ignore"}


settings = Settings()
