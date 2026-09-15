import io

from pptx import Presentation
from pptx.util import Inches, Pt

from app.core.errors import ArtifactError
from app.schemas.artifacts import ARTIFACT_TYPE_INFO, ArtifactType
from app.services.artifacts.generators.base import ArtifactGenerator
from app.services.artifacts.schemas import ArtifactInstruction, GeneratedArtifact


class PptxArtifactGenerator:
    """Renders a .pptx from ``spec = {title, slides:[{title,bullets,notes}]}``.

    python-pptx produces macro-free presentations. Slides use the default title+content
    layout; notes land in the speaker-notes pane.
    """

    @property
    def supported_types(self) -> tuple[ArtifactType, ...]:
        return (ArtifactType.PPTX,)

    def generate(self, instruction: ArtifactInstruction) -> GeneratedArtifact:
        if instruction.spec is None:
            raise ArtifactError("ARTIFACT_INVALID_SPEC", "pptx requires `spec`")
        spec = instruction.spec
        presentation = Presentation()
        title = spec.get("title")
        for index, slide in enumerate(spec.get("slides", []) or []):
            layout = presentation.slide_layouts[0] if index == 0 and title else presentation.slide_layouts[1]
            slide_obj = presentation.slides.add_slide(layout)
            slide_title = slide.get("title") or (title if index == 0 else None)
            if slide_title:
                slide_obj.shapes.title.text = str(slide_title)
            body = slide_obj.placeholders[1]
            text_frame = body.text_frame
            text_frame.word_wrap = True
            bullets = slide.get("bullets", []) or []
            for bullet_index, bullet in enumerate(bullets):
                paragraph = text_frame.paragraphs[0] if bullet_index == 0 else text_frame.add_paragraph()
                paragraph.text = str(bullet)
                paragraph.level = 0
            notes = slide.get("notes")
            if notes:
                slide_obj.notes_slide.notes_text_frame.text = str(notes)

        buffer = io.BytesIO()
        presentation.save(buffer)
        data = buffer.getvalue()
        mime, ext = ARTIFACT_TYPE_INFO[ArtifactType.PPTX]
        return GeneratedArtifact(
            artifact_id=instruction.artifact_id or "",
            filename=instruction.filename,
            type=ArtifactType.PPTX,
            mime_type=mime,
            extension=ext,
            data=data,
            size=len(data),
        )
