import base64
import json

import pytest
from pydantic import SecretStr

from app.core.settings import Settings
from app.providers.registry import ProviderRegistry
from app.services.artifacts.artifact_service import ArtifactService
from app.services.chat import ChatService
from app.schemas.chat import ChatMessage, ChatRequest, ChatRole
from tests.fakes import ArtifactToolProvider, StubProvider


def request() -> ChatRequest:
    return ChatRequest(
        ai_request_id="request-artifact",
        provider_key="openai-compatible",
        messages=[ChatMessage(role=ChatRole.USER, content="make me a file")],
    )


def service(provider: StubProvider) -> ChatService:
    settings = Settings(openai_compatible_default_model="m")
    return ChatService(ProviderRegistry({"openai-compatible": provider}), settings, ArtifactService(settings))


async def collect(stream: ChatService, req: ChatRequest) -> list[dict]:
    events: list[dict] = []
    for raw in "".join([e async for e in stream.stream(req)]).split("\n\n"):
        if not raw.strip():
            continue
        event_type = None
        payload = None
        for line in raw.splitlines():
            if line.startswith("event: "):
                event_type = line[len("event: ") :]
            elif line.startswith("data: "):
                payload = json.loads(line[len("data: ") :])
        events.append({"event": event_type, "payload": (payload or {}).get("payload")})
    return events


@pytest.mark.asyncio
async def test_text_artifact_is_streamed_as_start_delta_done() -> None:
    provider = ArtifactToolProvider(
        {"artifactId": "art-1", "filename": "demo.py", "type": "py", "content": "print(1)\n"}
    )
    events = await collect(service(provider), request())
    types = [e["event"] for e in events]
    assert "artifact_start" in types
    assert "artifact_done" in types
    assert "artifact_error" not in types

    start = next(e for e in events if e["event"] == "artifact_start")
    assert start["payload"]["filename"] == "demo.py"
    assert start["payload"]["artifactType"] == "py"
    assert start["payload"]["artifactId"] == "art-1"

    # The base64 delta chunks must reconstruct the original file text.
    content = ""
    for event in events:
        if event["event"] == "artifact_delta":
            content += base64.b64decode(event["payload"]["content"]).decode("utf-8")
    assert content == "print(1)\n"

    done = next(e for e in events if e["event"] == "artifact_done")
    assert done["payload"]["sha256"]
    assert done["payload"]["size"] == len(b"print(1)\n")


@pytest.mark.asyncio
async def test_invalid_artifact_instruction_emits_artifact_error_not_chat_failure() -> None:
    provider = ArtifactToolProvider({"filename": "x"}, bad_args=True)
    events = await collect(service(provider), request())
    assert "artifact_error" in [e["event"] for e in events]
    # The chat itself still completes normally.
    assert "done" in [e["event"] for e in events]
    assert "error" not in [e["event"] for e in events]


@pytest.mark.asyncio
async def test_no_tool_call_means_no_artifact_events() -> None:
    provider = StubProvider()
    events = await collect(service(provider), request())
    assert not any(e["event"] for e in events if e["event"] and e["event"].startswith("artifact"))


@pytest.mark.asyncio
async def test_office_artifact_streams_when_provider_supports_it() -> None:
    pytest.importorskip("docx")
    provider = ArtifactToolProvider(
        {
            "artifactId": "art-docx",
            "filename": "report.docx",
            "type": "docx",
            "spec": {"title": "T", "sections": [{"heading": "H", "paragraphs": ["p"]}]},
        }
    )
    events = await collect(service(provider), request())
    assert "artifact_done" in [e["event"] for e in events]
    done = next(e for e in events if e["event"] == "artifact_done")
    assert done["payload"]["mimeType"].endswith("wordprocessingml.document")
