package com.example.stardust_springboot.conversation.service;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.api.PageResult;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.conversation.dto.ConversationView;
import com.example.stardust_springboot.conversation.dto.CreateConversationRequest;
import com.example.stardust_springboot.conversation.dto.UpdateConversationRequest;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.entity.ConversationStatus;
import com.example.stardust_springboot.conversation.repository.ConversationRepository;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.stardust_springboot.common.api.BatchDeleteResult;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationService {

    private static final String DEFAULT_TITLE = "New conversation";

    private final ConversationRepository conversationRepository;
    private final AppUserRepository userRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               AppUserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ConversationView create(AuthenticatedUser principal, CreateConversationRequest request) {
        AppUser user = userRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        String title = normalizeTitle(request.title(), DEFAULT_TITLE);
        Conversation conversation = conversationRepository.saveAndFlush(new Conversation(user, title));
        return ConversationView.from(conversation);
    }

    @Transactional(readOnly = true)
    public PageResult<ConversationView> list(AuthenticatedUser principal, int page, int size,
                                             String search, ConversationStatus status,
                                             ConversationSort sort, Sort.Direction direction) {
        if (status == ConversationStatus.DELETED) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Sort pageSort = Sort.by(direction, sort.property()).and(Sort.by(direction, "id"));
        Page<ConversationView> result = conversationRepository.findAllOwned(
                        principal.id(), status, normalizeSearch(search), PageRequest.of(page, size, pageSort))
                .map(ConversationView::from);
        return PageResult.from(result);
    }

    @Transactional(readOnly = true)
    public ConversationView get(AuthenticatedUser principal, String conversationId) {
        return ConversationView.from(requireOwned(conversationId, principal.id()));
    }

    @Transactional
    public ConversationView update(AuthenticatedUser principal, String conversationId,
                                   UpdateConversationRequest request) {
        if (request.title() == null && request.status() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        if (request.status() == ConversationStatus.DELETED) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        Conversation conversation = requireOwned(conversationId, principal.id());
        String title = request.title() == null ? null : normalizeTitle(request.title(), null);
        conversation.update(title, request.status());
        return ConversationView.from(conversationRepository.saveAndFlush(conversation));
    }

    @Transactional
    public void delete(AuthenticatedUser principal, String conversationId) {
        Conversation conversation = requireOwned(conversationId, principal.id());
        conversation.softDelete();
        conversationRepository.saveAndFlush(conversation);
    }

    /**
     * Deletes many owned conversations, one transaction per id. A failure on one id is reported in
     * {@link BatchDeleteResult#failures()} and does not roll back the others.
     */
    public BatchDeleteResult batchDelete(AuthenticatedUser principal, List<String> ids) {
        long deleted = 0;
        List<BatchDeleteResult.BatchDeleteFailure> failures = new ArrayList<>();
        for (String id : ids) {
            try {
                delete(principal, id);
                deleted++;
            } catch (BusinessException error) {
                failures.add(new BatchDeleteResult.BatchDeleteFailure(
                        id, String.valueOf(error.getErrorCode().code()), error.getMessage()));
            } catch (Exception error) {
                failures.add(new BatchDeleteResult.BatchDeleteFailure(id, "UNEXPECTED", error.getMessage()));
            }
        }
        return BatchDeleteResult.of(deleted, failures);
    }

    /**
     * Soft-deletes every conversation owned by the user that has no messages yet (messageCount == 0),
     * except the one identified by {@code keepId}. Used to prune the empty "New conversation" rows a
     * user leaves behind by clicking "开启新对话" without ever sending a message. Conversations that
     * already contain a user message are never touched.
     *
     * @return number of conversations removed
     */
    @Transactional
    public int deleteEmptyConversations(AuthenticatedUser principal, String keepId) {
        return conversationRepository.softDeleteEmptyOwned(principal.id(), keepId, Instant.now());
    }

    private Conversation requireOwned(String conversationId, Long userId) {
        return conversationRepository.findByPublicIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String normalizeTitle(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty()) {
            if (fallback != null) {
                return fallback;
            }
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        return normalized;
    }

    private String normalizeSearch(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
