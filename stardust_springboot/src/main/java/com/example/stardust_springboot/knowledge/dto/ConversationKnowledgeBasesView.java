package com.example.stardust_springboot.knowledge.dto;

import java.util.List;

public record ConversationKnowledgeBasesView(List<KnowledgeBaseView> items) {
    public ConversationKnowledgeBasesView {
        items = List.copyOf(items);
    }
}
