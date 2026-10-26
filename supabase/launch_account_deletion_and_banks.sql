-- =============================================================================
-- v1 launch: in-app account deletion + lock down the shared bank catalog.
-- Run once in the Supabase SQL editor. Safe to re-run.
-- =============================================================================

begin;

-- -----------------------------------------------------------------------------
-- 1. delete_my_account()
--
-- Called by the app (POST /rest/v1/rpc/delete_my_account) from
-- Settings → Delete account. Deletes the caller's auth user; every user table
-- references auth.users(id) ON DELETE CASCADE (i_users, i_transactions,
-- i_raw_data, i_category_rules, budgets, goals, income, ledger, sessions), so
-- all of their rows go with it. i_feedback is ON DELETE SET NULL: survey
-- answers are kept but no longer linked to anyone.
-- -----------------------------------------------------------------------------
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;
  delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

-- -----------------------------------------------------------------------------
-- 2. i_banks: was "for all ... using (true)", i.e. any signed-in user could
-- rename or delete any bank for everyone. Now: read + insert only.
--
-- UPDATE stays technically allowed because the app syncs banks with an
-- upsert (Prefer: resolution=merge-duplicates), which PostgREST runs as
-- INSERT ... ON CONFLICT DO UPDATE and needs an UPDATE policy even when
-- nothing changes. The trigger below rejects any update that actually
-- changes a row, so existing beta builds keep syncing but nobody can edit.
-- No DELETE policy => deletes are denied.
-- -----------------------------------------------------------------------------
drop policy if exists "i_banks_write_all" on public.i_banks;
drop policy if exists "i_banks_insert" on public.i_banks;
drop policy if exists "i_banks_noop_upsert" on public.i_banks;

create policy "i_banks_insert"
on public.i_banks for insert
to authenticated
with check (nullif(trim(bank_name), '') is not null);

create policy "i_banks_noop_upsert"
on public.i_banks for update
to authenticated
using (true)
with check (true);

create or replace function public.i_banks_block_changes()
returns trigger
language plpgsql
as $$
begin
  if row(new.*) is distinct from row(old.*) then
    raise exception 'i_banks rows are read-only' using errcode = '42501';
  end if;
  return new;
end;
$$;

drop trigger if exists i_banks_block_changes on public.i_banks;
create trigger i_banks_block_changes
before update on public.i_banks
for each row execute function public.i_banks_block_changes();

revoke delete, truncate on public.i_banks from authenticated, anon;

commit;
