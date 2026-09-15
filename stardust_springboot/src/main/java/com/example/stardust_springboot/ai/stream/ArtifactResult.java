package com.example.stardust_springboot.ai.stream;

/** View of a persisted artifact returned to the streaming layer for the public SSE envelope. */
record ArtifactResult(String fileId, String fileName, String mimeType, long size, String downloadUrl) {
}
