package com.example.stardust_springboot.ai.gateway;

import com.example.stardust_springboot.config.AiServiceProperties;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Component
public class JdkHttpAiGateway implements AiGateway {
    private static final Set<String> EVENTS = Set.of(
            "start", "delta", "reasoning", "usage", "done", "error",
            "citation", "tool_start", "tool_delta", "tool_done");
    private static final int MAX_EVENT_CHARS = 1_000_000;

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final AiServiceProperties properties;

    public JdkHttpAiGateway(HttpClient client, ObjectMapper objectMapper, AiServiceProperties properties) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void stream(AiGatewayRequest request, StreamCancellation cancellation,
                       Consumer<AiGatewayEvent> consumer) {
        requireConfigured();
        HttpRequest httpRequest;
        try {
            Map<String, Object> body = Map.of(
                    "schemaVersion", "1",
                    "aiRequestId", request.requestId(),
                    "providerKey", request.providerKey(),
                    "model", request.model(),
                    "messages", request.messages());
            httpRequest = HttpRequest.newBuilder(properties.streamUri())
                    .timeout(properties.requestTimeout())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("X-Service-Authorization", properties.token())
                    .header("X-Request-Id", request.requestId())
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (Exception error) {
            throw new AiGatewayException("AI_REQUEST_INVALID", "AI request could not be encoded", false);
        }

        try {
            HttpResponse<InputStream> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw statusError(response.statusCode());
            }
            consume(response.body(), request.requestId(), cancellation, consumer);
        } catch (HttpTimeoutException error) {
            throw new AiGatewayException("AI_TIMEOUT", "AI service timed out", true);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            if (cancellation.isCancelled()) {
                throw new AiGatewayException("AI_CANCELLED", "AI request was cancelled", false);
            }
            throw new AiGatewayException("AI_SERVICE_UNAVAILABLE", "AI service call was interrupted", true);
        } catch (IOException error) {
            if (cancellation.isCancelled()) {
                throw new AiGatewayException("AI_CANCELLED", "AI request was cancelled", false);
            }
            throw new AiGatewayException("AI_SERVICE_UNAVAILABLE", "AI service is unavailable", true);
        }
    }

    private void consume(InputStream input, String requestId, StreamCancellation cancellation,
                         Consumer<AiGatewayEvent> consumer) throws IOException {
        cancellation.onCancel(() -> closeQuietly(input));
        boolean terminal = false;
        long expectedSequence = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String event = null;
            StringBuilder data = new StringBuilder();
            String line;
            while (!cancellation.isCancelled() && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (event != null && !data.isEmpty()) {
                        terminal = dispatch(event, data.toString(), requestId,
                                expectedSequence, consumer) || terminal;
                        expectedSequence++;
                        if (terminal) {
                            return;
                        }
                    }
                    event = null;
                    data.setLength(0);
                } else if (line.startsWith("event:")) {
                    event = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (data.length() > MAX_EVENT_CHARS) {
                        throw protocolError();
                    }
                    if (!data.isEmpty()) {
                        data.append('\n');
                    }
                    data.append(line.substring(5).trim());
                }
            }
        }
        if (cancellation.isCancelled()) {
            throw new AiGatewayException("AI_CANCELLED", "AI request was cancelled", false);
        }
        if (!terminal) {
            throw protocolError();
        }
    }

    private boolean dispatch(String event, String data, String requestId, long expectedSequence,
                             Consumer<AiGatewayEvent> consumer) {
        if (!EVENTS.contains(event)) {
            throw protocolError();
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            if (!requestId.equals(root.path("aiRequestId").asText())
                    || !event.equals(root.path("type").asText())
                    || root.path("seq").asLong(-1) != expectedSequence) {
                throw protocolError();
            }
            Map<String, Object> payload = objectMapper.convertValue(
                    root.path("payload"), new TypeReference<>() { });
            if ("error".equals(event)) {
                throw new AiGatewayException(
                        String.valueOf(payload.getOrDefault("code", "PROVIDER_ERROR")),
                        "AI provider request failed",
                        Boolean.TRUE.equals(payload.get("retryable")));
            }
            consumer.accept(new AiGatewayEvent(event, payload));
            return "done".equals(event);
        } catch (AiGatewayException error) {
            throw error;
        } catch (Exception error) {
            throw protocolError();
        }
    }

    private void requireConfigured() {
        if (properties.token() == null || properties.token().length() < 32) {
            throw new AiGatewayException("AI_SERVICE_NOT_CONFIGURED", "AI service is not configured", false);
        }
    }

    private AiGatewayException statusError(int status) {
        if (status == 429) {
            return new AiGatewayException("PROVIDER_RATE_LIMITED", "AI provider rate limited the request", true);
        }
        if (status >= 500) {
            return new AiGatewayException("AI_SERVICE_UNAVAILABLE", "AI service is unavailable", true);
        }
        return new AiGatewayException("AI_SERVICE_REJECTED", "AI service rejected the request", false);
    }

    private AiGatewayException protocolError() {
        return new AiGatewayException("AI_PROTOCOL_ERROR", "AI service returned an invalid stream", false);
    }

    private void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // Cancellation is best-effort; the stream worker owns final status persistence.
        }
    }
}
