-- V9__language_stats_name_index.sql
-- TKM-46: support cross-repo language aggregation by indexing language_name
-- for GROUP BY scans on language_stats. Safe to add; safe to drop.
CREATE INDEX IF NOT EXISTS idx_language_stats_name
    ON language_stats (language_name);
