# Decisions

Why Kash is the way it is. Newest first. Never delete or edit an entry: to
reverse one, add a new dated entry that says so. Do not reverse a logged
decision without asking the owner.

Entries before 2026-11-05 were reconstructed from the git history.

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
