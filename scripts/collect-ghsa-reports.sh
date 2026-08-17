#!/usr/bin/env bash
set -euo pipefail

GHSA_DIR="$PWD/.github/ghsa"
if [ ! -d "$GHSA_DIR" ]; then
    echo "Error: $GHSA_DIR is not a directory -- this script must be run from the project root directory"
    exit 1
fi
if ! command -v "jq" &> /dev/null; then
    echo "Error: jq is not installed"
    exit 1
fi
if ! command -v "gh" &> /dev/null; then
    echo "Error: gh is not installed"
    exit 1
fi

find "$GHSA_DIR" -name '*.json' -delete

gh api repos/netty/netty/security-advisories --paginate | jq -rc '.[]' | while read -r report ; do
    ghsa=$(jq -r '.ghsa_id' <<< "$report")
    state=$(jq -r '.state' <<< "$report")

    if [[ ! "$ghsa" =~ ^GHSA-[a-zA-Z0-9_-]+$ ]]; then
        echo "Warning: skipping entry, unexpected ghsa_id value '$ghsa'" >&2
        continue
    fi
    if [[ ! "$state" =~ ^[a-zA-Z0-9_-]+$ ]]; then
        echo "Warning: skipping $ghsa, unexpected state value '$state'" >&2
        continue
    fi

    echo "==== $ghsa ===="
    mkdir -p "$GHSA_DIR/$state"
    jq <<< "$report" > "$GHSA_DIR/$state/$ghsa.json"
done
echo "Done."
