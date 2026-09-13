package com.example.stardust_springboot.ai.attachment;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Extracts plain text from a PDF attachment so Spring can deliver it to the AI service as a TEXT part.
 *
 * <p>A PDF has no text layer when it is a scanned image, so extraction can legitimately return
 * nothing; the resolver then reports the file as UNSUPPORTED instead of inventing content. Both the
 * page count and the extracted length are bounded so a malformed or decompressing-bomb PDF cannot
 * exhaust the streaming worker's memory.
 */
@Component
public class PdfTextExtractor {
    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);
    private static final int MAX_PAGES = 5000;
    private static final int HARD_MAX_CHARS = 400_000;

    /**
     * @return extracted text, or {@code null} when the PDF cannot be parsed, has no text layer, or
     *         exceeds a safety limit. The caller decides how to report a {@code null} result.
     */
    public String extract(byte[] content, String requestId, int maxChars) {
        if (content == null || content.length == 0) {
            return null;
        }
        try (PDDocument document = Loader.loadPDF(content)) {
            int pages = document.getNumberOfPages();
            if (pages > MAX_PAGES) {
                log.warn("PDF attachment skipped: too many pages requestId={} pages={}", requestId, pages);
                return null;
            }
            int limit = Math.min(maxChars, HARD_MAX_CHARS);
            BoundedStripper stripper = new BoundedStripper(limit);
            String text = stripper.getText(document);
            if (text == null || text.isBlank()) {
                return null;
            }
            return text;
        } catch (IOException error) {
            log.warn("PDF attachment parse failed requestId={}", requestId, error);
            return null;
        }
    }

    /**
     * Collects extracted characters into a bounded buffer. Once the budget is reached, later text
     * fragments are dropped so the worker never materialises an unbounded string.
     */
    private static final class BoundedStripper extends PDFTextStripper {
        private final int maxChars;
        private final StringBuilder builder = new StringBuilder();
        private boolean full;

        private BoundedStripper(int maxChars) throws IOException {
            super();
            this.maxChars = maxChars;
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            if (full || text == null || text.isEmpty()) {
                return;
            }
            if (builder.length() + text.length() >= maxChars) {
                int remaining = Math.max(0, maxChars - builder.length());
                builder.append(text, 0, remaining);
                full = true;
                return;
            }
            builder.append(text);
        }

        @Override
        public String getText(PDDocument document) throws IOException {
            super.getText(document);
            return builder.toString();
        }
    }
}
