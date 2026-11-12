#!/usr/bin/env bash
#
# Builds the Android App Bundle for Google Play, and refuses to hand over one Play would reject.
#
# ## Credentials
#
# **Asked for at the prompt and kept nowhere.** They reach Gradle through `ORG_GRADLE_PROJECT_*`
# environment variables -- how Gradle takes a project property from the environment -- and live
# only as long as this process: nothing reaches the repository, gradle.properties or the shell
# history. The keystore passwords are the owner's; no agent runs this script with them.
#
# If `PRZUNK_UPLOAD_STORE_FILE` is already in ~/.gradle/gradle.properties, that is used instead and
# nothing is asked. Without a terminal, the four `PRZUNK_UPLOAD_*` variables must be set.
#
# Usage:  ./scripts/build-bundle.sh [extra gradle args…]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./use-tooling.sh
source "$SCRIPT_DIR/use-tooling.sh"
cd "$PROJECT_DIR"
# shellcheck source=./internal/build-common.sh
source "$SCRIPT_DIR/internal/build-common.sh"

LOG_FILE="$LOG_PREFIX-bundle.log"
BUILD_AAB="$MODULE_BUILD_DIR/outputs/bundle/release/$APP_MODULE-release.aab"
DIST_DIR="$PROJECT_DIR/dist"

fail() {
    echo "❌ $1"
    shift || true
    for line in "$@"; do echo "   $line"; done
    exit 1
}

already_configured() {
    grep -qs '^[[:space:]]*PRZUNK_UPLOAD_STORE_FILE[[:space:]]*=' "$HOME/.gradle/gradle.properties"
}

collect_signing_configuration() {
    local entered current

    if already_configured && [ -z "${PRZUNK_UPLOAD_STORE_FILE:-}" ]; then
        echo "🔑 Using PRZUNK_UPLOAD_* from ~/.gradle/gradle.properties"
        return
    fi

    if [ -t 0 ]; then
        current="${PRZUNK_UPLOAD_STORE_FILE:-$KEYSTORE_DEFAULT}"
        read -r -p "Upload keystore path [$current]: " entered
        PRZUNK_UPLOAD_STORE_FILE="${entered:-$current}"
    fi
    [ -n "${PRZUNK_UPLOAD_STORE_FILE:-}" ] || fail "no keystore given" \
        "Set PRZUNK_UPLOAD_STORE_FILE, or run this from a terminal."

    # ~ is the shell's, not the filesystem's; a typed path keeps it literal.
    PRZUNK_UPLOAD_STORE_FILE="${PRZUNK_UPLOAD_STORE_FILE/#\~/$HOME}"
    [ -f "$PRZUNK_UPLOAD_STORE_FILE" ] || fail "keystore not found: $PRZUNK_UPLOAD_STORE_FILE"

    if [ -t 0 ]; then
        current="${PRZUNK_UPLOAD_KEY_ALIAS:-$KEY_ALIAS}"
        read -r -p "Key alias [$current]: " entered
        PRZUNK_UPLOAD_KEY_ALIAS="${entered:-$current}"
    fi
    [ -n "${PRZUNK_UPLOAD_KEY_ALIAS:-}" ] || fail "no key alias given"

    if [ -t 0 ]; then
        read -r -s -p "Keystore password: " PRZUNK_UPLOAD_STORE_PASSWORD
        echo
    fi
    [ -n "${PRZUNK_UPLOAD_STORE_PASSWORD:-}" ] || fail "no keystore password given"

    if [ -t 0 ]; then
        read -r -s -p "Key password [Enter = keystore password]: " entered
        echo
        PRZUNK_UPLOAD_KEY_PASSWORD="${entered:-$PRZUNK_UPLOAD_STORE_PASSWORD}"
    fi
    PRZUNK_UPLOAD_KEY_PASSWORD="${PRZUNK_UPLOAD_KEY_PASSWORD:-$PRZUNK_UPLOAD_STORE_PASSWORD}"

    export ORG_GRADLE_PROJECT_PRZUNK_UPLOAD_STORE_FILE="$PRZUNK_UPLOAD_STORE_FILE"
    export ORG_GRADLE_PROJECT_PRZUNK_UPLOAD_KEY_ALIAS="$PRZUNK_UPLOAD_KEY_ALIAS"
    export ORG_GRADLE_PROJECT_PRZUNK_UPLOAD_STORE_PASSWORD="$PRZUNK_UPLOAD_STORE_PASSWORD"
    export ORG_GRADLE_PROJECT_PRZUNK_UPLOAD_KEY_PASSWORD="$PRZUNK_UPLOAD_KEY_PASSWORD"

    echo "🔑 $PRZUNK_UPLOAD_STORE_FILE (alias $PRZUNK_UPLOAD_KEY_ALIAS) — passwords not stored"
}

name="$(version_name)"
code="$(version_code)"
source="$(source_id)"
artifact="$(bundle_artifact "$name" "$code" "$source")"

echo "🔨 $APP_NAME — Play bundle ${name:-?} ($code) from $source"

# Not refused, because a bundle is sometimes built to look at, but said loudly: a bundle from
# uncommitted changes cannot be traced back to any commit, and release.sh never builds one.
case "$source" in
    *-dirty) echo "⚠️  Uncommitted changes: this bundle matches no commit. Do not upload it." ;;
esac

# Play rejects a versionCode it has already seen. The versionCode is the commit count, so this file
# existing means this very commit count was bundled before: a rebuild is fine, but if that bundle
# was uploaded, this one cannot be -- commit first.
if [ -f "$artifact" ]; then
    echo "ℹ️  A bundle for versionCode $code already exists and will be replaced."
    echo "   If it was uploaded, Play will refuse this one: the versionCode moves only with a commit."
fi

require_toolchain
write_local_properties
collect_signing_configuration
echo

started_at=$(date +%s)
if ! ./gradlew ":$APP_MODULE:bundleRelease" --no-daemon --console=plain "$@" > "$LOG_FILE" 2>&1; then
    report_failure "$LOG_FILE"
fi

[ -f "$BUILD_AAB" ] || fail "the build reported success but produced no bundle at $BUILD_AAB"

# A bundle carries a JAR signature, not an APK one, so apksigner cannot read it.
JARSIGNER="$JAVA_HOME/bin/jarsigner"
[ -x "$JARSIGNER" ] || fail "jarsigner is unavailable in $JAVA_HOME"

VERIFY_LOG="$LOG_PREFIX-bundle-verify.log"
if ! "$JARSIGNER" -J-Duser.language=en -verify "$BUILD_AAB" > "$VERIFY_LOG" 2>&1 ||
    ! grep -Fq "jar verified." "$VERIFY_LOG"; then
    fail "the bundle carries no verifiable signature" "Full log: $VERIFY_LOG"
fi

signer="$("$JAVA_HOME/bin/keytool" -printcert -jarfile "$BUILD_AAB" 2>/dev/null | grep -m1 'Owner:' || true)"
[ -n "$signer" ] || fail "no certificate found in the bundle"

# The hard rule, and the reason this is not build-release.sh with another task. For an APK the debug
# fallback earns its place; for a bundle headed to Play it buys only a rejection discovered later.
if echo "$signer" | grep -qi 'CN=Android Debug'; then
    fail "DEBUG KEY — Play will reject this bundle." \
        "$signer" \
        "" \
        "Gradle fell back to the debug key, which means it never saw the credentials." \
        "Check the alias exists in that keystore: keytool -list -keystore <path>"
fi

mkdir -p "$DIST_DIR"
cp "$BUILD_AAB" "$artifact"

echo "🔎 $signer"
echo "✅ OK in $(($(date +%s) - started_at))s"
echo "📦 $artifact ($(du -h "$artifact" | cut -f1))"
sha256sum "$artifact"
