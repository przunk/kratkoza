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
    echo "❌ Build failed. Last lines:"
    tail -n 40 "$LOG_FILE"
    exit 1
fi
