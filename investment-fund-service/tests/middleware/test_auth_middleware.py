"""Unit tests for AuthMiddleware JWT validation logic."""

import jwt
import pytest
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.testclient import TestClient

from project.config.settings import Settings
from project.middleware.auth_middleware import AuthMiddleware

SECRET = "test-secret"
ALGORITHM = "HS256"


def _make_settings(secret: str = SECRET) -> Settings:
    """Return a minimal Settings instance with the given JWT secret."""
    return Settings(jwt_secret=secret, jwt_algorithm=ALGORITHM, db_host="localhost", db_name="test", db_user="test", db_password="test")


def _make_app(secret: str = SECRET) -> TestClient:
    """Return a TestClient wrapping a minimal FastAPI app with AuthMiddleware applied."""
    app = FastAPI()
    app.add_middleware(AuthMiddleware, settings=_make_settings(secret))

    @app.get("/health")
    async def health():
        """Public health endpoint that bypasses auth."""
        return {"status": "ok"}

    @app.get("/protected")
    async def protected(request: Request):
        """Protected endpoint that returns the decoded token payload."""
        return request.state.token_payload

    return TestClient(app, raise_server_exceptions=False)


def _make_token(roles: list[str] = None, secret: str = SECRET, expired: bool = False) -> str:
    """Produce a signed JWT for use in test requests."""
    import time
    payload = {"id": 1, "roles": roles or ["CLIENT"], "permissions": [], "sub": "user"}
    if expired:
        payload["exp"] = int(time.time()) - 3600
    return jwt.encode(payload, secret, algorithm=ALGORITHM)


class TestAuthMiddlewareSkipPaths:
    """Tests for paths that bypass JWT validation."""

    def test_health_endpoint_returns_200_without_token(self):
        """Requests to /health succeed without an Authorization header."""
        client = _make_app()
        response = client.get("/health")
        assert response.status_code == 200

    def test_docs_endpoint_skipped(self):
        """Requests to /docs succeed without an Authorization header."""
        client = _make_app()
        response = client.get("/docs")
        assert response.status_code in (200, 404)

    def test_openapi_json_skipped(self):
        """Requests to /openapi.json succeed without an Authorization header."""
        client = _make_app()
        response = client.get("/openapi.json")
        assert response.status_code == 200


class TestAuthMiddlewareMissingToken:
    """Tests for requests with missing or malformed Authorization headers."""

    def test_missing_header_returns_401(self):
        """A request with no Authorization header receives a 401 response."""
        client = _make_app()
        response = client.get("/protected")
        assert response.status_code == 401

    def test_wrong_scheme_returns_401(self):
        """A request using Basic auth instead of Bearer receives a 401 response."""
        client = _make_app()
        response = client.get("/protected", headers={"Authorization": "Basic dXNlcjpwYXNz"})
        assert response.status_code == 401

    def test_401_response_has_error_field(self):
        """The 401 body contains an 'error' field."""
        client = _make_app()
        response = client.get("/protected")
        assert "error" in response.json()


class TestAuthMiddlewareInvalidToken:
    """Tests for requests with invalid or expired tokens."""

    def test_invalid_token_returns_401(self):
        """A request with a malformed token string receives a 401 response."""
        client = _make_app()
        response = client.get("/protected", headers={"Authorization": "Bearer not.a.token"})
        assert response.status_code == 401

    def test_wrong_secret_returns_401(self):
        """A token signed with a different secret is rejected with 401."""
        token = _make_token(secret="wrong-secret")
        client = _make_app()
        response = client.get("/protected", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 401

    def test_expired_token_returns_401(self):
        """An expired token is rejected with 401."""
        token = _make_token(expired=True)
        client = _make_app()
        response = client.get("/protected", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 401


class TestAuthMiddlewareValidToken:
    """Tests for requests with valid tokens."""

    def test_valid_token_allows_request(self):
        """A valid Bearer token allows the request to pass through."""
        token = _make_token(roles=["SUPERVISOR"])
        client = _make_app()
        response = client.get("/protected", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 200

    def test_valid_token_attaches_payload(self):
        """The decoded payload is attached to request.state.token_payload."""
        token = _make_token(roles=["CLIENT"])
        client = _make_app()
        response = client.get("/protected", headers={"Authorization": f"Bearer {token}"})
        body = response.json()
        assert body["id"] == 1
        assert "CLIENT" in body["roles"]


class TestDecodeToken:
    """Direct unit tests for AuthMiddleware._decode_token."""

    def test_decode_returns_payload_dict(self):
        """_decode_token returns the correct claims from a valid JWT."""
        token = jwt.encode({"id": 7, "roles": ["ADMIN"]}, SECRET, algorithm=ALGORITHM)
        settings = _make_settings()
        app = FastAPI()
        middleware = AuthMiddleware.__new__(AuthMiddleware)
        middleware._secret = SECRET
        middleware._algorithm = ALGORITHM
        result = middleware._decode_token(token)
        assert result["id"] == 7
        assert result["roles"] == ["ADMIN"]

    def test_decode_raises_on_invalid_token(self):
        """_decode_token raises jwt.InvalidTokenError for a garbage token string."""
        middleware = AuthMiddleware.__new__(AuthMiddleware)
        middleware._secret = SECRET
        middleware._algorithm = ALGORITHM
        with pytest.raises(jwt.InvalidTokenError):
            middleware._decode_token("garbage")
