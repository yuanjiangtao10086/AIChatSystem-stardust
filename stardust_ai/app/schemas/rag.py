from typing import Any

from pydantic import Field

from app.schemas.common import ApiModel


class ProcessedChunk(ApiModel):
    chunk_index: int
    content: str
    token_count: int
    page: int | None = None
    source_metadata: dict[str, Any] = Field(default_factory=dict)


class ProcessDocumentResponse(ApiModel):
    document_id: str
    knowledge_base_id: str
    chunks: list[ProcessedChunk]
    embedding_model: str


class RetrieveRequest(ApiModel):
    user_id: str = Field(min_length=1, max_length=64)
    knowledge_base_ids: list[str] = Field(min_length=1, max_length=20)
    query: str = Field(min_length=1, max_length=32_000)
    top_k: int = Field(default=6, ge=1, le=20)
    token_budget: int = Field(default=1200, ge=32, le=16_000)
    provider_key: str = Field(default="openai-compatible", min_length=1, max_length=64)
    model: str | None = Field(default=None, min_length=1, max_length=256)


class RetrievedSource(ApiModel):
    document_id: str
    knowledge_base_id: str
    chunk_index: int
    content: str
    token_count: int
    score: float
    page: int | None = None
    source_metadata: dict[str, Any] = Field(default_factory=dict)


class RetrieveResponse(ApiModel):
    sources: list[RetrievedSource]


class DeleteDocumentRequest(ApiModel):
    user_id: str = Field(min_length=1, max_length=64)
    knowledge_base_id: str = Field(min_length=1, max_length=64)
