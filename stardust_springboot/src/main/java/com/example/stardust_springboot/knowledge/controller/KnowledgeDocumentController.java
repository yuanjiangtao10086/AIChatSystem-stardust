package com.example.stardust_springboot.knowledge.controller;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.ApiResult;
import com.example.stardust_springboot.common.api.BatchDeleteRequest;
import com.example.stardust_springboot.common.api.BatchDeleteResult;
import com.example.stardust_springboot.knowledge.dto.KnowledgeDocumentView;
import com.example.stardust_springboot.knowledge.service.KnowledgeDocumentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge-documents")
public class KnowledgeDocumentController {
    private final KnowledgeDocumentService documents;

    public KnowledgeDocumentController(KnowledgeDocumentService documents) {
        this.documents = documents;
    }

    @GetMapping("/{documentId}")
    public ApiResult<KnowledgeDocumentView> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable String documentId) {
        return ApiResult.success(documents.get(principal, documentId));
    }

    @PostMapping("/{documentId}/retry")
    public ApiResult<KnowledgeDocumentView> retry(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable String documentId) {
        return ApiResult.success(documents.retry(principal, documentId));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable String documentId) {
        documents.delete(principal, documentId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/batch")
    public ApiResult<BatchDeleteResult> deleteBatch(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @Valid @RequestBody BatchDeleteRequest request) {
        return ApiResult.success(documents.batchDelete(principal, request.ids()));
    }
}
