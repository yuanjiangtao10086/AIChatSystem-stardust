import logging
from typing import Annotated

from fastapi import APIRouter, Body, Depends, Header
from starlette.responses import StreamingResponse

from app.api.dependencies import (
    chat_service_from_request,
    provider_registry_from_request,
    rag_service_from_request,
    settings_from_request,
)
from app.api.security import require_internal_service
from app.core.request_context import current_request_id
from app.core.settings import Settings
from app.providers.registry import ProviderRegistry
from app.schemas.chat import ChatRequest, ChatResponse
from app.schemas.health import HealthResponse
from app.schemas.rag import (
    DeleteDocumentRequest,
    ProcessDocumentResponse,
    RetrieveRequest,
    RetrieveResponse,
)
from app.services.chat import ChatService
from app.services.rag import DocumentIdentity, RagService

logger = logging.getLogger(__name__)
router = APIRouter()


@router.get("/health", response_model=HealthResponse)
async def health(
    settings: Annotated[Settings, Depends(settings_from_request)],
    registry: Annotated[ProviderRegistry, Depends(provider_registry_from_request)],
) -> HealthResponse:
    return HealthResponse(
        service=settings.service_name,
        environment=settings.environment,
        ready=settings.internal_api_configured and settings.provider_configured,
        internal_api_configured=settings.internal_api_configured,
        provider_configured=settings.provider_configured,
        providers=list(registry.keys),
    )


@router.post(
    "/internal/chat",
    response_model=ChatResponse,
    dependencies=[Depends(require_internal_service)],
)
async def chat(
    request: ChatRequest,
    service: Annotated[ChatService, Depends(chat_service_from_request)],
) -> ChatResponse:
    logger.info(
        "chat request started ai_request_id=%s provider=%s message_count=%d",
        request.ai_request_id,
        request.provider_key,
        len(request.messages),
    )
    result = await service.chat(request)
    logger.info("chat request completed ai_request_id=%s", request.ai_request_id)
    return result


@router.post(
    "/internal/chat/stream",
    dependencies=[Depends(require_internal_service)],
)
async def stream_chat(
    request: ChatRequest,
    service: Annotated[ChatService, Depends(chat_service_from_request)],
) -> StreamingResponse:
    logger.info(
        "stream request accepted ai_request_id=%s provider=%s request_id=%s",
        request.ai_request_id,
        request.provider_key,
        current_request_id(),
    )
    return StreamingResponse(
        service.stream(request, current_request_id()),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.post(
    "/internal/rag/documents/process",
    response_model=ProcessDocumentResponse,
    dependencies=[Depends(require_internal_service)],
)
async def process_document(
    content: Annotated[bytes, Body(media_type="application/octet-stream")],
    service: Annotated[RagService, Depends(rag_service_from_request)],
    user_id: Annotated[str, Header(alias="X-User-Id")],
    knowledge_base_id: Annotated[str, Header(alias="X-Knowledge-Base-Id")],
    document_id: Annotated[str, Header(alias="X-Document-Id")],
    filename: Annotated[str, Header(alias="X-Document-Name")],
    mime_type: Annotated[str, Header(alias="X-Document-Mime")],
    provider_key: Annotated[str, Header(alias="X-Provider-Key")] = "openai-compatible",
    embedding_model: Annotated[str | None, Header(alias="X-Embedding-Model")] = None,
) -> ProcessDocumentResponse:
    from app.core.errors import AiServiceError, ServiceNotConfiguredError

    if len(content) > service.settings.rag_max_document_bytes:
        raise AiServiceError("DOCUMENT_TOO_LARGE", "document exceeds processing limit", 413)
    model = embedding_model or service.settings.openai_compatible_embedding_model
    if not model:
        raise ServiceNotConfiguredError("embedding model")
    identity = DocumentIdentity(
        user_id, knowledge_base_id, document_id, filename, mime_type, provider_key, model
    )
    return await service.process(identity, content)


@router.post(
    "/internal/rag/retrieve",
    response_model=RetrieveResponse,
    dependencies=[Depends(require_internal_service)],
)
async def retrieve(
    payload: RetrieveRequest,
    service: Annotated[RagService, Depends(rag_service_from_request)],
) -> RetrieveResponse:
    return await service.retrieve(payload)


@router.delete(
    "/internal/rag/documents/{document_id}",
    status_code=204,
    dependencies=[Depends(require_internal_service)],
)
async def delete_document(
    document_id: str,
    payload: DeleteDocumentRequest,
    service: Annotated[RagService, Depends(rag_service_from_request)],
) -> None:
    await service.vector_store.delete_document(
        payload.user_id, payload.knowledge_base_id, document_id
    )
