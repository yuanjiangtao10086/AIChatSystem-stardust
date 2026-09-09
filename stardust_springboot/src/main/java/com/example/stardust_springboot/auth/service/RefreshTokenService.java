package com.example.stardust_springboot.auth.service;

import com.example.stardust_springboot.auth.entity.RefreshToken;
import com.example.stardust_springboot.auth.entity.RefreshTokenStatus;
import com.example.stardust_springboot.auth.repository.RefreshTokenRepository;
import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.id.PublicIdGenerator;
import com.example.stardust_springboot.config.SecurityProperties;
import com.example.stardust_springboot.user.entity.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public RefreshSession create(AppUser user) {
        return create(user, PublicIdGenerator.newUlid());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public RefreshSession rotate(String rawToken) {
        RefreshToken current = repository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        Instant now = clock.instant();
        if (current.getStatus() == RefreshTokenStatus.ROTATED
                || current.getStatus() == RefreshTokenStatus.REUSED) {
            current.markReused(now);
            repository.revokeActiveFamily(current.getFamilyId(), now);
            repository.saveAndFlush(current);
            throw new RefreshTokenReuseException();
        }
        if (current.getStatus() != RefreshTokenStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!current.getExpiresAt().isAfter(now)) {
            current.expire(now);
            repository.saveAndFlush(current);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        RefreshSession replacementSession = create(current.getUser(), current.getFamilyId());
        RefreshToken replacement = repository.findByTokenHashForUpdate(hash(replacementSession.rawToken()))
                .orElseThrow(() -> new IllegalStateException("Created refresh token was not persisted"));
        current.rotateTo(replacement, now);
        repository.saveAndFlush(current);
        return replacementSession;
    }

    @Transactional
    public void revokeCurrent(String rawToken, Long authenticatedUserId) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHashForUpdate(hash(rawToken)).ifPresent(token -> {
            if (token.getUser().getId().equals(authenticatedUserId)) {
                repository.revokeActiveFamily(token.getFamilyId(), clock.instant());
            }
        });
    }

    @Transactional
    public void revokeAll(Long userId) {
        repository.revokeAllActiveForUser(userId, clock.instant());
    }

    private RefreshSession create(AppUser user, String familyId) {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant now = clock.instant();
        RefreshToken token = new RefreshToken(user, familyId, hash(rawToken), now,
                now.plus(properties.refreshTokenTtl()));
        repository.saveAndFlush(token);
        return new RefreshSession(user, rawToken);
    }

    private String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.refreshTokenPepper().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to hash refresh token", exception);
        }
    }
}
