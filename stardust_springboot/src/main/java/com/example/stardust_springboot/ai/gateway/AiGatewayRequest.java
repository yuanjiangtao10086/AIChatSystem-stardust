package com.example.stardust_springboot.ai.gateway;

import com.example.stardust_springboot.ai.attachment.ChatAttachment;

import java.util.List;

public record AiGatewayRequest(String requestId, String providerKey, String model,
                               List<AiGatewayMessage> messages, List<ChatAttachment> attachments) {

    public AiGatewayRequest(String requestId, String providerKey, String model,
                            List<AiGatewayMessage> messages) {
        this(requestId, providerKey, model, messages, List.of());
    }
}
