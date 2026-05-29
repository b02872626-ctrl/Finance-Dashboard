-- =============================================================================
-- Daily Review — augments i_transactions with the prediction + review-status
-- columns the Daily Review queue needs, and adds i_category_rules: the
-- user-owned merchant_key -> category map the categorizer reads at parse time
-- and writes to when the user picks/changes a category.
--
-- review_status state machine:
--   PENDING    parser emitted a row, user hasn't touched it yet
--   CONFIRMED  user accepted the predicted category (or category was set
--              before this migration existed — backfilled below)
--   CHANGED    user picked a different category than the prediction
--   SKIPPED    user swiped left — the queue will ask again tomorrow
--   NOT_A_TXN  user marked it as not a real transaction (e.g. promo SMS
--              that slipped past the parser)
--
-- merchant_key is a normalized lookup key (uppercase, refs/dates stripped)
-- computed client-side by MerchantKey.normalize. Stored on every row so
-- "apply to similar" + recurring detection can JOIN cheaply.
--
-- Safe to run multiple times — every change is guarded with `if not exists`,
-- the RLS policies drop-then-create, and the row backfill only touches rows
-- whose review_status is still null (i.e. pre-migration rows).
-- =============================================================================

-- ---- i_transactions: new columns -------------------------------------------
alter table public.i_transactions
  add column if not exists predicted_category   text,
  add column if not exists category_confidence  double precision
    check (category_confidence is null
       or (category_confidence >= 0 and category_confidence <= 1)),
  add column if not exists review_status        text,
  add column if not exists merchant_key         text;

-- Backfill the status of pre-migration rows: anything with a category is
-- treated as already-confirmed; anything without a category is PENDING and
-- will surface in tomorrow's Daily Review.
update public.i_transactions
   set review_status = case
         when category is not null and length(trim(category)) > 0 then 'CONFIRMED'
         else 'PENDING'
       end
 where review_status is null;

-- Now that every row has a status we can enforce the check + NOT NULL.
do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.i_transactions'::regclass
      and conname = 'i_transactions_review_status_check'
  ) then
    alter table public.i_transactions
      add constraint i_transactions_review_status_check
      check (review_status in ('PENDING','CONFIRMED','CHANGED','SKIPPED','NOT_A_TXN'));
  end if;
end$$;

alter table public.i_transactions
  alter column review_status set default 'PENDING';

alter table public.i_transactions
  alter column review_status set not null;

-- Index for the Home entry card: how many PENDING txns today?
create index if not exists i_transactions_user_status_idx
  on public.i_transactions(user_id, review_status, occurred_at desc);

-- Index for "apply to similar" + recurring counterparty queries.
create index if not exists i_transactions_user_merchant_key_idx
  on public.i_transactions(user_id, merchant_key)
  where merchant_key is not null;

-- =============================================================================
-- i_category_rules — the user-owned merchant_key -> category map.
--
-- One row per (user, merchant_key). match_count bumps every time the user
-- confirms/changes that merchant; the categorizer turns count into confidence
-- via a log scale capped at 0.99. updated_at lets us age out stale rules.
-- =============================================================================
create table if not exists public.i_category_rules (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users(id) on delete cascade,
  merchant_key text not null,
  category     text not null,
  match_count  integer not null default 1 check (match_count > 0),
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  unique (user_id, merchant_key)
);

create index if not exists i_category_rules_user_key_idx
  on public.i_category_rules(user_id, merchant_key);

-- =============================================================================
-- Row-Level Security — every rule belongs to a single user.
-- =============================================================================
alter table public.i_category_rules enable row level security;

drop policy if exists "category_rules_owner_select" on public.i_category_rules;
drop policy if exists "category_rules_owner_insert" on public.i_category_rules;
drop policy if exists "category_rules_owner_update" on public.i_category_rules;
drop policy if exists "category_rules_owner_delete" on public.i_category_rules;

create policy "category_rules_owner_select" on public.i_category_rules
  for select using (auth.uid() = user_id);
create policy "category_rules_owner_insert" on public.i_category_rules
  for insert with check (auth.uid() = user_id);
create policy "category_rules_owner_update" on public.i_category_rules
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "category_rules_owner_delete" on public.i_category_rules
  for delete using (auth.uid() = user_id);

-- =============================================================================
-- updated_at trigger — reuse the touch_updated_at() function from add_goals.sql
-- but define it here too so this migration is self-contained.
-- =============================================================================
create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists i_category_rules_touch on public.i_category_rules;
create trigger i_category_rules_touch
  before update on public.i_category_rules
  for each row execute procedure public.touch_updated_at();
