"""ORM model for the FundPerformanceSnapshot entity."""

from datetime import date
from decimal import Decimal

from sqlalchemy import BigInteger, Date, ForeignKey, Numeric, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from project.models.base import Base


class FundPerformanceSnapshot(Base):
    """Daily snapshot of a fund's total value, profit, and liquid assets."""

    __tablename__ = "fund_performance_snapshots"
    __table_args__ = (UniqueConstraint("fund_id", "date", name="uq_fund_date"),)

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    fund_id: Mapped[int] = mapped_column(BigInteger, ForeignKey("investment_funds.id"), nullable=False, index=True)
    date: Mapped[date] = mapped_column(Date, nullable=False)
    vrednost_fonda: Mapped[Decimal] = mapped_column(Numeric(19, 4), nullable=False)
    profit: Mapped[Decimal] = mapped_column(Numeric(19, 4), nullable=False)
    likvidna_sredstva: Mapped[Decimal] = mapped_column(Numeric(19, 4), nullable=False)
