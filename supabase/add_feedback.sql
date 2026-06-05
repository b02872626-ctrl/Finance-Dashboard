-- =============================================================================
-- Tester feedback — one row per submission of the in-app feedback survey.
--
-- Answers are stored as a single JSONB blob keyed by the question id from
-- FEEDBACK_QUESTIONS in Web/app.js (perm_feel, wrong_guess, prefer, parse,
-- before, freq, opened_for, calendar, keep, recommend, change, sentence,
-- trust_total, unknown). Using JSONB instead of a column per question lets
-- us tweak the survey freely without schema migrations.
--
-- Safe to run multiple times — every change is guarded with `if not exists`
-- and the RLS policies drop-then-create.
-- =============================================================================

create table if not exists public.i_feedback (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid references auth.users(id) on delete set null,
  submitted_at timestamptz not null default now(),
  user_agent   text,
  client       text not null default 'web',  -- 'web' | 'android' if mobile ever submits
  answers      jsonb not null
);

create index if not exists i_feedback_user_submitted_idx
  on public.i_feedback(user_id, submitted_at desc);
create index if not exists i_feedback_submitted_idx
  on public.i_feedback(submitted_at desc);

-- =============================================================================
-- Row-Level Security
--   * Any authenticated user can INSERT their own row (user_id must match
--     auth.uid()).
--   * A user can SELECT their own submissions (lets the admin dashboard
--     show "your past feedback" too).
--   * Cross-user reads require the service-role key (used by the admin
--     dashboard's serverless functions, or read directly in the Supabase
--     Table Editor).
-- =============================================================================
alter table public.i_feedback enable row level security;

drop policy if exists "feedback_owner_insert" on public.i_feedback;
drop policy if exists "feedback_owner_select" on public.i_feedback;

create policy "feedback_owner_insert" on public.i_feedback
  for insert with check (auth.uid() = user_id);

create policy "feedback_owner_select" on public.i_feedback
  for select using (auth.uid() = user_id);
