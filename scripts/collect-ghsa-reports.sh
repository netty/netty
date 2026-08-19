#!/usr/bin/env bash
# ----------------------------------------------------------------------------
# Copyright 2026 The Netty Project
#
# The Netty Project licenses this file to you under the Apache License,
# version 2.0 (the "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at:
#
#   https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------
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
