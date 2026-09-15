import io

from openpyxl import Workbook

from app.core.errors import ArtifactError
from app.schemas.artifacts import ARTIFACT_TYPE_INFO, ArtifactType
from app.services.artifacts.generators.base import ArtifactGenerator
from app.services.artifacts.schemas import ArtifactInstruction, GeneratedArtifact


class XlsxArtifactGenerator:
    """Renders a .xlsx from ``spec = {sheets:[{name,columns,rows}]}``.

    openpyxl produces macro-free workbooks. The first sheet reuses the active sheet; extra
    sheets are created. ``columns`` become the header row when present.
    """

    @property
    def supported_types(self) -> tuple[ArtifactType, ...]:
        return (ArtifactType.XLSX,)

    def generate(self, instruction: ArtifactInstruction) -> GeneratedArtifact:
        if instruction.spec is None:
            raise ArtifactError("ARTIFACT_INVALID_SPEC", "xlsx requires `spec`")
        sheets = instruction.spec.get("sheets", []) or []
        if not sheets:
            raise ArtifactError("ARTIFACT_INVALID_SPEC", "xlsx requires at least one sheet")
        workbook = Workbook()
        first = True
        for sheet in sheets:
            worksheet = workbook.active if first else workbook.create_sheet()
            first = False
            name = sheet.get("name")
            if name:
                worksheet.title = str(name)[:31]
            columns = sheet.get("columns", []) or []
            if columns:
                worksheet.append([str(column) for column in columns])
            for row in sheet.get("rows", []) or []:
                worksheet.append([self._cell(value) for value in row])

        buffer = io.BytesIO()
        workbook.save(buffer)
        data = buffer.getvalue()
        mime, ext = ARTIFACT_TYPE_INFO[ArtifactType.XLSX]
        return GeneratedArtifact(
            artifact_id=instruction.artifact_id or "",
            filename=instruction.filename,
            type=ArtifactType.XLSX,
            mime_type=mime,
            extension=ext,
            data=data,
            size=len(data),
        )

    @staticmethod
    def _cell(value: object) -> object:
        if value is None:
            return ""
        if isinstance(value, bool):
            return value
        if isinstance(value, (int, float)):
            return value
        return str(value)
