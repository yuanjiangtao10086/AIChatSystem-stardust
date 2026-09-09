from collections.abc import AsyncIterator

from app.core.errors import ProviderUnavailableError
from app.providers.base import LLMProvider
from app.providers.types import (
    ChatRequest,
    ChatResult,
    ChatStreamChunk,
    EmbeddingRequest,
    EmbeddingResult,
    TokenUsage,
)


class StubProvider(LLMProvider):
    def __init__(self, fail: bool = False) -> None:
        self.fail = fail
        self.chat_requests: list[ChatRequest] = []

    async def chat(self, request: ChatRequest) -> ChatResult:
        self.chat_requests.append(request)
        if self.fail:
            raise ProviderUnavailableError()
        return ChatResult(
            model=request.model,
            content="stub response",
            finish_reason="stop",
            usage=TokenUsage(4, 2, 6),
        )

    async def stream_chat(self, request: ChatRequest) -> AsyncIterator[ChatStreamChunk]:
        if self.fail:
            raise ProviderUnavailableError()
        yield ChatStreamChunk(reasoning_content="thinking")
        yield ChatStreamChunk(content="stub ")
        yield ChatStreamChunk(content="stream", finish_reason="stop")
        yield ChatStreamChunk(usage=TokenUsage(4, 2, 6))

    async def embedding(self, request: EmbeddingRequest) -> EmbeddingResult:
        return EmbeddingResult(
            model=request.model,
            vectors=tuple((0.1, 0.2) for _ in request.inputs),
        )
