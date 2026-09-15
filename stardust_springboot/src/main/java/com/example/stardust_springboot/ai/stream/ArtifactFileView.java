package com.example.stardust_springboot.ai.stream;

import java.util.LinkedHashMap;
import java.util.Map;

/** One generated file summarized inside the terminal `done` event's `files` array. */
record ArtifactFileView(String fileId, String fileName, String mimeType, long size, String downloadUrl) {
    Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fileId", fileId);
        map.put("filename", fileName);
        map.put("mimeType", mimeType);
        map.put("size", size);
        map.put("downloadUrl", downloadUrl);
        return map;
    }
}
