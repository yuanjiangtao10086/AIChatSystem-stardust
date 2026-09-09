package com.example.stardust_springboot.admin.audit;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.logging.RequestContext;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditService {
    private final AdminAuditLogRepository logs;
    private final AppUserRepository users;
    private final HttpServletRequest request;

    public AdminAuditService(AdminAuditLogRepository logs, AppUserRepository users,
                             HttpServletRequest request) {
        this.logs = logs;
        this.users = users;
        this.request = request;
    }

    public void record(AuthenticatedUser principal,
                       AdminAuditAction action, AppUser targetUser,
                       String resourceType, String resourceId, String metadataJson) {
        AppUser admin = users.findById(principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        String requestId = RequestContext.requestId();
        logs.save(new AdminAuditLog(admin, action, targetUser, resourceType, resourceId,
                truncate(request.getRemoteAddr(), 45), truncate(request.getHeader("User-Agent"), 500),
                requestId == null ? "unknown" : truncate(requestId, 64), metadataJson));
    }

    private String truncate(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
