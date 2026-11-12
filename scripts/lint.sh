#!/usr/bin/env bash
#
# Runs Android Lint and prints only what it found.
#
# The build is configured with `warningsAsErrors`, so a lint warning fails the build; this script
# exists to see *what* failed without opening an HTML report.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./use-tooling.sh
source "$SCRIPT_DIR/use-tooling.sh"
cd "$PROJECT_DIR"
# shellcheck source=./internal/build-common.sh
source "$SCRIPT_DIR/internal/build-common.sh"
write_local_properties

LOG_FILE="$LOG_PREFIX-lint.log"
started_at=$(date +%s)
echo "🔍 $APP_NAME — lint…"

set +e
./gradlew ":$APP_MODULE:lintDebug" --no-daemon --console=plain "$@" > "$LOG_FILE" 2>&1
status=$?
set -e

elapsed=$(($(date +%s) - started_at))

if [ $status -eq 0 ]; then
    echo "✅ Lint clean (${elapsed}s)"
    exit 0
fi

echo "❌ Lint failed (${elapsed}s):"
# The task can also fail before lint runs at all — a resource that will not link, a Kotlin error.
# Those are reported by Gradle, not by lint, and are what the reader needs to see first.
if grep -aq '^\* What went wrong:' "$LOG_FILE"; then
    sed -n '/^\* What went wrong:/,/^\* Try:/p' "$LOG_FILE" \
        | grep -av '^\* Try:' | sed "s|$PROJECT_DIR/||" | head -n 25
fi
# Lint reports as "path[:line]: Error: message [IssueId]" — the line number is absent when the
# finding is about a whole directory, so it must not be required here.
grep -aE '^[^ ]+: (Error|Warning):' "$LOG_FILE" \
    | sed "s|$PROJECT_DIR/||" | sort -u | head -n 40
grep -aE '^[0-9]+ errors?, [0-9]+ warnings?' "$LOG_FILE" | tail -n 1
echo
echo "Full log: $LOG_FILE"
exit 1
