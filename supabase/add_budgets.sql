-- =============================================================================
-- Budgets — envelope-style funded budgets that auto-refund on a cadence and
-- prompt the user to send unspent leftover to a savings goal.
--
-- Model:
--   i_budgets             one row per envelope (name, fund_amount, cadence,
--                         funding_date anchor, last_cycle_handled_at)
--   i_budget_categories   many-to-many: which categories drain this envelope
--
-- Cycle calculation is done client-side from funding_date + cadence so we
-- don't need to materialize a cycle history table. The "leftover prompt"
-- is gated by last_cycle_handled_at — once the user dismisses or saves a
-- cycle's leftover, we bump this to the cycle_end and don't ask again.
--
-- Safe to run multiple times — every change is guarded with `if not exists`,
-- the RLS policies drop-then-create, and the trigger uses create or replace.
-- =============================================================================

create table if not exists public.i_budgets (
  id                   uuid primary key default gen_random_uuid(),
  user_id              uuid not null references auth.users(id) on delete cascade,
  name                 text not null,
  emoji                text,
  -- Amount that gets deposited into the envelope at the start of each cycle.
  fund_amount          numeric(18, 2) not null check (fund_amount > 0),
  -- Reuses the same cadence enum the ledger + income tables already accept,
  -- so the budget UI can share UX with those tabs.
  cadence              text not null check (
    cadence in ('WEEKLY','BIWEEKLY','MONTHLY','EVERY_30_DAYS','QUARTERLY','YEARLY','CUSTOM')
  ),
  interval_days        integer check (interval_days is null or interval_days > 0),
  -- Anchor date: the first funding day. All future cycles are computed by
  -- stepping forward in `cadence` units from this date.
  funding_date         date not null,
  -- End-date of the most recent cycle whose leftover the user has handled
  -- (either skipped or saved to a goal). When current_cycle_start exceeds
  -- this, the app shows the "send leftover to goal?" prompt for the
  -- previous cycle. Null until the first dismissal.
  last_cycle_handled_at date,
  status               text not null default 'ACTIVE'
    check (status in ('ACTIVE','PAUSED','ARCHIVED')),
  created_at           timestamptz not null default now(),
  updated_at           timestamptz not null default now()
);

create index if not exists i_budgets_user_status_idx
  on public.i_budgets(user_id, status);

-- =============================================================================
-- i_budget_categories — which categories drain which budget.
--
-- Composite PK (budget_id, category) so a single category can't be listed
-- twice on the same budget. A category CAN belong to multiple budgets (we
-- don't enforce uniqueness at the DB layer — the app picks the right one
-- by status, and the UI prevents double-assignment on active budgets).
-- =============================================================================
create table if not exists public.i_budget_categories (
  budget_id  uuid not null references public.i_budgets(id) on delete cascade,
  user_id    uuid not null references auth.users(id) on delete cascade,
  category   text not null,
  created_at timestamptz not null default now(),
  primary key (budget_id, category)
);

create index if not exists i_budget_categories_user_idx
  on public.i_budget_categories(user_id, category);

-- =============================================================================
-- Row-Level Security
-- =============================================================================
alter table public.i_budgets             enable row level security;
alter table public.i_budget_categories   enable row level security;

drop policy if exists "budgets_owner_select" on public.i_budgets;
drop policy if exists "budgets_owner_insert" on public.i_budgets;
drop policy if exists "budgets_owner_update" on public.i_budgets;
drop policy if exists "budgets_owner_delete" on public.i_budgets;

create policy "budgets_owner_select" on public.i_budgets
  for select using (auth.uid() = user_id);
create policy "budgets_owner_insert" on public.i_budgets
  for insert with check (auth.uid() = user_id);
create policy "budgets_owner_update" on public.i_budgets
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "budgets_owner_delete" on public.i_budgets
  for delete using (auth.uid() = user_id);

drop policy if exists "budget_categories_owner_select" on public.i_budget_categories;
drop policy if exists "budget_categories_owner_insert" on public.i_budget_categories;
drop policy if exists "budget_categories_owner_delete" on public.i_budget_categories;

create policy "budget_categories_owner_select" on public.i_budget_categories
  for select using (auth.uid() = user_id);
create policy "budget_categories_owner_insert" on public.i_budget_categories
  for insert with check (auth.uid() = user_id);
create policy "budget_categories_owner_delete" on public.i_budget_categories
  for delete using (auth.uid() = user_id);

-- =============================================================================
-- updated_at trigger — reuses touch_updated_at() defined in add_goals.sql /
-- add_daily_review.sql. Define here too so this migration is self-contained.
-- =============================================================================
create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists i_budgets_touch on public.i_budgets;
create trigger i_budgets_touch
  before update on public.i_budgets
  for each row execute procedure public.touch_updated_at();
