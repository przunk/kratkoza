#!/usr/bin/env bash
#
# Checks the Play listing fields that can be validated without Play Console. The fields have hard
# character limits and every locale has to hold the same files; both are checkable here and neither
# is checkable in Play Console until an upload is refused.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
# shellcheck source=./project.env
source "$SCRIPT_DIR/project.env"

failed=0

character_count() {
    local value
    value="$(<"$1")"
    LC_ALL=C.UTF-8 printf '%s' "$value" | LC_ALL=C.UTF-8 wc -m
}

check_field() {
    local path="$1" limit="$2" label="$3" count
    if [ ! -s "$path" ]; then
        echo "❌ Missing or empty store field: ${path#"$PROJECT_DIR/"}"
        failed=1
        return
    fi
    count="$(character_count "$path")"
    if [ "$count" -gt "$limit" ]; then
        echo "❌ $label is $count characters; Google Play allows $limit: ${path#"$PROJECT_DIR/"}"
        failed=1
    fi
}

read -r -a locales <<< "$STORE_LOCALES"
for locale in "${locales[@]}"; do
    listing="$PROJECT_DIR/store/listing/$locale"
    check_field "$listing/title.txt" 30 "$locale title"
    check_field "$listing/short-description.txt" 80 "$locale short description"
    check_field "$listing/full-description.txt" 4000 "$locale full description"

    # Every release-note file, not one named here: checking a file by name means that renaming it
    # silently stops checking anything.
    notes=0
    for note in "$listing/release-notes/"*.txt; do
        [ -e "$note" ] || continue
        check_field "$note" 500 "$locale release notes ($(basename "$note"))"
        notes=$((notes + 1))
    done
    if [ "$notes" -eq 0 ]; then
        echo "❌ No release notes at all for $locale: store/listing/$locale/release-notes/"
        failed=1
    fi
done

# Every locale must hold the same files as the first. A text that exists in one language and not in
# another makes Play show the default language's instead, silently.
first="${locales[0]}"
first_files="$(cd "$PROJECT_DIR/store/listing/$first" 2>/dev/null && find . -name '*.txt' | sort)"
for locale in "${locales[@]:1}"; do
    files="$(cd "$PROJECT_DIR/store/listing/$locale" 2>/dev/null && find . -name '*.txt' | sort)"
    if [ "$first_files" != "$files" ]; then
        echo "❌ $first and $locale do not hold the same files:"
        diff <(echo "$first_files") <(echo "$files") | sed 's/^/   /' || true
        failed=1
    fi
done

[ "$failed" -eq 0 ] || exit 1
echo "✅ Store metadata fields fit Google Play limits"
