-- ── user_activity: unified event log ─────────────────────────────────────────
-- Tracks every meaningful user action across the platform.
-- Immutable — INSERT only; rows are never updated or deleted.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.user_activity (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    type       TEXT        NOT NULL,   -- e.g. idea_analyzed, pitch_created, case_viewed
    title      TEXT        NOT NULL,
    metadata   JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Fast feed query: user's own activity sorted newest-first
CREATE INDEX IF NOT EXISTS idx_user_activity_user_created
    ON public.user_activity (user_id, created_at DESC);

-- ── RLS ───────────────────────────────────────────────────────────────────────
ALTER TABLE public.user_activity ENABLE ROW LEVEL SECURITY;

-- Users can only read their own activity
DO $$ BEGIN
    CREATE POLICY "Users can read own activity"
        ON public.user_activity FOR SELECT TO authenticated
        USING (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Users can only insert their own activity
DO $$ BEGIN
    CREATE POLICY "Users can insert own activity"
        ON public.user_activity FOR INSERT TO authenticated
        WITH CHECK (auth.uid() = user_id);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ── Refresh PostgREST schema cache ────────────────────────────────────────────
NOTIFY pgrst, 'reload schema';
