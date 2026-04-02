-- RLS for user_activity table
-- Ensures users can only read their own activity events.
-- Backend inserts use the service role key and bypass RLS.

ALTER TABLE public.user_activity ENABLE ROW LEVEL SECURITY;

-- Allow each user to read only their own rows
CREATE POLICY "users can read own activity"
  ON public.user_activity
  FOR SELECT
  USING (auth.uid() = user_id);

-- Service role (backend / Edge Functions) bypasses RLS — no INSERT policy needed.
-- If you ever need frontend inserts (e.g. case_viewed from the browser), add:
-- CREATE POLICY "users can insert own activity"
--   ON public.user_activity FOR INSERT WITH CHECK (auth.uid() = user_id);
