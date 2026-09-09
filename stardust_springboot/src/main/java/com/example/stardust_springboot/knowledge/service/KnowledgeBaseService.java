package com.example.stardust_springboot.knowledge.service;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.file.entity.UserFileStatus;
import com.example.stardust_springboot.file.repository.UserFileRepository;
import com.example.stardust_springboot.knowledge.dto.*;
import com.example.stardust_springboot.knowledge.entity.*;
import com.example.stardust_springboot.knowledge.repository.*;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;

@Service
public class KnowledgeBaseService {
    private final KnowledgeBaseRepository bases;
    private final KnowledgeDocumentRepository documents;
    private final ConversationKnowledgeBaseRepository bindings;
    private final UserFileRepository files;
    private final AppUserRepository users;

    public KnowledgeBaseService(KnowledgeBaseRepository bases,
                                KnowledgeDocumentRepository documents,
                                ConversationKnowledgeBaseRepository bindings,
                                UserFileRepository files,
                                AppUserRepository users) {
        this.bases = bases;
        this.documents = documents;
        this.bindings = bindings;
        this.files = files;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResult<KnowledgeBaseView> list(AuthenticatedUser principal, int page, int size,
                                               String search) {
        String normalized = nullable(search);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.from(bases.findOwned(principal.id(), KnowledgeBaseStatus.ACTIVE,
                normalized, pageable).map(KnowledgeBaseView::from));
    }

    @Transactional(readOnly = true)
    public KnowledgeBaseView get(AuthenticatedUser principal, String id) {
        return KnowledgeBaseView.from(requireBase(principal.id(), id));
    }

    @Transactional
    public KnowledgeBaseView create(AuthenticatedUser principal, CreateKnowledgeBaseRequest request) {
        KnowledgeBase value = new KnowledgeBase(users.findById(principal.id()).orElseThrow(),
                required(request.name()), nullable(request.description()));
        return KnowledgeBaseView.from(bases.save(value));
    }

    @Transactional
    public KnowledgeBaseView update(AuthenticatedUser principal, String id,
                                    UpdateKnowledgeBaseRequest request) {
        if (request.name() == null && request.description() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        KnowledgeBase value = requireBase(principal.id(), id);
        value.update(request.name() == null ? value.getName() : required(request.name()),
                request.description() == null ? value.getDescription() : nullable(request.description()));
        return KnowledgeBaseView.from(value);
    }

    @Transactional
    public void delete(AuthenticatedUser principal, String id) {
        KnowledgeBase value = requireBase(principal.id(), id);
        if (documents.existsByKnowledgeBaseIdAndUserIdAndDeletedAtIsNull(value.getId(), principal.id())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT,
                    "delete knowledge documents before deleting the knowledge base");
        }
        bindings.deleteByKnowledgeBaseId(value.getId());
        value.softDelete();
    }

    @Transactional(readOnly = true)
    public PageResult<KnowledgeDocumentView> listDocuments(AuthenticatedUser principal, String baseId,
                                                            int page, int size) {
        KnowledgeBase base = requireBase(principal.id(), baseId);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.from(documents.findByKnowledgeBaseIdAndUserIdAndDeletedAtIsNull(
                base.getId(), principal.id(), pageable).map(KnowledgeDocumentView::from));
    }

    @Transactional
    public KnowledgeDocumentView addDocument(AuthenticatedUser principal, String baseId, String fileId) {
        KnowledgeBase base = requireBase(principal.id(), baseId);
        var file = files.findByPublicIdAndUserIdAndStatusAndDeletedAtIsNull(
                fileId, principal.id(), UserFileStatus.AVAILABLE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (documents.existsByKnowledgeBaseIdAndUserFileIdAndDeletedAtIsNull(
                base.getId(), file.getId())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CONFLICT,
                    "file is already attached to a knowledge base");
        }
        KnowledgeDocument document = new KnowledgeDocument(base, base.getUser(), file);
        return KnowledgeDocumentView.from(documents.save(document));
    }

    KnowledgeBase requireBase(Long userId, String id) {
        return bases.findByPublicIdAndUserIdAndStatusAndDeletedAtIsNull(
                        id, userId, KnowledgeBaseStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String required(String value) {
        String normalized = nullable(value);
        if (normalized == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        return normalized;
    }

    private String nullable(String value) {
        if (value == null) return null;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ").trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
