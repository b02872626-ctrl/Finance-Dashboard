One transaction in a list: an arrow well, the name, one line of *account · time* followed by a category pill, and the signed amount.

**You provide:** `name`, `account` ("Telebirr", "CBE", "CBE Birr"), `time`, `category` (optional, shown as a pill), `amount` (a positive number), `direction` and `href` or `onClick` to open the transaction.

| direction | Arrow | Amount | Colour |
|---|---|---|---|
| `in` | down-left | `+160.00` | `money-in` |
| `out` | up-right | `−100.00` | `money-out` |
| `transfer` | two arrows | `500.00`, no sign | `money-neutral` (lighter grey); category "Own accounts" |

**Do**
- Sit rows straight on the sheet, never inside a card. Group them under a `DayHeader`.
- Write the subline as account then time, then the category pill: "Telebirr · 12:36  [Airtime]". No pill when there is no category yet.
- Leave out whatever the screen already says: no account on an account's own page, no pill on a category's own page.
- Let long names truncate; the full name is on the transaction page.

**Don't**
- Add the balance after, or a second subline. One line of detail per row.
- Use money colours on slate.
