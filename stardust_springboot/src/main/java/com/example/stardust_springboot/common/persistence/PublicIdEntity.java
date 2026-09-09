package com.example.stardust_springboot.common.persistence;

import com.example.stardust_springboot.common.id.PublicIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

@MappedSuperclass
public abstract class PublicIdEntity extends BaseEntity {

    @Column(name = "public_id", nullable = false, updatable = false, length = 26,
            columnDefinition = "CHAR(26)")
    private String publicId;

    @PrePersist
    protected void assignPublicId() {
        if (publicId == null) {
            publicId = PublicIdGenerator.newUlid();
        }
    }

    public String getPublicId() {
        return publicId;
    }
}
