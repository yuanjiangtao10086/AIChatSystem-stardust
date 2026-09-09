package com.example.stardust_springboot.file.dto;

import com.example.stardust_springboot.file.entity.UserFile;
import com.example.stardust_springboot.file.entity.UserFileStatus;

import java.time.Instant;

public record FileView(
        String id,
        String name,
        String mimeType,
        String detectedMimeType,
        String extension,
        long size,
        String sha256,
        String storageProvider,
        UserFileStatus status,
        boolean previewable,
        String downloadUrl,
        String previewUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static FileView from(UserFile file) {
        boolean previewable = file.getDetectedMime().startsWith("image/");
        String base = "/api/v1/files/" + file.getPublicId();
        return new FileView(file.getPublicId(), file.getOriginalName(), file.getDeclaredMime(),
                file.getDetectedMime(), file.getExtension(), file.getSizeBytes(), file.getSha256(),
                file.getStorageProvider(), file.getStatus(), previewable, base + "/download",
                previewable ? base + "/preview" : null, file.getCreatedAt(), file.getUpdatedAt());
    }
}
