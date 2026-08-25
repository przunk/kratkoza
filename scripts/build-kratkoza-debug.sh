#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/kratkoza-env.sh"
cd "$KRATKOZA_DIR"

LOG_FILE="/tmp/kratkoza-build.log"
echo "🔨 Kratkoza — debug APK…"

if ./gradlew :app:assembleDebug --no-daemon --console=plain "$@" > "$LOG_FILE" 2>&1; then
    APK_PATH="$(ls -t "$KRATKOZA_DIR"/app/build/outputs/apk/debug/*.apk 2>/dev/null | head -n 1 || true)"
    echo "✅ OK"
    echo "📦 ${APK_PATH:-<no apk found>}"
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
