package com.example.stardust_springboot.knowledge.controller;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.knowledge.dto.*;
import com.example.stardust_springboot.knowledge.service.KnowledgeBaseService;
import com.example.stardust_springboot.knowledge.service.KnowledgeDocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController {
    private final KnowledgeBaseService bases;
    private final KnowledgeDocumentService documents;

    public KnowledgeBaseController(KnowledgeBaseService bases, KnowledgeDocumentService documents) {
        this.bases = bases;
        this.documents = documents;
    }

    @GetMapping
    public ApiResult<PageResult<KnowledgeBaseView>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 200) String search) {
        return ApiResult.success(bases.list(principal, page, size, search));
    }

    @PostMapping
    public ResponseEntity<ApiResult<KnowledgeBaseView>> create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success(bases.create(principal, request)));
    }

    @GetMapping("/{baseId}")
    public ApiResult<KnowledgeBaseView> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                            @PathVariable String baseId) {
        return ApiResult.success(bases.get(principal, baseId));
    }

    @PatchMapping("/{baseId}")
    public ApiResult<KnowledgeBaseView> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                               @PathVariable String baseId,
                                               @Valid @RequestBody UpdateKnowledgeBaseRequest request) {
        return ApiResult.success(bases.update(principal, baseId, request));
    }

    @DeleteMapping("/{baseId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable String baseId) {
        bases.delete(principal, baseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{baseId}/documents")
    public ApiResult<PageResult<KnowledgeDocumentView>> documents(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String baseId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResult.success(bases.listDocuments(principal, baseId, page, size));
    }

    @PostMapping("/{baseId}/documents")
    public ResponseEntity<ApiResult<KnowledgeDocumentView>> addDocument(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable String baseId,
            @Valid @RequestBody AddKnowledgeDocumentRequest request) {
        KnowledgeDocumentView uploaded = bases.addDocument(principal, baseId, request.fileId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.success(documents.process(principal, uploaded.id())));
    }
}
