# Kash — rules for every AI session

Read this at the start of every session and follow it for everything. If a
request conflicts with a rule here, stop and ask the owner before doing
anything.

## What Kash is

- A free Android app for people in Ethiopia that turns bank and wallet SMS
  into a spending record, with no bank login and no manual entry.
- It reads SMS from CBE, CBE Birr, Telebirr, Bank of Abyssinia, Awash,
  Dashen, Berhan and NIB (35 parsers), keeps everything on the phone first,
  and syncs to Supabase.
- Screens: Home, Review, History, Analytics, Accounts, Add, Settings, behind a
  4-digit PIN. Dates can be shown in the Ethiopian calendar.
- Package `et.kash.app`, version 1.0.17 (versionCode 37), minSdk 23,
  targetSdk 36. Testers install the APK from the download page; Play Store
  submission is in progress (`docs/play-store-submission.md`).
- Not paid. No in-app purchases.

## Parts of the repo

- `app/` — the Android app: Kotlin, Jetpack Compose, Room, WorkManager.
  Kotlin packages stay `com.financeapp`.
  - `parsing/` — `SmsParserEngine` and one `BankParser` per SMS shape.
  - `sms/` — reading and receiving SMS. `remote/` — Supabase auth and sync.
  - `ui/kash/` — the Kash design system. `ui/screens/` — the screens.
- `Web/` — static site on Vercel (deploys from `main`): landing, download
  page, privacy policy, account deletion, admin dashboard (`Web/admin/`).
  `Web/apk/` holds the one current APK.
- `supabase/` — SQL files run by hand in the Supabase SQL editor.
- `design/` — design references for the Kash redesign.
- `docs/` — project memory (see `CLAUDE.md` for which file holds what).

## Architecture rules (never break these)

1. **Offline first.** The phone's Room database is the source of truth for
   the user. Supabase is a sync target; the app must work with no network.
2. **Parsers are ordered.** Each SMS goes to the first parser in
   `SmsParserEngine.parsers` that matches. Put specific parsers before
   permissive ones. `UnknownBankParser` stays last.
3. **Only money SMS leave the phone.** One-time codes, promos and notices are
   never uploaded. Only SMS from known bank senders are read.
4. **One cloud row per SMS.** A transaction's Supabase key comes from the SMS
   (`SupabaseTransactionKey`), so re-syncing never makes duplicates.
5. **Transfers between the user's own accounts are not spending or income.**
6. **Row Level Security on every Supabase table.** A user only ever sees
   their own rows. The admin dashboard reads only through `admin_*` views
   that check the caller is an admin; never open a table to it directly.
7. **Releases come only from up-to-date `main`** (see Git workflow in
   `CLAUDE.md`). The APK on the site is always signed with the same key
   (SHA-256 `24f79763…0f1509`) so it installs as an update.

## Code rules

- Every new parser or parser fix comes with a test using a real (masked)
  SMS. Keep CBE's misspelling: match `transfer(?:r)?ed`.
- Every database change is a new SQL file in `supabase/`, named for what it
  does. Never change the schema only in the dashboard. Never ship a file that
  drops tables with user data without the owner saying so.
- Crash reports go to Sentry through `remote/CrashReporting.kt` only. Never
  attach SMS text, amounts, balances, accounts, names or phones to a report;
  `CrashReporting.scrub` must keep passing `CrashReportingTest`.
- Secrets only in `local.properties` (Supabase URL/key, `sentry.dsn`, signing) or Supabase
  secrets. Never in code, never in chat, never in a commit.
- Ask before adding any new library.
- One feature per session and per branch. At the end, say in plain English
  what changed and how the owner can test it on the phone.
- New screens use the Kash components in `ui/kash/`; check them at 360dp.
- Say clearly when a build has not been run on a real device.

## Must have automated tests

- Every parser (`app/src/test/java/com/financeapp/`), including the real-SMS corpus
  tests (`RawDataParserTest`, `BugQueueParserTest`).
- Money totals: spending, income, own-account transfers (`SpendingTest`).
- The sync key and what is uploaded (`SupabaseTransactionKeyTest`,
  `RawSmsUploadTest`).
- Crash-report scrubbing (`CrashReportingTest`).
- The PIN (`PinManagerTest`) and Ethiopian dates (`EthiopianCalendarTest`).
- Any bug that bit once: add a test that would have caught it.

Run them with `./gradlew testDebugUnitTest`.

## Naming

- Supabase tables start with `i_` (`i_users`, `i_transactions`, `i_banks`,
  `i_raw_data`).
- Parsers: `<Bank><Kind>Parser.kt` (`CbeDebitParser`, `TelebirrTransferParser`).
- APK on the site: `Web/apk/Kash-<versionName>.apk`.
- Branches: `claude/<short-topic>`.
