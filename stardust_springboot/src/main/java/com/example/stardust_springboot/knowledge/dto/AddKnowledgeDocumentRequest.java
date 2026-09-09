package com.example.stardust_springboot.knowledge.dto;

import jakarta.validation.constraints.NotBlank;

public record AddKnowledgeDocumentRequest(@NotBlank String fileId) {
}
