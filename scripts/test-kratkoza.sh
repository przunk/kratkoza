#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/kratkoza-env.sh"
cd "$KRATKOZA_DIR"

LOG_FILE="/tmp/kratkoza-test.log"
echo "🧪 Kratkoza — unit tests…"

if ./gradlew :app:testDebugUnitTest --no-daemon --console=plain "$@" > "$LOG_FILE" 2>&1; then
    echo "✅ Tests passed"
else
    echo "❌ Tests failed. Last lines:"
    tail -n 60 "$LOG_FILE"
    exit 1
fi
