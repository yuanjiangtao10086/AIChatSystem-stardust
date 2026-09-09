package com.example.stardust_springboot.auth.repository;

import com.example.stardust_springboot.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token join fetch token.user where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken token set token.status = com.example.stardust_springboot.auth.entity.RefreshTokenStatus.REVOKED,
                token.revokedAt = :now
            where token.familyId = :familyId
              and token.status = com.example.stardust_springboot.auth.entity.RefreshTokenStatus.ACTIVE
            """)
    int revokeActiveFamily(@Param("familyId") String familyId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update RefreshToken token set token.status = com.example.stardust_springboot.auth.entity.RefreshTokenStatus.REVOKED,
                token.revokedAt = :now
            where token.user.id = :userId
              and token.status = com.example.stardust_springboot.auth.entity.RefreshTokenStatus.ACTIVE
            """)
    int revokeAllActiveForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
