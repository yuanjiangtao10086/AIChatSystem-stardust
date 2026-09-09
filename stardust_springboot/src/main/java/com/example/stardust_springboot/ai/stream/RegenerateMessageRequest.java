package com.example.stardust_springboot.ai.stream;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegenerateMessageRequest(
        @NotBlank @Size(max = 26) String modelId
) {
}
