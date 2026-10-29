# Google Play: SMS permission submission

Answers to paste into Play Console for Kash's `READ_SMS` and `RECEIVE_SMS`.
Google's SMS policy lists **"SMS-based money management"** as an allowed
use for apps that aren't the default SMS app: track and manage a budget
from bank SMS. Budgeting apps "may not exfiltrate or share non-financial or
personal SMS history".
Policy: https://support.google.com/googleplay/android-developer/answer/10208820

Keep this file, `Web/privacy.html` and the in-app SMS screen
(`AllowSmsScreen` in `OnboardingFlow.kt`) saying the same thing. If SMS use
changes (new upload, new sender types), the form has to be submitted again.

## 1. Permissions Declaration Form

**Core functionality:** SMS-based money management.

**Describe how the app uses the permissions** (short):

> Kash is a personal budgeting app for Ethiopia. It builds the user's
> transaction history, balances and spending categories from the SMS that
> their banks and mobile-money services (CBE, CBE Birr, Telebirr, Awash,
> Dashen, Bank of Abyssinia, Berhan Bank) send after every payment. Ethiopian
> banks offer no open-banking API, so these SMS are the only source of the
> user's transactions. READ_SMS imports the history already on the phone the
> first time the app opens; RECEIVE_SMS records each new bank SMS as it
> arrives. The app only keeps messages whose sender is on its bank list,
> and only uploads the ones about money: one-time codes and promotions from
> those banks stay on the phone. Personal messages are never stored or
> uploaded.

**Why there's no alternative:** no Ethiopian bank or wallet offers an API
or account-aggregation service, and asking users to type every transaction
defeats the app's purpose. Manual entry is offered when SMS access is denied,
but the app's core function (automatic tracking) doesn't work without it.

**Video (required):** record about 30 seconds on a phone with bank SMS:
1. Fresh install → Welcome → "Let Kash read your bank SMS" screen (the
   disclosure). Pause on it.
2. Tap Allow SMS access → Android's prompt → Allow.
3. The scan runs and transactions appear on Home, with the bank SMS each
   one came from.

Upload it unlisted to YouTube or Google Drive and paste the link.

## 2. Data safety form

Data **collected** (sent off the phone), all **encrypted in transit**, users
**can request deletion** (Settings → Delete account). Nothing is **shared**
with third parties (Supabase/Vercel/Google are service providers, which
Google does not count as sharing).

| Data type | Collected | Purpose | Optional? |
|---|---|---|---|
| Financial info → Purchase history / Other financial info | Yes | App functionality, Analytics (parser fixes) | Required for automatic tracking |
| Messages → SMS or MMS | Yes (money SMS from bank and wallet senders only; no codes or promos) | App functionality | Required for automatic tracking |
| Personal info → Email address, Name | Yes | Account management | Required |
| App activity → App interactions | Yes | Analytics | Required |
| App info and performance → Crash logs / Diagnostics | Yes | Analytics | Required |
| Device or other IDs | Yes (random install ID) | Analytics | Required |

Not collected: location, contacts, photos, files, audio, health, web history.
The app shows no ads.

## 3. Store listing

Policy requires the SMS feature to be prominent in the description.

**Short description (80 chars max):**

> Track spending automatically from your CBE, Telebirr and other bank SMS.

**Full description, opening paragraph:**

> Kash reads the SMS your bank sends after every payment and turns them into
> a clear transaction history, with no typing. It works with CBE, CBE Birr,
> Telebirr, Awash, Dashen, Bank of Abyssinia and Berhan Bank.
>
> To do this, Kash asks for permission to read SMS. It only keeps messages
> from those banks and wallets; personal messages are never stored or
> uploaded. Your data syncs to your Kash account so you can see it on the
> web, and you can delete it at any time.

**Privacy policy URL:** https://finance-dashboard-two-virid.vercel.app/privacy.html

## 4. Before submitting

- [ ] `supabase/drop_ignored_raw_sms.sql` has been run, so no one-time codes
      are stored on the server.
- [ ] The privacy policy URL loads and matches what the app does.
- [ ] The in-app SMS screen appears before every Android SMS prompt
      (first launch, and on later launches when access is off).
- [ ] App is distributed first to an internal or closed test track; the
      SMS declaration review happens there before production.
