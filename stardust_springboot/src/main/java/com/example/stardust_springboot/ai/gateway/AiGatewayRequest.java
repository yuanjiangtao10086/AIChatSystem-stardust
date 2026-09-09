package com.example.stardust_springboot.ai.gateway;

import java.util.List;

public record AiGatewayRequest(String requestId, String providerKey, String model,
                               List<AiGatewayMessage> messages) {
    public record AiGatewayMessage(String role, String content) {
    }
}
