package com.eventflow.billing;

import com.eventflow.billing.domain.PlanTier;
import com.eventflow.billing.domain.UsageRecord;
import com.eventflow.billing.infrastructure.UsageRecordRepository;
import com.eventflow.billing.metering.UsageAccumulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class UsageAccumulatorTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
    }

    @Autowired UsageAccumulator accumulator;
    @Autowired UsageRecordRepository repo;

    private final YearMonth thisMonth = YearMonth.now();

    @BeforeEach
    void clean() { repo.deleteAll(); }

    @Test
    void recordOrder_createsRecordAndIncrements() {
        accumulator.recordOrder("tenant-A", "STARTER", thisMonth);
        accumulator.recordOrder("tenant-A", "STARTER", thisMonth);

        UsageRecord r = repo.findByTenantIdAndBillingYearAndBillingMonth(
            "tenant-A", thisMonth.getYear(), thisMonth.getMonthValue()).orElseThrow();
        assertThat(r.getOrderCount()).isEqualTo(2);
    }

    @Test
    void recordOrder_separateTenantsAreIsolated() {
        accumulator.recordOrder("tenant-A", "FREE", thisMonth);
        accumulator.recordOrder("tenant-B", "FREE", thisMonth);

        long countA = repo.findByTenantIdAndBillingYearAndBillingMonth(
            "tenant-A", thisMonth.getYear(), thisMonth.getMonthValue())
            .map(UsageRecord::getOrderCount).orElse(-1L);
        long countB = repo.findByTenantIdAndBillingYearAndBillingMonth(
            "tenant-B", thisMonth.getYear(), thisMonth.getMonthValue())
            .map(UsageRecord::getOrderCount).orElse(-1L);

        assertThat(countA).isEqualTo(1);
        assertThat(countB).isEqualTo(1);
    }

    @Test
    void checkOrderQuota_noRecordReturnsFalse() {
        var status = accumulator.checkOrderQuota("tenant-unknown", "FREE", thisMonth);
        assertThat(status.exceeded()).isFalse();
        assertThat(status.used()).isEqualTo(0);
        assertThat(status.limit()).isEqualTo(PlanTier.FREE.orderQuota());
    }

    @Test
    void checkOrderQuota_exceededWhenAtLimit() {
        // Bypass accumulator to set count directly
        var rec = new UsageRecord("tenant-C", PlanTier.FREE, thisMonth);
        // Use reflection to set orderCount to quota value
        for (int i = 0; i < PlanTier.FREE.orderQuota(); i++) {
            rec.incrementOrders(1);
        }
        repo.save(rec);

        var status = accumulator.checkOrderQuota("tenant-C", "FREE", thisMonth);
        assertThat(status.exceeded()).isTrue();
    }

    @Test
    void recordWebhookDelivery_incrementsCorrectField() {
        accumulator.recordWebhookDelivery("tenant-D", "PRO", thisMonth);
        accumulator.recordWebhookDelivery("tenant-D", "PRO", thisMonth);

        UsageRecord r = repo.findByTenantIdAndBillingYearAndBillingMonth(
            "tenant-D", thisMonth.getYear(), thisMonth.getMonthValue()).orElseThrow();
        assertThat(r.getWebhookDeliveryCount()).isEqualTo(2);
        assertThat(r.getOrderCount()).isEqualTo(0);
    }

    @Test
    void recordsAcrossMonthsAreKeptSeparate() {
        YearMonth lastMonth = thisMonth.minusMonths(1);
        accumulator.recordOrder("tenant-E", "FREE", thisMonth);
        accumulator.recordOrder("tenant-E", "FREE", lastMonth);

        long thisCount = repo.findByTenantIdAndBillingYearAndBillingMonth(
            "tenant-E", thisMonth.getYear(), thisMonth.getMonthValue())
            .map(UsageRecord::getOrderCount).orElse(-1L);
        long lastCount = repo.findByTenantIdAndBillingYearAndBillingMonth(
            "tenant-E", lastMonth.getYear(), lastMonth.getMonthValue())
            .map(UsageRecord::getOrderCount).orElse(-1L);

        assertThat(thisCount).isEqualTo(1);
        assertThat(lastCount).isEqualTo(1);
    }
}
