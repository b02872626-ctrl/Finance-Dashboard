-- =============================================================================
-- Habits (web dashboard, Habits tab): which people and places the user counts
-- as which habit. One row per name key, e.g. name_key "abebe keb" → "Food".
-- The methods (paybacks, chains, payday) are the same for everyone and live in
-- Web/habits.js; only the user's own labels are stored here.
--   name_key — a name key (Web/habits.js personKey): a payment belongs to the
--            row when its counterparty, lower-cased, starts with it
--   habit  — the user's own habit name
--   shared — friends usually pay the user back here (group lunch, coffee)
-- =============================================================================

create table if not exists public.i_habit_rules (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  name_key    text not null check (char_length(name_key) between 1 and 80),
  habit       text not null check (char_length(habit) between 1 and 40),
  shared      boolean not null default false,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  unique (user_id, name_key)
);

create index if not exists i_habit_rules_user_idx on public.i_habit_rules(user_id);

-- =============================================================================
-- Row-Level Security: owner only.
-- =============================================================================
alter table public.i_habit_rules enable row level security;

drop policy if exists "habit_rules_owner_select" on public.i_habit_rules;
drop policy if exists "habit_rules_owner_insert" on public.i_habit_rules;
drop policy if exists "habit_rules_owner_update" on public.i_habit_rules;
drop policy if exists "habit_rules_owner_delete" on public.i_habit_rules;

create policy "habit_rules_owner_select" on public.i_habit_rules
  for select using (auth.uid() = user_id);
create policy "habit_rules_owner_insert" on public.i_habit_rules
  for insert with check (auth.uid() = user_id);
create policy "habit_rules_owner_update" on public.i_habit_rules
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "habit_rules_owner_delete" on public.i_habit_rules
  for delete using (auth.uid() = user_id);
