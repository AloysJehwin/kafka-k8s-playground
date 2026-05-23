CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    slug VARCHAR(63) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    plan VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    api_key VARCHAR(64) UNIQUE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenants_slug ON tenants (slug);
CREATE INDEX idx_tenants_api_key ON tenants (api_key);
CREATE INDEX idx_tenants_status ON tenants (status);
