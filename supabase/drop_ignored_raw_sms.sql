-- =============================================================================
-- Stop storing bank SMS the parser ignores (one-time codes, promos, notices).
--
-- Why: Google Play's SMS policy and basic security. A login or payment code
-- has no use on the server, and storing it is a risk if the database leaks.
-- From app 1.0.13 the phone never uploads them (getUnsyncedRawMessages).
-- Older app versions still send them, so this file:
--   1. silently drops IGNORED rows on insert, so old versions sync fine but
--      nothing is stored (the upsert succeeds; the row is just skipped),
--   2. deletes the IGNORED rows already stored.
--
-- Rows from before 1.0.9 have no parse_status; every device re-sent its SMS
-- with a status since then (resyncRawOnce), so few if any remain.
--
-- Safe to run more than once. Paste into the Supabase SQL editor and run.
-- =============================================================================

-- 1. Skip ignored messages on insert. A BEFORE INSERT trigger that returns
--    null drops the row before the ON CONFLICT check, so an old app's
--    upsert of a code neither inserts nor updates anything.
create or replace function public.i_raw_data_skip_ignored()
returns trigger
language plpgsql
as $$
begin
  if new.parse_status = 'IGNORED' then
    return null;
  end if;
  return new;
end;
$$;

drop trigger if exists i_raw_data_skip_ignored on public.i_raw_data;
create trigger i_raw_data_skip_ignored
  before insert on public.i_raw_data
  for each row execute function public.i_raw_data_skip_ignored();

-- 2. Delete what is already stored.
delete from public.i_raw_data where parse_status = 'IGNORED';
