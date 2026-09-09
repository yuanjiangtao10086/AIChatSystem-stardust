package com.example.stardust_springboot.knowledge.gateway;

import com.example.stardust_springboot.config.AiServiceProperties;
import com.example.stardust_springboot.config.RagProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JdkHttpRagGateway implements RagGateway {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final AiServiceProperties service;
    private final RagProperties rag;

    public JdkHttpRagGateway(HttpClient client, ObjectMapper mapper,
                             AiServiceProperties service, RagProperties rag) {
        this.client = client;
        this.mapper = mapper;
        this.service = service;
        this.rag = rag;
    }

    @Override
    public RagProcessResult process(RagProcessCommand command) {
        requireConfigured();
        HttpRequest request = base(service.baseUrl().resolve("/internal/rag/documents/process"))
                .header("Content-Type", "application/octet-stream")
                .header("X-User-Id", command.userId())
                .header("X-Knowledge-Base-Id", command.knowledgeBaseId())
                .header("X-Document-Id", command.documentId())
                .header("X-Document-Name", command.filename())
                .header("X-Document-Mime", command.mimeType())
                .header("X-Provider-Key", rag.providerKey())
                .header("X-Embedding-Model", rag.embeddingModel())
                .POST(HttpRequest.BodyPublishers.ofInputStream(command::content))
                .build();
        return send(request, RagProcessResult.class);
    }

    @Override
    public RagRetrieveResult retrieve(String userPublicId, List<String> knowledgeBaseIds,
                                      String query) {
        requireConfigured();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userPublicId);
        body.put("knowledgeBaseIds", knowledgeBaseIds);
        body.put("query", query);
        body.put("topK", rag.retrievalTopK());
        body.put("tokenBudget", rag.retrievalTokenBudget());
        body.put("providerKey", rag.providerKey());
        body.put("model", rag.embeddingModel());
        HttpRequest request = base(service.baseUrl().resolve("/internal/rag/retrieve"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(write(body)))
                .build();
        return send(request, RagRetrieveResult.class);
    }

    @Override
    public void deleteDocument(String userPublicId, String knowledgeBaseId, String documentId) {
        requireConfigured();
        byte[] body = write(Map.of("userId", userPublicId, "knowledgeBaseId", knowledgeBaseId));
        HttpRequest request = base(service.baseUrl().resolve("/internal/rag/documents/" + documentId))
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        send(request, Void.class);
    }

    private HttpRequest.Builder base(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(service.requestTimeout())
                .header("Accept", "application/json")
                .header("X-Service-Authorization", service.token());
    }

    private void requireConfigured() {
        if (service.token() == null || service.token().length() < 32 || rag.embeddingModel().isBlank()) {
            throw new RagGatewayException("RAG_NOT_CONFIGURED", "RAG service is not configured");
        }
    }

    private byte[] write(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (RuntimeException error) {
            throw new RagGatewayException("RAG_PROTOCOL_ERROR", "could not encode RAG request", error);
        }
    }

    private <T> T send(HttpRequest request, Class<T> type) {
        try {
            HttpResponse<byte[]> response = client.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String code = "RAG_SERVICE_ERROR";
                try {
                    JsonNode error = mapper.readTree(response.body());
                    if (error.hasNonNull("code")) code = error.get("code").asText();
                } catch (Exception ignored) {
                    // Upstream bodies are never exposed.
                }
                throw new RagGatewayException(code, "RAG service rejected the request");
            }
            return type == Void.class ? null : mapper.readValue(response.body(), type);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new RagGatewayException("RAG_INTERRUPTED", "RAG request interrupted", error);
        } catch (IOException error) {
            throw new RagGatewayException("RAG_SERVICE_UNAVAILABLE", "RAG service unavailable", error);
        }
    }
}
