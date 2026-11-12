# Decisions

Why Kash is the way it is. Newest first. Never delete or edit an entry: to
reverse one, add a new dated entry that says so. Do not reverse a logged
decision without asking the owner.

Entries before 2026-11-05 were reconstructed from the git history.

## 2026-11-12 — Download page: font loads after first paint, pictures are HTML

For booth visitors on slow 3G (NAH-38). The Google Fonts stylesheet on
`Web/download.html` loads with `media="print"` swapped to `all` on load,
so the page and its Download button paint in the system font first instead
of waiting on two extra connections. The install-step pictures are small
HTML/CSS drawings of the Android screens, not image files: no extra
downloads, sharp on any screen. They say "Kash.apk" (no version) so
`scripts/release.sh`'s "old version still on the page" check stays valid.

## 2026-11-12 — App session replay, fully masked (reverses part of 2026-11-09)

Owner's call: turn on PostHog session replay in the Android app to see
where people get stuck. Masked fully: `maskAllTextInputs` (all Text and
TextFields), `maskAllImages`, no logcat, screenshot mode (needed for
Compose). Set in `ProductAnalytics.replayConfig()`; `ProductAnalyticsTest`
fails if any mask is turned off. Same release-only, install-ID-only rules
as the events. The website and web dashboard stay without replay (the
dashboard shows money and uses the no-recorder posthog-js build). Privacy
policy and Play data-safety notes now say recordings exist and what they
hide. Recordings appear only after the owner turns on "Record user
sessions" in PostHog.

## 2026-11-12 — Home shows only the banks Kash follows, and less around them

Owner's call from screenshots. The hand-entered "Cash" account gets no card
on Home: cash isn't tracked as a balance. A bank switched off in "Banks Kash
follows" leaves Home and the total too (switching it off already stopped new
SMS; its old balance kept showing). The "Leaves out N accounts with no SMS
since …" line is gone. Quiet accounts still stay out of the total and still
say "Last updated <month>" on their card. The weekly bar strip under Review
is gone from Home. Account cards use the Chip radius (12dp) instead of
Control (16dp).

## 2026-11-12 — The name the banks greet always counts as the owner

On 1.0.32 the owner saw their own CBE, Telebirr and CBE Birr cards marked
"<their name>'s?". The owner's name came from Settings (or the sign-in
account) and only fell back to the name most SMS greet when Settings was
empty, so a nickname or English name in Settings made every account look
like someone else's. Now both names count as the owner. An account is
"someone else's" only when its SMS greet a name that matches neither.

## 2026-11-12 — Income and low balance skip the daily alert cap

Owner's call after a screenshot where "16.4k just came in" and "CBE is
getting low" only arrived in the 6:06 p.m. digest. Instant alerts were held
for the digest once 3 had shown that day. Money in and low balance are only
useful right away, so they now never wait for that cap and don't count
toward it (`NotificationRules.UNCAPPED`). Fee alerts still share the cap of
3 a day. Quiet hours still hold everything except the PIN lockout. The
digest card also stopped repeating its headline as its first line.

## 2026-11-12 — Moves between your own accounts are found by pairing the two texts

Spending and income were inflated: only Telebirr's own transfer texts and
some Berhan credits were typed as own-account moves, so CBE → CBE Birr,
CBE 1 → CBE 2, CBE → Awash and the like counted as spending on one side and
income on the other. Names can't tell them apart (people hold accounts under
other names), and most bank SMS don't print the other account's number. So
`OwnTransferPairing` pairs a money-out row and a money-in row of the same
amount within 5 minutes on two different accounts (another bank, or another
last-4 digits at the same bank), and files both as "Own accounts". A shared
reference number is the fallback for legs that can't be told apart by
account. A pair is taken only when neither leg has a rival partner, and a
row with a category of its own is never touched: wrongly hiding a payment
is worse than leaving a transfer in. A transfer already typed
INTERNAL_TRANSFER ("to CBE…" / "from CBE…") anchors the other bank's leg.
It runs after every SMS scan (new texts and old history), before alerts. The
category syncs, so the web dashboard needs no change. On the owner's export,
pairing at 60 seconds found ~400 debit/credit pairs (nearly all CBE ↔
Telebirr); shared reference numbers matched none there, so the reference
fallback is a safety net, not the main path.

## 2026-11-12 — No real people's details in the public repo

The repo is public. Parser comments and tests had real testers' names, two
full phone numbers, the owner's full account number, and a tester's
balances copied from SMS. They were replaced with made-up ones (each real
person mapped to one made-up name so the tests still check the same
things); the sync-key test's reference value was recomputed with the
database formula. Old commits still hold the originals (main's history is
never rewritten). From now on: made-up names, numbers and balances
everywhere in the repo (RULES.md, Code rules).

## 2026-11-11 — Other people's accounts and quiet accounts on Home

A tester's phone also gets bank SMS for accounts that belong to other
people (their number was registered for them), and Home counted those
accounts and their old balances in the total. Every bank SMS greets the
account holder, so Home now reads the "Dear …" name per account
(AccountOwnership): when it isn't the user (their Settings/sign-in first
name, else the name most of their SMS greet; short forms match, "Dear
Customer" is ignored) the card says "<Name>'s?" and Home offers once to
hide those accounts ("Hide them" uses the existing hide; "They're mine"
keeps them). Accounts with no SMS for 90 days leave the total, show "Last
updated <month>", sort last, and a line under the total says what was left
out.

## 2026-11-11 — Several incomes can count for Payday

Owner's follow-up. "Pick your income" toggles each sender on or off (a
salary, rent, a second job), each saved as a monthly recurring income with
the note "Salary". Payday follows the biggest one's rhythm, and each
paycheck is everything the picked senders paid from that payday to the
next.

## 2026-11-11 — People can say which sender is their salary

Owner's request. The web Payday card has "Pick your salary": a list of
everyone who paid the user in the last 6 months (real payers first), and
"This is my salary". The pick is saved as a monthly recurring income in
`i_income_entries` with the note "Salary" (one at a time; no schema
change, same RLS), so it shows in the Income tab and follows the account.
Payday uses it before its own guess, even when the rules would skip that
sender; "Use automatic" clears the note.

## 2026-11-11 — Only real payers count as income for payday and "income landed"

The owner's Payday card picked "Telebirr wallet top-up" as their income
(Telebirr's "your account has been credited" names no sender: it is a
top-up from the user's own bank or a Mela loan), so "70% spent by day 2"
and the rest were wrong. Regular income (web Payday) and looksLikeSalary
(app "income landed") now skip non-payers: top-ups, wallets, Mela/loans,
deposits, cash, interest, refunds/reversals and bank names. On the web a
payday must also be at least a quarter of a usual month's spending. The
card compares ETB spent with the usual pace (no more "0% left, slower than
usual") and expects the next payday on the usual day of the month.

## 2026-11-11 — Web dashboard: dark mode, hide amounts, and a polish round

Owner's requests. Dark mode follows the system and a sun/moon switch
overrides it (remembered per browser, applied before paint); it swaps the
Kash tokens and the handful of colours written out by hand. An eye button
blurs every amount for showing the dashboard to someone (remembered too).
Payday's chart is ETB spent since payday against the usual pace and the
paycheck (a "share left" line went flat at 0 when spending passed the
paycheck), and shows the last cycle until this one has 3 days. Day-of-week
is Monday-first bars with a one-line takeaway, averaged over every day of
that weekday in the period (quiet days too). Category cards are calm white
cards. Counterparty rows lose their tick boxes and open that counterparty's
transactions instead (batch-categorising from the dashboard is gone).

## 2026-11-11 — One date filter for the whole web dashboard, pinned on scroll

Owner's call. The period bar at the top (now This week, This month, Last
month, 30 days, 90 days, This year, All time, Pick dates) drives the
spending line, its total and comparison, Where you pay most, Spent from
and every section below; the line's own range chips are gone, so the two
can't disagree. The today / this week / this month tiles stay fixed, and
Money in vs out keeps its 6/12-month switch. Low-balance days shows last
month when the filter is Last month. The header with the filter sticks to
the top while scrolling and shrinks to one row (just the filter on a
phone).

## 2026-11-11 — Web dashboard leads with the phone's Analytics, plus cash-flow insights

Owner-approved plan (NAH-30). The dashboard's six period stat cards and
the old 30-day bars and 12-month income/expense bars are replaced by: total
balance, today, this week and this month (each vs the same point before);
a daily spending line with This week / This month / 30 days / Pick dates;
Where you pay most and Spent from for that range; money in vs out by month
(6 or 12) with a kept line; a payday card (regular income = same sender,
±20% amount, 2 of the last 3 months, as `looksLikeSalary`; when it lands,
when 70% is gone, a burn-down against the usual pace, each cycle measured
against its own paycheck); and low-balance days per account this month
(floor = 2× the account's average daily spend, last SMS balance carried
forward). "Spend by Category" shows only when something is labelled. The
transaction "In context" block ranks ties together, names the month, works
for money in, and follows the Ethiopian calendar.
All the numbers live in `Web/insights.js` (pure, no DOM), tested by
`tests/web/insights.test.mjs` under Node in CI; app.js imports the
spending rules, Ethiopian calendar and account grouping from it, so the
web gets the phone's Berhan two-masks fix too.

## 2026-11-11 — Kash Wrapped is built from SMS data, not categories

NAH-34. A month's story in pages (spent and vs the month before, kept,
biggest payment, who you paid most often, bank fees, most expensive
weekday and time of day, one tip). It uses only what every SMS has, since
most people never categorise. Offered on Home in the first week of a
month for last month ("Later" hides it for that month) and from a row on
Analytics; needs at least 3 payments. "Share my month" makes a picture
with totals, habits and the download link, never account numbers,
balances or the names of people paid; the shared text carries
`?ref=wrapped` so PostHog counts installs from shares. Monthly only;
a yearly one can come later.

## 2026-11-11 — Analytics leads with today, this week and a spending line

Owner's call (NAH-27): most people never categorise, so the Analytics tab
no longer leads with categories. The hero is today's spend (and payment
count) with "This week" and "This month" tiles, each against the same
point last week or month. Below is a daily spending line for a chosen
range (This week, This month, 30 days, or picked dates) with the total,
the change against the period before, the daily average (dashed) and a
touch-to-read day. Where you pay most and Spent from follow the range;
six months of money in and out stays; "Where it goes" moves to the end
and shows only when something is labelled. The month picker and the big
"Nothing has a category yet" block are gone.

## 2026-11-11 — Tester builds update themselves, with a pop-up

NAH-35. Update (Home card, Settings → Updates, and a new pop-up when Kash
opens) now downloads the APK inside Kash and hands it to Android's
installer instead of opening the browser. `version.json` carries `apk`
(only `https://kash-140.pages.dev/apk/…` is accepted) and `sha256`;
`scripts/release.sh` writes both and `verify` checks they match, and the
app refuses a download whose SHA-256 differs. Older version.json files
without the fields fall back to the download page. The pop-up's "Later"
hides it for 3 days for that version; the Home card stays as before.
Installing needs `REQUEST_INSTALL_PACKAGES`, which Google Play does not
allow for self-updating apps: it is in the main manifest for now (there is
no Play build yet) and must be removed for the Play upload
(`docs/play-store-submission.md`). Play installs never show any of this.

## 2026-11-11 — Notifications talk like a friend texting

Owner approved a casual voice for every notification (NAH-33): lowercase,
loose grammar, "u"/"ur", emoji, e.g. "hey Dawi, CBE is getting low 👀" /
"only 180 Br left, thats like 2 days of ur usual spending". Amounts under
1,000 stay as they are (250, 12.50) and 1,000 and up become "k" (1.2k,
32k), via `NotifyText.k`. "Br" only appears in low balance and fee
messages. The first name comes from Settings, which is now filled from the
sign-in account (Google's name) when empty, on sign-in or the next token
refresh; with no name the copy starts with "hey". The owner accepts the
Play review risk of the informal tone. App-update copy is unchanged
(NAH-35).

## 2026-11-11 — No scanning, syncing or backup notifications; no spend alerts

Owner's call (NAH-33). Users never see a notification about Kash's own
plumbing: "Reading your bank SMS…", "Found N new transactions", "Waiting
for internet to back up" and "Backed up" are gone, and their channels are
deleted on update. The in-app scan (opening Kash, pull to refresh, SMS
access granted) now runs in `SmsIngestWorker` instead of a foreground
service, which Android only allows with a visible notification;
`SmsIngestService` and the FOREGROUND_SERVICE / dataSync permissions are
removed (one less thing to declare on Play). Ordinary spends still get no
notification: the bank already sent an SMS, and spends to categorize wait
for the evening digest. What remains: low balance, income landed, fee
spike, fees today, PIN lockout, review ready, unread bank, weekly, month
end, backlog, re-engage, SMS access lost, app update.

Low balance changed with it: each account's automatic floor is 2× that
account's own average daily spend (was everyone's spending, so a small,
rarely used wallet was always "low"), and it alerts once when the balance
drops below the floor, not every day it stays there.

## 2026-11-11 — No AI in the app; Gemini only on the web dashboard (FOW summit)

Kash is applying for a free booth at the Future of Work Ethiopia Summit
(28 Nov 2026, apply by 18 Nov), aimed at "AI startups". The owner decided
not to add AI to the Android app. The AI story is that Kash is built end
to end with AI agents, plus an optional "Ask Kash" on the web dashboard
using Gemini (NAH-40). Gemini sees only per-user summaries (totals by
category, merchant and month), never raw SMS, account numbers or phones.
It is off until the user turns it on, the key stays in Supabase secrets,
and it is called through an Edge Function as that user. This needs a
privacy policy and SECURITY.md update when it lands. Goal for the summit:
100 downloads and 100 active users (NAH-32).

## 2026-11-10 — Accounts live on Home; the Accounts tab is gone

Owner picked "strip under the total" from three layouts (strip, a list in
the sheet, tap the total to expand). Each account is a small card under the
total balance in the hero; the strip scrolls sideways so Review and the week
stay above the fold. Its amounts follow the total's eye toggle (hidden on
every launch). Tapping a card opens Account detail (the NAH-5 screen). The
Accounts tab, its swipeable big cards and "Spent this month" were removed;
the `accounts` route stays only to send old notification links to Home.
Replaces most of NAH-12 (Banks page redesign).

## 2026-11-10 — Blocked notifications are shown in Settings, not asked for on launch

Onboarding asks for notifications in the same Android prompt as SMS. Anyone
who refused, or turned them off in the phone's settings, used to see
"Notifications: On" in Kash and get nothing. Settings now checks what
Android allows: the row says "Blocked" and Settings → Notifications shows a
card that asks again (once) or opens the phone's settings. We don't nag on
every launch; the one-time ask stays in onboarding.

## 2026-11-10 — New tester builds show as a card on Home, not only a notification

The update notification needs notification permission, which onboarding
never asks for on Android 13+, so many testers never saw it. `UpdateChecker`
now keeps the last `version.json` it read (with `notes`) and Home shows a
card for a newer build; "Later" hides it until a newer one. Settings →
Updates shows the versions and a "Check now". Play installs never see the
card (Play updates the app and forbids other update sources).

## 2026-11-09 — Safety footers are cut off before the ignore rules run

Banks now end real alerts with safety lines: BoA "Link your Fayda: <url>"
and Awash "Alert: Awash Bank will never ask for your PIN … OTP. Do not
share …". Those words are ignore patterns for notices, so every alert with
the footer was dropped (all BoA alerts since 1.0.12). The engine now strips
known footers (`noticeFooters`) before the ignore patterns and the money
check; parsers still get the full SMS. The Fayda pattern stays for real
Fayda notices. Mirrored in `Web/admin/admin.js` (`NOTICE_FOOTERS`).
BoA USD-account alerts stay ignored until Kash supports a second currency,
so dollars never mix into birr totals.

## 2026-11-09 — The admin sorts unparsed texts into money and notices

The bug queue mixed notices (activation, Fayda links, queue tokens) with
real misses, so parse rates looked under target. The admin now applies the
app's own rule from `SmsParserEngine` (ignore patterns, then "birr amount
next to a money word") in `Web/admin/admin.js`, and leaves notices out of
the parse rate. The two copies must stay in sync: when `RULES_VERSION`
changes, update `NOTICE_PATTERNS`, `AMOUNT_RE` and `MONEY_VERB_RE` too.

## 2026-11-09 — Replay real SMS before writing a parser for a "Fix next" item

The admin Parser tab lists SMS whose last status from a phone was
unparsed, and old installs report with old rules. Before writing a parser,
replay the shape through today's code (`ParserCoverageReport`, with
`KASH_RAW_CSV` pointing at an i_raw_data export). Of 14 shapes listed in
Nov 2026, 9 already parsed. Those got tests and a `RULES_VERSION` bump (to 5)
instead of new parsers, so phones re-send statuses and the list clears.

## 2026-11-09 — Tester releases go through `scripts/release.sh`, weekly

Releases were made by hand (bump, build, swap APK, edit four files), which
is slow and easy to get wrong. Now `scripts/release.sh prepare` does it from
the latest main, refuses an APK not signed with the Kash key, opens the PR,
and undoes everything if a step fails; `verify` checks the live site.
Release notes live in `Web/version.json` (`notes`) and are reused for the
tester message. One tester release a week, same-day only for crashes or
broken core flows. A bad release is fixed forward (revert, release again),
since Android can't install an older version over a newer one. Full process
in `docs/WORKFLOW.md`.

## 2026-11-09 — PostHog sends each app event right away

1.0.22 sent nothing from phones: PostHog Android waits until 20 events are
queued (`flushAt`), and Kash makes only a few per visit, so they sat on the
phone. `flushAt = 1` now; volume is tiny. Found with a diagnostic build
(`./gradlew assembleRelease -Pposthog.debug=true`, then `adb logcat -s
PostHog`), which is how to debug PostHog on a phone again. Setup failures
now log a one-line warning (class name only).

## 2026-11-09 — The web dashboard uses the sidebar-and-panel layout

Owner's call, from the same reference design as the admin. The slate hero
(header, tab chips and period chips on a dark band over a light sheet) is
gone. Tabs are now a left sidebar (a swipeable row on phones), the GC/EC
toggle and menu sit top right, and every view lives in one white panel
whose head holds the greeting and a compact period control. The six
dashboard numbers are one divided block with coloured icons. Money colours
stay: coral for spending, green for income. No behaviour changed; the
script finds tabs and periods by the same ids and classes.

## 2026-11-09 — PostHog for product analytics (reverses 2026-11-05)

Owner's call: add PostHog (EU cloud, `eu.i.posthog.com`) to the app and the
website, next to the Supabase `Telemetry` that feeds the admin dashboard
(that stays as it is). App: `com.posthog:posthog-android` 3.75.0, set up in
`remote/ProductAnalytics.kt`. Every `Telemetry.track` event is also sent to
PostHog, plus PostHog's app opened / installed / updated events. Off: screen
views, deep links, session replay, feature flags, surveys. The phone is
identified only by the random install ID (same as Sentry); properties named
`error`/`summary` are masked with `CrashReporting.scrubText`; Delete account
calls `PostHog.reset()`. Off in debug builds and when `posthog.apiKey` is not
in `local.properties`. Site: `Web/analytics.js` on the landing/dashboard,
download, privacy and delete-account pages (not `/admin/`): page views, page
leaves and `apk_download_clicked`, no autocapture or replay, no person
profiles, URLs cut before `?`/`#` (reset links carry tokens there).
posthog-js 1.438.21 is pinned on jsDelivr with an SRI hash and loads only
when the public key is filled in. The `i_downloads` counter stays.

## 2026-11-09 — Admin dashboard opens on three numbers

The owner found the admin a wall of equal-weight numbers. It now opens on
an Overview with only current installs, parsed messages and daily active
users, laid out after a reference design the owner picked (sidebar, white
panel, KPI row, block chart). The four old sections (Parser,
Categorization, Retention, Health) are unchanged but sit behind sidebar
tabs. Definitions: a current install is a phone that reported sync status
in the last 7 days (`i_device_status`, so signed-in 1.0.9+ installs only);
parsed messages is the row count of `i_transactions` (one row per SMS);
a daily active user is anyone with an Android `i_sessions` row started
that Addis Ababa day, skipping the `backfill-v1` rows. No SQL changes: the
admin already reads these tables.

## 2026-11-09 — No preview builds; the site builds only from `main`

Owner's call: per-branch preview builds on Cloudflare Pages (and Vercel)
were noise. Cloudflare's Preview branch setting is None, so only merges to
`main` build and go live at `kash-140.pages.dev`. This replaces "every
branch gets a preview link" in the 2026-11-05 Cloudflare entry. To try a
change before merging, run `Web/` locally. Supabase reset links never
worked on previews anyway (they aren't on the Redirect URLs list).

## 2026-11-09 — The web dashboard handles its own password reset links

Owners didn't know their current password, so the web dashboard got
"Forgot password?" on the sign-in page and in Settings, using Supabase's
reset email with the home page as the return address. A link that opens the
dashboard shows Settings as "Set a new password" without the old one; the
emailed link is the proof. This replaces the earlier rule (same day) that
forwarded every `type=recovery` link from the home page to `/admin/`: that
would show regular users "Access denied". Admin resets started on `/admin/`
still return to `/admin/`; one that falls back to the home page is now
finished there, which sets the same account's password. The sign-in page
shows the same message whether or not the email has an account.

## 2026-11-09 — Changing the web password needs the current one

The web dashboard's Settings asks for the current password before changing
it, so someone at an unlocked, signed-in browser can't take over the
account. `Web/app.js` checks it with a second Supabase client that saves
nothing, changes the password through that fresh session (Supabase can
refuse a change on an old one), then signs that client out; the page's own
session is not touched. Google-only accounts have no Kash password, so the
form is hidden for them. No new library and no database change.
## 2026-11-09 — Admins reset their own password from the dashboard

The owner forgot the admin password and the only way back in was SQL in the
Supabase editor. `Web/admin/` now has "Forgot password?", using Supabase's
own reset email (`resetPasswordForEmail`) with the admin page as the return
address, then `updateUser` to save the new password. The page only sends a
reset for addresses on its admin list, so it can't be used to mail app
users, and it shows the same message for any address so it doesn't reveal
which ones are admins. Who is an admin is still decided only by `is_admin()`
in the database. No new library and no database change.

## 2026-11-06 — Staging database, and every SQL change goes there first

`kash-staging` is a second Supabase project rebuilt from the files in
`supabase/` (`supabase/staging/build_setup.sh`), so the database rules can be
tested without touching real data. `supabase/staging/isolation_test.py` signs
up two throwaway users there and checks that neither can read, change, delete
or create the other's rows; it is Python with only the standard library,
because the app's Kotlin unit tests run on Android's HTTP stack, which can't
send the PATCH requests the attacks need. Staging has Confirm email off (live
keeps it on). New SQL runs on staging, then the test, then live.

Comparing the two found a hand-made live table with RLS off that anyone with
the public key could read. It was locked (not deleted) on 2026-11-05.
Rule since: no hand-made tables in `public`; see `docs/SECURITY.md`.

## 2026-11-05 — Site moved from Vercel to Cloudflare Pages

Owner's call, without a custom domain for now. The site is the Pages project
`kash` at `kash-140.pages.dev` (`kash.pages.dev` was taken), deployed from
`main` with output folder `Web/`; every branch gets a preview link. Header
rules moved from `vercel.json` to `Web/_headers`; the Vercel analytics
script was removed.

The old `finance-dashboard-two-virid.vercel.app` stays up as a 301 redirect
to the new address, because installed apps (≤ 1.0.20) and old links use it.
Exception: Vercel still serves `/version.json` directly (no redirect), so
1.0.20's update check can't break on a redirect. Keep that Vercel project
alive until no tester runs 1.0.20 or older. If Kash gets its own domain
later, the address changes once more (app, Play forms, this redirect).

## 2026-11-05 — The app tells testers about new versions itself

Kash never had its own update notice; testers used to hear about builds
from Firebase App Distribution, which stopped when releases moved to the
download page and the package became `et.kash.app`. Now `UpdateChecker`
reads `Web/version.json` (at start and every 12 hours, online only) and
posts one notification per newer version, opening the download page. It is
off for Google Play installs, which Play updates itself (Play does not allow
pointing users at other update sources). No new library: plain
`HttpURLConnection` and WorkManager.

## 2026-11-05 — Upload R8 mappings to Sentry from the build

Release builds are minified, so crash reports showed renamed classes. Added
the Sentry Gradle plugin (`io.sentry.android.gradle` 6.23.0) only to tag
each release with a mapping ID and upload `mapping.txt`. Its other features
(auto-adding SDKs, bytecode instrumentation, source upload, telemetry) are
off. The upload needs an organization token (`sentry.authToken`); without
one the build still works and skips the upload. 1.0.18 and earlier stay
unreadable because they carry no mapping ID.

## 2026-11-05 — Sentry for crashes; no PostHog, no Cloudflare for now

Kash had no crash reporting, so crashes on testers' phones were invisible.
Added `io.sentry:sentry-android-core` (no NDK, replay, tracing, screenshots
or breadcrumbs). Before sending, `CrashReporting.scrub` drops breadcrumbs,
extras and user details except the install ID, and masks numbers and quoted
text in error messages. Off in debug builds and when no DSN is set.
PostHog was not added: `Telemetry` already sends a short list of named
events to Supabase. Cloudflare was not added: the site works on Vercel;
revisit for DNS when Kash gets its own domain.

## 2026-11-05 — Project memory lives in the repo

Adopted the Mela Playbook layout: `CLAUDE.md` points to `RULES.md`,
`docs/PROGRESS.md` and this file. Every session reads them first and updates
PROGRESS and DECISIONS before finishing. Why: AI sessions have no memory of
each other, and several run in parallel on this repo.

## 2026-11-05 — The user can hide an account that isn't theirs

CBE sends "your Account" alerts for every account tied to a phone number,
including other people's or an organisation's, so they showed up as extra
cards and counted in every total. Such an account can now be hidden (local
`hidden` flag, DB 11 → 12) and shown again from Settings › Banks Kash
follows; new SMS for it arrive hidden. Shipped in 1.0.17.

## 2026-10-31 — No vs-last-month tile in Analytics

Removed from Analytics in 1.0.16.

## 2026-10-29 — Package ID is `et.kash.app`

Chosen for the Play Store; permanent once uploaded. Kotlin packages stay
`com.financeapp` to avoid a huge rename.

## 2026-10-29 — Target API 36; no money-sender scout

Play rejects apps below API 36 from 31 Aug 2026. `MoneySenderScout` (finding
banks Kash doesn't follow yet by reading other SMS) was removed so the app
only reads known bank senders, which Play's SMS policy requires.

## 2026-10-29 — Only money SMS are uploaded

One-time codes, promos and notices stay on the phone (1.0.13). Money SMS are
still uploaded for parser work, so the Play listing declares Messages: SMS.

## 2026-10-29 — One card per account when a bank has several

Accounts at the same bank show as CBE 1, CBE 2 instead of being merged.

## 2026-10-29 — Branches must contain the latest `main`

A stale branch nearly shipped the pre-Kash screens over the redesign. Added
`.githooks/pre-push` and `.github/workflows/up-to-date.yml`; rules in
`CLAUDE.md`.

## 2026-10-28 — One cloud row per SMS

The Supabase key for a transaction is derived from its SMS, so re-syncs and
reinstalls never create duplicates.

## 2026-10-28 — Admin dashboard is admin-only

`Web/admin/` reads `admin_*` views that are `security_invoker`; admin access
is decided by `is_admin()` policies in the database, not by the page.

## 2026-10-27 — Every app change ships to the download page

Owner's rule: after any app change, bump the version, build the signed
release, replace `Web/apk/`, update `Web/download.html`, push to `main`.
Testers install from the download page.

## 2026-10-27 — 4-digit app PIN

The app opens behind a 4-digit PIN.

## 2026-10-26 — Renamed to Kash

Finance Lore (and "Piggy Bank") became Kash, with a full redesign.

## 2026-10-26 — Flat 2D phone in the download hero

Owner chose it over the interactive 3D phones.

## 2026-10-26 — Own-account transfers are not spending or income

Moving money between your own accounts would otherwise count twice.
