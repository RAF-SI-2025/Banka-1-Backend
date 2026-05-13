"""HTTP client for communicating with the employee-service."""

from typing import Any, Dict, List

import httpx

from project.config.settings import Settings


class EmployeeClient:
    """Wraps REST calls to the employee-service for employee validation."""

    def __init__(self, settings: Settings, http_client: httpx.AsyncClient) -> None:
        """Initialise with settings (for base URL) and a shared AsyncClient."""
        self._base_url = settings.employee_service_url
        self._http = http_client

    async def get_employee(self, employee_id: int, bearer_token: str) -> Dict[str, Any]:
        """Fetch employee details by ID, forwarding the caller's JWT."""
        headers = {"Authorization": f"Bearer {bearer_token}"}
        response = await self._http.get(f"{self._base_url}/employees/{employee_id}", headers=headers)
        response.raise_for_status()
        return response.json()

    async def is_supervisor(self, employee_id: int, bearer_token: str) -> bool:
        """Return True if the employee holds the SUPERVISOR or ADMIN role."""
        employee = await self.get_employee(employee_id, bearer_token)
        role = employee.get("role", "")
        return role in ("SUPERVISOR", "ADMIN")

    async def get_all_employees(self, bearer_token: str) -> List[Dict[str, Any]]:
        """Return all employees from the employee-service."""
        headers = {"Authorization": f"Bearer {bearer_token}"}
        response = await self._http.get(f"{self._base_url}/employees", headers=headers)
        response.raise_for_status()
        data = response.json()
        if isinstance(data, list):
            return data
        return data.get("content", data.get("employees", []))
