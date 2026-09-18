#!/usr/bin/env bash
#
# Runs the unit tests and prints a short report.
#
# On failure it lists the failing tests with their assertion messages, read out of the JUnit XML,
# rather than dumping the Gradle log. A failing run should be readable in a dozen lines.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/kratkoza-env.sh"
cd "$KRATKOZA_DIR"

LOG_FILE="$KRATKOZA_LOG_PREFIX-test.log"
RESULTS_DIR="$KRATKOZA_DIR/app/build/test-results/testDebugUnitTest"

started_at=$(date +%s)
echo "🧪 Kratkoza — unit tests…"

set +e
./gradlew :app:testDebugUnitTest --no-daemon --console=plain "$@" > "$LOG_FILE" 2>&1
status=$?
set -e

elapsed=$(($(date +%s) - started_at))

# A compile error produces no XML at all, so it has to be reported from the log.
if grep -aq '^e: ' "$LOG_FILE"; then
    echo "❌ Compile errors (${elapsed}s):"
    grep -a '^e: ' "$LOG_FILE" | sed 's|file:///mnt/workspace/Kratkoza/||' | head -n 30
    exit 1
fi

summary="$(grep -aoE '[0-9]+ tests completed(, [0-9]+ failed)?(, [0-9]+ skipped)?' "$LOG_FILE" | tail -n 1 || true)"

if [ $status -eq 0 ]; then
    # Gradle only prints a count when something failed, so a green run counts the XML itself.
    total="$(python3 -c 'import glob,sys,xml.etree.ElementTree as ET; print(sum(int(ET.parse(f).getroot().get("tests",0)) for f in glob.glob(sys.argv[1]+"/*.xml")))' "$RESULTS_DIR" 2>/dev/null || true)"

    # Gradle exiting zero having run nothing is not a pass. It happens when the results directory
    # is stale or the task is skipped, and it used to print a green tick over the number zero —
    # the loudest possible way of saying nothing, and the failure ARCHITECTURE.md section 9 is about.
    if [ -z "$total" ] || [ "$total" -eq 0 ]; then
        echo "❌ No tests ran (${elapsed}s). Gradle succeeded but produced no results."
        echo "   Usually a stale build directory: rm -rf app/build and run again."
        echo "   Full log: $LOG_FILE"
        exit 1
    fi

    echo "✅ $total tests passed (${elapsed}s)"
    "$SCRIPT_DIR/check-store-metadata.sh"
    exit 0
fi

echo "❌ ${summary:-Tests failed} (${elapsed}s)"
echo
python3 - "$RESULTS_DIR" <<'PY'
import glob, sys, xml.etree.ElementTree as ET

for path in sorted(glob.glob(sys.argv[1] + "/*.xml")):
    for case in ET.parse(path).getroot().iter("testcase"):
        for failure in list(case.iter("failure")) + list(case.iter("error")):
            suite = case.get("classname", "").rsplit(".", 1)[-1]
            message = (failure.get("message") or "").strip().splitlines()
            print(f"  {suite} > {case.get('name')}")
            print(f"    {message[0] if message else failure.get('type', '')}")

            # The first frame inside the project is what the reader needs; the rest is JUnit.
            for line in (failure.text or "").splitlines():
                if "com.przunk.kratkoza" in line and ".kt:" in line:
                    print(f"    {line.strip()}")
                    break
PY

echo
echo "Full log: $LOG_FILE"
exit 1
