-- Per-account split: users with several accounts at one bank (e.g. two CBE
-- accounts) see one card per account — "CBE 1", "CBE 2" — instead of one
-- merged card whose balance flips between the two.
--
-- Run this BEFORE deploying the web dashboard or shipping the Android build
-- that sends account_number, or their selects / upserts will fail on the
-- missing column.

alter table public.i_transactions
add column if not exists account_number text;

-- Backfill rows synced before the app sent account_number, by pulling the
-- user's own masked account out of the SMS body. CBE only: these are the
-- formats the CBE parsers read the account from —
--   "your Account 1****8166 has been debited/Credited"
--   "on your account 1********9408" / "from your account …" / "to your account …"
--   "from account 1********4607 to account 1********9258 (NAME)"  (own = from)
-- Other banks fill in as their rows re-sync from the app.
update public.i_transactions t
set account_number = x.acct
from (
  select id, user_id,
         rtrim(coalesce(
           substring(raw_body from '(?i)your\s+account\s+(\S+)'),
           substring(raw_body from '(?i)from\s+account\s+(\S+)')
         ), '.,;:') as acct
  from public.i_transactions
  where account_number is null
    and bank_name = 'Commercial Bank of Ethiopia'
    and raw_body is not null
) x
where t.id = x.id
  and t.user_id = x.user_id
  and x.acct ~ '[0-9]{4}$';

-- Sanity check: accounts per user at CBE (2+ means they'll get split cards).
--   select user_id, count(distinct right(account_number, 4)) as accounts
--   from public.i_transactions
--   where bank_name = 'Commercial Bank of Ethiopia' and account_number is not null
--   group by user_id order by accounts desc;
