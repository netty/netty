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
# Drop-in replacement for ./mvnw that can restrict the build to the modules affected by a change.
# The modules are taken from the NETTY_AFFECTED_MODULES environment variable, see detect_affected_modules.sh:
#
#   unset / empty / ALL   behaves exactly like ./mvnw (full build)
#   NONE                  nothing to do
#   a,b,c                 build a, b and c and the modules they depend on (-am) without running tests, then run
#                         the full lifecycle (including the tests) for a, b and c only.
#
# Modules that depend on the affected modules are intentionally not built. The one full build that is done
# per PR covers them.
#
# Usage: mvn_affected.sh <maven arguments>
#
# NETTY_MVN can be used to override the maven executable (default ./mvnw).
set -e

cd "$(dirname "$0")/../.."

MVN=${NETTY_MVN:-./mvnw}
AFFECTED=${NETTY_AFFECTED_MODULES:-ALL}

case "$AFFECTED" in
    ALL)
        exec "$MVN" "$@"
        ;;
    NONE)
        echo "No module affected by the changes, skipping build"
        exit 0
        ;;
esac

# Second pass operates on the output of the first one, so don't clean it away again.
ARGS_WITHOUT_CLEAN=()
for arg in "$@"; do
    [ "$arg" = "clean" ] || ARGS_WITHOUT_CLEAN+=("$arg")
done

echo "Building affected modules: $AFFECTED"
echo "Pass 1/2: build (and install) $AFFECTED and their dependencies without tests"
"$MVN" "$@" -pl "$AFFECTED" -am -DskipTests=true
echo "Pass 2/2: run the tests of $AFFECTED"
"$MVN" "${ARGS_WITHOUT_CLEAN[@]}" -pl "$AFFECTED"
