"""Shared pytest fixtures for service, middleware, and schema tests."""

from datetime import date, datetime
from decimal import Decimal
from unittest.mock import AsyncMock, MagicMock

import jwt
import pytest

from project.enums.transaction_status import TransactionStatus
from project.models.client_fund_position import ClientFundPosition
from project.models.client_fund_transaction import ClientFundTransaction
from project.models.fund_performance_snapshot import FundPerformanceSnapshot
from project.models.investment_fund import InvestmentFund

TEST_SECRET = "test-secret-key"
TEST_ALGORITHM = "HS256"


def make_token(roles: list[str] = None, user_id: int = 42) -> str:
    """Return a signed JWT with the given roles for test requests."""
    payload = {"id": user_id, "roles": roles or ["CLIENT"], "permissions": [], "sub": "testuser"}
    return jwt.encode(payload, TEST_SECRET, algorithm=TEST_ALGORITHM)


@pytest.fixture()
def fund() -> InvestmentFund:
    """Return a fully populated InvestmentFund instance for use in tests."""
    f = InvestmentFund()
    f.id = 1
    f.naziv = "Alpha Fund"
    f.opis = "Test fund"
    f.minimalni_ulog = Decimal("1000.00")
    f.menadzer_id = 10
    f.likvidna_sredstva = Decimal("50000.00")
    f.account_id = 99
    f.datum_kreiranja = date(2024, 1, 1)
    return f


@pytest.fixture()
def position() -> ClientFundPosition:
    """Return a ClientFundPosition with 5000 RSD invested for client 42 in fund 1."""
    p = ClientFundPosition()
    p.id = 1
    p.klijent_id = 42
    p.fund_id = 1
    p.ukupan_ulozeni_iznos = Decimal("5000.00")
    p.datum_poslednje_promene = datetime(2024, 6, 1, 12, 0)
    return p


@pytest.fixture()
def transaction() -> ClientFundTransaction:
    """Return a COMPLETED inflow ClientFundTransaction for client 42 in fund 1."""
    t = ClientFundTransaction()
    t.id = 1
    t.klijent_id = 42
    t.fund_id = 1
    t.iznos = Decimal("5000.00")
    t.status = TransactionStatus.COMPLETED
    t.is_inflow = True
    t.source_account_id = 7
    t.idempotency_key = "key-abc"
    t.timestamp = datetime(2024, 6, 1, 12, 0)
    return t


@pytest.fixture()
def snapshot() -> FundPerformanceSnapshot:
    """Return a FundPerformanceSnapshot for fund 1 dated 2024-06-01."""
    s = FundPerformanceSnapshot()
    s.id = 1
    s.fund_id = 1
    s.date = date(2024, 6, 1)
    s.vrednost_fonda = Decimal("60000.00")
    s.profit = Decimal("10000.00")
    s.likvidna_sredstva = Decimal("50000.00")
    return s


@pytest.fixture()
def mock_fund_repo(fund) -> MagicMock:
    """Return a mock InvestmentFundRepository pre-configured with common return values."""
    repo = MagicMock()
    repo.find_by_id = AsyncMock(return_value=fund)
    repo.find_by_naziv = AsyncMock(return_value=None)
    repo.find_all = AsyncMock(return_value=[fund])
    repo.save = AsyncMock(side_effect=lambda f: f)
    repo.update = AsyncMock(side_effect=lambda f: f)
    repo.update_likvidna_sredstva = AsyncMock(return_value=None)
    return repo


@pytest.fixture()
def mock_tx_repo(transaction) -> MagicMock:
    """Return a mock ClientFundTransactionRepository pre-configured for happy-path flows."""
    repo = MagicMock()
    repo.find_by_id = AsyncMock(return_value=transaction)
    repo.find_by_idempotency_key = AsyncMock(return_value=None)
    repo.find_by_fund_id = AsyncMock(return_value=[transaction])
    repo.find_pending_outflow_by_fund = AsyncMock(return_value=[])
    repo.save = AsyncMock(side_effect=lambda t: t)
    repo.update_status = AsyncMock(return_value=None)
    repo.sum_inflows_by_fund = AsyncMock(return_value=Decimal("5000.00"))
    return repo


@pytest.fixture()
def mock_position_repo(position) -> MagicMock:
    """Return a mock ClientFundPositionRepository pre-configured for happy-path flows."""
    repo = MagicMock()
    repo.find_by_id = AsyncMock(return_value=position)
    repo.find_by_klijent_and_fund = AsyncMock(return_value=position)
    repo.find_by_fund_id = AsyncMock(return_value=[position])
    repo.find_all = AsyncMock(return_value=[position])
    repo.sum_ulozeni_iznos_by_fund = AsyncMock(return_value=Decimal("5000.00"))
    repo.upsert = AsyncMock(side_effect=lambda ki, fi, d: position)
    repo.save = AsyncMock(side_effect=lambda p: p)
    return repo


@pytest.fixture()
def mock_perf_repo(snapshot) -> MagicMock:
    """Return a mock FundPerformanceRepository pre-configured for happy-path flows."""
    repo = MagicMock()
    repo.find_by_fund_and_period = AsyncMock(return_value=[snapshot])
    repo.save = AsyncMock(side_effect=lambda s: s)
    repo.upsert_snapshot = AsyncMock(return_value=None)
    return repo


@pytest.fixture()
def mock_banking_client() -> MagicMock:
    """Return a mock BankingClient that succeeds on all calls."""
    client = MagicMock()
    client.create_fund_account = AsyncMock(return_value={"id": 99, "accountNumber": "1234567890123456789"})
    client.get_account_details = AsyncMock(return_value={"id": 99, "accountNumber": "1234567890123456789", "clientId": 42})
    client.transfer = AsyncMock(return_value={"senderBalance": "45000.00", "receiverBalance": "5000.00"})
    client.credit_account = AsyncMock(return_value=None)
    client.debit_account = AsyncMock(return_value=None)
    return client


@pytest.fixture()
def mock_employee_client() -> MagicMock:
    """Return a mock EmployeeClient that confirms the employee is a supervisor."""
    client = MagicMock()
    client.get_employee = AsyncMock(return_value={"id": 10, "role": "SUPERVISOR"})
    client.is_supervisor = AsyncMock(return_value=True)
    client.get_all_employees = AsyncMock(return_value=[{"id": 10, "role": "SUPERVISOR", "firstName": "Test", "lastName": "Manager"}])
    return client


@pytest.fixture()
def mock_order_client() -> MagicMock:
    """Return a mock OrderClient returning a single holding worth 10000 RSD."""
    client = MagicMock()
    client.get_fund_portfolio = AsyncMock(return_value=[{"listingId": 5, "currentPrice": "100.00", "quantity": 100, "acquisitionDate": "2024-01-01"}])
    client.create_sell_order = AsyncMock(return_value={"id": 55, "status": "PENDING"})
    return client


@pytest.fixture()
def mock_valuation_service(position) -> MagicMock:
    """Return a mock FundValuationService where current position value equals ukupan_ulozeni_iznos."""
    svc = MagicMock()
    svc.get_cached_or_compute_vrednost = AsyncMock(return_value=Decimal("50000.00"))
    svc.compute_procenat_fonda = AsyncMock(return_value=Decimal("0.1"))
    svc.compute_profit = AsyncMock(return_value=Decimal("5000.00"))
    svc.get_fund_holdings = AsyncMock(return_value=[])
    return svc
