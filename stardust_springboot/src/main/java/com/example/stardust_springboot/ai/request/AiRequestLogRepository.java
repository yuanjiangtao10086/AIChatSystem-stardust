package com.example.stardust_springboot.ai.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, Long> {
    Optional<AiRequestLog> findByRequestId(String requestId);

    Optional<AiRequestLog> findByRequestIdAndUserId(String requestId, Long userId);

    @Modifying
    @Query("""
            update AiRequestLog request
            set request.status = com.example.stardust_springboot.ai.request.AiRequestStatus.FAILED,
                request.errorCode = 'SERVER_RESTART',
                request.completedAt = :now,
                request.updatedAt = :now
            where request.status in (
                com.example.stardust_springboot.ai.request.AiRequestStatus.PENDING,
                com.example.stardust_springboot.ai.request.AiRequestStatus.STREAMING)
            """)
    int failInterruptedRequests(@Param("now") Instant now);

    @Query("select coalesce(sum(request.totalTokens), 0) from AiRequestLog request")
    long sumTotalTokens();

    @Query("select coalesce(sum(request.totalTokens), 0) from AiRequestLog request where request.user.id = :userId")
    long sumTotalTokensByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);
    long countByStatusAndCreatedAtGreaterThanEqual(AiRequestStatus status, Instant since);

    @Query("""
            select request from AiRequestLog request
            where (:userId is null or request.user.publicId = :userId)
              and (:status is null or request.status = :status)
              and (:provider is null or request.providerCode = :provider)
              and (:model is null or request.modelCode = :model)
            """)
    Page<AiRequestLog> findAdmin(@Param("userId") String userId,
                                 @Param("status") AiRequestStatus status,
                                 @Param("provider") String provider,
                                 @Param("model") String model,
                                 Pageable pageable);
}
