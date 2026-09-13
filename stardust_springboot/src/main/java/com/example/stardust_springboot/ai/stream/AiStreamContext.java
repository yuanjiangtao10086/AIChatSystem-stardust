package com.example.stardust_springboot.ai.stream;

import com.example.stardust_springboot.ai.attachment.ChatAttachment;
import com.example.stardust_springboot.ai.gateway.AiGatewayRequest;

import java.util.List;

/**
 * Prompt context for one AI request: the ordered chat messages plus the resolved attachments of the
 * user message being answered.
 */
public record AiStreamContext(List<AiGatewayRequest.AiGatewayMessage> messages,
                              List<ChatAttachment> attachments) {

    public AiStreamContext(List<AiGatewayRequest.AiGatewayMessage> messages) {
        this(messages, List.of());
    }
}
