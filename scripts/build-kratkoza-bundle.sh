#!/usr/bin/env bash
#
# Builds an Android App Bundle for Google Play and refuses to hand over one Play would reject.
#
# Credentials are asked for when the script runs and are never written anywhere — not into the
# repository, not into ~/.gradle/gradle.properties, not into the shell history. They are handed to
# Gradle through ORG_GRADLE_PROJECT_* environment variables, which is how Gradle takes a project
# property from the environment, and they live only as long as this process does.
#
# Non-interactive use (CI, or a shell without a terminal) reads the same values from:
#   KRATKOZA_UPLOAD_KEYSTORE, KRATKOZA_UPLOAD_KEY_ALIAS,
#   KRATKOZA_UPLOAD_STORE_PASSWORD, KRATKOZA_UPLOAD_KEY_PASSWORD
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/kratkoza-env.sh"
cd "$KRATKOZA_DIR"

DIST_DIR="$KRATKOZA_DIR/dist"
LOG_FILE="$KRATKOZA_LOG_PREFIX-bundle.log"
BUILD_AAB="$KRATKOZA_DIR/app/build/outputs/bundle/release/app-release.aab"

fail() {
    echo "❌ $1"
    shift || true
    for line in "$@"; do echo "   $line"; done
    exit 1
}

# Asks for what it needs, defaulting to whatever the environment already carries.
collect_signing_configuration() {
    local entered current

    if [ -t 0 ]; then
        current="${KRATKOZA_UPLOAD_KEYSTORE:-}"
        if [ -n "$current" ]; then
            read -r -p "Upload keystore path [$current]: " entered
            KRATKOZA_UPLOAD_KEYSTORE="${entered:-$current}"
        else
            read -r -p "Upload keystore path: " KRATKOZA_UPLOAD_KEYSTORE
        fi
    fi
    [ -n "${KRATKOZA_UPLOAD_KEYSTORE:-}" ] || fail "no keystore given" \
        "Set KRATKOZA_UPLOAD_KEYSTORE, or run this from a terminal so it can ask."

    # ~ is the shell's, not the filesystem's; a typed path keeps it literal.
    KRATKOZA_UPLOAD_KEYSTORE="${KRATKOZA_UPLOAD_KEYSTORE/#\~/$HOME}"
    [ -f "$KRATKOZA_UPLOAD_KEYSTORE" ] || fail "keystore not found: $KRATKOZA_UPLOAD_KEYSTORE"

    if [ -t 0 ]; then
        current="${KRATKOZA_UPLOAD_KEY_ALIAS:-kratkoza}"
        read -r -p "Key alias [$current]: " entered
        KRATKOZA_UPLOAD_KEY_ALIAS="${entered:-$current}"
    fi
    [ -n "${KRATKOZA_UPLOAD_KEY_ALIAS:-}" ] || fail "no key alias given"

    if [ -t 0 ]; then
        read -r -s -p "Keystore password: " KRATKOZA_UPLOAD_STORE_PASSWORD
        echo
    fi
    [ -n "${KRATKOZA_UPLOAD_STORE_PASSWORD:-}" ] || fail "no keystore password given"

    if [ -t 0 ]; then
        read -r -s -p "Key password [Enter = keystore password]: " entered
        echo
        KRATKOZA_UPLOAD_KEY_PASSWORD="${entered:-$KRATKOZA_UPLOAD_STORE_PASSWORD}"
    fi
    KRATKOZA_UPLOAD_KEY_PASSWORD="${KRATKOZA_UPLOAD_KEY_PASSWORD:-$KRATKOZA_UPLOAD_STORE_PASSWORD}"

    # Gradle reads a project property of its own name from ORG_GRADLE_PROJECT_<name>, so the
    # signingConfig in app/build.gradle.kts picks these up without any of them touching a file.
    export ORG_GRADLE_PROJECT_PRZUNK_UPLOAD_STORE_FILE="$KRATKOZA_UPLOAD_KEYSTORE"
    export ORG_GRADLE_PROJECT_PRZUNK_UPLOAD_KEY_ALIAS="$KRATKOZA_UPLOAD_KEY_ALIAS"
    export ORG_GRADLE_PROJECT_PRZUNK_UPLOAD_STORE_PASSWORD="$KRATKOZA_UPLOAD_STORE_PASSWORD"
    export ORG_GRADLE_PROJECT_PRZUNK_UPLOAD_KEY_PASSWORD="$KRATKOZA_UPLOAD_KEY_PASSWORD"
}

version_name="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
version_code="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n 1)"
artifact="$DIST_DIR/kratkoza-${version_name:-unknown}-${version_code:-0}.aab"

echo "🔨 Kratkoza — Play bundle ${version_name:-?} (${version_code:-?})"

# Play rejects a versionCode it has already seen. This file being here means the code was built
# before — a rebuild is fine, a forgotten bump is not, and only the author can tell which.
if [ -f "$artifact" ]; then
    echo "ℹ️  A bundle for versionCode ${version_code} already exists and will be replaced."
    echo "   If that code has been uploaded, bump it in app/build.gradle.kts first."
fi

collect_signing_configuration
echo "🔑 $KRATKOZA_UPLOAD_KEYSTORE (alias ${KRATKOZA_UPLOAD_KEY_ALIAS}) — passwords not stored"
echo

started_at=$(date +%s)
if ! ./gradlew :app:bundleRelease --no-daemon --console=plain "$@" > "$LOG_FILE" 2>&1; then
    echo "❌ Build failed. What went wrong:"
    if grep -aq '^\* What went wrong:' "$LOG_FILE"; then
        sed -n '/^\* What went wrong:/,/^\* Try:/p' "$LOG_FILE" \
            | grep -av '^\* Try:' | sed "s|$KRATKOZA_DIR/||" | head -n 25
    fi
    grep -a '^e: ' "$LOG_FILE" | sed "s|file://$KRATKOZA_DIR/||" | head -n 25
    echo
    echo "Full log: $LOG_FILE"
    exit 1
fi

[ -f "$BUILD_AAB" ] || fail "build reported success but produced no bundle at $BUILD_AAB"

# A bundle carries a JAR signature, not an APK one, so apksigner cannot read it.
JARSIGNER="$JAVA_HOME/bin/jarsigner"
[ -x "$JARSIGNER" ] || fail "jarsigner is unavailable in $JAVA_HOME"

if ! "$JARSIGNER" -J-Duser.language=en -verify "$BUILD_AAB" > "$LOG_FILE" 2>&1 ||
    ! grep -Fq "jar verified." "$LOG_FILE"; then
    fail "the bundle carries no verifiable signature" "Full log: $LOG_FILE"
fi

signer="$("$JAVA_HOME/bin/keytool" -printcert -jarfile "$BUILD_AAB" 2>/dev/null \
    | grep -m1 'Owner:' || true)"
[ -n "$signer" ] || fail "no certificate found in the bundle"

# The hard rule, and the reason this script is not build-kratkoza-release.sh with a different task.
# For an APK the debug fallback earns its place: an unsigned APK cannot be sideloaded at all. For a
# bundle headed to Play it buys nothing but a rejected upload discovered later instead of now.
if echo "$signer" | grep -qi 'CN=Android Debug'; then
    fail "DEBUG KEY — Play will reject this bundle." \
        "$signer" \
        "" \
        "Gradle fell back to the debug key, which means it did not see the credentials." \
        "Check the alias exists in that keystore: keytool -list -keystore <path>"
fi

mkdir -p "$DIST_DIR"
cp "$BUILD_AAB" "$artifact"

echo "🔎 $signer"
echo "✅ OK in $(($(date +%s) - started_at))s"
echo "📦 $artifact ($(du -h "$artifact" | cut -f1))"
command -v sha256sum >/dev/null 2>&1 && sha256sum "$artifact"
echo
echo "Upload at https://play.google.com/console → Kratkoza → Testing → Closed testing."
