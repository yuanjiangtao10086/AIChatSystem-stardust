from dataclasses import dataclass, field
from enum import StrEnum


class ProviderMessageRole(StrEnum):
    SYSTEM = "system"
    USER = "user"
    ASSISTANT = "assistant"
    TOOL = "tool"


@dataclass(frozen=True, slots=True)
class TextPart:
    """One text segment of a multimodal user message."""

    text: str


@dataclass(frozen=True, slots=True)
class ImagePart:
    """One image of a multimodal user message, addressed by URL or data URI."""

    url: str
    detail: str = "auto"


ContentPart = TextPart | ImagePart


@dataclass(frozen=True, slots=True)
class ProviderMessage:
    role: ProviderMessageRole
    content: str | tuple[ContentPart, ...]


@dataclass(frozen=True, slots=True)
class ChatRequest:
    model: str
    messages: tuple[ProviderMessage, ...]
    temperature: float | None = None
    max_output_tokens: int | None = None


@dataclass(frozen=True, slots=True)
class TokenUsage:
    prompt_tokens: int
    completion_tokens: int
    total_tokens: int


@dataclass(frozen=True, slots=True)
class ChatResult:
    model: str
    content: str
    finish_reason: str | None
    usage: TokenUsage | None = None


@dataclass(frozen=True, slots=True)
class ChatStreamChunk:
    content: str = ""
    reasoning_content: str = ""
    finish_reason: str | None = None
    usage: TokenUsage | None = None


@dataclass(frozen=True, slots=True)
class EmbeddingRequest:
    model: str
    inputs: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class EmbeddingResult:
    model: str
    vectors: tuple[tuple[float, ...], ...]
    usage: TokenUsage | None = None
    metadata: dict[str, str] = field(default_factory=dict)
