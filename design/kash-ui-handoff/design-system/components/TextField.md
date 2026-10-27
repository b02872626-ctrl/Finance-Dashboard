A labelled text input for forms such as sign in and add transaction.

**You provide:** `label` (always visible, above the field), `type`, a `placeholder` that shows the expected shape ("you@example.com", "At least 8 characters"), an optional `hint`, and `value` + `onChange`.

- Labels stay visible; never rely on the placeholder alone.
- Stack fields with 14px between them; put the primary button at the bottom of the screen, not under the last field.
- Show errors in the `hint` line in plain words ("Wrong email or password").
