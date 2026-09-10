package com.example.stardust_springboot.auth.service;

import com.example.stardust_springboot.auth.dto.AuthResponse;
import com.example.stardust_springboot.auth.dto.LoginRequest;
import com.example.stardust_springboot.auth.dto.RegisterRequest;
import com.example.stardust_springboot.auth.security.ApiAuthenticationException;
import com.example.stardust_springboot.auth.security.AuthenticatedUser;
import com.example.stardust_springboot.auth.security.LoginAttemptGuard;
import com.example.stardust_springboot.auth.security.IssuedAccessToken;
import com.example.stardust_springboot.auth.security.JwtTokenService;
import com.example.stardust_springboot.auth.security.UserSecurityService;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.ratelimit.EndpointRateGuard;
import com.example.stardust_springboot.config.StorageProperties;
import com.example.stardust_springboot.file.entity.UserStorageUsage;
import com.example.stardust_springboot.file.repository.UserStorageUsageRepository;
import com.example.stardust_springboot.user.dto.ChangePasswordRequest;
import com.example.stardust_springboot.user.dto.UserView;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.entity.Role;
import com.example.stardust_springboot.user.entity.UserRole;
import com.example.stardust_springboot.user.entity.UserStatus;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import com.example.stardust_springboot.user.repository.RoleRepository;
import com.example.stardust_springboot.user.repository.UserRoleRepository;
import com.example.stardust_springboot.usage.service.AiUsageService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.text.Normalizer;
import java.util.Locale;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserSecurityService userSecurityService;
    private final JwtTokenService jwtTokenService;
    private final UserStorageUsageRepository storageUsageRepository;
    private final AiUsageService usageService;
    private final LoginAttemptGuard loginAttempts;
    private final EndpointRateGuard endpointGuards;
    private final long defaultStorageQuotaBytes;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(AppUserRepository userRepository, RoleRepository roleRepository,
                       UserRoleRepository userRoleRepository, PasswordEncoder passwordEncoder,
                       RefreshTokenService refreshTokenService, UserSecurityService userSecurityService,
                       JwtTokenService jwtTokenService, UserStorageUsageRepository storageUsageRepository,
                       AiUsageService usageService, LoginAttemptGuard loginAttempts,
                       EndpointRateGuard endpointGuards,
                       StorageProperties storageProperties, Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.userSecurityService = userSecurityService;
        this.jwtTokenService = jwtTokenService;
        this.storageUsageRepository = storageUsageRepository;
        this.usageService = usageService;
        this.loginAttempts = loginAttempts;
        this.endpointGuards = endpointGuards;
        this.defaultStorageQuotaBytes = storageProperties.defaultQuotaBytes();
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("not-a-real-stardust-account-password");
    }

    @Transactional
    public IssuedAuthSession register(RegisterRequest request, String clientIp) {
        endpointGuards.guardRegister(clientIp);
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailNormalized(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        Role userRole = roleRepository.findByCode("USER")
                .orElseThrow(() -> new IllegalStateException("Built-in USER role is missing"));
        try {
            AppUser user = userRepository.saveAndFlush(new AppUser(
                    email, passwordEncoder.encode(request.password()), request.displayName().trim()));
            userRoleRepository.saveAndFlush(new UserRole(user, userRole, null));
            storageUsageRepository.saveAndFlush(new UserStorageUsage(user, defaultStorageQuotaBytes));
            usageService.createAccount(user);
            return issueSession(userSecurityService.loadActiveUser(user.getPublicId()), user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    @Transactional
    public IssuedAuthSession login(LoginRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        loginAttempts.verifyNotBlocked(clientIp, email);
        AppUser user = userRepository.findByEmailNormalizedAndDeletedAtIsNull(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            loginAttempts.recordFailure(clientIp, email);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttempts.recordFailure(clientIp, email);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        validateStatusForApi(user);
        loginAttempts.reset(clientIp, email);
        user.recordLogin(clock.instant());
        userRepository.saveAndFlush(user);
        return issueSession(userSecurityService.loadActiveUser(user.getPublicId()), user);
    }

    public IssuedAuthSession refresh(String rawRefreshToken, String clientIp) {
        endpointGuards.guardRefresh(clientIp);
        RefreshSession refreshSession = refreshTokenService.rotate(rawRefreshToken);
        AppUser user = refreshSession.user();
        try {
            validateStatusForApi(user);
            AuthenticatedUser authenticatedUser = userSecurityService.loadActiveUser(user.getPublicId());
            return issueSessionWithToken(authenticatedUser, refreshSession.rawToken());
        } catch (BusinessException | ApiAuthenticationException exception) {
            refreshTokenService.revokeAll(user.getId());
            throw exception instanceof BusinessException businessException
                    ? businessException
                    : new BusinessException(((ApiAuthenticationException) exception).getErrorCode());
        }
    }

    @Transactional
    public IssuedAuthSession changePassword(AuthenticatedUser principal, ChangePasswordRequest request) {
        AppUser user = userRepository.findById(principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        validateStatusForApi(user);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_INVALID);
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()), clock.instant());
        userRepository.saveAndFlush(user);
        refreshTokenService.revokeAll(user.getId());
        return issueSession(userSecurityService.loadActiveUser(user.getPublicId()), user);
    }

    public void logout(String rawRefreshToken, AuthenticatedUser principal) {
        refreshTokenService.revokeCurrent(rawRefreshToken, principal.id());
    }

    public void logoutAll(AuthenticatedUser principal) {
        refreshTokenService.revokeAll(principal.id());
    }

    private IssuedAuthSession issueSession(AuthenticatedUser authenticatedUser, AppUser user) {
        RefreshSession refreshSession = refreshTokenService.create(user);
        return issueSessionWithToken(authenticatedUser, refreshSession.rawToken());
    }

    private IssuedAuthSession issueSessionWithToken(AuthenticatedUser user, String rawRefreshToken) {
        IssuedAccessToken accessToken = jwtTokenService.issue(user);
        AuthResponse response = new AuthResponse(accessToken.value(), "Bearer",
                jwtTokenService.expiresInSeconds(accessToken), UserView.from(user));
        return new IssuedAuthSession(response, rawRefreshToken);
    }

    private void validateStatusForApi(AppUser user) {
        if (user.getStatus() == UserStatus.BANNED) {
            throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
        }
        if (user.getStatus() != UserStatus.NORMAL || user.isDeleted()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
    }

    private String normalizeEmail(String email) {
        return Normalizer.normalize(email, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
