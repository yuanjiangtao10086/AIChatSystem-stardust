package com.example.stardust_springboot.admin.audit;

import com.example.stardust_springboot.common.persistence.PublicIdEntity;
import com.example.stardust_springboot.user.entity.AppUser;
import jakarta.persistence.*;

@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog extends PublicIdEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false, updatable = false)
    private AppUser admin;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 64)
    private AdminAuditAction action;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", updatable = false)
    private AppUser targetUser;
    @Column(name = "target_resource_type", nullable = false, updatable = false, length = 64)
    private String targetResourceType;
    @Column(name = "target_resource_id", updatable = false, length = 64)
    private String targetResourceId;
    @Column(nullable = false, updatable = false, length = 45)
    private String ip;
    @Column(name = "user_agent", updatable = false, length = 500)
    private String userAgent;
    @Column(name = "request_id", nullable = false, updatable = false, length = 64)
    private String requestId;
    @Column(name = "metadata_json", updatable = false, columnDefinition = "TEXT")
    private String metadataJson;

    protected AdminAuditLog() {}

    public AdminAuditLog(AppUser admin, AdminAuditAction action, AppUser targetUser,
                         String resourceType, String resourceId, String ip,
                         String userAgent, String requestId, String metadataJson) {
        this.admin = admin;
        this.action = action;
        this.targetUser = targetUser;
        this.targetResourceType = resourceType;
        this.targetResourceId = resourceId;
        this.ip = ip;
        this.userAgent = userAgent;
        this.requestId = requestId;
        this.metadataJson = metadataJson;
    }

    public AppUser getAdmin() { return admin; }
    public AdminAuditAction getAction() { return action; }
    public AppUser getTargetUser() { return targetUser; }
    public String getTargetResourceType() { return targetResourceType; }
    public String getTargetResourceId() { return targetResourceId; }
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public String getRequestId() { return requestId; }
    public String getMetadataJson() { return metadataJson; }
}
