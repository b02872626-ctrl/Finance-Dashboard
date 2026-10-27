A short list of facts, label on the left and value on the right, used on the transaction and review screens.

**You provide:** `items` — `{label, value, numeric?}` — and `card` (default true: a white card; false: plain rows inside another card, as on the review card).

- Keep it to four rows. Anything more (reference, VAT, the original SMS) goes in a "More details" fold.
- Labels are plain nouns: "To", "Account", "When", "Balance after".
- Set `numeric` on amounts and masked numbers so digits line up.
