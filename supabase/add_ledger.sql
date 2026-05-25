-- =============================================================================
-- Ledger feature — personal IOUs (one-off) + recurring commitments (rent etc.)
-- Mirrors the lightweight model agreed for the Web dashboard:
--   • principal + balance + due date (no interest math)
--   • direction: I_OWE | OWED_TO_ME
--   • type:      IOU   | RECURRING
--   • status:    ACTIVE | SETTLED
-- =============================================================================

create table if not exists public.i_ledger_entries (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  type        text not null check (type in ('IOU', 'RECURRING')),
  direction   text not null check (direction in ('I_OWE', 'OWED_TO_ME')),
  counterparty text not null,
  principal   numeric(18, 2) not null check (principal >= 0),
  balance     numeric(18, 2) not null check (balance >= 0),
  currency    text not null default 'ETB',
  due_date    date,
  cadence     text check (cadence in ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
  parent_id   uuid references public.i_ledger_entries(id) on delete set null,
  note        text,
  status      text not null default 'ACTIVE' check (status in ('ACTIVE', 'SETTLED', 'ARCHIVED')),
  created_at  timestamptz not null default now(),
  settled_at  timestamptz,
  updated_at  timestamptz not null default now()
);

create index if not exists i_ledger_entries_user_status_idx
  on public.i_ledger_entries(user_id, status);
create index if not exists i_ledger_entries_user_due_idx
  on public.i_ledger_entries(user_id, due_date)
  where status = 'ACTIVE';

create table if not exists public.i_ledger_repayments (
  id          uuid primary key default gen_random_uuid(),
  entry_id    uuid not null references public.i_ledger_entries(id) on delete cascade,
  user_id     uuid not null references auth.users(id) on delete cascade,
  amount      numeric(18, 2) not null check (amount > 0),
  paid_at     timestamptz not null default now(),
  note        text,
  created_at  timestamptz not null default now()
);

create index if not exists i_ledger_repayments_entry_idx
  on public.i_ledger_repayments(entry_id);

-- =============================================================================
-- Row-Level Security — each user sees only their rows.
-- =============================================================================
alter table public.i_ledger_entries    enable row level security;
alter table public.i_ledger_repayments enable row level security;

drop policy if exists "ledger_entries_owner_select" on public.i_ledger_entries;
drop policy if exists "ledger_entries_owner_insert" on public.i_ledger_entries;
drop policy if exists "ledger_entries_owner_update" on public.i_ledger_entries;
drop policy if exists "ledger_entries_owner_delete" on public.i_ledger_entries;

create policy "ledger_entries_owner_select" on public.i_ledger_entries
  for select using (auth.uid() = user_id);
create policy "ledger_entries_owner_insert" on public.i_ledger_entries
  for insert with check (auth.uid() = user_id);
create policy "ledger_entries_owner_update" on public.i_ledger_entries
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "ledger_entries_owner_delete" on public.i_ledger_entries
  for delete using (auth.uid() = user_id);

drop policy if exists "ledger_repayments_owner_select" on public.i_ledger_repayments;
drop policy if exists "ledger_repayments_owner_insert" on public.i_ledger_repayments;
drop policy if exists "ledger_repayments_owner_delete" on public.i_ledger_repayments;

create policy "ledger_repayments_owner_select" on public.i_ledger_repayments
  for select using (auth.uid() = user_id);
create policy "ledger_repayments_owner_insert" on public.i_ledger_repayments
  for insert with check (auth.uid() = user_id);
create policy "ledger_repayments_owner_delete" on public.i_ledger_repayments
  for delete using (auth.uid() = user_id);

-- =============================================================================
-- Keep updated_at fresh on every UPDATE.
-- =============================================================================
create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists i_ledger_entries_touch on public.i_ledger_entries;
create trigger i_ledger_entries_touch
  before update on public.i_ledger_entries
  for each row execute procedure public.touch_updated_at();
