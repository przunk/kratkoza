#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/kratkoza-env.sh"
cd "$KRATKOZA_DIR"

LOG_FILE="$KRATKOZA_LOG_PREFIX-build.log"
echo "🔨 Kratkoza — debug APK…"

if ./gradlew :app:assembleDebug --no-daemon --console=plain "$@" > "$LOG_FILE" 2>&1; then
    APK_PATH="$(ls -t "$KRATKOZA_DIR"/app/build/outputs/apk/debug/*.apk 2>/dev/null | head -n 1 || true)"
    echo "✅ OK"

    # Copied into ./dist, where the release builds already land, because that is the directory
    # reachable from outside WSL — an APK left under app/build has to be dug out by hand.
    if [ -n "$APK_PATH" ]; then
        version_name="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -n 1)"
        version_name="${version_name%%\$*}"
        version_code="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -n 1)"
        mkdir -p "$KRATKOZA_DIR/dist"
        DIST_APK="$KRATKOZA_DIR/dist/kratkoza-debug-${version_name}-${version_code}-$(date +%Y%m%d-%H%M%S).apk"
        cp "$APK_PATH" "$DIST_APK"
        echo "📦 $DIST_APK"
    else
        echo "📦 <no apk found>"
    fi
else
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
