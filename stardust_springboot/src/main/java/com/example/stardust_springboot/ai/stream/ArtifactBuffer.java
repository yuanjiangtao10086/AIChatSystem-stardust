package com.example.stardust_springboot.ai.stream;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

/** Accumulates the base64 streamed body of one AI artifact during the SSE flow. */
class ArtifactBuffer {
    private final String artifactId;
    private final String filename;
    private final String mimeType;
    private final String type;
    private final ByteArrayOutputStream content = new ByteArrayOutputStream();

    ArtifactBuffer(String artifactId, String filename, String mimeType, String type) {
        this.artifactId = artifactId;
        this.filename = filename;
        this.mimeType = mimeType;
        this.type = type;
    }

    void appendBase64(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        content.writeBytes(Base64.getDecoder().decode(chunk));
    }

    byte[] bytes() {
        return content.toByteArray();
    }

    String filename() {
        return filename;
    }

    String mimeType() {
        return mimeType;
    }
}
