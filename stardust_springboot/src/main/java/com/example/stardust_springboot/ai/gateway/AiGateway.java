package com.example.stardust_springboot.ai.gateway;

import java.util.function.Consumer;

public interface AiGateway {
    void stream(AiGatewayRequest request, StreamCancellation cancellation,
                Consumer<AiGatewayEvent> consumer);
}
