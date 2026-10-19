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
# Prints which maven modules are affected by the changes between <base-rev> and HEAD:
#
#   ALL              a change outside of a single module (root pom, build setup, CI, dev-tools ...) so
#                    everything needs to be built
#   NONE             only files that can't influence the build (docs, license files ...) changed
#   a,b,c            comma separated list of modules that contain changes
#
# Usage: detect_affected_modules.sh <base-rev>
#
# The diff is taken against the merge-base of <base-rev> and HEAD, so this works for a local branch
# (e.g. origin/4.2) as well as for the merge commit GitHub checks out for pull requests (HEAD^1).
set -e

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <base-rev>" >&2
    exit 1
fi

cd "$(git rev-parse --show-toplevel)"

BASE=$1
if MERGE_BASE=$(git merge-base "$BASE" HEAD 2>/dev/null); then
    BASE=$MERGE_BASE
fi

# All modules are declared as top-level directories in the root pom.
ALL_MODULES=",$(sed -n 's:.*<module>\(.*\)</module>.*:\1:p' pom.xml | tr '\n' ','),"

# Modules that influence every other module (checkstyle config etc.) and so can't be built in isolation.
GLOBAL_MODULES=",dev-tools,"

# Changed files that never influence the build. Everything else outside of a module forces a full build.
is_ignorable() {
    case "$1" in
        *.md|LICENSE*|NOTICE*|license/*|.gitignore|.gitattributes|.editorconfig|.github/ISSUE_TEMPLATE/*|.github/*.md) return 0 ;;
        *) return 1 ;;
    esac
}

SELECTED=""
# --no-renames so that both the old and the new location of a moved file are reported.
CHANGED=$(git diff --name-only --no-renames "$BASE" HEAD)
while IFS= read -r file; do
    [ -z "$file" ] && continue
    top=${file%%/*}
    if [ "$top" = "$file" ] || ! case "$ALL_MODULES" in *",$top,"*) true ;; *) false ;; esac; then
        # Not part of a module.
        if is_ignorable "$file"; then
            continue
        fi
        echo ALL
        exit 0
    fi
    if is_ignorable "$file"; then
        continue
    fi
    case "$GLOBAL_MODULES" in
        *",$top,"*) echo ALL; exit 0 ;;
    esac
    case ",$SELECTED," in
        *",$top,"*) ;;
        *) SELECTED="${SELECTED:+$SELECTED,}$top" ;;
    esac
done <<EOT
$CHANGED
EOT

if [ -z "$SELECTED" ]; then
    echo NONE
else
    echo "$SELECTED"
fi
