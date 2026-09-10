package com.example.stardust_springboot.ai.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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

    @Query("select request.requestId from AiRequestLog request where request.status in ("
            + "com.example.stardust_springboot.ai.request.AiRequestStatus.PENDING,"
            + "com.example.stardust_springboot.ai.request.AiRequestStatus.STREAMING)")
    List<String> findInterruptedRequestIds();

    @Query("select coalesce(sum(request.totalTokens), 0) from AiRequestLog request")
    long sumTotalTokens();

    @Query("select coalesce(sum(request.totalTokens), 0) from AiRequestLog request where request.user.id = :userId")
    long sumTotalTokensByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);
    long countByStatusAndCreatedAtGreaterThanEqual(AiRequestStatus status, Instant since);

    /** Guards provider/model deletion: request logs reference them with {@code ON DELETE RESTRICT}. */
    long countByProviderId(Long providerId);

    long countByModelId(Long modelId);

    @Query("""
            select request from AiRequestLog request
            where (:userId is null or request.user.publicId = :userId)
              and (:status is null or request.status = :status)
              and (:provider is null or request.providerCode = :provider)
              and (:model is null or request.modelCode = :model)
              and (:from is null or request.createdAt >= :from)
              and (:to is null or request.createdAt <= :to)
            """)
    Page<AiRequestLog> findAdmin(@Param("userId") String userId,
                                 @Param("status") AiRequestStatus status,
                                 @Param("provider") String provider,
                                 @Param("model") String model,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 Pageable pageable);

    /**
     * Compact projection for the dashboard trend: only the two columns needed to bucket the last
     * 24 hours in Java, so neither MySQL nor H2 needs a vendor-specific hour() expression.
     */
    @Query("select request.createdAt, request.status from AiRequestLog request where request.createdAt >= :from")
    List<Object[]> timestampsSince(@Param("from") Instant from);

    /** Newest failed calls for the dashboard, so an operator sees breakage without opening the log. */
    List<AiRequestLog> findByStatusOrderByCreatedAtDescIdDesc(AiRequestStatus status, Pageable pageable);

    @Query("select cast(request.createdAt as LocalDate), count(request), coalesce(sum(request.promptTokens),0L), "
            + "coalesce(sum(request.completionTokens),0L), coalesce(sum(request.totalTokens),0L) "
            + "from AiRequestLog request where request.user.id = :userId "
            + "and request.status = com.example.stardust_springboot.ai.request.AiRequestStatus.COMPLETED "
            + "and request.createdAt between :from and :to group by cast(request.createdAt as LocalDate) order by 1")
    List<Object[]> breakdownByDayForUser(@Param("userId") Long userId,
                                         @Param("from") Instant from, @Param("to") Instant to);

    @Query("select cast(request.createdAt as LocalDate), count(request), coalesce(sum(request.promptTokens),0L), "
            + "coalesce(sum(request.completionTokens),0L), coalesce(sum(request.totalTokens),0L) "
            + "from AiRequestLog request where request.status = com.example.stardust_springboot.ai.request.AiRequestStatus.COMPLETED "
            + "and request.createdAt between :from and :to group by cast(request.createdAt as LocalDate) order by 1")
    List<Object[]> breakdownByDayGlobal(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select request.modelCode, count(request), coalesce(sum(request.promptTokens),0L), "
            + "coalesce(sum(request.completionTokens),0L), coalesce(sum(request.totalTokens),0L) "
            + "from AiRequestLog request where request.user.id = :userId "
            + "and request.status = com.example.stardust_springboot.ai.request.AiRequestStatus.COMPLETED "
            + "and request.createdAt between :from and :to group by request.modelCode order by 3 desc")
    List<Object[]> breakdownByModelForUser(@Param("userId") Long userId,
                                            @Param("from") Instant from, @Param("to") Instant to);

    @Query("select request.modelCode, count(request), coalesce(sum(request.promptTokens),0L), "
            + "coalesce(sum(request.completionTokens),0L), coalesce(sum(request.totalTokens),0L) "
            + "from AiRequestLog request where request.status = com.example.stardust_springboot.ai.request.AiRequestStatus.COMPLETED "
            + "and request.createdAt between :from and :to group by request.modelCode order by 3 desc")
    List<Object[]> breakdownByModelGlobal(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select request.providerCode, count(request), coalesce(sum(request.promptTokens),0L), "
            + "coalesce(sum(request.completionTokens),0L), coalesce(sum(request.totalTokens),0L) "
            + "from AiRequestLog request where request.user.id = :userId "
            + "and request.status = com.example.stardust_springboot.ai.request.AiRequestStatus.COMPLETED "
            + "and request.createdAt between :from and :to group by request.providerCode order by 3 desc")
    List<Object[]> breakdownByProviderForUser(@Param("userId") Long userId,
                                              @Param("from") Instant from, @Param("to") Instant to);

    @Query("select request.providerCode, count(request), coalesce(sum(request.promptTokens),0L), "
            + "coalesce(sum(request.completionTokens),0L), coalesce(sum(request.totalTokens),0L) "
            + "from AiRequestLog request where request.status = com.example.stardust_springboot.ai.request.AiRequestStatus.COMPLETED "
            + "and request.createdAt between :from and :to group by request.providerCode order by 3 desc")
    List<Object[]> breakdownByProviderGlobal(@Param("from") Instant from, @Param("to") Instant to);
}
