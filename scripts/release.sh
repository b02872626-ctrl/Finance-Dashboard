#!/usr/bin/env bash
# Kash tester release, in two commands. See docs/WORKFLOW.md §5.
#
#   scripts/release.sh prepare "What changed, for testers" ["Another line" ...]
#       From the latest main: bumps the version, runs the tests, builds and
#       checks the signed APK, swaps it into Web/apk, updates version.json,
#       the download page, RULES.md and PROGRESS.md, commits on
#       claude/release-<version>, pushes and opens the pull request.
#
#   scripts/release.sh verify
#       After the PR is merged: waits until the live site serves the new
#       version.json and APK, then prints the message to send testers.
#
# Options (environment):
#   VERSION=1.1.0   use this versionName instead of bumping the last number
#   DRY_RUN=1       do everything up to the commit, then undo it all (practice run)
#   NO_PUSH=1       commit but don't push or open the PR
set -euo pipefail

SITE="https://kash-140.pages.dev"
CERT_SHA256="24f79763866be2554b2de83b0652c45361823b3fc5e7af0001bd03be690f1509"
GRADLE_FILE="app/build.gradle.kts"

die() { echo "release: $*" >&2; exit 1; }
step() { printf '\n==> %s\n' "$*"; }

cd "$(git rev-parse --show-toplevel)"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
[ -x "$JAVA_HOME/bin/java" ] || die "no Java at $JAVA_HOME (brew install openjdk@17, or set JAVA_HOME)"

# Newest build-tools (apksigner, aapt2) in any of the usual SDK folders.
build_tools() {
  local sdk bt
  sdk=$(grep -E '^sdk.dir=' local.properties 2>/dev/null | cut -d= -f2- || true)
  for d in "$sdk" "${ANDROID_HOME:-}" "$HOME/Library/Android/sdk"; do
    [ -n "$d" ] || continue
    bt=$(ls -d "$d"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)
    [ -n "$bt" ] && [ -x "$bt/apksigner" ] && { echo "$bt"; return; }
  done
  die "no Android build-tools (apksigner) found; install them with Android Studio's SDK Manager"
}

current_name() { sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' "$GRADLE_FILE"; }
current_code() { sed -nE 's/^[[:space:]]*versionCode = ([0-9]+).*/\1/p' "$GRADLE_FILE"; }

tester_message() { # $1 version, then notes
  local v="$1"; shift
  echo "Kash $v is out."
  echo
  echo "What's new:"
  [ $# -gt 0 ] || set -- "Fixes and small improvements"
  for n in "$@"; do echo "• $n"; done
  echo
  echo "Update: $SITE/download"
  echo "It installs over your current Kash; your data stays."
}

on_exit() {
  local status=$?
  if [ "${COMMITTED:-1}" = "0" ]; then
    git reset --quiet --hard origin/main
    rm -f "$UNDO_APK"
    git switch --quiet --detach origin/main
    git branch --quiet -D "$UNDO_BRANCH"
    echo
    if [ "${DRY_RUN:-}" = "1" ]; then echo "DRY_RUN: all changes undone."
    else echo "release: stopped; all changes undone, nothing was committed."; fi
  fi
  return $status
}

# ---------------------------------------------------------------- prepare --
prepare() {
  [ $# -ge 1 ] || die 'give at least one line of notes: scripts/release.sh prepare "Fixed X"'
  local notes=("$@")

  step "Checking you're on the latest main with nothing uncommitted"
  [ -z "$(git status --porcelain)" ] || die "uncommitted changes; commit or remove them first"
  git fetch --quiet origin
  [ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] ||
    die "HEAD isn't origin/main. Run: git switch --detach origin/main  (releases come only from main)"

  # Worktrees don't have local.properties; borrow the main checkout's.
  if [ ! -f local.properties ]; then
    local main_tree
    main_tree=$(git worktree list --porcelain | sed -n '1s/^worktree //p')
    [ -f "$main_tree/local.properties" ] || die "no local.properties here or in $main_tree"
    ln -s "$main_tree/local.properties" local.properties
    echo "Linked local.properties from $main_tree"
  fi
  for key in keystore.path keystore.alias keystore.password sentry.authToken; do
    grep -qE "^$key=.+" local.properties || die "local.properties is missing $key"
  done

  local old_name old_code new_name new_code
  old_name=$(current_name); old_code=$(current_code)
  [ -n "$old_name" ] && [ -n "$old_code" ] || die "couldn't read the version from $GRADLE_FILE"
  grep -q "\"versionCode\": $old_code," Web/version.json ||
    die "Web/version.json doesn't match $GRADLE_FILE ($old_code); fix that first"
  new_code=$((old_code + 1))
  new_name="${VERSION:-${old_name%.*}.$(( ${old_name##*.} + 1 ))}"
  local branch="claude/release-$new_name"
  local apk_old="Web/apk/Kash-$old_name.apk" apk_new="Web/apk/Kash-$new_name.apk"
  [ -f "$apk_old" ] || die "$apk_old not found"
  git show-ref --quiet "refs/heads/$branch" && die "branch $branch already exists"

  echo "Releasing $old_name ($old_code) -> $new_name ($new_code)"
  git switch --quiet -c "$branch"

  # A failed run (or a practice run) puts everything back as it was:
  # files, the release branch, the copied APK.
  UNDO_BRANCH="$branch"; UNDO_APK="$apk_new"; COMMITTED=0
  trap on_exit EXIT

  step "Bumping the version"
  perl -pi -e "s/versionCode = $old_code\b/versionCode = $new_code/; s/versionName = \"\Q$old_name\E\"/versionName = \"$new_name\"/" "$GRADLE_FILE"
  {
    echo '{'
    echo "  \"versionCode\": $new_code,"
    echo "  \"versionName\": \"$new_name\","
    echo "  \"url\": \"$SITE/download\","
    printf '  "notes": ['
    local i=0
    for n in "${notes[@]}"; do
      [ $i -gt 0 ] && printf ','
      printf '\n    "%s"' "$(printf '%s' "$n" | sed 's/\\/\\\\/g; s/"/\\"/g')"
      i=$((i + 1))
    done
    printf '\n  ]\n}\n'
  } > Web/version.json
  perl -pi -e "s/version \Q$old_name\E \(versionCode $old_code\)/version $new_name (versionCode $new_code)/" RULES.md

  step "Running the unit tests and building the signed release"
  local gradle_args=()
  [ "${DRY_RUN:-}" = "1" ] && gradle_args+=(-Psentry.noUpload=true)
  ./gradlew --quiet "${gradle_args[@]+"${gradle_args[@]}"}" testDebugUnitTest assembleRelease

  local built
  built=$(ls app/build/outputs/apk/release/*.apk 2>/dev/null | grep -v unsigned | sed -n 1p || true)
  [ -n "$built" ] || die "no signed APK in app/build/outputs/apk/release (is keystore.path right?)"

  step "Checking the APK"
  local bt cert badging
  bt=$(build_tools)
  cert=$("$bt/apksigner" verify --print-certs "$built" | sed -nE 's/.*certificate SHA-256 digest: ([0-9a-f]+).*/\1/p' | sed -n 1p)
  [ "$cert" = "$CERT_SHA256" ] || die "APK is signed with $cert, not the Kash key; it would not install as an update"
  badging=$("$bt/aapt2" dump badging "$built" | sed -n 1p)
  [[ "$badging" == *"versionCode='$new_code'"* ]] || die "APK has the wrong versionCode: $badging"
  [[ "$badging" == *"versionName='$new_name'"* ]] || die "APK has the wrong versionName: $badging"
  [[ "$badging" == *"name='et.kash.app'"* ]] || die "APK has the wrong package: $badging"
  echo "Signed with the Kash key, et.kash.app $new_name ($new_code)"

  step "Swapping the APK on the download page"
  git rm --quiet "$apk_old"
  mkdir -p Web/apk
  cp "$built" "$apk_new"
  local size_mb
  size_mb=$(awk -v b="$(wc -c < "$apk_new")" 'BEGIN { printf "%.1f", b / 1048576 }')
  perl -pi -e "s/Kash-\Q$old_name\E\.apk/Kash-$new_name.apk/g; s/Version \Q$old_name\E/Version $new_name/g; s{<span>[0-9.]+ MB</span>}{<span>$size_mb MB</span>}g" Web/download.html
  grep -q "$old_name" Web/download.html && die "Web/download.html still mentions $old_name"

  step "Logging it in docs/PROGRESS.md"
  local today joined
  today=$(date +%F)
  joined=$(printf "%s; " "${notes[@]}" | sed -E "s/; \$//; s/([^.!?])\$/\\1./")
  NOTE="- $today $new_name: $joined Not yet installed on a real phone." \
    perl -0pi -e 's/(## Done\n\n)/$1$ENV{NOTE}\n/' docs/PROGRESS.md

  git add -A "$GRADLE_FILE" Web/version.json Web/download.html Web/apk RULES.md docs/PROGRESS.md
  git commit --quiet -m "Release $new_name: ${notes[0]}

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
  echo "Committed on $branch"
  [ "${DRY_RUN:-}" = "1" ] || COMMITTED=1

  if [ "${DRY_RUN:-}" = "1" ]; then
    git --no-pager show --stat HEAD | cat
    echo; tester_message "$new_name" "${notes[@]}"
    return
  fi
  if [ "${NO_PUSH:-}" = "1" ]; then
    echo; echo "NO_PUSH: push with  git push -u origin $branch  and open the PR yourself."
    return
  fi

  step "Pushing and opening the pull request"
  git push --quiet -u origin "$branch"
  local body
  body="Tester release $new_name (versionCode $new_code).

## What's new
$(for n in "${notes[@]}"; do echo "- $n"; done)

## Checked by the script
- Unit tests pass
- APK signed with the Kash key, package \`et.kash.app\`, $new_name ($new_code), $size_mb MB
- \`Web/version.json\`, download page, \`RULES.md\` and \`docs/PROGRESS.md\` updated

## After merging
Run \`scripts/release.sh verify\`, then send testers the message it prints.

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
  gh pr create --base main --head "$branch" --title "Release $new_name: ${notes[0]}" --body "$body"

  echo
  echo "Next: when CI is green, merge it (gh pr merge --merge --delete-branch),"
  echo "then run: scripts/release.sh verify"
}

# ----------------------------------------------------------------- verify --
verify() {
  git fetch --quiet origin
  local json code name apk_size
  json=$(git show origin/main:Web/version.json)
  code=$(echo "$json" | sed -nE 's/.*"versionCode": ([0-9]+).*/\1/p')
  name=$(echo "$json" | sed -nE 's/.*"versionName": "([^"]+)".*/\1/p')
  apk_size=$(git cat-file -s "origin/main:Web/apk/Kash-$name.apk") ||
    die "Web/apk/Kash-$name.apk isn't on main yet; merge the release PR first"

  step "Waiting for $SITE to serve $name ($code)"
  local tries=0 live
  while :; do
    live=$(curl -fsS "$SITE/version.json?t=$(date +%s)" 2>/dev/null | sed -nE 's/.*"versionCode": ([0-9]+).*/\1/p' || true)
    [ "$live" = "$code" ] && break
    tries=$((tries + 1))
    [ $tries -ge 30 ] && die "site still serves versionCode '${live:-?}' after 10 minutes; check Cloudflare Pages"
    sleep 20
  done
  echo "version.json: $code"

  local headers status length
  headers=$(curl -fsSI "$SITE/apk/Kash-$name.apk") || die "APK URL doesn't answer: $SITE/apk/Kash-$name.apk"
  status=$(echo "$headers" | sed -n 1p | awk '{print $2}')
  length=$(echo "$headers" | tr -d '\r' | awk -F': ' 'tolower($1)=="content-length" {print $2}')
  [ "$status" = "200" ] || die "APK URL returned $status"
  [ -z "$length" ] || [ "$length" = "$apk_size" ] || die "live APK is $length bytes, main has $apk_size"
  [[ "$(curl -fsS "$SITE/download")" == *"Kash-$name.apk"* ]] || die "download page doesn't link Kash-$name.apk yet"
  echo "APK and download page: live"

  local notes=()
  while IFS= read -r line; do notes+=("$line"); done < <(
    echo "$json" | python3 -c 'import json,sys; [print(n) for n in json.load(sys.stdin).get("notes", [])]')

  step "Live. Send this to testers:"
  echo
  tester_message "$name" ${notes[@]+"${notes[@]}"}
  echo
  echo "Then install it on your own phone over the old version, and watch Sentry and PostHog for a day."
}

case "${1:-}" in
  prepare) shift; prepare "$@" ;;
  verify) verify ;;
  *) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 1 ;;
esac
