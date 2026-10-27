A search input with a leading magnifier, for filtering transactions.

**You provide:** `placeholder` (what can be searched: "Search name, category, reference"), `tone`, and `value` + `onChange`.

- In History it sits in the slate hero (`tone="onSlate"`) with the filter chips under it.
- In Account detail it sits at the top of the sheet (`tone="light"`), scoped to that account: "Search this account".
- Search filters the list below as the user types; there is no search button.
