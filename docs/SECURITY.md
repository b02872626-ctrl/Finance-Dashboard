# Security

Who can read and change what. The database enforces this, not the app: if a
screen has a bug, the data still stays locked. Every rule here comes from a
file in `supabase/`; when you change a policy, change this file in the same
commit.

Words used below:
- **anon**: anyone with the public app key, signed in or not (the key ships
  inside the APK and the web pages, so treat it as public).
- **user**: a signed-in Kash account (`authenticated`).
- **owner**: the user whose `user_id` is on the row (`auth.uid() = user_id`).
- **admin**: a user for whom `is_admin()` is true (see below).

## Who is an admin

`public.is_admin()` (`supabase/add_admin_sessions.sql`) is the only admin
gate. It is true when the signed-in email is on a fixed list in that function
(two addresses today). To add or remove an admin, change the list there,
run the file, and log it in `docs/DECISIONS.md`.

The email comes from the sign-in token, so it is only as trustworthy as the
sign-in. These Supabase Auth settings must stay as they are (checked
2026-11-05 under Authentication → Sign In / Providers):
- **Confirm email: on**, so nobody can sign up with an admin's address.
- **Secure email change: on**, so nobody can switch their email to an
  admin's without both inboxes confirming.
- **Allow anonymous sign-ins: off.**
- **Redirect URLs** (Authentication → URL Configuration): only Kash's own
  sites may receive a sign-in: `https://kash-140.pages.dev/**` (added
  2026-11-05) and the two old Vercel addresses. Site URL is still the old
  Vercel address, which redirects to Cloudflare. Never add a wildcard that
  covers other people's sites (such as `https://*.pages.dev`).

Google sign-in emails are already verified.

## Tables

| Table | What it holds | anon | owner | admin | Set in |
|---|---|---|---|---|---|
| `i_users` | Profile mirrored from `auth.users` (name, email, dates) | — | read, insert, update own | read all | `secure_schema.sql`, `add_admin_sessions.sql` |
| `i_banks` | Shared catalog of bank names | — | read; insert; update only if nothing changes (trigger); no delete | same | `migrate_banks_to_catalog.sql`, `launch_account_deletion_and_banks.sql` |
| `i_transactions` | Parsed transactions | — | all on own rows; bank must exist in `i_banks` | read all | `secure_schema.sql`, `add_admin_sessions.sql` |
| `i_raw_data` | Money SMS text (no codes or promos) | — | all on own rows | read all | `add_i_raw_data.sql`, `add_admin_sessions.sql` |
| `i_sessions` | App and dashboard session times | — | read, insert, update own | read all | `add_admin_sessions.sql` |
| `i_events` | Named telemetry events | — | read, insert own | read all | `admin_health_metrics.sql` |
| `i_device_status` | Sync status per device | — | all on own row | read all | `admin_health_metrics.sql` |
| `i_account_deletions` | Anonymous stats on deleted accounts (no email, no user ID) | — | — | read | `admin_health_metrics.sql` |
| `i_downloads` | One row per APK click | insert only (length limits) | insert only | read | `admin_health_metrics.sql` |
| `i_installs` | One row per install (random ID) | insert only (length limits) | insert only | read | `admin_health_metrics.sql` |
| `i_feedback` | Survey answers | — | read, insert own | — | `add_feedback.sql` |
| `i_category_rules` | Learned category rules | — | all on own rows | — | `add_daily_review.sql` |
| `i_budgets`, `i_budget_categories` | Budgets | — | read, insert, update, delete own (categories: no update) | — | `add_budgets.sql` |
| `i_goals`, `i_goal_contributions` | Savings goals | — | read, insert, update, delete own (contributions: no update) | — | `add_goals.sql` |
| `i_income_entries`, `i_income_dismissed` | Income | — | read, insert, update, delete own (dismissed: no update) | — | `add_income.sql` |
| `i_ledger_entries`, `i_ledger_repayments` | Money lent and borrowed | — | read, insert, update, delete own (repayments: no update) | — | `add_ledger.sql` |

Every table has Row Level Security on. "—" means no policy, so the database
returns nothing or refuses.

Rule: never create a table in `public` by hand (backups, scratch copies).
Supabase grants new tables to `anon`, so a table without RLS is readable by
anyone with the public key. Put copies in a schema the API doesn't expose, or
turn RLS on in the same statement. `policies_snapshot.sql` (below) lists every
table's RLS switch; any `rls|<table>|false` line is a leak.

Live-only table (not in any repo file): `i_transactions_backup_20260406`, a
hand-made copy of transactions. Until 2026-11-05 it had RLS off and was
readable with the public key (6,272 rows). Locked by
`lock_transactions_backup.sql` (RLS on, API grants removed; data kept, still
visible in the SQL editor). On 2026-11-09 the owner chose to keep it,
locked; never re-grant it to `anon` or `authenticated`.

## Admin views

The admin dashboard (`Web/admin/`) reads only these views. All are
`security_invoker = on`, so they run with the caller's rights: an admin sees
everyone's totals, a user sees totals over their own rows, anon sees zeros.

`admin_raw_sms`, `admin_unparsed_sms`, `admin_parse_rate_7d`,
`admin_capture_only_senders`, `admin_money_senders`, `admin_txn_quality`,
`admin_prediction_accuracy`, `admin_category_hotspots`,
`admin_categorization_summary`, `admin_user_health`
(`admin_health_metrics.sql`, `dedupe_transactions_by_sms.sql`,
`money_senders.sql`).

Rule: a new admin view must be `security_invoker = on`. A view without it
runs as its creator and would show every user's data to anyone.

## Functions that bypass Row Level Security

`security definer` functions run with full rights, so each one must check
who is calling.

| Function | Who can call | What it does | Check |
|---|---|---|---|
| `is_admin()` | user | Reads the caller's own email from the token | Only answers about the caller |
| `delete_my_account()` | user (not anon) | Logs anonymous stats, then deletes the caller's `auth.users` row; owned rows cascade, feedback is unlinked | Uses `auth.uid()`; refuses when signed out |
| `sync_auth_user_to_i_users()` | trigger only | Copies a new or changed `auth.users` row into `i_users` | Not callable through the API |

## Secrets

| Secret | Where it lives | Never |
|---|---|---|
| Supabase URL and anon key | `local.properties` → `BuildConfig` | Treated as public; safety comes from the policies above |
| Supabase service-role key | Supabase dashboard only | Never in the app, the site, the repo or chat |
| Sentry DSN | `local.properties` (`sentry.dsn`) | Not in the repo or chat |
| Sentry organization token (uploads only) | `local.properties` (`sentry.authToken`) | Not in the repo, CI or chat; revoke in Sentry if leaked |
| Release keystore and password | `~/.keystores/`, `local.properties` | Not in the repo or chat |
| Google web client ID | `local.properties` | — |

## On the phone

- Local data sits in the app's private storage, excluded from cloud backups
  and device transfers (`allowBackup="false"`, `data_extraction_rules.xml`).
- The app opens behind a 4-digit PIN (`security/PinManager.kt`).
- Only money SMS from known bank senders are uploaded; one-time codes,
  promos and notices stay on the phone (`RawSmsUploadTest`).
- Crash reports are scrubbed of numbers and quoted text before sending
  (`CrashReportingTest`).

## How this is checked

| Check | Covers | Last run |
|---|---|---|
| Anonymous probe: read every table and admin view, try to insert, delete and call `delete_my_account` with only the public key | anon sees no rows (admin views show zeros), writes and account deletion refused | 2026-11-05, passed |
| Auth settings in the Supabase dashboard | Confirm email on, secure email change on, anonymous sign-ins off | 2026-11-05, passed |
| `RawSmsUploadTest`, `SupabaseTransactionKeyTest` | What leaves the phone, one row per SMS | CI, every push |
| `CrashReportingTest` | Crash-report scrubbing | CI, every push |
| `supabase/staging/isolation_test.py` on staging: two throwaway users; B tries to read, change, delete, forge and take over A's rows in all 16 per-user tables; signed-out reads; admin-only tables and all admin views; bank list; `delete_my_account` | One user can't touch another's rows; non-admins see only their own data in admin views; deleting one account leaves others alone | 2026-11-05, 214 checks passed. Proven to catch a leak: a deliberate `using (true)` policy on staging made it fail. CI runs it once the staging secrets are set |
| `supabase/staging/policies_snapshot.sql` on live and staging | Same rules on both; no table with RLS off | 2026-11-05: rules match (two policies differ only by name; two functions only by comments); found and locked the backup table above |

## Staging

`kash-staging` (`qajkfxidpabvpqjsljtp`, separate from live
`sadbxjnmcgzwtjbqqhbk`) is built from `supabase/` by
`supabase/staging/build_setup.sh`. It differs from live only in Auth:
**Confirm email is off** there so the test can sign up throwaway users.
Its URL and publishable key are in `local.properties` as
`staging.supabase.url` / `staging.supabase.anonKey`. New SQL runs on staging
first, then the isolation test, then live. See `supabase/staging/README.md`.

Not yet covered: the Google sign-in path and the admin dashboard's own
page logic (the database rules behind them are covered above).
