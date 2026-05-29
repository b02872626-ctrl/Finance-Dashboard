-- =============================================================================
-- Admin sessions + admin read policies for Finance Lore.
--
-- Adds:
--   • public.i_sessions          — per-user app sessions (time-spent)
--   • public.is_admin()          — helper that gates by JWT email claim
--   • Admin SELECT policies      — i_users, i_sessions, i_transactions,
--                                  i_raw_data become readable to the admin
--   • public.admin_user_overview — view with per-user aggregates for the
--                                  Web/admin/ dashboard
--
-- The admin gate is the email allowlist inside is_admin(). To add more
-- admins, edit that function (or migrate it to read an `is_admin` column on
-- i_users for a UI-managed list).
--
-- Safe to run multiple times.
-- =============================================================================

create table if not exists public.i_sessions (
  id                uuid        primary key default gen_random_uuid(),
  user_id           uuid        not null references auth.users(id) on delete cascade,
  client            text        not null check (client in ('web', 'android')),
  started_at        timestamptz not null default now(),
  last_heartbeat_at timestamptz not null default now(),
  ended_at          timestamptz,
  duration_seconds  integer,
  app_version       text,
  user_agent        text,
  created_at        timestamptz not null default now()
);

create index if not exists i_sessions_user_started_idx
  on public.i_sessions (user_id, started_at desc);
create index if not exists i_sessions_started_idx
  on public.i_sessions (started_at desc);

alter table public.i_sessions enable row level security;

-- Owner policies — each user inserts/updates/reads their own sessions.
drop policy if exists "i_sessions_owner_select" on public.i_sessions;
drop policy if exists "i_sessions_owner_insert" on public.i_sessions;
drop policy if exists "i_sessions_owner_update" on public.i_sessions;

create policy "i_sessions_owner_select" on public.i_sessions
  for select using (auth.uid() = user_id);
create policy "i_sessions_owner_insert" on public.i_sessions
  for insert with check (auth.uid() = user_id);
create policy "i_sessions_owner_update" on public.i_sessions
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

grant select, insert, update on public.i_sessions to authenticated;

-- =============================================================================
-- Admin helper — single source of truth for the admin gate.
-- =============================================================================
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public, auth
as $$
  select coalesce(
    (auth.jwt() ->> 'email') in (
      'aklogteferada@gmail.com',
      'nahusenaygebreamlak@gmail.com'
    ),
    false
  );
$$;

grant execute on function public.is_admin() to authenticated;

-- =============================================================================
-- Admin SELECT policies. Postgres OR's permissive policies for the same
-- command, so these stack on top of the existing per-user policies without
-- weakening them for non-admin users.
-- =============================================================================
drop policy if exists "i_sessions_admin_select"     on public.i_sessions;
drop policy if exists "i_users_admin_select"        on public.i_users;
drop policy if exists "i_transactions_admin_select" on public.i_transactions;
drop policy if exists "i_raw_data_admin_select"     on public.i_raw_data;

create policy "i_sessions_admin_select" on public.i_sessions
  for select to authenticated using (public.is_admin());

create policy "i_users_admin_select" on public.i_users
  for select to authenticated using (public.is_admin());

create policy "i_transactions_admin_select" on public.i_transactions
  for select to authenticated using (public.is_admin());

create policy "i_raw_data_admin_select" on public.i_raw_data
  for select to authenticated using (public.is_admin());

-- =============================================================================
-- Per-user aggregate view consumed by Web/admin/.
-- security_invoker = on  means RLS on the underlying tables decides what each
-- caller can see, so non-admins see only their own row and the admin sees all.
-- =============================================================================
create or replace view public.admin_user_overview
with (security_invoker = on) as
select
  u.id,
  u.email,
  u.name,
  u.account_created_at,
  u.last_sign_in_at,
  (
    select count(*)::int
    from public.i_sessions s
    where s.user_id = u.id
  ) as session_count,
  (
    select coalesce(sum(coalesce(s.duration_seconds, 0)), 0)::bigint
    from public.i_sessions s
    where s.user_id = u.id
  ) as total_seconds,
  (
    select max(coalesce(s.ended_at, s.last_heartbeat_at))
    from public.i_sessions s
    where s.user_id = u.id
  ) as last_session_at,
  (
    select count(*)::int
    from public.i_transactions t
    where t.user_id = u.id
  ) as txn_count,
  (
    select count(*)::int
    from public.i_raw_data r
    where r.user_id = u.id
  ) as raw_count
from public.i_users u;

grant select on public.admin_user_overview to authenticated;
