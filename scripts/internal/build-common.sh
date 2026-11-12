#!/usr/bin/env bash
#
# Shared by the build and release scripts. Sourced after use-tooling.sh, never executed.

# Fail before a build spends minutes discovering the toolchain is incomplete.
require_toolchain() {
    if ! "$PROJECT_DIR/scripts/check-tooling.sh" > "$LOG_PREFIX-toolchain.log" 2>&1; then
        echo "❌ Toolchain incomplete:"
        sed 's/^/   /' "$LOG_PREFIX-toolchain.log"
        exit 1
    fi
    if declare -F require_sources >/dev/null; then
        require_sources || exit 1
    fi
}

# ANDROID_HOME is exported, but Android Studio writes local.properties too, and a stale one from
# another machine points somewhere that does not exist.
write_local_properties() {
    printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$PROJECT_DIR/local.properties"
}

# The versionName as typed in the module's build file, without the branch suffix Kratkoza appends
# at build time ("0.32.0$versionSuffix" reads as 0.32.0). The one place that reads it, because the
# bundle script, the release script and the evidence entry all have to agree on it.
version_name() {
    local name
    name="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' "$PROJECT_DIR/$APP_MODULE/build.gradle.kts" | head -n 1)"
    printf '%s\n' "${name%%\$*}"
}

# The versionCode: the commit count, computed exactly as app/build.gradle.kts computes it. A bundle
# cannot be asked what it contains the way an APK can, so the two expressions have to agree -- if
# the build file changes how it counts, this changes with it.
version_code() {
    git -C "$PROJECT_DIR" rev-list --count HEAD 2>/dev/null || echo 1
}

# Which sources a build came from (AGENTS.md section 11): the short commit SHA, and "-dirty" when the
# tree had uncommitted changes. versionCode alone cannot say it -- parallel branches share counts,
# and an amend keeps the count. With "-dirty" the SHA names only the base commit, not the build.
source_id() {
    local sha
    sha="$(git -C "$PROJECT_DIR" rev-parse --short HEAD 2>/dev/null || echo nogit)"
    if [ -n "$(git -C "$PROJECT_DIR" status --porcelain 2>/dev/null)" ]; then
        sha="$sha-dirty"
    fi
    printf '%s\n' "$sha"
}

# Where build-bundle.sh puts the bundle, and where release.sh looks for it. One function so that the
# two cannot drift apart.
bundle_artifact() {
    printf '%s\n' "$PROJECT_DIR/dist/$APP_SLUG-${1:-unknown}-$2-$3.aab"
}

# Prints the part of a Gradle log worth reading and exits.
report_failure() {
    local log_file="$1"
    echo "❌ Build failed. What went wrong:"
    if grep -aq '^\* What went wrong:' "$log_file"; then
        sed -n '/^\* What went wrong:/,/^\* Try:/p' "$log_file" \
            | grep -av '^\* Try:' | sed "s|$PROJECT_DIR/||" | head -n 25
    fi
    grep -a '^e: ' "$log_file" | sed "s|file://$PROJECT_DIR/||" | head -n 25
    echo
    echo "Full log: $log_file"
    exit 1
}

# Copies a built APK into dist/ under a name that says which build it is: variant, versionName,
# versionCode, source commit and a timestamp. Two builds of one versionName are otherwise
# indistinguishable once they leave dist/, which is exactly when it matters which one is on the phone.
publish_artifact() {
    local build_apk="$1" kind="$2"

    # A green build with no artifact is a failure, not a success.
    if [ ! -f "$build_apk" ]; then
        echo "❌ Build reported success but produced no APK at $build_apk" >&2
        exit 1
    fi

    # Read out of the artifact rather than out of the source: asking the APK what it is cannot
    # disagree with what it is.
    local name code stamp infix artifact aapt badging
    aapt="$(ls -d "$ANDROID_HOME"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -n 1)"
    if [ -x "$aapt" ]; then
        badging="$("$aapt" dump badging "$build_apk" 2>/dev/null | head -n 1)"
        name="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<< "$badging")"
        code="$(sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" <<< "$badging")"
    fi
    # A branch build's versionName carries "+feature/x"; a slash has no place in a file name.
    name="${name//\//-}"
    stamp="$(date +%Y%m%d-%H%M%S)"
    infix=""
    [ "$kind" = "debug" ] && infix="debug-"

    mkdir -p "$PROJECT_DIR/dist"
    artifact="$PROJECT_DIR/dist/$APP_SLUG-${infix}${name:-unknown}-${code:-0}-$(source_id)-$stamp.apk"
    cp "$build_apk" "$artifact"
    printf '%s\n' "$artifact"
}
