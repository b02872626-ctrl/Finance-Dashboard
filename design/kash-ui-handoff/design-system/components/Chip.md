A filter or category chip; one is selected at a time in a row.

**You provide:** the label (a noun: "All", "Transfers", "Family", "1M"), `selected`, `tone` and `onClick`.

- On surface (`tone="light"`), the selected chip is slate with white text: period filters, review categories.
- On slate (`tone="onSlate"`), the selected chip is sand: History's type filters. That sand is the screen's one accent.
- A trailing "…" chip opens the full list; give it `label="More categories"`.
- Rows of chips do not wrap; they clip at the edge and scroll.
