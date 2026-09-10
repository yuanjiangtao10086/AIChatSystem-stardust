import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any

import uvicorn
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from starlette.responses import JSONResponse

from app.api.routes import router
from app.core.errors import AiServiceError
from app.core.logging import configure_logging
from app.core.request_context import RequestContextMiddleware, current_request_id
from app.core.settings import Settings
from app.providers.openai_compatible.provider import OpenAICompatibleProvider
from app.providers.registry import ProviderRegistry
from app.schemas.common import ErrorResponse
from app.services.chat import ChatService
from app.services.rag import RagService
from app.vectorstores.base import VectorStore
from app.vectorstores.sqlite import SQLiteVectorStore

logger = logging.getLogger(__name__)


def build_provider_registry(settings: Settings) -> ProviderRegistry:
    key = settings.openai_compatible_api_key
    provider = OpenAICompatibleProvider(
        base_url=str(settings.openai_compatible_base_url).rstrip("/"),
        api_key=None if key is None else key.get_secret_value(),
        timeout_seconds=settings.provider_timeout_seconds,
        stream_read_timeout_seconds=settings.provider_stream_read_timeout_seconds,
    )
    return ProviderRegistry({"openai-compatible": provider})


def create_app(
    settings: Settings | None = None,
    provider_registry: ProviderRegistry | None = None,
    vector_store: VectorStore | None = None,
) -> FastAPI:
    resolved_settings = settings or Settings()
    resolved_registry = provider_registry or build_provider_registry(resolved_settings)
    configure_logging(resolved_settings.log_level)

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        yield
        await resolved_registry.aclose()
        close = getattr(application.state.vector_store, "aclose", None)
        if close is not None:
            await close()

    application = FastAPI(
        title="Stardust AI Internal Service",
        version="0.1.0",
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
        lifespan=lifespan,
    )
    application.state.settings = resolved_settings
    application.state.provider_registry = resolved_registry
    application.state.chat_service = ChatService(resolved_registry, resolved_settings)
    application.state.vector_store = vector_store or SQLiteVectorStore(
        resolved_settings.rag_vector_store_path
    )
    application.state.rag_service = RagService(
        resolved_registry, application.state.vector_store, resolved_settings
    )
    application.add_middleware(RequestContextMiddleware)
    application.include_router(router)

    @application.exception_handler(AiServiceError)
    async def handle_service_error(_: Request, error: AiServiceError) -> JSONResponse:
        logger.warning("request rejected code=%s retryable=%s", error.code, error.retryable)
        return error_response(error.status_code, error.code, error.message, error.retryable)

    @application.exception_handler(RequestValidationError)
    async def handle_validation_error(_: Request, error: RequestValidationError) -> JSONResponse:
        details = [
            {
                "field": ".".join(str(part) for part in item["loc"] if part != "body"),
                "reason": item["type"],
            }
            for item in error.errors()
        ]
        logger.warning("schema validation failed field_count=%d", len(details))
        return error_response(
            422,
            "SCHEMA_VALIDATION_ERROR",
            "request schema validation failed",
            False,
            details,
        )

    @application.exception_handler(Exception)
    async def handle_unexpected_error(_: Request, error: Exception) -> JSONResponse:
        logger.exception("unhandled internal service error type=%s", type(error).__name__)
        return error_response(500, "INTERNAL_ERROR", "internal service error", False)

    return application


def error_response(
    status_code: int,
    code: str,
    message: str,
    retryable: bool,
    details: list[dict[str, Any]] | None = None,
) -> JSONResponse:
    payload = ErrorResponse(
        code=code,
        message=message,
        request_id=current_request_id(),
        retryable=retryable,
        details=details,
    )
    return JSONResponse(status_code=status_code, content=payload.model_dump(by_alias=True))


_app: FastAPI | None = None


def __getattr__(name: str) -> Any:
    """Build the ASGI application on first access.

    Keeping this lazy means importing ``app.main`` (tests, tooling) no longer
    opens sqlite and writes the vector-store file as an import side effect.
    """
    global _app
    if name == "app":
        if _app is None:
            _app = create_app()
        return _app
    raise AttributeError(f"module {__name__!r} has no attribute {name!r}")


def run() -> None:
    uvicorn.run("app.main:app", host="127.0.0.1", port=8000, reload=False)
