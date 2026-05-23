CREATE TABLE usage_records (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    plan            VARCHAR(16) NOT NULL,
    billing_year    INTEGER NOT NULL,
    billing_month   INTEGER NOT NULL,
    order_count     BIGINT NOT NULL DEFAULT 0,
    webhook_delivery_count BIGINT NOT NULL DEFAULT 0,
    api_call_count  BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_usage_tenant_month UNIQUE (tenant_id, billing_year, billing_month)
);

CREATE INDEX idx_usage_tenant ON usage_records (tenant_id);

CREATE TABLE invoices (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    plan            VARCHAR(16) NOT NULL,
    billing_year    INTEGER NOT NULL,
    billing_month   INTEGER NOT NULL,
    order_count     BIGINT NOT NULL DEFAULT 0,
    webhook_delivery_count BIGINT NOT NULL DEFAULT 0,
    api_call_count  BIGINT NOT NULL DEFAULT 0,
    amount_cents    BIGINT NOT NULL DEFAULT 0,
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_invoice_tenant_month UNIQUE (tenant_id, billing_year, billing_month)
);

CREATE INDEX idx_invoice_tenant  ON invoices (tenant_id);
CREATE INDEX idx_invoice_status  ON invoices (status);
