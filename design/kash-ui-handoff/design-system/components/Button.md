A full-width button; each screen has at most one primary or accent button.

**You provide:** the label (a verb: "Allow SMS access", "Import 1,253 transactions", "Confirm as Family"), a `variant`, and `href` or `onClick`.

| Variant | Where |
|---|---|
| `primary` | The main action on a light page or sheet: slate fill, white label. |
| `accent` | The next thing to do, on slate: sand fill, slate-ink label. Pass `badge` for a count ("Review 4"). Use `compact` inside the hero. |
| `secondary` | A second option beside primary ("Skip"), or a utility ("Open bank receipt"). |
| `quiet` / `quiet-on-slate` | The text-only exit under a primary button ("Not now", "I already have an account"). |

**Do**
- Pair a primary with at most one quiet exit below it.
- Keep every label on one line. Labels never wrap.
- In a two-button row, the secondary hugs its label and the primary takes the rest of the row.
- If both labels are long ("Not a transaction" / "Change category"), stack the buttons full width instead of squeezing them side by side.

**Don't**
- Use two primary buttons on one screen.
- Use sand on a light page: sand belongs on slate.
- Let a label break onto two lines, or shrink its text to fit.
