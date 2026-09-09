package com.example.stardust_springboot.ai.gateway;

import java.util.Map;

public record AiGatewayEvent(String type, Map<String, Object> payload) {
}
