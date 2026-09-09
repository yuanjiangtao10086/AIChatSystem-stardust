package com.example.stardust_springboot.knowledge.entity;

import com.example.stardust_springboot.common.persistence.BaseEntity;
import com.example.stardust_springboot.conversation.entity.Conversation;
import com.example.stardust_springboot.user.entity.AppUser;
import jakarta.persistence.*;

@Entity
@Table(name = "conversation_knowledge_base")
public class ConversationKnowledgeBase extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_base_id", nullable = false, updatable = false)
    private KnowledgeBase knowledgeBase;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    protected ConversationKnowledgeBase() {
    }

    public ConversationKnowledgeBase(Conversation conversation, KnowledgeBase knowledgeBase) {
        this.conversation = conversation;
        this.knowledgeBase = knowledgeBase;
        this.user = conversation.getUser();
    }

    public KnowledgeBase getKnowledgeBase() { return knowledgeBase; }
}
