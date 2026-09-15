from typing import Protocol, runtime_checkable

from app.schemas.artifacts import ArtifactType
from app.services.artifacts.schemas import ArtifactInstruction, GeneratedArtifact


@runtime_checkable
class ArtifactGenerator(Protocol):
    """Renders one :class:`ArtifactInstruction` into file bytes.

    Implementations are registered by the artifact types they support; the
    :class:`ArtifactService` dispatches on ``instruction.type``. Keeping each format in
    its own class avoids a god-object and keeps the OpenAI/text paths independent of the
    (heavier) office libraries.
    """

    @property
    def supported_types(self) -> tuple[ArtifactType, ...]:
        ...

    def generate(self, instruction: ArtifactInstruction) -> GeneratedArtifact:
        ...
