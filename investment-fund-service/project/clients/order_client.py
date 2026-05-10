"""HTTP client for communicating with the order-service."""

from typing import Any, Dict, List

import httpx

from project.config.settings import Settings


class OrderClient:
    """Wraps REST calls to the order-service for portfolio queries and sell orders."""

    def __init__(self, settings: Settings, http_client: httpx.AsyncClient) -> None:
        """Initialise with settings (for base URL) and a shared AsyncClient."""
        self._base_url = settings.order_service_url
        self._http = http_client

    async def get_fund_portfolio(self, fund_id: int, bearer_token: str) -> List[Dict[str, Any]]:
        """Return the list of holdings for a fund from the order-service portfolio endpoint."""
        headers = {"Authorization": f"Bearer {bearer_token}"}
        response = await self._http.get(f"{self._base_url}/portfolio/fund/{fund_id}", headers=headers)
        response.raise_for_status()
        data = response.json()
        return data if isinstance(data, list) else data.get("holdings", [])

    async def create_sell_order(self, listing_id: int, quantity: int, account_id: int, bearer_token: str) -> Dict[str, Any]:
        """Place a MARKET SELL order for a given listing and quantity on behalf of the fund."""
        headers = {"Authorization": f"Bearer {bearer_token}", "Content-Type": "application/json"}
        payload = {"listingId": listing_id, "quantity": quantity, "accountId": account_id}
        response = await self._http.post(f"{self._base_url}/orders/sell", json=payload, headers=headers)
        response.raise_for_status()
        return response.json()
