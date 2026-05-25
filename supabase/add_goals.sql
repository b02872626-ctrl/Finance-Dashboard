-- =============================================================================
-- Goals feature — manual savings goals with priority-waterfall allocation.
-- Types:
--   DATE_BOUND  → fixed target_amount by a deadline
--   OPEN_ENDED  → target derived from data (target_months × avg monthly spend)
--   RECURRING   → save target_amount each month indefinitely
-- =============================================================================

create table if not exists public.i_goals (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references auth.users(id) on delete cascade,
  name          text not null,
  emoji         text,
  type          text not null check (type in ('DATE_BOUND', 'OPEN_ENDED', 'RECURRING')),
  -- DATE_BOUND  → required (absolute target)
  -- OPEN_ENDED  → null (target is computed live from target_months × avg spend)
  -- RECURRING   → required (monthly contribution target)
  target_amount numeric(18, 2) check (target_amount is null or target_amount >= 0),
  -- OPEN_ENDED only: how many months of spend to bank.
  target_months integer check (target_months is null or target_months > 0),
  -- DATE_BOUND only.
  deadline      date,
  -- Lower number = higher priority. Used for the auto-waterfall contribution.
  priority      integer not null default 100,
  note          text,
  status        text not null default 'ACTIVE' check (status in ('ACTIVE', 'ACHIEVED', 'ARCHIVED')),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  achieved_at   timestamptz
);

create index if not exists i_goals_user_status_idx
  on public.i_goals(user_id, status, priority);

create table if not exists public.i_goal_contributions (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null references auth.users(id) on delete cascade,
  goal_id        uuid not null references public.i_goals(id) on delete cascade,
  amount         numeric(18, 2) not null check (amount > 0),
  contributed_at timestamptz not null default now(),
  note           text,
  created_at     timestamptz not null default now()
);

create index if not exists i_goal_contributions_goal_idx
  on public.i_goal_contributions(goal_id, contributed_at desc);
create index if not exists i_goal_contributions_user_idx
  on public.i_goal_contributions(user_id, contributed_at desc);

-- =============================================================================
-- Row-Level Security
-- =============================================================================
alter table public.i_goals               enable row level security;
alter table public.i_goal_contributions  enable row level security;

drop policy if exists "goals_owner_select" on public.i_goals;
drop policy if exists "goals_owner_insert" on public.i_goals;
drop policy if exists "goals_owner_update" on public.i_goals;
drop policy if exists "goals_owner_delete" on public.i_goals;

create policy "goals_owner_select" on public.i_goals
  for select using (auth.uid() = user_id);
create policy "goals_owner_insert" on public.i_goals
  for insert with check (auth.uid() = user_id);
create policy "goals_owner_update" on public.i_goals
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "goals_owner_delete" on public.i_goals
  for delete using (auth.uid() = user_id);

drop policy if exists "goal_contributions_owner_select" on public.i_goal_contributions;
drop policy if exists "goal_contributions_owner_insert" on public.i_goal_contributions;
drop policy if exists "goal_contributions_owner_delete" on public.i_goal_contributions;

create policy "goal_contributions_owner_select" on public.i_goal_contributions
  for select using (auth.uid() = user_id);
create policy "goal_contributions_owner_insert" on public.i_goal_contributions
  for insert with check (auth.uid() = user_id);
create policy "goal_contributions_owner_delete" on public.i_goal_contributions
  for delete using (auth.uid() = user_id);

-- =============================================================================
-- updated_at trigger
-- =============================================================================
create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

drop trigger if exists i_goals_touch on public.i_goals;
create trigger i_goals_touch
  before update on public.i_goals
  for each row execute procedure public.touch_updated_at();
