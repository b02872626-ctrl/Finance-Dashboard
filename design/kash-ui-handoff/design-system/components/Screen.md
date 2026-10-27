The two-layer app screen: a slate hero on top and a light sheet that rises over it.

Use it for every in-app screen (Home, Accounts, Account detail, History, Transaction, Review). Onboarding screens do not use it.

**You provide:** `hero` (header row, the one key figure, its controls), `children` (the sheet's content), `sheetTop` (where the sheet starts) and, on tab screens, a `tabBar`.

| Screen | sheetTop |
|---|---|
| Home | 404 |
| Accounts | 352 |
| Transaction | 374 |
| History | 262 |
| Account detail | 248 |
| Review | 168 |

**Do**
- Put exactly one figure on slate, in `figure-xl` or `figure-l`.
- Put lists straight in the sheet (no card around them).
- Pass a `TabBar` only on the four tab screens; sub-pages have a back `IconButton` instead.

**Don't**
- Put money colours on slate: amounts there are white.
- Add a second accent: sand appears once per screen.
