package com.example.stardust_springboot.ai.attachment;

import java.util.Locale;

/**
 * Server-side attachment projection sent to the internal AI service.
 *
 * <p>This is an internal transport model, never an API model: it carries file content and must
 * therefore never be serialized into a browser response. Storage paths and object keys are
 * intentionally absent — the AI service receives content, never a local filesystem location.
 */
public record ChatAttachment(String fileId, String fileName, String mimeType, long sizeBytes,
                             ChatAttachmentKind kind, String text, String imageBase64, String note) {

    public static ChatAttachment text(String fileId, String fileName, String mimeType, long sizeBytes,
                                      String text, String note) {
        return new ChatAttachment(fileId, fileName, mimeType, sizeBytes, ChatAttachmentKind.TEXT,
                text, null, note);
    }

    public static ChatAttachment image(String fileId, String fileName, String mimeType, long sizeBytes,
                                       String imageBase64) {
        return new ChatAttachment(fileId, fileName, mimeType, sizeBytes, ChatAttachmentKind.IMAGE,
                null, imageBase64, null);
    }

    public static ChatAttachment unsupported(String fileId, String fileName, String mimeType,
                                             long sizeBytes, String note) {
        return new ChatAttachment(fileId, fileName, mimeType, sizeBytes, ChatAttachmentKind.UNSUPPORTED,
                null, null, note);
    }

    public String mimeTypeLower() {
        return mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
    }
}
