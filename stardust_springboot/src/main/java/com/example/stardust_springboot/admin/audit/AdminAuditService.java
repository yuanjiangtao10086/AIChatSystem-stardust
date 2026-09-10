package com.example.stardust_springboot.admin.audit;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.logging.RequestContext;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Append-only writer for administrative audit rows.
 *
 * <p>Audit writes must never silently disappear: they run in the caller's transaction and a failure
 * is logged with the concrete resource coordinates (never the resource body) before it propagates,
 * so the caller's error can be traced back to the exact audited operation.
 */
@Service
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);
    private static final String UNKNOWN = "unknown";
    private static final int MAX_IP_LENGTH = 45;
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final int MAX_REQUEST_ID_LENGTH = 64;

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
        // `ip` is NOT NULL in the schema, so a blank client address must fall back to a placeholder
        // instead of becoming a null that fails the whole request with a generic 50002.
        String clientIp = truncate(request.getRemoteAddr(), MAX_IP_LENGTH);
        try {
            logs.save(new AdminAuditLog(admin, action, targetUser, resourceType, resourceId,
                    clientIp == null ? UNKNOWN : clientIp,
                    truncate(request.getHeader("User-Agent"), MAX_USER_AGENT_LENGTH),
                    requestId == null ? UNKNOWN : truncate(requestId, MAX_REQUEST_ID_LENGTH),
                    metadataJson));
        } catch (RuntimeException failure) {
            log.error("Admin audit write failed: action={} resourceType={} resourceId={} adminId={} requestId={} exceptionType={}",
                    action, resourceType, resourceId, principal.publicId(), requestId,
                    failure.getClass().getName(), failure);
            throw failure;
        }
    }

    /**
     * Truncates to the column length without splitting a surrogate pair, which would produce a
     * string MySQL rejects as invalid UTF-8.
     */
    private String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        int end = max;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end -= 1;
        }
        return value.substring(0, end);
    }
}
