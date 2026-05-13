"""Repository for FundPerformanceSnapshot database operations."""

from datetime import date
from decimal import Decimal
from typing import List

from sqlalchemy import select
from sqlalchemy.dialects.postgresql import insert
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

    async def upsert_snapshot(self, fund_id: int, snapshot_date: date, vrednost_fonda: Decimal, profit: Decimal, likvidna_sredstva: Decimal) -> None:
        """Insert or update a daily snapshot, updating values if one already exists for that fund+date."""
        stmt = (
            insert(FundPerformanceSnapshot)
            .values(fund_id=fund_id, date=snapshot_date, vrednost_fonda=vrednost_fonda, profit=profit, likvidna_sredstva=likvidna_sredstva)
            .on_conflict_do_update(
                constraint="uq_fund_date",
                set_={"vrednost_fonda": vrednost_fonda, "profit": profit, "likvidna_sredstva": likvidna_sredstva},
            )
        )
        await self._session.execute(stmt)
        await self._session.flush()
