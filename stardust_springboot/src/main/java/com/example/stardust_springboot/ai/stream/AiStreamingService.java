package com.example.stardust_springboot.ai.stream;

import com.example.stardust_springboot.ai.gateway.AiGateway;
import com.example.stardust_springboot.ai.gateway.AiGatewayEvent;
import com.example.stardust_springboot.ai.gateway.AiGatewayException;
import com.example.stardust_springboot.ai.gateway.AiGatewayRequest;
import com.example.stardust_springboot.ai.gateway.StreamCancellation;
import com.example.stardust_springboot.ai.request.AiRequestStatus;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.conversation.entity.FinishReason;
import com.example.stardust_springboot.conversation.memory.ConversationSummaryService;
import com.example.stardust_springboot.memory.service.MemoryService;
import com.example.stardust_springboot.config.AiServiceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Service
public class AiStreamingService {
    private static final Logger log = LoggerFactory.getLogger(AiStreamingService.class);

    private final AiStreamPersistenceService persistence;
    private final AiGateway gateway;
    private final ActiveStreamRegistry registry;
    private final ExecutorService executor;
    private final AiServiceProperties properties;
    private final ConversationSummaryService summaryService;
    private final MemoryService memoryService;
    private final MessageArtifactService artifactService;

    public AiStreamingService(AiStreamPersistenceService persistence, AiGateway gateway,
                              ActiveStreamRegistry registry, ExecutorService aiStreamExecutor,
                              AiServiceProperties properties,
                              ConversationSummaryService summaryService,
                              MemoryService memoryService, MessageArtifactService artifactService) {
        this.persistence = persistence;
        this.gateway = gateway;
        this.registry = registry;
        this.executor = aiStreamExecutor;
        this.properties = properties;
        this.summaryService = summaryService;
        this.memoryService = memoryService;
        this.artifactService = artifactService;
    }

    public SseEmitter start(AuthenticatedUser principal, String conversationId,
                            String idempotencyKey, StreamChatRequest request) {
        PreparedAiStream prepared = persistence.prepare(principal, conversationId, idempotencyKey, request);
        return launch(principal, prepared);
    }

    public SseEmitter regenerate(AuthenticatedUser principal, String messageId,
                                 String idempotencyKey, RegenerateMessageRequest request) {
        return launch(principal, persistence.prepareRegenerate(principal, messageId, idempotencyKey, request));
    }

    public SseEmitter editAndResend(AuthenticatedUser principal, String messageId,
                                    String idempotencyKey, EditAndResendMessageRequest request) {
        return launch(principal,
                persistence.prepareEditAndResend(principal, messageId, idempotencyKey, request));
    }

    private SseEmitter launch(AuthenticatedUser principal, PreparedAiStream prepared) {
        SseEmitter emitter = new SseEmitter(0L);
        StreamCancellation cancellation = new StreamCancellation();
        registry.register(prepared.requestId(), principal.id(), cancellation);
        emitter.onTimeout(cancellation::cancel);
        emitter.onError(error -> cancellation.cancel());
        emitter.onCompletion(cancellation::cancel);
        Future<?> watchdog = executor.submit(() -> runWatchdog(cancellation));
        executor.submit(() -> run(prepared, emitter, cancellation, watchdog));
        return emitter;
    }

    public StopStreamResponse stop(AuthenticatedUser principal, String requestId) {
        ActiveStreamRegistry.CancelResult result = registry.cancelOwned(requestId, principal.id());
        if (result == ActiveStreamRegistry.CancelResult.NOT_OWNED) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (result == ActiveStreamRegistry.CancelResult.NOT_ACTIVE) {
            AiRequestStatus status = persistence.requireRequestStatus(requestId, principal.id());
            return new StopStreamResponse(requestId, status.name());
        }
        AiRequestStatus status = persistence.requireRequestStatus(requestId, principal.id());
        return new StopStreamResponse(requestId, status.name());
    }

    private void run(PreparedAiStream stream, SseEmitter emitter, StreamCancellation cancellation,
                     Future<?> watchdog) {
        long startNanos = System.nanoTime();
        long[] firstDeltaNanos = {-1};
        long[] pythonStartNanos = {-1};
        StringBuilder content = new StringBuilder();
        MutableUsage usage = new MutableUsage();
        MutableFinish finish = new MutableFinish();
        Map<String, ArtifactBuffer> artifacts = new LinkedHashMap<>();
        List<ArtifactFileView> generatedFiles = new ArrayList<>();
        try {
            persistence.markStreaming(stream);
            log.info("AI stream started requestId={} userId={} conversationId={} provider={} model={}",
                    stream.requestId(), stream.userId(), stream.conversationId(),
                    stream.providerKey(), stream.externalModelId());
            // Deliver start before context building so the client enters the "generating" state
            // immediately instead of staring at a blank screen while Memory/RAG retrieval runs.
            send(emitter, "start", baseEvent(stream, "start", Map.of(
                    "userMessageId", stream.userMessageId(),
                    "modelId", stream.modelId(),
                    "operation", stream.operation())), cancellation);

            // Runs on the streaming worker, after the SSE response is already open. The conversation
            // FOR UPDATE lock from prepare() is long released; remote RAG embedding latency no longer
            // blocks the HTTP response or other conversations.
            AiStreamContext context = persistence.buildContext(stream);
            long contextMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
            log.info("AI stream context built requestId={} contextMs={} messageCount={} attachmentCount={}",
                    stream.requestId(), contextMs, context.messages().size(), context.attachments().size());

            pythonStartNanos[0] = System.nanoTime();
            AiGatewayRequest gatewayRequest = new AiGatewayRequest(stream.requestId(),
                    stream.providerKey(), stream.externalModelId(), context.messages(),
                    context.attachments());
            gateway.stream(gatewayRequest, cancellation,
                    event -> {
                        cancellation.markActivity();
                        handleGatewayEvent(stream, emitter, cancellation, content, usage, finish,
                                firstDeltaNanos, pythonStartNanos, startNanos, artifacts, generatedFiles, event);
                    });

            if (cancellation.isTimedOut()) {
                throw new AiGatewayException("AI_TIMEOUT", "AI request timed out", true);
            } else if (cancellation.isCancelled()) {
                persistence.stop(stream, content.toString());
                log.info("AI stream stopped requestId={} status=STOPPED", stream.requestId());
                sendIfConnected(emitter, "done", baseEvent(stream, "done",
                        Map.of("status", "STOPPED", "finishReason", "USER_CANCELLED")), cancellation);
            } else {
                persistence.complete(stream, content.toString(), finish.reason(), usage.snapshot());
                refreshSummarySafely(stream);
                extractMemorySafely(stream);
                log.info("AI stream completed requestId={} status=COMPLETED totalTokens={} artifacts={}",
                        stream.requestId(), usage.snapshot().totalTokens(), generatedFiles.size());
                Map<String, Object> donePayload = new LinkedHashMap<>();
                donePayload.put("status", "COMPLETED");
                donePayload.put("finishReason", finish.reason().name());
                if (!generatedFiles.isEmpty()) {
                    List<Map<String, Object>> files = new ArrayList<>();
                    for (ArtifactFileView file : generatedFiles) {
                        files.add(file.asMap());
                    }
                    donePayload.put("files", files);
                }
                send(emitter, "done", baseEvent(stream, "done", donePayload), cancellation);
            }
        } catch (AiGatewayException error) {
            if (cancellation.isTimedOut()) {
                safeFail(stream, content.toString(), "AI_TIMEOUT");
                log.warn("AI stream timed out requestId={} status=FAILED", stream.requestId());
                sendTerminal(emitter, "error", baseEvent(stream, "error", Map.of(
                        "code", "AI_TIMEOUT", "message", "AI generation timed out",
                        "retryable", true, "partial", !content.isEmpty())));
            } else if (cancellation.isCancelled() || "AI_CANCELLED".equals(error.code())) {
                safeStop(stream, content.toString());
                log.info("AI stream cancelled requestId={} status=STOPPED", stream.requestId());
                sendIfConnected(emitter, "done", baseEvent(stream, "done",
                        Map.of("status", "STOPPED", "finishReason", "USER_CANCELLED")), cancellation);
            } else {
                safeFail(stream, content.toString(), error.code());
                log.warn("AI stream failed requestId={} status=FAILED errorCode={} retryable={}",
                        stream.requestId(), error.code(), error.retryable());
                sendIfConnected(emitter, "error", baseEvent(stream, "error", Map.of(
                        "code", error.code(),
                        "message", "AI generation failed",
                        "retryable", error.retryable(),
                        "partial", !content.isEmpty())), cancellation);
            }
        } catch (Exception error) {
            log.error("Unexpected AI stream failure requestId={}", stream.requestId(), error);
            safeFail(stream, content.toString(), "INTERNAL_ERROR");
            sendIfConnected(emitter, "error", baseEvent(stream, "error", Map.of(
                    "code", "INTERNAL_ERROR", "message", "AI generation failed",
                    "retryable", false, "partial", !content.isEmpty())), cancellation);
        } finally {
            watchdog.cancel(true);
            registry.remove(stream.requestId());
            emitter.complete();
        }
    }

    private void runWatchdog(StreamCancellation cancellation) {
        try {
            long timeoutMillis = properties.requestTimeout().toMillis();
            // Poll in short slices so that any upstream activity resets the clock. A stream is
            // killed only when no event has been seen for the whole requestTimeout window, i.e. it
            // is genuinely stalled — not merely slow (a reasoning model can think for minutes).
            long stepMillis = Math.max(250, Math.min(1_000L, timeoutMillis));
            while (!cancellation.isCancelled()) {
                Thread.sleep(stepMillis);
                if (cancellation.idleMillis() >= timeoutMillis) {
                    log.warn("AI stream idle exceeded requestTimeout idleMs={}", timeoutMillis);
                    cancellation.timeout();
                    break;
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleGatewayEvent(PreparedAiStream stream, SseEmitter emitter,
                                    StreamCancellation cancellation, StringBuilder content,
                                    MutableUsage usage, MutableFinish finish, long[] firstDeltaNanos,
                                    long[] pythonStartNanos, long startNanos,
                                    Map<String, ArtifactBuffer> artifacts,
                                    List<ArtifactFileView> generatedFiles, AiGatewayEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("[stream] spring->vue requestId={} type={}",
                    stream.requestId(), event.type());
        }
        switch (event.type()) {
            case "start" -> { }
            case "delta" -> {
                String delta = text(event.payload(), "content");
                if (!delta.isEmpty() && firstDeltaNanos[0] < 0) {
                    firstDeltaNanos[0] = System.nanoTime();
                    long springPreprocessMs = Duration.ofNanos(pythonStartNanos[0] - startNanos).toMillis();
                    long pythonTtftMs = Duration.ofNanos(firstDeltaNanos[0] - pythonStartNanos[0]).toMillis();
                    long totalTtftMs = Duration.ofNanos(firstDeltaNanos[0] - startNanos).toMillis();
                    log.info("AI stream first token requestId={} springPreprocessMs={} pythonTtftMs={} totalTtftMs={}",
                            stream.requestId(), springPreprocessMs, pythonTtftMs, totalTtftMs);
                }
                content.append(delta);
                send(emitter, "delta", baseEvent(stream, "delta", Map.of("content", delta)), cancellation);
            }
            case "reasoning" -> send(emitter, "reasoning", baseEvent(stream, "reasoning",
                    Map.of("content", text(event.payload(), "content"))), cancellation);
            case "usage" -> {
                usage.update(event.payload());
                send(emitter, "usage", baseEvent(stream, "usage", usage.asMap()), cancellation);
            }
            case "done" -> finish.update(event.payload());
            case "error" -> send(emitter, "error", baseEvent(stream, "error", event.payload()), cancellation);
            case "citation", "tool_start", "tool_delta", "tool_done" ->
                    send(emitter, event.type(), baseEvent(stream, event.type(), event.payload()), cancellation);
            case "artifact_start" -> {
                String artifactId = stringOrNull(event.payload(), "artifactId");
                if (artifactId == null) break;
                artifacts.put(artifactId, new ArtifactBuffer(
                        artifactId,
                        stringOrNull(event.payload(), "filename"),
                        stringOrNull(event.payload(), "mimeType"),
                        stringOrNull(event.payload(), "artifactType")));
                send(emitter, "artifact_start", baseEvent(stream, "artifact_start", event.payload()), cancellation);
            }
            case "artifact_delta" -> {
                ArtifactBuffer buffer = artifacts.get(stringOrNull(event.payload(), "artifactId"));
                if (buffer != null) {
                    try {
                        buffer.appendBase64(stringOrNull(event.payload(), "content"));
                    } catch (RuntimeException ignored) {
                        // A malformed base64 chunk is dropped; the artifact resolves on done or errors there.
                    }
                }
                send(emitter, "artifact_delta", baseEvent(stream, "artifact_delta", event.payload()), cancellation);
            }
            case "artifact_done" -> {
                String artifactId = stringOrNull(event.payload(), "artifactId");
                ArtifactBuffer buffer = artifactId == null ? null : artifacts.remove(artifactId);
                if (buffer == null) {
                    send(emitter, "artifact_done", baseEvent(stream, "artifact_done", event.payload()), cancellation);
                    break;
                }
                try {
                    ArtifactResult result = artifactService.persist(stream, stream.userId(), buffer);
                    Map<String, Object> done = new LinkedHashMap<>(event.payload());
                    done.put("fileId", result.fileId());
                    done.put("downloadUrl", result.downloadUrl());
                    send(emitter, "artifact_done", baseEvent(stream, "artifact_done", done), cancellation);
                    generatedFiles.add(new ArtifactFileView(result.fileId(), result.fileName(),
                            result.mimeType(), result.size(), result.downloadUrl()));
                } catch (Exception error) {
                    log.warn("Artifact persist failed requestId={} artifactId={}", stream.requestId(), artifactId, error);
                    Map<String, Object> err = new LinkedHashMap<>(event.payload());
                    err.put("code", "ARTIFACT_GENERATION_FAILED");
                    err.put("message", "failed to store generated artifact");
                    send(emitter, "artifact_error", baseEvent(stream, "artifact_error", err), cancellation);
                }
            }
            case "artifact_error" -> {
                artifacts.remove(stringOrNull(event.payload(), "artifactId"));
                send(emitter, "artifact_error", baseEvent(stream, "artifact_error", event.payload()), cancellation);
            }
            default -> throw new AiGatewayException("AI_PROTOCOL_ERROR", "Unexpected stream event", false);
        }
    }

    private Map<String, Object> baseEvent(PreparedAiStream stream, String type,
                                          Map<String, ?> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("requestId", stream.requestId());
        event.put("conversationId", stream.conversationId());
        event.put("messageId", stream.assistantMessageId());
        event.putAll(payload);
        return event;
    }

    private void send(SseEmitter emitter, String event, Map<String, Object> data,
                      StreamCancellation cancellation) {
        if (cancellation.isCancelled()) {
            throw new AiGatewayException("AI_CANCELLED", "AI request was cancelled", false);
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException error) {
            cancellation.cancel();
            throw new AiGatewayException("AI_CANCELLED", "AI client disconnected", false);
        }
    }

    private void sendIfConnected(SseEmitter emitter, String event, Map<String, Object> data,
                                 StreamCancellation cancellation) {
        if (!cancellation.isCancelled()) {
            send(emitter, event, data, cancellation);
        }
    }

    private void sendTerminal(SseEmitter emitter, String event, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException ignored) {
            // Persistence is already terminal; a disconnected client cannot receive this event.
        }
    }

    private String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (!(value instanceof String text)) {
            throw new AiGatewayException("AI_PROTOCOL_ERROR", "Invalid AI stream payload", false);
        }
        return text;
    }

    private static String stringOrNull(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof String text ? text : null;
    }

    private void safeStop(PreparedAiStream stream, String content) {
        try {
            persistence.stop(stream, content);
        } catch (Exception error) {
            log.error("Failed to persist stopped AI stream requestId={}", stream.requestId(), error);
        }
    }

    private void safeFail(PreparedAiStream stream, String content, String code) {
        try {
            persistence.fail(stream, content, code);
        } catch (Exception error) {
            log.error("Failed to persist failed AI stream requestId={}", stream.requestId(), error);
        }
    }

    private void refreshSummarySafely(PreparedAiStream stream) {
        try {
            summaryService.refresh(stream);
        } catch (Exception error) {
            // Summary is an optimization. A refresh failure must not turn a completed answer into a failure.
            log.warn("Conversation summary refresh failed requestId={} conversationId={}",
                    stream.requestId(), stream.conversationId(), error);
        }
    }

    private void extractMemorySafely(PreparedAiStream stream) {
        try {
            memoryService.extractFromCompletedStream(stream);
        } catch (Exception error) {
            // Long-term memory is optional enrichment and must never change the chat terminal state.
            log.warn("Long-term memory extraction failed requestId={} conversationId={}",
                    stream.requestId(), stream.conversationId(), error);
        }
    }

    private static final class MutableUsage {
        private Long prompt;
        private Long completion;
        private Long total;

        void update(Map<String, Object> payload) {
            prompt = number(payload.get("promptTokens"));
            completion = number(payload.get("completionTokens"));
            total = number(payload.get("totalTokens"));
        }

        AiStreamPersistenceService.TokenTotals snapshot() {
            return new AiStreamPersistenceService.TokenTotals(prompt, completion, total);
        }

        Map<String, Object> asMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("promptTokens", prompt);
            values.put("completionTokens", completion);
            values.put("totalTokens", total);
            return values;
        }

        private Long number(Object value) {
            return value instanceof Number number ? number.longValue() : null;
        }
    }

    private static final class MutableFinish {
        private FinishReason reason = FinishReason.STOP;

        void update(Map<String, Object> payload) {
            String raw = String.valueOf(payload.getOrDefault("finishReason", "stop"));
            reason = switch (raw.toLowerCase()) {
                case "length" -> FinishReason.LENGTH;
                case "content_filter" -> FinishReason.CONTENT_FILTER;
                case "tool_call", "tool_calls" -> FinishReason.TOOL_CALL;
                default -> FinishReason.STOP;
            };
        }

        FinishReason reason() {
            return reason;
        }
    }
}
