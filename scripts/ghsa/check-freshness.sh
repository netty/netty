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
# Checks whether the GHSA-*.json files extracted by scripts/collect-ghsa-reports.sh
# are recent enough to deduplicate. Only the dedupe input directories are checked.
#
# Exit codes: 0 = fresh, 1 = stale (some file is older than the limit), 2 = no files.
# The limit is GHSA_MAX_AGE_MINUTES (default 240).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MAX_AGE_MINUTES="${GHSA_MAX_AGE_MINUTES:-240}"

dirs=()
for state in triage draft published; do
    [ -d "$ROOT/.github/ghsa/$state" ] && dirs+=("$ROOT/.github/ghsa/$state")
done

if [ ${#dirs[@]} -eq 0 ] || [ -z "$(find "${dirs[@]}" -name 'GHSA-*.json' -print -quit)" ]; then
    echo "MISSING: no GHSA-*.json files under .github/ghsa/{triage,draft,published}"
    exit 2
fi

stale="$(find "${dirs[@]}" -name 'GHSA-*.json' -mmin "+$MAX_AGE_MINUTES" -print -quit)"
if [ -n "$stale" ]; then
    echo "STALE: ${stale#"$ROOT"/} is older than $MAX_AGE_MINUTES minutes"
    exit 1
fi

echo "FRESH: all GHSA-*.json files are newer than $MAX_AGE_MINUTES minutes"
