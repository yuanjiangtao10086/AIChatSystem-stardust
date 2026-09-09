package com.example.stardust_springboot.auth.entity;

import com.example.stardust_springboot.common.persistence.PublicIdEntity;
import com.example.stardust_springboot.user.entity.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "refresh_token", indexes = {
        @Index(name = "idx_refresh_token_user_status_expires", columnList = "user_id, status, expires_at, id"),
        @Index(name = "idx_refresh_token_family_status", columnList = "family_id, status, id"),
        @Index(name = "idx_refresh_token_expires", columnList = "status, expires_at, id"),
        @Index(name = "idx_refresh_token_updated", columnList = "updated_at, id")
})
public class RefreshToken extends PublicIdEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "family_id", nullable = false, length = 26, columnDefinition = "CHAR(26)")
    private String familyId;

    @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RefreshTokenStatus status = RefreshTokenStatus.ACTIVE;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id")
    private RefreshToken replacedBy;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected RefreshToken() {
    }

    public RefreshToken(AppUser user, String familyId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        this.user = user;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public void rotateTo(RefreshToken replacement, Instant usedAt) {
        this.status = RefreshTokenStatus.ROTATED;
        this.rotatedAt = usedAt;
        this.lastUsedAt = usedAt;
        this.replacedBy = replacement;
    }

    public void markReused(Instant usedAt) {
        this.status = RefreshTokenStatus.REUSED;
        this.rotatedAt = this.rotatedAt == null ? usedAt : this.rotatedAt;
        this.lastUsedAt = usedAt;
    }

    public void revoke(Instant revokedAt) {
        if (status == RefreshTokenStatus.ACTIVE) {
            status = RefreshTokenStatus.REVOKED;
            this.revokedAt = revokedAt;
        }
    }

    public void expire(Instant expiredAt) {
        if (status == RefreshTokenStatus.ACTIVE) {
            status = RefreshTokenStatus.EXPIRED;
            this.revokedAt = expiredAt;
        }
    }

    public AppUser getUser() {
        return user;
    }

    public String getFamilyId() {
        return familyId;
    }

    public RefreshTokenStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
