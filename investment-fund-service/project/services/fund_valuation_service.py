"""Service for computing derived fund valuation fields with Redis caching."""

from decimal import Decimal
from typing import Optional

import redis.asyncio as aioredis

from project.clients.order_client import OrderClient
from project.models.investment_fund import InvestmentFund
from project.repositories.client_fund_position_repository import ClientFundPositionRepository
from project.repositories.client_fund_transaction_repository import ClientFundTransactionRepository
from project.repositories.investment_fund_repository import InvestmentFundRepository


class FundValuationService:
    """Computes runtime-only fund metrics; caches vrednost_fonda in Redis for 60 s."""

    def __init__(self, fund_repo: InvestmentFundRepository, position_repo: ClientFundPositionRepository, tx_repo: ClientFundTransactionRepository, order_client: OrderClient, redis_client: Optional[aioredis.Redis]) -> None:
        """Initialise with repositories, the order client, and an optional Redis connection."""
        self._fund_repo = fund_repo
        self._position_repo = position_repo
        self._tx_repo = tx_repo
        self._order_client = order_client
        self._redis = redis_client

    async def compute_vrednost_fonda(self, fund: InvestmentFund, bearer_token: str) -> Decimal:
        """Return total fund value = liquid assets + market value of all held securities."""
        try:
            holdings = await self._order_client.get_fund_portfolio(fund.id, bearer_token)
            securities_value = sum(Decimal(str(h.get("currentPrice", 0))) * Decimal(str(h.get("quantity", 0))) for h in holdings)
        except Exception:
            securities_value = Decimal("0")
        return fund.likvidna_sredstva + securities_value

    async def compute_profit(self, fund: InvestmentFund, vrednost_fonda: Decimal) -> Decimal:
        """Return profit = fund value minus total of all completed client inflows."""
        total_invested = await self._tx_repo.sum_inflows_by_fund(fund.id)
        return vrednost_fonda - total_invested

    async def compute_procenat_fonda(self, klijent_id: int, fund_id: int) -> Decimal:
        """Return the client's ownership share of the fund (0 to 1)."""
        total = await self._position_repo.sum_ulozeni_iznos_by_fund(fund_id)
        if total == Decimal("0"):
            return Decimal("0")
        position = await self._position_repo.find_by_klijent_and_fund(klijent_id, fund_id)
        if not position:
            return Decimal("0")
        return position.ukupan_ulozeni_iznos / total

    async def compute_trenutna_vrednost_pozicije(self, klijent_id: int, fund_id: int, bearer_token: str) -> Decimal:
        """Return the current monetary value of the client's position in the fund."""
        fund = await self._fund_repo.find_by_id(fund_id)
        if not fund:
            return Decimal("0")
        vrednost_fonda = await self.get_cached_or_compute_vrednost(fund_id, fund, bearer_token)
        procenat = await self.compute_procenat_fonda(klijent_id, fund_id)
        return procenat * vrednost_fonda

    async def get_cached_or_compute_vrednost(self, fund_id: int, fund: InvestmentFund, bearer_token: str) -> Decimal:
        """Return cached fund value from Redis if available, otherwise compute and cache it."""
        cache_key = f"fund:valuation:{fund_id}"
        if self._redis:
            try:
                cached = await self._redis.get(cache_key)
                if cached:
                    return Decimal(cached.decode())
            except Exception:
                pass
        vrednost = await self.compute_vrednost_fonda(fund, bearer_token)
        if self._redis:
            try:
                ttl = 60
                await self._redis.set(cache_key, str(vrednost), ex=ttl)
            except Exception:
                pass
        return vrednost

    async def invalidate_cache(self, fund_id: int) -> None:
        """Evict the cached valuation for the given fund from Redis."""
        if self._redis:
            try:
                await self._redis.delete(f"fund:valuation:{fund_id}")
            except Exception:
                pass
