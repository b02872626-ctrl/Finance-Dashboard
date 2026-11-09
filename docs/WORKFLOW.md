# How Kash work gets done

The one process for every change, from idea to testers' phones. It applies
to the owner and to every AI session. `RULES.md` says what must stay true;
this file says the order to do things in.

```
Linear issue → branch from main → change + tests → PR → CI green → merge
   → (app change?) scripts/release.sh prepare → merge → release.sh verify
   → message testers
```

## 1. Plan the change

- Every change starts as a Linear issue (team NAH) with a short list of
  "done when" points. Quick fixes found mid-work get an issue too, so
  nothing lives only in chat.
- One issue = one branch = one pull request. If it can't be merged the same
  day, split it.
- Say up front which part it touches, because each has its own checks:

  | Part | Extra checks before merging |
  |---|---|
  | App (`app/`) | Unit tests; screens checked at 360dp |
  | Parsers (`parsing/`) | A test with a real, masked SMS for every new shape |
  | Website / dashboards (`Web/`) | Open the page and click through it |
  | Database (`supabase/`) | New SQL file → staging → `isolation_test.py` → live; `docs/SECURITY.md` updated |

## 2. Make the change

1. Start from the latest main:
   ```bash
   git fetch origin
   git switch -c claude/nah-29-short-name origin/main
   ```
   Name branches `claude/nah-<issue>-<few-words>` (or `claude/<few-words>`
   when there's no issue).
2. Move the Linear issue to **In Progress**.
3. Make the change in small steps. Commit each step that works.
4. Write down, in plain English, how to test it on the phone. It goes in the
   PR and in `docs/PROGRESS.md`.

## 3. Commit and merge

- **Commit message:** one line saying what changed, in plain words, starting
  with the area: `Parsing: read Hibret Bank debits`,
  `Settings: confirm before logging out`, `Docs: work process`. Add a blank
  line and a short "why" when it isn't obvious. Mention the issue
  (`NAH-29`) so Linear links it.
- **Before pushing:** catch up with main and rerun the tests.
  ```bash
  git fetch origin && git rebase origin/main
  ./gradlew testDebugUnitTest
  git push -u origin HEAD
  ```
  The pre-push hook refuses a branch that is behind main (turn it on once
  per clone: `git config core.hooksPath .githooks`).
- **Pull request:** title like the commit; body says what changed, how to
  test it, and the Linear issue. In the same PR: move the item to Done in
  `docs/PROGRESS.md`, and add a dated entry to `docs/DECISIONS.md` if the
  change alters how the app is built or behaves.
- **Merge** when CI is green (`gh pr merge --merge --delete-branch`), then
  mark the Linear issue Done. Never force-push main.

## 4. Deploy

Merging to main is the deploy for everything except the app:

- **Website and dashboards:** Cloudflare Pages publishes `Web/` from main a
  minute or two after the merge. Open the changed page on
  `kash-140.pages.dev` and check it (signed in, for the dashboards).
- **Database:** SQL is run by hand, staging first, before the app or page
  that needs it is merged. Never change the schema only in the dashboard.
- **App:** merged changes wait on main until the next tester release (§5).
  Several merged changes can go out in one release.

## 5. Release to testers

**When:** one tester release a week, on the same day, with whatever was
merged. A crash or a broken core flow (import, history, sign-in) gets a
release the same day. No release in a week with nothing new.

**How (two commands):**

1. From the latest main, with nothing uncommitted:
   ```bash
   git fetch origin && git switch --detach origin/main
   scripts/release.sh prepare "Reads Hibret Bank SMS" "Log out asks first"
   ```
   Each quoted line is one release note, written for testers (what they'll
   notice, not how it was built). The script bumps the version, runs the
   tests, builds the signed APK and checks it's signed with the Kash key,
   swaps it into `Web/apk`, updates `version.json` (with the notes), the
   download page, `RULES.md` and `docs/PROGRESS.md`, then pushes
   `claude/release-<version>` and opens the PR. If anything fails it undoes
   everything and nothing is committed. Practice run: `DRY_RUN=1` in front.
2. When CI is green, merge the PR, then:
   ```bash
   scripts/release.sh verify
   ```
   It waits for the live site to serve the new version and APK, then prints
   the message for testers.

**Tell testers** (the same notes everywhere, written once):
- Installed apps see the new `version.json` within 12 hours and show
  "Kash x.y.z is available" (tester APK only, never Play installs).
- Paste the printed message into the testers' Telegram group.

**After the release:**
- Install it on your own phone over the old version (that's the real
  "does it update" test) and open Home, History and Review.
- Check Sentry for new crashes and PostHog for app-opened events over the
  next day.

**If a release is bad:** Android can't go back to an older version without
uninstalling, so fix forward: revert the bad change on main
(`git revert <commit>` in a PR), then release again. The new version
number is higher, so everyone gets it.

## 6. Later: Google Play

The Play build uses the same main and the same version numbers. When Play
is live, `release.sh` gains a step that builds the app bundle (`.aab`) for
the closed test track; the tester APK and the download page carry on as
they are.

## End of every session (AI or owner)

1. `docs/PROGRESS.md` updated (Done with the date, next up, blocked).
2. `docs/DECISIONS.md` entry for any decision that changes the app.
3. The Linear issue moved to the right status.
4. A plain-English note: what changed and how to test it on the phone.
