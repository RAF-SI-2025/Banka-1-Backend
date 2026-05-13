"""Repository for InvestmentFund database operations."""

from decimal import Decimal
from typing import List, Optional

from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from project.models.investment_fund import InvestmentFund


class InvestmentFundRepository:
    """Provides CRUD and query operations for InvestmentFund entities."""

    def __init__(self, session: AsyncSession) -> None:
        """Initialise with an open async database session."""
        self._session = session

    async def find_by_id(self, fund_id: int) -> Optional[InvestmentFund]:
        """Return the fund with the given ID, or None if not found."""
        result = await self._session.execute(select(InvestmentFund).where(InvestmentFund.id == fund_id))
        return result.scalar_one_or_none()

    async def find_by_naziv(self, naziv: str) -> Optional[InvestmentFund]:
        """Return the fund with the given name, or None if not found."""
        result = await self._session.execute(select(InvestmentFund).where(InvestmentFund.naziv == naziv))
        return result.scalar_one_or_none()

    async def find_all(self) -> List[InvestmentFund]:
        """Return all investment funds ordered by creation date descending."""
        result = await self._session.execute(select(InvestmentFund).order_by(InvestmentFund.datum_kreiranja.desc()))
        return list(result.scalars().all())

    async def save(self, fund: InvestmentFund) -> InvestmentFund:
        """Persist a new InvestmentFund and return the managed instance."""
        self._session.add(fund)
        await self._session.flush()
        await self._session.refresh(fund)
        return fund

    async def update(self, fund: InvestmentFund) -> InvestmentFund:
        """Merge changes to an existing InvestmentFund and return it."""
        self._session.add(fund)
        await self._session.flush()
        await self._session.refresh(fund)
        return fund

    async def update_likvidna_sredstva(self, fund_id: int, delta: Decimal) -> None:
        """Atomically add delta (positive or negative) to likvidna_sredstva."""
        await self._session.execute(
            update(InvestmentFund)
            .where(InvestmentFund.id == fund_id)
            .values(likvidna_sredstva=InvestmentFund.likvidna_sredstva + delta)
        )
