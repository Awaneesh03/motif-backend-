-- 90-day retention policy for user_activity
-- Requires pg_cron extension (available in Supabase).
-- Run once in the Supabase SQL Editor.

-- Enable pg_cron if not already enabled (requires superuser; Supabase enables it by default)
-- CREATE EXTENSION IF NOT EXISTS pg_cron;

-- Schedule a nightly job at 02:00 UTC to delete rows older than 90 days.
-- The job is idempotent — safe to re-run if it fails mid-flight.
SELECT cron.schedule(
  'purge-old-user-activity',   -- unique job name
  '0 2 * * *',                 -- every day at 02:00 UTC
  $$
    DELETE FROM public.user_activity
    WHERE created_at < NOW() - INTERVAL '90 days';
  $$
);

-- To verify the job was registered:
-- SELECT * FROM cron.job WHERE jobname = 'purge-old-user-activity';

-- To remove the job:
-- SELECT cron.unschedule('purge-old-user-activity');
