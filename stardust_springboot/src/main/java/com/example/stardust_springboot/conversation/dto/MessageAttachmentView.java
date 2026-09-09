package com.example.stardust_springboot.conversation.dto;

import com.example.stardust_springboot.conversation.entity.ChatMessageAttachment;

public record MessageAttachmentView(
        String id,
        String name,
        String mimeType,
        long size,
        boolean previewable,
        String downloadUrl,
        String previewUrl
) {
    public static MessageAttachmentView from(ChatMessageAttachment attachment) {
        var file = attachment.getUserFile();
        boolean previewable = file.getDetectedMime().startsWith("image/");
        String base = "/api/v1/files/" + file.getPublicId();
        return new MessageAttachmentView(file.getPublicId(), file.getOriginalName(),
                file.getDetectedMime(), file.getSizeBytes(), previewable, base + "/download",
                previewable ? base + "/preview" : null);
    }
}
