package com.example.stardust_springboot.auth.security;

import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.id.PublicIdGenerator;
import com.example.stardust_springboot.config.SecurityProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtTokenService {

    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String AUTH_VERSION_CLAIM = "av";

    private final SecurityProperties properties;
    private final Clock clock;
    private final byte[] secret;

    public JwtTokenService(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        try {
            this.secret = Base64.getDecoder().decode(properties.jwtSecretBase64());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT secret must be valid Base64", exception);
        }
        if (secret.length < 32) {
            throw new IllegalStateException("JWT secret must decode to at least 32 bytes");
        }
    }

    public IssuedAccessToken issue(AuthenticatedUser user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.jwtIssuer())
                .subject(user.publicId())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(PublicIdGenerator.newUlid())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .claim(AUTH_VERSION_CLAIM, user.authVersion())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException exception) {
            throw new IllegalStateException("Unable to sign access token", exception);
        }
        return new IssuedAccessToken(jwt.serialize(), expiresAt);
    }

    public AccessTokenClaims parseAndValidate(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new MACVerifier(secret))) {
                throw new ApiAuthenticationException(ErrorCode.TOKEN_INVALID);
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Instant now = clock.instant();
            Date expiresAt = claims.getExpirationTime();
            if (expiresAt == null || !expiresAt.toInstant().isAfter(now)) {
                throw new ApiAuthenticationException(ErrorCode.TOKEN_EXPIRED);
            }
            if (!properties.jwtIssuer().equals(claims.getIssuer())
                    || !ACCESS_TOKEN_TYPE.equals(claims.getStringClaim(TOKEN_TYPE_CLAIM))
                    || claims.getSubject() == null
                    || claims.getIssueTime() == null) {
                throw new ApiAuthenticationException(ErrorCode.TOKEN_INVALID);
            }
            Object authVersionClaim = claims.getClaim(AUTH_VERSION_CLAIM);
            if (!(authVersionClaim instanceof Number authVersion)) {
                throw new ApiAuthenticationException(ErrorCode.TOKEN_INVALID);
            }
            return new AccessTokenClaims(claims.getSubject(), authVersion.longValue(),
                    claims.getIssueTime().toInstant(), expiresAt.toInstant());
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw new ApiAuthenticationException(ErrorCode.TOKEN_INVALID);
        }
    }

    public long expiresInSeconds(IssuedAccessToken token) {
        return Math.max(0, token.expiresAt().getEpochSecond() - clock.instant().getEpochSecond());
    }
}
