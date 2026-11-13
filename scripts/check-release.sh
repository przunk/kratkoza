#!/usr/bin/env bash
#
# Runs release.sh for real, on throwaway clones of this repository, and checks what it leaves.
#
# A release script is the one script whose mistakes cannot be undone afterwards -- a tag pushed, a
# master moved -- and syntax checks say nothing about what it does to the branches. So each scenario
# clones the repository into a temporary directory, installs the scripts *from this working tree*
# (the version under test, committed or not), and runs the release there. Nothing touches this
# repository: the clones have no remote, and they are deleted at the end.
#
# What is replaced in the clones, and only there: the suite, lint and the bundle build. Running the
# real Gradle suite per scenario would take an hour and tests nothing about the release; the bundle
# needs the owner's keystore. The bundle stand-in still checks that it runs on a detached checkout
# of the tag with the tagged versionName, and produces a JAR-signed file under the name release.sh
# expects, so steps 5 and 6 run against a real signature.
#
# Usage:  ./scripts/check-release.sh            all scenarios
#         KEEP=1 ./scripts/check-release.sh     keep the clones for inspection
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./use-tooling.sh
source "$SCRIPT_DIR/use-tooling.sh"

WORK="$(mktemp -d "/tmp/$APP_SLUG-check-release.XXXXXX")"
if [ "${KEEP:-0}" != 1 ]; then
    trap 'rm -rf "$WORK"' EXIT
fi

passed=0
failed=0
ok() { echo "   ✅ $*"; passed=$((passed + 1)); }
bad() { echo "   ❌ $*"; failed=$((failed + 1)); }
check() { local what="$1"; shift; if "$@" >/dev/null 2>&1; then ok "$what"; else bad "$what"; fi; }

# One throwaway keystore for the bundle stand-in.
KEYSTORE="$WORK/check.jks"
"$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KEYSTORE" -storepass checkpass -keypass checkpass \
    -alias check -dname "CN=Release Check" -keyalg RSA -keysize 2048 -validity 2 >/dev/null 2>&1
export CHECK_RELEASE_KEYSTORE="$KEYSTORE"

# A clone with develop and master as they are here, no remote, and the scripts under test committed
# on develop. Prints its path.
fresh_clone() {
    local name="$1" repo="$WORK/$1"
    git clone -q --no-local "$PROJECT_DIR" "$repo"
    git -C "$repo" checkout -q -B master origin/master
    git -C "$repo" checkout -q -B develop origin/develop
    git -C "$repo" remote remove origin
    git -C "$repo" config user.name "Release Check"
    git -C "$repo" config user.email "release-check@localhost"

    # The release tooling under test: the scripts, and the merge rule for the evidence file.
    rm -rf "$repo/scripts"
    cp -r "$PROJECT_DIR/scripts" "$repo/scripts"
    cp "$PROJECT_DIR/.gitattributes" "$repo/.gitattributes"
    install_stand_ins "$repo"
    git -C "$repo" add -A scripts .gitattributes
    git -C "$repo" commit -q -m "Scripts under test"
    printf '%s\n' "$repo"
}

install_stand_ins() {
    local repo="$1"
    cat > "$repo/scripts/test.sh" <<'EOF'
#!/usr/bin/env bash
# Stand-in for the suite in a release-check clone.
[ "${CHECK_RELEASE_FAIL_SUITE:-0}" = 1 ] && { echo "❌ stand-in suite: failing on request"; exit 1; }
echo "✅ stand-in suite"
EOF
    cat > "$repo/scripts/lint.sh" <<'EOF'
#!/usr/bin/env bash
echo "✅ stand-in lint"
EOF
    cat > "$repo/scripts/build-bundle.sh" <<'EOF'
#!/usr/bin/env bash
# Stand-in for the bundle build in a release-check clone. Checks what the real one is handed.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/use-tooling.sh"
source "$SCRIPT_DIR/internal/build-common.sh"
cd "$PROJECT_DIR"
[ "${CHECK_RELEASE_FAIL_BUNDLE:-0}" = 1 ] && { echo "❌ stand-in bundle: failing on request"; exit 1; }
[ "$(git rev-parse --abbrev-ref HEAD)" = HEAD ] || { echo "❌ bundle not built from a detached checkout"; exit 1; }
tag="$(git describe --exact-match --tags HEAD 2>/dev/null)" || { echo "❌ bundle not built from a tag"; exit 1; }
[ "v$(version_name)" = "$tag" ] || { echo "❌ versionName $(version_name) does not match $tag"; exit 1; }
artifact="$(bundle_artifact "$(version_name)" "$(version_code)" "$(source_id)")"
payload="$(mktemp -d)"
echo "release check" > "$payload/BundleConfig.pb"
mkdir -p "$(dirname "$artifact")"
"$JAVA_HOME/bin/jar" cf "$artifact" -C "$payload" .
"$JAVA_HOME/bin/jarsigner" -keystore "$CHECK_RELEASE_KEYSTORE" -storepass checkpass "$artifact" check >/dev/null
echo "📦 $artifact"
EOF
    chmod +x "$repo/scripts/test.sh" "$repo/scripts/lint.sh" "$repo/scripts/build-bundle.sh"
}

# Every ref and the working tree, to show that a refused or checked run changed nothing.
snapshot() {
    { git -C "$1" for-each-ref --format='%(refname) %(objectname)'; git -C "$1" rev-parse HEAD
      git -C "$1" status --porcelain; } | sha256sum
}

# Runs release.sh in a clone, typing $answer at the prompt. Output goes to a log; the exit status is
# returned.
run_release() {
    local repo="$1" answer="$2"; shift 2
    printf '%s\n' "$answer" | (cd "$repo" && ./scripts/release.sh "$@") > "$repo.log" 2>&1
}

refuses() {
    local what="$1" repo="$2" expect="$3"; shift 3
    local before; before="$(snapshot "$repo")"
    if run_release "$repo" "" "$@"; then
        bad "$what: release.sh succeeded"
    elif ! grep -q -- "$expect" "$repo.log"; then
        bad "$what: refused, but not with \"$expect\" (see $repo.log)"
    elif [ "$(snapshot "$repo")" != "$before" ]; then
        bad "$what: refused, but changed the repository"
    else
        ok "$what"
    fi
}

# Brings master into develop's history the way it was repaired on 2026-11-12 (8ee63df): a merge on
# develop, not a moved master. A no-op once the history is shared.
repair_history() {
    git -C "$1" merge -q --no-ff master -m "Merge master into develop to restore a shared history"
}

echo "🧪 $APP_NAME — release.sh on throwaway clones ($WORK)"

# --- the history as it stands --------------------------------------------------------------------
echo
echo "As the history stands"
repo="$(fresh_clone as-is)"
before="$(snapshot "$repo")"
if run_release "$repo" "" 9.0.0 --check; then
    check "this repository's own history passes --check and is left unchanged" \
        test "$(snapshot "$repo")" = "$before"
else
    bad "this repository's own history fails --check (see $repo.log)"
fi
# The state master was in until 2026-11-12: a commit that only master has.
git -C "$repo" checkout -q master
git -C "$repo" commit -q --allow-empty -m "A commit only master has"
git -C "$repo" checkout -q develop
refuses "master holding a commit develop lacks is refused" "$repo" "cannot be fast-forwarded" 9.0.0 --check

# --- refusals before anything is written ---------------------------------------------------------
echo
echo "Refusals"
repo="$(fresh_clone refusals)"
repair_history "$repo"
# Below the current versionName, and not an existing tag, so it is the version rule that refuses.
refuses "a version that is not newer" "$repo" "is not newer than the current versionName" 0.31.9
refuses "a malformed version" "$repo" "usage" 1.2
git -C "$repo" tag -a v9.9.9 -m test
refuses "an existing tag" "$repo" "already exists" 9.9.9
git -C "$repo" tag -d v9.9.9 >/dev/null
git -C "$repo" checkout -q -b feature/elsewhere
refuses "a feature branch" "$repo" "releases are made from develop" 9.0.0
git -C "$repo" checkout -q develop
echo "scratch" >> "$repo/README.md"
refuses "uncommitted changes" "$repo" "uncommitted changes" 9.0.0
git -C "$repo" checkout -q -- README.md
before="$(snapshot "$repo")"
CHECK_RELEASE_FAIL_SUITE=1 run_release "$repo" "" 9.0.0 && bad "a failing suite: release.sh succeeded" ||
    check "a failing suite stops it and changes nothing" test "$(snapshot "$repo")" = "$before"
before="$(snapshot "$repo")"
run_release "$repo" "9.0.1" 9.0.0 && bad "a mistyped confirmation: release.sh succeeded" ||
    check "a mistyped confirmation stops it and changes nothing" test "$(snapshot "$repo")" = "$before"
before="$(snapshot "$repo")"
if run_release "$repo" "" 9.0.0 --check; then
    check "--check passes and changes nothing" test "$(snapshot "$repo")" = "$before"
else
    bad "--check failed (see $repo.log)"
fi

# --- a release from develop ----------------------------------------------------------------------
echo
echo "A release from develop"
repo="$(fresh_clone release)"
repair_history "$repo"
if run_release "$repo" "9.0.0" 9.0.0; then
    ok "release.sh 9.0.0 succeeded"
    g() { git -C "$repo" "$@"; }
    check "the tag is annotated and names the release commit" \
        test "$(g cat-file -t v9.0.0)" = tag
    check "master is on the tag, fast-forwarded" test "$(g rev-parse master)" = "$(g rev-parse 'v9.0.0^{commit}')"
    check "master's tip is the release commit itself, not a merge" \
        test "$(g rev-list --parents -n 1 master | wc -w)" -eq 2
    check "develop is one evidence commit past the tag" test "$(g rev-list --count v9.0.0..develop)" -eq 1
    check "the evidence commit is not on master" bash -c "! git -C '$repo' merge-base --is-ancestor develop master"
    check "versionName is 9.0.0 at the tag, suffix kept" \
        bash -c "git -C '$repo' show v9.0.0:app/build.gradle.kts | grep -q 'versionName = \"9.0.0\$versionSuffix\"'"
    check "the evidence entry is there, prepared and not yet uploaded" \
        bash -c "grep -q '^## 9.0.0 — versionCode' '$repo/store/play-console/releases.md' && grep -q 'Uploaded to Play Console: \*\*not yet\*\*' '$repo/store/play-console/releases.md'"
    check "the bundle named in the evidence exists" \
        bash -c "f=\$(sed -n 's/^| Artifact | \`\(.*\)\` |\$/\1/p' '$repo/store/play-console/releases.md' | head -n 1); [ -f '$repo/'\"\$f\" ]"
    check "it ends on develop, with a clean tree" \
        test "$(g rev-parse --abbrev-ref HEAD) $(g status --porcelain | wc -l)" = "develop 0"
    check "the next release can fast-forward master again" g merge-base --is-ancestor master develop
else
    bad "release.sh 9.0.0 failed (see $repo.log)"
fi

# --- a hotfix ------------------------------------------------------------------------------------
echo
echo "A hotfix while develop has unreleased work"
if [ -d "$repo" ] && git -C "$repo" rev-parse -q --verify v9.0.0 >/dev/null; then
    g() { git -C "$repo" "$@"; }
    echo "unreleased" > "$repo/UNRELEASED.md"; g add UNRELEASED.md; g commit -q -m "Unreleased work"
    g checkout -q -b hotfix/urgent master
    echo "fix" > "$repo/HOTFIX.md"; g add HOTFIX.md; g commit -q -m "Fix the urgent thing"
    if run_release "$repo" "9.0.1" 9.0.1; then
        ok "release.sh 9.0.1 on hotfix/urgent succeeded"
        check "master is on v9.0.1" test "$(g rev-parse master)" = "$(g rev-parse 'v9.0.1^{commit}')"
        check "the unreleased work is not in the hotfix" bash -c "! git -C '$repo' cat-file -e v9.0.1:UNRELEASED.md 2>/dev/null"
        check "develop contains the hotfix, by merge" g merge-base --is-ancestor hotfix/urgent develop
        check "develop keeps its unreleased work" g cat-file -e develop:UNRELEASED.md
        check "develop's evidence holds both 9.0.0 and 9.0.1" \
            bash -c "git -C '$repo' show develop:store/play-console/releases.md | grep -c '^## 9\.0\.[01] — ' | grep -qx 2"
        check "it ends on develop" test "$(g rev-parse --abbrev-ref HEAD)" = develop
        if run_release "$repo" "9.1.0" 9.1.0; then
            ok "the next release from develop fast-forwards master past the hotfix"
            check "master is on v9.1.0" test "$(g rev-parse master)" = "$(g rev-parse 'v9.1.0^{commit}')"
        else
            bad "the next release from develop failed (see $repo.log)"
        fi
    else
        bad "release.sh 9.0.1 on hotfix/urgent failed (see $repo.log)"
    fi
    # The branch's own versionName is not the only bar: a tag left by an interrupted or abandoned
    # release elsewhere still counts, or the next release could be cut below it.
    g tag -a v9.5.0 -m "left behind" develop
    g checkout -q -b hotfix/late master
    echo "late" > "$repo/LATE.md"; g add LATE.md; g commit -q -m "Late fix"
    refuses "a version below the newest tag, even off the branch" "$repo" "newest tag v9.5.0" 9.1.1
    g tag -d v9.5.0 >/dev/null
    g checkout -q develop
else
    bad "skipped: the develop release did not happen"
fi

# --- a stop after the tag ------------------------------------------------------------------------
echo
echo "A bundle failure after the tag"
repo="$(fresh_clone interrupted)"
repair_history "$repo"
if CHECK_RELEASE_FAIL_BUNDLE=1 run_release "$repo" "9.0.0" 9.0.0; then
    bad "release.sh succeeded although the bundle failed"
else
    g() { git -C "$repo" "$@"; }
    check "the tag stays" g rev-parse -q --verify 'refs/tags/v9.0.0'
    check "master stays on the tag" test "$(g rev-parse master)" = "$(g rev-parse 'v9.0.0^{commit}')"
    check "no evidence commit" test "$(g rev-list --count v9.0.0..develop)" -eq 0
    check "back on develop, not left detached" test "$(g rev-parse --abbrev-ref HEAD)" = develop
    check "the stop is described" grep -q "Last step completed: 4" "$repo.log"
    check "and says how to rebuild the bundle for the tag" grep -q "git checkout --detach v9.0.0" "$repo.log"
fi

echo
if [ "$failed" -eq 0 ] && [ "$passed" -gt 0 ]; then
    echo "✅ $passed checks passed"
else
    echo "❌ $failed of $((passed + failed)) checks failed"
    exit 1
fi
