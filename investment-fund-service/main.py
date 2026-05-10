"""Entry point for the investment-fund-service FastAPI application."""

from contextlib import asynccontextmanager
from typing import Optional

import uvicorn
import httpx
import redis.asyncio as aioredis
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
from fastapi import FastAPI
from fastapi.openapi.utils import get_openapi

from project.config.settings import get_settings
from project.database.database import Database
from project.dependencies import set_http_client, set_redis_client
from project.exceptions.http_exception_handler import HttpExceptionHandler
from project.middleware.auth_middleware import AuthMiddleware
from project.routers.bank_profit_router import BankProfitRouter
from project.routers.fund_router import FundRouter
from project.routers.investment_router import InvestmentRouter
from project.routers.performance_router import PerformanceRouter
from project.routers.position_router import PositionRouter
from project.routers.redemption_router import RedemptionRouter
from project.routers.transaction_router import TransactionRouter


def create_app() -> FastAPI:
    """Create, configure, and return the FastAPI application instance."""
    settings = get_settings()
    database = Database(settings)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        """Manage application startup and shutdown lifecycle."""
        await database.connect()
        app.state.database = database

        http_client = httpx.AsyncClient(timeout=30.0)
        set_http_client(http_client)
        app.state.http_client = http_client

        redis_client: Optional[aioredis.Redis] = None
        try:
            redis_client = aioredis.from_url(settings.get_redis_url(), encoding="utf-8", decode_responses=False)
            await redis_client.ping()
            set_redis_client(redis_client)
            app.state.redis = redis_client
        except Exception:
            set_redis_client(None)

        scheduler = AsyncIOScheduler()
        scheduler.add_job(_noop_snapshot, CronTrigger(hour=0, minute=5), id="daily_snapshot", replace_existing=True)
        scheduler.start()
        app.state.scheduler = scheduler

        yield

        scheduler.shutdown(wait=False)
        await http_client.aclose()
        if redis_client:
            await redis_client.aclose()
        await database.disconnect()

    app = FastAPI(title="Investment Fund Service", version="1.0.0", description="Manages investment funds, client positions, and performance tracking.", lifespan=lifespan)

    app.add_middleware(AuthMiddleware, settings=settings)
    HttpExceptionHandler(app).register()

    app.include_router(FundRouter().router)
    app.include_router(InvestmentRouter().router)
    app.include_router(RedemptionRouter().router)
    app.include_router(PerformanceRouter().router)
    app.include_router(PositionRouter().router)
    app.include_router(TransactionRouter().router)
    app.include_router(BankProfitRouter().router)

    def custom_openapi():
        """Return OpenAPI schema with Bearer security scheme."""
        if app.openapi_schema:
            return app.openapi_schema
        schema = get_openapi(title=app.title, version=app.version, description=app.description, routes=app.routes)
        schema.setdefault("components", {}).setdefault("securitySchemes", {})["bearerAuth"] = {"type": "http", "scheme": "bearer", "bearerFormat": "JWT"}
        for path in schema.get("paths", {}).values():
            for operation in path.values():
                operation.setdefault("security", [{"bearerAuth": []}])
        app.openapi_schema = schema
        return schema

    app.openapi = custom_openapi

    @app.get("/health", tags=["health"])
    async def health():
        """Return service liveness status."""
        return {"status": "ok", "service": "investment-fund-service"}

    return app


async def _noop_snapshot() -> None:
    """Placeholder scheduler job; in production wire to FundPerformanceService.take_daily_snapshot."""
    pass


if __name__ == "__main__":
    """Run the service directly with uvicorn."""
    _s = get_settings()
    uvicorn.run("main:create_app", factory=True, host=_s.host, port=_s.port, reload=True)
