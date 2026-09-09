package com.example.stardust_springboot.admin.controller;

import com.example.stardust_springboot.admin.audit.AdminAuditAction;
import com.example.stardust_springboot.admin.dto.AdminDtos;
import com.example.stardust_springboot.admin.service.*;
import com.example.stardust_springboot.ai.request.AiRequestStatus;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.*;
import com.example.stardust_springboot.conversation.dto.MessageView;
import com.example.stardust_springboot.file.entity.UserFileStatus;
import com.example.stardust_springboot.knowledge.dto.KnowledgeDocumentView;
import com.example.stardust_springboot.knowledge.entity.*;
import com.example.stardust_springboot.user.entity.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/users") public ApiResult<PageResult<AdminDtos.UserView>> users(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) @Size(max=200) String search,@RequestParam(required=false) UserStatus status){return ApiResult.success(users.list(page,size,search,status));}
    @GetMapping("/users/{id}") public ApiResult<AdminDtos.UserView> user(@PathVariable String id){return ApiResult.success(users.get(id));}
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED) public ApiResult<AdminDtos.UserView> createUser(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody AdminDtos.CreateUser body){return ApiResult.success(users.create(actor,body));}
    @PatchMapping("/users/{id}") public ApiResult<AdminDtos.UserView> updateUser(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.UpdateUser body){return ApiResult.success(users.update(actor,id,body));}
    @PatchMapping("/users/{id}/status") public ApiResult<AdminDtos.UserView> status(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.UserStatusChange body){return ApiResult.success(users.changeStatus(actor,id,body));}
    @PostMapping("/users/{id}/restore") public ApiResult<AdminDtos.UserView> restore(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){return ApiResult.success(users.restore(actor,id));}
    @PostMapping("/users/{id}/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT) public void reset(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.ResetPassword body){users.resetPassword(actor,id,body);}
    @PutMapping("/users/{id}/roles") public ApiResult<AdminDtos.UserView> roles(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.RoleChange body){return ApiResult.success(users.changeRoles(actor,id,body));}
    @DeleteMapping("/users/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteUser(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){users.delete(actor,id);}

    @GetMapping("/conversations") public ApiResult<PageResult<AdminDtos.ConversationView>> conversations(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String userId,@RequestParam(required=false) @Size(max=200) String search,@RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to){return ApiResult.success(resources.conversations(page,size,userId,search,from,to));}
    @GetMapping("/conversations/{id}/messages") public ApiResult<PageResult<MessageView>> messages(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="100") @Min(1) @Max(100) int size){return ApiResult.success(resources.messages(actor,id,page,size));}
    @DeleteMapping("/conversations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteConversation(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){resources.deleteConversation(actor,id);}

    @GetMapping("/files") public ApiResult<PageResult<AdminDtos.FileView>> files(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String userId,@RequestParam(required=false) String mime,@RequestParam(required=false) UserFileStatus status,@RequestParam(required=false) String search){return ApiResult.success(resources.files(page,size,userId,mime,status,search));}
    @DeleteMapping("/files/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteFile(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){resources.deleteFile(actor,id);}

    @GetMapping("/knowledge-bases") public ApiResult<PageResult<AdminDtos.KnowledgeBaseView>> knowledgeBases(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String userId,@RequestParam(required=false) KnowledgeBaseStatus status,@RequestParam(required=false) String search){return ApiResult.success(resources.knowledgeBases(page,size,userId,status,search));}
    @GetMapping("/knowledge-documents") public ApiResult<PageResult<AdminDtos.KnowledgeDocumentView>> knowledgeDocuments(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String userId,@RequestParam(required=false) String knowledgeBaseId,@RequestParam(required=false) KnowledgeDocumentStatus status){return ApiResult.success(resources.knowledgeDocuments(page,size,userId,knowledgeBaseId,status));}
    @PostMapping("/knowledge-documents/{id}/retry") public ApiResult<KnowledgeDocumentView> retryDocument(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){return ApiResult.success(resources.retryDocument(actor,id));}
    @DeleteMapping("/knowledge-documents/{id}/vectors") public ApiResult<KnowledgeDocumentView> removeVector(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){return ApiResult.success(resources.removeVector(actor,id));}

    @GetMapping("/ai/providers") public ApiResult<List<AdminDtos.ProviderView>> providers(){return ApiResult.success(ai.providers());}
    @PostMapping("/ai/providers") @ResponseStatus(HttpStatus.CREATED) public ApiResult<AdminDtos.ProviderView> createProvider(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody AdminDtos.ProviderRequest body){return ApiResult.success(ai.createProvider(actor,body));}
    @PutMapping("/ai/providers/{id}") public ApiResult<AdminDtos.ProviderView> updateProvider(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.ProviderRequest body){return ApiResult.success(ai.updateProvider(actor,id,body));}
    @PatchMapping("/ai/providers/{id}/status") public ApiResult<AdminDtos.ProviderView> providerStatus(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.StatusRequest body){return ApiResult.success(ai.providerStatus(actor,id,body.enabled()));}
    @DeleteMapping("/ai/providers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteProvider(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){ai.providerStatus(actor,id,false);}
    @GetMapping("/ai/models") public ApiResult<List<AdminDtos.ModelView>> models(){return ApiResult.success(ai.models());}
    @PostMapping("/ai/models") @ResponseStatus(HttpStatus.CREATED) public ApiResult<AdminDtos.ModelView> createModel(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody AdminDtos.ModelRequest body){return ApiResult.success(ai.createModel(actor,body));}
    @PutMapping("/ai/models/{id}") public ApiResult<AdminDtos.ModelView> updateModel(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.ModelRequest body){return ApiResult.success(ai.updateModel(actor,id,body));}
    @PatchMapping("/ai/models/{id}/status") public ApiResult<AdminDtos.ModelView> modelStatus(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id,@Valid @RequestBody AdminDtos.StatusRequest body){return ApiResult.success(ai.modelStatus(actor,id,body.enabled()));}
    @DeleteMapping("/ai/models/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteModel(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String id){ai.modelStatus(actor,id,false);}
    @GetMapping("/ai/requests") public ApiResult<PageResult<AdminDtos.AiRequestView>> aiRequests(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String userId,@RequestParam(required=false) AiRequestStatus status,@RequestParam(required=false) String provider,@RequestParam(required=false) String model){return ApiResult.success(ai.requestLogs(page,size,userId,status,provider,model));}
    @GetMapping("/audit-logs") public ApiResult<PageResult<AdminDtos.AuditView>> auditLogs(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@RequestParam(required=false) String adminId,@RequestParam(required=false) AdminAuditAction action,@RequestParam(required=false) String targetType){return ApiResult.success(audits.list(page,size,adminId,action,targetType));}
}
