-- Migration: add normalized_idea column + unique index for one-idea-per-user deduplication
-- Run once against your Supabase / Postgres database.

-- 1. Add the column (safe to re-run — IF NOT EXISTS guard)
ALTER TABLE public.idea_analyses
    ADD COLUMN IF NOT EXISTS normalized_idea TEXT;

-- 2. Back-fill existing rows: trim + lowercase + collapse whitespace
UPDATE public.idea_analyses
SET normalized_idea = TRIM(REGEXP_REPLACE(LOWER(idea_title), '\s+', ' ', 'g'))
WHERE normalized_idea IS NULL;

-- 3. Unique constraint: one idea per user (enforced at DB level).
--    Standard (non-partial) unique index so ON CONFLICT (user_id, normalized_idea)
--    in the native upsert query resolves to it without needing a WHERE predicate.
--    PostgreSQL treats NULL as distinct in unique indexes, so legacy rows with
--    normalized_idea IS NULL cannot conflict with each other.
CREATE UNIQUE INDEX IF NOT EXISTS uq_idea_analyses_user_normalized
    ON public.idea_analyses (user_id, normalized_idea);
