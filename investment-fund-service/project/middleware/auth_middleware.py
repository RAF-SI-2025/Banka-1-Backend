"""JWT authentication middleware that validates Bearer tokens on every request."""

from typing import Callable

import jwt
from fastapi import Request, Response
from fastapi.responses import JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.types import ASGIApp

from project.config.settings import Settings

_SKIP_PATHS = {"/health", "/docs", "/openapi.json", "/redoc"}


class AuthMiddleware(BaseHTTPMiddleware):
    """Validates the Authorization: Bearer <token> header on all non-public paths."""

    def __init__(self, app: ASGIApp, settings: Settings) -> None:
        """Initialise middleware with JWT secret and algorithm from settings."""
        super().__init__(app)
        self._secret = settings.jwt_secret
        self._algorithm = settings.jwt_algorithm

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        """Decode and attach the JWT payload; return 401/403 on invalid or missing token."""
        if request.url.path in _SKIP_PATHS or request.url.path.startswith("/redoc") or request.url.path.startswith("/docs"):
            return await call_next(request)
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            return JSONResponse(status_code=401, content={"error": "Unauthorized", "message": "Missing or invalid Authorization header", "status": 401})
        token = auth_header.removeprefix("Bearer ").strip()
        try:
            payload = self._decode_token(token)
            request.state.token_payload = payload
            request.state.raw_token = token
        except jwt.ExpiredSignatureError:
            return JSONResponse(status_code=401, content={"error": "Unauthorized", "message": "Token has expired", "status": 401})
        except jwt.InvalidTokenError as exc:
            return JSONResponse(status_code=401, content={"error": "Unauthorized", "message": str(exc), "status": 401})
        return await call_next(request)

    def _decode_token(self, token: str) -> dict:
        """Decode a JWT and return its payload dict."""
        return jwt.decode(token, self._secret.encode("utf-8"), algorithms=[self._algorithm])
