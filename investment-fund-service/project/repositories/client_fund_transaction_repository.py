"""Repository for ClientFundTransaction database operations."""

from decimal import Decimal
from typing import List, Optional

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from project.enums.transaction_status import TransactionStatus
from project.models.client_fund_transaction import ClientFundTransaction


class ClientFundTransactionRepository:
    """Provides query and persistence operations for ClientFundTransaction entities."""

    def __init__(self, session: AsyncSession) -> None:
        """Initialise with an open async database session."""
        self._session = session

    async def find_by_id(self, tx_id: int) -> Optional[ClientFundTransaction]:
        """Return the transaction with the given ID, or None."""
        result = await self._session.execute(select(ClientFundTransaction).where(ClientFundTransaction.id == tx_id))
        return result.scalar_one_or_none()

    async def find_by_idempotency_key(self, key: str) -> Optional[ClientFundTransaction]:
        """Return an existing transaction matching the idempotency key, or None."""
        result = await self._session.execute(select(ClientFundTransaction).where(ClientFundTransaction.idempotency_key == key))
        return result.scalar_one_or_none()

    async def find_all(self) -> List[ClientFundTransaction]:
        """Return all transactions across all funds and clients."""
        result = await self._session.execute(select(ClientFundTransaction))
        return list(result.scalars().all())

    async def find_by_fund_id(self, fund_id: int) -> List[ClientFundTransaction]:
        """Return all transactions for the given fund."""
        result = await self._session.execute(select(ClientFundTransaction).where(ClientFundTransaction.fund_id == fund_id))
        return list(result.scalars().all())

    async def find_by_klijent_id(self, klijent_id: int) -> List[ClientFundTransaction]:
        """Return all transactions for the given client."""
        result = await self._session.execute(select(ClientFundTransaction).where(ClientFundTransaction.klijent_id == klijent_id))
        return list(result.scalars().all())

    async def find_pending_outflow_by_fund(self, fund_id: int) -> List[ClientFundTransaction]:
        """Return all PENDING outflow transactions for the given fund (used by liquidation polling)."""
        result = await self._session.execute(
            select(ClientFundTransaction)
            .where(ClientFundTransaction.fund_id == fund_id, ClientFundTransaction.status == TransactionStatus.PENDING, ClientFundTransaction.is_inflow.is_(False))
        )
        return list(result.scalars().all())

    async def save(self, tx: ClientFundTransaction) -> ClientFundTransaction:
        """Persist a new transaction and return the managed instance."""
        self._session.add(tx)
        await self._session.flush()
        await self._session.refresh(tx)
        return tx

    async def update_status(self, tx_id: int, status: TransactionStatus) -> None:
        """Update the status of a transaction identified by tx_id."""
        tx = await self.find_by_id(tx_id)
        if tx:
            tx.status = status
            await self._session.flush()

    async def sum_inflows_by_fund(self, fund_id: int) -> Decimal:
        """Return the total of all COMPLETED inflow transaction amounts for the fund."""
        result = await self._session.execute(
            select(func.coalesce(func.sum(ClientFundTransaction.iznos), Decimal("0")))
            .where(ClientFundTransaction.fund_id == fund_id, ClientFundTransaction.is_inflow.is_(True), ClientFundTransaction.status == TransactionStatus.COMPLETED)
        )
        return result.scalar_one() or Decimal("0")
