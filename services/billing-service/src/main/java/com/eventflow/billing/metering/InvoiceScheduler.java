package com.eventflow.billing.metering;

import com.eventflow.billing.domain.Invoice;
import com.eventflow.billing.domain.InvoiceStatus;
import com.eventflow.billing.domain.UsageRecord;
import com.eventflow.billing.infrastructure.InvoiceRepository;
import com.eventflow.billing.infrastructure.UsageRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

/**
 * Runs at the start of each month to finalize the previous month's invoices.
 * Also runs a daily draft pass so invoices are visible in DRAFT state mid-month.
 */
@Component
public class InvoiceScheduler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceScheduler.class);

    private final UsageRecordRepository usageRepo;
    private final InvoiceRepository invoiceRepo;

    public InvoiceScheduler(UsageRecordRepository usageRepo, InvoiceRepository invoiceRepo) {
        this.usageRepo = usageRepo;
        this.invoiceRepo = invoiceRepo;
    }

    /** Generate DRAFT invoices daily for the current month so tenants can preview charges. */
    @Scheduled(cron = "${eventflow.billing.draft-cron:0 0 2 * * *}")
    @Transactional
    public void generateDrafts() {
        YearMonth current = YearMonth.now();
        List<UsageRecord> records = usageRepo.findAll().stream()
            .filter(r -> r.getBillingYear() == current.getYear()
                      && r.getBillingMonth() == current.getMonthValue())
            .toList();

        for (UsageRecord usage : records) {
            invoiceRepo.findByTenantIdAndBillingYearAndBillingMonth(
                    usage.getTenantId(), usage.getBillingYear(), usage.getBillingMonth())
                .ifPresentOrElse(
                    inv -> {
                        if (inv.getStatus() == InvoiceStatus.DRAFT) {
                            // Recreate with fresh counts — draft is mutable
                            invoiceRepo.delete(inv);
                            invoiceRepo.save(new Invoice(usage));
                        }
                    },
                    () -> invoiceRepo.save(new Invoice(usage))
                );
        }
        log.info("Generated/refreshed {} draft invoices for {}", records.size(), current);
    }

    /** Finalize prior month's invoices on the 1st of each month at 03:00. */
    @Scheduled(cron = "${eventflow.billing.finalize-cron:0 0 3 1 * *}")
    @Transactional
    public void finalizeLastMonth() {
        YearMonth last = YearMonth.now().minusMonths(1);
        List<UsageRecord> records = usageRepo.findAll().stream()
            .filter(r -> r.getBillingYear() == last.getYear()
                      && r.getBillingMonth() == last.getMonthValue())
            .toList();

        int count = 0;
        for (UsageRecord usage : records) {
            boolean alreadyFinalized = invoiceRepo
                .findByTenantIdAndBillingYearAndBillingMonth(
                    usage.getTenantId(), usage.getBillingYear(), usage.getBillingMonth())
                .map(inv -> inv.getStatus() != InvoiceStatus.DRAFT)
                .orElse(false);

            if (!alreadyFinalized) {
                Invoice invoice = invoiceRepo
                    .findByTenantIdAndBillingYearAndBillingMonth(
                        usage.getTenantId(), usage.getBillingYear(), usage.getBillingMonth())
                    .orElse(new Invoice(usage));
                invoice.finalize(InvoiceStatus.FINALIZED);
                invoiceRepo.save(invoice);
                count++;
            }
        }
        log.info("Finalized {} invoices for {}", count, last);
    }
}
