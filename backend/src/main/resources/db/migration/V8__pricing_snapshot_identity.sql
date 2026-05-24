-- V8__pricing_snapshot_identity.sql
--
-- TKM-48: attach a deterministic, content-derived id to every analysis and
-- analysis_job so callers can answer "are these costs still current?" and
-- TKM-45 can dedupe on (repoKey, pricingSnapshotId).
--
-- The id is computed by canonicalising the active sorted PricingSnapshot list
-- (excluding externalModelId and fetchedAt) and hashing the result with
-- SHA-256. The value is stored as `v1:<64-hex>` (67 chars). The column is
-- VARCHAR(80) to leave headroom for a future canonicalisation prefix bump.
--
-- All columns are nullable so legacy rows (rows inserted before this change,
-- jobs that fail before reaching CALCULATING_COSTS) keep working unchanged.
-- No backfill — historical rows remain NULL.
--
-- One ALTER per column so H2 (PostgreSQL-mode in tests) accepts the script.
ALTER TABLE analysis ADD COLUMN pricing_snapshot_id VARCHAR(80);
ALTER TABLE analysis ADD COLUMN pricing_primary_source VARCHAR(64);
ALTER TABLE analysis ADD COLUMN pricing_captured_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE analysis_job ADD COLUMN pricing_snapshot_id VARCHAR(80);
ALTER TABLE analysis_job ADD COLUMN pricing_primary_source VARCHAR(64);
ALTER TABLE analysis_job ADD COLUMN pricing_captured_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_analysis_pricing_snapshot_id
    ON analysis (pricing_snapshot_id);
