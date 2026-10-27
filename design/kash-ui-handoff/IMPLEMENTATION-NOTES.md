# Kash UI redesign: implementation notes

## Step 1: check against the code (27 Oct 2026, after commit `3fe89e8`)

### The inventory still matches
`existing-app-inventory.md` is the same file as `docs/ui-inventory.md`. It was regenerated from the code today and updated for the PIN feature, so it describes the app as it is now.

### The "Replaces" column checks out
Every composable named in HANDOFF.md exists under that name:
- `WelcomeStep`, `SmsAccessStep`, `ReadyToImportStep`, `CategoriesStep` ("What do you track?", to be removed), `AllSetStep`: `ui/screens/OnboardingScreen.kt`
- `SupabaseAuthScreen`: `ui/screens/SupabaseAuthScreen.kt`
- `PinSetupScreen`, `PinLockScreen`, `ChangePinScreen`: `ui/screens/PinScreens.kt`
- `DashboardScreen`: `DashboardScreen.kt`. `DailyReviewCard` (`ReviewView.AllCaughtUp`, `ReviewView.OlderCta`): `ui/components/DailyReviewCard.kt`
- `ReviewQueueScreen`, `CategorizeTransactionsScreen`: `ui/screens/`. `CategoryPickerSheet`, `CorrectionSheet`, `AddCustomCategoryDialog`: `ui/components/ReviewSheets.kt`
- `TransactionsScreen`, `TransactionDetailScreen`, `AccountsScreen`, `BankAnalyticsScreen`, `AddTransactionScreen`, `AnalyticsScreen`, `CategoryDetailScreen`, `SettingsScreen`, and the `ProfileSubScreens.kt` pages (Edit account, Privacy & Data, Notifications)

The data needed for the "Behaviour changes" list is already there:
- **Disabled-senders prefs.** `SettingsRepository.getDisabledSenders()`, `toggleSender()` and `disabledSendersFlow()`. Today they only filter the lists; the scanner doesn't check them yet (behaviour change 2).
- **Own-account check.** It exists, but `ReviewQueueScreen.kt:176` still picks `"Transfer"` (behaviour change 7).
- **Review.** `skipReview`, `undoReview`, `markNotATransaction` and `applyReviewedCategory(applyToSimilar)` exist, along with the category rule.
- **Transaction fields.** `receiptLink`, `refNumber`, `serviceCharge`, `vat`, `balance`, `accountNumber`, `counterpartyId` and `rawBody` are all on `TransactionEntity`.
- **Calendar.** `util/EthiopianCalendar.kt` and `CalendarFormatter.kt` exist.

### In the mockups but with no logic behind it yet (hidden unless noted)
| Mockup | Element | Status |
|---|---|---|
| AppAccounts | **Bank SMS** button | No screen. Hidden, as HANDOFF says. |
| AppAccounts | **+** in the header | Not specified. It will open Add transaction (cash by hand), the only "add" that exists. |
| AppAnalytics | "Where it goes · **See all 9**" | No full-list screen. It will expand the list in place, using the real count. |
| AppAnalyticsMore | "Where you pay most · **See all**" | No screen. It will expand in place, like the categories list. |
| AppReview, AppReviewCategory | Category names *Family, Salary, Refund, Airtime, Eating out* and groups *Recent / Everyday / Money in and between accounts* | These are samples. Chips and groups come from `TransactionCategoryCatalog` (sections *Common / Lifestyle / Money in/out*) plus custom categories and recents. No new categories are invented. |
| AppReview | "Same sender · 3 times this month" | Can be counted from the DB (same `merchantKey`, this month). It will be built and the row hidden when there's no key. |
| AppAllowSMS | "18 months of history, at once" | The reader has no 18-month cap; it reads the whole inbox. The copy has to change ("Your full SMS history, at once") or a cap has to be added. **Needs your call.** |
| AppAllowSMS | "CBE, Telebirr, Awash, Dashen and 3 more" | The parsers cover CBE, CBE Birr, Telebirr, Awash, Dashen, BoA and Berhan. The line will be built from that list. |
| AppSettings, AppEditAccount | User's name and "MA" initials | The name is only set from Edit account; sign-up never asks for it. Until it's set, the screens fall back to the email. |
| AppSignIn | Official Google button | Pending asset. Placeholder G as drawn. |
| All | Bank logos | Pending. `BankBadge` initials. Only CBE / Telebirr / BoA have colours; other banks use slate. |
| AppSystemMessages | "Reading your bank SMS…" with a progress bar | The foreground service has a count, so a determinate bar is possible. It will be built. |
| AppHome | Pull down to retry a scan ("Pull down on Home to try again") | Pull-to-refresh exists on History only. It will be added to Home and call the same rescan. |

Nothing else in the 62 screens needs logic that doesn't already exist or isn't in the "Behaviour changes" list.

## Step 2: foundation (built, waiting for review)
Everything is new and additive; no screen uses it yet. Paths are under `app/src/main/`.
- `java/com/financeapp/ui/kash/KashTheme.kt`: colours, Figtree type scale (tabular figures), shapes, spacing, sizes, `KashTheme`, and `kashClickable` (fades to 88% when pressed and 40% when disabled, no ripple)
- `java/com/financeapp/ui/kash/KashIcons.kt`: the 23 stroke icons as `ImageVector`s, generated from the SVGs, plus `Info` for error lines
- `java/com/financeapp/ui/kash/components/`: every component, each with `@Preview`s
  - `KashControls.kt`: Button, IconButton, Chip, Switch
  - `KashLists.kt`: TransactionRow (+ pill), DayHeader, KeyValueList, ListGroup/ListRow, RadioRow, Notice, EmptyState
  - `KashFeedback.kt`: Spinner, Skeleton, SkeletonRow, Snackbar, StepBar, WeekChart, BankBadge, AccountCard, AppIcon
  - `KashInputs.kt`: SearchField, TextField
  - `KashLayout.kt`: Screen (+ handle, sub-page TopBar), TabBar, Sheet, Dialog
  - `KashPinPad.kt`: PinPad
  - `KashBasics.kt`: money formatting (`1,250.00`, real − and + signs), `KashFigure`, the bank badge table, dashed border
- `res/font/figtree_{regular,medium,semibold,bold}.ttf`: the full static Figtree under the OFL (licence in `brand/Figtree-OFL.txt`)
- Brand assets:
  - `ic_launcher_background` / `ic_launcher_foreground` (replaced) and `ic_launcher_monochrome` (themed icon, new)
  - `ic_stat_kash`: notification icon, now used by both notification builders
  - `values-v31/themes.xml`: Android 12+ splash
  - `splash_window.xml`: splash for older Android, switched on in step 3

Screenshot tests: `app/src/test/java/com/financeapp/ui/kash/KashComponentsScreenshotTest.kt` (Paparazzi, JVM only). Run `./gradlew recordPaparazziDebug` to save the images to `app/src/test/snapshots/images/`.

## Steps 3–6: done (27 Oct 2026)
Every screen in the handoff is rebuilt on the Kash components. The old theme, components, screens, Google Sans fonts and 50 unused icons are gone (APK 5.3 MB → 2.0 MB).

Decisions taken from your answers:
- "18 months of history, at once" now reads "Your full SMS history, at once" (the reader has no cap).
- "+" on Accounts opens Add transaction.
- No "See all" links on Analytics. Categories and payees are listed in full on the page.

Behaviour changes 1–8 are all in place. Notes on each:
1. First-launch order. "I already have an account" goes straight to sign in, then picks up the SMS steps.
2. Switching a bank off stops the scanner reading its new SMS. Rows already imported stay in your lists.
3. The "categorize today" notification and every Review entry point open Review.
4. The summary numbers come from `OnboardingViewModel`.
5. SMS denied has its own screen, with "Open settings" going to the app's system settings.
6. Export lives in Settings, with period and account filters. Files are named `kash_transactions_….csv`.
7. Review pre-selects "Own accounts", and the predictor now suggests it for internal transfers.
8. Every date goes through `util/KashDates`. History's "This month" and the Analytics month follow the calendar setting.

Hidden because there is no logic behind them yet:
- Accounts **Bank SMS** button
- Transaction **Not a transaction? Hide it**. Marking a row doesn't remove it from lists yet.
- Review card **Same sender: N times this month**. The card shows the account and balance after instead.
- Account card **Spent this month** bar: shown only when there was spending last month (it shows the share of last month spent so far).

Still needs assets:
- The official **Continue with Google** button and G mark. A placeholder is drawn to Google's colours.
- **Bank logos.** `BankBadge` shows initials. Three PNGs (`res/drawable-nodpi/logo_cbe|logo_telebirr|logo_boa.png`) are still in the repo, unused. If they are the official marks, they can go into `BankBadge`.

Checked on the JVM (Paparazzi): components at 390 dp, and rows, buttons and dates at 360 dp with long names and the Ethiopian calendar (`KashSmallPhoneScreenshotTest`). No device was connected, so the screens themselves haven't been run on a phone yet.
