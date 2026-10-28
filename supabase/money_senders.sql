-- =============================================================================
-- Banks Kash doesn't follow yet (admin "New money senders").
--
-- From app 1.0.11, once a week each phone reports business sender names
-- (never phone numbers, never message text) that aren't on the bank list
-- but whose SMS read like money moving, with a message count. Real banks on
-- this list get added to SmsParserEngine's sender list; the raw SMS then
-- arrive in i_raw_data for parser work.
--
-- Paste into the Supabase SQL editor and run. Safe to run more than once.
-- =============================================================================

create or replace view public.admin_money_senders
with (security_invoker = on) as
with latest as (
  -- Each phone's newest report per sender.
  select distinct on (e.user_id, e.props->>'sender')
    e.user_id,
    e.props->>'sender'                       as sender,
    (e.props->>'count')::int                 as messages,
    to_timestamp(((e.props->>'latest_at')::bigint) / 1000.0) as last_sms_at,
    e.occurred_at
  from public.i_events e
  where e.name = 'money_sender_seen'
    and e.props ? 'sender'
  order by e.user_id, e.props->>'sender', e.occurred_at desc
)
select
  sender,
  count(distinct user_id) as users,
  sum(messages)           as messages,
  max(last_sms_at)        as last_sms_at,
  max(occurred_at)        as last_reported_at
from latest
group by sender
order by users desc, messages desc;

grant select on public.admin_money_senders to authenticated;
