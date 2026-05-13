"""Service for investment fund CRUD operations."""

from datetime import date
from typing import List

from fastapi import HTTPException

from project.clients.banking_client import BankingClient
from project.clients.employee_client import EmployeeClient
from project.models.investment_fund import InvestmentFund
from project.repositories.investment_fund_repository import InvestmentFundRepository
from project.schemas.create_fund_request import CreateFundRequest
from project.schemas.update_fund_request import UpdateFundRequest


class InvestmentFundService:
    """Handles creation, retrieval, and modification of investment funds."""

    def __init__(self, fund_repo: InvestmentFundRepository, banking_client: BankingClient, employee_client: EmployeeClient) -> None:
        """Initialise with repository and downstream service clients."""
        self._fund_repo = fund_repo
        self._banking = banking_client
        self._employee = employee_client

    async def create_fund(self, request: CreateFundRequest, bearer_token: str) -> InvestmentFund:
        """Validate inputs, create a banking account, and persist a new InvestmentFund."""
        existing = await self._fund_repo.find_by_naziv(request.naziv)
        if existing:
            raise HTTPException(status_code=409, detail=f"Fund with name '{request.naziv}' already exists")
        is_supervisor = await self._employee.is_supervisor(request.menadzer_id, bearer_token)
        if not is_supervisor:
            raise HTTPException(status_code=400, detail=f"Employee {request.menadzer_id} is not a supervisor")
        account_data = await self._banking.create_fund_account(request.naziv)
        account_id = account_data.get("id") or account_data.get("accountId")
        if not account_id:
            raise HTTPException(status_code=502, detail="Banking service did not return an account ID")
        fund = InvestmentFund(naziv=request.naziv, opis=request.opis, minimalni_ulog=request.minimalni_ulog, menadzer_id=request.menadzer_id, account_id=int(account_id), datum_kreiranja=date.today(), likvidna_sredstva=0)
        return await self._fund_repo.save(fund)

    async def list_funds(self) -> List[InvestmentFund]:
        """Return all investment funds."""
        return await self._fund_repo.find_all()

    async def get_fund(self, fund_id: int) -> InvestmentFund:
        """Return a single fund by ID, raising 404 if not found."""
        fund = await self._fund_repo.find_by_id(fund_id)
        if not fund:
            raise HTTPException(status_code=404, detail=f"Fund {fund_id} not found")
        return fund

    async def update_fund(self, fund_id: int, request: UpdateFundRequest, bearer_token: str) -> InvestmentFund:
        """Apply partial updates to a fund; validates new manager role if provided."""
        fund = await self.get_fund(fund_id)
        if request.menadzer_id is not None:
            is_supervisor = await self._employee.is_supervisor(request.menadzer_id, bearer_token)
            if not is_supervisor:
                raise HTTPException(status_code=400, detail=f"Employee {request.menadzer_id} is not a supervisor")
            fund.menadzer_id = request.menadzer_id
        if request.opis is not None:
            fund.opis = request.opis
        if request.minimalni_ulog is not None:
            fund.minimalni_ulog = request.minimalni_ulog
        return await self._fund_repo.update(fund)
