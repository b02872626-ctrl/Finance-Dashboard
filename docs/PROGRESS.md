# Progress

Read this first in every session. Update it before you finish: move finished
items to Done with the date, add what is next or blocked.

## Next up

- Play Store first upload: follow `docs/play-store-submission.md` §6
  (order of operations).
- Staging Supabase project, then automated tests that one user can't read
  or change another's rows and that non-admins see only their own totals in
  the admin views (gaps listed at the end of `docs/SECURITY.md`).

- Readable crash traces: release builds are minified, so Sentry shows
  renamed classes. Either add the Sentry Gradle plugin with an auth token to
  upload `mapping.txt`, or keep each release's mapping and retrace by hand.

## Blocked

- Paparazzi screenshot tests (`KashComponentsScreenshotTest`,
  `KashSmallPhoneScreenshotTest`) don't run on SDK 36: 1.3.5 can't render
  it, 2.0 needs Gradle on Java 21. Skipped by default (CI too); run them
  with `./gradlew testDebugUnitTest -PscreenshotTests`.

## Owner tasks



- Play Console: SMS permission declaration (text in
  `docs/play-store-sms-declaration.md`), Data safety, account deletion link,
  unlisted upload of the SMS demo video.
- Android OAuth client for `et.kash.app` with the upload key's SHA-1, then
  Play App Signing's SHA-1 after the first upload.
- Closed test with testers before production (new personal developer
  account rule, see the submission checklist §5).

## Done

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
