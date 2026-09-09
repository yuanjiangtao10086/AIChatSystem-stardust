import pytest
from pydantic import SecretStr

from app.core.errors import ProviderNotFoundError
from app.core.settings import Settings
from app.providers.registry import ProviderRegistry
from app.schemas.chat import ChatMessage, ChatRequest, ChatRole
from app.services.chat import ChatService
from tests.fakes import StubProvider


def request(provider_key: str = "openai-compatible") -> ChatRequest:
    return ChatRequest(
        ai_request_id="request-1234",
        provider_key=provider_key,
        messages=[ChatMessage(role=ChatRole.USER, content="hello")],
    )


@pytest.mark.asyncio
async def test_chat_service_maps_schema_and_uses_configured_default_model() -> None:
    provider = StubProvider()
    settings = Settings(
        internal_service_token=SecretStr("x" * 32),
        openai_compatible_default_model="configured-model",
    )
    service = ChatService(ProviderRegistry({"openai-compatible": provider}), settings)

    result = await service.chat(request())

    assert result.message.content == "stub response"
    assert result.model == "configured-model"
    assert result.usage is not None and result.usage.total_tokens == 6
    assert provider.chat_requests[0].messages[0].role.value == "user"


@pytest.mark.asyncio
async def test_chat_service_rejects_unknown_provider() -> None:
    service = ChatService(ProviderRegistry({}), Settings())
    with pytest.raises(ProviderNotFoundError):
        await service.chat(request("unknown"))


@pytest.mark.asyncio
async def test_chat_service_emits_normalized_sse_and_terminal_error() -> None:
    settings = Settings(openai_compatible_default_model="configured-model")
    success = ChatService(ProviderRegistry({"openai-compatible": StubProvider()}), settings)
    events = "".join([event async for event in success.stream(request())])
    assert "event: start" in events
    assert "event: reasoning" in events
    assert "event: delta" in events
    assert "event: usage" in events
    assert "event: done" in events

    failure = ChatService(
        ProviderRegistry({"openai-compatible": StubProvider(fail=True)}), settings
    )
    error_events = "".join([event async for event in failure.stream(request())])
    assert "event: error" in error_events
    assert "PROVIDER_UNAVAILABLE" in error_events
