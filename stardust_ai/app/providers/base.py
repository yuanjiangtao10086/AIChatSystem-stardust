from abc import ABC, abstractmethod
from collections.abc import AsyncIterator

from app.providers.types import (
    ChatRequest,
    ChatResult,
    ChatStreamChunk,
    EmbeddingRequest,
    EmbeddingResult,
)


class LLMProvider(ABC):
    @abstractmethod
    async def chat(self, request: ChatRequest) -> ChatResult:
        """Generate one complete chat response."""

    @abstractmethod
    def stream_chat(self, request: ChatRequest) -> AsyncIterator[ChatStreamChunk]:
        """Generate normalized chat chunks without exposing vendor event types."""

    @abstractmethod
    async def embedding(self, request: EmbeddingRequest) -> EmbeddingResult:
        """Generate embeddings for one or more inputs."""
