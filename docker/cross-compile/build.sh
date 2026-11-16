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
# Builds the native modules of Netty for one target platform, using the zig based cross compile toolchain of
# docker/Dockerfile.cross_compile (see zig-toolchain.sh).
#
# Usage: docker/cross-compile/build.sh <target> <maven arguments>...
#
# Targets:
#   linux-x86_64    transport-native-{unix-common,epoll,io_uring}, codec-native-quic
#   linux-aarch_64  transport-native-{unix-common,epoll,io_uring}, codec-native-quic
#   linux-riscv64   transport-native-{unix-common,epoll,io_uring}
#   osx-x86_64      transport-native-{unix-common,kqueue}, resolver-dns-native-macos, codec-native-quic
#   osx-aarch_64    transport-native-{unix-common,kqueue}, resolver-dns-native-macos, codec-native-quic
#   windows-x86_64  codec-native-quic
#
# The maven arguments are passed through, for example:
#
#   docker/cross-compile/build.sh osx-aarch_64 -am clean install
#
# Set NETTY_CROSS_MODULES to a comma separated list of modules to only build some of the native modules of the target,
# or to "all" to build the whole project (for linux-x86_64, which is also where all the other artifacts come from).
#
# Tests are always skipped, as the libraries are built for another platform than the one we run on.
set -euo pipefail

if [ "$#" -lt 1 ]; then
    sed -n '/^# Usage:/,/^set -euo/p' "$0" | sed -e 's/^# \{0,1\}//' -e '$d' >&2
    exit 2
fi

target="$1"
shift

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "$script_dir/../.." && pwd)"

unix_common=transport-native-unix-common
linux_modules="$unix_common,transport-native-epoll,transport-native-io_uring"
mac_modules="$unix_common,transport-native-kqueue,resolver-dns-native-macos"
quic=codec-native-quic

maven_args=()
case "$target" in
    linux-x86_64)
        modules="$linux_modules,$quic"
        # The "linux" profiles are activated by the (x86_64 linux) host, so there is no profile to select. Make
        # sure they use the cross compile toolchain instead of the compiler of the host though, so we get the
        # same minimum glibc version as for all the other linux targets.
        profiles=""
        maven_args+=("-Dexe.compiler=x86_64-linux-gnu-gcc" "-Dexe.archiver=x86_64-linux-gnu-ar")
        export CC=x86_64-linux-gnu-gcc CXX=x86_64-linux-gnu-g++
        ;;
    linux-aarch_64)
        modules="$linux_modules,$quic"
        # These profiles already refer to the compilers by their triple, see zig-toolchain.sh
        profiles="linux-aarch64"
        ;;
    linux-riscv64)
        modules="$linux_modules"
        profiles="linux-riscv64"
        ;;
    osx-x86_64|osx-aarch_64)
        if [ "$target" = osx-x86_64 ]; then
            triple=x86_64-apple-darwin
            profiles="mac-intel-cross-compile"
        else
            triple=aarch64-apple-darwin
            profiles="mac-m1-cross-compile"
        fi
        modules="$mac_modules,$quic"
        # The "linux" profiles are activated by the host, and would replace the native build of the cross compile
        # profile.
        profiles="$profiles,!linux,!Linux"
        maven_args+=("-Dexe.compiler=$triple-gcc" "-Dexe.archiver=$triple-ar")
        ;;
    windows-x86_64)
        modules="$quic"
        profiles="windows-x86_64-cross-compile,!linux"
        ;;
    *)
        echo "Unsupported target: $target" >&2
        exit 2
        ;;
esac

modules="${NETTY_CROSS_MODULES:-$modules}"
module_args=()
if [ "$modules" != all ]; then
    module_args+=(-pl "$modules")
else
    # Building everything anyway, -am (as used by docker/docker-compose.cross.yaml) has no meaning without -pl.
    args=()
    for arg in "$@"; do
        case "$arg" in
            -am|--also-make) ;;
            *) args+=("$arg") ;;
        esac
    done
    set -- ${args[@]+"${args[@]}"}
fi

profile_args=()
if [ -n "$profiles" ]; then
    profile_args+=("-P$profiles")
fi

cd "$project_dir"
set -x
exec "$script_dir/env.sh" "$target" ./mvnw -B -ntp ${module_args[@]+"${module_args[@]}"} ${profile_args[@]+"${profile_args[@]}"} \
    -DskipTests=true -Drevapi.skip=true -Dcheckstyle.skip=true -Dforbiddenapis.skip=true \
    ${maven_args[@]+"${maven_args[@]}"} "$@"
