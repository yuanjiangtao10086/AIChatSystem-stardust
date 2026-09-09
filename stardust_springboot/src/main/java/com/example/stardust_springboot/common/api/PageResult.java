package com.example.stardust_springboot.common.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResult<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public PageResult {
        items = List.copyOf(items);
    }

    public static <T> PageResult<T> from(Page<T> source) {
        return new PageResult<>(source.getContent(), source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages(), source.hasNext());
    }
}
