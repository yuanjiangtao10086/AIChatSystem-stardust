package com.example.stardust_springboot.conversation.dto;

import com.example.stardust_springboot.conversation.entity.ConversationStatus;
import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(
        @Size(max = 200, message = "title must not exceed 200 characters") String title,
        ConversationStatus status
) {
}
