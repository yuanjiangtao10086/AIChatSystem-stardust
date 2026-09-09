package com.example.stardust_springboot.conversation.dto;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
        @Size(max = 200, message = "title must not exceed 200 characters") String title
) {
}
