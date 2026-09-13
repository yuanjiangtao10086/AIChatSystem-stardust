package com.example.stardust_springboot.conversation.controller;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.conversation.dto.ConversationView;
import com.example.stardust_springboot.conversation.dto.CreateConversationRequest;
import com.example.stardust_springboot.conversation.dto.MessageSearchHitView;
import com.example.stardust_springboot.conversation.dto.MessageView;
import com.example.stardust_springboot.conversation.dto.UpdateConversationRequest;
import com.example.stardust_springboot.conversation.dto.ConversationExportFormat;
import com.example.stardust_springboot.conversation.entity.ConversationStatus;
import com.example.stardust_springboot.conversation.service.ConversationExportService;
import com.example.stardust_springboot.conversation.service.ConversationService;
import com.example.stardust_springboot.conversation.service.ConversationSort;
import com.example.stardust_springboot.conversation.service.ConversationTitleService;
import com.example.stardust_springboot.conversation.service.MessageSearchService;
import com.example.stardust_springboot.conversation.service.MessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@Validated
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final ConversationTitleService conversationTitleService;
    private final MessageSearchService messageSearchService;
    private final ConversationExportService conversationExportService;

    public ConversationController(ConversationService conversationService, MessageService messageService,
                                  ConversationTitleService conversationTitleService,
                                  MessageSearchService messageSearchService,
                                  ConversationExportService conversationExportService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.conversationTitleService = conversationTitleService;
        this.messageSearchService = messageSearchService;
        this.conversationExportService = conversationExportService;
    }

    @PostMapping
    public ResponseEntity<ApiResult<ConversationView>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success(conversationService.create(principal, request)));
    }

    @GetMapping
    public ApiResult<PageResult<ConversationView>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 200) String search,
            @RequestParam(required = false) ConversationStatus status,
            @RequestParam(defaultValue = "LAST_MESSAGE_AT") ConversationSort sort,
            @RequestParam(defaultValue = "DESC") Sort.Direction direction) {
        return ApiResult.success(conversationService.list(
                principal, page, size, search, status, sort, direction));
    }

    @GetMapping("/{conversationId}")
    public ApiResult<ConversationView> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @PathVariable String conversationId) {
        return ApiResult.success(conversationService.get(principal, conversationId));
    }

    @PatchMapping("/{conversationId}")
    public ApiResult<ConversationView> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable String conversationId,
                                              @Valid @RequestBody UpdateConversationRequest request) {
        return ApiResult.success(conversationService.update(principal, conversationId, request));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable String conversationId) {
        conversationService.delete(principal, conversationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/title")
    public ApiResult<ConversationView> generateTitle(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @PathVariable String conversationId) {
        return ApiResult.success(conversationTitleService.generate(principal, conversationId));
    }

    /**
     * Prunes the current user's empty conversations (no messages sent yet). The conversation identified by
     * {@code keep} is preserved so a freshly created chat the user is still looking at is never removed.
     */
    @DeleteMapping("/empty")
    public ApiResult<Integer> deleteEmpty(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @RequestParam(required = false) String keep) {
        return ApiResult.success(conversationService.deleteEmptyConversations(principal, keep));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResult<PageResult<MessageView>> messages(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int size) {
        return ApiResult.success(messageService.list(principal, conversationId, page, size));
    }

    @GetMapping("/{conversationId}/messages/search")
    public ApiResult<PageResult<MessageSearchHitView>> searchMessages(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String conversationId,
            @RequestParam("q") @Size(max = 200) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResult.success(
                messageSearchService.searchInConversation(principal, conversationId, keyword, page, size));
    }

    @GetMapping("/{conversationId}/export")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "MARKDOWN") ConversationExportFormat format) {
        ConversationExportService.ConversationExport export =
                conversationExportService.export(principal, conversationId, format);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(export.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(export.contentType()))
                .body(export.body().getBytes(StandardCharsets.UTF_8));
    }

}
