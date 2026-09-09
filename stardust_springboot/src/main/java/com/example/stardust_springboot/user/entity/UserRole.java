package com.example.stardust_springboot.user.entity;

import com.example.stardust_springboot.common.persistence.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "app_user_role",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_role", columnNames = {"user_id", "role_id"}),
        indexes = @Index(name = "idx_user_role_role_user", columnList = "role_id, user_id"))
public class UserRole extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by")
    private AppUser grantedBy;

    protected UserRole() {
    }

    public UserRole(AppUser user, Role role, AppUser grantedBy) {
        this.user = user;
        this.role = role;
        this.grantedBy = grantedBy;
    }

    public Role getRole() {
        return role;
    }

    public AppUser getUser() { return user; }
}
