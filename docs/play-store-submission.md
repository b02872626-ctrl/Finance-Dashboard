# Getting Kash approved on Google Play

Kash reads bank SMS, so Play review is stricter than for a normal app. This
is the list of what blocks approval today, what to change in the repo, and
what to fill in on the Play Console, in the order to do it.

Policy sources: [SMS and Call Log permissions](https://support.google.com/googleplay/android-developer/answer/10208820),
[target API level](https://developer.android.com/google/play/requirements/target-sdk),
[Financial features declaration](https://support.google.com/googleplay/android-developer/answer/9876821),
[Account deletion](https://support.google.com/googleplay/android-developer/answer/13327111),
[Data safety](https://support.google.com/googleplay/android-developer/answer/10787469).

## 1. Blockers in the code (fix before the first upload)

### 1a. Target API 36

Play rejects new apps and updates that target below API 36 since
31 August 2026. `app/build.gradle.kts` has `compileSdk = 34` / `targetSdk = 34`.

Change, in `app/build.gradle.kts`:

```kotlin
compileSdk = 36
...
targetSdk = 36
```

That needs a newer toolchain in `build.gradle.kts` and
`gradle/wrapper/gradle-wrapper.properties` (AGP 8.3 does not know API 36):

```kotlin
id("com.android.application") version "8.9.1" apply false
id("org.jetbrains.kotlin.android") version "2.0.21" apply false
id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false   // add
```

```
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
```

With Kotlin 2.x the Compose compiler comes from the
`org.jetbrains.kotlin.plugin.compose` plugin: apply it in `app/build.gradle.kts`
and delete the `composeOptions { kotlinCompilerExtensionVersion = ... }` block.
Check Paparazzi still runs (1.3.5 supports Kotlin 2.0; bump if needed).

Things that change behaviour when targeting 35/36, check on a device:

- Edge-to-edge is forced on API 35+. The app already calls `enableEdgeToEdge()`
  and sets bar colours per screen, so this should be a visual check only.
- Foreground service: `dataSync` type now has a 6-hour-per-day cap on API 35+.
  `SmsIngestService` only runs for the historical scan, so it is fine, but a
  very large inbox should finish well under that.
- Predictive back is on by default on API 36. Confirm the onboarding back
  steps and the PIN lock screen behave.
- 16 KB page size: the app has no native libraries, so nothing to do.

This was not changed in this branch because this cloud session has no
Android SDK to build with. Do it in Android Studio, run
`./gradlew testDebugUnitTest` and `./gradlew bundleRelease`, and install the
release build on a phone before uploading.

### 1b. Stop reading SMS that is not from a bank

`MoneySenderScout` scans messages from every business sender ID not on the
bank list and reports sender name, count and date to the admin dashboard.
The SMS policy only allows SMS access for the app's core functionality, and
reviewers read the code paths behind the permission. Scanning non-bank
senders to grow the bank list is a secondary purpose and the most likely
single reason for a rejection. Before submitting, either:

- remove `MoneySenderScout` and its call site, and drop the matching
  paragraph ("Banks we don't support yet") from `Web/privacy.html`; or
- make it an explicit opt-in switch in Settings, off by default, and say so
  in the policy.

Removing is the safer choice for the first submission.

### 1c. Raw SMS text uploaded to Supabase

`SupabaseSyncer` uploads the full text of every bank SMS (`raw_message`) and
the admin account can read it. This is disclosed in the privacy policy and is
allowed for sync, but it must be declared honestly in Data safety (section 3)
as "Messages: SMS" collected and shared with no one, encrypted in transit,
deletable. If you can live without it, keep only the parsed transaction and
a hash of the body, which makes both the declaration form and the Data
safety form much simpler.

### 1d. Release build

- Build an Android App Bundle (`./gradlew bundleRelease`), not an APK. Play
  requires `.aab` for new apps.
- Opt in to Play App Signing on first upload and keep
  `~/.keystores/finance-lore-release.jks` as the upload key. Back it up now;
  losing it means a new app listing.
- `versionCode` must go up on every upload (currently 32).
- Keep `app/build/outputs/mapping/release/mapping.txt` and upload it with
  the bundle so crash traces are readable.

## 2. SMS permission declaration (Play Console → App content → Permissions)

Kash fits the permitted use case **SMS-based money management** (apps that
track and manage budget), which allows `READ_SMS` and `RECEIVE_SMS`. Both are
in the manifest; declare both. Do not add any other SMS permission.

Core functionality, one sentence (the form asks for this):

> Kash reads SMS sent by Ethiopian banks and mobile-money services (CBE,
> Telebirr, Bank of Abyssinia, Awash, Dashen and others) and turns them
> into a transaction history, balances and spending insights; the app has
> no other way to get this data because these services do not offer APIs.

Why each permission:

> READ_SMS: on first launch, with the user's consent, the app scans the
> existing inbox once so the history is complete from day one. Only messages
> whose sender is on a fixed list of bank sender IDs are parsed and stored;
> every other message is ignored and never leaves the phone.
>
> RECEIVE_SMS: new bank messages are parsed as they arrive so balances stay
> current without the user opening the app. The receiver drops any message
> not from a listed bank sender.

Also upload a short screen recording (under 30 s) that shows: the in-app
"Let Kash read your bank SMS" screen, the system dialog, and a bank SMS
appearing as a transaction. Reviewers approve SMS apps far more often when
the video exists.

The onboarding already has a prominent disclosure before the system dialog
(`AllowSmsScreen`: what is read, why, and that only bank messages are kept).
Keep it: the policy requires the disclosure before the runtime prompt.

## 3. Data safety form (App content → Data safety)

Answer from what the code actually does:

| Data type | Collected | Shared | Required | Purpose |
|---|---|---|---|---|
| Personal info: name, email | Yes | No | Yes (for sync account) | App functionality, account management |
| Financial info: other financial info (transactions, balances) | Yes | No | Yes | App functionality |
| Messages: SMS | Yes | No | Yes | App functionality (bank SMS only) |
| App activity: in-app actions (telemetry events, review completed, scan completed) | Yes | No | Yes | Analytics |
| App info and performance: crash/diagnostics (error events) | Yes | No | Yes | Analytics |
| Device or other IDs (install UUID) | Yes | No | Yes | Analytics |
| Feedback survey answers (App activity: other) | Yes | No | No | Analytics |

Also tick: data is encrypted in transit (HTTPS to Supabase), users can
request deletion, and account creation is offered with a deletion path.
"Shared" is No because Supabase, Google sign-in and Vercel are service
providers, which Play does not count as sharing.

If 1b is removed, nothing about non-bank senders needs declaring. If kept,
add "Messages: SMS" metadata as collected for "App functionality /
developer communications" and describe it.

## 4. Account deletion (App content → Data safety → Account deletion)

Play requires an in-app deletion flow and a public web page that explains
how to delete without the app. Both now exist:

- In app: Settings → Delete account (calls the `delete_my_account` RPC).
- Web: `Web/delete-account.html`, served at `/delete-account.html` on the
  Kash site. Paste that URL into the form.

The form also asks whether some data is kept: answer yes, and name the
feedback answers (unlinked) and the anonymous install row.

## 5. Other App content declarations

- **Privacy policy URL**: the live `/privacy.html` URL. The same URL goes in
  the store listing. The policy already covers SMS, Supabase, Google sign-in,
  retention and deletion; update it if 1b or 1c change.
- **Financial features**: Kash shows transactions but does not move money,
  lend, trade or hold funds. Declare "My app does not provide any financial
  features". Do not pick Banking.
- **Ads**: no. **Target audience**: 18 and over. **News app**: no.
  **COVID**: no. **Government app**: no.
- **Content rating**: IARC questionnaire, Finance category; comes out
  "Everyone".
- **App category**: Finance.

## 6. Store listing

- App name "Kash". Short description under 80 characters, full description
  that names the supported banks and says explicitly that only bank SMS are
  read and nothing is sold or used for ads (reviewers read this against the
  permission form).
- 512 px icon (export from `app/src/main/res/mipmap-anydpi-v26`), 1024 x 500
  feature graphic, at least 4 phone screenshots. `design/home`,
  `design/insights`, `design/onboarding` have the source frames.
- Contact email that you will actually answer; Google writes there on
  rejection.

## 7. Testing requirements for a new personal developer account

Personal accounts created after November 2023 must run a closed test with
at least 12 testers opted in for 14 continuous days before production
access is granted. Start the closed track first, invite testers, and only
then apply for production. Organisation accounts skip this.

## 8. Order of operations

1. Make the changes in 1a to 1d locally, run the tests, install the release
   bundle on a phone.
2. Deploy the site so `/privacy.html` and `/delete-account.html` are live.
3. Create the app in Play Console, upload the bundle to a closed track.
4. Fill in App content: Permissions (with video), Data safety, Account
   deletion, Financial features, Ads, Target audience, Content rating.
5. Store listing, then submit the closed track for review. SMS apps go to
   manual review and typically take 3 to 7 days.
6. Run the 14-day closed test, then apply for production.

If Google rejects with "permission not justified", reply through the appeal
form with the one-sentence core-functionality line above, the video, and
the fact that the sender list is fixed in code; that is what usually turns a
rejection around.
