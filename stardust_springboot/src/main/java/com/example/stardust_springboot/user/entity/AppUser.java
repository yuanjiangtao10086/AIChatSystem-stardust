package com.example.stardust_springboot.user.entity;

import com.example.stardust_springboot.common.persistence.SoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "app_user", indexes = {
        @Index(name = "idx_app_user_status_created", columnList = "status, created_at, id"),
        @Index(name = "idx_app_user_updated", columnList = "updated_at, id")
})
public class AppUser extends SoftDeleteEntity {

    @Column(name = "email_normalized", nullable = false, length = 320)
    private String emailNormalized;

    @Column(length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.NORMAL;

    @Column(name = "ban_reason", length = 500)
    private String banReason;

    @Column(name = "banned_until")
    private Instant bannedUntil;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    protected AppUser() {
    }

    public AppUser(String emailNormalized, String passwordHash, String displayName) {
        this.emailNormalized = emailNormalized;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public void softDelete() {
        status = UserStatus.DELETED;
        markDeletedAt(Instant.now());
    }

    public void updateProfile(String displayName) {
        this.displayName = displayName;
    }

    public void adminUpdate(String emailNormalized, String displayName) {
        this.emailNormalized = emailNormalized;
        this.displayName = displayName;
    }

    public void changePassword(String passwordHash, Instant changedAt) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = changedAt;
        this.authVersion++;
    }

    public void recordLogin(Instant loggedInAt) {
        this.lastLoginAt = loggedInAt;
    }

    public void changeStatus(UserStatus status, String banReason, Instant bannedUntil) {
        if (status == UserStatus.DELETED) {
            throw new IllegalArgumentException("Use softDelete() for deleted users");
        }
        this.status = status;
        this.banReason = status == UserStatus.BANNED ? banReason : null;
        this.bannedUntil = status == UserStatus.BANNED ? bannedUntil : null;
    }

    public void restore() {
        if (status != UserStatus.DELETED) {
            throw new IllegalStateException("Only deleted users can be restored");
        }
        clearDeletedAt();
        status = UserStatus.NORMAL;
        banReason = null;
        bannedUntil = null;
        authVersion++;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public long getAuthVersion() {
        return authVersion;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public String getBanReason() { return banReason; }
    public Instant getBannedUntil() { return bannedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
