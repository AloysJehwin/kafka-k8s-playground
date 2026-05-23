-- Phase 2: Restricted app role for true RLS enforcement.
--
-- PRODUCTION DEPLOYMENT NOTE:
--   The application should connect as `app` (not `eventflow`) for all data queries:
--     DB_USER=app DB_PASS=<secret>
--   Flyway should run separately as a migration role with DDL privileges:
--     FLYWAY_USER=eventflow FLYWAY_PASSWORD=<secret>
--   The outbox relay continues to run as `eventflow` (superuser bypass needed for
--   cross-tenant access to forward all outbox events to Kafka).
--
-- In local dev and CI the eventflow superuser continues to handle everything because
-- the `app` role password is not set, but the role and grants are pre-provisioned here
-- so production can activate RLS enforcement without any further schema changes.

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app') THEN
    CREATE ROLE app NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
END $$;

-- Connect + schema access
GRANT CONNECT ON DATABASE orders TO app;
GRANT USAGE ON SCHEMA public TO app;

-- DML only — no DDL — on the two tenant-scoped tables.
-- Because `app` is not a superuser, RLS policies on these tables will be enforced.
GRANT SELECT, INSERT, UPDATE, DELETE ON orders TO app;
GRANT SELECT, INSERT, UPDATE ON outbox TO app;

-- app role must be able to call set_config so TenantFilter can push app.tenant_id
GRANT EXECUTE ON FUNCTION set_config(text, text, boolean) TO app;

-- The superuser_bypass_* policies from V3 were created for the `eventflow` role.
-- eventflow is already a Postgres superuser, so those explicit bypass policies are
-- redundant (superusers bypass RLS unconditionally).  Drop them to keep the policy
-- list tidy; they do not affect the actual RLS behaviour either way.
DROP POLICY IF EXISTS superuser_bypass_orders ON orders;
DROP POLICY IF EXISTS superuser_bypass_outbox ON outbox;

-- Auto-grant DML to `app` on all future tables created in this schema
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app;
