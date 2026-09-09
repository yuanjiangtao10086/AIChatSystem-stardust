package com.example.stardust_springboot.knowledge.service;

import com.example.stardust_springboot.conversation.entity.ChatMessage;
import com.example.stardust_springboot.knowledge.gateway.RagGateway;
import com.example.stardust_springboot.knowledge.repository.ConversationKnowledgeBaseRepository;
import com.example.stardust_springboot.knowledge.repository.KnowledgeDocumentRepository;
import com.example.stardust_springboot.knowledge.entity.KnowledgeDocumentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RagContextService {
    private static final Logger log = LoggerFactory.getLogger(RagContextService.class);
    private final ConversationKnowledgeBaseRepository bindings;
    private final RagGateway gateway;
    private final KnowledgeDocumentRepository documents;

    public RagContextService(ConversationKnowledgeBaseRepository bindings, RagGateway gateway,
                             KnowledgeDocumentRepository documents) {
        this.bindings = bindings;
        this.gateway = gateway;
        this.documents = documents;
    }

    @Transactional(readOnly = true)
    public RagContext retrieve(ChatMessage currentUserMessage) {
        var values = bindings.findOwnedBindings(currentUserMessage.getConversation().getId(),
                currentUserMessage.getUser().getId());
        if (values.isEmpty()) return RagContext.empty();
        var baseIds = values.stream().map(value -> value.getKnowledgeBase().getPublicId()).toList();
        try {
            var result = gateway.retrieve(currentUserMessage.getUser().getPublicId(), baseIds,
                    currentUserMessage.getContentText());
            var returnedDocumentIds = result.sources().stream()
                    .map(com.example.stardust_springboot.knowledge.gateway.RagRetrieveResult.Source::documentId)
                    .distinct().toList();
            if (returnedDocumentIds.isEmpty()) return RagContext.empty();
            var readyDocumentIds = new java.util.HashSet<>(documents.findReadyPublicIds(
                    currentUserMessage.getUser().getId(), baseIds, returnedDocumentIds,
                    KnowledgeDocumentStatus.READY));
            return new RagContext(result.sources().stream()
                    .filter(source -> readyDocumentIds.contains(source.documentId()))
                    .map(source -> new RagContext.Source(
                    source.documentId(), source.knowledgeBaseId(), source.chunkIndex(),
                    source.content(), source.tokenCount(), source.score(), source.page(),
                    source.sourceMetadata())).toList());
        } catch (RuntimeException error) {
            // RAG is enrichment: an unavailable retriever must not make the primary chat fail.
            log.warn("RAG retrieval degraded conversationId={} userId={} cause={}",
                    currentUserMessage.getConversation().getPublicId(),
                    currentUserMessage.getUser().getPublicId(), error.getClass().getSimpleName());
            return RagContext.empty();
        }
    }
}
