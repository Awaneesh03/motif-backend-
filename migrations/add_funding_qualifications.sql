-- ── funding_qualifications ────────────────────────────────────────────────────
-- One row per user. Stores founder profile data that persists across funding
-- request sessions so returning users see a pre-filled qualification form.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.funding_qualifications (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL UNIQUE,                    -- one row per user
    full_name          TEXT        NOT NULL DEFAULT '',
    email              TEXT        NOT NULL DEFAULT '',
    experience_level   TEXT        NOT NULL DEFAULT '',               -- e.g. 'first_time', '1_2_startups', '3_plus', 'serial'
    linkedin_url       TEXT                 DEFAULT '',
    previous_startups  TEXT                 DEFAULT '',               -- free-text description
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Indexes ───────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_funding_qualifications_user_id
    ON public.funding_qualifications (user_id);

-- ── Row Level Security ────────────────────────────────────────────────────────
ALTER TABLE public.funding_qualifications ENABLE ROW LEVEL SECURITY;

-- Authenticated users can read and write their own row only
CREATE POLICY "Users can manage own qualification"
    ON public.funding_qualifications
    FOR ALL
    TO authenticated
    USING  (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Super admins can read all rows (for review purposes)
CREATE POLICY "Admins can view all qualifications"
    ON public.funding_qualifications
    FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.profiles
            WHERE profiles.id = auth.uid()
              AND profiles.role = 'super_admin'
        )
    );
