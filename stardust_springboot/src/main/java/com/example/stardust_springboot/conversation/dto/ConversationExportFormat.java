package com.example.stardust_springboot.conversation.dto;

/**
 * Supported conversation export formats. {@code MARKDOWN} is meant for humans (notes, hand-off documents),
 * {@code JSON} is meant for re-import and for tooling.
 */
public enum ConversationExportFormat {
    MARKDOWN("text/markdown;charset=UTF-8", "md"),
    JSON("application/json;charset=UTF-8", "json");

    private final String contentType;
    private final String extension;

    ConversationExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
