"""ORM model for the InvestmentFund entity."""

from datetime import date
from decimal import Decimal
from typing import Optional

from sqlalchemy import BigInteger, Date, Numeric, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from project.models.base import Base


class InvestmentFund(Base):
    """Represents an investment fund managed by the bank."""

    __tablename__ = "investment_funds"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    naziv: Mapped[str] = mapped_column(String(255), unique=True, nullable=False, index=True)
    opis: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    minimalni_ulog: Mapped[Decimal] = mapped_column(Numeric(19, 4), nullable=False)
    menadzer_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    likvidna_sredstva: Mapped[Decimal] = mapped_column(Numeric(19, 4), nullable=False, default=Decimal("0"))
    account_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    datum_kreiranja: Mapped[date] = mapped_column(Date, nullable=False, default=date.today)
