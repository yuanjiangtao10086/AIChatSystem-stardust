package com.example.stardust_springboot.common.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageRequest(
        @Min(value = 0, message = "page must be greater than or equal to 0") Integer page,
        @Min(value = 1, message = "size must be greater than or equal to 1")
        @Max(value = 100, message = "size must be less than or equal to 100") Integer size
) {
    public PageRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }

    public org.springframework.data.domain.PageRequest toSpringPageRequest() {
        return org.springframework.data.domain.PageRequest.of(page, size);
    }
}
