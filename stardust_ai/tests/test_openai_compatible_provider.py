import json

import httpx
import pytest

from app.core.errors import ProviderRateLimitedError, ProviderTimeoutError
from app.providers.openai_compatible.provider import OpenAICompatibleProvider
from app.providers.types import (
    ChatRequest,
    EmbeddingRequest,
    ProviderMessage,
    ProviderMessageRole,
)


def chat_request() -> ChatRequest:
    return ChatRequest(
        model="test-chat-model",
        messages=(ProviderMessage(ProviderMessageRole.USER, "hello"),),
        temperature=0.2,
        max_output_tokens=100,
    )


@pytest.mark.asyncio
async def test_openai_compatible_provider_supports_chat_stream_and_embedding() -> None:
    async def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        assert request.headers["Authorization"] == "Bearer test-key"
        if request.url.path.endswith("/chat/completions") and body["stream"]:
            return httpx.Response(
                200,
                text=(
                    'data: {"choices":[{"delta":{"reasoning_content":"think",'
                    '"content":"hello "},"finish_reason":null}]}\n\n'
                    'data: {"choices":[{"delta":{"content":"world"},"finish_reason":"stop"}],'
                    '"usage":{"prompt_tokens":2,"completion_tokens":2,"total_tokens":4}}\n\n'
                    "data: [DONE]\n\n"
                ),
            )
        if request.url.path.endswith("/chat/completions"):
            return httpx.Response(
                200,
                json={
                    "model": "test-chat-model",
                    "choices": [{"message": {"content": "hello world"}, "finish_reason": "stop"}],
                    "usage": {"prompt_tokens": 2, "completion_tokens": 2, "total_tokens": 4},
                },
            )
        return httpx.Response(
            200,
            json={
                "model": "test-embedding-model",
                "data": [
                    {"index": 1, "embedding": [0.3, 0.4]},
                    {"index": 0, "embedding": [0.1, 0.2]},
                ],
                "usage": {"prompt_tokens": 3, "completion_tokens": 0, "total_tokens": 3},
            },
        )

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    provider = OpenAICompatibleProvider("https://provider.test/v1", "test-key", 5, client)

    result = await provider.chat(chat_request())
    assert result.content == "hello world"
    assert result.usage is not None and result.usage.total_tokens == 4

    chunks = [chunk async for chunk in provider.stream_chat(chat_request())]
    assert "".join(chunk.content for chunk in chunks) == "hello world"
    assert chunks[0].reasoning_content == "think"
    assert chunks[-1].usage is not None and chunks[-1].usage.total_tokens == 4

    embeddings = await provider.embedding(
        EmbeddingRequest("test-embedding-model", ("first", "second"))
    )
    assert embeddings.vectors == ((0.1, 0.2), (0.3, 0.4))
    await client.aclose()


@pytest.mark.asyncio
async def test_provider_maps_rate_limit_and_timeout_without_exposing_body() -> None:
    rate_client = httpx.AsyncClient(
        transport=httpx.MockTransport(lambda _: httpx.Response(429, text="secret provider body"))
    )
    rate_provider = OpenAICompatibleProvider("https://provider.test/v1", None, 5, rate_client)
    with pytest.raises(ProviderRateLimitedError, match="rate limit") as rate_error:
        await rate_provider.chat(chat_request())
    assert "secret provider body" not in str(rate_error.value)
    await rate_client.aclose()

    async def timeout_handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("vendor detail", request=request)

    timeout_client = httpx.AsyncClient(transport=httpx.MockTransport(timeout_handler))
    timeout_provider = OpenAICompatibleProvider("https://provider.test/v1", None, 5, timeout_client)
    with pytest.raises(ProviderTimeoutError, match="timed out"):
        await timeout_provider.chat(chat_request())
    await timeout_client.aclose()
