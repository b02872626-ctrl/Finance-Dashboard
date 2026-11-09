# Progress

Read this first in every session. Update it before you finish: move finished
items to Done with the date, add what is next or blocked.

## Next up

- Parser gaps still open (from the admin Parser tab, Nov 2026), waiting on
  the owner's admin export `kash_unparsed_sms.csv` (Parser → All unparsed
  texts → Download every unparsed SMS) for real examples: Awash water bill,
  Awash electric bill, CBE "received ETB … from your account … to your
  account", Berhan "transferred … to TeleBirr number", and two Telebirr
  Amharic ተቀብለዋል shapes. Also: a BoA Amharic promo ("ያልታሰበ ሲሳይ!…") is
  counted as a money text; add it to the ignore list (mirror in admin.js).
- Play Store first upload: follow `docs/play-store-submission.md` §6
  (order of operations).


## Blocked

- Paparazzi screenshot tests (`KashComponentsScreenshotTest`,
  `KashSmallPhoneScreenshotTest`) don't run on SDK 36: 1.3.5 can't render
  it, 2.0 needs Gradle on Java 21. Skipped by default (CI too); run them
  with `./gradlew testDebugUnitTest -PscreenshotTests`.

## Owner tasks

- Make a Telegram group (or pick another channel) for testers; the release
  script prints the message to paste there. Pick a weekly release day.
- PostHog: if not done yet, turn on "Discard client IP data" (PostHog →
  Settings → Project → General).
- Turn off preview builds if not done yet: Cloudflare → Workers & Pages →
  kash → Settings → Build → Branch control → Preview branch: None. Optional:
  stop Vercel `finance-dashboard` building branches other than `main`
  (Settings → Git); keep the project itself, it serves the redirect.
- Optional: add GitHub secrets `STAGING_SUPABASE_URL` and
  `STAGING_SUPABASE_ANON_KEY` (values in `local.properties`) so CI runs the
  isolation test on every push.
- Keep the Vercel project `finance-dashboard` running: it redirects the old
  address and serves `/version.json` to 1.0.20 installs.
- Play Console (when filling it in): privacy policy and account-deletion
  URLs are now on `kash-140.pages.dev`.
- Play Console: SMS permission declaration (text in
  `docs/play-store-sms-declaration.md`), Data safety, account deletion link,
  unlisted upload of the SMS demo video.
- Android OAuth client for `et.kash.app` with the upload key's SHA-1, then
  Play App Signing's SHA-1 after the first upload.
- Closed test with testers before production (new personal developer
  account rule, see the submission checklist §5).

## Done

- 2026-11-09 Parser "Fix next" check: 9 of the 14 listed shapes (Dashen
  withdrawal and check payments, Awash school fees, credit and other-bank
  transfer, CBE short transfer and "debited for", CBE Birr reversal,
  Telebirr parking) already parse with today's rules; they were stale
  statuses from old installs. Added masked tests for the uncovered ones and
  bumped `RULES_VERSION` to 5 so phones re-send statuses and the list clears.
- 2026-11-09 Work process (NAH-31): `docs/WORKFLOW.md` (Linear issue →
  branch → PR → merge → deploy → tester release) and `scripts/release.sh`
  (`prepare` builds, checks and opens the release PR; `verify` checks the
  live site and prints the tester message). Practice run passed and undid
  itself; not yet used for a real release.
- 2026-11-09 1.0.23: app events reach PostHog (sent right away). Installed
  on the owner's Galaxy S22.
- 2026-11-09 App events reach PostHog: they were stuck on the phone
  (PostHog waited for 20). Checked on the owner's Galaxy S22 with a
  diagnostic build: each event is sent within a second.
- 2026-11-09 Admin fix 2: the Overview still stuck on "Loading…". The
  redesign had deleted the `guard()` helper that paints each card, so
  painting crashed once data arrived. Restored; checked signed in with live
  data (all cards fill).
- 2026-11-09 Web dashboard redesign: sidebar tabs, one white panel, greeting
  and period control in the panel head, six numbers in one block with
  icons. Checked with sample numbers at desktop and phone width; not yet
  checked signed in with real data.
- 2026-11-09 Admin fix: the Overview stuck on "Loading…" after sign-in.
  The page loaded twice, and the second load ran inside Supabase's auth
  callback, which hangs every request. Routing now waits a tick, the same
  admin is never reloaded by a repeat auth event, older loads can't paint
  over newer ones, and any request that hangs shows "Timed out" after 25 s.
- 2026-11-09 1.0.22: PostHog analytics on (app events go to PostHog EU as
  well as Supabase). Not yet installed on a real phone.
- 2026-11-09 PostHog analytics (EU) wired into the app
  (`remote/ProductAnalytics.kt`, mirrors every `Telemetry` event plus app
  opened/installed/updated) and the site (`Web/analytics.js`: page views and
  APK download clicks, URLs cut before `?`/`#`, not on `/admin/`). Off until
  the key is set. Privacy policy and Data safety text updated.
- 2026-11-09 PostHog key added to `Web/analytics.js` (site sends) and to
  `local.properties` (next release build sends).
- 2026-11-09 Admin dashboard redesign: opens on Overview with current
  installs, parsed messages and active users today (with trend pills), a
  30-day active-users block chart, parse rate by bank this week, and the
  most recently synced phones. Parser / Categorization / Retention / Health
  moved to sidebar tabs. Checked with sample data at desktop and phone
  width; not yet checked signed in against live data.
- 2026-11-09 Web dashboard "Forgot password?" on the sign-in page and in
  Settings (under Current password). The emailed link opens the dashboard
  with Settings set to "Set a new password" (no old password needed).
  Expired links say so.
- 2026-11-09 Web dashboard Settings (☰ menu → Settings): shows the signed-in
  account (name, email, sign-in method, member since) and changes the
  password after checking the current one. Google-only accounts see a note
  instead of the form.
- 2026-11-09 Admin dashboard "Forgot password?": emails a reset link (admin
  addresses only) that comes back to `/admin/` and asks for a new password
  before the dashboard opens. Expired links say so. Reset links that land
  on the home page (Supabase's fallback) are passed on to `/admin/`.
- 2026-11-06 Staging Supabase `kash-staging`, built from `supabase/` by
  `supabase/staging/build_setup.sh`; rules match live. Cross-user isolation
  test (`supabase/staging/isolation_test.py`, 214 checks) passes, and fails
  when a leaky rule is added on purpose. It found a live leak: the
  hand-made table `i_transactions_backup_20260406` (6,272 rows) was readable
  with the public key; locked 2026-11-05 (`lock_transactions_backup.sql`).
- 2026-11-05 Supabase Auth redirect URLs include `kash-140.pages.dev/**`.
- 2026-11-05 1.0.21: site moved to Cloudflare Pages (`kash-140.pages.dev`);
  app, privacy policy and Play docs point there; Vercel redirects to it.
- 2026-11-05 1.0.20: "Kash x.y.z is available" notification. The app reads
  `/version.json` at start and every 12 h; off for Play installs. First
  real test is when 1.0.21 ships.
- 2026-11-05 1.0.19: readable crash traces. The Sentry Gradle plugin
  uploads each release's R8 mapping to Sentry project `android`
  (org `kash-r2`); needs `sentry.authToken` in `local.properties`.
- 2026-11-05 `docs/SECURITY.md`: every table, policy, admin view and
  privileged function; live anonymous probe passed (no rows, writes refused);
  Auth checked: confirm email and secure email change on, anonymous off.
- 2026-11-05 CI (`.github/workflows/ci.yml`): unit tests and a debug build
  on every push and pull request.
- 2026-11-05 1.0.18: Sentry crash reporting (crash-only, scrubbed). Sentry
  org `kash-r2`, EU region; DSN in `local.properties` as `sentry.dsn`.
  Privacy policy and Data safety text updated.
- 2026-11-05 Project memory files: `RULES.md`, `docs/PROGRESS.md`,
  `docs/DECISIONS.md`.
- 2026-11-05 1.0.17: hide an account that isn't yours.
- 2026-10-31 1.0.16: Analytics without the vs-last-month tile.
- 2026-10-29 1.0.15: Review shows Yours and Recent picks.
- 2026-10-29 1.0.14: API 36, package `et.kash.app`, money-sender scout removed.
- 2026-10-29 1.0.13: one-time codes, promos and notices no longer uploaded;
  public account-deletion page; Play submission checklist.
- 2026-10-29 Several accounts at one bank shown as separate cards.
- 2026-10-29 Guards against pushing branches that are behind `main`.
- 2026-10-28 1.0.8–1.0.12: admin dashboard locked to admins, parser /
  categorization / retention / health metrics, one cloud row per SMS,
  unparsed-SMS export, parser trained on the `i_raw_data` export.
- 2026-10-27 1.0.1–1.0.7: Kash redesign, PIN, loading screen, touch feedback,
  notifications, review and data animations.
- 2026-10-26 Renamed Finance Lore to Kash; download page serves the APK.
