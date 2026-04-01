-- ── user_activity: partial unique constraint for race-condition-safe dedup ────
-- Enforces that the same (user_id, dedup_key) cannot be inserted more than once
-- within a 10-second window. Combined with the app-layer SELECT check this makes
-- concurrent inserts idempotent — the second writer gets a 23505 which the app
-- silently ignores.
--
-- Implementation: a unique index on a time-bucketed expression.
-- Bucket = Unix epoch truncated to 10-second intervals, stored as an integer.
-- Two inserts within the same 10s bucket → same bucket value → unique violation.
-- Inserts in different buckets → different value → allowed (correct long-term).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_activity_dedup_unique
    ON public.user_activity (
        user_id,
        dedup_key,
        (EXTRACT(EPOCH FROM created_at)::BIGINT / 10)  -- 10-second bucket
    );

NOTIFY pgrst, 'reload schema';
