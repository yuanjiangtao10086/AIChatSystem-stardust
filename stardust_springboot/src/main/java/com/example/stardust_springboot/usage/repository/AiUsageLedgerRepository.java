package com.example.stardust_springboot.usage.repository;

import com.example.stardust_springboot.usage.entity.AiUsageLedger;
import com.example.stardust_springboot.usage.entity.UsageLedgerEntryType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Append-only repository. The interface deliberately exposes no delete or update method;
 * corrections are expressed as additional ledger entries.
 */
public interface AiUsageLedgerRepository extends Repository<AiUsageLedger, Long> {

    AiUsageLedger save(AiUsageLedger entry);

    Optional<AiUsageLedger> findByAiRequestIdAndOperationKey(String aiRequestId, String operationKey);

    boolean existsByAiRequestIdAndOperationKey(String aiRequestId, String operationKey);

    List<AiUsageLedger> findByAiRequestIdOrderByIdAsc(String aiRequestId);

    /** ADJUST rows carry no {@code ai_request_id}, so their key is unique on its own by convention. */
    boolean existsByOperationKey(String operationKey);

    List<AiUsageLedger> findByEntryTypeAndCreatedAtGreaterThanEqualOrderByIdAsc(UsageLedgerEntryType type,
                                                                                Instant since);

    @Query("select coalesce(sum(entry.tokenDelta), 0) from AiUsageLedger entry where entry.user.id = :userId")
    long sumTokenDeltaByUserId(@Param("userId") Long userId);

    @Query("select coalesce(sum(entry.tokenDelta), 0) from AiUsageLedger entry "
            + "where entry.user.id = :userId and entry.createdAt >= :since")
    long sumTokenDeltaSince(@Param("userId") Long userId, @Param("since") Instant since);

    @Query("select coalesce(sum(entry.costDelta), 0) from AiUsageLedger entry "
            + "where entry.user.id = :userId and entry.createdAt >= :since")
    BigDecimal sumCostDeltaSince(@Param("userId") Long userId, @Param("since") Instant since);
}
