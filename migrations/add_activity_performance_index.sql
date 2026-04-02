-- Performance index for user_activity feed queries.
-- Covers: WHERE user_id = ? ORDER BY created_at DESC LIMIT 10
-- Already may exist from earlier migrations; IF NOT EXISTS makes this idempotent.

CREATE INDEX IF NOT EXISTS idx_user_activity_user_created
    ON public.user_activity (user_id, created_at DESC);

-- The unique dedup index (should already exist from add_user_activity_dedup_bucket.sql):
-- CREATE UNIQUE INDEX IF NOT EXISTS idx_user_activity_unique_bucket
--     ON public.user_activity (user_id, dedup_key, dedup_bucket);
