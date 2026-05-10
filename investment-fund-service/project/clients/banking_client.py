"""HTTP client for communicating with the banking-service."""

from decimal import Decimal
from typing import Any, Dict

import httpx

from project.clients.jwt_token_generator import JwtTokenGenerator
from project.config.settings import Settings


class BankingClient:
    """Wraps all REST calls to the banking-service, generating a fresh service JWT per request."""

    def __init__(self, settings: Settings, http_client: httpx.AsyncClient, token_generator: JwtTokenGenerator) -> None:
        """Initialise with settings, a shared AsyncClient, and a service JWT generator."""
        self._base_url = settings.banking_service_url
        self._http = http_client
        self._token_generator = token_generator

    def _headers(self) -> Dict[str, str]:
        """Return auth headers with a freshly generated service JWT."""
        return {"Authorization": f"Bearer {self._token_generator.generate()}", "Content-Type": "application/json"}

    async def create_fund_account(self, fund_name: str) -> Dict[str, Any]:
        """Create a dedicated RSD liquidity account for a fund; returns account details dict."""
        response = await self._http.post(f"{self._base_url}/internal/accounts/fund-account", json={"fundName": fund_name}, headers=self._headers())
        response.raise_for_status()
        return response.json()

    async def get_account_details(self, account_id: int) -> Dict[str, Any]:
        """Return account details (including accountNumber) for the given account ID."""
        response = await self._http.get(f"{self._base_url}/internal/accounts/id/{account_id}/details", headers=self._headers())
        response.raise_for_status()
        return response.json()

    async def transfer(self, from_account_number: str, to_account_number: str, amount: Decimal, commission: Decimal, client_id: int) -> Dict[str, Any]:
        """Execute a transfer between two accounts; returns updated balance response."""
        payload = {"fromAccountNumber": from_account_number, "toAccountNumber": to_account_number, "fromAmount": str(amount), "toAmount": str(amount), "commission": str(commission), "clientId": client_id}
        response = await self._http.post(f"{self._base_url}/internal/accounts/transaction", json=payload, headers=self._headers())
        response.raise_for_status()
        return response.json()

    async def credit_account(self, account_number: str, amount: Decimal, client_id: int) -> None:
        """Credit the given account with the specified amount."""
        payload = {"accountNumber": account_number, "amount": str(amount), "clientId": client_id}
        response = await self._http.post(f"{self._base_url}/internal/accounts/credit", json=payload, headers=self._headers())
        response.raise_for_status()

    async def debit_account(self, account_number: str, amount: Decimal, client_id: int) -> None:
        """Debit the given account by the specified amount."""
        payload = {"accountNumber": account_number, "amount": str(amount), "clientId": client_id}
        response = await self._http.post(f"{self._base_url}/internal/accounts/debit", json=payload, headers=self._headers())
        response.raise_for_status()
