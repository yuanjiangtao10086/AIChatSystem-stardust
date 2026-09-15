package com.example.stardust_springboot.common.api;

import java.util.List;

/**
 * Outcome of a batch delete operation. Deletion is attempted for every id; individual failures are
 * collected (with a machine code and human reason) instead of aborting the whole batch, so callers
 * can show "N deleted, M failed" without re-running per id.
 */
public record BatchDeleteResult(long deleted, List<BatchDeleteFailure> failures) {
    public record BatchDeleteFailure(String id, String code, String reason) {}

    public static BatchDeleteResult of(long deleted, List<BatchDeleteFailure> failures) {
        return new BatchDeleteResult(deleted, List.copyOf(failures));
    }

    public boolean allSucceeded() {
        return failures.isEmpty();
    }
}
