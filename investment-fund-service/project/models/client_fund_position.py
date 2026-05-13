"""ORM model for the ClientFundPosition entity."""

from datetime import datetime
from decimal import Decimal

from sqlalchemy import BigInteger, DateTime, ForeignKey, Numeric, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from project.models.base import Base


class ClientFundPosition(Base):
    """Tracks the cumulative invested amount of a client in a specific fund."""

    __tablename__ = "client_fund_positions"
    __table_args__ = (UniqueConstraint("klijent_id", "fund_id", name="uq_client_fund"),)

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    klijent_id: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)
    fund_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("investment_funds.id"), nullable=False, index=True)
    ukupan_ulozeni_iznos: Mapped[Decimal] = mapped_column(Numeric(19, 4), nullable=False, default=Decimal("0"))
    datum_poslednje_promene: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
