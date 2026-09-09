package com.example.stardust_springboot.admin.service;

import com.example.stardust_springboot.admin.audit.*;
import com.example.stardust_springboot.admin.dto.AdminDtos;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.common.exception.*;
import com.example.stardust_springboot.conversation.dto.MessageView;
import com.example.stardust_springboot.conversation.entity.*;
import com.example.stardust_springboot.conversation.repository.*;
import com.example.stardust_springboot.file.entity.*;
import com.example.stardust_springboot.file.repository.UserFileRepository;
import com.example.stardust_springboot.file.service.FilePersistenceService;
import com.example.stardust_springboot.file.storage.StorageService;
import com.example.stardust_springboot.knowledge.dto.KnowledgeDocumentView;
import com.example.stardust_springboot.knowledge.entity.*;
import com.example.stardust_springboot.knowledge.repository.*;
import com.example.stardust_springboot.knowledge.service.KnowledgeDocumentService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

@Service
public class AdminResourceService {
    private final ConversationRepository conversations; private final ChatMessageRepository messages;
    private final UserFileRepository files; private final ChatMessageAttachmentRepository attachments;
    private final KnowledgeDocumentRepository documents; private final KnowledgeBaseRepository bases;
    private final FilePersistenceService filePersistence; private final StorageService storage;
    private final KnowledgeDocumentService documentService; private final AdminAuditService audit;
    public AdminResourceService(ConversationRepository conversations, ChatMessageRepository messages,
            UserFileRepository files, ChatMessageAttachmentRepository attachments,
            KnowledgeDocumentRepository documents, KnowledgeBaseRepository bases,
            FilePersistenceService filePersistence, StorageService storage,
            KnowledgeDocumentService documentService, AdminAuditService audit){
        this.conversations=conversations;this.messages=messages;this.files=files;this.attachments=attachments;
        this.documents=documents;this.bases=bases;this.filePersistence=filePersistence;this.storage=storage;
        this.documentService=documentService;this.audit=audit;
    }
    @Transactional(readOnly=true)
    public PageResult<AdminDtos.ConversationView> conversations(int page,int size,String userId,String search,Instant from,Instant to){
        return PageResult.from(conversations.findAdmin(blank(userId),blank(search),from,to,page(page,size)).map(c->
                new AdminDtos.ConversationView(c.getPublicId(),c.getUser().getPublicId(),c.getUser().getEmailNormalized(),
                        c.getTitle(),c.getStatus(),c.getMessageCount(),c.getLastMessageAt(),c.getCreatedAt())));
    }
    @Transactional
    public PageResult<MessageView> messages(AuthenticatedUser actor,String id,int page,int size){
        Conversation c=requireConversation(id); audit.record(actor,AdminAuditAction.CONVERSATION_VIEW,c.getUser(),"CONVERSATION",id,null);
        return PageResult.from(messages.findByConversationIdAndDeletedAtIsNull(c.getId(),
                PageRequest.of(page,size,Sort.by("sequenceNo").ascending().and(Sort.by("variantNo").ascending()))).map(MessageView::from));
    }
    @Transactional
    public void deleteConversation(AuthenticatedUser actor,String id){ Conversation c=requireConversation(id); c.softDelete();
        audit.record(actor,AdminAuditAction.CONVERSATION_DELETE,c.getUser(),"CONVERSATION",id,null); }

    @Transactional(readOnly=true)
    public PageResult<AdminDtos.FileView> files(int page,int size,String userId,String mime,UserFileStatus status,String search){
        return PageResult.from(files.findAdmin(blank(userId),blank(mime),status,blank(search),page(page,size)).map(f->
                new AdminDtos.FileView(f.getPublicId(),f.getUser().getPublicId(),f.getUser().getEmailNormalized(),
                        f.getOriginalName(),f.getDetectedMime(),f.getSizeBytes(),f.getStatus(),
                        attachments.existsByUserFileId(f.getId())||documents.existsByUserFileIdAndUserIdAndDeletedAtIsNull(f.getId(),f.getUser().getId()),f.getCreatedAt())));
    }
    public void deleteFile(AuthenticatedUser actor,String id){
        UserFile f=files.findByPublicIdAndDeletedAtIsNull(id).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if(documents.existsByUserFileIdAndUserIdAndDeletedAtIsNull(f.getId(),f.getUser().getId())) throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT);
        UserFile deleting=filePersistence.beginDelete(f.getUser().getId(),id);
        try { storage.delete(deleting.getObjectKey()); filePersistence.finishDelete(f.getUser().getId(),id); }
        catch(IOException e){ filePersistence.restoreDelete(f.getUser().getId(),id); throw new BusinessException(ErrorCode.STORAGE_ERROR); }
        audit.record(actor,AdminAuditAction.FILE_DELETE,f.getUser(),"USER_FILE",id,null);
    }
    @Transactional(readOnly=true)
    public PageResult<AdminDtos.KnowledgeBaseView> knowledgeBases(int page,int size,String userId,KnowledgeBaseStatus status,String search){
        return PageResult.from(bases.findAdmin(blank(userId),status,blank(search),page(page,size)).map(k->
                new AdminDtos.KnowledgeBaseView(k.getPublicId(),k.getUser().getPublicId(),k.getUser().getEmailNormalized(),k.getName(),k.getStatus(),k.getCreatedAt())));
    }
    @Transactional(readOnly=true)
    public PageResult<AdminDtos.KnowledgeDocumentView> knowledgeDocuments(int page,int size,String userId,String baseId,KnowledgeDocumentStatus status){
        return PageResult.from(documents.findAdmin(blank(userId),blank(baseId),status,page(page,size)).map(d->
                new AdminDtos.KnowledgeDocumentView(d.getPublicId(),d.getKnowledgeBase().getPublicId(),d.getUser().getPublicId(),
                        d.getUserFile().getOriginalName(),d.getStatus(),d.getChunkCount(),d.getErrorCode(),d.getCreatedAt())));
    }
    public KnowledgeDocumentView retryDocument(AuthenticatedUser actor,String id){ KnowledgeDocument d=requireDocument(id);
        var result=documentService.retry(owner(d),id); audit.record(actor,AdminAuditAction.RAG_DOCUMENT_RETRY,d.getUser(),"KNOWLEDGE_DOCUMENT",id,null); return result; }
    public KnowledgeDocumentView removeVector(AuthenticatedUser actor,String id){ KnowledgeDocument d=requireDocument(id);
        var result=documentService.removeVectors(owner(d),id); audit.record(actor,AdminAuditAction.RAG_VECTOR_REMOVE,d.getUser(),"KNOWLEDGE_DOCUMENT",id,null); return result; }
    private Conversation requireConversation(String id){return conversations.findByPublicIdAndDeletedAtIsNull(id).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
    private KnowledgeDocument requireDocument(String id){return documents.findByPublicIdAndDeletedAtIsNull(id).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
    private AuthenticatedUser owner(KnowledgeDocument d){return new AuthenticatedUser(d.getUser().getId(),d.getUser().getPublicId(),d.getUser().getEmailNormalized(),d.getUser().getDisplayName(),d.getUser().getAuthVersion(),Set.of("USER"));}
    private Pageable page(int page,int size){return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by(Sort.Direction.DESC,"id")));}
    private String blank(String v){return v==null||v.isBlank()?null:v.trim();}
}
