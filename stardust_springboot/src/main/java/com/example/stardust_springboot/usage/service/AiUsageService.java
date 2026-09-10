package com.example.stardust_springboot.usage.service;

import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.config.AiUsageProperties;
import com.example.stardust_springboot.usage.dto.UsageView;
import com.example.stardust_springboot.usage.entity.AiUsageAccount;
import com.example.stardust_springboot.usage.entity.AiUsageLedger;
import com.example.stardust_springboot.usage.entity.UsageLedgerEntryType;
import com.example.stardust_springboot.usage.repository.AiUsageAccountRepository;
import com.example.stardust_springboot.usage.repository.AiUsageLedgerRepository;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * AI quota reservation and settlement.
 *
 * <p>{@code ai_usage_account} is the fast concurrency view; {@code ai_usage_ledger} is the immutable
 * reconciliation fact. A reservation is created before the upstream call and converted to real usage on
 * completion, or released when the request stops or fails.
 */
@Service
public class AiUsageService {
    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    /** Operation keys are part of the {@code UNIQUE(ai_request_id, operation_key)} idempotency constraint. */
    public static final String KEY_RESERVE = "RESERVE";
    public static final String KEY_SETTLE = "SETTLE";
    public static final String KEY_RELEASE = "RELEASE";
    /** Prefix for administrative adjustments; the caller appends an opaque unique suffix. */
    public static final String KEY_ADJUST_PREFIX = "ADJUST:";

    /** Model prices are expressed per 1_000 tokens; see ADR-050. */
    private static final BigDecimal PRICE_UNIT_TOKENS = BigDecimal.valueOf(1000L);

    private final AiUsageAccountRepository accounts;
    private final AiUsageLedgerRepository ledger;
    private final AppUserRepository users;
    private final AiUsageProperties properties;
    private final Clock clock;

    public AiUsageService(AiUsageAccountRepository accounts, AiUsageLedgerRepository ledger,
                          AppUserRepository users, AiUsageProperties properties, Clock clock) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void createAccount(AppUser user) {
        if (accounts.existsById(user.getId())) {
            return;
        }
        Instant now = clock.instant();
        accounts.saveAndFlush(new AiUsageAccount(user, properties.quotaTokens(), properties.quotaCost(),
                properties.currency(), now, periodEnd(now)));
    }

    /** Reserves quota before the upstream call. Idempotent per {@code aiRequestId}. */
    @Transactional
    public void reserve(Long userId, String aiRequestId, long promptTokens, long outputTokens,
                        BigDecimal inputPrice, BigDecimal outputPrice) {
        if (ledger.existsByAiRequestIdAndOperationKey(aiRequestId, KEY_RESERVE)) {
            return;
        }
        long tokens = Math.max(1, Math.max(0, promptTokens) + Math.max(0, outputTokens));
        BigDecimal cost = cost(promptTokens, inputPrice).add(cost(outputTokens, outputPrice));
        AiUsageAccount account = requireAccountForUpdate(userId);
        if (!account.canReserve(tokens, cost)) {
            throw new BusinessException(ErrorCode.AI_QUOTA_EXCEEDED);
        }
        account.reserve(tokens, cost);
        accounts.saveAndFlush(account);
        ledger.save(new AiUsageLedger(account.getUser(), aiRequestId, KEY_RESERVE,
                UsageLedgerEntryType.RESERVE, tokens, cost, properties.currency()));
    }

    /** Converts a reservation into real consumption. Idempotent and a no-op without a reservation. */
    @Transactional
    public void settle(String aiRequestId, long promptTokens, long completionTokens,
                       BigDecimal inputPrice, BigDecimal outputPrice) {
        AiUsageLedger reservation = ledger.findByAiRequestIdAndOperationKey(aiRequestId, KEY_RESERVE).orElse(null);
        if (reservation == null || ledger.existsByAiRequestIdAndOperationKey(aiRequestId, KEY_SETTLE)) {
            return;
        }
        long tokens = Math.max(0, promptTokens) + Math.max(0, completionTokens);
        BigDecimal cost = cost(promptTokens, inputPrice).add(cost(completionTokens, outputPrice));
        AiUsageAccount account = requireAccountForUpdate(reservation.getUser().getId());
        account.settle(reservation.getTokenDelta(), reservation.getCostDelta(), tokens, cost);
        accounts.saveAndFlush(account);
        ledger.save(new AiUsageLedger(account.getUser(), aiRequestId, KEY_SETTLE, UsageLedgerEntryType.SETTLE,
                tokens - reservation.getTokenDelta(),
                cost.subtract(reservation.getCostDelta()), properties.currency()));
    }

    /** Returns the reservation of a request that stopped or failed without a reported usage. Idempotent. */
    @Transactional
    public void release(String aiRequestId) {
        AiUsageLedger reservation = ledger.findByAiRequestIdAndOperationKey(aiRequestId, KEY_RESERVE).orElse(null);
        if (reservation == null
                || ledger.existsByAiRequestIdAndOperationKey(aiRequestId, KEY_SETTLE)
                || ledger.existsByAiRequestIdAndOperationKey(aiRequestId, KEY_RELEASE)) {
            return;
        }
        AiUsageAccount account = requireAccountForUpdate(reservation.getUser().getId());
        account.release(reservation.getTokenDelta(), reservation.getCostDelta());
        accounts.saveAndFlush(account);
        ledger.save(new AiUsageLedger(account.getUser(), aiRequestId, KEY_RELEASE, UsageLedgerEntryType.RELEASE,
                -reservation.getTokenDelta(), reservation.getCostDelta().negate(), properties.currency()));
    }

    /**
     * Administrative correction. Positive deltas consume quota and are rejected when they would exceed it;
     * negative deltas are rejected when they would make consumed usage negative. Idempotent per
     * {@code operationKey}, which the caller must make unique because ADJUST rows have no
     * {@code ai_request_id}.
     */
    @Transactional
    public UsageView adjust(Long userId, String operationKey, long tokenDelta, BigDecimal costDelta) {
        if (ledger.existsByOperationKey(operationKey)) {
            return current(userId);
        }
        BigDecimal cost = costDelta == null ? BigDecimal.ZERO : costDelta;
        AiUsageAccount account = requireAccountForUpdate(userId);
        if (account.isExpired(clock.instant())) {
            Instant now = clock.instant();
            account.rollover(now, periodEnd(now));
        }
        if (account.getUsedTokens() + tokenDelta < 0 || account.getUsedCost().add(cost).compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION);
        }
        if (tokenDelta > 0 && !account.canReserve(tokenDelta, cost)) {
            throw new BusinessException(ErrorCode.AI_QUOTA_EXCEEDED);
        }
        account.adjust(tokenDelta, cost);
        accounts.saveAndFlush(account);
        ledger.save(new AiUsageLedger(account.getUser(), null, operationKey, UsageLedgerEntryType.ADJUST,
                tokenDelta, cost, properties.currency()));
        return UsageView.from(account);
    }

    @Transactional
    public UsageView current(Long userId) {
        AiUsageAccount account = requireAccountForUpdate(userId);
        if (account.isExpired(clock.instant())) {
            Instant now = clock.instant();
            account.rollover(now, periodEnd(now));
            accounts.saveAndFlush(account);
        }
        return UsageView.from(account);
    }

    /** Read-only view for administrative listings; never rolls the period over and never creates an account. */
    @Transactional(readOnly = true)
    public UsageView snapshot(Long userId) {
        return accounts.findById(userId).map(UsageView::from).orElseGet(() -> {
            Instant now = clock.instant();
            return new UsageView(properties.quotaTokens(), 0, 0, Math.max(0, properties.quotaTokens()),
                    properties.quotaCost(), BigDecimal.ZERO, BigDecimal.ZERO, properties.currency(),
                    now, periodEnd(now));
        });
    }

    private AiUsageAccount requireAccountForUpdate(Long userId) {
        return accounts.findForUpdate(userId).orElseGet(() -> {
            AppUser user = users.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
            log.info("Creating missing AI usage account userId={}", userId);
            Instant now = clock.instant();
            AiUsageAccount created = new AiUsageAccount(user, properties.quotaTokens(), properties.quotaCost(),
                    properties.currency(), now, periodEnd(now));
            return accounts.saveAndFlush(created);
        });
    }

    private long periodDays() {
        return Math.max(1, properties.periodDays());
    }

    private Instant periodEnd(Instant start) {
        return start.plus(Duration.ofDays(periodDays()));
    }

    private static BigDecimal cost(long tokens, BigDecimal pricePerThousandTokens) {
        if (pricePerThousandTokens == null || tokens <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens)
                .multiply(pricePerThousandTokens)
                .divide(PRICE_UNIT_TOKENS, 8, RoundingMode.HALF_UP);
    }
}
