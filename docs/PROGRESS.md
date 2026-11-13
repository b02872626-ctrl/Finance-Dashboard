# Progress

Read this first in every session. Update it before you finish: move finished
items to Done with the date, add what is next or blocked.

## Next up

- **Goals section in the app (NAH-14).** Rebuilt to the dark design in
  `design/goals-handoff`. PR A (dark theme, Goals tab, goal page, sheets,
  dialogs, icons, pixel-wipe motion) and PR B (New goal / Edit goal /
  Change account, dark) are open and ship together; PR B sits on PR A's
  branch. PR B still needs a look on the phone. Later: Supabase sync, web
  dashboard, "goal reached" notification, archived list.

- **FOW Summit booth, 28 Nov (NAH-32).** Apply by 18 Nov. Goal: 100
  downloads and 100 active users. Release and install on a real phone by
  26 Nov. Sub-issues, one branch each: parsers NAH-29, notifications
  NAH-33, analytics NAH-27, Kash Wrapped NAH-34, in-app update + popup
  NAH-35, category swipe NAH-36, icon clean-up NAH-37, download page
  NAH-38, web insights NAH-30, web redesign + speed NAH-39, Gemini "Ask
  Kash" on the web NAH-40.

- NAH-29 part 2: the export has no money SMS at all from Hibret, Debub
  Global or NIB (only notices), and none from Zemen, Coop, Wegagen, Bunna,
  Oromia, Amhara, Enat, Global, Abay. New parsers need real samples from
  testers (or the beta-only collection in NAH-29 part 3). Also open: BoA
  USD-account alerts (21 in the export) need currency support first.
- Parser gaps still open (from the admin Parser tab, Nov 2026), waiting on
  the owner's admin export `kash_unparsed_sms.csv` (Parser → All unparsed
  texts → Download every unparsed SMS) for real examples: Awash water bill,
  Awash electric bill, CBE "received ETB … from your account … to your
  account", Berhan "transferred … to TeleBirr number", and two Telebirr
  Amharic ተቀብለዋል shapes.
- Play Store first upload: follow `docs/play-store-submission.md` §6
  (order of operations).


## Blocked

- Paparazzi screenshot tests (`KashComponentsScreenshotTest`,
  `KashSmallPhoneScreenshotTest`) don't run on SDK 36: 1.3.5 can't render
  it, 2.0 needs Gradle on Java 21. Skipped by default (CI too); run them
  with `./gradlew testDebugUnitTest -PscreenshotTests`.

## Owner tasks

- PostHog session replay is on and checked. Keep "Capture console logs"
  off (PostHog → Settings → Session replay). Update the Play Console data
  safety form (App interactions row) to match `docs/play-store-sms-declaration.md`.

- Send the FOW Summit booth application by 18 Nov (NAH-32). Check
  whether a registered company is required.
- Get a Gemini API key (Google AI Studio) for NAH-40; it goes into
  Supabase secrets, never into chat or the repo.

- Export a fresh `i_raw_data` CSV (the one in Downloads ends 28 Oct) so
  parser work sees November SMS.
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

- 2026-11-13 New goal, Edit goal and Change account in the dark design
  (NAH-14, PR B): dark step bar, icon picker (12 icons, sideways row),
  name counter "6 / 40", "Save it by … · about X ETB a month", monthly
  and safety-net notes with average spend, "Kash counts it for you" card,
  account rows with bank badge and "Used by …" pill, "Saving goal…", a
  dark "Save it by" date picker (follows the Ethiopian calendar setting),
  Edit strip (kind + "Started …"), Change account as its own page. Steps
  slide in from the right and back out to the right (fade with reduced
  motion). Not yet checked on the phone (not connected).

- 2026-11-13 Goals in the dark design (NAH-14, PR A): dark top and sheet
  with sand as the accent; Goals tab with total, "+X ETB this month",
  pace strip, quiet cards (next step only when off pace), one large card
  for a single goal, empty state with the three kinds, "Every goal is
  reached"; goal page with a pace mark on the bar, status card facts,
  history by month; dark Add a save, More menu, Delete and Remove
  dialogs; "You reached it" with a filling bar, check and haptic; "Month
  done" for monthly goals. 12 goal icons replace emoji. Installed on the
  owner's Galaxy S22; screens not yet looked at (phone locked).

- 2026-11-12 New goal and Edit goal (NAH-14, PR 3): "+" on Goals opens a
  3-step form: kind (amount by a date / safety net / every month),
  details (name, emoji, amount or months with "That's about X ETB", date
  chips 3 / 6 / 12 months or a day picker in either calendar), then the
  account (taken ones are greyed out with "Used by Laptop", or "None,
  I'll add saves myself"). The goal page menu has Edit and Change
  account. `GoalFormTest` (7). Not checked at 360dp or on a phone.

- 2026-11-12 Goals screens (NAH-14, PR 2): a 4th tab (target icon) opens
  Goals with a sand top instead of slate: total saved, "2 of 3 on track",
  one card per goal (bar, amounts, what to save next), a folded "Reached"
  list, and an empty state. The goal page shows the big figure and bar,
  pace and "save X a month", where the money comes from, and the history
  (SMS rows open the transaction; your own saves can be removed). "Add a
  save" sheet (amount, note, today or yesterday), Archive / Delete, and a
  one-time "You reached it" card. `GoalCopyTest` (7). Not checked at 360dp
  or on a phone (no emulator here; screenshot tests blocked on SDK 36).

- 2026-11-12 Goals logic for the app (NAH-14, PR 1, no screens yet):
  goals are stored on the phone (3 kinds: an amount by a date, a safety
  net of N months of spending, a monthly amount). A goal can follow one of
  your accounts: money landing there after the goal was made counts as
  saved, money leaving takes it back (never below 0, never eating saves
  logged by hand). One account feeds one goal at a time. Saves by hand
  add on top. Pace (ahead / on track / behind / missed), "save X a month",
  an estimated finish date and a monthly streak are worked out.
  `GoalProgressTest` (29 tests). Nothing to see on the phone yet.

- 2026-11-12 Web dashboard starts faster (NAH-39, part 2a): one pinned
  Supabase file instead of ~14, its imports download in parallel with
  app.js, and the font no longer blocks the first paint. Signed-in "time to
  data" not measured yet (needs the owner signed in).
- 2026-11-12 Web dashboard "Spending over time" (NAH-39, part 1): a dot
  rides the line at the day under your cursor or finger, with a faint guide
  down to the axis, and the line above the chart names that day. Checked
  with made-up data; not yet signed in with real data.
- 2026-11-12 NAH-38 download page for the booth: headline "Every bank SMS
  becomes your spending record." with "No bank login, nothing to type";
  a bigger Download button that stays above the fold on a phone (the
  rolling words hide there); a drawing of what Android shows for each of
  the 5 install steps (one list, English only: the owner had the
  English / Amharic switch removed); bank chips with the
  app's bank marks; a new "Is my data safe?" section (matches the privacy
  policy); the font no longer blocks the first paint (10 KB HTML, nothing
  render-blocking). Checked in the browser at 375px and desktop, `?ref=fow`
  reaches `apk_download_clicked`. Still open for NAH-38: click through on a
  real phone over a slow connection. Earlier (PR #31): Amharic steps, `?ref=`
  tracking, `Web/flyer.html`.

- 2026-11-12 Review: "New" category chip right on the card, "Create
  “name”" from the picker search, tap the card for the SMS and details, and
  the picker shows which transaction you're labelling (NAH-42). Not yet
  checked on a real phone or at 360dp (no emulator on this Mac).
- 2026-11-12 Categories survive a reinstall (NAH-41): signing in again on
  a fresh install brings back every category, including your own category
  names. Not yet installed on a real phone. Categories already wiped by the
  old bug are not in the cloud any more and can't come back.
- 2026-11-12 1.0.34: Home is cleaner: no Cash card, and banks you switch off leave Home and your total; The quiet-accounts line and the weekly bars are gone from Home; To help fix problems, Kash can record how screens are used, with every word, number and picture hidden. Not yet installed on a real phone.
- 2026-11-12 Web dashboard Transactions: "Show 100 more" / "Show all" under
  the table (it used to stop at 100 rows with no way to see the rest).

- 2026-11-12 Web dashboard design basics, look unchanged: press feedback
  everywhere, keyboard focus ring, bigger chips on phones, reduced-motion /
  contrast / transparency settings respected.
- 2026-11-12 Web dashboard Transactions: All / Credit / Debit / Internal
  transfer filter (moves between own accounts only under Internal transfer).
- 2026-11-12 Admin dashboard: "Daily active users" is now a line chart
  (was a block chart). Peak day and today get a dot; hover shows the day's count.
- 2026-11-12 App session replay in PostHog, fully masked (all text, text
  fields and images hidden, no logs). Privacy policy updated. Owner
  checked it on a Galaxy S22 (branch build): recordings arrive, masked.
- 2026-11-12 Home cleanup (owner's screenshots): no Cash card, and a bank
  switched off in "Banks Kash follows" leaves Home and the total; the
  "Leaves out 1 account…" line and the weekly bar strip are gone; account
  cards have tighter corners (12dp). `HomeAccountsTest`. Not yet released.

- 2026-11-12 1.0.33: Fixed a crash when you tap "They're mine" or "Hide them" on the card about other people's accounts; Your own accounts are no longer marked as someone else's. Not yet installed on a real phone.
- 2026-11-12 Home no longer marks the owner's own accounts as someone
  else's ("<Name>'s?") when the name in Settings isn't the one the banks
  use. The name most SMS greet now always counts as the owner too
  (`AccountOwnershipTest`, 6 tests). Not yet on a phone.

- 2026-11-12 Crash fix: tapping "Hide them" or "They're mine" on the
  "accounts get SMS for …" card could close Kash ("List is empty" while the
  card animated away; seen once on 1.0.31). The card keeps its accounts
  until it has gone. Not yet on a phone.

- 2026-11-12 1.0.32: Moving money between your own accounts no longer counts as spending and income; Money-in and low-balance alerts arrive right away, even on busy days. Not yet installed on a real phone.
- 2026-11-12 Notifications: income and low-balance alerts no longer wait
  for the evening digest when 3 alerts have already shown that day (the
  cap now applies to fee alerts only). The digest card no longer shows its
  first item twice (NAH-33).

- 2026-11-12 Own-account moves: a money-out and a money-in of the same
  amount within 5 minutes on two different accounts (CBE ↔ Telebirr, CBE 1
  ↔ CBE 2, CBE ↔ Awash…) are filed as "Own accounts", so they stop counting
  as spending and income. Shared reference number is the fallback. Runs on
  every SMS scan, including old history (`OwnTransferPairing`, 16 tests).
  Not yet on a phone. Still open: Telebirr → someone else's bank account is
  treated as your own when the SMS shows no person's name; matching the
  bank account number against your own accounts would fix that.

- 2026-11-12 1.0.31: Accounts that belong to someone else can be hidden in one tap, and old quiet accounts no longer count in your total; Sharing your Wrapped makes a clean picture; No more "money came in" alerts for your own top-ups or loans. Not yet installed on a real phone.

- 2026-11-11 Home: accounts whose SMS greet someone else are marked
  "<Name>'s?" and Home offers to hide them; accounts with no SMS for 90
  days leave the total and show "Last updated <month>". Rules unit-tested
  with made-up names (`AccountOwnershipTest`). Not yet on a phone.

- 2026-11-11 Web dashboard loads every transaction: it stopped at the
  newest 2,000, so heavy users saw empty months before ~August in "Money
  in vs out" and a short "All time" (owner's screenshot). Now pages of
  1,000 (first with the count, the rest in parallel), up to 50,000.

- 2026-11-11 Wrapped share picture: stats sit in two columns and shrink to
  fit, so five stats no longer run into the "Track yours free" footer
  (owner's Telegram screenshot); Ethiopian weekdays read "Kidame days", not
  "Kidames". Layout unit-tested (`WrappedShareTest`). Not yet on a phone.

- 2026-11-11 NAH-30 web dashboard insights: overview like the phone's
  Analytics (balance, today, this week, this month; spending line with
  ranges; where you pay most; spent from), money in vs out with a kept
  line, payday pattern with burn-down, low-balance days per account, and
  a fixed "In context" block. Numbers in `Web/insights.js`, 16 Node tests
  in CI. Checked in the browser with sample data (desktop, phone width,
  Ethiopian calendar); not yet with a real account.

- 2026-11-11 1.0.30: Kash Wrapped: last month's money story, ready to share; The Settings button is a gear now. Not yet installed on a real phone.
- 2026-11-11 NAH-37 clean-up: the Settings icon is a gear (it was a ring
  with rays that read as a sun). Removed dead code left from the pre-Kash
  screens: the old Analytics, Insights and Period-insights flows and their
  data classes, the unused CalendarFormatter (KashDates does this), and
  NetworkMonitor (only the removed backup notifications used it). No
  user-facing wording needed changes. Not yet on a phone.

- 2026-11-11 NAH-34 Kash Wrapped: last month's money story in pages
  (spent vs the month before, kept, biggest payment, your regular, bank
  fees, busiest weekday and time of day, a tip), from SMS data only.
  Home card in the first week of a month and a row on Analytics. Share
  makes a picture without account numbers, balances or names, with the
  download link (`?ref=wrapped`). Numbers unit-tested (`WrappedTest`).
  Not yet seen on a phone.

- 2026-11-11 1.0.29: Analytics shows today, this week and a spending graph you can set to any dates; Notifications are quieter and friendlier, with no more scanning or backup pings; Updates install right inside Kash from the next version on; Swiping review cards follows your finger again. Not yet installed on a real phone.
- 2026-11-11 NAH-27 Analytics: today's spend up top with This week and
  This month tiles (vs the same point before), then a daily spending line
  with range chips (This week, This month, 30 days, Pick dates), total,
  change and daily average; payees and accounts follow the range;
  categories last and only when labelled. Numbers unit-tested
  (`SpendOverviewTest`). Not yet seen on a phone (no device; screenshot
  tests don't run on SDK 36).

- 2026-11-11 NAH-36 review swipe: the card read the finger in its own
  moving, tilted coordinates, so it lagged, jittered and flicks barely
  counted; the drag is now read before the card moves. Swipes on the card
  no longer trigger Android's back gesture, fast moves can't overwrite
  each other, and with reduced motion the card can't get stuck after a
  swipe that keeps it on screen. Release rule unit-tested
  (`ReviewSwipeTest`). Not yet on a phone.

- 2026-11-11 NAH-35 in-app update: Update downloads the APK inside Kash
  (with progress), checks its SHA-256 against version.json, and opens
  Android's installer; the first time, Android asks to allow installs
  from Kash. A pop-up shows when Kash opens with an update waiting
  ("Later" = 3 days). release.sh publishes `apk` and `sha256`. Works from
  the first release that includes it onward (testers install that one the
  old way). Play upload must drop REQUEST_INSTALL_PACKAGES. Unit tests
  pass; not yet on a phone.

- 2026-11-11 NAH-33 notifications: no scan, sync or backup notifications
  any more (owner's call). The in-app scan runs in the background worker,
  the foreground service and its permissions are gone, and the old
  channels are deleted on update. Low-balance alerts match accounts like
  Home does. The review question reads "250 ETB to Abebe G.: was it
  Transport?". Triggers: low balance is per account (its own spending)
  and only alerts when the balance drops below the floor, saying how many
  days it lasts; "Kash can't read <bank> yet" at most once a week; the
  backlog nudge skips days that already have a review. All notification
  copy is in the owner-approved casual voice ("ayy Dawi 16k just came in
  🎉"), with amounts as k and the first name from the sign-in account.
  Unit tests pass; not yet on a phone (none connected).

- 2026-11-11 1.0.28: Berhan accounts show your real balance (one account was counted twice). Not yet installed on a real phone.
- 2026-11-11 Berhan balance fix: Berhan writes one account as
  "*********5167" on debits and "105001XXXXX67" on credits. Kash read the
  credit's last four as "0267" and showed two accounts, adding a stale
  balance (a tester: about 16k shown, under 100 real). The account suffix now
  uses only the digits after the mask; a short tail ("67") joins the one
  account ending with it. Hiding an account and the account page follow
  the same rule. Checked on tester Dawi's phone (1.0.28).

- 2026-11-11 1.0.27: Bank of Abyssinia deposits show who sent the money and your balance; Berhan transfers to Telebirr are counted as money out; Berhan deposits say where the money came from; Telebirr ticket payments are no longer missed. Not yet installed on a real phone.
- 2026-11-11 NAH-29 part 2 (first pass): replayed the 28 Oct export again;
  no SMS from tracked senders is left unread. Fixed 154 BoA deposits in the
  2023–24 "has been credited … for a transfer made by …" wording, which had
  no account, balance or sender (now e.g. "Telebirr", "Other bank
  (EthSwitch)", or the depositor's name). Fixed Telebirr ticket payments
  dropped as one-time codes because of "Don't share the link".
  Berhan (owner's 173-SMS Berhan export): Berhan → Telebirr transfers now
  read as money out (new `BerhanBankTransferParser`), and Berhan credits
  name the source (another bank's RTGS, "transfer by …", cheque, interest,
  payment purpose) instead of "from Berhan Bank <own account>". Replay:
  156 parsed, 17 notices, 0 missed. `RULES_VERSION` 8. Not yet on a phone.
  The rest of NAH-29 still needs November SMS and samples from the banks
  we don't read yet.

- 2026-11-10 1.0.26: Your accounts are on Home now: swipe the row under your total and tap one for its history; Three tabs instead of four: Home, History, Analytics; A low-balance alert opens that account's history. Not yet installed on a real phone.
- 2026-11-10 NAH-28: Home and the Accounts tab are one page. Under the
  total balance, a row of account cards (bank mark, name, balance) you swipe
  sideways; tap one for that account's history. Amounts hide with the total.
  The tab bar is Home · History · Analytics. The low-balance notification
  opens that account's history; old "accounts" links land on Home.
- 2026-11-10 1.0.25: A card on Home tells you when a new version is ready; Settings → Updates shows your version and what's new; Settings shows when your phone blocks Kash notifications, and how to fix it. Not yet installed on a real phone.
- 2026-11-10 NAH-15 (notifications part): Settings → Notifications shows
  "Your phone is blocking Kash notifications" with Allow notifications
  (Android's prompt) or Open phone settings, and the Settings row says
  "Blocked". Onboarding already asked together with SMS; this is the way
  back for anyone who said no or switched them off.
- 2026-11-10 NAH-26: update card on Home ("Kash x.y.z is ready", the
  release notes, Update / Later) and Settings → Updates (installed and
  latest version, what's new, last checked, Check now; "Google Play" for
  Play installs). The notification now leads with the first release note.
  Unit tests pass; not yet seen on a phone (no device connected).
- 2026-11-09 1.0.24: BoA and Berhan transactions show up again; Reads Awash wallet transfers and CBE ATM withdrawals. Not yet installed on a real phone.
- 2026-11-09 NAH-29 part 1: ~557 real money SMS that were dropped as
  "ignored" now parse (replayed on the 27,479-row i_raw_data export to
  28 Oct). BoA's new "Link your Fayda" footer (since 1.0.12) dropped every
  BoA alert (+408); Berhan's May 2026 "debited 2000 on …" wording (+135);
  Awash "You have sent", Telebirr transfer, "you have received", negative
  debits (+10); CBE ATM withdrawals; Dashen amounts under 1 birr.
  `RULES_VERSION` 6. `ParserCoverageReport` also writes
  `build/parser-ignored.txt` (ignored SMS by shape). Not yet on a phone.
- 2026-11-09 Admin Parser tab rebuilt: verdict line, real 7-day parse rate
  (notices left out), "Fix next" (missed money texts grouped by shape),
  per-bank table, notices and raw list folded away. Overview "Parsed this
  week" uses the real rate. Checked signed in with live data.
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
