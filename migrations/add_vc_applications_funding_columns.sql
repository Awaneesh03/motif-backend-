-- ── vc_applications: add funding pipeline columns ────────────────────────────
-- Adds vc_notes, reviewed_at, and ensures founder_id + updated_at exist.
-- All changes are idempotent (IF NOT EXISTS / DO NOTHING patterns).
-- Existing rows and the VC intro-request flow are unaffected.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. founder_id  — the authenticated user who submitted the funding request
ALTER TABLE public.vc_applications
    ADD COLUMN IF NOT EXISTS founder_id UUID;

-- 2. vc_notes — free-text feedback/notes written by the reviewing VC/admin
ALTER TABLE public.vc_applications
    ADD COLUMN IF NOT EXISTS vc_notes TEXT;

-- 3. reviewed_at — timestamp when a VC/admin last changed the status
ALTER TABLE public.vc_applications
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ;

-- 4. updated_at — auto-maintained last-modified timestamp
ALTER TABLE public.vc_applications
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- 5. Ensure status column has a sensible default for new founder submissions
--    (existing rows keep their current value; only new inserts are affected)
ALTER TABLE public.vc_applications
    ALTER COLUMN status SET DEFAULT 'submitted';

-- 6. Auto-update updated_at on every row change
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

-- Drop the trigger first so this script is re-runnable
DROP TRIGGER IF EXISTS trg_vc_applications_updated_at ON public.vc_applications;

CREATE TRIGGER trg_vc_applications_updated_at
    BEFORE UPDATE ON public.vc_applications
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- 7. Index for founder dashboard queries
CREATE INDEX IF NOT EXISTS idx_vc_applications_founder_id
    ON public.vc_applications (founder_id);

-- 8. RLS — founders can read their own applications; VC/admin can read all
--    (extend existing RLS if present; safe to run even if policies already exist)

ALTER TABLE public.vc_applications ENABLE ROW LEVEL SECURITY;

-- Founders see their own submissions
DO $$ BEGIN
    CREATE POLICY "Founders can view own applications"
        ON public.vc_applications
        FOR SELECT
        TO authenticated
        USING (auth.uid() = founder_id);
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- VC / admin can read all rows (for pipeline view)
DO $$ BEGIN
    CREATE POLICY "VC and admin can view all applications"
        ON public.vc_applications
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

-- VC / admin can update status, vc_notes, reviewed_at
DO $$ BEGIN
    CREATE POLICY "VC and admin can update application status"
        ON public.vc_applications
        FOR UPDATE
        TO authenticated
        USING (
            EXISTS (
                SELECT 1 FROM public.profiles
                WHERE profiles.id    = auth.uid()
                  AND profiles.role IN ('vc', 'admin', 'super_admin')
            )
        )
        WITH CHECK (
            EXISTS (
                SELECT 1 FROM public.profiles
                WHERE profiles.id    = auth.uid()
                  AND profiles.role IN ('vc', 'admin', 'super_admin')
            )
        );
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
