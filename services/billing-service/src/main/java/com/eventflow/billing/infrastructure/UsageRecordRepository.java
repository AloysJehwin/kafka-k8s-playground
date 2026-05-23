package com.eventflow.billing.infrastructure;

import com.eventflow.billing.domain.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, UUID> {

    Optional<UsageRecord> findByTenantIdAndBillingYearAndBillingMonth(
        String tenantId, int year, int month);

    List<UsageRecord> findByTenantIdOrderByBillingYearDescBillingMonthDesc(String tenantId);

    @Modifying
    @Query("UPDATE UsageRecord u SET u.orderCount = u.orderCount + :delta, u.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE u.tenantId = :tenantId AND u.billingYear = :year AND u.billingMonth = :month")
    int incrementOrders(@Param("tenantId") String tenantId,
                        @Param("year") int year,
                        @Param("month") int month,
                        @Param("delta") long delta);

    @Modifying
    @Query("UPDATE UsageRecord u SET u.webhookDeliveryCount = u.webhookDeliveryCount + :delta, u.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE u.tenantId = :tenantId AND u.billingYear = :year AND u.billingMonth = :month")
    int incrementWebhookDeliveries(@Param("tenantId") String tenantId,
                                   @Param("year") int year,
                                   @Param("month") int month,
                                   @Param("delta") long delta);

    @Modifying
    @Query("UPDATE UsageRecord u SET u.apiCallCount = u.apiCallCount + :delta, u.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE u.tenantId = :tenantId AND u.billingYear = :year AND u.billingMonth = :month")
    int incrementApiCalls(@Param("tenantId") String tenantId,
                          @Param("year") int year,
                          @Param("month") int month,
                          @Param("delta") long delta);
}
