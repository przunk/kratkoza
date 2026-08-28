#!/usr/bin/env bash
#
# Pushes to GitHub, asking for the token when it runs and keeping it nowhere.
#
# Same bargain as build-kratkoza-bundle.sh: the secret is typed, used, and gone when the process
# ends. It is never written to .git/config, never baked into the remote URL (where `git remote -v`
# and every error message would show it), and never left in a credential store.
#
# Non-interactive use reads KRATKOZA_GITHUB_USER and KRATKOZA_GITHUB_TOKEN from the environment.
#
# Usage:  ./scripts/push-kratkoza.sh [branch…]      (default: the current branch)
#
# KRATKOZA_FORCE_PUSH=1 overwrites what is on the remote, for the one case that needs it: history
# that was deliberately rewritten locally. It uses --force-with-lease, which refuses if the remote
# has moved since the last fetch — so it can overwrite your own rewrite, but not somebody else's
# work that arrived in the meantime.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

REMOTE="${KRATKOZA_GIT_REMOTE:-origin}"
branches=("$@")
if [ ${#branches[@]} -eq 0 ]; then
    branches=("$(git rev-parse --abbrev-ref HEAD)")
fi

git remote get-url "$REMOTE" >/dev/null 2>&1 ||
    { echo "❌ No remote '$REMOTE'. Add it with: git remote add $REMOTE <url>"; exit 1; }

push_flags=(--set-upstream)
if [ "${KRATKOZA_FORCE_PUSH:-0}" = "1" ]; then
    push_flags+=(--force-with-lease)
fi

echo "🌐 $(git remote get-url "$REMOTE")"
echo "   branches: ${branches[*]}"
[ "${KRATKOZA_FORCE_PUSH:-0}" = "1" ] && echo "   ⚠️  force (--force-with-lease): the remote history will be replaced"

if [ -t 0 ]; then
    current="${KRATKOZA_GITHUB_USER:-przunk}"
    read -r -p "GitHub user [$current]: " entered
    KRATKOZA_GITHUB_USER="${entered:-$current}"
    read -r -s -p "Token (personal access token, not your password): " KRATKOZA_GITHUB_TOKEN
    echo
fi

[ -n "${KRATKOZA_GITHUB_USER:-}" ] || { echo "❌ no GitHub user given"; exit 1; }
[ -n "${KRATKOZA_GITHUB_TOKEN:-}" ] || { echo "❌ no token given"; exit 1; }

export KRATKOZA_GITHUB_USER KRATKOZA_GITHUB_TOKEN
export GIT_ASKPASS="$SCRIPT_DIR/internal/git-askpass.sh"
# Without this git would still try the terminal first on some setups, defeating the point.
export GIT_TERMINAL_PROMPT=0

echo
if git push "${push_flags[@]}" "$REMOTE" "${branches[@]}"; then
    echo
    echo "✅ Pushed ${branches[*]} to $REMOTE"
else
    echo
    echo "❌ Push failed."
    echo "   A 403 usually means the token lacks write access to this repository."
    echo "   Classic token: needs the 'repo' scope."
    echo "   Fine-grained:  needs Contents → Read and write on przunk/kratkoza."
    echo "   A rejected non-fast-forward means the history was rewritten:"
    echo "     KRATKOZA_FORCE_PUSH=1 ./scripts/push-kratkoza.sh ${branches[*]}"
    exit 1
fi
