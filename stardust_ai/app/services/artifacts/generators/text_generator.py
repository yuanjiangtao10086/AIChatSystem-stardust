from app.core.errors import ArtifactError
from app.schemas.artifacts import ARTIFACT_TYPE_INFO, TEXT_TYPES, ArtifactType
from app.services.artifacts.generators.base import ArtifactGenerator
from app.services.artifacts.schemas import ArtifactInstruction, GeneratedArtifact


class TextArtifactGenerator:
    """Renders text-like artifacts (py/md/txt/csv/json/html/sql/yaml/xml) from ``content``.

    No third-party dependency: the content is encoded as UTF-8. The MIME/extension come
    from the shared :data:`ARTIFACT_TYPE_INFO` table so the emitted file matches what the
    Spring whitelist expects.
    """

    @property
    def supported_types(self) -> tuple[ArtifactType, ...]:
        return tuple(TEXT_TYPES)

    def generate(self, instruction: ArtifactInstruction) -> GeneratedArtifact:
        if instruction.type not in TEXT_TYPES:
            raise ArtifactError(
                "ARTIFACT_TYPE_NOT_ALLOWED",
                f"text generator cannot render {instruction.type}",
            )
        if instruction.content is None:
            raise ArtifactError(
                "ARTIFACT_INVALID_SPEC",
                f"text artifact {instruction.type} requires `content`",
            )
        mime, ext = ARTIFACT_TYPE_INFO[instruction.type]
        data = instruction.content.encode("utf-8")
        return GeneratedArtifact(
            artifact_id=instruction.artifact_id or "",
            filename=instruction.filename,
            type=instruction.type,
            mime_type=mime,
            extension=ext,
            data=data,
            size=len(data),
        )
