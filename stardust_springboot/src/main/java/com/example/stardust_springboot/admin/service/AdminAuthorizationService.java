package com.example.stardust_springboot.admin.service;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.repository.UserRoleRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AdminAuthorizationService {
    private final UserRoleRepository userRoles;

    public AdminAuthorizationService(UserRoleRepository userRoles) { this.userRoles = userRoles; }

    public void requireCanManage(AuthenticatedUser actor, AppUser target) {
        if (actor.id().equals(target.getId())) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (rank(actor.roles()) <= rank(Set.copyOf(userRoles.findEnabledRoleCodesByUserId(target.getId())))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * Read authorization for viewing/moderating another user's private resources.
     * An actor that is not a SUPER_ADMIN may not open, read or delete the private
     * resources (conversations / messages) of a SUPER_ADMIN; SUPER_ADMIN may access everything.
     */
    public void requireCanView(AuthenticatedUser actor, AppUser target) {
        if (actor.roles().contains("SUPER_ADMIN")) return;
        Set<String> targetRoles = Set.copyOf(userRoles.findEnabledRoleCodesByUserId(target.getId()));
        if (targetRoles.contains("SUPER_ADMIN")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    public void validateRoleAssignment(AuthenticatedUser actor, Set<String> roles) {
        if (!Set.of("USER", "ADMIN", "SUPER_ADMIN").containsAll(roles) || !roles.contains("USER")) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION);
        }
        if (!actor.roles().contains("SUPER_ADMIN") && !roles.equals(Set.of("USER"))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private int rank(Set<String> roles) {
        if (roles.contains("SUPER_ADMIN")) return 3;
        if (roles.contains("ADMIN")) return 2;
        return 1;
    }
}
