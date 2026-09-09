package com.example.stardust_springboot.user.entity;

import com.example.stardust_springboot.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_role")
public class Role extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoleStatus status = RoleStatus.ENABLED;

    protected Role() {
    }

    public Role(String code, String name, boolean builtIn) {
        this.code = code;
        this.name = name;
        this.builtIn = builtIn;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public RoleStatus getStatus() {
        return status;
    }
}
