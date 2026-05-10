"""Async database engine and session factory."""

from contextlib import asynccontextmanager
from typing import AsyncGenerator, Optional

from sqlalchemy.ext.asyncio import AsyncEngine, AsyncSession, async_sessionmaker, create_async_engine

from project.config.settings import Settings


class Database:
    """Manages the async SQLAlchemy engine and per-request sessions."""

    def __init__(self, settings: Settings) -> None:
        """Initialise with application settings; engine is created on connect()."""
        self._settings = settings
        self._engine: Optional[AsyncEngine] = None
        self._session_factory: Optional[async_sessionmaker[AsyncSession]] = None

    async def connect(self) -> None:
        """Create the async engine and session factory. Schema is managed by Alembic migrations."""
        self._engine = create_async_engine(self._settings.get_database_url(), echo=False, pool_pre_ping=True)
        self._session_factory = async_sessionmaker(self._engine, expire_on_commit=False, class_=AsyncSession)

    async def disconnect(self) -> None:
        """Dispose the engine and release all connections."""
        if self._engine:
            await self._engine.dispose()

    @asynccontextmanager
    async def get_session(self) -> AsyncGenerator[AsyncSession, None]:
        """Yield a transactional AsyncSession; rolls back on exception."""
        if self._session_factory is None:
            raise RuntimeError("Database.connect() must be called before get_session()")
        async with self._session_factory() as session:
            try:
                yield session
                await session.commit()
            except Exception:
                await session.rollback()
                raise

    @property
    def engine(self) -> AsyncEngine:
        """Return the underlying async engine."""
        if self._engine is None:
            raise RuntimeError("Database not connected")
        return self._engine
