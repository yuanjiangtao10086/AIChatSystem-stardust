package com.example.stardust_springboot.user.service;

import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.user.dto.UpdateProfileRequest;
import com.example.stardust_springboot.user.dto.UserView;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import com.example.stardust_springboot.user.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class UserService {

    private final AppUserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public UserService(AppUserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(readOnly = true)
    public UserView current(AuthenticatedUser principal) {
        AppUser user = findCurrent(principal);
        return UserView.from(user, expandedRoles(user.getId()));
    }

    @Transactional
    public UserView updateProfile(AuthenticatedUser principal, UpdateProfileRequest request) {
        AppUser user = findCurrent(principal);
        user.updateProfile(request.displayName().trim());
        userRepository.saveAndFlush(user);
        return UserView.from(user, expandedRoles(user.getId()));
    }

    public Set<String> roles(AuthenticatedUser principal) {
        return principal.roles();
    }

    private AppUser findCurrent(AuthenticatedUser principal) {
        return userRepository.findByPublicIdAndDeletedAtIsNull(principal.publicId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Set<String> expandedRoles(Long userId) {
        Set<String> roles = new LinkedHashSet<>(userRoleRepository.findEnabledRoleCodesByUserId(userId));
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
