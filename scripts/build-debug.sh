#!/usr/bin/env bash
#
# Builds the debug APK into dist/.
#
# Gradle's output goes to a log file, not to the terminal. On success you get a few lines; on
# failure, the part of the log that says what broke and the path to the rest.
#
# Usage:  ./scripts/build-debug.sh [extra gradle args…]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./use-tooling.sh
source "$SCRIPT_DIR/use-tooling.sh"
cd "$PROJECT_DIR"
# shellcheck source=./internal/build-common.sh
source "$SCRIPT_DIR/internal/build-common.sh"

LOG_FILE="$LOG_PREFIX-debug.log"
BUILD_APK="$MODULE_BUILD_DIR/outputs/apk/debug/$APP_MODULE-debug.apk"

require_toolchain
write_local_properties

started_at=$(date +%s)
echo "🔨 $APP_NAME — debug APK…"

if ! ./gradlew ":$APP_MODULE:assembleDebug" --no-daemon --console=plain "$@" > "$LOG_FILE" 2>&1; then
    report_failure "$LOG_FILE"
fi

artifact="$(publish_artifact "$BUILD_APK" debug)"

echo "✅ OK in $(($(date +%s) - started_at))s"
echo "📦 $artifact ($(du -h "$artifact" | cut -f1))"
