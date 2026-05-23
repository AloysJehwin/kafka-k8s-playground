-- Add tenantId to orders (backfill existing rows with a placeholder tenant)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
UPDATE orders SET tenant_id = 'system' WHERE tenant_id IS NULL;
ALTER TABLE orders ALTER COLUMN tenant_id SET NOT NULL;

-- Add tenantId to outbox for routing/observability
ALTER TABLE outbox ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);
UPDATE outbox SET tenant_id = 'system' WHERE tenant_id IS NULL;
ALTER TABLE outbox ALTER COLUMN tenant_id SET NOT NULL;

-- Performance index for tenant-scoped queries
CREATE INDEX IF NOT EXISTS idx_orders_tenant ON orders (tenant_id);
CREATE INDEX IF NOT EXISTS idx_orders_tenant_customer ON orders (tenant_id, customer_id);
-- Partial index: only unprocessed outbox events need fast tenant lookup
CREATE INDEX IF NOT EXISTS idx_outbox_tenant ON outbox (tenant_id) WHERE processed_at IS NULL;

-- Enable RLS (no policy = superuser only; safe to enable before policies exist)
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox ENABLE ROW LEVEL SECURITY;

-- App-role policy: app user sees only rows matching current_setting('app.tenant_id').
-- The setting is injected at the connection/session level by TenantContext.
-- NOTE: the `true` second arg to current_setting means it returns NULL instead of
-- raising an error when the setting is absent (e.g., during migrations).
CREATE POLICY tenant_isolation_orders ON orders
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

CREATE POLICY tenant_isolation_outbox ON outbox
    USING (tenant_id = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true));

-- Superuser/service-account bypass: the `eventflow` role is used by Flyway migrations
-- and the outbox relay. The relay must see ALL tenants' outbox events to forward them
-- to Kafka, so it cannot be restricted to a single app.tenant_id session setting.
-- These policies intentionally bypass RLS for that role.
CREATE POLICY superuser_bypass_orders ON orders TO eventflow USING (true);
CREATE POLICY superuser_bypass_outbox ON outbox TO eventflow USING (true);
