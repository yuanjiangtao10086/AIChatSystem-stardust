package com.example.stardust_springboot.knowledge.gateway;

import java.io.InputStream;

public record RagProcessCommand(
        String userId,
        String knowledgeBaseId,
        String documentId,
        String filename,
        String mimeType,
        InputStream content) {
}
