package com.example.stardust_springboot.ai.stream;

import com.example.stardust_springboot.conversation.entity.MessageContentFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StreamChatRequest(
        @NotBlank @Size(max = 32000) String content,
        MessageContentFormat contentType,
        @Size(max = 26) String parentMessageId,
        @NotBlank @Size(max = 26) String modelId,
        @Size(max = 10) List<@Size(min = 26, max = 26) String> attachmentIds
) {
}
