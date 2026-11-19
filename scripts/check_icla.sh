#!/bin/bash
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
#
# Checks whether a GitHub user has signed the Netty ICLA by looking them up in
# the ICLA spreadsheet (column "Github user name").
#
# Usage:
#   scripts/check_icla.sh <github-user | PR number | PR URL> [...]
#
# A PR number or URL is resolved to the PR author via the GitHub CLI (gh); a PR
# number refers to netty/netty unless GITHUB_REPOSITORY is set.
#
# The sheet is fetched as CSV. If it is not publicly readable, the script falls
# back to a gcloud access token (`gcloud auth login --enable-gdrive-access` first). You can also set
# ICLA_SHEET_TOKEN to an OAuth access token with Drive read access.
#
# Environment:
#   ICLA_SHEET_ID     spreadsheet id (default: the Netty ICLA sheet)
#   ICLA_SHEET_GID    sheet tab gid  (default: 0)
#   ICLA_SHEET_TOKEN  OAuth access token (optional)
#   ICLA_SHEET_CSV    path to a local CSV export, skips the download (optional)
#
# Exit status: 0 = all users signed, 1 = at least one user not found, 2 = error.
set -e

SHEET_ID="${ICLA_SHEET_ID:-1csGlaBCltss7AZ6zWktPFS8BDeOvumf-6aqcMct_bSw}"
SHEET_GID="${ICLA_SHEET_GID:-0}"
REPO="${GITHUB_REPOSITORY:-netty/netty}"
COLUMN="Github user name"

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 <github-user | PR number | PR URL> [...]" >&2
    exit 2
fi

csv="$(mktemp)"
trap 'rm -f "$csv"' EXIT

download() {
    # $1 = optional bearer token
    local url="https://docs.google.com/spreadsheets/d/${SHEET_ID}/export?format=csv&gid=${SHEET_GID}"
    local args=(-sSL -o "$csv" -w '%{http_code}')
    [ -n "$1" ] && args+=(-H "Authorization: Bearer $1")
    last_error=""
    local code
    if ! code="$(curl "${args[@]}" "$url" 2>&1)"; then
        last_error="curl failed: $code"
        return 1
    fi
    if [ "$code" != "200" ]; then
        last_error="HTTP $code"
        return 1
    fi
    # A login redirect returns HTML with a 200 status, so verify we got CSV.
    if head -c 100 "$csv" | grep -qi '<html\|<!doctype'; then
        last_error="got a login page instead of CSV (no access)"
        return 1
    fi
}

fetch_sheet() {
    if [ -n "$ICLA_SHEET_CSV" ]; then
        cp "$ICLA_SHEET_CSV" "$csv"
        return 0
    fi
    if [ -n "$ICLA_SHEET_TOKEN" ]; then
        download "$ICLA_SHEET_TOKEN" && return 0
    else
        download "" && return 0
        if command -v gcloud >/dev/null 2>&1; then
            local token
            token="$(gcloud auth print-access-token 2>/dev/null)" || token=""
            [ -n "$token" ] && download "$token" && return 0
        fi
    fi
    echo "Could not download the ICLA sheet (${last_error:-unknown error})." >&2
    echo "The sheet is not publicly readable. Either:" >&2
    echo "  - run 'gcloud auth login --enable-gdrive-access' (the default gcloud token has no Drive scope)," >&2
    echo "  - set ICLA_SHEET_TOKEN to an OAuth token with Drive read access, or" >&2
    echo "  - export the sheet as CSV and set ICLA_SHEET_CSV=/path/to/file.csv" >&2
    return 1
}

resolve_user() {
    local arg="$1" pr
    if [[ "$arg" =~ ^[0-9]+$ ]]; then
        pr="$arg"
    elif [[ "$arg" =~ ^https?://github\.com/([^/]+/[^/]+)/pull/([0-9]+) ]]; then
        REPO="${BASH_REMATCH[1]}"
        pr="${BASH_REMATCH[2]}"
    else
        # Plain user name, tolerate a leading '@'.
        echo "${arg#@}"
        return 0
    fi
    gh pr view "$pr" --repo "$REPO" --json author --jq '.author.login'
}

fetch_sheet || exit 2

status=0
for arg in "$@"; do
    if ! user="$(resolve_user "$arg")" || [ -z "$user" ]; then
        echo "ERROR: could not resolve a GitHub user from '$arg'" >&2
        status=2
        continue
    fi

    set +e
    ICLA_USER="$user" ICLA_COLUMN="$COLUMN" python3 - "$csv" <<'EOF'
import csv, os, re, sys

user = os.environ["ICLA_USER"].strip().lower()
column = os.environ["ICLA_COLUMN"].strip().lower()

def normalize(value):
    value = value.strip().lower()
    value = re.sub(r"^https?://(www\.)?github\.com/", "", value)
    return value.lstrip("@").strip("/ ")

with open(sys.argv[1], newline="", encoding="utf-8-sig") as f:
    reader = csv.DictReader(f)
    fields = {(name or "").strip().lower(): name for name in (reader.fieldnames or [])}
    if column not in fields:
        print("ERROR: column '%s' not found, available: %s" % (column, ", ".join(fields)), file=sys.stderr)
        sys.exit(2)
    key = fields[column]
    for row in reader:
        if normalize(row.get(key) or "") == user:
            print("OK:      %s has signed the ICLA" % os.environ["ICLA_USER"])
            sys.exit(0)

print("MISSING: %s was not found in the ICLA sheet" % os.environ["ICLA_USER"])
sys.exit(1)
EOF
    rc=$?
    set -e
    # Keep the most severe status: 2 (error) > 1 (missing) > 0 (ok).
    [ "$rc" -gt "$status" ] && status=$rc
done
exit "$status"
