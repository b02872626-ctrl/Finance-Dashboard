-- Goals made in the app can follow one of the user's accounts: money that
-- lands there after the goal was made counts as saved (see
-- app/src/main/java/com/financeapp/data/goals/GoalProgress.kt). The key is
-- the app's own account key ("Commercial Bank of Ethiopia#1234"), kept in
-- the cloud so a reinstall or a second phone gets the link back. The web
-- dashboard ignores it for now.
--
-- No policy changes: i_goals keeps its owner-only rules from add_goals.sql.
alter table public.i_goals
  add column if not exists linked_account_key text;
