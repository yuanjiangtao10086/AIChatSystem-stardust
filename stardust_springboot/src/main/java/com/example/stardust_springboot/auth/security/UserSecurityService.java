package com.example.stardust_springboot.auth.security;

import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.entity.UserStatus;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import com.example.stardust_springboot.user.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class UserSecurityService {

    private final AppUserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public UserSecurityService(AppUserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser loadActiveUser(String publicId) {
        AppUser user = userRepository.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ApiAuthenticationException(ErrorCode.TOKEN_INVALID));
        validateStatus(user);
        Set<String> roles = expandRoleHierarchy(
                new LinkedHashSet<>(userRoleRepository.findEnabledRoleCodesByUserId(user.getId())));
        return new AuthenticatedUser(user.getId(), user.getPublicId(), user.getEmailNormalized(),
                user.getDisplayName(), user.getAuthVersion(), roles);
    }

    public void validateStatus(AppUser user) {
        if (user.getStatus() == UserStatus.BANNED) {
            throw new ApiAuthenticationException(ErrorCode.ACCOUNT_BANNED);
        }
        if (user.getStatus() != UserStatus.NORMAL || user.isDeleted()) {
            throw new ApiAuthenticationException(ErrorCode.ACCOUNT_DISABLED);
        }
    }

    private Set<String> expandRoleHierarchy(Set<String> roles) {
        Set<String> expanded = new LinkedHashSet<>();
        if (roles.contains("SUPER_ADMIN")) {
            expanded.add("SUPER_ADMIN");
        }
        if (roles.contains("SUPER_ADMIN") || roles.contains("ADMIN")) {
            expanded.add("ADMIN");
        }
        if (roles.contains("USER") || roles.contains("ADMIN") || roles.contains("SUPER_ADMIN")) {
            expanded.add("USER");
        }
        return java.util.Collections.unmodifiableSet(expanded);
    }
}
