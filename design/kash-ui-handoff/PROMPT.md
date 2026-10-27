I want to replace the UI of this Android app (Kash) with a new design. The design handoff is in `design/kash-ui-handoff/`. Start by reading `design/kash-ui-handoff/HANDOFF.md` and `design/kash-ui-handoff/design-system/README.md`, then look at every PNG in `screens/png/`.

Rules:
- Change the UI only. Keep the existing data layer, SMS parsing, sync, auth, PIN logic, view models and business logic. The only logic changes allowed are the ones under "Behaviour changes" in HANDOFF.md.
- The PNGs are the source of truth for the look. The HTML files in `screens/html/` give exact sizes, spacing and copy; tokens are in `design-system/tokens.json`.
- No drop shadows. No text under bottom-bar icons. Sand (#E9DFC6) at most once per screen. Button labels never wrap. Light theme only.
- Don't invent data. Every number on screen comes from the app's real data; the values in the mockups are samples.

Work in this order and stop for my review after step 2:
1. Read `existing-app-inventory.md` and check it still matches the code. Confirm the mapping in the "Replaces" column of HANDOFF.md. List anything in the mockups that has no backing logic yet.
2. Build the foundation: theme (colours, Figtree font files in res/font, type scale, shapes, spacing), the 23 icons from `icons/`, the app icon from `brand/` (adaptive launcher icon, themed icon, splash, notification icon), and every component in `design-system/components/` as reusable composables with previews.
3. Rebuild the screens one group at a time, building and running after each group: (a) first launch and PIN (rows 1 and 6); (b) Home and Review (row 2); (c) History, Transaction, Accounts, Account detail, Add, pickers and export (rows 3 and 8); (d) Analytics and Category detail (row 4); (e) Settings and its pages (row 5); (f) every loading, empty, error and busy state (rows 7 and 12); (g) notification wording (row 9).
4. Apply the behaviour changes in HANDOFF.md.
5. Check every screen at 360 dp wide with long names (row 10) and with the Ethiopian calendar on (row 11).
6. Remove the old theme, colours and components once nothing uses them.

After each group, run the build and fix errors. If you can take screenshots (emulator or Compose preview screenshots), compare them with the PNGs and fix differences in spacing, size and colour. Finish with a summary: what changed, what you hid because it had no backing logic, and what still needs assets (Google sign-in button, bank logos).
