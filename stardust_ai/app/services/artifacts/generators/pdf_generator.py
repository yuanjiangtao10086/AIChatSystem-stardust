import io
import os

from app.core.errors import ArtifactError
from app.schemas.artifacts import ARTIFACT_TYPE_INFO, ArtifactType
from app.services.artifacts.generators.base import ArtifactGenerator
from app.services.artifacts.schemas import ArtifactInstruction, GeneratedArtifact

# Candidate CJK-capable fonts, probed in order (Windows first). reportlab's default
# Helvetica has no CJK glyphs, so every Chinese character renders as a black square
# unless a CJK font is registered and applied to every paragraph/table style.
_CJK_FONT_CANDIDATES: tuple[str, ...] = (
    r"C:\Windows\Fonts\msyh.ttc",        # Microsoft YaHei
    r"C:\Windows\Fonts\msyh.ttf",
    r"C:\Windows\Fonts\simhei.ttf",      # SimHei
    r"C:\Windows\Fonts\simsun.ttc",      # SimSun
    r"C:\Windows\Fonts\msjh.ttc",        # JhengHei (traditional)
    "/System/Library/Fonts/PingFang.ttc",  # macOS
    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",  # Linux
    "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
)

_CJK_FONT_NAME: str | None = None


def _cjk_font() -> str:
    """Return a registered font name that can render CJK text (cached).

    Prefers an embedded TTF/TTC from the OS so the PDF renders identically in every
    viewer; falls back to reportlab's built-in CID font `STSong-Light`, which needs
    no font file at all.
    """
    global _CJK_FONT_NAME
    if _CJK_FONT_NAME:
        return _CJK_FONT_NAME

    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.cidfonts import UnicodeCIDFont
    from reportlab.pdfbase.ttfonts import TTFont

    for path in _CJK_FONT_CANDIDATES:
        if not os.path.exists(path):
            continue
        try:
            pdfmetrics.registerFont(TTFont("StardustCJK", path, subfontIndex=0))
            _CJK_FONT_NAME = "StardustCJK"
            return _CJK_FONT_NAME
        except Exception:  # noqa: BLE001 - any single unusable font file is not fatal
            continue
    pdfmetrics.registerFont(UnicodeCIDFont("STSong-Light"))
    _CJK_FONT_NAME = "STSong-Light"
    return _CJK_FONT_NAME


class PdfArtifactGenerator:
    """Renders a .pdf from ``content`` (text/markdown) or ``spec`` (docx-like structure).

    reportlab is imported lazily so a missing optional dependency degrades to a clear
    ``artifact_error`` for PDF requests instead of breaking service startup (the same
    defensive posture the docx/pptx/xlsx generators rely on).
    """

    @property
    def supported_types(self) -> tuple[ArtifactType, ...]:
        return (ArtifactType.PDF,)

    def generate(self, instruction: ArtifactInstruction) -> GeneratedArtifact:
        try:
            from reportlab.lib.pagesizes import A4
            from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
            from reportlab.lib.units import cm
            from reportlab.platypus import (
                ListFlowable,
                ListItem,
                Paragraph,
                SimpleDocTemplate,
                Spacer,
            )
        except ImportError as exc:
            raise ArtifactError(
                "ARTIFACT_DEPENDENCY_MISSING",
                "PDF generation requires the reportlab package (pip install reportlab)",
            ) from exc

        mime, ext = ARTIFACT_TYPE_INFO[ArtifactType.PDF]
        font = _cjk_font()
        base = getSampleStyleSheet()
        styles = {
            "title": ParagraphStyle("CJKTitle", parent=base["Title"], fontName=font),
            "h1": ParagraphStyle("CJKHeading1", parent=base["Heading1"], fontName=font),
            "h2": ParagraphStyle("CJKHeading2", parent=base["Heading2"], fontName=font),
            "h3": ParagraphStyle("CJKHeading3", parent=base["Heading3"], fontName=font),
            "body": ParagraphStyle("CJKBody", parent=base["BodyText"], fontName=font),
        }

        def esc(text: object) -> str:
            return str(text).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        buffer = io.BytesIO()
        doc = SimpleDocTemplate(
            buffer,
            pagesize=A4,
            topMargin=2 * cm,
            bottomMargin=2 * cm,
            leftMargin=2 * cm,
            rightMargin=2 * cm,
            title=(instruction.spec or {}).get("title"),
        )

        flow = []
        if instruction.spec:
            spec = instruction.spec
            title = spec.get("title")
            if title:
                flow.append(Paragraph(esc(title), styles["title"]))
            for section in spec.get("sections", []) or []:
                heading = section.get("heading")
                if heading:
                    flow.append(Paragraph(esc(heading), styles["h1"]))
                for paragraph in section.get("paragraphs", []) or []:
                    flow.append(Paragraph(esc(paragraph), styles["body"]))
                bullets = section.get("bullets", []) or []
                if bullets:
                    flow.append(
                        ListFlowable(
                            [ListItem(Paragraph(esc(b), styles["body"])) for b in bullets],
                            bulletType="bullet",
                        )
                    )
                for table in section.get("tables", []) or []:
                    flow.append(self._build_table(table, styles, font))
                flow.append(Spacer(1, 6))
        elif instruction.content:
            flow.extend(self._render_text(instruction.content, styles, esc))
        else:
            raise ArtifactError("ARTIFACT_INVALID_SPEC", "pdf requires `content` or `spec`")

        doc.build(flow)
        data = buffer.getvalue()
        return GeneratedArtifact(
            artifact_id=instruction.artifact_id or "",
            filename=instruction.filename,
            type=ArtifactType.PDF,
            mime_type=mime,
            extension=ext,
            data=data,
            size=len(data),
        )

    @staticmethod
    def _build_table(table: dict, styles: dict, font: str) -> object:
        from reportlab.platypus import Paragraph, Spacer, Table, TableStyle

        rows = table.get("rows", []) or []
        if not rows:
            return Spacer(1, 1)
        header = table.get("header") or rows[0]
        data_rows = rows[1:] if table.get("header") else rows
        cell_data = [[Paragraph(str(c), styles["body"]) for c in header]]
        for row in data_rows:
            cell_data.append([Paragraph(str(c), styles["body"]) for c in row])
        built = Table(cell_data, hAlign="LEFT")
        built.setStyle(
            TableStyle(
                [
                    ("FONTNAME", (0, 0), (-1, -1), font),
                    ("GRID", (0, 0), (-1, -1), 0.5, (0.7, 0.7, 0.7)),
                    ("BACKGROUND", (0, 0), (-1, 0), (0.9, 0.9, 0.9)),
                    ("VALIGN", (0, 0), (-1, -1), "TOP"),
                    ("LEFTPADDING", (0, 0), (-1, -1), 4),
                    ("RIGHTPADDING", (0, 0), (-1, -1), 4),
                ]
            )
        )
        return built

    @staticmethod
    def _render_text(content: str, styles: dict, esc) -> list:
        from reportlab.platypus import ListFlowable, ListItem, Paragraph

        flow = []
        buffer_para: list[str] = []
        for line in content.splitlines():
            stripped = line.strip()
            if not stripped:
                if buffer_para:
                    flow.append(Paragraph(esc(" ".join(buffer_para)), styles["body"]))
                    buffer_para = []
                continue
            if stripped.startswith("### "):
                flow.append(Paragraph(esc(stripped[4:]), styles["h3"]))
            elif stripped.startswith("## "):
                flow.append(Paragraph(esc(stripped[3:]), styles["h2"]))
            elif stripped.startswith("# "):
                flow.append(Paragraph(esc(stripped[2:]), styles["h1"]))
            elif stripped.startswith(("- ", "* ")):
                flow.append(
                    ListFlowable(
                        [ListItem(Paragraph(esc(stripped[2:]), styles["body"]))],
                        bulletType="bullet",
                    )
                )
            else:
                buffer_para.append(stripped)
        if buffer_para:
            flow.append(Paragraph(esc(" ".join(buffer_para)), styles["body"]))
        return flow
