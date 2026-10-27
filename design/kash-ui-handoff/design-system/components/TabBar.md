The four-tab bar — Home, Accounts, History, Analytics — icons only, pinned to the bottom of the sheet.

**You provide:** `active` and either `hrefs` (one link per tab) or `onSelect`.

- No text labels under the icons. Each tab carries its name as an `aria-label`, and the active one gets `aria-current="page"`.
- The active tab is `ink` with a small dot under it; the others are `ink-faint`.
- Show it only on the four tab screens. Sub-pages (Account detail, Transaction, Review) drop it and use a back `IconButton`.
- Pass it to `Screen` as `tabBar` so it sits on the sheet and the list scrolls under it.
