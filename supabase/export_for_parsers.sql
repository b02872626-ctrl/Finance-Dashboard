-- Export the SMS Kash couldn't turn into transactions, to write new parsers.
-- Run in the Supabase SQL editor, then "Download CSV" and save it to
-- ~/Downloads as kash_unparsed_sms.csv. No user ids or emails are included.
select
  r.sender_name,
  r.bank_name,
  coalesce(r.parse_status, 'NO_STATUS')      as parse_status,
  coalesce(r.received_at, r.created_at)      as received_at,
  r.raw_message
from public.i_raw_data r
where coalesce(r.parse_status, 'UNPARSED') <> 'PARSED'
order by r.sender_name, received_at desc;
