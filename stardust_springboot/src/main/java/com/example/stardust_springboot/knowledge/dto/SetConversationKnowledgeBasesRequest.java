package com.example.stardust_springboot.knowledge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SetConversationKnowledgeBasesRequest(
        @NotNull @Size(max = 20) List<@NotBlank String> knowledgeBaseIds) {
}
