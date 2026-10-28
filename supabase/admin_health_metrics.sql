-- =============================================================================
-- Admin dashboard v2: parser health, categorization quality, retention, and
-- device health. Replaces the time-spent metrics the old dashboard showed.
--
-- Adds:
--   • i_raw_data.received_at / parse_status — sent by Android 1.0.9+. Older
--     rows are backfilled below by matching against i_transactions.raw_body.
--   • i_transactions.reviewed_at — stamped by trigger when a row leaves
--     PENDING/SKIPPED through a human review, so retention works for every
--     client version (and the web app) without app-side changes.
--   • public.i_events          — app telemetry: review_completed,
--                                sms_permission_granted, scan_completed,
--                                scan_failed, pin_set, error.
--   • public.i_device_status   — one row per user, upserted by the app:
--                                sync backlog, app version, PIN / SMS state.
--   • public.i_account_deletions — anonymous tombstone written by
--                                  delete_my_account() before the cascade.
--   • admin_* views            — one per dashboard card (Web/admin/).
--
-- Every view is security_invoker, so the admin SELECT policies (is_admin())
-- decide what the caller sees; non-admins only ever see their own rows.
--
-- Safe to run multiple times. Requires add_admin_sessions.sql (is_admin()).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Sender → parser group. A sender with no group is capture-only (we store its
-- SMS but have no parser for it). Mirrors SmsParserEngine.financialSenders.
-- -----------------------------------------------------------------------------
create or replace function public.admin_sender_group(sender text)
returns text
language sql
immutable
as $$
  select case
    when lower(trim(sender)) = 'cbe'                                   then 'CBE'
    when lower(trim(sender)) = '127'                                   then 'Telebirr'
    when lower(trim(sender)) = 'cbebirr'                               then 'CBE Birr'
    when lower(trim(sender)) in ('awash bank', 'awashbank', 'awash')   then 'Awash'
    when lower(trim(sender)) in ('dashenbank', 'dashen')               then 'Dashen'
    when lower(trim(sender)) in ('boa', 'abyssinia')                   then 'BoA'
    when lower(trim(sender)) in ('berhan bank', 'berhan', 'berhanbank', 'berhanbnk') then 'Berhan'
    else null
  end;
$$;

-- =============================================================================
-- 1. i_raw_data: when the SMS arrived + what the parser made of it.
--    PARSED   → a bank parser produced a real transaction type
--    UNPARSED → fell through to UnknownBankParser (broken / new template)
--    IGNORED  → matched an ignore pattern (OTP, promo, Fayda notice …)
-- =============================================================================
alter table public.i_raw_data
  add column if not exists received_at  timestamptz,
  add column if not exists parse_status text;

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.i_raw_data'::regclass
      and conname = 'i_raw_data_parse_status_check'
  ) then
    alter table public.i_raw_data
      add constraint i_raw_data_parse_status_check
      check (parse_status is null or parse_status in ('PARSED', 'UNPARSED', 'IGNORED'));
  end if;
end$$;

create index if not exists i_raw_data_received_idx
  on public.i_raw_data (coalesce(received_at, created_at) desc);

create index if not exists i_transactions_user_body_md5_idx
  on public.i_transactions (user_id, md5(raw_body));

-- Backfill rows uploaded by older builds: anything that became a transaction
-- is PARSED and takes the transaction's timestamp. The rest stay NULL until
-- the device re-uploads them (1.0.9 re-sends every raw SMS once with its
-- status); the views treat NULL as UNPARSED.
update public.i_raw_data r
   set parse_status = 'PARSED',
       received_at  = coalesce(r.received_at, t.occurred_at)
  from (
    select distinct on (user_id, md5(raw_body)) user_id, md5(raw_body) as body_md5, occurred_at
    from public.i_transactions
    where type <> 'UNKNOWN'
    order by user_id, md5(raw_body), occurred_at
  ) t
 where r.parse_status is null
   and t.user_id = r.user_id
   and t.body_md5 = md5(r.raw_message);

-- =============================================================================
-- 2. i_transactions.reviewed_at — set when a human review lands.
--    Insert: only CHANGED / NOT_A_TXN count (an insert as CONFIRMED is the
--    USER_RULE auto-confirm, which nobody looked at).
--    Update: any move from PENDING/SKIPPED into CONFIRMED/CHANGED/NOT_A_TXN.
-- =============================================================================
alter table public.i_transactions
  add column if not exists reviewed_at timestamptz;

create index if not exists i_transactions_user_reviewed_idx
  on public.i_transactions (user_id, reviewed_at desc)
  where reviewed_at is not null;

create or replace function public.i_transactions_stamp_reviewed()
returns trigger
language plpgsql
as $$
begin
  if tg_op = 'INSERT' then
    if new.review_status in ('CHANGED', 'NOT_A_TXN') and new.reviewed_at is null then
      new.reviewed_at := now();
    end if;
  elsif new.review_status in ('CONFIRMED', 'CHANGED', 'NOT_A_TXN')
        and old.review_status in ('PENDING', 'SKIPPED') then
    new.reviewed_at := now();
  elsif new.review_status in ('PENDING', 'SKIPPED') then
    -- Undo puts a row back in the queue.
    new.reviewed_at := null;
  else
    new.reviewed_at := old.reviewed_at;
  end if;
  return new;
end;
$$;

drop trigger if exists i_transactions_stamp_reviewed on public.i_transactions;
create trigger i_transactions_stamp_reviewed
  before insert or update of review_status on public.i_transactions
  for each row execute function public.i_transactions_stamp_reviewed();

-- =============================================================================
-- 3. i_events — append-only telemetry from the app.
--    occurred_at is the device clock (events are queued offline and flushed
--    on the next sync); created_at is when the server got it.
-- =============================================================================
create table if not exists public.i_events (
  id          bigint generated by default as identity primary key,
  user_id     uuid        not null references auth.users(id) on delete cascade,
  name        text        not null,
  props       jsonb       not null default '{}'::jsonb,
  app_version text,
  occurred_at timestamptz not null default now(),
  created_at  timestamptz not null default now()
);

create index if not exists i_events_name_occurred_idx on public.i_events (name, occurred_at desc);
create index if not exists i_events_user_occurred_idx on public.i_events (user_id, occurred_at desc);

alter table public.i_events enable row level security;

drop policy if exists "i_events_owner_insert" on public.i_events;
drop policy if exists "i_events_owner_select" on public.i_events;
drop policy if exists "i_events_admin_select" on public.i_events;

create policy "i_events_owner_insert" on public.i_events
  for insert to authenticated with check (auth.uid() = user_id);
create policy "i_events_owner_select" on public.i_events
  for select to authenticated using (auth.uid() = user_id);
create policy "i_events_admin_select" on public.i_events
  for select to authenticated using (public.is_admin());

grant select, insert on public.i_events to authenticated;
grant usage, select on all sequences in schema public to authenticated;

-- =============================================================================
-- 4. i_device_status — latest health snapshot per user.
-- =============================================================================
create table if not exists public.i_device_status (
  user_id                uuid primary key references auth.users(id) on delete cascade,
  app_version            text,
  unsynced_transactions  integer not null default 0,
  unsynced_raw           integer not null default 0,
  oldest_unsynced_at     timestamptz,
  last_sync_ok_at        timestamptz,
  last_sync_error        text,
  has_pin                boolean,
  sms_permission         boolean,
  reported_at            timestamptz not null default now()
);

alter table public.i_device_status enable row level security;

drop policy if exists "i_device_status_owner_all"    on public.i_device_status;
drop policy if exists "i_device_status_admin_select" on public.i_device_status;

create policy "i_device_status_owner_all" on public.i_device_status
  for all to authenticated
  using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "i_device_status_admin_select" on public.i_device_status
  for select to authenticated using (public.is_admin());

grant select, insert, update on public.i_device_status to authenticated;

-- =============================================================================
-- 5. Account deletions — no email, no user id: just enough to see how long
--    people stayed and how far they got.
-- =============================================================================
create table if not exists public.i_account_deletions (
  id                 bigint generated by default as identity primary key,
  deleted_at         timestamptz not null default now(),
  account_created_at timestamptz,
  app_version        text,
  txn_count          integer,
  reviewed_count     integer
);

alter table public.i_account_deletions enable row level security;

drop policy if exists "i_account_deletions_admin_select" on public.i_account_deletions;
create policy "i_account_deletions_admin_select" on public.i_account_deletions
  for select to authenticated using (public.is_admin());

grant select on public.i_account_deletions to authenticated;

create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'not authenticated' using errcode = '28000';
  end if;

  insert into public.i_account_deletions (account_created_at, app_version, txn_count, reviewed_count)
  select
    (select created_at from auth.users where id = uid),
    coalesce(
      (select app_version from public.i_device_status where user_id = uid),
      (select app_version from public.i_sessions
        where user_id = uid and app_version is distinct from 'backfill-v1'
        order by started_at desc limit 1)
    ),
    (select count(*) from public.i_transactions where user_id = uid),
    (select count(*) from public.i_transactions
      where user_id = uid and review_status in ('CONFIRMED', 'CHANGED', 'NOT_A_TXN'));

  delete from auth.users where id = uid;
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;

-- =============================================================================
-- 6. Downloads + installs — the top of the funnel, before anyone signs in.
--    Both are written with the anon key, insert-only: nobody can read,
--    change or delete rows except the admin (read).
--    i_downloads: one row per APK click on Web/download.html.
--    i_installs:  one row per install, keyed by a random id the app makes on
--                 first launch. i_device_status.install_id links it to the
--                 account once the user signs in.
-- =============================================================================
create table if not exists public.i_downloads (
  id          uuid primary key default gen_random_uuid(),
  app_version text check (app_version is null or length(app_version) <= 40),
  referrer    text check (referrer is null or length(referrer) <= 300),
  user_agent  text check (user_agent is null or length(user_agent) <= 500),
  created_at  timestamptz not null default now()
);

create table if not exists public.i_installs (
  install_id  uuid primary key,
  app_version text check (app_version is null or length(app_version) <= 40),
  device      text check (device is null or length(device) <= 200),
  created_at  timestamptz not null default now()
);

alter table public.i_downloads enable row level security;
alter table public.i_installs  enable row level security;

drop policy if exists "i_downloads_insert"       on public.i_downloads;
drop policy if exists "i_downloads_admin_select" on public.i_downloads;
drop policy if exists "i_installs_insert"        on public.i_installs;
drop policy if exists "i_installs_admin_select"  on public.i_installs;

create policy "i_downloads_insert" on public.i_downloads
  for insert to anon, authenticated with check (true);
create policy "i_downloads_admin_select" on public.i_downloads
  for select to authenticated using (public.is_admin());
create policy "i_installs_insert" on public.i_installs
  for insert to anon, authenticated with check (true);
create policy "i_installs_admin_select" on public.i_installs
  for select to authenticated using (public.is_admin());

grant insert on public.i_downloads, public.i_installs to anon, authenticated;
grant select on public.i_downloads, public.i_installs to authenticated;

alter table public.i_device_status
  add column if not exists install_id uuid;

-- Old dashboard view; nothing reads it any more.
drop view if exists public.admin_user_overview;

-- =============================================================================
-- Views — section 1: is the parser working?
-- =============================================================================

-- Effective status per raw SMS. NULL status (pre-1.0.9 upload, unmatched by
-- the backfill) counts as UNPARSED.
create or replace view public.admin_raw_sms
with (security_invoker = on) as
select
  r.id,
  r.user_id,
  r.sender_name,
  r.bank_name,
  public.admin_sender_group(r.sender_name) as sender_group,
  r.raw_message,
  coalesce(r.received_at, r.created_at)    as received_at,
  coalesce(r.parse_status, 'UNPARSED')     as parse_status,
  r.parse_status is null                   as status_inferred
from public.i_raw_data r;

-- One row per parser bank, last 7 days. rate = parsed / (parsed + unparsed);
-- IGNORED (OTP, promos) is reported but kept out of the rate.
create or replace view public.admin_parse_rate_7d
with (security_invoker = on) as
with groups(sender_group, sort) as (
  values ('CBE', 1), ('Telebirr', 2), ('CBE Birr', 3), ('Awash', 4),
         ('Dashen', 5), ('BoA', 6), ('Berhan', 7)
),
recent as (
  select * from public.admin_raw_sms
  where received_at >= now() - interval '7 days'
    and sender_group is not null
)
select
  g.sender_group,
  g.sort,
  count(r.id)                                              as captured,
  count(r.id) filter (where r.parse_status = 'PARSED')     as parsed,
  count(r.id) filter (where r.parse_status = 'UNPARSED')   as unparsed,
  count(r.id) filter (where r.parse_status = 'IGNORED')    as ignored,
  count(distinct r.user_id)                                as users,
  max(r.received_at)                                       as last_received_at,
  case
    when count(r.id) filter (where r.parse_status <> 'IGNORED') = 0 then null
    else round(
      100.0 * count(r.id) filter (where r.parse_status = 'PARSED')
            / count(r.id) filter (where r.parse_status <> 'IGNORED'), 1)
  end                                                      as parse_rate
from groups g
left join recent r on r.sender_group = g.sender_group
group by g.sender_group, g.sort;

-- The bug queue: unparsed SMS from senders that have a parser, newest first.
create or replace view public.admin_unparsed_sms
with (security_invoker = on) as
select
  r.id,
  r.sender_group,
  r.sender_name,
  r.raw_message,
  r.received_at,
  r.status_inferred,
  r.user_id,
  u.email
from public.admin_raw_sms r
left join public.i_users u on u.id = r.user_id
where r.sender_group is not null
  and r.parse_status = 'UNPARSED'
order by r.received_at desc
limit 300;

-- Which bank parser to write next.
create or replace view public.admin_capture_only_senders
with (security_invoker = on) as
select
  r.sender_name,
  max(r.bank_name)                                                        as bank_name,
  count(*) filter (where r.parse_status <> 'IGNORED')                     as messages,
  count(*) filter (where r.parse_status <> 'IGNORED'
                     and r.received_at >= now() - interval '30 days')     as messages_30d,
  count(distinct r.user_id)                                               as users,
  max(r.received_at)                                                      as last_received_at,
  (array_agg(r.raw_message order by r.received_at desc)
     filter (where r.parse_status <> 'IGNORED'))[1]                       as sample
from public.admin_raw_sms r
where r.sender_group is null
group by r.sender_name
order by messages desc;

-- Duplicates that got past the on-device dedup, and UNKNOWN rows that older
-- builds inserted. Duplicate = same user + ref number, or same user + sender
-- + amount + timestamp; every copy after the first counts.
create or replace view public.admin_txn_quality
with (security_invoker = on) as
with t as (
  select
    user_id, ref_num, sender, amount, occurred_at, type, created_at,
    case when nullif(trim(ref_num), '') is not null
         then row_number() over (partition by user_id, trim(ref_num) order by created_at, id)
    end as ref_rank,
    row_number() over (partition by user_id, sender, amount, occurred_at order by created_at, id) as near_rank
  from public.i_transactions
)
select
  count(*)                                                             as total,
  count(*) filter (where ref_rank > 1 or near_rank > 1)                as duplicates,
  count(*) filter (where type = 'UNKNOWN')                             as unknown,
  count(*) filter (where created_at >= now() - interval '7 days')      as total_7d,
  count(*) filter (where created_at >= now() - interval '7 days'
                     and (ref_rank > 1 or near_rank > 1))              as duplicates_7d,
  count(*) filter (where created_at >= now() - interval '7 days'
                     and type = 'UNKNOWN')                             as unknown_7d
from t;

-- =============================================================================
-- Views — section 2: is categorization good enough?
-- =============================================================================

-- CategoryPredictor's source isn't stored, but its confidences are
-- deterministic: SEED = 0.80, TYPE_FALLBACK = 0.55, USER_RULE =
-- 0.65 + 0.075·ln(n+1) ≥ 0.70 (never exactly 0.80 for integer n).
create or replace function public.admin_prediction_source(predicted text, confidence double precision)
returns text
language sql
immutable
as $$
  select case
    when predicted is null                          then 'none'
    when confidence is null                         then 'unknown'
    when abs(confidence - 0.80) < 0.0001            then 'seed'
    when abs(confidence - 0.55) < 0.0001            then 'fallback'
    when confidence >= 0.70                         then 'user_rule'
    else 'unknown'
  end;
$$;

create or replace view public.admin_prediction_accuracy
with (security_invoker = on) as
select
  public.admin_prediction_source(predicted_category, category_confidence) as source,
  count(*) filter (where review_status = 'CONFIRMED')                      as confirmed,
  count(*) filter (where review_status = 'CHANGED')                        as changed,
  count(*) filter (where review_status = 'NOT_A_TXN')                      as not_a_txn,
  count(*) filter (where review_status in ('PENDING', 'SKIPPED'))          as unreviewed,
  count(*) filter (where review_status = 'CONFIRMED'
                     and reviewed_at >= now() - interval '30 days')        as confirmed_30d,
  count(*) filter (where review_status = 'CHANGED'
                     and reviewed_at >= now() - interval '30 days')        as changed_30d
from public.i_transactions
group by 1;

-- Counterparties that land in "Other" (confirmed with no guess, or picked
-- Other) or get corrected most often — seed-pattern candidates.
create or replace view public.admin_category_hotspots
with (security_invoker = on) as
select
  coalesce(merchant_key, upper(counterparty), '(none)')                         as merchant_key,
  max(counterparty)                                                              as example,
  count(*)                                                                       as txns,
  count(distinct user_id)                                                        as users,
  count(*) filter (where category = 'Other'
                     or (predicted_category is null and review_status in ('PENDING', 'SKIPPED'))) as in_other,
  count(*) filter (where review_status = 'CHANGED')                              as changed,
  mode() within group (order by category) filter (where review_status = 'CHANGED') as changed_to,
  mode() within group (order by predicted_category) filter (where review_status = 'CHANGED') as predicted
from public.i_transactions
where type <> 'UNKNOWN'
group by 1
having count(*) filter (where review_status = 'CHANGED') > 0
    or count(*) filter (where category = 'Other'
                          or (predicted_category is null and review_status in ('PENDING', 'SKIPPED'))) > 0
order by (count(*) filter (where review_status = 'CHANGED')
        + count(*) filter (where category = 'Other'
                             or (predicted_category is null and review_status in ('PENDING', 'SKIPPED')))) desc
limit 40;

-- Review backlog + internal-transfer detection, one row.
create or replace view public.admin_categorization_summary
with (security_invoker = on) as
select
  count(*) filter (where created_at < now() - interval '7 days')                             as older_than_7d,
  count(*) filter (where created_at < now() - interval '7 days' and review_status = 'PENDING') as pending_older_than_7d,
  count(*) filter (where created_at < now() - interval '7 days' and review_status = 'SKIPPED') as skipped_older_than_7d,
  count(*) filter (where type = 'INTERNAL_TRANSFER')                                         as transfers_flagged,
  count(*) filter (where type = 'INTERNAL_TRANSFER'
                     and review_status in ('CONFIRMED', 'CHANGED')
                     and coalesce(category, '') <> 'Own accounts')                           as transfers_recategorised,
  count(*) filter (where type = 'INTERNAL_TRANSFER'
                     and review_status in ('CONFIRMED', 'CHANGED'))                          as transfers_reviewed,
  count(*) filter (where type <> 'INTERNAL_TRANSFER' and category = 'Own accounts')         as transfers_missed
from public.i_transactions;

-- =============================================================================
-- Views — sections 3 + 4: people and devices. One row per user.
-- Review days are counted in Addis Ababa time.
-- =============================================================================
create or replace view public.admin_user_health
with (security_invoker = on) as
with review_days as (
  select user_id, (reviewed_at at time zone 'Africa/Addis_Ababa')::date as d
  from public.i_transactions where reviewed_at is not null
  union
  select user_id, (occurred_at at time zone 'Africa/Addis_Ababa')::date
  from public.i_events where name = 'review_completed'
),
review_agg as (
  select
    user_id,
    count(*) filter (where d > (now() at time zone 'Africa/Addis_Ababa')::date - 7) as review_days_7d,
    min(d) as first_review_day,
    max(d) as last_review_day
  from review_days
  group by user_id
),
events as (
  select
    user_id,
    min(occurred_at) filter (where name = 'sms_permission_granted') as sms_permission_at,
    min(occurred_at) filter (where name = 'scan_completed')         as first_scan_at,
    min(occurred_at) filter (where name = 'pin_set')                as pin_set_at
  from public.i_events
  group by user_id
),
latest_session as (
  select distinct on (user_id) user_id, app_version, started_at
  from public.i_sessions
  where app_version is distinct from 'backfill-v1'
  order by user_id, started_at desc
),
activity as (
  -- For week-1 retention: any real session or review.
  select user_id, started_at as at from public.i_sessions
  where app_version is distinct from 'backfill-v1'
  union all
  select user_id, reviewed_at from public.i_transactions where reviewed_at is not null
),
txn as (
  select
    user_id,
    count(*)                                                                     as txn_count,
    count(*) filter (where review_status = 'PENDING')                            as pending,
    count(*) filter (where review_status = 'PENDING'
                       and created_at < now() - interval '7 days')               as pending_older_7d,
    bool_or(review_status in ('CHANGED', 'NOT_A_TXN'))                           as any_human_review
  from public.i_transactions
  group by user_id
),
raw as (
  select user_id, count(*) as raw_count, min(created_at) as first_raw_at
  from public.i_raw_data
  group by user_id
)
select
  u.id,
  u.email,
  u.name,
  u.account_created_at,
  coalesce(d.app_version, ls.app_version)                       as app_version,
  ls.started_at                                                 as last_session_at,
  coalesce(ra.review_days_7d, 0)                                as review_days_7d,
  ra.last_review_day,
  ((now() at time zone 'Africa/Addis_Ababa')::date - ra.last_review_day) as days_since_review,
  -- Funnel. Older builds don't send events, so fall back to what the data
  -- implies: SMS rows mean permission + a scan; a CHANGED row means a review.
  coalesce(e.sms_permission_at, case when d.sms_permission then d.reported_at end, r.first_raw_at) as sms_permission_at,
  coalesce(e.first_scan_at, r.first_raw_at)                     as first_scan_at,
  coalesce(ra.first_review_day::timestamptz,
           case when t.any_human_review then u.account_created_at end) as first_review_at,
  coalesce(e.pin_set_at, case when d.has_pin then d.reported_at end) as pin_set_at,
  -- Week-1 retention: active on any of days 1–7 after signup. NULL until the
  -- window has closed.
  case
    when u.account_created_at > now() - interval '8 days' then null
    else exists (
      select 1 from activity a
      where a.user_id = u.id
        and a.at >= u.account_created_at + interval '1 day'
        and a.at <  u.account_created_at + interval '8 days'
    )
  end                                                           as week1_retained,
  coalesce(t.txn_count, 0)                                      as txn_count,
  coalesce(r.raw_count, 0)                                      as raw_count,
  coalesce(t.pending, 0)                                        as pending,
  coalesce(t.pending_older_7d, 0)                               as pending_older_7d,
  d.unsynced_transactions,
  d.unsynced_raw,
  d.oldest_unsynced_at,
  d.last_sync_ok_at,
  d.last_sync_error,
  d.has_pin,
  d.sms_permission,
  d.reported_at                                                 as status_reported_at,
  d.install_id
from public.i_users u
left join review_agg ra     on ra.user_id = u.id
left join events e          on e.user_id = u.id
left join latest_session ls on ls.user_id = u.id
left join txn t             on t.user_id = u.id
left join raw r             on r.user_id = u.id
left join public.i_device_status d on d.user_id = u.id;

grant select on
  public.admin_raw_sms,
  public.admin_parse_rate_7d,
  public.admin_unparsed_sms,
  public.admin_capture_only_senders,
  public.admin_txn_quality,
  public.admin_prediction_accuracy,
  public.admin_category_hotspots,
  public.admin_categorization_summary,
  public.admin_user_health
to authenticated;
