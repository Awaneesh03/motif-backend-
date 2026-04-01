-- ── profiles: add extended founder profile fields ────────────────────────────
-- Adds linkedin, about, location, education, startup_goals.
-- All idempotent (ADD COLUMN IF NOT EXISTS).
-- Existing rows get empty-string / empty-array defaults — no data loss.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS linkedin      TEXT   NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS about         TEXT   NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS location      TEXT   NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS education     TEXT   NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS startup_goals TEXT[] NOT NULL DEFAULT '{}';

-- ── RLS policies ──────────────────────────────────────────────────────────────

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
    CREATE POLICY "Users can view own profile"
        ON public.profiles FOR SELECT TO authenticated
        USING (auth.uid() = id);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE POLICY "Users can update own profile"
        ON public.profiles FOR UPDATE TO authenticated
        USING (auth.uid() = id)
        WITH CHECK (auth.uid() = id);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE POLICY "Users can insert own profile"
        ON public.profiles FOR INSERT TO authenticated
        WITH CHECK (auth.uid() = id);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- ── Refresh PostgREST schema cache ────────────────────────────────────────────
NOTIFY pgrst, 'reload schema';
