"""Centralised FastAPI exception handler registration class."""

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse


class HttpExceptionHandler:
    """Registers uniform JSON error responses for HTTP and validation exceptions."""

    def __init__(self, app: FastAPI) -> None:
        """Initialise with the FastAPI application instance."""
        self._app = app

    def register(self) -> None:
        """Attach all exception handlers to the FastAPI app."""
        self._app.add_exception_handler(HTTPException, self.handle_http_exception)
        self._app.add_exception_handler(RequestValidationError, self.handle_validation_error)
        self._app.add_exception_handler(Exception, self.handle_generic_exception)

    async def handle_http_exception(self, request: Request, exc: HTTPException) -> JSONResponse:
        """Return a structured JSON response for FastAPI HTTP exceptions."""
        return JSONResponse(status_code=exc.status_code, content={"error": exc.detail, "message": exc.detail, "status": exc.status_code})

    async def handle_validation_error(self, request: Request, exc: RequestValidationError) -> JSONResponse:
        """Return a 422 JSON response listing all validation failures."""
        errors = [{"field": ".".join(str(l) for l in e["loc"]), "message": e["msg"]} for e in exc.errors()]
        return JSONResponse(status_code=422, content={"error": "Validation Error", "message": "Request validation failed", "details": errors, "status": 422})

    async def handle_generic_exception(self, request: Request, exc: Exception) -> JSONResponse:
        """Return a 500 JSON response for unhandled server errors."""
        return JSONResponse(status_code=500, content={"error": "Internal Server Error", "message": "An unexpected error occurred", "status": 500})
