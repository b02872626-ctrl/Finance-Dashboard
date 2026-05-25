-- =============================================================================
-- Income feature — track one-off and recurring incoming money.
-- Recurring entries are seeded via auto-detection from CREDIT SMS transactions
-- (matching: same counterparty + amount within ±10% + ~monthly cadence).
-- =============================================================================

create table if not exists public.i_income_entries (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null references auth.users(id) on delete cascade,
  type            text not null check (type in ('ONEOFF', 'RECURRING')),
  source          text not null,
  amount          numeric(18, 2) not null check (amount >= 0),
  currency        text not null default 'ETB',
  -- ONEOFF   → date the income was received
  -- RECURRING → date the next instance is expected
  occurs_on       date,
  cadence         text check (cadence in ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
  -- For RECURRING entries created from detection, this stores the counterparty
  -- key we matched against so we can re-detect and update last_received_at.
  counterparty_match text,
  last_received_at timestamptz,
  note            text,
  status          text not null default 'ACTIVE' check (status in ('ACTIVE', 'ARCHIVED')),
  created_at      timestamptz not null default now(),
  updated_at      timestamptz not null default now()
);

create index if not exists i_income_entries_user_status_idx
  on public.i_income_entries(user_id, status);

-- Lets the user dismiss a detected pattern they don't want to track. Keyed by
-- a normalised counterparty match so future detections respect the dismissal.
create table if not exists public.i_income_dismissed (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users(id) on delete cascade,
  counterparty_match text not null,
  dismissed_at timestamptz not null default now(),
  unique (user_id, counterparty_match)
);

create index if not exists i_income_dismissed_user_idx
  on public.i_income_dismissed(user_id);

-- =============================================================================
-- Row-Level Security
-- =============================================================================
alter table public.i_income_entries    enable row level security;
alter table public.i_income_dismissed  enable row level security;

drop policy if exists "income_entries_owner_select" on public.i_income_entries;
drop policy if exists "income_entries_owner_insert" on public.i_income_entries;
drop policy if exists "income_entries_owner_update" on public.i_income_entries;
drop policy if exists "income_entries_owner_delete" on public.i_income_entries;

create policy "income_entries_owner_select" on public.i_income_entries
  for select using (auth.uid() = user_id);
create policy "income_entries_owner_insert" on public.i_income_entries
  for insert with check (auth.uid() = user_id);
create policy "income_entries_owner_update" on public.i_income_entries
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "income_entries_owner_delete" on public.i_income_entries
  for delete using (auth.uid() = user_id);

drop policy if exists "income_dismissed_owner_select" on public.i_income_dismissed;
drop policy if exists "income_dismissed_owner_insert" on public.i_income_dismissed;
drop policy if exists "income_dismissed_owner_delete" on public.i_income_dismissed;

create policy "income_dismissed_owner_select" on public.i_income_dismissed
  for select using (auth.uid() = user_id);
create policy "income_dismissed_owner_insert" on public.i_income_dismissed
  for insert with check (auth.uid() = user_id);
create policy "income_dismissed_owner_delete" on public.i_income_dismissed
  for delete using (auth.uid() = user_id);

-- =============================================================================
-- Keep updated_at fresh on every UPDATE (reuses touch_updated_at from
-- add_ledger.sql; the create-or-replace there will recreate it harmlessly).
-- =============================================================================
create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists i_income_entries_touch on public.i_income_entries;
create trigger i_income_entries_touch
  before update on public.i_income_entries
  for each row execute procedure public.touch_updated_at();
