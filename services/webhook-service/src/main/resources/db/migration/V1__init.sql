CREATE TABLE webhook_endpoints (
    id          UUID        NOT NULL PRIMARY KEY,
    tenant_id   TEXT        NOT NULL,
    url         TEXT        NOT NULL,
    secret      TEXT        NOT NULL,
    event_types TEXT        NOT NULL,
    status      TEXT        NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_webhook_tenant ON webhook_endpoints (tenant_id);

CREATE TABLE webhook_deliveries (
    id               UUID        NOT NULL PRIMARY KEY,
    endpoint_id      UUID        NOT NULL REFERENCES webhook_endpoints(id),
    tenant_id        TEXT        NOT NULL,
    event_type       TEXT        NOT NULL,
    event_id         TEXT        NOT NULL,
    payload          TEXT        NOT NULL,
    status           TEXT        NOT NULL DEFAULT 'PENDING',
    attempt_count    INT         NOT NULL DEFAULT 0,
    last_http_status INT,
    last_error       TEXT,
    next_attempt_at  TIMESTAMPTZ,
    delivered_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_endpoint ON webhook_deliveries (endpoint_id);
CREATE INDEX idx_delivery_tenant   ON webhook_deliveries (tenant_id);
CREATE INDEX idx_delivery_retry    ON webhook_deliveries (status, next_attempt_at)
    WHERE status = 'FAILED';
