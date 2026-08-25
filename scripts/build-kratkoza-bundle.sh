#!/usr/bin/env bash
#
# Builds an Android App Bundle for Google Play and refuses to hand over one Play would reject.
#
# Signing credentials never live in this repository. Put them in ~/.gradle/gradle.properties:
#
#   PRZUNK_UPLOAD_STORE_FILE=/absolute/path/to/kratkoza-upload.jks
#   PRZUNK_UPLOAD_STORE_PASSWORD=…
#   PRZUNK_UPLOAD_KEY_ALIAS=…
#   PRZUNK_UPLOAD_KEY_PASSWORD=…
#
# Unlike build-kratkoza-release.sh, this script *fails* when the artifact turns out to be signed
# with the debug key. For an APK the debug fallback is useful — an unsigned APK cannot be sideloaded
# at all. For a bundle headed to Play it is useless: the upload is rejected, and the only thing the
# fallback buys is finding out later rather than now.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/kratkoza-env.sh"
cd "$KRATKOZA_DIR"

DIST_DIR="$KRATKOZA_DIR/dist"
LOG_FILE="/tmp/kratkoza-bundle.log"
BUILD_AAB="$KRATKOZA_DIR/app/build/outputs/bundle/release/app-release.aab"

started_at=$(date +%s)

version_name="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
version_code="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n 1)"
artifact="$DIST_DIR/kratkoza-${version_name:-unknown}-${version_code:-0}.aab"

echo "🔨 Kratkoza — Play bundle ${version_name:-?} (${version_code:-?})…"

# Play rejects a versionCode it has already seen. The file being here means this code was built
# before — a rebuild is fine, a forgotten bump is not, and only the author knows which.
if [ -f "$artifact" ]; then
    echo "ℹ️  A bundle for versionCode ${version_code} already exists and will be replaced."
    echo "   If that code has been uploaded, bump it in app/build.gradle.kts first."
fi

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

if [ ! -f "$BUILD_AAB" ]; then
    echo "❌ Build reported success but produced no bundle at $BUILD_AAB"
    exit 1
fi

# A bundle is JAR-signed, not APK-signed, so apksigner cannot read it. keytool can.
echo
echo "🔎 Signature"
signer=""
if [ -x "$JAVA_HOME/bin/keytool" ]; then
    signer="$("$JAVA_HOME/bin/keytool" -printcert -jarfile "$BUILD_AAB" 2>/dev/null \
        | grep -m1 'Owner:' || true)"
fi

if [ -z "$signer" ]; then
    echo "   ❌ No certificate found. Play will not accept an unsigned bundle."
    exit 1
fi

if echo "$signer" | grep -qi 'CN=Android Debug'; then
    echo "   ❌ DEBUG KEY — Play will reject this bundle."
    echo "       $signer"
    echo
    echo "   Create an upload key once:"
    echo "     \$JAVA_HOME/bin/keytool -genkeypair -v \\"
    echo "       -keystore ~/keys/kratkoza-upload.jks -storetype JKS \\"
    echo "       -keyalg RSA -keysize 4096 -validity 10000 -alias kratkoza"
    echo
    echo "   Then add PRZUNK_UPLOAD_* to ~/.gradle/gradle.properties (see the header of this file)."
    echo
    # Deliberately not copied into dist/: an artifact that cannot be uploaded should not be sitting
    # in the folder the uploadable ones live in.
    if [ "${KRATKOZA_ALLOW_DEBUG_BUNDLE:-0}" = "1" ]; then
        debug_artifact="$DIST_DIR/kratkoza-${version_name}-${version_code}-DEBUGKEY-DO-NOT-UPLOAD.aab"
        mkdir -p "$DIST_DIR"
        cp "$BUILD_AAB" "$debug_artifact"
        echo "   ⚠️  KRATKOZA_ALLOW_DEBUG_BUNDLE=1 — kept for pipeline testing only:"
        echo "   📦 $debug_artifact ($(du -h "$debug_artifact" | cut -f1))"
    fi
    exit 1
fi

mkdir -p "$DIST_DIR"
cp "$BUILD_AAB" "$artifact"

echo "   ✅ Upload key."
echo "       $signer"
echo
echo "✅ OK in $(($(date +%s) - started_at))s"
echo "📦 $artifact ($(du -h "$artifact" | cut -f1))"
echo
echo "Upload at https://play.google.com/console → Kratkoza → Testing → Closed testing."
