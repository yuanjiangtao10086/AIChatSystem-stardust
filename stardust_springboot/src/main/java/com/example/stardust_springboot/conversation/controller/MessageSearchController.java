package com.example.stardust_springboot.conversation.controller;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.conversation.dto.MessageSearchHitView;
import com.example.stardust_springboot.conversation.service.MessageSearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cross-conversation message search for the signed-in user. Mirrors the admin conversation search but can
 * never cross an ownership boundary: every query is scoped to the authenticated user id.
 */
@Validated
@RestController
@RequestMapping("/api/v1/messages")
public class MessageSearchController {

    private final MessageSearchService messageSearchService;

    public MessageSearchController(MessageSearchService messageSearchService) {
        this.messageSearchService = messageSearchService;
    }

    @GetMapping("/search")
    public ApiResult<PageResult<MessageSearchHitView>> search(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam("q") @Size(max = 200) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ApiResult.success(messageSearchService.searchAll(principal, keyword, page, size));
    }
}
