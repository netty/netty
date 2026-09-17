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
# Figures out which Maven modules are affected by the changes in a pull request and
# emits Maven reactor arguments (-pl <modules> -amd -am) that build only those modules
# plus everything that depends on them (and what they depend on).
#
# Falls back to a full build (empty reactor args) whenever we can't be confident about
# the affected set, e.g. changes to shared build config/tooling, or anything that isn't
# a pull_request merge commit.
set -euo pipefail

# Modules whose content affects how every other module is built/tested (build tooling,
# dependency management, etc). Changing these always triggers a full build.
GLOBAL_MODULES=("dev-tools" "bom")

full_build() {
  echo "full-build=true" >> "$GITHUB_OUTPUT"
  echo "reactor-args=" >> "$GITHUB_OUTPUT"
  echo "$1"
}

if [ "${GITHUB_EVENT_NAME:-}" != "pull_request" ]; then
  full_build "Not a pull_request build; running full build."
  exit 0
fi

if ! git rev-parse HEAD^2 >/dev/null 2>&1; then
  full_build "Could not resolve merge commit parents (need fetch-depth >= 2); running full build."
  exit 0
fi

MODULES=($(grep -oE '<module>[^<]+</module>' pom.xml | sed -E 's#</?module>##g'))

CHANGED_FILES="$(git diff --name-only HEAD^1 HEAD^2)"

if [ -z "$CHANGED_FILES" ]; then
  full_build "No changed files detected; running full build."
  exit 0
fi

echo "Changed files:"
echo "$CHANGED_FILES"

FULL_BUILD=false
AFFECTED=()

while IFS= read -r file; do
  [ -z "$file" ] && continue

  top="${file%%/*}"
  if [ "$top" == "$file" ]; then
    # Root-level file (not inside any module directory).
    FULL_BUILD=true
    break
  fi

  matched=false
  for m in "${MODULES[@]}"; do
    if [ "$top" == "$m" ]; then
      matched=true
      for g in "${GLOBAL_MODULES[@]}"; do
        if [ "$m" == "$g" ]; then
          FULL_BUILD=true
        fi
      done
      AFFECTED+=("$m")
      break
    fi
  done

  if [ "$matched" == false ]; then
    # Path we don't recognize as a module (e.g. .github/, docker/) - play it safe.
    FULL_BUILD=true
  fi

  [ "$FULL_BUILD" == true ] && break
done <<< "$CHANGED_FILES"

if [ "$FULL_BUILD" == true ] || [ "${#AFFECTED[@]}" -eq 0 ]; then
  full_build "Change outside a single module (or to shared build config) detected; running full build."
  exit 0
fi

MODULE_LIST="$(printf '%s\n' "${AFFECTED[@]}" | sort -u | paste -sd, -)"
echo "full-build=false" >> "$GITHUB_OUTPUT"
echo "reactor-args=-pl ${MODULE_LIST} -amd -am" >> "$GITHUB_OUTPUT"
echo "Affected modules: ${MODULE_LIST}"
