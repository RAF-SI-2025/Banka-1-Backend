"""Pydantic model representing the decoded JWT token payload."""

from typing import List

from pydantic import BaseModel


class TokenData(BaseModel):
    """Decoded claims extracted from a valid JWT token."""

    id: int
    roles: List[str] = []
    permissions: List[str] = []
    sub: str = ""
