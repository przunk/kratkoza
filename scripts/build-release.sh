#!/usr/bin/env bash
#
# Builds a release APK into dist/ and reports which key actually signed it.
#
# Without the upload key configured, Gradle falls back to the local debug key (see README). That
# keeps sideloading alive -- an unsigned APK cannot be installed at all -- but such an APK must
# never be published, so which key was used is printed rather than assumed. The APK handed over for
# testing on the phone is this one: minified, the way users will get it.
#
# Usage:  ./scripts/build-release.sh [extra gradle args…]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./use-tooling.sh
source "$SCRIPT_DIR/use-tooling.sh"
cd "$PROJECT_DIR"
# shellcheck source=./internal/build-common.sh
source "$SCRIPT_DIR/internal/build-common.sh"

LOG_FILE="$LOG_PREFIX-release.log"
BUILD_APK="$MODULE_BUILD_DIR/outputs/apk/release/$APP_MODULE-release.apk"

require_toolchain
write_local_properties

started_at=$(date +%s)
echo "🔨 $APP_NAME — release APK…"

if ! ./gradlew ":$APP_MODULE:assembleRelease" --no-daemon --console=plain "$@" > "$LOG_FILE" 2>&1; then
    report_failure "$LOG_FILE"
fi

artifact="$(publish_artifact "$BUILD_APK" release)"

echo
echo "🔎 Signature"
APKSIGNER="$(ls -d "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1 || true)"
if [ -n "$APKSIGNER" ]; then
    # The certificate's subject is the only trustworthy answer to "which key is this?" -- the Gradle
    # config can look right and still have fallen back.
    certs="$("$APKSIGNER" verify --print-certs "$artifact" 2>/dev/null || true)"
    signer_line="$(grep -m1 'certificate DN' <<< "$certs" || true)"
    # Play Games signs a player in only for an applicationId and SHA-1 pair it has a credential for,
    # so the SHA-1 is printed every time: it is the first thing to compare when sign-in fails.
    signer_sha1="$(sed -n 's/.*certificate SHA-1 digest: //p' <<< "$certs" | head -n 1)"
    if [ -z "$signer_line" ]; then
        echo "   ⚠️  Could not read the certificate. The APK may be unsigned."
    elif echo "$signer_line" | grep -qi 'CN=Android Debug'; then
        echo "   ⚠️  DEBUG KEY — sideloadable, NOT publishable."
        echo "       $signer_line"
        echo "       SHA-1: ${signer_sha1:-?}"
    else
        echo "   ✅ Release key."
        echo "       $signer_line"
        echo "       SHA-1: ${signer_sha1:-?}"
    fi
else
    echo "   ⚠️  apksigner not found under $ANDROID_HOME/build-tools; signature not verified."
fi

echo
echo "✅ OK in $(($(date +%s) - started_at))s"
echo "📦 $artifact ($(du -h "$artifact" | cut -f1))"
