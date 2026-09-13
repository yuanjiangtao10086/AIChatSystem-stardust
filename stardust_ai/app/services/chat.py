import asyncio
import json
import logging
import time
from collections.abc import AsyncIterator
from datetime import UTC, datetime

from app.core.errors import AiServiceError, ServiceNotConfiguredError
from app.core.request_context import current_request_id
from app.core.settings import Settings
from app.providers.registry import ProviderRegistry
from app.providers.types import ChatRequest as ProviderChatRequest
from app.providers.types import (
    ProviderMessage,
    ProviderMessageRole,
    TextPart,
    TokenUsage,
)
from app.schemas.chat import (
    ChatMessage,
    ChatRequest,
    ChatResponse,
    ChatRole,
    StreamEvent,
)
from app.schemas.chat import TokenUsage as TokenUsageSchema
from app.services.attachments import AttachmentContext, build_attachment_context

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
        usage_sent = False
        resolved_request_id = request_id or current_request_id()
        finish_reason: str | None = None
        start_ns = time.perf_counter_ns()
        first_token_ns: int | None = None
        logger.info(
            "chat stream start ai_request_id=%s provider_key=%s model=%s"
            " message_count=%d attachment_count=%d",
            request.ai_request_id,
            request.provider_key,
            request.model,
            len(request.messages),
            len(request.attachments),
        )
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
                if first_token_ns is None and (chunk.content or chunk.reasoning_content):
                    first_token_ns = time.perf_counter_ns()
                    logger.info(
                        "chat stream first token ai_request_id=%s serviceTtftMs=%.1f",
                        request.ai_request_id,
                        (first_token_ns - start_ns) / 1e6,
                    )
                if chunk.reasoning_content:
                    throttle = self._settings.stream_throttle_ms
                    if throttle > 0:
                        for piece in self._split_content(chunk.reasoning_content):
                            yield self._sse(
                                "reasoning",
                                StreamEvent(
                                    type="reasoning",
                                    ai_request_id=request.ai_request_id,
                                    request_id=resolved_request_id,
                                    seq=seq,
                                    timestamp=self._timestamp(),
                                    payload={"content": piece},
                                ),
                            )
                            seq += 1
                            await asyncio.sleep(throttle / 1000.0)
                    else:
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
                    throttle = self._settings.stream_throttle_ms
                    if throttle > 0:
                        for piece in self._split_content(chunk.content):
                            yield self._sse(
                                "delta",
                                StreamEvent(
                                    type="delta",
                                    ai_request_id=request.ai_request_id,
                                    request_id=resolved_request_id,
                                    seq=seq,
                                    timestamp=self._timestamp(),
                                    payload={"content": piece},
                                ),
                            )
                            seq += 1
                            await asyncio.sleep(throttle / 1000.0)
                    else:
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
                    usage_sent = True
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
            if not usage_sent:
                # Spring settles the request from this event; a provider that
                # ignores stream_options would otherwise leave usage unset.
                logger.warning(
                    "provider omitted usage ai_request_id=%s; emitting zero usage",
                    request.ai_request_id,
                )
                yield self._sse(
                    "usage",
                    StreamEvent(
                        type="usage",
                        ai_request_id=request.ai_request_id,
                        request_id=resolved_request_id,
                        seq=seq,
                        timestamp=self._timestamp(),
                        payload={"promptTokens": 0, "completionTokens": 0, "totalTokens": 0},
                    ),
                )
                seq += 1
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
            messages=self._build_messages(request),
            temperature=request.temperature,
            max_output_tokens=request.max_output_tokens,
        )

    def _build_messages(self, request: ChatRequest) -> tuple[ProviderMessage, ...]:
        """Converts schema messages to provider messages, folding attachments in.

        Attachment text becomes one untrusted block immediately before the final user turn; images
        become vision parts of that same turn, which is the only place providers accept them. The
        current user message therefore always stays last, as the context contract requires.
        """
        context: AttachmentContext | None = None
        if request.attachments:
            context = build_attachment_context(request.attachments, self._settings)
            logger.info(
                "chat attachments ai_request_id=%s attachmentCount=%d"
                " imageCount=%d hasTextBlock=%s",
                request.ai_request_id,
                len(request.attachments),
                len(context.image_parts),
                context.text_block is not None,
            )
        last_user_index = -1
        for index, message in enumerate(request.messages):
            if message.role is ChatRole.USER:
                last_user_index = index

        messages: list[ProviderMessage] = []
        if last_user_index < 0 and context is not None and context.text_block:
            # No user turn to anchor to: keep the attachment data, still marked untrusted.
            messages.append(ProviderMessage(ProviderMessageRole.SYSTEM, context.text_block))
        for index, message in enumerate(request.messages):
            role = ProviderMessageRole(message.role.value)
            if index != last_user_index or context is None:
                messages.append(ProviderMessage(role, message.content))
                continue
            if context.text_block:
                messages.append(ProviderMessage(ProviderMessageRole.SYSTEM, context.text_block))
            if context.image_parts:
                messages.append(
                    ProviderMessage(role, (TextPart(message.content), *context.image_parts))
                )
            else:
                messages.append(ProviderMessage(role, message.content))
        return tuple(messages)

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

    @staticmethod
    def _split_content(content: str, step: int = 2) -> list[str]:
        # Break a (possibly large, provider-batched) delta into small visible
        # pieces so paced streaming renders incrementally instead of popping in.
        return [content[i : i + step] for i in range(0, len(content), step)]

    def _sse(self, event: str, payload: StreamEvent) -> str:
        data = json.dumps(
            payload.model_dump(by_alias=True),
            ensure_ascii=False,
            separators=(",", ":"),
        )
        return f"event: {event}\ndata: {data}\n\n"

    def _timestamp(self) -> str:
        return datetime.now(UTC).isoformat(timespec="milliseconds").replace("+00:00", "Z")
