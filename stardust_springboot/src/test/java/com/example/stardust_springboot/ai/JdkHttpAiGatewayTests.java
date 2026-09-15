package com.example.stardust_springboot.ai;

import com.example.stardust_springboot.ai.gateway.AiGatewayEvent;
import com.example.stardust_springboot.ai.gateway.AiGatewayMessage;
import com.example.stardust_springboot.ai.gateway.AiGatewayRequest;
import com.example.stardust_springboot.ai.gateway.JdkHttpAiGateway;
import com.example.stardust_springboot.ai.gateway.StreamCancellation;
import com.example.stardust_springboot.config.AiServiceProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdkHttpAiGatewayTests {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesAndValidatesPythonSseContractWithoutBufferingProviderBody() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/chat/stream", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Service-Authorization"))
                    .isEqualTo("test-service-token-that-is-at-least-32-chars");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("\"aiRequestId\":\"ai-request-1234\"", "\"model\":\"model-x\"");
            byte[] response = ("""
                    event: start
                    data: {"schemaVersion":"1","type":"start","aiRequestId":"ai-request-1234","requestId":"ai-request-1234","seq":0,"timestamp":"2026-09-08T00:00:00Z","payload":{}}

                    event: delta
                    data: {"schemaVersion":"1","type":"delta","aiRequestId":"ai-request-1234","requestId":"ai-request-1234","seq":1,"timestamp":"2026-09-08T00:00:00Z","payload":{"content":"hello"}}

                    event: done
                    data: {"schemaVersion":"1","type":"done","aiRequestId":"ai-request-1234","requestId":"ai-request-1234","seq":2,"timestamp":"2026-09-08T00:00:00Z","payload":{"finishReason":"stop"}}

                    """).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        AiServiceProperties properties = new AiServiceProperties(baseUrl,
                "test-service-token-that-is-at-least-32-chars", Duration.ofSeconds(1),
                Duration.ofSeconds(2));
        JdkHttpAiGateway gateway = new JdkHttpAiGateway(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(),
                JsonMapper.builder().build(), properties);
        List<AiGatewayEvent> events = new ArrayList<>();

        gateway.stream(new AiGatewayRequest("ai-request-1234", "openai-compatible", "model-x",
                        List.of(new AiGatewayMessage("user", "hello"))),
                new StreamCancellation(), events::add);

        assertThat(events).extracting(AiGatewayEvent::type).containsExactly("start", "delta", "done");
        assertThat(events.get(1).payload()).containsEntry("content", "hello");
    }
}
