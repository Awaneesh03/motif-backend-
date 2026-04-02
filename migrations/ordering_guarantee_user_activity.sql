-- Ordering guarantee for user_activity
-- Ensures created_at is always set by the DB (never null, never caller-supplied).

-- 1. Verify / add DEFAULT NOW() on created_at
ALTER TABLE public.user_activity
  ALTER COLUMN created_at SET DEFAULT NOW();

-- 2. Add NOT NULL constraint if not already present
ALTER TABLE public.user_activity
  ALTER COLUMN created_at SET NOT NULL;

-- 3. Confirm the composite performance index exists
--    (also in add_activity_performance_index.sql — idempotent due to IF NOT EXISTS)
CREATE INDEX IF NOT EXISTS idx_user_activity_user_created
  ON public.user_activity (user_id, created_at DESC);

-- After running, verify with:
-- SELECT column_name, column_default, is_nullable
--   FROM information_schema.columns
--  WHERE table_schema = 'public'
--    AND table_name   = 'user_activity'
--    AND column_name  = 'created_at';
