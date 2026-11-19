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
# Runs a command in the environment for cross compiling for one target platform with the zig based toolchain of
# docker/Dockerfile.cross_compile (see zig-toolchain.sh).
#
# Usage: docker/cross-compile/env.sh <target> <command> [<argument>...]
#
# Targets: linux-x86_64, linux-aarch_64, linux-riscv64, osx-x86_64, osx-aarch_64, windows-x86_64
#
# Used by build.sh (one target per maven build), and by the cross-compile-all profiles of the native modules (all
# targets in one maven build). Besides what the tools of the build look at (CMAKE_TOOLCHAIN_FILE, the variables of the
# cc crate and bindgen, ...), it exports:
#
#   NETTY_CROSS_TARGET       the target, as given
#   NETTY_CROSS_TRIPLE       the autoconf style triple of the toolchain, for example x86_64-apple-darwin
#   NETTY_CROSS_RUST_TARGET  the Rust target, for example x86_64-apple-darwin
set -euo pipefail

if [ "$#" -lt 2 ]; then
    sed -n '/^# Usage:/,/^set -euo/p' "$0" | sed -e 's/^# \{0,1\}//' -e '$d' >&2
    exit 2
fi

target="$1"
shift

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v zig > /dev/null 2>&1; then
    echo "zig was not found on PATH, this script is meant to be run in docker/Dockerfile.cross_compile" >&2
    exit 1
fi

case "$target" in
    linux-x86_64) triple=x86_64-linux-gnu; rust_target=x86_64-unknown-linux-gnu ;;
    linux-aarch_64) triple=aarch64-linux-gnu; rust_target=aarch64-unknown-linux-gnu ;;
    linux-riscv64) triple=riscv64-linux-gnu; rust_target=riscv64gc-unknown-linux-gnu ;;
    osx-x86_64) triple=x86_64-apple-darwin; rust_target=x86_64-apple-darwin ;;
    osx-aarch_64) triple=aarch64-apple-darwin; rust_target=aarch64-apple-darwin ;;
    # The MinGW flavour that uses the UCRT, libunwind and libc++, like zig does.
    windows-x86_64) triple=x86_64-w64-mingw32; rust_target=x86_64-pc-windows-gnullvm ;;
    *)
        echo "Unsupported target: $target" >&2
        exit 2
        ;;
esac

# Put the cross compile toolchain on the PATH, as <triple>-<tool> links to zig-toolchain.sh. Outside of the project
# directory, which "mvn clean" would delete while the build still needs it.
bin_dir="${XDG_CACHE_HOME:-$HOME/.cache}/netty-cross-compile/bin"
mkdir -p "$bin_dir"
for t in x86_64-linux-gnu aarch64-linux-gnu aarch64-none-linux-gnu riscv64-linux-gnu \
        x86_64-apple-darwin aarch64-apple-darwin x86_64-w64-mingw32; do
    tools="gcc cc g++ c++ ar ranlib ld"
    case "$t" in
        # CMake requires one for macOS targets, see zig-toolchain.sh
        *-apple-darwin) tools="$tools install_name_tool" ;;
    esac
    for tool in $tools; do
        ln -sfn "$script_dir/zig-toolchain.sh" "$bin_dir/$t-$tool"
    done
done
export PATH="$bin_dir:$PATH"

export NETTY_CROSS_TARGET="$target"
export NETTY_CROSS_TRIPLE="$triple"
export NETTY_CROSS_RUST_TARGET="$rust_target"

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
export CPPFLAGS="-I$jni_md_dir"

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

exec "$@"
