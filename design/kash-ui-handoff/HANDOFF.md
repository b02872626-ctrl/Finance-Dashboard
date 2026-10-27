# Kash — UI redesign handoff

A new look for the existing Kash Android app ("Slate + sand", light theme only for launch). **Only the UI changes.** Keep the data layer, SMS parsing, sync, auth, PIN logic, view models and navigation logic, except for the changes listed under "Behaviour changes".

## What's in this folder

| Path | What it is |
|---|---|
| `screens/png/` | All 62 screens as images (2× pixels). **The source of truth for the look.** Most are 390×844; the `AppSmall*` ones are 360×780. |
| `screens/html/` | The same screens as HTML, for exact sizes, spacing and copy. `<x-import component-from-global-scope="Kash.Button" variant="primary">` means "the Kash Button component with these props"; `var(--token)` values are in `design-system/tokens.css`; `href="AppX.dc.html"` is where a tap goes. |
| `design-system/README.md` | The rules: screen structure, colour, type, spacing, states and patterns, navigation, icons, accessibility. **Read first.** |
| `design-system/tokens.json`, `tokens.css` | Every colour, type style, radius, spacing and size, with usage notes. |
| `design-system/components/*.md` | One page per component (29): what it is, its props, do and don't. |
| `design-system/components/bundle.js`, `bundle.css`, `index.d.ts` | A web reference implementation of every component. Read it for exact measurements; don't ship it. |
| `icons/*.svg` | The 23 stroke icons (24px grid, 2px round stroke, `currentColor`). |
| `brand/` | The app icon: full icon, bars-only mark for the splash, and the monochrome (themed) version. |
| `existing-app-inventory.md` | The map of today's app (composables, routes, strings, flows), read from the code. |
| `original-ui-brief.md` | The original screen-by-screen brief. |

## The look in one paragraph

Every app screen has two layers: a **slate** (#3D4859) top area with the headline number and actions, and a **light sheet** (#F4F5F7, 28dp top corners, a grab handle) rising over it with lists and details. Onboarding is light with a slate primary button. Welcome is light on top with a slate panel rising from the bottom. PIN screens are all slate. **Sand** (#E9DFC6) appears at most once per screen, for the next thing to do. Money colours (green in, rust out) appear only on the light sheet, always with a sign and an arrow; transfers between your own accounts are a lighter grey with a two-way arrow and are left out of totals. Font: **Figtree** (400/500/600/700, OFL). Corners: sheet 28, card 20, control 16, field 14, chip 12. **No drop shadows. No text under tab icons. Button labels never wrap.**

## Screens

"Replaces" names the existing composable from `existing-app-inventory.md`.

### 1 · First launch
| File | Screen | Replaces | Notes |
|---|---|---|---|
| AppWelcome | Welcome | `WelcomeStep` | Rolling words (Spending, Income, Transfers, Bills, Trends), ~2.4 s each; still when the system asks for less motion. |
| AppAllowSMS | Allow SMS · step 1 of 3 | `SmsAccessStep` | Same permission request as today. |
| AppSMSDenied | SMS denied | new | Shown when refused, including "Don't ask again". "Open settings" opens the app's system settings. |
| AppSources | Choose sources · step 2 of 3 | `ReadyToImportStep` | Switches write the existing disabled-senders prefs. |
| AppSignIn | Sign in · step 3 of 3 | `SupabaseAuthScreen` (onboarding) | Keep all existing error and success texts, shown under the fields. |
| AppSummary | Your money at a glance | `AllSetStep` | Transactions found, biggest payee by total, average money in a month, last 6 months with real month labels. Hide rows with no data. |
| AppPinCreate | Create a PIN | `PinSetupScreen` step 1 | |

### 2 · Home and the daily review
| File | Screen | Replaces | Notes |
|---|---|---|---|
| AppHome | Home | `DashboardScreen` | + (add) and settings in the top bar. Review button with the pending count. Banks block, fake search and type chips removed. |
| AppHomeEmpty | Home, nothing yet | empty state | |
| AppHomeCaughtUp | Home, caught up, balance hidden | `DailyReviewCard` AllCaughtUp | Balance hidden on every launch, as today. |
| AppHomeOlder | Home, only older items to review | `DailyReviewCard` OlderCta | "Review older ones" with the count. |
| AppReview | Review | `ReviewQueueScreen` + `CategorizeTransactionsScreen` | Pre-selected chip = the prediction. Confidence %, "Low-confidence parse" and "SMS · time" are dropped. |
| AppReviewTransfer | Review, own account | transfer flag | When the existing own-account check fires, pre-select "Own accounts" and show the note. |
| AppReviewUndo | After confirming | snackbar | Undo above the buttons. |
| AppEditTransaction | Check this one | `CorrectionSheet` | Long-press on the card. |
| AppReviewCategory | Pick a category | `CategoryPickerSheet` | Bottom switch = the "apply to similar" rule. |
| AppNewCategory | New category | new-category dialog | |
| AppReviewApplyAll | Apply to all? | apply-to-all dialog | Lists the other uncategorised rows from that sender. |
| AppReviewDone | All caught up | review empty state | |

### 3 · Money
| File | Screen | Replaces | Notes |
|---|---|---|---|
| AppHistory | History (tab) | `TransactionsScreen` | Rows: name / account · time + category pill. Export moved to Settings. "This month" opens AppHistoryPeriod; the calendar opens AppHistoryDate. |
| AppTransaction | Transaction | `TransactionDetailScreen` | Category chip opens the category sheet. "More details" is a plain row, not a card. |
| AppAccounts | Accounts (tab) | `AccountsScreen` | Swipe between account cards; next card peeks in. History and Bank SMS buttons (hide Bank SMS until it has a screen). |
| AppAccountDetail | Account detail | `BankAnalyticsScreen` | |
| AppAdd | Add transaction | `AddTransactionScreen` | Category chips come from the real category list. |

### 4 · Insights
| File | Screen | Replaces | Notes |
|---|---|---|---|
| AppAnalytics | Analytics (tab), top | `AnalyticsScreen` | Month total; money in, left over, change vs last month; 6 months in vs out; top categories with share. |
| AppAnalyticsMore | Analytics, scrolled | | Every day this month, where you pay most, spent from each account, uncategorised → Review. |
| AppCategory | Category detail | `CategoryDetailScreen` | Rows open Transaction. |

### 5 · Settings
| File | Screen | Replaces | Notes |
|---|---|---|---|
| AppSettings | Settings | `SettingsScreen` | Back button, Change PIN and Export transactions rows. |
| AppEditAccount, AppPrivacy, AppNotifications, AppDeleteAccount | | existing screens | Notifications keeps one switch. |

### 6 · PIN and signing back in
AppPinConfirm (mismatch), AppUnlock, AppUnlockLocked (keypad disabled, countdown), AppForgotPin, AppChangePin, AppSignInAgain (session expired, with an example error). Replace `PinSetupScreen`, `PinLockScreen`, `ChangePinScreen` and the non-onboarding `SupabaseAuthScreen`.

### 7 · Loading, empty and error states
AppHistoryLoading, AppHistoryEmpty, AppAccountsEmpty, AppAnalyticsEmpty, AppTransactionNoReceipt (no receipt button, original SMS open), AppHomeOlder, AppAddError.

### 8 · Pickers and export
AppHistoryPeriod (period sheet), AppHistoryDate (date range dialog), AppExport (export sheet from Settings: period and accounts, then the system save dialog).

### 9 · Icon, splash and notifications
AppIcon (launcher icon in every mask shape, themed version), AppSplash, AppSystemMessages (notification copy and the toast style).

### 10 · Small phones
AppSmallHome, AppSmallHistory, AppSmallReview, AppSmallTransaction at 360×780 with long names. Every screen must hold up like these.

### 11 · Ethiopian calendar
AppHistoryEC, AppHistoryDateEC, AppTransactionEC. Every date in the app follows the calendar setting, not just Review and Transaction as today.

### 12 · More states
AppAccountDetailEmpty, AppCategoryEmpty, AppAnalyticsLoading, AppSignInBusy, AppDeleteBusy, AppReviewTransfer.

## Navigation

- Bottom bar (icons only, dot under the active one) on Home, Accounts, History and Analytics only. Every other screen has a back button.
- Settings is reached from the gear on Home only. Remove the non-tappable profile icons.
- Home: Review → Review; + → Add; a row → Transaction; See all → History.
- Analytics: category → Category detail; "N need a category" → Review. The "categorize today" notification also opens Review.

## Behaviour changes (the only logic to touch)

1. **First-launch order:** Welcome → Allow SMS → (SMS denied) → Choose sources → Sign in → Your money at a glance → Create a PIN → Home. Move the auth gate after the SMS steps. Remove "What do you track?".
2. **Choose sources switches** save to the existing disabled-senders prefs and the scanner honours them.
3. **Categorize Today merges into Review.** Every entry point opens Review.
4. **Summary screen** gets its numbers from a view model, with real month labels.
5. **SMS denied** gets its screen and an "Open settings" path.
6. **Export** moves to Settings and gains period and account filters.
7. **Own-account suggestion** picks "Own accounts" (excluded from totals), not "Transfer".
8. **Calendar setting** applies to every date label.

Anything in the mockups without backing logic is hidden, not built. List it in your summary.

## Copy fixes

- "Finance App" in the foreground notification and `finance_transactions_…` as the CSV name → Kash.
- No "Supabase" in user-facing text.
- Fix: "with out", "Todays Spending", "This Months spending", "Todays spend", "transactions Tracked", "is your Biggest merchant".
- "Last 18 months" → "Found on this phone".
- Scan failure: "Couldn't read your SMS. Pull down on Home to try again."
- Notification wording: see AppSystemMessages.

## Pending assets

- Official Sign in with Google button / G mark (placeholder drawn).
- Bank logos (CBE, Telebirr, BoA…). Until then `BankBadge`: coloured square with initials.
