-- =============================================================================
-- Backfill i_sessions from existing i_transactions for the admin dashboard.
--
-- Approach
--   Each i_transactions row marks a moment the user was actively using the
--   app (SMS parsed → row created). We cluster a user's transactions into
--   "sessions" by gap: any two consecutive txns within 10 minutes belong to
--   the same session. The session's started_at = first txn, ended_at /
--   last_heartbeat_at = last txn. Single-txn sessions get a 60-second floor
--   so the duration bar isn't zero-height in charts.
--
-- Idempotent — rerunning the script first deletes prior backfill rows. The
-- marker `app_version = 'backfill-v1'` lets you wipe these later with:
--    delete from public.i_sessions where app_version = 'backfill-v1';
--
-- Scope
--   Runs across all users. Each user's history is reconstructed from their
--   own txn timeline. Real (forward) sessions inserted by the app at
--   runtime carry app_version != 'backfill-v1' and are untouched.
-- =============================================================================

begin;

delete from public.i_sessions where app_version = 'backfill-v1';

with ordered as (
  select
    user_id,
    occurred_at,
    lag(occurred_at) over (partition by user_id order by occurred_at) as prev_at
  from public.i_transactions
),
flagged as (
  select
    user_id,
    occurred_at,
    case
      when prev_at is null
        or occurred_at - prev_at > interval '10 minutes'
      then 1
      else 0
    end as is_new_session
  from ordered
),
grouped as (
  select
    user_id,
    occurred_at,
    sum(is_new_session) over (
      partition by user_id
      order by occurred_at
      rows between unbounded preceding and current row
    ) as session_seq
  from flagged
),
sessions as (
  select
    user_id,
    min(occurred_at) as started_at,
    max(occurred_at) as last_at,
    count(*)         as txn_count
  from grouped
  group by user_id, session_seq
)
insert into public.i_sessions (
  user_id,
  client,
  started_at,
  last_heartbeat_at,
  ended_at,
  duration_seconds,
  app_version,
  user_agent
)
select
  user_id,
  'android' as client,
  started_at,
  last_at as last_heartbeat_at,
  last_at as ended_at,
  greatest(60, extract(epoch from last_at - started_at)::int) as duration_seconds,
  'backfill-v1' as app_version,
  'synthesized from i_transactions' as user_agent
from sessions;

commit;

-- Quick sanity check after running:
--   select count(*) as inserted from public.i_sessions where app_version = 'backfill-v1';
--   select min(started_at), max(started_at) from public.i_sessions where app_version = 'backfill-v1';
