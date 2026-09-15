package com.example.stardust_springboot.admin.service;

import com.example.stardust_springboot.admin.audit.*;
import com.example.stardust_springboot.admin.dto.AdminDtos;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.BatchDeleteResult;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.common.exception.*;
import com.example.stardust_springboot.conversation.dto.MessageView;
import com.example.stardust_springboot.conversation.entity.*;
import com.example.stardust_springboot.conversation.repository.*;
import com.example.stardust_springboot.file.entity.*;
import com.example.stardust_springboot.file.repository.UserFileRepository;
import com.example.stardust_springboot.file.service.FileDownload;
import com.example.stardust_springboot.file.service.FilePersistenceService;
import com.example.stardust_springboot.file.storage.StorageService;
import com.example.stardust_springboot.knowledge.entity.*;
import com.example.stardust_springboot.knowledge.repository.*;
import com.example.stardust_springboot.knowledge.service.KnowledgeDocumentService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class AdminResourceService {
    private final ConversationRepository conversations; private final ChatMessageRepository messages;
    private final UserFileRepository files; private final ChatMessageAttachmentRepository attachments;
    private final KnowledgeDocumentRepository documents; private final KnowledgeBaseRepository bases;
    private final FilePersistenceService filePersistence; private final StorageService storage;
    private final KnowledgeDocumentService documentService; private final AdminAuditService audit;
    private final AdminAuthorizationService adminAuth;
    public AdminResourceService(ConversationRepository conversations, ChatMessageRepository messages,
            UserFileRepository files, ChatMessageAttachmentRepository attachments,
            KnowledgeDocumentRepository documents, KnowledgeBaseRepository bases,
            FilePersistenceService filePersistence, StorageService storage,
            KnowledgeDocumentService documentService, AdminAuditService audit,
            AdminAuthorizationService adminAuth){
        this.conversations=conversations;this.messages=messages;this.files=files;this.attachments=attachments;
        this.documents=documents;this.bases=bases;this.filePersistence=filePersistence;this.storage=storage;
        this.documentService=documentService;this.audit=audit;this.adminAuth=adminAuth;
    }
    @Transactional(readOnly=true)
    public PageResult<AdminDtos.ConversationView> conversations(AuthenticatedUser actor,int page,int size,String userId,String search,Instant from,Instant to){
        return PageResult.from(conversations.findAdmin(blank(userId),blank(search),from,to,hidesSuperAdminOwned(actor),page(page,size)).map(c->
                new AdminDtos.ConversationView(c.getPublicId(),c.getUser().getPublicId(),c.getUser().getEmailNormalized(),
                        c.getUser().getDisplayName(),c.getTitle(),c.getStatus(),c.getMessageCount(),
                        c.getLastMessageAt(),c.getCreatedAt())));
    }
    /** Writes the VIEW_CONVERSATION audit row, so it must not run in a read-only transaction. */
    @Transactional
    public AdminDtos.ConversationDetailView conversation(AuthenticatedUser actor,String id){
        Conversation c=requireConversation(id); adminAuth.requireCanView(actor,c.getUser());
        audit.record(actor,AdminAuditAction.VIEW_CONVERSATION,c.getUser(),"CONVERSATION",id,null);
        PageResult<MessageView> page=PageResult.from(messages.findByConversationIdAndDeletedAtIsNull(c.getId(),
                PageRequest.of(0,100,Sort.by("sequenceNo").ascending().and(Sort.by("variantNo").ascending()))).map(MessageView::from));
        return new AdminDtos.ConversationDetailView(c.getPublicId(),c.getUser().getPublicId(),c.getUser().getEmailNormalized(),
                c.getUser().getDisplayName(),c.getTitle(),c.getStatus(),c.getMessageCount(),c.getLastMessageAt(),
                c.getCreatedAt(),c.getUpdatedAt(),page);
    }
    @Transactional
    public PageResult<MessageView> messages(AuthenticatedUser actor,String id,int page,int size){
        Conversation c=requireConversation(id); adminAuth.requireCanView(actor,c.getUser());
        audit.record(actor,AdminAuditAction.VIEW_CHAT_MESSAGES,c.getUser(),"CONVERSATION",id,null);
        return PageResult.from(messages.findByConversationIdAndDeletedAtIsNull(c.getId(),
                PageRequest.of(page,size,Sort.by("sequenceNo").ascending().and(Sort.by("variantNo").ascending()))).map(MessageView::from));
    }
    @Transactional
    public void deleteConversation(AuthenticatedUser actor,String id){ Conversation c=requireConversation(id);
        adminAuth.requireCanView(actor,c.getUser()); c.softDelete();
        audit.record(actor,AdminAuditAction.DELETE_CONVERSATION,c.getUser(),"CONVERSATION",id,null); }
    @Transactional
    public void deleteMessage(AuthenticatedUser actor,String id){
        ChatMessage m=messages.findByPublicIdAndDeletedAtIsNull(id).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        adminAuth.requireCanView(actor,m.getUser());
        Conversation c=m.getConversation();
        if(c!=null){ c.decrementMessageCount(); conversations.saveAndFlush(c); }
        m.softDelete(); messages.saveAndFlush(m);
        audit.record(actor,AdminAuditAction.DELETE_CHAT_MESSAGE,m.getUser(),"CHAT_MESSAGE",id,null);
    }

    @Transactional(readOnly=true)
    public PageResult<AdminDtos.FileView> files(AuthenticatedUser actor,int page,int size,String userId,String mime,UserFileStatus status,
                                                String search,String userSearch,Long minSize,Long maxSize,Instant from,Instant to){
        return PageResult.from(files.findAdmin(blank(userId),blank(mime),status,blank(search),blank(userSearch),
                minSize,maxSize,from,to,hidesSuperAdminOwned(actor),page(page,size)).map(f->
                new AdminDtos.FileView(f.getPublicId(),f.getUser().getPublicId(),f.getUser().getEmailNormalized(),
                        f.getUser().getDisplayName(),f.getOriginalName(),f.getDetectedMime(),f.getSizeBytes(),f.getStatus(),
                        referenced(f),f.getCreatedAt())));
    }
    /**
     * File metadata for an administrator. Viewing another user's file is a sensitive read and is
     * audited, so the transaction is read-write: persisting an audit row inside a
     * {@code readOnly=true} transaction relies on identity-insert side effects and is not a
     * guarantee the schema should depend on.
     */
    @Transactional
    public AdminDtos.FileDetailView file(AuthenticatedUser actor,String id){
        UserFile f=requireFile(id); adminAuth.requireCanView(actor,f.getUser());
        audit.record(actor,AdminAuditAction.VIEW_USER_FILE,f.getUser(),"USER_FILE",id,null);
        long attachmentCount=attachments.countActiveByUserFileId(f.getId());
        long documentCount=documents.countByUserFileIdAndDeletedAtIsNull(f.getId());
        return new AdminDtos.FileDetailView(f.getPublicId(),f.getUser().getPublicId(),f.getUser().getEmailNormalized(),
                f.getUser().getDisplayName(),f.getOriginalName(),f.getDeclaredMime(),f.getDetectedMime(),f.getExtension(),
                f.getSizeBytes(),f.getSha256(),f.getStorageProvider(),f.getStatus(),f.getMetadataJson(),
                attachmentCount,documentCount,attachmentCount>0||documentCount>0,f.getCreatedAt(),f.getUpdatedAt());
    }
    /**
     * Streams a file's bytes through {@link StorageService}. The caller can only address a file by its
     * public id and the returned payload never carries the storage object key or any server path.
     */
    /** Writes the FILE_DOWNLOAD audit row, so it must not run in a read-only transaction. */
    @Transactional
    public FileDownload download(AuthenticatedUser actor,String id){
        UserFile f=requireFile(id); adminAuth.requireCanView(actor,f.getUser());
        audit.record(actor,AdminAuditAction.FILE_DOWNLOAD,f.getUser(),"USER_FILE",id,null);
        String key=requireSafeObjectKey(f);
        try { return new FileDownload(f.getOriginalName(),f.getDetectedMime(),f.getSizeBytes(),storage.open(key)); }
        catch(IOException e){ throw new BusinessException(ErrorCode.STORAGE_ERROR); }
    }
    public void deleteFile(AuthenticatedUser actor,String id){
        UserFile f=requireFile(id); adminAuth.requireCanView(actor,f.getUser());
        if(documents.existsByUserFileIdAndUserIdAndDeletedAtIsNull(f.getId(),f.getUser().getId())) throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        UserFile deleting=filePersistence.beginDelete(f.getUser().getId(),id);
        try { storage.delete(requireSafeObjectKey(deleting)); filePersistence.finishDelete(f.getUser().getId(),id); }
        catch(IOException e){ filePersistence.restoreDelete(f.getUser().getId(),id); throw new BusinessException(ErrorCode.STORAGE_ERROR); }
        audit.record(actor,AdminAuditAction.FILE_DELETE,f.getUser(),"USER_FILE",id,null);
    }
    /** Batch file deletion: one id per call; failures are reported per id and never abort the batch. */
    public BatchDeleteResult batchDeleteFiles(AuthenticatedUser actor,List<String> ids){
        long deleted=0; List<BatchDeleteResult.BatchDeleteFailure> failures=new ArrayList<>();
        for(String id:ids){ try{ deleteFile(actor,id); deleted++; }
            catch(BusinessException e){ failures.add(new BatchDeleteResult.BatchDeleteFailure(id,String.valueOf(e.getErrorCode().code()),e.getMessage())); }
            catch(Exception e){ failures.add(new BatchDeleteResult.BatchDeleteFailure(id,"UNEXPECTED",e.getMessage())); } }
        return BatchDeleteResult.of(deleted,failures);
    }
    /** Batch conversation deletion: one id per call; failures are reported per id and never abort the batch. */
    public BatchDeleteResult batchDeleteConversations(AuthenticatedUser actor,List<String> ids){
        long deleted=0; List<BatchDeleteResult.BatchDeleteFailure> failures=new ArrayList<>();
        for(String id:ids){ try{ deleteConversation(actor,id); deleted++; }
            catch(BusinessException e){ failures.add(new BatchDeleteResult.BatchDeleteFailure(id,String.valueOf(e.getErrorCode().code()),e.getMessage())); }
            catch(Exception e){ failures.add(new BatchDeleteResult.BatchDeleteFailure(id,"UNEXPECTED",e.getMessage())); } }
        return BatchDeleteResult.of(deleted,failures);
    }
    /** Batch knowledge-document deletion: one id per call; failures are reported per id and never abort the batch. */
    public BatchDeleteResult batchDeleteDocuments(AuthenticatedUser actor,List<String> ids){
        long deleted=0; List<BatchDeleteResult.BatchDeleteFailure> failures=new ArrayList<>();
        for(String id:ids){ try{ deleteDocument(actor,id); deleted++; }
            catch(BusinessException e){ failures.add(new BatchDeleteResult.BatchDeleteFailure(id,String.valueOf(e.getErrorCode().code()),e.getMessage())); }
            catch(Exception e){ failures.add(new BatchDeleteResult.BatchDeleteFailure(id,"UNEXPECTED",e.getMessage())); } }
        return BatchDeleteResult.of(deleted,failures);
    }
    @Transactional(readOnly=true)
    public PageResult<AdminDtos.KnowledgeBaseView> knowledgeBases(AuthenticatedUser actor,int page,int size,String userId,KnowledgeBaseStatus status,String search,String userSearch){
        return PageResult.from(bases.findAdmin(blank(userId),status,blank(search),blank(userSearch),hidesSuperAdminOwned(actor),page(page,size)).map(k->
                new AdminDtos.KnowledgeBaseView(k.getPublicId(),k.getUser().getPublicId(),k.getUser().getEmailNormalized(),
                        k.getUser().getDisplayName(),k.getName(),k.getStatus(),k.getCreatedAt(),k.getUpdatedAt())));
    }
    /** Knowledge base detail: owner, pipeline statistics and the first page of documents. Audited as a sensitive read, hence read-write. */
    @Transactional
    public AdminDtos.KnowledgeBaseDetailView knowledgeBase(AuthenticatedUser actor,String id){
        KnowledgeBase b=requireBase(id); adminAuth.requireCanView(actor,b.getUser());
        audit.record(actor,AdminAuditAction.VIEW_KNOWLEDGE_BASE,b.getUser(),"KNOWLEDGE_BASE",id,null);
        long documentCount=documents.countByKnowledgeBaseIdAndDeletedAtIsNull(b.getId());
        long readyCount=documents.countByKnowledgeBaseIdAndStatusAndDeletedAtIsNull(b.getId(),KnowledgeDocumentStatus.READY);
        long failedCount=documents.countByKnowledgeBaseIdAndStatusAndDeletedAtIsNull(b.getId(),KnowledgeDocumentStatus.FAILED);
        long chunks=documents.sumChunkCountByKnowledgeBaseIdAndDeletedAtIsNull(b.getId());
        return new AdminDtos.KnowledgeBaseDetailView(b.getPublicId(),b.getUser().getPublicId(),b.getUser().getEmailNormalized(),
                b.getUser().getDisplayName(),b.getName(),b.getDescription(),b.getStatus(),documentCount,readyCount,failedCount,
                chunks,b.getCreatedAt(),b.getUpdatedAt(),documentsOf(b.getPublicId(),0,50));
    }
    @Transactional(readOnly=true)
    public PageResult<AdminDtos.KnowledgeDocumentView> knowledgeDocuments(AuthenticatedUser actor,int page,int size,String userId,String baseId,KnowledgeDocumentStatus status,String search,String userSearch){
        return PageResult.from(documents.findAdmin(blank(userId),blank(baseId),status,blank(search),blank(userSearch),hidesSuperAdminOwned(actor),page(page,size))
                .map(this::documentView));
    }
    /**
     * Re-runs the RAG pipeline for a failed document. Only Spring talks to Python, through
     * {@code KnowledgeDocumentService} and the {@code RagGateway}; Vue never reaches the AI service.
     */
    public AdminDtos.KnowledgeDocumentView retryDocument(AuthenticatedUser actor,String id){
        KnowledgeDocument d=requireDocument(id); adminAuth.requireCanView(actor,d.getUser());
        documentService.retry(owner(d),id);
        audit.record(actor,AdminAuditAction.RAG_DOCUMENT_RETRY,d.getUser(),"KNOWLEDGE_DOCUMENT",id,null);
        return documentView(requireDocument(id));
    }
    /** Deletes the document: chunks, metadata and the vector index are removed across the Spring/Python boundary. */
    public void deleteDocument(AuthenticatedUser actor,String id){
        KnowledgeDocument d=requireDocument(id); adminAuth.requireCanView(actor,d.getUser());
        documentService.delete(owner(d),id);
        audit.record(actor,AdminAuditAction.RAG_DOCUMENT_DELETE,d.getUser(),"KNOWLEDGE_DOCUMENT",id,null);
    }
    /** Drops only the vector index: the document row stays as an auditable, non READY record. */
    public AdminDtos.KnowledgeDocumentView removeVector(AuthenticatedUser actor,String id){
        KnowledgeDocument d=requireDocument(id); adminAuth.requireCanView(actor,d.getUser());
        documentService.removeVectors(owner(d),id);
        audit.record(actor,AdminAuditAction.RAG_VECTOR_REMOVE,d.getUser(),"KNOWLEDGE_DOCUMENT",id,null);
        return documentView(requireDocument(id));
    }
    private Conversation requireConversation(String id){return conversations.findByPublicIdAndDeletedAtIsNull(id).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
    /** Eagerly loads owner, base and source file because the write paths run without an enclosing transaction. */
    private KnowledgeDocument requireDocument(String id){return documents.findAdminDetail(id).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
    private KnowledgeBase requireBase(String id){return bases.findAdminDetail(id).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
    private PageResult<AdminDtos.KnowledgeDocumentView> documentsOf(String baseId,int page,int size){
        return PageResult.from(documents.findAdmin(null,baseId,null,null,null,false,
                PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by(Sort.Direction.DESC,"id"))))
                .map(this::documentView));
    }
    private AdminDtos.KnowledgeDocumentView documentView(KnowledgeDocument d){
        return new AdminDtos.KnowledgeDocumentView(d.getPublicId(),d.getKnowledgeBase().getPublicId(),d.getKnowledgeBase().getName(),
                d.getUser().getPublicId(),d.getUser().getEmailNormalized(),d.getUser().getDisplayName(),
                d.getUserFile().getPublicId(),d.getUserFile().getOriginalName(),d.getUserFile().getDetectedMime(),
                d.getStatus(),d.getChunkCount(),d.getProcessingVersion(),d.getParserType(),d.getEmbeddingProvider(),
                d.getEmbeddingModel(),d.getErrorCode(),d.getErrorMessage(),d.getStartedAt(),d.getCompletedAt(),
                d.getCreatedAt(),d.getUpdatedAt());
    }
    private UserFile requireFile(String id){return files.findByPublicIdAndDeletedAtIsNull(id).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
    private boolean referenced(UserFile f){return attachments.existsActiveByUserFileId(f.getId())||documents.existsByUserFileIdAndUserIdAndDeletedAtIsNull(f.getId(),f.getUser().getId());}
    /** Defence in depth: the storage object key must stay a relative, non traversing path. */
    private String requireSafeObjectKey(UserFile f){
        String key=f.getObjectKey();
        if(key==null||key.isBlank()||key.contains("..")||key.startsWith("/")||key.contains("\\"))
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        return key;
    }
    private AuthenticatedUser owner(KnowledgeDocument d){return new AuthenticatedUser(d.getUser().getId(),d.getUser().getPublicId(),d.getUser().getEmailNormalized(),d.getUser().getDisplayName(),d.getUser().getAuthVersion(),Set.of("USER"));}
    private Pageable page(int page,int size){return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by(Sort.Direction.DESC,"id")));}
    private String blank(String v){return v==null||v.isBlank()?null:v.trim();}
    /**
     * ADR-058: an administrator that is not SUPER_ADMIN may not open the private resources of a
     * SUPER_ADMIN. Listing must apply exactly the same rule as opening, otherwise the console
     * advertises rows the operator is forbidden to open and every click looks like a missing row.
     */
    private boolean hidesSuperAdminOwned(AuthenticatedUser actor){
        return actor == null || !actor.roles().contains("SUPER_ADMIN");
    }
}
