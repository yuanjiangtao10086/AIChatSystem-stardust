import re
from dataclasses import dataclass
from typing import Protocol

from app.core.errors import AiServiceError, ServiceNotConfiguredError
from app.core.settings import Settings
from app.providers.registry import ProviderRegistry
from app.providers.types import EmbeddingRequest
from app.schemas.rag import (
    ProcessDocumentResponse,
    ProcessedChunk,
    RetrievedSource,
    RetrieveRequest,
    RetrieveResponse,
)
from app.vectorstores.base import VectorRecord, VectorStore


class DocumentParser(Protocol):
    def supports(self, mime_type: str, filename: str) -> bool: ...
    async def parse(self, content: bytes, filename: str) -> str: ...


class TextCleaner(Protocol):
    def clean(self, text: str) -> str: ...


class TextSplitter(Protocol):
    def split(self, text: str) -> tuple[str, ...]: ...


class EmbeddingProvider(Protocol):
    async def embed(
        self, texts: tuple[str, ...], provider_key: str, model: str
    ) -> tuple[tuple[float, ...], ...]: ...


class Reranker(Protocol):
    async def rerank(self, query: str, matches: tuple[object, ...]) -> tuple[object, ...]: ...


class PlainTextParser:
    def supports(self, mime_type: str, filename: str) -> bool:
        return mime_type in {
            "text/plain",
            "text/markdown",
            "text/csv",
            "application/json",
            "text/json",
        }

    async def parse(self, content: bytes, filename: str) -> str:
        try:
            return content.decode("utf-8")
        except UnicodeDecodeError as error:
            raise AiServiceError(
                "DOCUMENT_PARSE_FAILED", "document is not valid UTF-8 text", 422
            ) from error


class DefaultTextCleaner:
    def clean(self, text: str) -> str:
        return re.sub(r"\n{3,}", "\n\n", text.replace("\x00", "")).strip()


class BudgetTextSplitter:
    def __init__(self, chunk_chars: int = 1800, overlap_chars: int = 180) -> None:
        self.chunk_chars = chunk_chars
        self.overlap_chars = overlap_chars

    def split(self, text: str) -> tuple[str, ...]:
        if not text:
            return ()
        chunks: list[str] = []
        start = 0
        while start < len(text):
            end = min(len(text), start + self.chunk_chars)
            if end < len(text):
                boundary = max(text.rfind("\n", start, end), text.rfind("。", start, end))
                if boundary > start + self.chunk_chars // 2:
                    end = boundary + 1
            chunk = text[start:end].strip()
            if chunk:
                chunks.append(chunk)
            if end >= len(text):
                break
            start = max(start + 1, end - self.overlap_chars)
        return tuple(chunks)


class ProviderEmbeddingAdapter:
    def __init__(self, registry: ProviderRegistry) -> None:
        self.registry = registry

    async def embed(
        self, texts: tuple[str, ...], provider_key: str, model: str
    ) -> tuple[tuple[float, ...], ...]:
        result = await self.registry.get(provider_key).embedding(EmbeddingRequest(model, texts))
        return result.vectors


@dataclass(frozen=True, slots=True)
class DocumentIdentity:
    user_id: str
    knowledge_base_id: str
    document_id: str
    filename: str
    mime_type: str
    provider_key: str
    embedding_model: str


class RagService:
    def __init__(
        self, registry: ProviderRegistry, vector_store: VectorStore, settings: Settings
    ) -> None:
        self.settings = settings
        self.parser = PlainTextParser()
        self.cleaner = DefaultTextCleaner()
        self.splitter = BudgetTextSplitter(
            settings.rag_chunk_chars, settings.rag_chunk_overlap_chars
        )
        self.embedding = ProviderEmbeddingAdapter(registry)
        self.vector_store = vector_store

    async def process(self, identity: DocumentIdentity, content: bytes) -> ProcessDocumentResponse:
        if not self.parser.supports(identity.mime_type, identity.filename):
            raise AiServiceError(
                "DOCUMENT_TYPE_UNSUPPORTED", "document parser is not configured for this type", 422
            )
        text = self.cleaner.clean(await self.parser.parse(content, identity.filename))
        chunks = self.splitter.split(text)
        if not chunks:
            raise AiServiceError("DOCUMENT_EMPTY", "document contains no indexable text", 422)
        vectors = await self.embedding.embed(
            chunks, identity.provider_key, identity.embedding_model
        )
        if len(vectors) != len(chunks):
            raise AiServiceError("EMBEDDING_PROTOCOL_ERROR", "embedding count mismatch", 502)
        processed = tuple(
            ProcessedChunk(
                chunk_index=index,
                content=chunk,
                token_count=self._tokens(chunk),
                source_metadata={"filename": identity.filename},
            )
            for index, chunk in enumerate(chunks)
        )
        records = tuple(
            VectorRecord(
                id=f"{identity.document_id}:{chunk.chunk_index}",
                user_id=identity.user_id,
                knowledge_base_id=identity.knowledge_base_id,
                document_id=identity.document_id,
                chunk_index=chunk.chunk_index,
                content=chunk.content,
                token_count=chunk.token_count,
                vector=vectors[chunk.chunk_index],
                metadata=chunk.source_metadata,
            )
            for chunk in processed
        )
        await self.vector_store.replace_document(records)
        return ProcessDocumentResponse(
            document_id=identity.document_id,
            knowledge_base_id=identity.knowledge_base_id,
            chunks=list(processed),
            embedding_model=identity.embedding_model,
        )

    async def retrieve(self, request: RetrieveRequest) -> RetrieveResponse:
        model = request.model or self.settings.openai_compatible_embedding_model
        if not model:
            raise ServiceNotConfiguredError("embedding model")
        vector = (await self.embedding.embed((request.query,), request.provider_key, model))[0]
        matches = await self.vector_store.search(
            request.user_id, tuple(request.knowledge_base_ids), vector, request.top_k * 3
        )
        sources: list[RetrievedSource] = []
        used = 0
        for match in matches:
            cost = match.record.token_count + 8
            if used + cost > request.token_budget:
                continue
            sources.append(
                RetrievedSource(
                    document_id=match.record.document_id,
                    knowledge_base_id=match.record.knowledge_base_id,
                    chunk_index=match.record.chunk_index,
                    content=match.record.content,
                    token_count=match.record.token_count,
                    score=match.score,
                    page=match.record.page,
                    source_metadata=match.record.metadata,
                )
            )
            used += cost
            if len(sources) >= request.top_k:
                break
        return RetrieveResponse(sources=sources)

    def _tokens(self, text: str) -> int:
        return max(1, (len(text) + 3) // 4)
