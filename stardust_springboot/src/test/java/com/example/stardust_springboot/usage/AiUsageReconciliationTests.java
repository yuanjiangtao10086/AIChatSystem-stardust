package com.example.stardust_springboot.usage;

import com.example.stardust_springboot.common.id.PublicIdGenerator;
import com.example.stardust_springboot.usage.entity.AiUsageAccount;
import com.example.stardust_springboot.usage.repository.AiUsageAccountRepository;
import com.example.stardust_springboot.usage.repository.AiUsageLedgerRepository;
import com.example.stardust_springboot.usage.service.AiUsageReconciliationService;
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
        "app.ai.usage.period-days=30",
        "app.ai.usage.reserve-sweep-window-hours=24"
})
@Transactional
class AiUsageReconciliationTests {

    @Autowired AiUsageService usage;
    @Autowired AiUsageReconciliationService reconciliation;
    @Autowired AiUsageAccountRepository accounts;
    @Autowired AiUsageLedgerRepository ledger;
    @Autowired AppUserRepository users;

    @Test
    void reservationWithoutRequestFactIsReleased() {
        Long userId = newUser("sweep");
        String requestId = PublicIdGenerator.newUlid();
        usage.reserve(userId, requestId, 200, 100, null, null);
        assertThat(usage.current(userId).reservedTokens()).isEqualTo(300);

        AiUsageReconciliationService.Report report = reconciliation.reconcile();

        assertThat(report.releasedReservations()).isEqualTo(1);
        assertThat(report.driftedAccounts()).isZero();
        assertThat(usage.current(userId).reservedTokens()).isZero();
    }

    @Test
    void accountBalanceDriftIsReportedAndNotSilentlyRewritten() {
        Long userId = newUser("drift");
        String requestId = PublicIdGenerator.newUlid();
        usage.reserve(userId, requestId, 200, 100, null, null);
        long reservedBefore = usage.current(userId).reservedTokens();

        AiUsageAccount account = accounts.findForUpdate(userId).orElseThrow();
        account.reserve(500, BigDecimal.ZERO);
        accounts.saveAndFlush(account);

        AiUsageReconciliationService.Report report = reconciliation.reconcile();

        assertThat(report.driftedAccounts()).isEqualTo(1);
        assertThat(report.driftDetails()).singleElement().asString().contains("userId=");
        assertThat(usage.current(userId).reservedTokens()).isNotEqualTo(reservedBefore);
    }

    @Test
    void consistentAccountProducesNoDrift() {
        Long userId = newUser("clean");
        String requestId = PublicIdGenerator.newUlid();
        usage.reserve(userId, requestId, 200, 100, null, null);
        usage.settle(requestId, 100, 50, null, null);

        AiUsageReconciliationService.Report report = reconciliation.reconcile();

        assertThat(report.releasedReservations()).isZero();
        assertThat(report.driftedAccounts()).isZero();
        assertThat(report.driftDetails()).isEmpty();
        assertThat(usage.current(userId).usedTokens()).isEqualTo(150);
        assertThat(ledger.sumTokenDeltaByUserId(userId)).isEqualTo(150);
    }

    private Long newUser(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        return users.saveAndFlush(new AppUser(prefix + suffix + "@test.local", "hash", prefix)).getId();
    }
}
