import pytest

from app.core.settings import Settings
from app.providers.registry import ProviderRegistry
from app.schemas.rag import RetrieveRequest
from app.services.rag import DocumentIdentity, RagService
from app.vectorstores.memory import InMemoryVectorStore
from tests.fakes import StubProvider


@pytest.mark.asyncio
async def test_pipeline_chunks_embeds_and_enforces_owner_namespace() -> None:
    service = RagService(
        ProviderRegistry({"openai-compatible": StubProvider()}),
        InMemoryVectorStore(),
        Settings(
            openai_compatible_default_model="chat-model",
            openai_compatible_embedding_model="embedding-model",
            rag_chunk_chars=256,
            rag_chunk_overlap_chars=32,
        ),
    )
    await service.process(
        DocumentIdentity(
            "user-a",
            "kb-a",
            "doc-a",
            "alpha.txt",
            "text/plain",
            "openai-compatible",
            "embedding-model",
        ),
        ("Alpha project architecture. " * 30).encode(),
    )
    await service.process(
        DocumentIdentity(
            "user-b",
            "kb-b",
            "doc-b",
            "private.txt",
            "text/plain",
            "openai-compatible",
            "embedding-model",
        ),
        b"User B private material",
    )

    result = await service.retrieve(
        RetrieveRequest(
            user_id="user-a",
            knowledge_base_ids=["kb-a", "kb-b"],
            query="architecture",
            top_k=3,
            token_budget=300,
            model="embedding-model",
        )
    )

    assert result.sources
    assert all(source.document_id == "doc-a" for source in result.sources)
    assert sum(source.token_count for source in result.sources) <= 300


@pytest.mark.asyncio
async def test_unsupported_document_type_fails_without_indexing() -> None:
    service = RagService(
        ProviderRegistry({"openai-compatible": StubProvider()}),
        InMemoryVectorStore(),
        Settings(openai_compatible_embedding_model="embedding-model"),
    )
    with pytest.raises(Exception) as captured:
        await service.process(
            DocumentIdentity(
                "user-a",
                "kb-a",
                "doc-a",
                "file.pdf",
                "application/pdf",
                "openai-compatible",
                "embedding-model",
            ),
            b"%PDF-test",
        )
    assert getattr(captured.value, "code", None) == "DOCUMENT_TYPE_UNSUPPORTED"
