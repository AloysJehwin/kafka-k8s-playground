package com.eventflow.billing.infrastructure;

import com.eventflow.billing.domain.Invoice;
import com.eventflow.billing.domain.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByTenantIdOrderByBillingYearDescBillingMonthDesc(String tenantId);

    Optional<Invoice> findByTenantIdAndBillingYearAndBillingMonth(
        String tenantId, int year, int month);

    List<Invoice> findByStatus(InvoiceStatus status);
}
