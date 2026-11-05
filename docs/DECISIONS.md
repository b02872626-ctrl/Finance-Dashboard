# Decisions

Why Kash is the way it is. Newest first. Never delete or edit an entry: to
reverse one, add a new dated entry that says so. Do not reverse a logged
decision without asking the owner.

Entries before 2026-11-05 were reconstructed from the git history.

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
