"""Service for computing derived fund valuation fields with Redis caching."""

import logging
from decimal import Decimal
from typing import Any, Dict, List, Optional, Tuple

import redis.asyncio as aioredis

from project.clients.order_client import OrderClient
from project.models.investment_fund import InvestmentFund
from project.repositories.client_fund_position_repository import ClientFundPositionRepository
from project.repositories.client_fund_transaction_repository import ClientFundTransactionRepository
from project.repositories.investment_fund_repository import InvestmentFundRepository

logger = logging.getLogger(__name__)


class FundValuationService:
    """Computes runtime-only fund metrics; caches vrednost_fonda in Redis."""

    def __init__(self, fund_repo: InvestmentFundRepository, position_repo: ClientFundPositionRepository, tx_repo: ClientFundTransactionRepository, order_client: OrderClient, redis_client: Optional[aioredis.Redis], ttl_seconds: int = 60) -> None:
        """Initialise with repositories, the order client, Redis connection, and cache TTL."""
        self._fund_repo = fund_repo
        self._position_repo = position_repo
        self._tx_repo = tx_repo
        self._order_client = order_client
        self._redis = redis_client
        self._ttl = ttl_seconds

    async def get_fund_holdings(self, fund_id: int, bearer_token: str) -> List[Dict[str, Any]]:
        """Return the raw holdings list from the order-service for the given fund."""
        try:
            return await self._order_client.get_fund_portfolio(fund_id, bearer_token)
        except Exception as exc:
            logger.warning("order-service portfolio fetch failed for fund %s: %s", fund_id, exc)
            return []

    async def compute_vrednost_fonda(self, fund: InvestmentFund, bearer_token: str) -> Decimal:
        """Return total fund value = liquid assets + market value of all held securities."""
        holdings = await self.get_fund_holdings(fund.id, bearer_token)
        securities_value = sum(
            Decimal(str(h.get("currentPrice", 0))) * Decimal(str(h.get("quantity", 0)))
            for h in holdings
        )
        return fund.likvidna_sredstva + securities_value

    async def compute_profit(self, fund: InvestmentFund, vrednost_fonda: Decimal) -> Decimal:
        """Return profit = fund value minus sum of all current net invested amounts from positions."""
        total_invested = await self._position_repo.sum_ulozeni_iznos_by_fund(fund.id)
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
            except Exception as exc:
                logger.warning("Redis get failed for fund %s: %s", fund_id, exc)
        vrednost = await self.compute_vrednost_fonda(fund, bearer_token)
        if self._redis:
            try:
                await self._redis.set(cache_key, str(vrednost), ex=self._ttl)
            except Exception as exc:
                logger.warning("Redis set failed for fund %s: %s", fund_id, exc)
        return vrednost

    async def invalidate_cache(self, fund_id: int) -> None:
        """Evict the cached valuation for the given fund from Redis."""
        if self._redis:
            try:
                await self._redis.delete(f"fund:valuation:{fund_id}")
            except Exception as exc:
                logger.warning("Redis delete failed for fund %s: %s", fund_id, exc)
