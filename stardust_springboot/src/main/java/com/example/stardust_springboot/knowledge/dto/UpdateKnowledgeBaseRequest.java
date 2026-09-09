package com.example.stardust_springboot.knowledge.dto;

import jakarta.validation.constraints.Size;

public record UpdateKnowledgeBaseRequest(
        @Size(min = 1, max = 120) String name,
        @Size(max = 1000) String description) {
}
