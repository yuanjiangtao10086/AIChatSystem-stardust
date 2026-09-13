package com.example.stardust_springboot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Limits and type policy for turning chat attachments into AI input.
 *
 * <p>Every limit exists so a chat request can never stream an unbounded amount of file bytes into
 * the JVM heap or into the internal AI request body.
 */
@ConfigurationProperties("app.ai.attachment")
public record AttachmentProperties(long maxFileBytes, long maxTotalBytes, int maxTextChars,
                                   Set<String> textMimeTypes, Set<String> imageMimeTypes,
                                   Set<String> pdfMimeTypes, String imageDetail) {

    private static final long DEFAULT_MAX_FILE_BYTES = 5L * 1024 * 1024;
    private static final long DEFAULT_MAX_TOTAL_BYTES = 20L * 1024 * 1024;
    private static final int DEFAULT_MAX_TEXT_CHARS = 60_000;
    private static final Set<String> DEFAULT_TEXT_MIMES = Set.of(
            "text/plain", "text/markdown", "text/csv", "application/json", "text/json");
    private static final Set<String> DEFAULT_IMAGE_MIMES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp");
    private static final Set<String> DEFAULT_PDF_MIMES = Set.of("application/pdf");

    public AttachmentProperties {
        // Every unset limit falls back to the built-in default instead of failing startup: a missing
        // knob must degrade the attachment feature, never the whole application.
        maxFileBytes = maxFileBytes > 0 ? maxFileBytes : DEFAULT_MAX_FILE_BYTES;
        maxTotalBytes = maxTotalBytes > 0 ? maxTotalBytes : DEFAULT_MAX_TOTAL_BYTES;
        maxTextChars = maxTextChars > 0 ? maxTextChars : DEFAULT_MAX_TEXT_CHARS;
        textMimeTypes = normalize(textMimeTypes, DEFAULT_TEXT_MIMES);
        imageMimeTypes = normalize(imageMimeTypes, DEFAULT_IMAGE_MIMES);
        pdfMimeTypes = normalize(pdfMimeTypes, DEFAULT_PDF_MIMES);
        imageDetail = imageDetail == null || imageDetail.isBlank() ? "auto" : imageDetail.trim();
    }

    private static Set<String> normalize(Set<String> values, Set<String> fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
