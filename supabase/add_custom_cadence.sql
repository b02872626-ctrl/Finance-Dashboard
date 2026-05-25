-- =============================================================================
-- Custom cadence — extends the cadence enum on both ledger + income tables
-- and adds an interval_days column for CUSTOM ("every N days") cadences.
--
-- New allowed cadence values:
--   WEEKLY | BIWEEKLY | MONTHLY | EVERY_30_DAYS | QUARTERLY | YEARLY | CUSTOM
--
-- CUSTOM cadence requires a positive interval_days; all other cadences ignore it.
--
-- Safe to run in any order relative to add_ledger.sql / add_income.sql:
-- each block is guarded with to_regclass() and silently skips a table that
-- hasn't been created yet.
-- =============================================================================

-- ---- i_ledger_entries -------------------------------------------------------
do $$
declare cname text;
begin
  if to_regclass('public.i_ledger_entries') is null then
    raise notice 'Skipping i_ledger_entries — table does not exist yet. Run add_ledger.sql first if you need it.';
  else
    for cname in
      select conname from pg_constraint
      where conrelid = 'public.i_ledger_entries'::regclass
        and contype = 'c'
        and pg_get_constraintdef(oid) ilike '%cadence%'
    loop
      execute format('alter table public.i_ledger_entries drop constraint %I', cname);
    end loop;

    alter table public.i_ledger_entries
      add constraint i_ledger_entries_cadence_check
      check (cadence is null or cadence in ('WEEKLY','BIWEEKLY','MONTHLY','EVERY_30_DAYS','QUARTERLY','YEARLY','CUSTOM'));

    alter table public.i_ledger_entries
      add column if not exists interval_days integer
      check (interval_days is null or interval_days > 0);
  end if;
end$$;

-- ---- i_income_entries -------------------------------------------------------
do $$
declare cname text;
begin
  if to_regclass('public.i_income_entries') is null then
    raise notice 'Skipping i_income_entries — table does not exist yet. Run add_income.sql first if you need it.';
  else
    for cname in
      select conname from pg_constraint
      where conrelid = 'public.i_income_entries'::regclass
        and contype = 'c'
        and pg_get_constraintdef(oid) ilike '%cadence%'
    loop
      execute format('alter table public.i_income_entries drop constraint %I', cname);
    end loop;

    alter table public.i_income_entries
      add constraint i_income_entries_cadence_check
      check (cadence is null or cadence in ('WEEKLY','BIWEEKLY','MONTHLY','EVERY_30_DAYS','QUARTERLY','YEARLY','CUSTOM'));

    alter table public.i_income_entries
      add column if not exists interval_days integer
      check (interval_days is null or interval_days > 0);
  end if;
end$$;
