"""Generates short-lived service-to-service JWT tokens signed with the shared secret."""

import time

import jwt

from project.config.settings import Settings


class JwtTokenGenerator:
    """Creates signed JWTs with role SERVICE for internal inter-service calls."""

    def __init__(self, settings: Settings) -> None:
        """Initialise with the application settings to read jwt_secret and algorithm."""
        self._secret = settings.jwt_secret
        self._algorithm = settings.jwt_algorithm

    def generate(self) -> str:
        """Return a freshly signed JWT valid for one hour with role SERVICE."""
        payload = {
            "sub": "investment-fund-service",
            "iss": "banka1",
            "id": 0,
            "roles": ["SERVICE"],
            "permissions": [],
            "exp": int(time.time()) + 3600,
        }
        return jwt.encode(payload, self._secret, algorithm=self._algorithm)
