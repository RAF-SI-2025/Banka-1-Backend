"""Application settings loaded from environment variables."""

from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict

BasicSettings = BaseSettings


class Settings(BasicSettings):
    """Central configuration class; all values read from .env or environment."""

    db_host: str = "localhost"
    db_port: int = 5432
    db_name: str = "investment_fund_db"
    db_user: str = "postgres"
    db_password: str = "postgres"

    redis_host: str = "redis"
    redis_port: int = 6379
    redis_ttl_seconds: int = 60

    banking_service_url: str = "http://banking-service:8084"
    employee_service_url: str = "http://employee-service:8081"
    order_service_url: str = "http://order-service:8088"

    jwt_secret: str
    jwt_algorithm: str = "HS256"

    commission_rate: float = 0.005

    host: str = "0.0.0.0"
    port: int = 8092
    root_path: str = ""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    def get_database_url(self) -> str:
        """Return the asyncpg-compatible database URL."""
        return f"postgresql+asyncpg://{self.db_user}:{self.db_password}@{self.db_host}:{self.db_port}/{self.db_name}"

    def get_redis_url(self) -> str:
        """Return the Redis connection URL."""
        return f"redis://{self.redis_host}:{self.redis_port}"


_settings: Optional[Settings] = None


def get_settings() -> Settings:
    """Return the singleton Settings instance."""
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings
