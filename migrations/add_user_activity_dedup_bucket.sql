-- ── user_activity: dedup_bucket column + unique constraint ───────────────────
-- Adds a plain INTEGER bucket (10-second window, computed in the app as
-- Math.floor(Date.now() / 10000)) so we can enforce a unique index without
-- relying on immutable expression functions (which caused the previous attempt
-- to fail with 42P17).
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.user_activity
    ADD COLUMN IF NOT EXISTS dedup_bucket INTEGER;

-- Backfill existing rows
UPDATE public.user_activity
SET dedup_bucket = FLOOR(EXTRACT(EPOCH FROM created_at) / 10)::INTEGER
WHERE dedup_bucket IS NULL;

-- Unique constraint: same user cannot log the same dedup_key in the same 10s bucket
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_activity_unique_bucket
    ON public.user_activity (user_id, dedup_key, dedup_bucket);

NOTIFY pgrst, 'reload schema';
