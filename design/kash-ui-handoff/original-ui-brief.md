# Finance Lore — UI redesign brief: every page, section and button

**What the app is.** An Android app for people in Ethiopia. It reads SMS from banks and mobile-money services (CBE, Telebirr, CBE Birr, Bank of Abyssinia, Awash, Dashen, Berhan) and turns them into a transaction history, balances, spending insights and a daily "review and categorize" habit. Nobody types in expenses; the SMS do the work. Currency is ETB. There is also a web dashboard, but this brief covers the **Android app only**.

**Who uses it.** Young professionals and students in Addis Ababa with 2–4 accounts (typically CBE + Telebirr). Mid-range Android phones, often slow or no internet, 360–412 dp wide screens.

**Version.** 1.0.0, launching Friday Oct 30, 2026. This inventory matches the shipped code.

---

## Current visual language (for reference — free to change)

| Token | Value | Used for |
|---|---|---|
| Page background | `#0E1614` (near-black green) | All screens; app is dark-only |
| Card | `#231E1A` (warm dark brown) | Cards, sheets, bottom bar |
| Recessed surface | `#15110F` | Search fields, icon wells |
| Headline / primary text | `#7FE3CB` (mint) | Titles, most text, selected states |
| Accent | `#FB5D53` (coral) | Primary buttons, money out, destructive |
| Income | `#4CD495` (green) | Money in |
| Muted text | `#B7B3AC` / `#7C7872` | Secondary text |
| Font | Google Sans | Everything |
| Shapes | Pills (fully rounded) buttons and chips; 18–28 dp radius cards | |
| Categories | Each category has its own colour pill (e.g. "Coffee and refreshments" orange, "Bills" olive, "Own accounts" green) | |

---

## Navigation map

```
Sign-in ─► Onboarding (5 steps) ─► Main app
                                     │
  Bottom bar (4 tabs):  Home │ Accounts │ History │ Analytics
                                     │
  From Home:      + (Add transaction) · ⚙ (Settings) · Daily Review · bank card → Account detail · transaction → Transaction detail
  From Accounts:  account card → Account detail
  From History:   transaction → Transaction detail
  From Analytics: category → Category detail · "uncategorized" → Categorize Today
  From Settings:  Edit account · Privacy & Data · Notifications · Connected Banks · Support (email)
  From a notification: "Categorize Today"
```

The bottom bar shows on: Home, Accounts, History, Analytics, Categorize Today. It is hidden on every other screen (they have a back button).

---

## 1. Sign-in / Create account

**Purpose:** Create or sign into the cloud account (required before anything else).
**When:** First launch, after logging out, or when a session expires.

| Element | Type | What it does |
|---|---|---|
| Top bar title "Step 1 of 3" (first launch) / "Cloud Sign-In" | Label | — |
| "← Back" | Icon button | Only shown when opened from Settings (not on first launch) |
| Eyebrow "1. SIGN UP OR SIGN IN" + title "Create your cloud space" | Text | Title switches to "You're signed in" when signed in |
| Email | Text field | |
| Password | Text field (hidden) | |
| **Sign in** | Primary button | Shows "Signing in…" while loading |
| **Sign in with Google** | Secondary button | Opens Google account picker |
| "Need an account? **Create one**" | Text link | Switches the form to sign-up mode (same fields, "Create account" button) |
| Sign out | Button | Only when already signed in |

**States to design:** loading; error message under the form (e.g. "Wrong email or password.", "No internet connection…", "An account with this email already exists…", "You were signed out because your session expired. Sign in again to keep syncing."); account-created confirmation ("Check your email to confirm, then sign in").

**Known issues to rethink:** "Step 1 of 3" doesn't match the 5 onboarding steps that follow. This screen comes *before* the Welcome screen, so users sign in before being told what the app does.

---

## 2. Onboarding (5 steps, after sign-in, first launch only)

### 2.1 Welcome
| Element | Type | What it does |
|---|---|---|
| Headline "Tracking your finance shouldn't feel like homework" | Text | |
| Sub "We help you track your finances without asking you to write down your expenses daily" | Text | |
| Card "Private & Secure — Only bank messages are read. Your data is never sold or shared." | Info card | |
| **Get started** | Primary button | → step 2 |
| **Sign in** | Secondary button | Skips the rest of onboarding (user is already signed in) |
| Footer "DESIGNED FOR PRIVACY · BANK SMS ONLY" | Caption | |

### 2.2 Allow SMS access
| Element | Type | What it does |
|---|---|---|
| Title "Allow SMS access" | Text | |
| Body "We read messages from banks and mobile money only, and sync your transactions to your account so they're on the web too." | Text | |
| 3 benefit rows: "detect transaction messages", "auto-import spending", "reduce manual entry" | List | |
| **Allow Access** | Primary button | Opens the Android SMS permission dialog; auto-advances when granted |

**States:** permission denied (currently nothing happens — needs a design: explain + retry / open settings).

### 2.3 Ready to import
| Element | Type | What it does |
|---|---|---|
| Title "Ready to import" + "We've successfully analyzed your SMS records…" | Text | |
| Big number "TRANSACTIONS DETECTED" + "Last 18 months" | Stat | |
| "Detected Sources" — "N ACTIVE" | Section header | |
| One row per detected bank (logo, name, masked account no.) with an on/off toggle | Toggle list | Toggles are visual only today |
| **Import transactions →** | Primary button | → step 4 |
| **Review first** | Secondary button | → step 4 (same as Import today) |

**States:** no banks found yet (empty list).

### 2.4 What do you track?
| Element | Type | What it does |
|---|---|---|
| Title "What do you track?" + "Select the categories most relevant to your daily spending." | Text | |
| "Select Categories — N SELECTED" | Header | |
| 4 category rows with toggles: Food (Dining, groceries, snacks), Transport (Commute, fuel, repairs), Bills (Rent, utilities, taxes), Airtime (Data, call credit, sub) | Toggle list | Not saved today — decide whether this step stays |
| **Continue →** | Primary button | → step 5 |

### 2.5 You're all set
| Element | Type | What it does |
|---|---|---|
| Title "You're all set!" + subtitle (changes if nothing imported) | Text | |
| Card "Activity overview — N Transactions imported" | Stat card | Only if data |
| Card "Frequent Merchant — X is your biggest merchant" | Stat card | Only if data |
| Card "Monthly Lifestyle" — 6-month bar chart + "You've spent N on average every month" | Chart card | Only if data |
| Card "Nothing imported yet — …you can also add a transaction by hand with the + button" | Empty card | Only if no data |
| **Go to dashboard →** | Primary button | Finishes onboarding → Home |

---

## 3. Home (tab 1)

**Purpose:** At-a-glance: total money, this week's spending, what needs review, accounts, recent activity.

Sections top → bottom:

| # | Section / element | Type | What it does |
|---|---|---|---|
| 1 | Greeting "Good morning/afternoon/evening" + name (or "Welcome") | Header | |
| 1a | **+** (coral square) | Icon button | → Add transaction |
| 1b | **⚙** | Icon button | → Settings |
| 2 | **Total Balance card** (coral): "Total Balance", "ETB ••••••" (hidden by default), "All accounts" | Hero card | |
| 2a | 👁 eye | Icon toggle | Show / hide the balance (resets to hidden on each app launch) |
| 2b | Weekly bar chart S M T W T F S — spending per day this week; today highlighted; future days empty | Chart | |
| 3 | **Daily Review card** — 4 variants: | Card | |
| | • "Review today's transactions — N transactions need confirmation" + → | CTA | → Daily Review (today) |
| | • "Review older transactions — N still need a category" + → | CTA | → Daily Review (all) |
| | • "All caught up — N reviewed today" + ✓ | Status | — |
| | • "Listening for new transactions — We'll let you know when an SMS comes in" | Status | — |
| 4 | **Banks** header + "See all" (only if >2 banks) | Section | See all → Accounts tab |
| 4a | Up to 2 bank cards: bank name + balance "616.48 ETB" | Cards | → that Account detail |
| 5 | **History** block: "Transactions / History" header (tappable), search field "Search by name, bank, date…", chips ALL · CREDITED · DEBIT · UNKNOWN | Section | Header/search → History tab; chip → History tab pre-filtered |
| 6 | Day groups (3 most recent days): "Today / Yesterday / Monday, 24 Oct", "N transactions", "Net −737.00", then transaction rows | List | Row → Transaction detail |
| 7 | **See more…** | Pill button | → History tab |

**States:** loading (spinner); no accounts yet (Banks section hidden); no transactions ("No transactions yet. Once messages are imported, your activity will appear here."); pull-to-refresh (re-scans SMS).

---

## 4. Accounts (tab 2)

| Element | Type | What it does |
|---|---|---|
| Header "Sources / Accounts" + profile icon | Header | Profile icon is **not tappable** today |
| One card per account: bank logo, balance, bank name, "Account - 1****4607", "N transactions Tracked" | Card list | → Account detail |

**States:** empty ("No accounts detected yet. Import SMS messages to see them here.").

---

## 5. Account detail (from Home bank card or Accounts)

| # | Element | Type | What it does |
|---|---|---|---|
| 1 | "← Sources / Accounts" + profile icon | Header | Back arrow → previous screen; profile icon not tappable |
| 2 | Balance card: balance, logo, bank name, "Account - 1****4607", "N transactions Tracked" | Card | |
| 3 | "Recent Activities" + "Net −2,390.00" (for the current filter) | Section header | |
| 4 | Search "Search this account …" with ✕ clear | Search field | Filters by name, category, reference |
| 5 | Period chips **1W · 1M · 1Y · All time** | Chips | Filter by period; dismisses keyboard |
| 6 | Transaction rows (8 at a time) | List | → Transaction detail |
| 7 | **Load more…** | Pill button | +10 rows |

**States:** loading; no activity ("No activity recorded for this account."); no match ("No transactions match this search or period.").

---

## 6. History (tab 3)

| # | Element | Type | What it does |
|---|---|---|---|
| 1 | Greeting header + profile icon | Header | Profile icon **not tappable** |
| 2 | Card "Transactions / History" | Card | |
| 2a | 📅 Pick date | Icon button | Date picker dialog (Apply / Cancel) → shows that single day |
| 2b | ⬇ Export CSV | Icon button | System "save file" dialog → CSV of current filter; spinner while exporting |
| 2c | Search "Search by name, bank, category…" with ✕ | Search field | |
| 2d | Type chips **ALL · CREDITED · DEBIT · UNKNOWN** | Chips | |
| 2e | Period chips **1W · 1M · 1Y · All time** | Chips | Custom date from 📅 un-highlights these |
| 3 | Day groups: "Today", "N transactions", "Net +/−", rows | List | Row → Transaction detail |
| 4 | **Load more…** | Pill button | Only when no search/period filter |

**States:** empty/no match ("No transactions match this view."); pull-to-refresh.

---

## 7. Transaction row (shared component — Home, History, Account detail, Category detail)

Icon well (↗ coral = money out, ↙ green = money in) · counterparty name (1 line, ellipsis) · bank name · category pill (if set) · amount "− 100.00" / "+ 160.00" · "bal 616.48" (balance after).
Must handle: very long names ("Telecom package: Daily Internet Pack…"), missing counterparty (falls back to sender), missing balance, no category, "Own accounts" transfers (neutral — excluded from totals but shown).

---

## 8. Transaction detail

| # | Element | Type | What it does |
|---|---|---|---|
| 1 | "← Transaction Details" | Header | Back |
| 2 | Hero card: category pill + "Edit ›", amount "100.00 ETB", bank name, masked account | Card (tappable) | Opens category picker sheet |
| 3 | Details card rows (only those present): Receiver/Sender, Your account, Reference Num, Date & time, Balance after expense/income, Service charge, VAT, Disaster fund, Total charge | Key–value list | |
| 4 | **Open receipt** (coral) + **Share** (round icon) — or full-width **Share transaction** when no receipt | Buttons | Receipt → bank's receipt page in browser; Share → Android share sheet (text excludes balance) |
| 5 | **Show original SMS / Hide original SMS** | Toggle button | Reveals the raw SMS text |
| 6 | Category picker sheet: "Category", list of categories, add new | Bottom sheet | Sets category |

---

## 9. Daily Review (from Home card or notification)

**Purpose:** The core habit — swipe through today's (or older) uncategorized transactions and confirm a category for each, like a card stack.

| # | Element | Type | What it does |
|---|---|---|---|
| 1 | ‹ back + "Daily Review" + date "Monday · Oct 26" | Header | |
| 2 | Segmented progress bar (one segment per transaction) + "3 of 4 reviewed" + "1 left · ~6s" | Progress | |
| 3 | **Review card**: source pill (Telebirr), "SMS · 12:36", INCOME/EXPENSE chip, big amount "+160.00 ETB", "From/To" + name | Swipeable card | Swipe right = confirm, swipe left = skip; long-press = open edit sheet |
| 3a | Hint chips on some cards: "Looks like a transfer", "Low-confidence parse" | Badges | |
| 3b | Suggestion box: initial tile + "Likely" + category name + **Change** | Row | Change → category sheet |
| 3c | Confidence dots + "55% confidence" | Indicator | |
| 3d | ▾ Show raw SMS | Disclosure | |
| 4 | Quick category chips row: Food · Transport · Bills · Shopping · Transfer … | Chips (scroll) | Pick a category directly |
| 5 | **⏭ Skip** / **✓ Confirm** (or **Save** when there's no suggestion) | Buttons | |
| 6 | Snackbar after each action with **Undo** | Snackbar | |
| 7 | Dialog "Found N transactions with this name… 'Apply to all' will set every one of them… " — **Apply to all** / **Just this one** | Dialog | |

Sheets opened from here:
- **Category sheet** ("Categorize"): 🔍 "Search categories", sections Recent / Common / Lifestyle / Money in/out / custom, **+** new category, toggle **"Apply to similar transactions — Updates rule for <name>"**.
- **Edit transaction sheet**: Amount, Merchant, Source, Time, Category, Raw SMS; buttons **Not a transaction** and **Change category**; ✕ close.
- **New category dialog**: field "e.g. Subscriptions", **Create** / **Cancel**.

**States:** loading ("Loading review queue…"); done ("All caught up — Every transaction reviewed for today.").

---

## 10. Categorize Today (from Analytics "uncategorized" row, notification, Category detail "+ Add Transactions")

| Element | Type | What it does |
|---|---|---|
| "Categorize Today" + Back | Header | |
| "Hey <name>" greeting | Text | |
| List of today's uncategorized transactions, each with a category selector and **Open details** | List | |
| Dialog "Categorize repeated customer?" — **Categorize all** / **Only this one** | Dialog | |

**States:** "You're all caught up for today." This screen overlaps heavily with Daily Review — consider merging.

---

## 11. Analytics (tab 4)

| # | Element | Type | What it does |
|---|---|---|---|
| 1 | Greeting "Welcome" + profile icon | Header | Profile icon **not tappable** |
| 2 | Card "Todays Spending — ETB 897.00 — 5 transactions today" | Stat card | |
| 3 | Card "This Months spending — ETB 90,616.24" + month calendar heatmap (S–S × weeks, darker = more spent) + "Top category: Loan" | Heatmap card | |
| 4 | Card "Spending — AVG 3,485.24 per day" + 7-day bar chart | Chart card | |
| 5 | "Total spend by Category — This month": donut/bars with category list | Breakdown | Category → Category detail |
| 6 | Row "ⓘ N uncategorized transactions ›" | Row | → Categorize Today |

**States:** no categorized data ("Categorise transactions to see the breakdown").

---

## 12. Category detail (from Analytics)

| # | Element | Type | What it does |
|---|---|---|---|
| 1 | "Back" + "<Category> Spending" + "30-Day View" + Calendar icon | Header | |
| 2 | "Total monthly expenditure — N ETB — ↑ X% of total spend" | Stat | |
| 3 | "Todays spend" / "Average daily spend" | Stat pair | |
| 4 | "Recent History" day groups with Net + rows | List | Row → Transaction detail |
| 5 | **+ Add Transactions** | Button | → Categorize Today |

**States:** "No activity recorded for this category yet."

---

## 13. Add transaction (Home "+")

| Element | Type | What it does |
|---|---|---|
| Back + "Add transaction" | Header | |
| Type toggle **Expense · Income · Transfer** | Segmented control | |
| Amount (big number input, ETB) | Number field | |
| Who paid / who got paid | Text field | Required |
| Account / Bank — placeholder "Cash, CBE, Telebirr…" | Text field | Free text |
| Date 📅 | Date picker | Defaults to now |
| Category (optional) | Category selector | |
| Note — "Optional" | Text field | |
| **Save transaction** ("Saving…") | Primary button | Disabled until valid; closes on success |

**States:** validation error message; saving.

---

## 14. Settings (Home ⚙)

| # | Element | Type | What it does |
|---|---|---|---|
| 1 | Profile card: ✏ edit (top-left), avatar, name (or "Your Account"), email | Card | ✏ → Edit account |
| 2 | "Preferences" | Section header | |
| 3 | **Privacy & Data** — "How your data is handled" | Row › | → Privacy & Data |
| 4 | **Notification Preferences** — "Transaction alerts" | Row › | → Notifications |
| 5 | **Connected Banks** — "N External Accounts Linked" | Row › | → Accounts tab |
| 6 | **Support** — "Email us — we usually reply within a day" | Row › | Opens email app with app version + phone model pre-filled |
| 7 | **Calendar** — "Gregorian (Jan, Feb…)" with **GR / ET** segmented toggle | Toggle | Switches all dates between Gregorian and Ethiopian calendar |
| 8 | **Log out** | Pill button | Signs out → Sign-in |
| 9 | **Delete account** (small coral text) | Link | Confirmation dialog → deletes account + all data |

**Delete dialog:** "Delete your account?" + explanation ("permanently deletes … from our servers, and clears them from this phone. Your bank SMS messages are not touched. This can't be undone.") — **Cancel** / **Delete permanently** (spinner while deleting; error toast if offline).

### 14.1 Edit account
Back · avatar (no photo upload) · **NAME** field · **EMAIL ADDRESS** (read-only) · **Save Changes** (disabled until the name changes).

### 14.2 Privacy & Data
Compact profile header · Back · "How your data is handled" with 4 facts: *Only bank messages* · *Synced to your account* · *Who can see it* (incl. "the developer can access synced data…") · *On this phone* · **Read the privacy policy** (opens web page) · **Delete account** link (same dialog).

### 14.3 Notifications
Compact profile header · Back · "Notifications" · one toggle **Transaction alerts** ("A reminder to review new transactions, and updates when syncing is waiting for an internet connection.") · note "You can also turn Finance Lore notifications off in your phone's settings."

---

## 15. System surfaces (also worth designing)

| Surface | Content |
|---|---|
| Launcher icon | Current: new icon (adaptive) |
| Notification: categorize | "Categorize today's transactions" → opens Categorize Today |
| Notification: offline | "Cloud sync pending" — waiting for internet |
| Notification: synced | "Cloud sync complete" — N transactions synced |
| Foreground-service notification | "Scanning SMS history…" → "Done! Found N new transactions." |
| Toasts | "Signed in", "Exported N transactions", "Couldn't open receipt link", "Your account was deleted" |

---

## Shared components inventory

- **Bottom bar**: 4 icon-only tabs (Home, Accounts, History, Analytics), floating rounded bar, active tab in mint.
- **Pill buttons**: coral primary, dark secondary, outline.
- **Chips**: type filters, period filters, quick categories.
- **Category pill**: coloured by category; bold variant on Transaction detail.
- **Search field**: recessed, coral search icon, ✕ clear.
- **Bottom sheets**: category picker, edit transaction.
- **Dialogs**: date picker, new category, apply-to-similar, delete account.
- **Day group header**: "Today / Yesterday / Weekday, d MMM", "N transactions", "Net ±".
- **Cards**: hero balance (coral), stat cards, info cards.
- **Empty states / loading spinners** (listed per screen above).

---

## Things the redesign should fix or decide

1. **Order of first launch**: sign-in comes before the Welcome pitch; step labels don't match ("Step 1 of 3" vs 5 onboarding steps).
2. **SMS permission denied** has no designed state.
3. **Onboarding toggles do nothing** (source toggles in 2.3, category toggles in 2.4; "Import" and "Review first" do the same thing). Keep and make real, or remove.
4. **Profile icons** in Accounts / History / Analytics / Account detail headers aren't tappable — make them open Settings, or remove.
5. **Daily Review vs Categorize Today** overlap — likely one flow.
6. **Home "History" block** duplicates the History tab's header and chips.
7. **Own-account transfers** (category "Own accounts") should look neutral in lists (not red/green spending) since they're excluded from totals.
8. **Balance privacy**: balance hidden by default on Home; account cards and lists still show balances.
9. **Long counterparty names** and masked numbers (e.g. "bekelu beyene (2519****3121)") must fit 360 dp widths.
10. **Offline-first**: the app often runs without internet; avoid designs that depend on network to render.
11. **Ethiopian calendar** toggle changes every date label — dates must fit both formats.
12. Dark mode only today; a light theme would be new scope.

## Not in v1 (don't design unless planned)
Loans page, Goals page, budgets — these exist on the web dashboard only.
