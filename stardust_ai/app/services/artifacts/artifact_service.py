import hashlib
import logging

from app.core.errors import ArtifactError
from app.core.settings import Settings
from app.schemas.artifacts import ArtifactType
from app.services.artifacts.generators.base import ArtifactGenerator
from app.services.artifacts.generators.docx_generator import DocxArtifactGenerator
from app.services.artifacts.generators.pdf_generator import PdfArtifactGenerator
from app.services.artifacts.generators.pptx_generator import PptxArtifactGenerator
from app.services.artifacts.generators.text_generator import TextArtifactGenerator
from app.services.artifacts.generators.xlsx_generator import XlsxArtifactGenerator
from app.services.artifacts.schemas import ArtifactInstruction, GeneratedArtifact

logger = logging.getLogger(__name__)


class ArtifactService:
    """Turns a validated :class:`ArtifactInstruction` into file bytes.

    A registry maps each :class:`ArtifactType` to its generator. The service also enforces
    the per-file size cap (defence in depth: the Spring side validates again) and never
    writes to disk itself — generation is in-memory and the bytes travel back to Spring
    over the stream, so there is no temp-file path-traversal surface.
    """

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._generators = self._build_registry()

    def _build_registry(self) -> dict[ArtifactType, ArtifactGenerator]:
        registry: dict[ArtifactType, ArtifactGenerator] = {}
        for generator in (
            TextArtifactGenerator(),
            DocxArtifactGenerator(),
            PptxArtifactGenerator(),
            XlsxArtifactGenerator(),
            PdfArtifactGenerator(),
        ):
            for artifact_type in generator.supported_types:
                registry[artifact_type] = generator
        return registry

    def generate(self, instruction: ArtifactInstruction) -> GeneratedArtifact:
        generator = self._generators.get(instruction.type)
        if generator is None:
            raise ArtifactError(
                "ARTIFACT_TYPE_NOT_ALLOWED",
                f"unsupported artifact type: {instruction.type}",
            )
        # Generators may raise ArtifactError (bad spec); let it propagate so the caller
        # can emit an artifact_error instead of failing the whole chat.
        artifact = generator.generate(instruction)
        if artifact.size > self._settings.artifact_max_bytes:
            raise ArtifactError(
                "ARTIFACT_TOO_LARGE",
                f"artifact {artifact.filename} exceeds {self._settings.artifact_max_bytes} bytes",
                413,
            )
        return artifact

    @staticmethod
    def sha256(data: bytes) -> str:
        return hashlib.sha256(data).hexdigest()
