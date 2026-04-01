-- ── vc_application_history: audit trail ──────────────────────────────────────
-- Records every status change on a vc_application row.
-- Immutable — rows are INSERT-only; no UPDATE or DELETE ever.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS public.vc_application_history (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID        NOT NULL REFERENCES public.vc_applications(id) ON DELETE CASCADE,
    old_status     TEXT,                       -- null on first transition from legacy rows
    new_status     TEXT        NOT NULL,
    changed_by     UUID        NOT NULL,        -- user_id of the reviewer
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Fast lookups by application and chronological order
CREATE INDEX IF NOT EXISTS idx_vc_app_history_application_id
    ON public.vc_application_history (application_id, changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_vc_app_history_changed_by
    ON public.vc_application_history (changed_by);

-- ── Performance indexes on vc_applications ────────────────────────────────────
-- Covers: founder dashboard (founder_id + status), pipeline filters (status),
-- and sort-by-updated (updated_at DESC).

CREATE INDEX IF NOT EXISTS idx_vc_applications_founder_status
    ON public.vc_applications (founder_id, status);

CREATE INDEX IF NOT EXISTS idx_vc_applications_status
    ON public.vc_applications (status);

CREATE INDEX IF NOT EXISTS idx_vc_applications_updated_at_desc
    ON public.vc_applications (updated_at DESC);

-- ── RLS on history table ──────────────────────────────────────────────────────
ALTER TABLE public.vc_application_history ENABLE ROW LEVEL SECURITY;

-- VCs / admins can read all history
DO $$ BEGIN
    CREATE POLICY "VC and admin can read history"
        ON public.vc_application_history
        FOR SELECT
        TO authenticated
        USING (
            EXISTS (
                SELECT 1 FROM public.profiles
                WHERE profiles.id    = auth.uid()
                  AND profiles.role IN ('vc', 'admin', 'super_admin')
            )
        );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Founders can read history for their own applications
DO $$ BEGIN
    CREATE POLICY "Founders can read own application history"
        ON public.vc_application_history
        FOR SELECT
        TO authenticated
        USING (
            EXISTS (
                SELECT 1 FROM public.vc_applications va
                WHERE va.id         = application_id
                  AND va.founder_id = auth.uid()
            )
        );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
