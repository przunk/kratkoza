#!/usr/bin/env bash
#
# Builds a signed release APK of Kratkoza and reports which key actually signed it.
#
# Signing credentials are never kept in this repository. Put them in ~/.gradle/gradle.properties:
#
#   PRZUNK_UPLOAD_STORE_FILE=/absolute/path/to/kratkoza-release.jks
#   PRZUNK_UPLOAD_STORE_PASSWORD=…
#   PRZUNK_UPLOAD_KEY_ALIAS=…
#   PRZUNK_UPLOAD_KEY_PASSWORD=…
#
# Without them the build still succeeds, signed with the local debug key. That keeps the sideload
# workflow alive on a machine that has no release key — an unsigned APK cannot be installed at all —
# but such an APK must never be published. Which key was used is printed at the end rather than
# assumed, because the mistake is otherwise invisible until an upload is rejected.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/kratkoza-env.sh"
cd "$KRATKOZA_DIR"

DIST_DIR="$KRATKOZA_DIR/dist"
LOG_FILE="$KRATKOZA_LOG_PREFIX-release.log"
BUILD_APK="$KRATKOZA_DIR/app/build/outputs/apk/release/app-release.apk"

started_at=$(date +%s)

echo "🔨 Kratkoza — release APK…"
if ! ./gradlew :app:assembleRelease --no-daemon --console=plain "$@" > "$LOG_FILE" 2>&1; then
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

if [ ! -f "$BUILD_APK" ]; then
    echo "❌ Build reported success but produced no APK at $BUILD_APK"
    exit 1
fi

mkdir -p "$DIST_DIR"
# versionCode as well as versionName, the way build-opowio-release.sh names its artifacts: two
# builds of the same versionName are otherwise indistinguishable once they leave dist/.
version_name="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
# The declaration interpolates a branch suffix at build time; the literal read out of the file still
# carries the reference, and a filename is no place for it.
version_name="${version_name%%\$*}"
version_code="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n 1)"
stamp="$(date +%Y%m%d-%H%M%S)"
artifact="$DIST_DIR/kratkoza-${version_name:-unknown}-${version_code:-0}-$stamp.apk"
cp "$BUILD_APK" "$artifact"

echo
echo "🔎 Signature"
APKSIGNER="$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1 || true)"
if [ -n "$APKSIGNER" ]; then
    # The certificate's subject is the only trustworthy answer to "which key is this?" — the
    # Gradle config can look right and still have fallen back.
    signer_line="$("$APKSIGNER" verify --print-certs "$artifact" 2>/dev/null | grep -m1 'certificate DN' || true)"
    if [ -z "$signer_line" ]; then
        echo "   ⚠️  Could not read the certificate. The APK may be unsigned."
    elif echo "$signer_line" | grep -qi 'CN=Android Debug'; then
        # Print what Play Games authenticates, without claiming whether the console contains the
        # matching credential — that configuration is outside the repository.
        sha1="$("$APKSIGNER" verify --print-certs "$artifact" 2>/dev/null \
            | grep -m1 -i 'SHA-1 digest' | sed 's/.*: *//')"
        echo "   ⚠️  DEBUG KEY — installable by sideload, NOT publishable."
        echo "       $signer_line"
        echo "       SHA-1: $sha1"
        echo "       Play Games credential must match this SHA-1 and com.przunk.kratkoza."
    else
        echo "   ✅ Release key."
        echo "       $signer_line"
    fi
else
    echo "   ⚠️  apksigner not found under $ANDROID_HOME/build-tools; signature not verified."
fi

echo
echo "✅ OK in $(($(date +%s) - started_at))s"
echo "📦 $artifact ($(du -h "$artifact" 2>/dev/null | cut -f1 || echo "?"))"
