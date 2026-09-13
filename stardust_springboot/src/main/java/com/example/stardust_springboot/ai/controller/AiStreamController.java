package com.example.stardust_springboot.ai.controller;

import com.example.stardust_springboot.ai.stream.AiStreamingService;
import com.example.stardust_springboot.ai.stream.StopStreamResponse;
import com.example.stardust_springboot.ai.stream.StreamChatRequest;
import com.example.stardust_springboot.ai.stream.RegenerateMessageRequest;
import com.example.stardust_springboot.ai.stream.EditAndResendMessageRequest;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.ApiResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping("/api/v1")
public class AiStreamController {
    private static final String SAFE_ID = "^[A-Za-z0-9_-]{8,64}$";
    private final AiStreamingService streamingService;

    public AiStreamController(AiStreamingService streamingService) {
        this.streamingService = streamingService;
    }

    @PostMapping(path = "/conversations/{conversationId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String conversationId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = SAFE_ID) String idempotencyKey,
            @Valid @RequestBody StreamChatRequest request) {
        return streamingResponse(streamingService.start(principal, conversationId, idempotencyKey, request));
    }

    @PostMapping(path = "/messages/{messageId}/regenerate",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> regenerate(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String messageId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = SAFE_ID) String idempotencyKey,
            @Valid @RequestBody RegenerateMessageRequest request) {
        return streamingResponse(streamingService.regenerate(principal, messageId, idempotencyKey, request));
    }

    @PostMapping(path = "/messages/{messageId}/edit-and-resend",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> editAndResend(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String messageId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = SAFE_ID) String idempotencyKey,
            @Valid @RequestBody EditAndResendMessageRequest request) {
        return streamingResponse(streamingService.editAndResend(principal, messageId, idempotencyKey, request));
    }

    /**
     * Wraps the streaming emitter so the browser-facing response carries the headers that stop any
     * reverse proxy (nginx, corporate proxy, dev-server proxy) from buffering the event stream. Without
     * {@code X-Accel-Buffering: no} and {@code Cache-Control: no-cache}, a proxy may hold every SSE
     * chunk until the connection closes and then deliver the whole reply at once, which looks like the
     * assistant answered in one shot.
     */
    private static ResponseEntity<SseEmitter> streamingResponse(SseEmitter emitter) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @PostMapping("/ai/requests/{requestId}/stop")
    public ResponseEntity<ApiResult<StopStreamResponse>> stop(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable @Pattern(regexp = SAFE_ID) String requestId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResult.success(streamingService.stop(principal, requestId)));
    }
}
