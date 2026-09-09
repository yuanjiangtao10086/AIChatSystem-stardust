from enum import StrEnum
from typing import Any, Literal

from pydantic import Field, field_validator

from app.schemas.common import AI_REQUEST_ID_PATTERN, ApiModel


class ChatRole(StrEnum):
    SYSTEM = "system"
    USER = "user"
    ASSISTANT = "assistant"
    TOOL = "tool"


class ChatMessage(ApiModel):
    role: ChatRole
    content: str = Field(min_length=1, max_length=100_000)


class ChatRequest(ApiModel):
    schema_version: Literal["1"] = "1"
    ai_request_id: str
    provider_key: str = Field(default="openai-compatible", min_length=1, max_length=64)
    model: str | None = Field(default=None, min_length=1, max_length=256)
    messages: list[ChatMessage] = Field(min_length=1, max_length=200)
    temperature: float | None = Field(default=None, ge=0, le=2)
    max_output_tokens: int | None = Field(default=None, ge=1, le=131_072)

    @field_validator("ai_request_id")
    @classmethod
    def validate_ai_request_id(cls, value: str) -> str:
        if not AI_REQUEST_ID_PATTERN.fullmatch(value):
            raise ValueError("aiRequestId must contain 8-64 safe identifier characters")
        return value


class TokenUsage(ApiModel):
    prompt_tokens: int = Field(ge=0)
    completion_tokens: int = Field(ge=0)
    total_tokens: int = Field(ge=0)


class ChatResponse(ApiModel):
    schema_version: Literal["1"] = "1"
    ai_request_id: str
    request_id: str
    provider_key: str
    model: str
    message: ChatMessage
    finish_reason: str | None = None
    usage: TokenUsage | None = None


class StreamEvent(ApiModel):
    schema_version: Literal["1"] = "1"
    type: Literal[
        "start",
        "delta",
        "reasoning",
        "usage",
        "done",
        "error",
        "citation",
        "tool_start",
        "tool_delta",
        "tool_done",
    ]
    ai_request_id: str
    request_id: str
    seq: int = Field(ge=0)
    timestamp: str
    payload: dict[str, Any]
