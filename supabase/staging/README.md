# Staging database

A second, empty Supabase project with the same tables and rules as the live
one. It exists so that the rules can be tested, and new SQL tried, without
touching real users' data.

## Files

- `build_setup.sh` turns every file in `supabase/` into one `setup.sql`, in
  the order they were run on the live project. It fails if a new SQL file is
  missing from its list.
- `setup.sql` is generated and not committed. **Staging only**: its first
  part drops tables.
- `policies_snapshot.sql` is a read-only list of every table's RLS switch,
  every policy, every view's mode and every security definer function. Run it
  on both projects and compare the results: they should match.
- `isolation_test.py` signs up two throwaway users on staging and checks that
  neither can read, change, delete or create the other's rows. It also checks
  that admin data stays hidden and that deleting one account leaves the other
  alone. It uses only Python's standard library.

## One-time setup

1. In Supabase, create a new project named `kash-staging` (free plan).
2. Run `supabase/staging/build_setup.sh`, then paste `setup.sql` into the
   staging SQL editor and run it.
3. In Authentication → Sign In / Providers → Email, turn **Confirm email off**
   (staging only; live keeps it on). The test users can't open an inbox.
4. Add these two lines to `local.properties`, with the values from staging's
   Project Settings → API:
   ```
   staging.supabase.url=https://<staging-ref>.supabase.co
   staging.supabase.anonKey=<staging anon public key>
   ```
5. Optional, so CI runs it on every push: add the same two values as GitHub
   repo secrets `STAGING_SUPABASE_URL` and `STAGING_SUPABASE_ANON_KEY`.

## Use

- Run `python3 supabase/staging/isolation_test.py` after any change to a
  policy, view or function, and before running new SQL on live.
- Run new SQL on staging first, then the test, then on live.
- After running SQL on live, run `policies_snapshot.sql` on both projects and
  compare.
