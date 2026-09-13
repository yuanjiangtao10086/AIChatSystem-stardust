import json

import httpx
import pytest
from pydantic import SecretStr

from app.core.settings import Settings
from app.providers.openai_compatible.provider import OpenAICompatibleProvider
from app.providers.registry import ProviderRegistry
from app.providers.types import (
    ChatRequest as ProviderChatRequest,
)
from app.providers.types import (
    ProviderMessage,
    ProviderMessageRole,
    TextPart,
)
from app.schemas.chat import (
    AttachmentKind,
    ChatAttachment,
    ChatMessage,
    ChatRequest,
    ChatRole,
)
from app.services.attachments import build_attachment_context
from app.services.chat import ChatService
from tests.fakes import StubProvider

TEST_MARKER = "STARDUST_FILE_TEST_928374"


def text_attachment() -> ChatAttachment:
    return ChatAttachment(
        file_id="file-1",
        file_name="test.txt",
        mime_type="text/plain",
        size_bytes=len(TEST_MARKER),
        kind=AttachmentKind.TEXT,
        text=TEST_MARKER,
    )


def image_attachment() -> ChatAttachment:
    return ChatAttachment(
        file_id="file-2",
        file_name="pixel.png",
        mime_type="image/png",
        size_bytes=16,
        kind=AttachmentKind.IMAGE,
        image_base64="aGVsbG8=",
    )


def unsupported_attachment() -> ChatAttachment:
    return ChatAttachment(
        file_id="file-3",
        file_name="doc.pdf",
        mime_type="application/pdf",
        size_bytes=1024,
        kind=AttachmentKind.UNSUPPORTED,
        note="no text or image extractor is configured for this file type",
    )


def request_with(*attachments: ChatAttachment) -> ChatRequest:
    return ChatRequest(
        ai_request_id="request-1234",
        provider_key="openai-compatible",
        messages=[
            ChatMessage(role=ChatRole.SYSTEM, content="system"),
            ChatMessage(role=ChatRole.USER, content="这个文件中的测试字符串是什么？"),
        ],
        attachments=list(attachments),
    )


def test_schema_accepts_camel_case_attachments() -> None:
    parsed = ChatRequest.model_validate(
        {
            "schemaVersion": "1",
            "aiRequestId": "request-1234",
            "model": "m",
            "messages": [{"role": "user", "content": "hi"}],
            "attachments": [
                {
                    "fileId": "file-1",
                    "fileName": "test.txt",
                    "mimeType": "text/plain",
                    "sizeBytes": 22,
                    "kind": "TEXT",
                    "text": TEST_MARKER,
                }
            ],
        }
    )
    assert len(parsed.attachments) == 1
    assert parsed.attachments[0].text == TEST_MARKER
    assert parsed.attachments[0].kind is AttachmentKind.TEXT


def test_schema_rejects_unknown_attachment_fields() -> None:
    with pytest.raises(ValueError):
        ChatAttachment.model_validate(
            {
                "fileId": "file-1",
                "fileName": "a.txt",
                "mimeType": "text/plain",
                "sizeBytes": 1,
                "kind": "TEXT",
                "storagePath": "/data/files/a.txt",
            }
        )


def test_text_attachment_becomes_untrusted_block_before_user_turn() -> None:
    context = build_attachment_context([text_attachment()], Settings())
    assert context.text_block is not None
    assert TEST_MARKER in context.text_block
    assert "untrusted" in context.text_block.lower()
    assert context.image_parts == ()


def test_unsupported_attachment_is_reported_as_unreadable() -> None:
    context = build_attachment_context([unsupported_attachment()], Settings())
    assert context.text_block is not None
    assert 'readable="false"' in context.text_block
    assert context.image_parts == ()


def test_attachment_name_is_escaped() -> None:
    attachment = ChatAttachment(
        file_id="file-4",
        file_name='"><script>alert(1)</script>',
        mime_type="text/plain",
        size_bytes=2,
        kind=AttachmentKind.TEXT,
        text="x",
    )
    context = build_attachment_context([attachment], Settings())
    assert context.text_block is not None
    assert "<script>" not in context.text_block


@pytest.mark.asyncio
async def test_chat_service_folds_attachments_into_provider_messages() -> None:
    provider = StubProvider()
    settings = Settings(
        internal_service_token=SecretStr("x" * 32), openai_compatible_default_model="m"
    )
    service = ChatService(ProviderRegistry({"openai-compatible": provider}), settings)

    await service.chat(request_with(text_attachment(), image_attachment()))

    messages = provider.chat_requests[0].messages
    # system + attachment block + user (multimodal: text part + image part)
    assert len(messages) == 3
    assert messages[0].role is ProviderMessageRole.SYSTEM
    assert TEST_MARKER in messages[1].content
    assert messages[2].role is ProviderMessageRole.USER
    assert isinstance(messages[2].content, tuple)
    assert isinstance(messages[2].content[0], TextPart)
    assert messages[2].content[0].text == "这个文件中的测试字符串是什么？"
    assert messages[2].content[1].url == "data:image/png;base64,aGVsbG8="


@pytest.mark.asyncio
async def test_chat_service_keeps_user_turn_last_without_attachments() -> None:
    provider = StubProvider()
    settings = Settings(openai_compatible_default_model="m")
    service = ChatService(ProviderRegistry({"openai-compatible": provider}), settings)

    request = request_with()
    await service.chat(request)
    assert [message.role for message in provider.chat_requests[0].messages] == [
        ProviderMessageRole.SYSTEM,
        ProviderMessageRole.USER,
    ]


@pytest.mark.asyncio
async def test_provider_sends_image_parts_to_the_upstream_api() -> None:
    seen: list[dict] = []

    def handler(http_request: httpx.Request) -> httpx.Response:
        seen.append(json.loads(http_request.content))
        return httpx.Response(
            200,
            json={
                "model": "vision-model",
                "choices": [{"message": {"content": "ok"}, "finish_reason": "stop"}],
                "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
            },
        )

    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    provider = OpenAICompatibleProvider("https://provider.test/v1", "test-key", 5, client)

    await provider.chat(
        ProviderChatRequest(
            model="vision-model",
            messages=(
                ProviderMessage(
                    ProviderMessageRole.USER,
                    (TextPart("what is this?"), *build_attachment_context(
                        [image_attachment()], Settings()
                    ).image_parts),
                ),
            ),
        )
    )

    content = seen[0]["messages"][0]["content"]
    assert content[0] == {"type": "text", "text": "what is this?"}
    assert content[1]["type"] == "image_url"
    assert content[1]["image_url"]["url"] == "data:image/png;base64,aGVsbG8="


def test_image_attachment_without_bytes_is_reported_instead_of_sent() -> None:
    attachment = ChatAttachment(
        file_id="file-5",
        file_name="a.png",
        mime_type="image/png",
        size_bytes=10,
        kind=AttachmentKind.IMAGE,
    )
    context = build_attachment_context([attachment], Settings())
    assert context.image_parts == ()
    assert context.text_block is not None
    assert 'readable="false"' in context.text_block


@pytest.mark.asyncio
async def test_stream_survives_attachment_rendering_and_emits_terminal_event() -> None:
    settings = Settings(openai_compatible_default_model="m")
    service = ChatService(
        ProviderRegistry({"openai-compatible": StubProvider()}), settings
    )
    events = "".join(
        [event async for event in service.stream(request_with(text_attachment()))]
    )
    assert "event: delta" in events
    assert "event: done" in events
