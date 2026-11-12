#!/usr/bin/env bash
#
# One release, from the owner's own terminal (AGENTS.md section 11).
#
#   ./scripts/release.sh X.Y.Z --check    check that it could be done; change nothing
#   ./scripts/release.sh X.Y.Z            do it
#
# Run on `develop` for an ordinary release, or on `hotfix/<name>` for a fix to what is already
# released. In this order, stopping at the first failure:
#
#   1. the branch is develop or hotfix/*, the tree is clean, the tag is free, the version is newer,
#      and `master` can be fast-forwarded to this branch (it holds nothing the branch lacks);
#   2. release notes present in every locale and within Play's limits, shown for a last read;
#   3. the full suite and lint;
#   4. after the owner types the version: `versionName` set and committed on the branch, `master`
#      fast-forwarded to that commit (never merged into), the tag `vX.Y.Z` on it;
#   5. the Play bundle built from the tag by build-bundle.sh, **which asks for the keystore
#      passwords itself and stores nothing** -- this script never sees them;
#   6. the evidence entry in store/play-console/releases.md, committed on the branch, not on
#      `master`, which stays on the tag;
#   7. for a hotfix: the branch merged back into `develop`, so the next fast-forward still works.
#
# Preparing a release and publishing it are separate states. This script prepares: the evidence
# entry says "prepared", and only the owner, by hand, marks it uploaded and then published.
#
# **A stop after step 4 leaves the steps already done in place.** Nothing is rolled back: the commit,
# `master` and the tag stay. The script says where it stopped and what is left; there is no resume
# mode, and a rerun from the start refuses at step 1 because the tag exists.
#
# **It does not push and does not upload.** Both are the owner's; the last lines say what to run
# and what to upload where.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
# shellcheck source=./use-tooling.sh
source scripts/use-tooling.sh
# shellcheck source=./internal/build-common.sh
source scripts/internal/build-common.sh

red() { printf '\033[31m%s\033[0m\n' "$*"; }
step() { printf '\n\033[1m== %s\033[0m\n' "$*"; }
fail() { red "❌ $*"; exit 1; }

version="${1:-}"
mode="${2:-}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "usage: ./scripts/release.sh X.Y.Z [--check]"
[ -z "$mode" ] || [ "$mode" = "--check" ] || fail "unknown option $mode"
tag="v$version"
build_file="$APP_MODULE/build.gradle.kts"
evidence="store/play-console/releases.md"
read -r -a locales <<< "$STORE_LOCALES"

# --- what a stop leaves behind -------------------------------------------------------------------
#
# Set once a step that writes has finished. An interrupted release is described, not undone: the
# owner decides what happens to a prepared tag, and a script that deleted one would be deciding it.
completed=""
branch=""
detached_for_bundle=0

describe_stop() {
    local status=$?
    if [ "$detached_for_bundle" = 1 ] && [ -n "$branch" ]; then
        # The bundle is built on a detached checkout of the tag; do not leave the owner there.
        git checkout -q "$branch" 2>/dev/null || true
    fi
    [ "$status" -ne 0 ] && [ -n "$completed" ] || return 0

    red ""
    red "Stopped. Last step completed: $completed. Nothing was rolled back."
    echo "   branch  $branch at $(git rev-parse --short "$branch" 2>/dev/null || echo '?')"
    echo "   master  at $(git rev-parse --short master 2>/dev/null || echo '?')"
    if git rev-parse -q --verify "refs/tags/$tag" >/dev/null; then
        echo "   tag     $tag at $(git rev-parse --short "$tag^{commit}")"
    else
        echo "   tag     $tag not created"
    fi
    case "$completed" in
        4*)
            echo
            echo "   The release commit, master and the tag are in place; the bundle is not."
            echo "   If the failure was the build or the signing, and no source or committed"
            echo "   configuration has to change, build the bundle for the existing tag:"
            echo "       git checkout --detach $tag && ./scripts/build-bundle.sh; git checkout $branch"
            echo "   then add the evidence entry to $evidence by hand (step 6)${hotfix_hint:-}."
            echo "   If the sources have to change, decide first what happens to $tag: a changed"
            echo "   tree must not be built under it."
            ;;
        5*)
            echo
            echo "   The bundle exists; the evidence entry is not committed. Add it to $evidence"
            echo "   by hand (step 6)${hotfix_hint:-}."
            ;;
        6*)
            echo
            echo "   Release prepared and recorded; merging $branch back into develop did not"
            echo "   finish. Merge it by hand: git checkout develop && git merge --no-ff $branch"
            ;;
    esac
}
trap describe_stop EXIT

# --- 1. where we stand ---------------------------------------------------------------------------
step "1. The tree"
branch="$(git rev-parse --abbrev-ref HEAD)"
case "$branch" in
    develop) kind="release" ;;
    hotfix/*) kind="hotfix"; hotfix_hint=", then merge $branch into develop (step 7)" ;;
    *) fail "releases are made from develop or a hotfix/* branch; this is $branch" ;;
esac
[ -z "$(git status --porcelain)" ] || fail "uncommitted changes -- commit them first"
git rev-parse -q --verify refs/heads/master >/dev/null || fail "no master branch"
if [ "$kind" = "hotfix" ]; then
    git rev-parse -q --verify refs/heads/develop >/dev/null || fail "no develop to merge the hotfix into"
fi
git rev-parse -q --verify "refs/tags/$tag" >/dev/null && fail "tag $tag already exists"
# master only ever moves forward onto a release commit. A commit that only master has would make the
# fast-forward impossible -- and is exactly what a merge commit there used to leave behind.
git merge-base --is-ancestor master HEAD ||
    fail "master holds commits that $branch does not; it cannot be fast-forwarded" \
        "(git log --oneline $branch..master shows them)"
[ -f "$evidence" ] || fail "no $evidence"

current="$(version_name)"
[ -n "$current" ] || fail "no versionName = \"…\" found in $build_file"
newest="$(printf '%s\n%s\n' "$current" "$version" | sort -V | tail -n 1)"
[ "$version" != "$current" ] && [ "$newest" = "$version" ] ||
    fail "$version is not newer than the current versionName $current"
# A hotfix branch carries the released versionName, not develop's; the tags are what every branch
# shares, so the version must also be newer than every tag already cut.
newest_tag="$(git tag -l 'v[0-9]*' | sed 's/^v//' | sort -V | tail -n 1)"
if [ -n "$newest_tag" ]; then
    [ "$(printf '%s\n%s\n' "$newest_tag" "$version" | sort -V | tail -n 1)" = "$version" ] ||
        fail "$version is not newer than the newest tag v$newest_tag"
fi
previous_tag="$(git describe --tags --abbrev=0 --match 'v[0-9]*' 2>/dev/null || true)"
echo "   $branch ($kind), clean; $current → $version; master fast-forwardable; last tag ${previous_tag:-none}"

# --- 2. release notes ----------------------------------------------------------------------------
step "2. Release notes"
for locale in "${locales[@]}"; do
    [ -s "store/listing/$locale/release-notes/default.txt" ] ||
        fail "no release notes at store/listing/$locale/release-notes/default.txt"
done
./scripts/check-store-metadata.sh
echo "   Have they been rewritten for $version? They are pasted into Play as they stand:"
for locale in "${locales[@]}"; do
    echo; echo "   [$locale]"; sed 's/^/   | /' "store/listing/$locale/release-notes/default.txt"
done

# --- 3. checks -----------------------------------------------------------------------------------
step "3. The full suite and lint"
if declare -F release_prepare >/dev/null; then
    release_prepare || fail "release_prepare (scripts/project.env) failed"
fi
./scripts/test.sh || fail "the suite failed; nothing was changed"
./scripts/lint.sh || fail "lint failed; nothing was changed"

if [ "$mode" = "--check" ]; then
    step "Checked. Nothing was changed."
    echo "   Run without --check to make the release."
    exit 0
fi

echo
read -r -p "Everything above is right, and the notes are for $version? Type the version to go on: " answer
[ "$answer" = "$version" ] || fail "stopped; nothing was changed"

notes_first="store/listing/${locales[0]}/release-notes/default.txt"

# --- 4. version, master and tag ------------------------------------------------------------------
step "4. versionName $version, commit on $branch, master fast-forwarded, tag $tag"
# Only the number is replaced: Kratkoza's literal is "0.32.0$versionSuffix", and the suffix is what
# keeps branch builds recognisable on the phone.
python3 - "$build_file" "$current" "$version" <<'PY'
import pathlib, re, sys
path, current, version = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
text = path.read_text(encoding="utf-8")
pattern = re.compile(r'(versionName = ")' + re.escape(current) + r'(?=["$])')
updated, count = pattern.subn(lambda m: m.group(1) + version, text, count=1)
if count != 1:
    sys.exit(f"versionName \"{current}\" not found in {path}")
path.write_text(updated, encoding="utf-8")
PY
[ "$(version_name)" = "$version" ] || fail "could not set versionName"
git add "$build_file"
git commit -q -m "Release $version" -m "$(cat "$notes_first")"
# fetch into a branch refuses anything but a fast-forward, and does it without a checkout.
git fetch -q . "$branch:master"
git tag -a "$tag" -m "$APP_NAME $version" -m "$(cat "$notes_first")"
commit="$(git rev-parse --short HEAD)"
code="$(version_code)"
[ "$(git rev-parse master)" = "$(git rev-parse HEAD)" ] || fail "master did not land on the release commit"
completed="4 (release commit $commit, master and $tag on it)"
echo "   $commit on $branch; master fast-forwarded to it; tagged $tag; versionCode $code"

# --- 5. the Play bundle --------------------------------------------------------------------------
step "5. Play bundle from $tag (the keystore passwords are asked for by build-bundle.sh, and kept nowhere)"
# Built from a detached checkout of the tag, so what is bundled is by construction what is tagged --
# and a detached HEAD gets no branch suffix in its versionName.
detached_for_bundle=1
git checkout -q --detach "$tag"
./scripts/build-bundle.sh
artifact="$(bundle_artifact "$version" "$code" "$(source_id)")"
git checkout -q "$branch"
detached_for_bundle=0
[ -f "$artifact" ] || fail "no bundle at $artifact"
completed="5 (bundle $(basename "$artifact"))"

# --- 6. evidence ---------------------------------------------------------------------------------
step "6. Evidence in $evidence"
size="$(stat -c %s "$artifact")"
sha="$(sha256sum "$artifact" | cut -d' ' -f1)"
fingerprint="$("$JAVA_HOME/bin/keytool" -printcert -jarfile "$artifact" 2>/dev/null \
    | sed -n 's/^[[:space:]]*SHA256: //p' | head -n 1)"
[ -n "$fingerprint" ] || fail "could not read the signer from the bundle"

entry="$(cat <<ENTRY
## $version — versionCode $code

$PLAY_TRACK. Notes as uploaded: \`store/listing/*/release-notes/default.txt\` at \`$tag\`.

| | |
| --- | --- |
| Artifact | \`dist/$(basename "$artifact")\` |
| Size | $(printf "%'d" "$size") bytes |
| SHA-256 | \`$sha\` |
| versionCode | $code — \`git rev-list --count HEAD\`, not written by hand |
| versionName | $version |
| Commit | \`$commit\` |
| Tag | \`$tag\` |
| Branch | \`$branch\` ($kind) |
| Built | $(date +%F) |

**Signer**, read from the bundle by \`release.sh\`: \`$fingerprint\`.

**State.** Prepared by \`release.sh\` on $(date +%F). Uploaded to Play Console: **not yet** —
confirmed by hand. Published: **not yet** — confirmed by hand.

**What changed since ${previous_tag:-the beginning}**: \`git log --oneline ${previous_tag:+$previous_tag..}$tag\`.
ENTRY
)"
# Newest first, above the previous entry; at the end when this is the first.
python3 - "$evidence" "$entry" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
at = text.find("\n## ")
entry = sys.argv[2].rstrip("\n") + "\n\n"
if at < 0:
    path.write_text(text.rstrip("\n") + "\n\n" + entry, encoding="utf-8")
else:
    path.write_text(text[:at + 1] + entry + text[at + 1:], encoding="utf-8")
PY
git add "$evidence"
git commit -q -m "Record the evidence for versionCode $code" -m "The bundle for $tag: $size bytes, SHA-256 $sha."
completed="6 (evidence committed on $branch)"
echo "   recorded on $branch; master stays on $tag"

# --- 7. a hotfix goes back into develop ----------------------------------------------------------
if [ "$kind" = "hotfix" ]; then
    step "7. $branch back into develop"
    # A merge, not a cherry-pick: a copy with another hash would leave master holding a commit that
    # develop lacks, and the next release's fast-forward would be refused.
    git checkout -q develop
    if ! git merge -q --no-ff "$branch" -m "Merge $branch into develop — hotfix $version"; then
        git merge --abort || true
        git checkout -q "$branch"
        fail "merging $branch into develop conflicts; merge it by hand"
    fi
    completed="7 ($branch merged into develop)"
    echo "   merged; master is an ancestor of develop again: $(git merge-base --is-ancestor master develop && echo yes || echo NO)"
fi
end_branch="$(git rev-parse --abbrev-ref HEAD)"

# --- what is the owner's -------------------------------------------------------------------------
step "Done: $version prepared. What is yours:"
push_refs="master $branch"
[ "$kind" = "hotfix" ] && push_refs="$push_refs develop"
cat <<DONE
   1. Upload   $artifact
      Play Console → $APP_NAME → Test and release → Testing → $PLAY_TRACK → Create new release.
      The SHA-256 Play shows for the upload should be $sha.
   2. Paste the release notes from store/listing/<locale>/release-notes/default.txt.
   3. Mark the entry in $evidence uploaded, and later published, when each has happened.
   4. Push:    git push origin $push_refs $tag
   You are on $end_branch.
DONE
