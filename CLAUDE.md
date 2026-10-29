# FinanceApp

Android app (Kotlin) that parses Ethiopian bank SMS. Parsers live in
`app/src/main/java/com/financeapp/parsing/`. The download site is in `Web/`.

## Git workflow (read before touching git)

Several sessions work on this repo at once, each on its own `claude/*` branch.
`main` moves fast, so a branch that started a day ago is usually already behind.
A branch that is behind `main` silently rolls back everything merged since it
was cut (this once nearly shipped the pre-Kash screens over the redesign).

1. **Start from the latest `main`.** Before the first edit run
   `git fetch origin` and branch from (or rebase onto) `origin/main`. Never
   start from a local `main` without fetching first.
2. **Sync before you ship.** Before pushing, merging, building an APK or
   saying "done", run `git fetch origin && git rebase origin/main`, resolve any
   conflicts by keeping main's newer work, and rerun the tests.
   `git merge-base --is-ancestor origin/main HEAD` must succeed.
3. **Never resolve a conflict by taking the old version wholesale.** If main
   replaced a screen or file your branch also edited, re-apply your change on
   top of main's version.
4. **Releases come only from up-to-date `main`.** Version bumps and the APK in
   `Web/apk` are made after the work is on `main`, never from a side branch.
5. **Keep it small and short-lived.** One change per branch; merge it the same
   day if possible. After it lands on `main`, delete the branch (local and
   remote).
6. **Never force-push `main`** and never rewrite commits already on it.

Guards that enforce this:
- `.githooks/pre-push` refuses to push any commit that does not contain the
  current `origin/main`. Enable once per clone with
  `git config core.hooksPath .githooks`. Bypass only on purpose:
  `ALLOW_STALE_PUSH=1 git push ...`.
- `.github/workflows/up-to-date.yml` fails any pushed branch or pull request
  that is behind `main` (this also covers cloud sessions, where the local hook
  does not run).
