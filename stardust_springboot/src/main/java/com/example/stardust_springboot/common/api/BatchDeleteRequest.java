package com.example.stardust_springboot.common.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request body for a batch delete endpoint: the public ids to remove. */
public record BatchDeleteRequest(
        @NotNull @Size(min = 1, max = 200) List<String> ids
) {
}
