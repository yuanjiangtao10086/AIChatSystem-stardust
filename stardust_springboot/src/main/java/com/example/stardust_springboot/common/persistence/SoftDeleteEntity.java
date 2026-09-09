package com.example.stardust_springboot.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.time.Instant;

@MappedSuperclass
public abstract class SoftDeleteEntity extends PublicIdEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    protected void markDeletedAt(Instant deletedAt) {
        if (deletedAt == null) {
            throw new IllegalArgumentException("deletedAt must not be null");
        }
        this.deletedAt = deletedAt;
    }

    protected void clearDeletedAt() {
        this.deletedAt = null;
    }
}
