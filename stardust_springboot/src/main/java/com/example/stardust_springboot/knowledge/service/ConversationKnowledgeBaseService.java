package com.example.stardust_springboot.knowledge.service;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.conversation.repository.ConversationRepository;
import com.example.stardust_springboot.knowledge.dto.ConversationKnowledgeBasesView;
import com.example.stardust_springboot.knowledge.dto.KnowledgeBaseView;
import com.example.stardust_springboot.knowledge.dto.SetConversationKnowledgeBasesRequest;
import com.example.stardust_springboot.knowledge.entity.ConversationKnowledgeBase;
import com.example.stardust_springboot.knowledge.entity.KnowledgeBase;
import com.example.stardust_springboot.knowledge.entity.KnowledgeBaseStatus;
import com.example.stardust_springboot.knowledge.repository.ConversationKnowledgeBaseRepository;
import com.example.stardust_springboot.knowledge.repository.KnowledgeBaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ConversationKnowledgeBaseService {
    private final ConversationRepository conversations;
    private final KnowledgeBaseRepository bases;
    private final ConversationKnowledgeBaseRepository bindings;

    public ConversationKnowledgeBaseService(ConversationRepository conversations,
                                            KnowledgeBaseRepository bases,
                                            ConversationKnowledgeBaseRepository bindings) {
        this.conversations = conversations;
        this.bases = bases;
        this.bindings = bindings;
    }

    @Transactional(readOnly = true)
    public ConversationKnowledgeBasesView get(AuthenticatedUser principal, String conversationId) {
        Conversation conversation = requireConversation(principal.id(), conversationId);
        return view(bindings.findOwnedBindings(conversation.getId(), principal.id()));
    }

    @Transactional
    public ConversationKnowledgeBasesView replace(AuthenticatedUser principal, String conversationId,
                                                   SetConversationKnowledgeBasesRequest request) {
        Conversation conversation = requireConversation(principal.id(), conversationId);
        var requestedIds = new LinkedHashSet<>(request.knowledgeBaseIds());
        List<KnowledgeBase> owned = requestedIds.isEmpty() ? List.of()
                : bases.findByPublicIdInAndUserIdAndStatusAndDeletedAtIsNull(
                        requestedIds, principal.id(), KnowledgeBaseStatus.ACTIVE);
        if (owned.size() != requestedIds.size()) {
            // Keep cross-tenant resource existence private.
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        bindings.deleteOwnedByConversationId(conversation.getId(), principal.id());
        bindings.flush();
        bindings.saveAll(owned.stream()
                .map(base -> new ConversationKnowledgeBase(conversation, base)).toList());
        return view(bindings.findOwnedBindings(conversation.getId(), principal.id()));
    }

    private Conversation requireConversation(Long userId, String publicId) {
        return conversations.findByPublicIdAndUserIdAndDeletedAtIsNull(publicId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ConversationKnowledgeBasesView view(List<ConversationKnowledgeBase> values) {
        return new ConversationKnowledgeBasesView(values.stream()
                .map(ConversationKnowledgeBase::getKnowledgeBase)
                .map(KnowledgeBaseView::from).toList());
    }
}
