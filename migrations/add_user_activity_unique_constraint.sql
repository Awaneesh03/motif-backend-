-- ── user_activity: dedup safety note ─────────────────────────────────────────
-- A time-windowed unique index (e.g. on epoch/10 bucket) is NOT possible in
-- PostgreSQL because EXTRACT(EPOCH FROM timestamptz) is not marked IMMUTABLE.
--
-- Dedup is handled entirely at the application layer:
--   Layer 1 — in-memory Map (10s window, same session, 0ms cost)
--   Layer 2 — DB SELECT check (10s window, cross-session, ~50ms)
--   Layer 3 — error.code === '23505' handler (defensive, silent)
--
-- The existing index idx_user_activity_dedup on (user_id, dedup_key, created_at DESC)
-- from add_user_activity_dedup_key.sql is sufficient for query performance.
--
-- Nothing to run — this migration is a no-op.
-- ─────────────────────────────────────────────────────────────────────────────

-- Clean up if the broken unique index was partially created:
DROP INDEX IF EXISTS idx_user_activity_dedup_unique;
