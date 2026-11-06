-- Lock the hand-made backup table i_transactions_backup_20260406 on live.
--
-- Found 2026-11-05 while comparing live with staging: the table was created
-- by hand (no file in supabase/), had Row Level Security OFF and kept
-- Supabase's default grants, so anyone holding the public anon key (it ships
-- in the APK and the web pages) could read all 6,272 rows of real
-- transactions through the API.
--
-- This keeps the data (nothing is deleted) but closes it to the API:
-- RLS on with no policies returns no rows, and the grants are removed so the
-- API refuses outright. It stays readable in the SQL editor. Whether to drop
-- the table entirely is the owner's call.
--
-- Safe to run more than once. Does nothing on a project without the table.
do $$
begin
  if to_regclass('public.i_transactions_backup_20260406') is not null then
    alter table public.i_transactions_backup_20260406 enable row level security;
    revoke all on public.i_transactions_backup_20260406 from anon, authenticated;
  end if;
end$$;
