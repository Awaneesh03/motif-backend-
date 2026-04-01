-- ── user_activity: add dedup_key column ──────────────────────────────────────
-- Enables DB-level duplicate prevention across sessions/devices.
-- Idempotent — safe to re-run.

ALTER TABLE public.user_activity
    ADD COLUMN IF NOT EXISTS dedup_key TEXT;

-- Index to make the dedup SELECT fast (user_id + dedup_key + created_at range)
CREATE INDEX IF NOT EXISTS idx_user_activity_dedup
    ON public.user_activity (user_id, dedup_key, created_at DESC);

NOTIFY pgrst, 'reload schema';
