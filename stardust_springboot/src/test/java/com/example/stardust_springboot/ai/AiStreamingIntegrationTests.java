package com.example.stardust_springboot.ai;

import com.example.stardust_springboot.ai.entity.AiModel;
import com.example.stardust_springboot.ai.entity.AiProvider;
import com.example.stardust_springboot.ai.entity.ModelType;
import com.example.stardust_springboot.ai.entity.ProviderType;
import com.example.stardust_springboot.ai.gateway.AiGateway;
import com.example.stardust_springboot.ai.gateway.AiGatewayEvent;
import com.example.stardust_springboot.ai.gateway.AiGatewayException;
import com.example.stardust_springboot.ai.gateway.AiGatewayRequest;
import com.example.stardust_springboot.ai.gateway.StreamCancellation;
import com.example.stardust_springboot.ai.repository.AiModelRepository;
import com.example.stardust_springboot.ai.repository.AiProviderRepository;
import com.example.stardust_springboot.ai.request.AiRequestLog;
import com.example.stardust_springboot.ai.request.AiRequestLogRepository;
import com.example.stardust_springboot.ai.request.AiRequestStatus;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.ai.service.request-timeout=PT1S")
@AutoConfigureMockMvc
@Import(AiStreamingIntegrationTests.TestGatewayConfiguration.class)
class AiStreamingIntegrationTests {
    private static final String PASSWORD = "StrongPassword!123";
    private static final Pattern REQUEST_ID = Pattern.compile("\\\"requestId\\\":\\\"([^\\\"]+)\\\"");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AiProviderRepository providerRepository;
    @Autowired
    private AiModelRepository modelRepository;
    @Autowired
    private AiRequestLogRepository requestLogRepository;
    @Autowired
    private FakeAiGateway gateway;

    private String modelId;

    @BeforeEach
    void setUpModel() {
        gateway.reset();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        AiProvider provider = new AiProvider("test-provider-" + suffix, "Test Provider",
                ProviderType.OPENAI_COMPATIBLE, "https://provider.test/v1", null);
        provider.enable();
        providerRepository.saveAndFlush(provider);
        AiModel model = new AiModel(provider, "test-model-" + suffix, "external-test-model",
                "Test Chat Model", ModelType.CHAT);
        model.enable();
        modelId = modelRepository.saveAndFlush(model).getPublicId();
    }

    @Test
    void completeStreamPersistsContentUsageAndSendsDoneAfterward() throws Exception {
        gateway.behavior = Behavior.SUCCESS;

        String token = register("stream-owner");
        String conversationId = createConversation(token);
        MvcResult result = startStream(token, conversationId, "complete-request-123");
        String body = dispatch(result);

        assertThat(body).contains("event:start", "event:reasoning", "event:delta", "event:usage", "event:done");
        assertThat(body.indexOf("event:done")).isGreaterThan(body.indexOf("event:usage"));
        String requestId = requestId(body);

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].role").value("USER"))
                .andExpect(jsonPath("$.data.items[1].content").value("你好"))
                .andExpect(jsonPath("$.data.items[1].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.items[1].totalTokens").value(9));

        AiRequestLog log = requestLogRepository.findByRequestId(requestId).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(AiRequestStatus.COMPLETED);
        assertThat(log.getTotalTokens()).isEqualTo(9L);
        assertThat(log.getLatencyMs()).isNotNegative();
    }

    @Test
    void providerFailureKeepsPartialResponseAndNeverLeavesStreamingStatus() throws Exception {
        gateway.behavior = Behavior.FAIL;

        String token = register("failed-owner");
        String conversationId = createConversation(token);
        String body = dispatch(startStream(token, conversationId, "failed-request-123"));
        assertThat(body).contains("event:error", "PROVIDER_RATE_LIMITED", "partial");
        String requestId = requestId(body);

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[1].content").value("partial"))
                .andExpect(jsonPath("$.data.items[1].status").value("FAILED"));
        assertThat(requestLogRepository.findByRequestId(requestId).orElseThrow().getErrorCode())
                .isEqualTo("PROVIDER_RATE_LIMITED");
    }

    @Test
    void timeoutAndProvider500MapToFailedTerminalEvents() throws Exception {
        String token = register("gateway-errors");
        String conversationId = createConversation(token);
        gateway.behavior = Behavior.TIMEOUT;
        String timeout = dispatch(startStream(token, conversationId, "timeout-request-123"));
        assertThat(timeout).contains("event:error", "AI_TIMEOUT");
        assertThat(requestLogRepository.findByRequestId(requestId(timeout)).orElseThrow().getStatus())
                .isEqualTo(AiRequestStatus.FAILED);

        gateway.behavior = Behavior.SERVER_ERROR;
        String unavailable = dispatch(startStream(token, conversationId, "server-error-request-123"));
        assertThat(unavailable).contains("event:error", "PROVIDER_UNAVAILABLE");
        assertThat(requestLogRepository.findByRequestId(requestId(unavailable)).orElseThrow().getStatus())
                .isEqualTo(AiRequestStatus.FAILED);

        gateway.behavior = Behavior.UNEXPECTED;
        String unexpected = dispatch(startStream(token, conversationId, "unexpected-request-123"));
        assertThat(unexpected).contains("event:error", "INTERNAL_ERROR");
        assertThat(requestLogRepository.findByRequestId(requestId(unexpected)).orElseThrow().getStatus())
                .isEqualTo(AiRequestStatus.FAILED);
    }

    @Test
    void springWatchdogCancelsHungStreamAndPersistsTimeout() throws Exception {
        gateway.behavior = Behavior.WATCHDOG;
        String token = register("watchdog-owner");
        String conversationId = createConversation(token);
        String body = dispatch(startStream(token, conversationId, "watchdog-request-123"));
        assertThat(body).contains("event:error", "AI_TIMEOUT");
        AiRequestLog log = requestLogRepository.findByRequestId(requestId(body)).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(log.getErrorCode()).isEqualTo("AI_TIMEOUT");
    }

    @Test
    void stopIsOwnerScopedAndPersistsStoppedState() throws Exception {
        gateway.behavior = Behavior.BLOCK;

        String ownerToken = register("stop-owner");
        String attackerToken = register("stop-attacker");
        String conversationId = createConversation(ownerToken);
        MvcResult stream = startStream(ownerToken, conversationId, "stop-request-12345");
        assertThat(gateway.started.await(2, TimeUnit.SECONDS)).isTrue();

        mockMvc.perform(post("/api/v1/ai/requests/{id}:stop", gateway.requestId.get())
                        .header("Authorization", bearer(attackerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/ai/requests/{id}:stop", gateway.requestId.get())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("STOPPED"));
        dispatch(stream);

        mockMvc.perform(post("/api/v1/ai/requests/{id}:stop", gateway.requestId.get())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("STOPPED"));

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[1].content").value("kept"))
                .andExpect(jsonPath("$.data.items[1].status").value("STOPPED"));
    }

    @Test
    void ownershipAndModelValidationHappenBeforeProviderCall() throws Exception {
        String ownerToken = register("model-owner");
        String attackerToken = register("model-attacker");
        String conversationId = createConversation(ownerToken);

        mockMvc.perform(post("/api/v1/conversations/{id}/messages:stream", conversationId)
                        .header("Authorization", bearer(attackerToken))
                        .header("Idempotency-Key", "ownership-request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(streamBody(modelId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/conversations/{id}/messages:stream", conversationId)
                        .header("Authorization", bearer(ownerToken))
                        .header("Idempotency-Key", "invalid-model-request-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(streamBody("00000000000000000000000000")))
                .andExpect(status().isNotFound());
        assertThat(gateway.invocations.get()).isZero();
    }

    @Test
    void regenerateAndEditCreateOwnedMessageVariantsAndBranchContext() throws Exception {
        gateway.behavior = Behavior.SUCCESS;
        String ownerToken = register("branch-owner");
        String attackerToken = register("branch-attacker");
        String conversationId = createConversation(ownerToken);
        dispatch(startStream(ownerToken, conversationId, "branch-first-request"));

        MvcResult firstHistory = mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andReturn();
        String firstBody = firstHistory.getResponse().getContentAsString();
        String userMessageId = JsonPath.read(firstBody, "$.data.items[0].id");
        String assistantMessageId = JsonPath.read(firstBody, "$.data.items[1].id");
        int callsAfterFirst = gateway.invocations.get();

        mockMvc.perform(post("/api/v1/messages/{id}:regenerate", assistantMessageId)
                        .header("Authorization", bearer(attackerToken))
                        .header("Idempotency-Key", "branch-attacker-regen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"%s\"}".formatted(modelId)))
                .andExpect(status().isNotFound());
        assertThat(gateway.invocations.get()).isEqualTo(callsAfterFirst);

        String regenerate = dispatch(mockMvc.perform(post("/api/v1/messages/{id}:regenerate", assistantMessageId)
                        .header("Authorization", bearer(ownerToken))
                        .header("Idempotency-Key", "branch-owner-regen")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"modelId\":\"%s\"}".formatted(modelId)))
                .andExpect(request().asyncStarted()).andReturn());
        assertThat(regenerate).contains("\"operation\":\"REGENERATE\"");

        MvcResult regeneratedHistory = mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[2].parentMessageId").value(userMessageId))
                .andExpect(jsonPath("$.data.items[2].supersedesMessageId").value(assistantMessageId))
                .andExpect(jsonPath("$.data.items[2].variantNo").value(1))
                .andReturn();
        String regeneratedAssistantId = JsonPath.read(
                regeneratedHistory.getResponse().getContentAsString(), "$.data.items[2].id");

        String edited = dispatch(mockMvc.perform(post("/api/v1/messages/{id}:edit-and-resend", userMessageId)
                        .header("Authorization", bearer(ownerToken))
                        .header("Idempotency-Key", "branch-owner-edit")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"edited question","contentType":"PLAIN_TEXT","modelId":"%s"}
                                """.formatted(modelId)))
                .andExpect(request().asyncStarted()).andReturn());
        assertThat(edited).contains("\"operation\":\"EDIT_AND_RESEND\"");
        assertThat(gateway.lastRequest.get().messages()).hasSize(2);
        assertThat(gateway.lastRequest.get().messages().get(0).role()).isEqualTo("system");
        assertThat(gateway.lastRequest.get().messages().get(1).content()).isEqualTo("edited question");

        MvcResult editedHistory = mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[1].role").value("USER"))
                .andExpect(jsonPath("$.data.items[1].supersedesMessageId").value(userMessageId))
                .andExpect(jsonPath("$.data.items[1].variantNo").value(1))
                .andExpect(jsonPath("$.data.items[4].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data.items[4].supersedesMessageId").value(regeneratedAssistantId))
                .andExpect(jsonPath("$.data.items[4].variantNo").value(2))
                .andReturn();
        String editedHistoryBody = editedHistory.getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(editedHistoryBody, "$.data.items[4].parentMessageId"))
                .isEqualTo(JsonPath.read(editedHistoryBody, "$.data.items[1].id"));
    }

    @Test
    void chatAttachmentsReferenceOwnedUserFilesAndRemainSingleStoredObjects() throws Exception {
        String owner = register("attachment-owner");
        String attacker = register("attachment-attacker");
        String ownerConversation = createConversation(owner);
        String attackerConversation = createConversation(attacker);
        MvcResult upload = mockMvc.perform(multipart("/api/v1/files")
                        .file(new MockMultipartFile("file", "context.txt", "text/plain", "facts".getBytes()))
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isCreated()).andReturn();
        String fileId = JsonPath.read(upload.getResponse().getContentAsString(), "$.data.id");

        MvcResult stream = mockMvc.perform(post("/api/v1/conversations/{id}/messages:stream", ownerConversation)
                        .header("Authorization", bearer(owner))
                        .header("Idempotency-Key", "attachment-request-123")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"use this file","contentType":"PLAIN_TEXT","modelId":"%s",
                                 "attachmentIds":["%s"]}
                                """.formatted(modelId, fileId)))
                .andExpect(request().asyncStarted()).andReturn();
        dispatch(stream);
        mockMvc.perform(get("/api/v1/conversations/{id}/messages", ownerConversation)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].attachments[0].id").value(fileId))
                .andExpect(jsonPath("$.data.items[0].attachments[0].name").value("context.txt"));

        mockMvc.perform(post("/api/v1/conversations/{id}/messages:stream", attackerConversation)
                        .header("Authorization", bearer(attacker))
                        .header("Idempotency-Key", "foreign-attachment-123")
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"steal","modelId":"%s","attachmentIds":["%s"]}
                                """.formatted(modelId, fileId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/files/{id}", fileId).header("Authorization", bearer(owner)))
                .andExpect(status().isConflict());
    }

    private MvcResult startStream(String token, String conversationId, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/v1/conversations/{id}/messages:stream", conversationId)
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", idempotencyKey)
                        .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(streamBody(modelId)))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    private String dispatch(MvcResult result) throws Exception {
        result.getAsyncResult(5000);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return result.getResponse().getContentAsString();
    }

    private String requestId(String body) {
        Matcher matcher = REQUEST_ID.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String streamBody(String requestedModelId) {
        return """
                {"content":"hello","contentType":"PLAIN_TEXT","modelId":"%s"}
                """.formatted(requestedModelId);
    }

    private String register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","displayName":"AI tester"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String createConversation(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/conversations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Streaming test\"}"))
                .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    enum Behavior {
        SUCCESS,
        FAIL,
        TIMEOUT,
        SERVER_ERROR,
        UNEXPECTED,
        WATCHDOG,
        BLOCK
    }

    static final class FakeAiGateway implements AiGateway {
        volatile Behavior behavior = Behavior.SUCCESS;
        final AtomicInteger invocations = new AtomicInteger();
        final AtomicReference<String> requestId = new AtomicReference<>();
        final AtomicReference<AiGatewayRequest> lastRequest = new AtomicReference<>();
        volatile CountDownLatch started = new CountDownLatch(1);

        void reset() {
            behavior = Behavior.SUCCESS;
            invocations.set(0);
            requestId.set(null);
            lastRequest.set(null);
            started = new CountDownLatch(1);
        }

        @Override
        public void stream(AiGatewayRequest request, StreamCancellation cancellation,
                           Consumer<AiGatewayEvent> consumer) {
            invocations.incrementAndGet();
            requestId.set(request.requestId());
            lastRequest.set(request);
            if (behavior == Behavior.FAIL) {
                consumer.accept(new AiGatewayEvent("delta", Map.of("content", "partial")));
                throw new AiGatewayException("PROVIDER_RATE_LIMITED", "rate limited", true);
            }
            if (behavior == Behavior.TIMEOUT) {
                throw new AiGatewayException("AI_TIMEOUT", "timed out", true);
            }
            if (behavior == Behavior.SERVER_ERROR) {
                throw new AiGatewayException("PROVIDER_UNAVAILABLE", "provider failed", true);
            }
            if (behavior == Behavior.UNEXPECTED) {
                throw new IllegalStateException("simulated Spring orchestration failure");
            }
            if (behavior == Behavior.BLOCK || behavior == Behavior.WATCHDOG) {
                consumer.accept(new AiGatewayEvent("delta", Map.of("content", "kept")));
                started.countDown();
                while (!cancellation.isCancelled()) {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new AiGatewayException("AI_CANCELLED", "cancelled", false);
                    }
                }
                throw new AiGatewayException("AI_CANCELLED", "cancelled", false);
            }
            consumer.accept(new AiGatewayEvent("start", Map.of()));
            consumer.accept(new AiGatewayEvent("reasoning", Map.of("content", "思考")));
            consumer.accept(new AiGatewayEvent("delta", Map.of("content", "你")));
            consumer.accept(new AiGatewayEvent("delta", Map.of("content", "好")));
            consumer.accept(new AiGatewayEvent("usage", Map.of(
                    "promptTokens", 7, "completionTokens", 2, "totalTokens", 9)));
            consumer.accept(new AiGatewayEvent("done", Map.of("finishReason", "stop")));
        }
    }

    @TestConfiguration
    static class TestGatewayConfiguration {
        @Bean
        @Primary
        FakeAiGateway fakeAiGateway() {
            return new FakeAiGateway();
        }
    }
}
