#!/usr/bin/env bash
#
# The full suite: unit tests, string resources, the store listing, and whatever the project adds
# through its extra_checks hook (project.env). Gradle's output goes to a log; the terminal gets the
# result and, on failure, what failed and why.
#
# Pass --really to force the tests to execute rather than be served from Gradle's cache. Worth it
# before claiming a suite is green on code nobody has run it against.
#
# Usage:  ./scripts/test.sh [--really] [extra gradle args…]
#         ./scripts/test.sh --tests '*SudokuGeneratorTest*'
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./use-tooling.sh
source "$SCRIPT_DIR/use-tooling.sh"
cd "$PROJECT_DIR"
# shellcheck source=./internal/build-common.sh
source "$SCRIPT_DIR/internal/build-common.sh"

LOG_FILE="$LOG_PREFIX-test.log"
TASK=":$APP_MODULE:testDebugUnitTest"
write_local_properties

force=""
if [ "${1:-}" = "--really" ]; then
    force="--rerun-tasks"
    shift
fi

started_at=$(date +%s)
echo "🧪 $APP_NAME — unit tests…"

set +e
./gradlew "$TASK" --no-daemon --console=plain $force "$@" > "$LOG_FILE" 2>&1
status=$?
set -e

# Counted from the JUnit XML rather than from the log. "BUILD SUCCESSFUL" with zero tests run is a
# failure wearing a success message, and it has fooled this workshop before.
#
# Deliberately not filtered by modification time: Gradle skips the task when nothing changed, and
# fresh-file filtering would then report zero tests on every second run. Whether the results were
# produced just now is reported separately below.
RESULTS_DIR="$MODULE_BUILD_DIR/test-results/testDebugUnitTest"
totals="$(find "$RESULTS_DIR" -name 'TEST-*.xml' \
    -exec grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' {} \; 2>/dev/null \
    | awk -F'"' '{a[$1]+=$2} END {printf "%d %d %d %d", a["tests="], a["failures="], a["errors="], a["skipped="]}')"
read -r total failures errors skipped <<< "${totals:-0 0 0 0}"

# Two ways Gradle reports a green suite it did not run: UP-TO-DATE (nothing changed) and FROM-CACHE
# (the answer came out of the build cache). Both print BUILD SUCCESSFUL and neither runs a test.
cached=""
if grep -aq "^> Task $TASK UP-TO-DATE" "$LOG_FILE"; then
    cached=" (up to date, not re-run)"
elif grep -aq "^> Task $TASK FROM-CACHE" "$LOG_FILE"; then
    cached=" (from the build cache, not re-run)"
fi

# A compile error is not a test failure, and stale counts from the previous run's XML next to it
# would say "29 passed" about code that did not build.
if [ "$status" -ne 0 ] && grep -aq '^e: ' "$LOG_FILE"; then
    echo "❌ Did not compile:"
    { grep -a '^e: ' "$LOG_FILE" || true; } | sed "s|file://$PROJECT_DIR/||" | sed 's/^/   /' | head -n 20
    echo
    echo "Full log: $LOG_FILE"
    exit 1
fi

if [ "$status" -ne 0 ]; then
    echo "❌ Tests failed:"
    # Read out of the JUnit XML rather than the Gradle log: each failing test with its assertion
    # message and the first frame inside the project, which is what the reader needs.
    python3 - "$RESULTS_DIR" <<'PY' || true
import glob, sys, xml.etree.ElementTree as ET

for path in sorted(glob.glob(sys.argv[1] + "/*.xml")):
    for case in ET.parse(path).getroot().iter("testcase"):
        for failure in list(case.iter("failure")) + list(case.iter("error")):
            suite = case.get("classname", "").rsplit(".", 1)[-1]
            message = (failure.get("message") or "").strip().splitlines()
            print(f"   {suite} > {case.get('name')}")
            print(f"     {message[0] if message else failure.get('type', '')}")
            for line in (failure.text or "").splitlines():
                if "com.przunk" in line and ".kt:" in line:
                    print(f"     {line.strip()}")
                    break
PY
    echo
    echo "   $total run, $failures failed, $errors errored, $skipped skipped"
    echo "Full log: $LOG_FILE"
    echo "Report:   $MODULE_BUILD_DIR/reports/tests/testDebugUnitTest/index.html"
    exit 1
fi

if [ "$total" -eq 0 ]; then
    echo "❌ Gradle reported success but no test results exist. That is a failure, not a pass."
    echo "   Looked in: $RESULTS_DIR"
    echo "Full log: $LOG_FILE"
    exit 1
fi

# --- string resources ----------------------------------------------------------------------------
#
# Two things the compiler accepts and a phone does not.
#
# 1. Flags written after the argument index -- "%,1$d" instead of "%1$,d". That is not a format
#    specifier, and String.format throws the moment the string is shown.
# 2. A string present in the default language and missing from a translation.
RES="$APP_MODULE/src/main/res"
if [ -f "$RES/values/strings.xml" ]; then
    if grep -qE '%[,+#0 -]+[0-9]+\$' "$RES"/values*/strings.xml; then
        echo "❌ Malformed format specifiers in string resources (flags before the argument index):"
        grep -nE '%[,+#0 -]+[0-9]+\$' "$RES"/values*/strings.xml | sed 's/^/   /' | head -n 10
        exit 1
    fi

    names() { grep -ohE '<(string|plurals) name="[^"]+"' "$1" | grep -oE '"[^"]+"' | tr -d '"' | sort -u; }
    for translated in "$RES"/values-*/strings.xml; do
        [ -e "$translated" ] || continue
        missing="$(comm -23 <(names "$RES/values/strings.xml") <(names "$translated"))"
        if [ -n "$missing" ]; then
            echo "❌ Strings missing from ${translated#"$PROJECT_DIR/"}:"
            echo "$missing" | sed 's/^/   /' | head -n 20
            exit 1
        fi
    done
fi

# --- the store listing, when the project has one ---------------------------------------------------
if [ -d store/listing ]; then
    "$SCRIPT_DIR/check-store-metadata.sh"
fi

# --- the project's own ---------------------------------------------------------------------------
if declare -F extra_checks >/dev/null; then
    extra_checks || { echo "❌ The project's extra checks failed (extra_checks in scripts/project.env)."; exit 1; }
fi

echo "✅ $total tests passed in $(($(date +%s) - started_at))s$cached"
