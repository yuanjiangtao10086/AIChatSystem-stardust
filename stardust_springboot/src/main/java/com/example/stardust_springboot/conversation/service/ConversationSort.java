package com.example.stardust_springboot.conversation.service;

public enum ConversationSort {
    LAST_MESSAGE_AT("lastMessageAt"),
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
    TITLE("title");

    private final String property;

    ConversationSort(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
