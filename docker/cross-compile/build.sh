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

if ! command -v zig > /dev/null 2>&1; then
    echo "zig was not found on PATH, this script is meant to be run in docker/Dockerfile.cross_compile" >&2
    exit 1
fi

# Put the cross compile toolchain on the PATH, as <triple>-<tool> links to zig-toolchain.sh. Outside of the project
# directory, which "mvn clean" would delete while the build still needs it.
bin_dir="${XDG_CACHE_HOME:-$HOME/.cache}/netty-cross-compile/bin"
rm -rf "$bin_dir"
mkdir -p "$bin_dir"
for triple in x86_64-linux-gnu aarch64-linux-gnu aarch64-none-linux-gnu riscv64-linux-gnu \
        x86_64-apple-darwin aarch64-apple-darwin x86_64-w64-mingw32; do
    for tool in gcc cc g++ c++ ar ranlib ld; do
        ln -s "$script_dir/zig-toolchain.sh" "$bin_dir/$triple-$tool"
    done
    case "$triple" in
        # CMake requires one for macOS targets, see zig-toolchain.sh
        *-apple-darwin) ln -s "$script_dir/zig-toolchain.sh" "$bin_dir/$triple-install_name_tool" ;;
    esac
done
export PATH="$bin_dir:$PATH"

unix_common=transport-native-unix-common
linux_modules="$unix_common,transport-native-epoll,transport-native-io_uring"
mac_modules="$unix_common,transport-native-kqueue,resolver-dns-native-macos"
quic=codec-native-quic

maven_args=()
case "$target" in
    linux-x86_64)
        triple=x86_64-linux-gnu
        rust_target=x86_64-unknown-linux-gnu
        modules="$linux_modules,$quic"
        # The "linux" profiles are activated by the (x86_64 linux) host, so there is no profile to select. Make
        # sure they use the cross compile toolchain instead of the compiler of the host though, so we get the
        # same minimum glibc version as for all the other linux targets.
        profiles=""
        maven_args+=("-Dexe.compiler=$triple-gcc" "-Dexe.archiver=$triple-ar")
        export CC="$triple-gcc" CXX="$triple-g++"
        ;;
    linux-aarch_64)
        triple=aarch64-linux-gnu
        rust_target=aarch64-unknown-linux-gnu
        modules="$linux_modules,$quic"
        # These profiles already refer to the compilers by their triple, see zig-toolchain.sh
        profiles="linux-aarch64"
        ;;
    linux-riscv64)
        triple=riscv64-linux-gnu
        rust_target=riscv64gc-unknown-linux-gnu
        modules="$linux_modules"
        profiles="linux-riscv64"
        ;;
    osx-x86_64|osx-aarch_64)
        if [ "$target" = osx-x86_64 ]; then
            triple=x86_64-apple-darwin
            rust_target=x86_64-apple-darwin
            profiles="mac-intel-cross-compile"
        else
            triple=aarch64-apple-darwin
            rust_target=aarch64-apple-darwin
            profiles="mac-m1-cross-compile"
        fi
        modules="$mac_modules,$quic"
        # The "linux" profiles are activated by the host, and would replace the native build of the cross compile
        # profile.
        profiles="$profiles,!linux,!Linux"
        maven_args+=("-Dexe.compiler=$triple-gcc" "-Dexe.archiver=$triple-ar")
        ;;
    windows-x86_64)
        triple=x86_64-w64-mingw32
        # The MinGW flavour that uses the UCRT, libunwind and libc++, like zig does.
        rust_target=x86_64-pc-windows-gnullvm
        modules="$quic"
        profiles="windows-x86_64-cross-compile,!linux"
        ;;
    *)
        echo "Unsupported target: $target" >&2
        exit 2
        ;;
esac

export NETTY_CROSS_TRIPLE="$triple"
# libtool looks at the linker to decide how to link shared libraries. For macOS we want what it does on a mac (which
# doesn't depend on the linker), so tell it that the linker isn't GNU ld, as otherwise it asks the ld of the host,
# which is the GNU one of binutils here. For the others make sure it finds a GNU compatible one (see zig-toolchain.sh),
# as otherwise it silently stops building shared libraries if the ld of the host isn't.
case "$triple" in
    *-apple-darwin) export lt_cv_prog_gnu_ld=no ;;
    *) export LD="$triple-ld" ;;
esac
export CMAKE_TOOLCHAIN_FILE="$script_dir/toolchain.cmake"

# The JDK that runs the build only ships the jni_md.h of the platform it runs on (include/linux in the docker image),
# hawtjni only looks for the one of the target platform though. The definitions are the same for linux and macOS. For
# Windows we have our own, see jni/win32/jni_md.h.
java_include="${JAVA_HOME:?JAVA_HOME must be set}/include"
case "$triple" in
    *-mingw32) jni_md_dir="$script_dir/jni/win32" ;;
    *) jni_md_dir="$(dirname "$(ls "$java_include"/*/jni_md.h | head -n 1)")" ;;
esac
export CPPFLAGS="${CPPFLAGS:+$CPPFLAGS }-I$jni_md_dir"

# Rust (quiche): compile C code (cc crate), and link with the cross toolchain.
rust_target_env="$(echo "$rust_target" | tr '[:lower:]-' '[:upper:]_')"
rust_target_var="$(echo "$rust_target" | tr '-' '_')"
# Not when the target is the host, as cargo then also uses it for the build scripts, which need to run here.
if [ "$(rustc -vV | sed -n 's/^host: //p')" != "$rust_target" ]; then
    export "CARGO_TARGET_${rust_target_env}_LINKER=$triple-gcc"
fi
export "CC_${rust_target_var}=$triple-gcc"
export "CXX_${rust_target_var}=$triple-g++"
export "AR_${rust_target_var}=$triple-ar"
# boring-sys generates its bindings with bindgen (libclang), which needs to see the same system headers as zig.
bindgen_args=""
if [ "$rust_target" = x86_64-pc-windows-gnullvm ]; then
    # clang does not know the Rust name of this target
    bindgen_args="--target=x86_64-w64-windows-gnu"
fi
while IFS= read -r dir; do
    bindgen_args="$bindgen_args -isystem $dir"
done < <("$triple-gcc" -E -v -x c /dev/null 2>&1 > /dev/null \
    | sed -n '/^#include <...> search starts here:/,/^End of search list./p' | sed -n 's/^ \{1,\}//p')
export "BINDGEN_EXTRA_CLANG_ARGS_${rust_target_var}=$bindgen_args"

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
exec ./mvnw -B -ntp ${module_args[@]+"${module_args[@]}"} ${profile_args[@]+"${profile_args[@]}"} \
    -DskipTests=true -Drevapi.skip=true -Dcheckstyle.skip=true -Dforbiddenapis.skip=true \
    ${maven_args[@]+"${maven_args[@]}"} "$@"
