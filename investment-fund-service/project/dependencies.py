"""FastAPI dependency provider functions for DI wiring across the application."""

from typing import AsyncGenerator, Optional

import httpx
import redis.asyncio as aioredis
from fastapi import Depends, HTTPException, Request
from sqlalchemy.ext.asyncio import AsyncSession

from project.clients.banking_client import BankingClient
from project.clients.jwt_token_generator import JwtTokenGenerator
from project.clients.employee_client import EmployeeClient
from project.clients.order_client import OrderClient
from project.config.settings import Settings, get_settings
from project.middleware.token_data import TokenData
from project.repositories.client_fund_position_repository import ClientFundPositionRepository
from project.repositories.client_fund_transaction_repository import ClientFundTransactionRepository
from project.repositories.fund_performance_repository import FundPerformanceRepository
from project.repositories.investment_fund_repository import InvestmentFundRepository
from project.services.fund_investment_service import FundInvestmentService
from project.services.fund_liquidation_service import FundLiquidationService
from project.services.fund_performance_service import FundPerformanceService
from project.services.fund_redemption_service import FundRedemptionService
from project.services.fund_valuation_service import FundValuationService
from project.services.investment_fund_service import InvestmentFundService

_http_client: Optional[httpx.AsyncClient] = None
_redis_client: Optional[aioredis.Redis] = None


def set_http_client(client: httpx.AsyncClient) -> None:
    """Store the shared httpx client for use by all dependency providers."""
    global _http_client
    _http_client = client


def set_redis_client(client: Optional[aioredis.Redis]) -> None:
    """Store the shared Redis client for use by valuation and caching providers."""
    global _redis_client
    _redis_client = client


def get_http_client() -> httpx.AsyncClient:
    """Return the shared httpx.AsyncClient instance."""
    if _http_client is None:
        raise RuntimeError("HTTP client not initialised")
    return _http_client


def get_redis() -> Optional[aioredis.Redis]:
    """Return the shared Redis client, or None if Redis is unavailable."""
    return _redis_client


async def get_db_session(request: Request) -> AsyncGenerator[AsyncSession, None]:
    """Yield a per-request database session from the application-level Database instance."""
    database = request.app.state.database
    async with database.get_session() as session:
        yield session


def get_settings_dep() -> Settings:
    """Return the singleton Settings instance."""
    return get_settings()


def get_banking_client(settings: Settings = Depends(get_settings_dep), http: httpx.AsyncClient = Depends(get_http_client)) -> BankingClient:
    """Construct a BankingClient for the current request."""
    return BankingClient(settings, http, JwtTokenGenerator(settings))


def get_employee_client(settings: Settings = Depends(get_settings_dep), http: httpx.AsyncClient = Depends(get_http_client)) -> EmployeeClient:
    """Construct an EmployeeClient for the current request."""
    return EmployeeClient(settings, http)


def get_order_client(settings: Settings = Depends(get_settings_dep), http: httpx.AsyncClient = Depends(get_http_client)) -> OrderClient:
    """Construct an OrderClient for the current request."""
    return OrderClient(settings, http)


def get_investment_fund_repository(session: AsyncSession = Depends(get_db_session)) -> InvestmentFundRepository:
    """Construct an InvestmentFundRepository for the current request session."""
    return InvestmentFundRepository(session)


def get_client_fund_transaction_repository(session: AsyncSession = Depends(get_db_session)) -> ClientFundTransactionRepository:
    """Construct a ClientFundTransactionRepository for the current request session."""
    return ClientFundTransactionRepository(session)


def get_client_fund_position_repository(session: AsyncSession = Depends(get_db_session)) -> ClientFundPositionRepository:
    """Construct a ClientFundPositionRepository for the current request session."""
    return ClientFundPositionRepository(session)


def get_fund_performance_repository(session: AsyncSession = Depends(get_db_session)) -> FundPerformanceRepository:
    """Construct a FundPerformanceRepository for the current request session."""
    return FundPerformanceRepository(session)


def get_investment_fund_service(fund_repo: InvestmentFundRepository = Depends(get_investment_fund_repository), banking: BankingClient = Depends(get_banking_client), employee: EmployeeClient = Depends(get_employee_client)) -> InvestmentFundService:
    """Construct the InvestmentFundService with its dependencies."""
    return InvestmentFundService(fund_repo, banking, employee)


def get_fund_valuation_service(fund_repo: InvestmentFundRepository = Depends(get_investment_fund_repository), position_repo: ClientFundPositionRepository = Depends(get_client_fund_position_repository), tx_repo: ClientFundTransactionRepository = Depends(get_client_fund_transaction_repository), order: OrderClient = Depends(get_order_client)) -> FundValuationService:
    """Construct the FundValuationService with its dependencies."""
    return FundValuationService(fund_repo, position_repo, tx_repo, order, get_redis())


def get_fund_liquidation_service(fund_repo: InvestmentFundRepository = Depends(get_investment_fund_repository), tx_repo: ClientFundTransactionRepository = Depends(get_client_fund_transaction_repository), position_repo: ClientFundPositionRepository = Depends(get_client_fund_position_repository), banking: BankingClient = Depends(get_banking_client), order: OrderClient = Depends(get_order_client)) -> FundLiquidationService:
    """Construct the FundLiquidationService with its dependencies."""
    return FundLiquidationService(fund_repo, tx_repo, position_repo, banking, order)


def get_fund_investment_service(fund_repo: InvestmentFundRepository = Depends(get_investment_fund_repository), tx_repo: ClientFundTransactionRepository = Depends(get_client_fund_transaction_repository), position_repo: ClientFundPositionRepository = Depends(get_client_fund_position_repository), banking: BankingClient = Depends(get_banking_client)) -> FundInvestmentService:
    """Construct the FundInvestmentService with its dependencies."""
    return FundInvestmentService(fund_repo, tx_repo, position_repo, banking)


def get_fund_redemption_service(fund_repo: InvestmentFundRepository = Depends(get_investment_fund_repository), tx_repo: ClientFundTransactionRepository = Depends(get_client_fund_transaction_repository), position_repo: ClientFundPositionRepository = Depends(get_client_fund_position_repository), banking: BankingClient = Depends(get_banking_client), liquidation: FundLiquidationService = Depends(get_fund_liquidation_service)) -> FundRedemptionService:
    """Construct the FundRedemptionService with its dependencies."""
    return FundRedemptionService(fund_repo, tx_repo, position_repo, banking, liquidation)


def get_fund_performance_service(fund_repo: InvestmentFundRepository = Depends(get_investment_fund_repository), perf_repo: FundPerformanceRepository = Depends(get_fund_performance_repository), valuation: FundValuationService = Depends(get_fund_valuation_service)) -> FundPerformanceService:
    """Construct the FundPerformanceService with its dependencies."""
    return FundPerformanceService(fund_repo, perf_repo, valuation)


def get_token_data(request: Request) -> TokenData:
    """Extract and return the decoded token payload stored by AuthMiddleware."""
    payload = getattr(request.state, "token_payload", None)
    if payload is None:
        raise HTTPException(status_code=401, detail="Not authenticated")
    return TokenData(id=payload.get("id", 0), roles=payload.get("roles", []), permissions=payload.get("permissions", []), sub=payload.get("sub", ""))


def require_authenticated(token: TokenData = Depends(get_token_data)) -> TokenData:
    """Dependency that requires a valid authenticated token (any role)."""
    return token


def require_supervisor(token: TokenData = Depends(get_token_data)) -> TokenData:
    """Dependency that requires the caller to hold the SUPERVISOR or ADMIN role."""
    if "SUPERVISOR" not in token.roles and "ADMIN" not in token.roles:
        raise HTTPException(status_code=403, detail="Supervisor or Admin role required")
    return token


def require_client_or_supervisor(token: TokenData = Depends(get_token_data)) -> TokenData:
    """Dependency that allows CLIENT, SUPERVISOR, and ADMIN but blocks AGENT and unauthenticated."""
    if not any(r in token.roles for r in ("CLIENT", "SUPERVISOR", "ADMIN")):
        raise HTTPException(status_code=403, detail="Client or Supervisor role required")
    return token
