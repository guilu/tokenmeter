-- V10__cost_estimate_tokenization_precision.sql
--
-- TKM-71: per-model tokenizer identity + precision. Nullable for legacy-row back-compat.
-- Existing UNIQUE(analysis_id, provider, model, mode) is unaffected.
--
-- One ALTER per column so H2 (PostgreSQL-mode in tests) accepts the script.
ALTER TABLE cost_estimates ADD COLUMN tokenizer_id VARCHAR(120);
ALTER TABLE cost_estimates ADD COLUMN tokenization_precision VARCHAR(30);
