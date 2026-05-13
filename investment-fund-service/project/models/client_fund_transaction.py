"""ORM model for the ClientFundTransaction entity."""

from datetime import datetime
from decimal import Decimal
from typing import Optional

from sqlalchemy import BigInteger, Boolean, DateTime, Enum as SAEnum, ForeignKey, Numeric, String
from sqlalchemy.orm import Mapped, mapped_column

from project.enums.transaction_status import TransactionStatus
from project.models.base import Base


class ClientFundTransaction(Base):
    """Records each deposit or withdrawal transaction for a client in a fund."""

    __tablename__ = "client_fund_transactions"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    klijent_id: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)
    fund_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("investment_funds.id"), nullable=False, index=True)
    iznos: Mapped[Decimal] = mapped_column(Numeric(19, 4), nullable=False)
    status: Mapped[TransactionStatus] = mapped_column(SAEnum(TransactionStatus, name="transactionstatus"), nullable=False)
    timestamp: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False, default=datetime.utcnow)
    is_inflow: Mapped[bool] = mapped_column(Boolean, nullable=False)
    source_account_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    idempotency_key: Mapped[Optional[str]] = mapped_column(String(64), unique=True, nullable=True, index=True)
