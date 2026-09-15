import io

import pytest
from pydantic import SecretStr

from app.core.errors import ArtifactError
from app.core.settings import Settings
from app.schemas.artifacts import ArtifactType
from app.services.artifacts.artifact_service import ArtifactService
from app.services.artifacts.schemas import ArtifactInstruction


def service(max_bytes: int | None = None) -> ArtifactService:
    settings = Settings(internal_service_token=SecretStr("x" * 32))
    if max_bytes is not None:
        settings.artifact_max_bytes = max_bytes
    return ArtifactService(settings)


def test_text_artifact_uses_utf8_and_emits_expected_metadata() -> None:
    artifact = service().generate(
        ArtifactInstruction(
            artifact_id="a1",
            filename="demo.py",
            type=ArtifactType.PY,
            content="print('hi')\n",
        )
    )
    assert artifact.data == b"print('hi')\n"
    assert artifact.mime_type == "text/x-python"
    assert artifact.extension == "py"
    assert artifact.size == len(artifact.data)


def test_text_artifact_requires_content() -> None:
    with pytest.raises(ArtifactError):
        service().generate(
            ArtifactInstruction(artifact_id="a", filename="x.md", type=ArtifactType.MD)
        )


def test_unknown_type_value_is_rejected() -> None:
    # Unknown values fail schema validation (pydantic ValueError), before reaching the service.
    with pytest.raises(ValueError):
        ArtifactInstruction(
            artifact_id="a",
            filename="x.bin",
            type="bin",  # type: ignore[arg-type]
        )


def test_oversize_artifact_is_rejected() -> None:
    with pytest.raises(ArtifactError) as exc:
        service(max_bytes=8).generate(
            ArtifactInstruction(
                artifact_id="a",
                filename="big.txt",
                type=ArtifactType.TEXT,
                content="x" * 16,
            )
        )
    assert exc.value.code == "ARTIFACT_TOO_LARGE"


def test_docx_artifact_is_a_real_office_document() -> None:
    docx = pytest.importorskip("docx")
    artifact = service().generate(
        ArtifactInstruction(
            artifact_id="d1",
            filename="report.docx",
            type=ArtifactType.DOCX,
            spec={
                "title": "Quarterly Report",
                "sections": [
                    {
                        "heading": "Summary",
                        "paragraphs": ["Hello world"],
                        "bullets": ["one", "two"],
                    }
                ],
            },
        )
    )
    assert artifact.data[:2] == b"PK"  # zip/OOXML magic
    assert artifact.mime_type.endswith("wordprocessingml.document")
    document = docx.Document(io.BytesIO(artifact.data))
    texts = [p.text for p in document.paragraphs]
    assert "Quarterly Report" in texts
    assert "Hello world" in texts


def test_pptx_artifact_is_a_real_office_document() -> None:
    pptx = pytest.importorskip("pptx")
    artifact = service().generate(
        ArtifactInstruction(
            artifact_id="p1",
            filename="deck.pptx",
            type=ArtifactType.PPTX,
            spec={
                "title": "Deck",
                "slides": [
                    {"title": "Slide 1", "bullets": ["a", "b"], "notes": "remember this"}
                ],
            },
        )
    )
    assert artifact.data[:2] == b"PK"
    assert artifact.mime_type.endswith("presentationml.presentation")
    presentation = pptx.Presentation(io.BytesIO(artifact.data))
    titles = [slide.shapes.title.text for slide in presentation.slides]
    assert "Slide 1" in titles


def test_xlsx_artifact_is_a_real_office_document() -> None:
    openpyxl = pytest.importorskip("openpyxl")
    artifact = service().generate(
        ArtifactInstruction(
            artifact_id="x1",
            filename="data.xlsx",
            type=ArtifactType.XLSX,
            spec={
                "sheets": [
                    {
                        "name": "Users",
                        "columns": ["id", "name"],
                        "rows": [[1, "alice"], [2, "bob"]],
                    }
                ]
            },
        )
    )
    assert artifact.data[:2] == b"PK"
    assert artifact.mime_type.endswith("spreadsheetml.sheet")
    workbook = openpyxl.load_workbook(io.BytesIO(artifact.data))
    sheet = workbook["Users"]
    assert [cell.value for cell in next(sheet.iter_rows())] == ["id", "name"]
    assert [cell.value for cell in list(sheet.iter_rows())[1]] == [1, "alice"]
