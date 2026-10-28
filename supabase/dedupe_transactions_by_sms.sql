-- =============================================================================
-- One transaction per SMS in i_transactions (admin "Duplicate rate").
--
-- Why there were copies: the row id was a hash of what the parser produced
-- (type, bank, reference, counterparty…). Each time a newer app version read
-- the same SMS a little differently, it uploaded the SMS again under a new
-- id and the old row stayed. Older builds also used other ids for identical
-- rows.
--
-- From app 1.0.10 the id is a hash of the SMS itself — sender + exact text
-- (+ the "manual-…" reference for hand-added entries). This file:
--   1. defines that hash in SQL (must match SupabaseTransactionKey.kt),
--   2. sets it on every insert, so even old app versions land on one row,
--   3. merges the existing copies, keeping the user's category choices,
--   4. limits the admin bug queue to SMS whose status the device reported.
--
-- Safe to run more than once. Paste into the Supabase SQL editor and run.
-- =============================================================================

-- 1. The id: SHA-256 of the UTF-8 text, first 8 bytes as a big-endian
--    bigint, sign bit cleared, 0 → 1.
create or replace function public.kash_txn_key(sender text, raw_body text, ref_num text)
returns bigint
language sql
immutable
as $$
  select case when k = 0 then 1 else k end
  from (
    select ((('x' || substr(encode(sha256(convert_to(
              coalesce(sender, '') || chr(10) || coalesce(raw_body, '')
              || case when sender = 'manual' then chr(10) || coalesce(ref_num, '') else '' end,
            'UTF8')), 'hex'), 1, 16))::bit(64)::bigint) & 9223372036854775807) as k
  ) s;
$$;

-- 2. Every insert (and upsert) gets the SMS id, whatever the client sent.
--    Postgres applies BEFORE INSERT triggers before the ON CONFLICT check,
--    so an upload of an SMS that is already stored updates that row.
create or replace function public.kash_txn_set_id()
returns trigger
language plpgsql
as $$
begin
  new.id := public.kash_txn_key(new.sender, new.raw_body, new.ref_num);
  return new;
end;
$$;

drop trigger if exists kash_txn_set_id on public.i_transactions;
create trigger kash_txn_set_id
  before insert on public.i_transactions
  for each row execute function public.kash_txn_set_id();

-- 3. Merge the existing copies. One DO block = one transaction, so it all
--    happens or none of it does (the SQL editor commits statement by
--    statement, which a begin/commit pair can't rely on).
do $$
begin
  -- Every row with its SMS id. Keep one per SMS: a parsed row over UNKNOWN,
  -- then the newest upload (the newest parser).
  create temp table kash_rows as
  select
    t.user_id,
    t.id,
    public.kash_txn_key(t.sender, t.raw_body, t.ref_num) as nk,
    row_number() over (
      partition by t.user_id, public.kash_txn_key(t.sender, t.raw_body, t.ref_num)
      order by (t.type = 'UNKNOWN'), t.created_at desc, t.id desc
    ) as rn
  from public.i_transactions t;

  -- The user's own decision on any copy (newest first): never lose a category.
  create temp table kash_decisions as
  select distinct on (r.user_id, r.nk)
    r.user_id, r.nk, t.category, t.review_status
  from kash_rows r
  join public.i_transactions t on t.user_id = r.user_id and t.id = r.id
  where coalesce(t.review_status, 'PENDING') <> 'PENDING'
  order by r.user_id, r.nk, t.created_at desc, t.id desc;

  delete from public.i_transactions t
  using kash_rows r
  where t.user_id = r.user_id and t.id = r.id and r.rn > 1;

  update public.i_transactions t
  set category = d.category,
      review_status = d.review_status
  from kash_rows r
  join kash_decisions d on d.user_id = r.user_id and d.nk = r.nk
  where t.user_id = r.user_id and t.id = r.id and r.rn = 1;

  -- Move each kept row to its SMS id. Via negative ids so no step can clash
  -- with a row that still has its old id.
  update public.i_transactions t
  set id = -r.nk
  from kash_rows r
  where t.user_id = r.user_id and t.id = r.id and r.rn = 1 and t.id <> r.nk;

  update public.i_transactions set id = -id where id < 0;

  drop table kash_decisions;
  drop table kash_rows;
end;
$$;

-- 4. The bug queue: only SMS the device itself marked UNPARSED. Rows sent
--    before 1.0.9 have no status; devices re-send them with one after they
--    update (once per parser-rules version), so they show up here only if
--    the current parser really misses them.
create or replace view public.admin_unparsed_sms
with (security_invoker = on) as
select
  r.id,
  r.sender_group,
  r.sender_name,
  r.raw_message,
  r.received_at,
  r.status_inferred,
  r.user_id,
  u.email
from public.admin_raw_sms r
left join public.i_users u on u.id = r.user_id
where r.sender_group is not null
  and r.parse_status = 'UNPARSED'
  and not r.status_inferred
order by r.received_at desc
limit 300;

grant select on public.admin_unparsed_sms to authenticated;

-- Check: should now be (close to) 0.
select duplicates, total from public.admin_txn_quality;
