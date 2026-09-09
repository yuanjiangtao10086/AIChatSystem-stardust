import httpx
import pytest
from pydantic import SecretStr

from app.core.settings import Settings
from app.main import create_app
from app.providers.registry import ProviderRegistry
from tests.fakes import StubProvider

TOKEN = "internal-service-test-token-1234567890"


def make_transport(provider: StubProvider | None = None) -> httpx.ASGITransport:
    settings = Settings(
        internal_service_token=SecretStr(TOKEN),
        openai_compatible_default_model="configured-model",
    )
    app = create_app(
        settings,
        ProviderRegistry({"openai-compatible": provider or StubProvider()}),
    )
    return httpx.ASGITransport(app=app, raise_app_exceptions=False)


def payload() -> dict[str, object]:
    return {
        "schemaVersion": "1",
        "aiRequestId": "ai-request-1234",
        "messages": [{"role": "user", "content": "hello"}],
    }


@pytest.mark.asyncio
async def test_health_is_available_without_internal_credential() -> None:
    async with httpx.AsyncClient(transport=make_transport(), base_url="http://test") as client:
        response = await client.get("/health")
    assert response.status_code == 200
    assert response.json()["ready"] is True
    assert response.json()["providers"] == ["openai-compatible"]


@pytest.mark.asyncio
async def test_internal_chat_requires_service_token_and_propagates_request_id() -> None:
    async with httpx.AsyncClient(transport=make_transport(), base_url="http://test") as client:
        unauthorized = await client.post("/internal/chat", json=payload())
        response = await client.post(
            "/internal/chat",
            json=payload(),
            headers={"X-Service-Authorization": TOKEN, "X-Request-Id": "spring-request-123"},
        )
    assert unauthorized.status_code == 401
    assert unauthorized.json()["code"] == "INTERNAL_UNAUTHORIZED"
    assert response.status_code == 200
    assert response.headers["X-Request-Id"] == "spring-request-123"
    assert response.json()["requestId"] == "spring-request-123"
    assert response.json()["message"]["content"] == "stub response"


@pytest.mark.asyncio
async def test_endpoint_rejects_unknown_fields_without_echoing_input() -> None:
    invalid = payload() | {"apiKey": "must-never-be-echoed"}
    async with httpx.AsyncClient(transport=make_transport(), base_url="http://test") as client:
        response = await client.post(
            "/internal/chat",
            json=invalid,
            headers={"X-Service-Authorization": TOKEN},
        )
    assert response.status_code == 422
    assert response.json()["code"] == "SCHEMA_VALIDATION_ERROR"
    assert "must-never-be-echoed" not in response.text


@pytest.mark.asyncio
async def test_stream_endpoint_uses_sse_and_maps_provider_failure() -> None:
    async with httpx.AsyncClient(
        transport=make_transport(StubProvider(fail=True)), base_url="http://test"
    ) as client:
        response = await client.post(
            "/internal/chat/stream",
            json=payload(),
            headers={
                "X-Service-Authorization": TOKEN,
                "X-Request-Id": "spring-stream-request-123",
            },
        )
    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    assert "event: start" in response.text
    assert "event: error" in response.text
    assert "PROVIDER_UNAVAILABLE" in response.text
    assert '"requestId":"spring-stream-request-123"' in response.text


@pytest.mark.asyncio
async def test_non_stream_provider_error_has_stable_sanitized_mapping() -> None:
    async with httpx.AsyncClient(
        transport=make_transport(StubProvider(fail=True)), base_url="http://test"
    ) as client:
        response = await client.post(
            "/internal/chat",
            json=payload(),
            headers={"X-Service-Authorization": TOKEN},
        )
    assert response.status_code == 502
    assert response.json()["code"] == "PROVIDER_UNAVAILABLE"
    assert response.json()["retryable"] is True


@pytest.mark.asyncio
async def test_internal_rag_process_and_retrieve() -> None:
    settings = Settings(
        internal_service_token=SecretStr(TOKEN),
        openai_compatible_default_model="configured-model",
        openai_compatible_embedding_model="embedding-model",
    )
    app = create_app(settings, ProviderRegistry({"openai-compatible": StubProvider()}))
    transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
    headers = {
        "X-Service-Authorization": TOKEN,
        "Content-Type": "application/octet-stream",
        "X-User-Id": "user-a",
        "X-Knowledge-Base-Id": "kb-a",
        "X-Document-Id": "doc-a",
        "X-Document-Name": "notes.txt",
        "X-Document-Mime": "text/plain",
        "X-Embedding-Model": "embedding-model",
    }
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        processed = await client.post(
            "/internal/rag/documents/process", content=b"Stardust retrieval notes", headers=headers
        )
        retrieved = await client.post(
            "/internal/rag/retrieve",
            json={
                "userId": "user-a",
                "knowledgeBaseIds": ["kb-a"],
                "query": "Stardust",
                "model": "embedding-model",
            },
            headers={"X-Service-Authorization": TOKEN},
        )
    assert processed.status_code == 200
    assert processed.json()["chunks"][0]["content"] == "Stardust retrieval notes"
    assert retrieved.status_code == 200
    assert retrieved.json()["sources"][0]["documentId"] == "doc-a"
