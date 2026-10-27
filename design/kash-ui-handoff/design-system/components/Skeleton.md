Grey placeholder shapes shown while a screen loads, so the layout appears before the numbers do.

**You provide:** `width`, `height`, `radius` and `tone` (`light` on the sheet, `onSlate` in the hero). `SkeletonRow` draws one placeholder transaction row.

- Match the shapes to what will appear: rows for lists, a wide bar for the big figure, bars for charts.
- Wrap the loading area in `role="status"` with a label such as "Loading transactions".
- No shimmer: the shapes stay still.
