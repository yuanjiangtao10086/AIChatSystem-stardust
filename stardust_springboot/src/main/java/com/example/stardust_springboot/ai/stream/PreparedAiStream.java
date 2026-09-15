package com.example.stardust_springboot.ai.stream;

import com.example.stardust_springboot.ai.gateway.AiGatewayMessage;
import com.example.stardust_springboot.ai.gateway.AiGatewayRequest;

import java.util.List;

public record PreparedAiStream(
        String requestId,
        Long userId,
        Long conversationDatabaseId,
        Long assistantMessageDatabaseId,
        Long requestLogDatabaseId,
        String conversationId,
        String userMessageId,
        String assistantMessageId,
        String modelId,
        String providerKey,
        String externalModelId,
        String operation,
        List<AiGatewayMessage> messages,
        long attachmentBytes
) {
}
