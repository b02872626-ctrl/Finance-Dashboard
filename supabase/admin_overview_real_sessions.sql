-- =============================================================================
-- admin_user_overview: count only real app sessions.
--
-- backfill_sessions_from_transactions.sql synthesised ~5k i_sessions rows from
-- SMS timestamps (app_version = 'backfill-v1'). Those reflect when a bank sent
-- a text, not when anyone used the app, and they dominated the dashboard's
-- Sessions / Total time / Last seen numbers. This rebuilds the view so the
-- aggregates skip them; Web/admin/admin.js filters its direct i_sessions
-- queries the same way.
--
-- The backfill rows themselves are left in place. To remove them for good:
--    delete from public.i_sessions where app_version = 'backfill-v1';
--
-- Safe to run multiple times.
-- =============================================================================

create or replace view public.admin_user_overview
with (security_invoker = on) as
with real_sessions as (
  select *
  from public.i_sessions
  where app_version is distinct from 'backfill-v1'
)
select
  u.id,
  u.email,
  u.name,
  u.account_created_at,
  u.last_sign_in_at,
  (
    select count(*)::int
    from real_sessions s
    where s.user_id = u.id
  ) as session_count,
  (
    select coalesce(sum(coalesce(s.duration_seconds, 0)), 0)::bigint
    from real_sessions s
    where s.user_id = u.id
  ) as total_seconds,
  (
    select max(coalesce(s.ended_at, s.last_heartbeat_at))
    from real_sessions s
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
