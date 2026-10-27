Kash turns the bank and mobile-money SMS already on an Android phone into a transaction history, balances and a 30-second daily review. Users are young professionals and students in Addis Ababa with two to four accounts (usually CBE and Telebirr), on mid-range phones 360–412dp wide, often offline. The interface is calm and plain: **slate frames the screen, a light sheet holds what you read and act on, and sand marks the one thing to do next.**

## Content fundamentals

- **Plain, short, second person.** Speak to the user as "you"; Kash refers to itself by name, never "we" on screen. "Kash only sees SMS already on this phone." — not "We are only able to access…".
- **Sentence case everywhere**: titles, buttons, chips. "Allow SMS access", "See all", "Not now".
- **Verbs on buttons, nouns on chips.** Buttons say what happens: "Import 1,253 transactions", "Confirm as Family". Chips name a thing: "Family", "Transfers", "This month".
- **One line of detail per row.** A transaction row shows the name, then *category · time*, then the amount. Never the bank, the balance after and the category at once; the rest lives on the transaction page.
- **Numbers, not adjectives.** "Found 1,284 transactions", "3 times this month", "Up 12% on last month". No "Great job!" or "Awesome".
- **No emoji, no exclamation marks.**
- **Money:** amounts with two decimals and thousands separators (`1,250.00`); currency as `ETB` after the figure, or `birr` in running text. Signs are real minus `−` and plus `+`.
- **Dates fit both calendars.** Every date label must fit Gregorian ("Mon 26 Oct") and Ethiopian ("ሰኞ 16 ጥቅምት") in the same space. Prefer "Today", "Yesterday" and a time over full dates.

## Visual foundations

### The screen structure

- **App screens are two layers.** The top is `slate`; the sheet below is `surface` with `radius-sheet` top corners and a `handle`. Put the one key figure and its controls on slate (balance, the account card, the amount, search and filters, review progress). Put lists, details and forms on the sheet.
- **Onboarding is light.** `surface` page, `slate` primary button, a `StepBar` in the header. Welcome is the exception: all `slate`, with a `sand` button.
- **Lists are never in cards.** Transaction rows sit straight on `surface`, divided by `line`, grouped by a `DayHeader`. They scroll; the tab bar floats over them. Use cards (`surface-raised`, `line` border, `radius-card`) for short fixed sets: a list of facts, a key–value block, a set of toggles.
- **Details live behind a fold.** A transaction shows four facts; reference, VAT and the original SMS sit under "More details".

### Colour

- `slate` and `surface` do almost all the work. `sand` appears **once per screen**, on the single next action or selection; never decorate with it.
- Money colours (`money-in`, `money-out`) appear **only on surface**, always with a sign and an arrow, never alone. On slate, amounts are `on-slate` white.
- Own-account transfers use `money-neutral`, no sign, and are labelled "Own accounts". They are not counted in totals.
- Text on slate is `on-slate`, labels `on-slate-muted`. Text on surface is `ink`, secondary `ink-muted`. `ink-faint` is for inactive tab icons only.
- No gradients. No drop shadows: separate with `line` borders and the slate/surface step.

### Type

- One family: Figtree (`--font-sans`), loaded from Google Fonts at 400, 500, 600 and 700.
- Each screen has **one** figure in `figure-xl` or `figure-l`; set its decimals and currency in `figure-unit` in the muted colour of its ground.
- Use `title-l` for onboarding headlines and break lines by hand so no word is left alone ("Let Kash read / your bank SMS").
- Tabular numbers (`font-variant-numeric: tabular-nums`) on every amount.

### Space, size and shape

- Screens pad `inset-app` (22px) at the sides; onboarding pads `inset-onboarding` (24px). Rows pad `row-y` (13px) top and bottom.
- Soft-square corners: `radius-card` 20, `radius-control` 16, `radius-field` 14, `radius-chip` 12, `radius-sheet` 28 for the sheet's top corners. Only switches and the sheet handle are fully round (`radius-pill`).
- Every tappable thing is at least `icon-button` (44px). Full-width buttons are `button-h` (56px); buttons inside the slate hero are `button-h-compact` (52px).

### States

- **Focus:** a 2px solid ring, offset 2px — `focus` on surface, `focus-on-slate` on slate.
- **Pressed:** 88% opacity. **Disabled:** 40% opacity, no pointer.
- **Selected chip:** `slate` fill with `on-slate` text on surface; `sand` fill with `slate-ink` text on slate.
- **Switch on:** `slate` track; off: `line-strong`.

### Navigation

- The tab bar has four icon-only tabs: Home, Accounts, History, Analytics. Each has an `aria-label`; the active tab is `ink` with a 5px dot under it, the rest `ink-faint`.
- Sub-pages have a back `IconButton` top-left and a `title-xs` centred title.

## Brand

- The app icon is three rounded bars on slate, the first in sand (`Kash.AppIcon`). It is the launcher icon, the splash mark (bars only, centred on slate) and the small notification icon.
- The Analytics tab icon (`chart`) is the same three bars as a stroke icon.

## States and patterns

- **Loading:** grey `Skeleton` shapes in the layout that is coming, never a blank screen or a lone spinner. On slate, skeletons are a lighter ghost tone.
- **Busy actions:** the pressed button shows a `Spinner` and a verb in progress ("Creating your account…"); other actions on the screen are disabled.
- **Empty:** `EmptyState` with an icon in a well, a plain title, one line of why, and at most one secondary button.
- **Errors in forms:** the field's border and a caption under it in money-out, with `aria-invalid` and the message linked to the field. Errors say what to do ("Add who you paid so you can find it later").
- **Choosing something:** a `Sheet` over a slate-ink backdrop. Confirming something important: a `Dialog`.
- **After an action:** a `Snackbar`, with Undo when the action can be taken back.
- **Dates:** every date follows the calendar setting. Ethiopian dates read "Tikimt 17" or "Saturday, Meskerem 30"; a full date is "Tikimt 16, 2019 · 18:40".
- **Small phones:** every screen works at 360 wide. Names truncate on one line in rows, wrap to two lines at most in headers, and button labels never wrap.
- **Light only for launch.** No dark theme yet.

## Iconography

- Stroke icons on a 24px grid, 2px stroke (2.2 for row arrows), round caps and joins, `currentColor`. They come from `Kash.Icon` by name: back, chevronRight, chevronDown, plus, settings, card, history, chart, home, export, calendar, search, share, sms, lock, in, out, transfer, external, pause, eye, check, backspace.
- Direction is drawn, not just coloured: `in` (arrow down-left), `out` (arrow up-right), `transfer` (two opposite arrows).
- No emoji, no filled icons, no illustrations.
- **Bank badges are placeholders.** `BankBadge` shows a coloured square with initials (`bank-cbe`, `bank-telebirr`, `bank-boa`) until the official bank marks are supplied. Do not draw bank logos.
- **Sign in with Google** must use Google's official button assets and colours (white, `#747775` border, Roboto Medium). The G mark is not in this system; place the official file from Google's branding guidelines.

## Accessibility

- Every text pair here meets 4.5:1 and every icon or control border that carries meaning meets 3:1 on the ground its token note names.
- Money in and out are told apart by sign and arrow as well as colour.
- Icon-only buttons and tabs carry an `aria-label`. Step bars and progress carry a text label ("Step 2 of 3").

## Consuming this system

- Namespace / bundle global: `window.Kash` (from `components/bundle.js`, a classic script that needs React 18 on the page).
- Load order on a page or Design canvas: `tokens.css`, then `components/bundle.css`, then `components/bundle.js`.
- Mount components by name (`Kash.Button`, `Kash.TransactionRow`, `Kash.TabBar`, `Kash.StepBar`, `Kash.Icon`…); each component's README lists its props. `Screen` takes nodes, so on a canvas build the screen frame in markup with the `k-screen`, `k-screen__hero`, `k-screen__sheet`, `k-screen__handle` and `k-screen__body` classes and mount the other components inside it.
- Plain markup between components uses the `var(--…)` tokens and the type classes in `tokens.css` (`title-m`, `figure-xl`, `caption`…).
