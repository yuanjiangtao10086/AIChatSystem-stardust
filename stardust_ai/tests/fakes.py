from collections.abc import AsyncIterator

from app.core.errors import ProviderUnavailableError
from app.providers.base import LLMProvider
from app.providers.types import (
    ChatRequest,
    ChatResult,
    ChatStreamChunk,
    EmbeddingRequest,
    EmbeddingResult,
    ToolCall,
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


class ArtifactToolProvider(StubProvider):
    """Streams a short answer then a single ``create_artifact`` tool call.

    ``instruction`` is the raw JSON object the model would pass to the tool; ``fail`` makes
    the tool arguments invalid JSON so the caller can assert the artifact_error path.
    """

    def __init__(self, instruction: dict, *, bad_args: bool = False, fail: bool = False) -> None:
        super().__init__(fail=fail)
        self._instruction = instruction
        self._bad_args = bad_args

    async def stream_chat(self, request: ChatRequest) -> AsyncIterator[ChatStreamChunk]:
        if self.fail:
            raise ProviderUnavailableError()
        yield ChatStreamChunk(reasoning_content="thinking")
        yield ChatStreamChunk(content="Here is your file.\n")
        arguments = "not-json" if self._bad_args else __import__("json").dumps(self._instruction)
        yield ChatStreamChunk(
            tool_calls=(
                ToolCall(id="call_1", name="create_artifact", arguments=arguments),
            )
        )
        yield ChatStreamChunk(finish_reason="tool_calls")
        yield ChatStreamChunk(usage=TokenUsage(5, 3, 8))
