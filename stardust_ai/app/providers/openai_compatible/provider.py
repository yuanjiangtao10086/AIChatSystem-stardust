import json
from collections.abc import AsyncIterator
from typing import Any

import httpx

from app.core.errors import (
    ProviderError,
    ProviderProtocolError,
    ProviderRateLimitedError,
    ProviderRequestError,
    ProviderTimeoutError,
    ProviderUnavailableError,
)
from app.providers.base import LLMProvider
from app.providers.types import (
    ChatRequest,
    ChatResult,
    ChatStreamChunk,
    EmbeddingRequest,
    EmbeddingResult,
    TokenUsage,
)


class OpenAICompatibleProvider(LLMProvider):
    def __init__(
        self,
        base_url: str,
        api_key: str | None,
        timeout_seconds: float,
        client: httpx.AsyncClient | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._owns_client = client is None
        self._client = client or httpx.AsyncClient(
            timeout=httpx.Timeout(timeout_seconds),
            headers={"User-Agent": "stardust-ai/0.1"},
        )

    async def chat(self, request: ChatRequest) -> ChatResult:
        try:
            response = await self._client.post(
                f"{self._base_url}/chat/completions",
                headers=self._headers(),
                json=self._chat_payload(request, stream=False),
            )
            self._raise_for_status(response.status_code)
            payload = response.json()
            choice = payload["choices"][0]
            content = choice["message"]["content"]
            if not isinstance(content, str):
                raise ProviderProtocolError()
            return ChatResult(
                model=str(payload.get("model") or request.model),
                content=content,
                finish_reason=choice.get("finish_reason"),
                usage=self._parse_usage(payload.get("usage")),
            )
        except ProviderError:
            raise
        except httpx.TimeoutException as error:
            raise ProviderTimeoutError() from error
        except httpx.RequestError as error:
            raise ProviderUnavailableError() from error
        except (KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError) as error:
            raise ProviderProtocolError() from error

    async def stream_chat(self, request: ChatRequest) -> AsyncIterator[ChatStreamChunk]:
        try:
            async with self._client.stream(
                "POST",
                f"{self._base_url}/chat/completions",
                headers=self._headers(),
                json=self._chat_payload(request, stream=True),
            ) as response:
                self._raise_for_status(response.status_code)
                async for line in response.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    data = line[5:].strip()
                    if not data or data == "[DONE]":
                        continue
                    yield self._parse_stream_chunk(json.loads(data))
        except ProviderError:
            raise
        except httpx.TimeoutException as error:
            raise ProviderTimeoutError() from error
        except httpx.RequestError as error:
            raise ProviderUnavailableError() from error
        except (KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError) as error:
            raise ProviderProtocolError() from error

    async def embedding(self, request: EmbeddingRequest) -> EmbeddingResult:
        try:
            response = await self._client.post(
                f"{self._base_url}/embeddings",
                headers=self._headers(),
                json={"model": request.model, "input": list(request.inputs)},
            )
            self._raise_for_status(response.status_code)
            payload = response.json()
            ordered = sorted(payload["data"], key=lambda item: int(item["index"]))
            vectors = tuple(tuple(float(value) for value in item["embedding"]) for item in ordered)
            if len(vectors) != len(request.inputs):
                raise ProviderProtocolError()
            return EmbeddingResult(
                model=str(payload.get("model") or request.model),
                vectors=vectors,
                usage=self._parse_usage(payload.get("usage")),
            )
        except ProviderError:
            raise
        except httpx.TimeoutException as error:
            raise ProviderTimeoutError() from error
        except httpx.RequestError as error:
            raise ProviderUnavailableError() from error
        except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
            raise ProviderProtocolError() from error

    async def aclose(self) -> None:
        if self._owns_client:
            await self._client.aclose()

    def _headers(self) -> dict[str, str]:
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if self._api_key:
            headers["Authorization"] = f"Bearer {self._api_key}"
        return headers

    def _chat_payload(self, request: ChatRequest, stream: bool) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "model": request.model,
            "messages": [
                {"role": message.role.value, "content": message.content}
                for message in request.messages
            ],
            "stream": stream,
        }
        if request.temperature is not None:
            payload["temperature"] = request.temperature
        if request.max_output_tokens is not None:
            payload["max_tokens"] = request.max_output_tokens
        if stream:
            payload["stream_options"] = {"include_usage": True}
        return payload

    def _parse_stream_chunk(self, payload: dict[str, Any]) -> ChatStreamChunk:
        choices = payload.get("choices") or []
        content = ""
        reasoning_content = ""
        finish_reason = None
        if choices:
            delta = choices[0].get("delta") or {}
            raw_content = delta.get("content")
            if raw_content is not None and not isinstance(raw_content, str):
                raise ProviderProtocolError()
            content = raw_content or ""
            raw_reasoning = delta.get("reasoning_content")
            if raw_reasoning is not None and not isinstance(raw_reasoning, str):
                raise ProviderProtocolError()
            reasoning_content = raw_reasoning or ""
            finish_reason = choices[0].get("finish_reason")
        return ChatStreamChunk(
            content=content,
            reasoning_content=reasoning_content,
            finish_reason=finish_reason,
            usage=self._parse_usage(payload.get("usage")),
        )

    def _parse_usage(self, usage: Any) -> TokenUsage | None:
        if usage is None:
            return None
        try:
            prompt = int(usage.get("prompt_tokens", 0))
            completion = int(usage.get("completion_tokens", 0))
            total = int(usage.get("total_tokens", prompt + completion))
        except (AttributeError, TypeError, ValueError) as error:
            raise ProviderProtocolError() from error
        if min(prompt, completion, total) < 0:
            raise ProviderProtocolError()
        return TokenUsage(prompt, completion, total)

    def _raise_for_status(self, status_code: int) -> None:
        if status_code < 400:
            return
        if status_code == 429:
            raise ProviderRateLimitedError()
        if status_code >= 500:
            raise ProviderUnavailableError()
        raise ProviderRequestError()
