from enum import StrEnum
from typing import Any

from pydantic import Field

from app.schemas.common import ApiModel


class ArtifactType(StrEnum):
    """File kinds the assistant can ask the service to generate.

    Text-like types carry raw ``content``; office types carry a structured ``spec``
    rendered by python-docx / python-pptx / openpyxl.
    """

    TEXT = "text"
    MD = "md"
    CSV = "csv"
    JSON = "json"
    HTML = "html"
    PY = "py"
    SQL = "sql"
    YAML = "yaml"
    XML = "xml"
    DOCX = "docx"
    PPTX = "pptx"
    XLSX = "xlsx"
    PDF = "pdf"


# artifact type -> (MIME type, file extension). The Spring side keeps an equivalent
# whitelist; this copy is what the Python generator emits and the two MUST stay in sync.
ARTIFACT_TYPE_INFO: dict[ArtifactType, tuple[str, str]] = {
    ArtifactType.TEXT: ("text/plain", "txt"),
    ArtifactType.MD: ("text/markdown", "md"),
    ArtifactType.CSV: ("text/csv", "csv"),
    ArtifactType.JSON: ("application/json", "json"),
    ArtifactType.HTML: ("text/html", "html"),
    ArtifactType.PY: ("text/x-python", "py"),
    ArtifactType.SQL: ("application/sql", "sql"),
    ArtifactType.YAML: ("text/yaml", "yaml"),
    ArtifactType.XML: ("application/xml", "xml"),
    ArtifactType.DOCX: (
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "docx",
    ),
    ArtifactType.PPTX: (
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "pptx",
    ),
    ArtifactType.XLSX: (
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "xlsx",
    ),
    ArtifactType.PDF: ("application/pdf", "pdf"),
}

TEXT_TYPES = {
    ArtifactType.TEXT,
    ArtifactType.MD,
    ArtifactType.CSV,
    ArtifactType.JSON,
    ArtifactType.HTML,
    ArtifactType.PY,
    ArtifactType.SQL,
    ArtifactType.YAML,
    ArtifactType.XML,
}

OFFICE_TYPES = {ArtifactType.DOCX, ArtifactType.PPTX, ArtifactType.XLSX, ArtifactType.PDF}


class ArtifactInstruction(ApiModel):
    """One file the model asked the service to create via the ``create_artifact`` tool.

    ``artifactId`` is optional: when the model omits it the chat service assigns one so
    the streaming correlation id is always present.
    """

    artifact_id: str | None = Field(default=None, max_length=64)
    filename: str = Field(min_length=1, max_length=255)
    type: ArtifactType
    content: str | None = Field(default=None)
    spec: dict[str, Any] | None = Field(default=None)


def build_create_artifact_tool() -> dict[str, Any]:
    """OpenAI-compatible function-tool description for ``create_artifact``."""
    return {
        "type": "function",
        "function": {
            "name": "create_artifact",
            "description": (
                "Create a downloadable file for the user. Use this tool whenever the user asks "
                "for a file, script, document, spreadsheet, presentation, or PDF — including but "
                "not limited to .py/.sql/.yaml/.xml/.md/.txt/.csv/.json/.html files and "
                ".docx/.pptx/.xlsx/.pdf office files. NEVER put long file contents directly in "
                "the chat reply; instead put the full content in this tool's `content` field and "
                "write only a short plain-text sentence in the normal reply explaining what the "
                "file contains. For text types set `content` to the full file text; for office "
                "types (docx/pptx/xlsx) set `spec` to the structured description; for pdf set "
                "`spec` to the same docx-like structure {title, sections:[{heading,paragraphs,"
                "bullets,tables}]} or set `content` to plain text / markdown."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "artifactId": {
                        "type": "string",
                        "description": "Stable id for this file; reuse across a conversation when replacing it.",
                    },
                    "filename": {
                        "type": "string",
                        "description": "File name with extension, e.g. demo.py",
                    },
                    "type": {
                        "type": "string",
                        "enum": [t.value for t in ArtifactType],
                    },
                    "content": {
                        "type": "string",
                        "description": "Full file text for text types (py/md/txt/csv/json/html/sql/yaml/xml).",
                    },
                    "spec": {
                        "type": "object",
                        "description": (
                            "Structured spec for office types. docx/pdf: {title, sections:"
                            "[{heading,paragraphs,bullets,tables}]}; pptx: {title, slides:"
                            "[{title,bullets,notes}]}; xlsx: {sheets:[{name,columns,rows}]}."
                        ),
                    },
                },
                "required": ["filename", "type"],
            },
        },
    }
