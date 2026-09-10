package com.example.stardust_springboot.usage;

import com.example.stardust_springboot.common.exception.BusinessException;
import com.example.stardust_springboot.common.exception.ErrorCode;
import com.example.stardust_springboot.common.id.PublicIdGenerator;
import com.example.stardust_springboot.usage.dto.UsageView;
import com.example.stardust_springboot.usage.repository.AiUsageAccountRepository;
import com.example.stardust_springboot.usage.repository.AiUsageLedgerRepository;
import com.example.stardust_springboot.usage.service.AiUsageService;
import com.example.stardust_springboot.user.entity.AppUser;
import com.example.stardust_springboot.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "app.ai.usage.quota-tokens=1000",
        "app.ai.usage.quota-cost=1",
        "app.ai.usage.currency=USD",
        "app.ai.usage.output-reserve-tokens=100",
        "app.ai.usage.period-days=30"
})
@Transactional
class AiUsageIntegrationTests {

    @Autowired AiUsageService usage;
    @Autowired AiUsageAccountRepository accounts;
    @Autowired AiUsageLedgerRepository ledger;
    @Autowired AppUserRepository users;

    @Test
    void reserveSettlesRealUsageAndKeepsLedgerConsistent() {
        Long userId = newUser("settle");
        String requestId = PublicIdGenerator.newUlid();

        usage.reserve(userId, requestId, 200, 100, null, null);

        UsageView reserved = usage.current(userId);
        assertThat(reserved.reservedTokens()).isEqualTo(300);
        assertThat(reserved.usedTokens()).isZero();
        assertThat(reserved.availableTokens()).isEqualTo(700);

        usage.settle(requestId, 200, 50, null, null);

        UsageView settled = usage.current(userId);
        assertThat(settled.reservedTokens()).isZero();
        assertThat(settled.usedTokens()).isEqualTo(250);
        assertThat(ledger.sumTokenDeltaByUserId(userId)).isEqualTo(250);
    }

    @Test
    void costUsesPerThousandTokenPrices() {
        Long userId = newUser("cost");
        String requestId = PublicIdGenerator.newUlid();

        usage.reserve(userId, requestId, 200, 100, BigDecimal.ONE, BigDecimal.valueOf(2));
        usage.settle(requestId, 200, 50, BigDecimal.ONE, BigDecimal.valueOf(2));

        UsageView view = usage.current(userId);
        assertThat(view.currency()).isEqualTo("USD");
        assertThat(view.usedCost()).isEqualByComparingTo("0.30000000");
        assertThat(view.usedTokens()).isEqualTo(250);
    }

    @Test
    void overQuotaReservationIsRejected() {
        Long userId = newUser("quota");
        usage.reserve(userId, PublicIdGenerator.newUlid(), 600, 100, null, null);

        assertThatThrownBy(() -> usage.reserve(userId, PublicIdGenerator.newUlid(), 600, 100, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.AI_QUOTA_EXCEEDED);
    }

    @Test
    void releaseReturnsReservationAndIsIgnoredAfterSettlement() {
        Long userId = newUser("release");
        String stopped = PublicIdGenerator.newUlid();
        String settled = PublicIdGenerator.newUlid();

        usage.reserve(userId, stopped, 200, 100, null, null);
        usage.release(stopped);
        assertThat(usage.current(userId).reservedTokens()).isZero();
        assertThat(usage.current(userId).availableTokens()).isEqualTo(1000);

        usage.reserve(userId, settled, 200, 100, null, null);
        usage.settle(settled, 10, 10, null, null);
        usage.release(settled);
        assertThat(usage.current(userId).usedTokens()).isEqualTo(20);

        usage.release(PublicIdGenerator.newUlid());
        assertThat(ledger.sumTokenDeltaByUserId(userId)).isEqualTo(20);
    }

    @Test
    void repeatedOperationsAreIdempotent() {
        Long userId = newUser("idempotent");
        String requestId = PublicIdGenerator.newUlid();

        usage.reserve(userId, requestId, 200, 100, null, null);
        usage.reserve(userId, requestId, 200, 100, null, null);
        assertThat(usage.current(userId).reservedTokens()).isEqualTo(300);

        usage.settle(requestId, 100, 50, null, null);
        usage.settle(requestId, 100, 50, null, null);
        assertThat(usage.current(userId).usedTokens()).isEqualTo(150);
        assertThat(ledger.findByAiRequestIdOrderByIdAsc(requestId)).hasSize(2);
    }

    @Test
    void missingAccountIsCreatedLazilyFromConfiguredDefaults() {
        Long userId = newUser("lazy");
        UsageView view = usage.current(userId);
        assertThat(view.quotaTokens()).isEqualTo(1000);
        assertThat(view.quotaCost()).isEqualByComparingTo("1");
        assertThat(view.usedTokens()).isZero();
        assertThat(view.periodEnd()).isAfter(view.periodStart());
    }

    private Long newUser(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return users.saveAndFlush(new AppUser(prefix + suffix + "@test.local", "hash", prefix)).getId();
    }
}
