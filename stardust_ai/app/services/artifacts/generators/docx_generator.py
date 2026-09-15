import io

from docx import Document
from docx.shared import Pt

from app.core.errors import ArtifactError
from app.schemas.artifacts import ARTIFACT_TYPE_INFO, ArtifactType
from app.services.artifacts.generators.base import ArtifactGenerator
from app.services.artifacts.schemas import ArtifactInstruction, GeneratedArtifact


class DocxArtifactGenerator:
    """Renders a .docx from ``spec = {title, sections:[{heading,paragraphs,bullets,tables}]}``.

    python-docx never emits VBA macros, so generated documents cannot carry executable
    code. Tables render as a header row plus data rows.
    """

    @property
    def supported_types(self) -> tuple[ArtifactType, ...]:
        return (ArtifactType.DOCX,)

    def generate(self, instruction: ArtifactInstruction) -> GeneratedArtifact:
        if instruction.spec is None:
            raise ArtifactError("ARTIFACT_INVALID_SPEC", "docx requires `spec`")
        spec = instruction.spec
        document = Document()
        title = spec.get("title")
        if title:
            document.add_heading(str(title), level=0)
        for section in spec.get("sections", []) or []:
            heading = section.get("heading")
            if heading:
                document.add_heading(str(heading), level=1)
            for paragraph in section.get("paragraphs", []) or []:
                document.add_paragraph(str(paragraph))
            for bullet in section.get("bullets", []) or []:
                document.add_paragraph(str(bullet), style="List Bullet")
            for table in section.get("tables", []) or []:
                self._add_table(document, table)

        buffer = io.BytesIO()
        document.save(buffer)
        data = buffer.getvalue()
        mime, ext = ARTIFACT_TYPE_INFO[ArtifactType.DOCX]
        return GeneratedArtifact(
            artifact_id=instruction.artifact_id or "",
            filename=instruction.filename,
            type=ArtifactType.DOCX,
            mime_type=mime,
            extension=ext,
            data=data,
            size=len(data),
        )

    @staticmethod
    def _add_table(document: Document, table: dict) -> None:
        rows = table.get("rows", []) or []
        if not rows:
            return
        grid = document.add_table(rows=1, cols=max(len(row) for row in rows))
        grid.style = "Light Grid Accent 1"
        header = table.get("header") or rows[0]
        for index, cell in enumerate(header):
            grid.rows[0].cells[index].text = str(cell)
        for row in rows[1:] if table.get("header") else rows:
            cells = grid.add_row().cells
            for index, value in enumerate(row):
                if index < len(cells):
                    cells[index].text = str(value)
