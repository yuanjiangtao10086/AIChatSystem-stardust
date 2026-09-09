package com.example.stardust_springboot.knowledge.entity;

import com.example.stardust_springboot.common.persistence.SoftDeleteEntity;
import com.example.stardust_springboot.user.entity.AppUser;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "knowledge_base")
public class KnowledgeBase extends SoftDeleteEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KnowledgeBaseStatus status = KnowledgeBaseStatus.ACTIVE;

    protected KnowledgeBase() {
    }

    public KnowledgeBase(AppUser user, String name, String description) {
        this.user = user;
        this.name = name;
        this.description = description;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void softDelete() {
        status = KnowledgeBaseStatus.DELETED;
        markDeletedAt(Instant.now());
    }

    public AppUser getUser() { return user; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public KnowledgeBaseStatus getStatus() { return status; }
}
