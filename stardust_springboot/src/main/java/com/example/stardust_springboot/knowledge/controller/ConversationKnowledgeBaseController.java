package com.example.stardust_springboot.knowledge.controller;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.knowledge.dto.ConversationKnowledgeBasesView;
import com.example.stardust_springboot.knowledge.dto.SetConversationKnowledgeBasesRequest;
import com.example.stardust_springboot.knowledge.service.ConversationKnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/knowledge-bases")
public class ConversationKnowledgeBaseController {
    private final ConversationKnowledgeBaseService service;

    public ConversationKnowledgeBaseController(ConversationKnowledgeBaseService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<ConversationKnowledgeBasesView> get(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String conversationId) {
        return ApiResult.success(service.get(principal, conversationId));
    }

    @PutMapping
    public ApiResult<ConversationKnowledgeBasesView> replace(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String conversationId,
            @Valid @RequestBody SetConversationKnowledgeBasesRequest request) {
        return ApiResult.success(service.replace(principal, conversationId, request));
    }
}
