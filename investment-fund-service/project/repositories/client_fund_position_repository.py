"""Repository for ClientFundPosition database operations."""

from datetime import datetime
from decimal import Decimal
from typing import List, Optional

from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from project.models.client_fund_position import ClientFundPosition


class ClientFundPositionRepository:
    """Provides query and upsert operations for ClientFundPosition entities."""

    def __init__(self, session: AsyncSession) -> None:
        """Initialise with an open async database session."""
        self._session = session

    async def find_by_id(self, pos_id: int) -> Optional[ClientFundPosition]:
        """Return the position with the given ID, or None."""
        result = await self._session.execute(select(ClientFundPosition).where(ClientFundPosition.id == pos_id))
        return result.scalar_one_or_none()

    async def find_by_klijent_and_fund(self, klijent_id: int, fund_id: int) -> Optional[ClientFundPosition]:
        """Return the position for a specific client-fund pair, or None."""
        result = await self._session.execute(
            select(ClientFundPosition)
            .where(ClientFundPosition.klijent_id == klijent_id, ClientFundPosition.fund_id == fund_id)
        )
        return result.scalar_one_or_none()

    async def find_by_klijent_id(self, klijent_id: int) -> List[ClientFundPosition]:
        """Return all positions for the given client across all funds."""
        result = await self._session.execute(select(ClientFundPosition).where(ClientFundPosition.klijent_id == klijent_id))
        return list(result.scalars().all())

    async def find_by_fund_id(self, fund_id: int) -> List[ClientFundPosition]:
        """Return all client positions for the given fund."""
        result = await self._session.execute(select(ClientFundPosition).where(ClientFundPosition.fund_id == fund_id))
        return list(result.scalars().all())

    async def sum_ulozeni_iznos_by_fund(self, fund_id: int) -> Decimal:
        """Return the total invested amount across all clients in the fund."""
        result = await self._session.execute(
            select(func.coalesce(func.sum(ClientFundPosition.ukupan_ulozeni_iznos), Decimal("0")))
            .where(ClientFundPosition.fund_id == fund_id)
        )
        return result.scalar_one() or Decimal("0")

    async def upsert(self, klijent_id: int, fund_id: int, delta: Decimal) -> ClientFundPosition:
        """Insert or update the position, adding delta to ukupan_ulozeni_iznos atomically."""
        now = datetime.utcnow()
        stmt = (
            pg_insert(ClientFundPosition)
            .values(klijent_id=klijent_id, fund_id=fund_id, ukupan_ulozeni_iznos=delta, datum_poslednje_promene=now)
            .on_conflict_do_update(
                constraint="uq_client_fund",
                set_={"ukupan_ulozeni_iznos": ClientFundPosition.ukupan_ulozeni_iznos + delta, "datum_poslednje_promene": now},
            )
            .returning(ClientFundPosition)
        )
        result = await self._session.execute(stmt)
        return result.scalar_one()

    async def save(self, pos: ClientFundPosition) -> ClientFundPosition:
        """Persist a new position and return the managed instance."""
        self._session.add(pos)
        await self._session.flush()
        await self._session.refresh(pos)
        return pos
