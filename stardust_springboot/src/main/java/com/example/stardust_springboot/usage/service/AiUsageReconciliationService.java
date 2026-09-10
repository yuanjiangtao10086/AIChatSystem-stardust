package com.example.stardust_springboot.usage.service;

import com.example.stardust_springboot.ai.request.AiRequestLog;
import com.example.stardust_springboot.ai.request.AiRequestLogRepository;
import com.example.stardust_springboot.ai.request.AiRequestStatus;
import com.example.stardust_springboot.config.AiUsageProperties;
import com.example.stardust_springboot.usage.entity.AiUsageAccount;
import com.example.stardust_springboot.usage.entity.AiUsageLedger;
import com.example.stardust_springboot.usage.entity.UsageLedgerEntryType;
import com.example.stardust_springboot.usage.repository.AiUsageAccountRepository;
import com.example.stardust_springboot.usage.repository.AiUsageLedgerRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Periodic AI usage reconciliation.
 *
 * <p>Two independent jobs run in one pass:
 * <ol>
 *   <li>Sweep: reservations without a terminal ledger entry are resolved from the request log fact —
 *       missing or {@code FAILED/STOPPED} requests are released, {@code COMPLETED} requests are settled
 *       with their recorded token counts. {@code PENDING/STREAMING} requests are left alone because the
 *       in-flight worker still owns them.</li>
 *   <li>Verify: compare {@code used + reserved} against the ledger sum of the current period and report
 *       drift. Drift is logged only; it is never auto-corrected, because silently rewriting balances
 *       would hide the bug that produced it.</li>
 * </ol>
 */
@Service
public class AiUsageReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(AiUsageReconciliationService.class);

    private final AiUsageAccountRepository accounts;
    private final AiUsageLedgerRepository ledger;
    private final AiRequestLogRepository requestLogs;
    private final AiUsageService usageService;
    private final AiUsageProperties properties;
    private final Clock clock;
    private final Timer reconcileTimer;
    private final Counter releasedCounter;
    private final Counter driftCounter;

    public AiUsageReconciliationService(AiUsageAccountRepository accounts, AiUsageLedgerRepository ledger,
                                        AiRequestLogRepository requestLogs, AiUsageService usageService,
                                        AiUsageProperties properties, Clock clock, MeterRegistry meter) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.requestLogs = requestLogs;
        this.usageService = usageService;
        this.properties = properties;
        this.clock = clock;
        this.reconcileTimer = meter.timer("ai_usage_reconcile_duration_seconds");
        this.releasedCounter = meter.counter("ai_usage_reconcile_released_total");
        this.driftCounter = meter.counter("ai_usage_reconcile_drift_total");
    }

    @Transactional
    @Scheduled(initialDelayString = "${app.ai.usage.reconcile-initial-delay-ms:60000}",
            fixedDelayString = "${app.ai.usage.reconcile-interval-ms:3600000}")
    public Report reconcile() {
        return reconcileTimer.record(() -> {
            int released = sweepDanglingReservations();
            List<String> drift = verifyAccountBalances();
            releasedCounter.increment(released);
            driftCounter.increment(drift.size());
            if (released > 0 || !drift.isEmpty()) {
                log.warn("AI usage reconciliation completed released={} driftedAccounts={} details={}",
                        released, drift.size(), String.join(" | ", drift));
            } else {
                log.info("AI usage reconciliation completed released=0 driftedAccounts=0");
            }
            return new Report(released, drift.size(), List.copyOf(drift));
        });
    }

    private int sweepDanglingReservations() {
        Instant since = clock.instant().minus(Duration.ofHours(Math.max(1, properties.reserveSweepWindowHours())));
        int released = 0;
        for (AiUsageLedger reservation : ledger.findByEntryTypeAndCreatedAtGreaterThanEqualOrderByIdAsc(
                UsageLedgerEntryType.RESERVE, since)) {
            String requestId = reservation.getAiRequestId();
            if (requestId == null
                    || ledger.existsByAiRequestIdAndOperationKey(requestId, AiUsageService.KEY_SETTLE)
                    || ledger.existsByAiRequestIdAndOperationKey(requestId, AiUsageService.KEY_RELEASE)) {
                continue;
            }
            Optional<AiRequestLog> request = requestLogs.findByRequestId(requestId);
            if (request.isEmpty()) {
                usageService.release(requestId);
                released++;
                continue;
            }
            AiRequestLog log = request.get();
            if (log.getStatus() == AiRequestStatus.PENDING || log.getStatus() == AiRequestStatus.STREAMING) {
                continue;
            }
            if (log.getStatus() == AiRequestStatus.COMPLETED) {
                usageService.settle(requestId, orZero(log.getPromptTokens()), orZero(log.getCompletionTokens()),
                        log.getModel().getInputPrice(), log.getModel().getOutputPrice());
            } else {
                usageService.release(requestId);
            }
            released++;
        }
        return released;
    }

    private List<String> verifyAccountBalances() {
        List<String> drift = new ArrayList<>();
        for (AiUsageAccount account : accounts.findAll()) {
            long committed = account.getUsedTokens() + account.getReservedTokens();
            BigDecimal committedCost = account.getUsedCost().add(account.getReservedCost());
            long ledgerTokens = ledger.sumTokenDeltaSince(account.getUser().getId(), account.getPeriodStart());
            BigDecimal ledgerCost = ledger.sumCostDeltaSince(account.getUser().getId(), account.getPeriodStart());
            if (committed != ledgerTokens || committedCost.compareTo(ledgerCost) != 0) {
                drift.add("userId=" + account.getUser().getPublicId()
                        + " account=" + committed + "/" + committedCost.toPlainString()
                        + " ledger=" + ledgerTokens + "/" + ledgerCost.toPlainString()
                        + " currency=" + account.getCurrency());
            }
        }
        return drift;
    }

    private long orZero(Long value) {
        return value == null ? 0L : value;
    }

    public record Report(int releasedReservations, int driftedAccounts, List<String> driftDetails) {
    }
}
