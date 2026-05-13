"""Repository for FundPerformanceSnapshot database operations."""

from datetime import date
from typing import List

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from project.models.fund_performance_snapshot import FundPerformanceSnapshot


class FundPerformanceRepository:
    """Provides query and persistence operations for FundPerformanceSnapshot entities."""

    def __init__(self, session: AsyncSession) -> None:
        """Initialise with an open async database session."""
        self._session = session

    async def find_by_fund_and_period(self, fund_id: int, since: date) -> List[FundPerformanceSnapshot]:
        """Return snapshots for a fund from the given date onwards, ordered ascending."""
        result = await self._session.execute(
            select(FundPerformanceSnapshot)
            .where(FundPerformanceSnapshot.fund_id == fund_id, FundPerformanceSnapshot.date >= since)
            .order_by(FundPerformanceSnapshot.date.asc())
        )
        return list(result.scalars().all())

    async def save(self, snapshot: FundPerformanceSnapshot) -> FundPerformanceSnapshot:
        """Persist a performance snapshot and return the managed instance."""
        self._session.add(snapshot)
        await self._session.flush()
        await self._session.refresh(snapshot)
        return snapshot
