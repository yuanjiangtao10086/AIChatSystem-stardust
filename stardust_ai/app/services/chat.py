import asyncio
import json
import logging
from collections.abc import AsyncIterator
from datetime import UTC, datetime

from app.core.errors import AiServiceError, ServiceNotConfiguredError
from app.core.request_context import current_request_id
from app.core.settings import Settings
from app.providers.registry import ProviderRegistry
from app.providers.types import ChatRequest as ProviderChatRequest
from app.providers.types import ProviderMessage, ProviderMessageRole, TokenUsage
from app.schemas.chat import (
    ChatMessage,
    ChatRequest,
    ChatResponse,
    ChatRole,
    StreamEvent,
)
from app.schemas.chat import TokenUsage as TokenUsageSchema

logger = logging.getLogger(__name__)


class ChatService:
    def __init__(self, registry: ProviderRegistry, settings: Settings) -> None:
        self._registry = registry
        self._settings = settings

    async def chat(self, request: ChatRequest) -> ChatResponse:
        provider = self._registry.get(request.provider_key)
        provider_request = self._to_provider_request(request)
        result = await provider.chat(provider_request)
        return ChatResponse(
            ai_request_id=request.ai_request_id,
            request_id=current_request_id(),
            provider_key=request.provider_key,
            model=result.model,
            message=ChatMessage(role=ChatRole.ASSISTANT, content=result.content),
            finish_reason=result.finish_reason,
            usage=self._usage_schema(result.usage),
        )

    async def stream(
        self,
        request: ChatRequest,
        request_id: str | None = None,
    ) -> AsyncIterator[str]:
        seq = 0
        resolved_request_id = request_id or current_request_id()
        finish_reason: str | None = None
        try:
            model = self._resolve_model(request)
            provider = self._registry.get(request.provider_key)
            yield self._sse(
                "start",
                StreamEvent(
                    type="start",
                    ai_request_id=request.ai_request_id,
                    request_id=resolved_request_id,
                    seq=seq,
                    timestamp=self._timestamp(),
                    payload={"providerKey": request.provider_key, "model": model},
                ),
            )
            seq += 1
            async for chunk in provider.stream_chat(self._to_provider_request(request)):
                if chunk.reasoning_content:
                    yield self._sse(
                        "reasoning",
                        StreamEvent(
                            type="reasoning",
                            ai_request_id=request.ai_request_id,
                            request_id=resolved_request_id,
                            seq=seq,
                            timestamp=self._timestamp(),
                            payload={"content": chunk.reasoning_content},
                        ),
                    )
                    seq += 1
                if chunk.content:
                    yield self._sse(
                        "delta",
                        StreamEvent(
                            type="delta",
                            ai_request_id=request.ai_request_id,
                            request_id=resolved_request_id,
                            seq=seq,
                            timestamp=self._timestamp(),
                            payload={"content": chunk.content},
                        ),
                    )
                    seq += 1
                if chunk.usage is not None:
                    yield self._sse(
                        "usage",
                        StreamEvent(
                            type="usage",
                            ai_request_id=request.ai_request_id,
                            request_id=resolved_request_id,
                            seq=seq,
                            timestamp=self._timestamp(),
                            payload=self._usage_payload(chunk.usage),
                        ),
                    )
                    seq += 1
                if chunk.finish_reason is not None:
                    finish_reason = chunk.finish_reason
            yield self._sse(
                "done",
                StreamEvent(
                    type="done",
                    ai_request_id=request.ai_request_id,
                    request_id=resolved_request_id,
                    seq=seq,
                    timestamp=self._timestamp(),
                    payload={"finishReason": finish_reason or "stop"},
                ),
            )
        except asyncio.CancelledError:
            logger.info("stream cancelled ai_request_id=%s", request.ai_request_id)
            raise
        except AiServiceError as error:
            logger.warning(
                "stream failed ai_request_id=%s code=%s retryable=%s",
                request.ai_request_id,
                error.code,
                error.retryable,
            )
            yield self._sse(
                "error",
                StreamEvent(
                    type="error",
                    ai_request_id=request.ai_request_id,
                    request_id=resolved_request_id,
                    seq=seq,
                    timestamp=self._timestamp(),
                    payload={
                        "code": error.code,
                        "message": error.message,
                        "retryable": error.retryable,
                    },
                ),
            )
        except Exception:
            logger.exception("unexpected stream failure ai_request_id=%s", request.ai_request_id)
            yield self._sse(
                "error",
                StreamEvent(
                    type="error",
                    ai_request_id=request.ai_request_id,
                    request_id=resolved_request_id,
                    seq=seq,
                    timestamp=self._timestamp(),
                    payload={
                        "code": "INTERNAL_ERROR",
                        "message": "internal service error",
                        "retryable": False,
                    },
                ),
            )

    def _to_provider_request(self, request: ChatRequest) -> ProviderChatRequest:
        return ProviderChatRequest(
            model=self._resolve_model(request),
            messages=tuple(
                ProviderMessage(ProviderMessageRole(message.role.value), message.content)
                for message in request.messages
            ),
            temperature=request.temperature,
            max_output_tokens=request.max_output_tokens,
        )

    def _resolve_model(self, request: ChatRequest) -> str:
        model = request.model or self._settings.openai_compatible_default_model
        if not model:
            raise ServiceNotConfiguredError("provider model")
        return model

    def _usage_schema(self, usage: TokenUsage | None) -> TokenUsageSchema | None:
        return None if usage is None else TokenUsageSchema(**self._usage_payload(usage))

    def _usage_payload(self, usage: TokenUsage) -> dict[str, int]:
        return {
            "promptTokens": usage.prompt_tokens,
            "completionTokens": usage.completion_tokens,
            "totalTokens": usage.total_tokens,
        }

    def _sse(self, event: str, payload: StreamEvent) -> str:
        data = json.dumps(
            payload.model_dump(by_alias=True),
            ensure_ascii=False,
            separators=(",", ":"),
        )
        return f"event: {event}\ndata: {data}\n\n"

    def _timestamp(self) -> str:
        return datetime.now(UTC).isoformat(timespec="milliseconds").replace("+00:00", "Z")
