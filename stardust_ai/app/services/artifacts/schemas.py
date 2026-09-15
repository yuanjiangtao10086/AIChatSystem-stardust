from dataclasses import dataclass

from app.schemas.artifacts import ArtifactInstruction, ArtifactType


@dataclass(slots=True)
class GeneratedArtifact:
    """Bytes + metadata produced by an :class:`ArtifactGenerator`."""

    artifact_id: str
    filename: str
    type: ArtifactType
    mime_type: str
    extension: str
    data: bytes
    size: int
