package com.example.stardust_springboot.admin.controller;

import com.example.stardust_springboot.admin.audit.AdminAuditAction;
import com.example.stardust_springboot.admin.dto.AdminDtos;
import com.example.stardust_springboot.admin.service.*;
import com.example.stardust_springboot.ai.request.AiRequestStatus;
import com.example.stardust_springboot.common.api.BatchDeleteRequest;
import com.example.stardust_springboot.common.api.BatchDeleteResult;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.*;
import com.example.stardust_springboot.conversation.dto.MessageView;
import com.example.stardust_springboot.file.entity.UserFileStatus;
import com.example.stardust_springboot.file.service.FileDownload;
import com.example.stardust_springboot.knowledge.dto.KnowledgeDocumentView;
import com.example.stardust_springboot.knowledge.entity.*;
import com.example.stardust_springboot.user.entity.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Validated
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminDashboardService dashboard; private final AdminUserService users;
    private final AdminResourceService resources; private final AdminAiService ai;
    private final AdminAuditQueryService audits;
    public AdminController(AdminDashboardService dashboard,AdminUserService users,AdminResourceService resources,AdminAiService ai,AdminAuditQueryService audits){
        this.dashboard=dashboard;this.users=users;this.resources=resources;this.ai=ai;this.audits=audits;
    }
    @GetMapping("/access-check") public ApiResult<Map<String,Boolean>> access(){return ApiResult.success(Map.of("allowed",true));}
    @GetMapping("/dashboard") public ApiResult<AdminDtos.Dashboard> dashboard(){return ApiResult.success(dashboard.dashboard());}

    @GetMapping("/users") public ApiResult<PageResult<AdminDtos.UserView>> users(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) @Size(max=200) String search,@RequestParam(required=false) UserStatus status,@RequestParam(required=false) String role,@RequestParam(required=false) org.springframework.data.domain.Sort.Direction direction){return ApiResult.success(users.list(page,size,search,status,role,direction));}
    @GetMapping("/users/{id}") public ApiResult<AdminDtos.UserView> user(@PathVariable String id){return ApiResult.success(users.get(id));}
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED) public ApiResult<AdminDtos.UserView> createUser(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody AdminDtos.CreateUser body){return ApiResult.success(users.create(actor,body));}
    @PatchMapping("/users/{id}") public ApiResult<AdminDtos.UserView> updateUser(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.UpdateUser body){return ApiResult.success(users.update(actor,id,body));}
    @PatchMapping("/users/{id}/status") public ApiResult<AdminDtos.UserView> status(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.UserStatusChange body){return ApiResult.success(users.changeStatus(actor,id,body));}
    @PostMapping("/users/{id}/restore") public ApiResult<AdminDtos.UserView> restore(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){return ApiResult.success(users.restore(actor,id));}
    @PostMapping("/users/{id}/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT) public void reset(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.ResetPassword body){users.resetPassword(actor,id,body);}
    @PutMapping("/users/{id}/roles") public ApiResult<AdminDtos.UserView> roles(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.RoleChange body){return ApiResult.success(users.changeRoles(actor,id,body));}
    @PostMapping("/users/{id}/usage/adjust") public ApiResult<AdminDtos.UserView> adjustUsage(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.UsageAdjust body){return ApiResult.success(users.adjustUsage(actor,id,body));}
    @DeleteMapping("/users/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteUser(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){users.delete(actor,id);}
    @DeleteMapping("/users/batch") public ApiResult<BatchDeleteResult> deleteUsersBatch(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody BatchDeleteRequest request){return ApiResult.success(users.batchDelete(actor,request.ids()));}

    @GetMapping("/conversations") public ApiResult<PageResult<AdminDtos.ConversationView>> conversations(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String userId,@RequestParam(required=false) @Size(max=200) String search,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return ApiResult.success(resources.conversations(actor,page,size,userId,search,from,to));}
    @GetMapping("/conversations/{id}") public ApiResult<AdminDtos.ConversationDetailView> conversation(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){return ApiResult.success(resources.conversation(actor,id));}
    @GetMapping("/conversations/{id}/messages") public ApiResult<PageResult<MessageView>> messages(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="100") @Min(1) @Max(100) int size){return ApiResult.success(resources.messages(actor,id,page,size));}
    @DeleteMapping("/conversations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteConversation(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){resources.deleteConversation(actor,id);}
    @DeleteMapping("/conversations/batch") public ApiResult<BatchDeleteResult> deleteConversationsBatch(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody BatchDeleteRequest request){return ApiResult.success(resources.batchDeleteConversations(actor,request.ids()));}
    @DeleteMapping("/messages/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteMessage(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){resources.deleteMessage(actor,id);}

    @GetMapping("/files") public ApiResult<PageResult<AdminDtos.FileView>> files(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String userId,@RequestParam(required=false) String mime,@RequestParam(required=false) UserFileStatus status,@RequestParam(required=false) String search,@RequestParam(required=false) String userSearch,@RequestParam(required=false) @Positive Long minSize,@RequestParam(required=false) @Positive Long maxSize,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return ApiResult.success(resources.files(actor,page,size,userId,mime,status,search,userSearch,minSize,maxSize,from,to));}
    @GetMapping("/files/{id}") public ApiResult<AdminDtos.FileDetailView> file(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){return ApiResult.success(resources.file(actor,id));}
    @GetMapping("/files/{id}/download") public ResponseEntity<InputStreamResource> downloadFile(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){return content(resources.download(actor,id));}
    @DeleteMapping("/files/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteFile(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){resources.deleteFile(actor,id);}
    @DeleteMapping("/files/batch") public ApiResult<BatchDeleteResult> deleteFilesBatch(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody BatchDeleteRequest request){return ApiResult.success(resources.batchDeleteFiles(actor,request.ids()));}

    @GetMapping("/knowledge-bases") public ApiResult<PageResult<AdminDtos.KnowledgeBaseView>> knowledgeBases(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String userId,@RequestParam(required=false) KnowledgeBaseStatus status,@RequestParam(required=false) String search,@RequestParam(required=false) String userSearch){return ApiResult.success(resources.knowledgeBases(actor,page,size,userId,status,search,userSearch));}
    @GetMapping("/knowledge-bases/{id}") public ApiResult<AdminDtos.KnowledgeBaseDetailView> knowledgeBase(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){return ApiResult.success(resources.knowledgeBase(actor,id));}
    @GetMapping("/knowledge-documents") public ApiResult<PageResult<AdminDtos.KnowledgeDocumentView>> knowledgeDocuments(@AuthenticationPrincipal AuthenticatedUser actor,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String userId,@RequestParam(required=false) String knowledgeBaseId,@RequestParam(required=false) KnowledgeDocumentStatus status,@RequestParam(required=false) String search,@RequestParam(required=false) String userSearch){return ApiResult.success(resources.knowledgeDocuments(actor,page,size,userId,knowledgeBaseId,status,search,userSearch));}
    @PostMapping("/knowledge-documents/{id}/retry") public ApiResult<AdminDtos.KnowledgeDocumentView> retryDocument(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){return ApiResult.success(resources.retryDocument(actor,id));}
    @DeleteMapping("/knowledge-documents/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteDocument(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){resources.deleteDocument(actor,id);}
    @DeleteMapping("/knowledge-documents/batch") public ApiResult<BatchDeleteResult> deleteDocumentsBatch(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody BatchDeleteRequest request){return ApiResult.success(resources.batchDeleteDocuments(actor,request.ids()));}
    @DeleteMapping("/knowledge-documents/{id}/vectors") public ApiResult<AdminDtos.KnowledgeDocumentView> removeVector(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){return ApiResult.success(resources.removeVector(actor,id));}

    @GetMapping("/ai/providers") public ApiResult<List<AdminDtos.ProviderView>> providers(){return ApiResult.success(ai.providers());}
    @PostMapping("/ai/providers") @ResponseStatus(HttpStatus.CREATED) public ApiResult<AdminDtos.ProviderView> createProvider(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody AdminDtos.ProviderRequest body){return ApiResult.success(ai.createProvider(actor,body));}
    @PutMapping("/ai/providers/{id}") public ApiResult<AdminDtos.ProviderView> updateProvider(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.ProviderRequest body){return ApiResult.success(ai.updateProvider(actor,id,body));}
    @PatchMapping("/ai/providers/{id}/status") public ApiResult<AdminDtos.ProviderView> providerStatus(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.StatusRequest body){return ApiResult.success(ai.providerStatus(actor,id,body.enabled()));}
    @DeleteMapping("/ai/providers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteProvider(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){ai.deleteProvider(actor,id);}
    @GetMapping("/ai/models") public ApiResult<List<AdminDtos.ModelView>> models(){return ApiResult.success(ai.models());}
    @PostMapping("/ai/models") @ResponseStatus(HttpStatus.CREATED) public ApiResult<AdminDtos.ModelView> createModel(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody AdminDtos.ModelRequest body){return ApiResult.success(ai.createModel(actor,body));}
    @PutMapping("/ai/models/{id}") public ApiResult<AdminDtos.ModelView> updateModel(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.ModelRequest body){return ApiResult.success(ai.updateModel(actor,id,body));}
    @PatchMapping("/ai/models/{id}/status") public ApiResult<AdminDtos.ModelView> modelStatus(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.StatusRequest body){return ApiResult.success(ai.modelStatus(actor,id,body.enabled()));}
    @PatchMapping("/ai/models/{id}/default") public ApiResult<AdminDtos.ModelView> modelDefault(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.ModelDefaultRequest body){return ApiResult.success(ai.modelDefault(actor,id,body.defaultModel()));}
    @PatchMapping("/ai/models/{id}/order") public ApiResult<List<AdminDtos.ModelView>> moveModel(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.ModelReorderRequest body){return ApiResult.success(ai.moveModel(actor,id,body.direction()));}
    @DeleteMapping("/ai/models/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteModel(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){ai.deleteModel(actor,id);}
    @GetMapping("/ai/requests") public ApiResult<PageResult<AdminDtos.AiRequestView>> aiRequests(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String userId,@RequestParam(required=false) AiRequestStatus status,@RequestParam(required=false) String provider,@RequestParam(required=false) String model,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return ApiResult.success(ai.requestLogs(page,size,userId,status,provider,model,from,to));}
    @GetMapping("/ai/requests/{requestId}") public ApiResult<AdminDtos.AiRequestView> aiRequest(@PathVariable String requestId){return ApiResult.success(ai.requestLog(requestId));}
    @GetMapping("/audit-logs") public ApiResult<PageResult<AdminDtos.AuditView>> auditLogs(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String adminId,@RequestParam(required=false) AdminAuditAction action,@RequestParam(required=false) String targetType,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return ApiResult.success(audits.list(page,size,adminId,action,targetType,from,to));}

    /** Streams file bytes with the same hardening headers used by the user-facing download endpoint. */
    private ResponseEntity<InputStreamResource> content(FileDownload file){
        ContentDisposition disposition=ContentDisposition.attachment()
                .filename(file.name(),StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mimeType()))
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString())
                .header("X-Content-Type-Options","nosniff")
                .header("Content-Security-Policy","default-src 'none'; sandbox")
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .body(new InputStreamResource(file.input()));
    }
}
