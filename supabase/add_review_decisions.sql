-- =============================================================================
-- Review decisions (web dashboard, Habits tab): what the user said about
-- something Kash found, so it is asked once.
--   kind     — 'pass_through': money in that went straight out again
--                 ref = the incoming transaction's id (i_transactions.id)
--                 detail = { "out_ids": [ids of the outgoing transactions] }
--                 decision 'yes' takes all of them out of income and spending
--              'salary': a suggested salary payer
--                 ref = the payer's name key; decision 'no' stops suggesting
--                 them (a 'yes' is saved as an income entry instead)
--   decision — 'yes' | 'no'
-- Removing a row undoes the decision.
-- =============================================================================

create table if not exists public.i_review_decisions (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  kind        text not null check (kind in ('pass_through', 'salary')),
  ref         text not null check (char_length(ref) between 1 and 120),
  decision    text not null check (decision in ('yes', 'no')),
  detail      jsonb not null default '{}'::jsonb,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  unique (user_id, kind, ref)
);

create index if not exists i_review_decisions_user_idx on public.i_review_decisions(user_id, kind);

-- =============================================================================
-- Row-Level Security: owner only.
-- =============================================================================
alter table public.i_review_decisions enable row level security;

drop policy if exists "review_decisions_owner_select" on public.i_review_decisions;
drop policy if exists "review_decisions_owner_insert" on public.i_review_decisions;
drop policy if exists "review_decisions_owner_update" on public.i_review_decisions;
drop policy if exists "review_decisions_owner_delete" on public.i_review_decisions;

create policy "review_decisions_owner_select" on public.i_review_decisions
  for select using (auth.uid() = user_id);
create policy "review_decisions_owner_insert" on public.i_review_decisions
  for insert with check (auth.uid() = user_id);
create policy "review_decisions_owner_update" on public.i_review_decisions
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "review_decisions_owner_delete" on public.i_review_decisions
  for delete using (auth.uid() = user_id);
