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
# Cross compile toolchain on top of zig (https://ziglang.org), see docker/cross-compile/build.sh, which sets up the
# links described below, and docker/Dockerfile.cross_compile.
#
# This script is invoked through symlinks named <triple>-<tool>, for example x86_64-apple-darwin-gcc or
# aarch64-linux-gnu-ar, which is the naming that autoconf (--host=<triple>), our Makefiles and CMake expect from a
# cross toolchain. The triple selects the zig target, the tool selects the zig sub-command:
#
#   <triple>-gcc, -cc, -clang      zig cc
#   <triple>-g++, -c++, -clang++   zig c++
#   <triple>-ar, -ranlib           zig ar / zig ranlib
#   <triple>-ld                    zig ld.lld
#   <triple>-install_name_tool     llvm-install-name-tool, if there is one (only macOS triples, see below)
#
# Supported triples, and the zig target they map to:
#
#   x86_64-linux-gnu                        x86_64-linux-gnu.<NETTY_GLIBC_X86_64, default 2.17>
#   aarch64-linux-gnu, aarch64-none-linux-gnu  aarch64-linux-gnu.<NETTY_GLIBC_AARCH64, default 2.17>
#   riscv64-linux-gnu                       riscv64-linux-gnu.<NETTY_GLIBC_RISCV64, default 2.31>
#   x86_64-apple-darwin                     x86_64-macos.<deployment target, default 11.0>
#   aarch64-apple-darwin                    aarch64-macos.<deployment target, default 11.0>
#   x86_64-w64-mingw32                      x86_64-windows-gnu
#
# zig links against stubs of the given glibc version, so no symbol versions newer than that end up in the
# libraries, and against its own copy of the macOS libSystem stubs and headers, so no macOS SDK is needed.
#
# The flags that our builds pass for the "native" compilers of the respective platform are mapped to what zig
# understands (after splitting -Wl,a,b,c into -Wl,a -Wl,b -Wl,c, like libtool does):
#
#   -target <clang triple>, -arch <arch>        dropped, the target comes from the name of the tool. For macOS the
#                                               deployment target is taken from a "macosX.Y" suffix.
#   -mmacosx-version-min=X.Y                    sets the macOS deployment target.
#   -Wl,-platform_version,macos,X.Y,Z           sets the macOS deployment target.
#   -Wl,-weak_library,<path to static archive>  replaced by the path to the archive, which is what ld64 does for
#                                               static archives anyway.
#   -Wl,-l<library>                             replaced by -l<library>, zig doesn't take libraries as linker args.
#   -l:libstdc++.a                              replaced by -lc++, zig links its own libc++ (and libc++abi and
#                                               libunwind) statically.
#   -l:libgcc.a, -l:libgcc_eh.a                 dropped, zig links compiler-rt and libunwind instead.
#
# aarch64 Linux shared libraries are linked with the workaround for Cortex-A53 erratum 843419, see
# link_with_cortex_a53_843419_fix below.
#
#   -compatibility_version X, -current_version X
#                                               X is padded to X.0.0, ld64 accepts just a major version, zig not
#                                               (libtool passes them for macOS).
#   -Wl,-exported_symbol,<pattern>, -Wl,-exported_symbols_list,<file>
#                                               not supported by zig's Mach-O linker. The symbols of the object
#                                               files and static archives that are linked are hidden instead, see
#                                               hide_unexported_symbols below.
#
# -Wno-unknown-warning-option is always added, as zig cc is clang, and our builds pass some warning options only gcc
# knows. The MACOSX_DEPLOYMENT_TARGET environment variable is respected as the default macOS deployment target.
set -euo pipefail

ZIG="${ZIG:-zig}"
name="$(basename "$0")"
tool="${name##*-}"
triple="${name%-*}"

case "$tool" in
    gcc|cc|clang) zig_cmd=(cc) ;;
    g++|c++|clang++) zig_cmd=(c++) ;;
    ar) exec "$ZIG" ar "$@" ;;
    ranlib) exec "$ZIG" ranlib "$@" ;;
    # Only used by libtool to find out that it talks to a GNU compatible linker (it links through the compiler).
    # Otherwise libtool asks the compiler, which points it to the ld of the host, and depending on what that is
    # silently stops building shared libraries.
    ld) exec "$ZIG" ld.lld "$@" ;;
    # CMake insists on finding one for macOS targets (CMakeFindBinUtils), but only needs it for shared libraries,
    # and our CMake builds (BoringSSL) only create static ones. zig doesn't have one, so this only works if there
    # is an llvm-install-name-tool, and otherwise fails loudly if anything ever really uses it.
    install_name_tool)
        if command -v llvm-install-name-tool > /dev/null 2>&1; then
            exec llvm-install-name-tool "$@"
        fi
        echo "$name: not available, install llvm-install-name-tool if this is needed" >&2
        exit 1
        ;;
    *) echo "$name: unsupported tool '$tool'" >&2; exit 2 ;;
esac

macos_version=""
case "$triple" in
    x86_64-linux-gnu) target="x86_64-linux-gnu.${NETTY_GLIBC_X86_64:-2.17}" ;;
    aarch64-linux-gnu|aarch64-none-linux-gnu) target="aarch64-linux-gnu.${NETTY_GLIBC_AARCH64:-2.17}" ;;
    riscv64-linux-gnu) target="riscv64-linux-gnu.${NETTY_GLIBC_RISCV64:-2.31}" ;;
    x86_64-apple-darwin) target="x86_64-macos"; macos_version="${MACOSX_DEPLOYMENT_TARGET:-11.0}" ;;
    aarch64-apple-darwin) target="aarch64-macos"; macos_version="${MACOSX_DEPLOYMENT_TARGET:-11.0}" ;;
    x86_64-w64-mingw32) target="x86_64-windows-gnu" ;;
    *) echo "$name: unsupported target '$triple'" >&2; exit 2 ;;
esac

script_dir="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"

# zig's Mach-O linker doesn't support -exported_symbol (and ignores -exported_symbols_list), so emulate them: link
# against copies of the object files and static archives in which all the symbols that should not be exported are
# marked as private extern (hidden), see macho-hide-symbols.py. Replaces the inputs in the args array.
hide_unexported_symbols() {
    local keep=() lib_dirs=() new_args=() arg i=0 n=0 name dir found
    hide_tmp="$(mktemp -d)"
    trap 'rm -rf "$hide_tmp"' EXIT
    for arg in "${exported_symbols[@]}"; do
        keep+=(--keep "$arg")
    done
    for ((i = 0; i < ${#args[@]}; i++)); do
        case "${args[$i]}" in
            -L) lib_dirs+=("${args[$((i + 1))]}") ;;
            -L*) lib_dirs+=("${args[$i]#-L}") ;;
        esac
    done
    for arg in "${args[@]}"; do
        found=""
        case "$arg" in
            -l*)
                # Same search as ld64: in each directory a dylib (or its stub) wins over a static archive.
                name="${arg#-l}"
                for dir in ${lib_dirs[@]+"${lib_dirs[@]}"}; do
                    if [ -e "$dir/lib$name.dylib" ] || [ -e "$dir/lib$name.tbd" ]; then
                        break
                    elif [ -e "$dir/lib$name.a" ]; then
                        found="$dir/lib$name.a"
                        break
                    fi
                done
                ;;
            *.o|*.a|*.rlib)
                if [ -f "$arg" ]; then found="$arg"; fi
                ;;
        esac
        if [ -n "$found" ]; then
            n=$((n + 1))
            python3 -I "$script_dir/macho-hide-symbols.py" "${keep[@]}" "$found" "$hide_tmp/$n-$(basename "$found")"
            new_args+=("$hide_tmp/$n-$(basename "$found")")
        else
            new_args+=("$arg")
        fi
    done
    args=("${new_args[@]}")
}

# Extracts the macOS version from a clang target triple like x86_64-apple-macos10.9 or arm64-apple-macosx11.
macos_version_from_triple() {
    if [[ "$1" =~ macosx?([0-9]+(\.[0-9]+)*)$ ]]; then
        echo "${BASH_REMATCH[1]}"
    fi
}

# Split -Wl,a,b,c into -Wl,a -Wl,b -Wl,c (like libtool does), so we only need to handle the split form below.
split_args=()
for arg in "$@"; do
    if [[ "$arg" == -Wl,*,* ]]; then
        IFS=',' read -r -a parts <<< "${arg#-Wl,}"
        for part in "${parts[@]}"; do
            split_args+=("-Wl,$part")
        done
    else
        split_args+=("$arg")
    fi
done
set -- ${split_args[@]+"${split_args[@]}"}

# zig wants at least major.minor for the deployment target, ld64 also accepts just the major version.
normalize_version() {
    if [[ "$1" != *.* ]]; then echo "$1.0"; else echo "$1"; fi
}

# zig wants major.minor.patch for the versions of a dylib, ld64 also accepts just major or major.minor.
normalize_dylib_version() {
    local v="$1"
    while [[ "$v" != *.*.* ]]; do v="$v.0"; done
    echo "$v"
}

args=()
exported_symbols=()
while [ "$#" -gt 0 ]; do
    arg="$1"
    shift
    case "$arg" in
        -target|-arch)
            if [ "$arg" = "-target" ] && [ -n "$macos_version" ]; then
                v="$(macos_version_from_triple "${1:-}")"
                if [ -n "$v" ]; then macos_version="$v"; fi
            fi
            shift || true
            ;;
        --target=*)
            if [ -n "$macos_version" ]; then
                v="$(macos_version_from_triple "${arg#--target=}")"
                if [ -n "$v" ]; then macos_version="$v"; fi
            fi
            ;;
        -mmacosx-version-min=*)
            macos_version="${arg#-mmacosx-version-min=}"
            ;;
        -Wl,-platform_version)
            if [ "${1:-}" = "-Wl,macos" ] && [ "$#" -ge 3 ]; then
                macos_version="${2#-Wl,}"
                shift 3
            else
                args+=("$arg")
            fi
            ;;
        -Wl,-weak_library)
            if [[ "${1:-}" == -Wl,*.a ]]; then
                args+=("${1#-Wl,}")
                shift
            else
                args+=("$arg")
            fi
            ;;
        -Wl,-exported_symbol)
            # Not supported by zig's Mach-O linker, see hide_unexported_symbols below.
            if [ "$#" -gt 0 ]; then
                exported_symbols+=("${1#-Wl,}")
                shift
            fi
            ;;
        -Wl,-exported_symbols_list)
            # Silently ignored by zig's Mach-O linker, see hide_unexported_symbols below.
            if [ "$#" -gt 0 ]; then
                while IFS= read -r line || [ -n "$line" ]; do
                    line="${line%%#*}"
                    line="$(echo "$line" | tr -d '[:space:]')"
                    if [ -n "$line" ]; then exported_symbols+=("$line"); fi
                done < "${1#-Wl,}"
                shift
            fi
            ;;
        -Wl,-compatibility_version|-Wl,-current_version|-compatibility_version|-current_version)
            args+=("$arg")
            if [ "$#" -gt 0 ]; then
                if [[ "$1" == -Wl,* ]]; then
                    args+=("-Wl,$(normalize_dylib_version "${1#-Wl,}")")
                else
                    args+=("$(normalize_dylib_version "$1")")
                fi
                shift
            fi
            ;;
        -Wl,-l*)
            # A library passed to the linker directly, which is the same as passing it to the compiler.
            args+=("${arg#-Wl,}")
            ;;
        -l:libstdc++.a)
            args+=("-lc++")
            ;;
        -l:libgcc.a|-l:libgcc_eh.a)
            ;;
        *)
            args+=("$arg")
            ;;
    esac
done

if [ -n "$macos_version" ]; then
    target="$target.$(normalize_version "$macos_version")"

    linking=true
    for arg in ${args[@]+"${args[@]}"}; do
        case "$arg" in
            -c|-E|-S|-M|-MM) linking=false; break ;;
        esac
    done
    if [ "$linking" = true ] && [ "${#exported_symbols[@]}" -gt 0 ]; then
        hide_unexported_symbols
    fi
fi

# Our builds pass some warning options that only gcc knows (zig cc is clang), which would fail builds that use
# -Werror.
# Links aarch64 Linux shared libraries with the workaround for erratum 843419 of the Cortex-A53, which GNU toolchains
# (and rustc) usually enable by default for this target: on affected cores, an adrp at the end of a 4KB page followed
# by certain loads/stores can load from the wrong address. zig ignores -mfix-cortex-a53-843419 and doesn't accept
# -Wl,--fix-cortex-a53-843419, so let zig link, and then link once more with the exact same lld command it used
# (which ZIG_VERBOSE_LINK prints) plus --fix-cortex-a53-843419.
link_with_cortex_a53_843419_fix() {
    local output="" i verbose lld_cmd
    for ((i = 0; i < ${#args[@]}; i++)); do
        if [ "${args[$i]}" = "-o" ]; then output="${args[$((i + 1))]:-}"; fi
    done
    if ! verbose="$(ZIG_VERBOSE_LINK=1 "$ZIG" "${zig_cmd[@]}" -target "$target" -Wno-unknown-warning-option \
            "${args[@]}" 2>&1)"; then
        echo "$verbose" >&2
        return 1
    fi
    lld_cmd="$(echo "$verbose" | grep "^ld\.lld .* -o $output " | tail -n 1)"
    if [ -z "$lld_cmd" ]; then
        echo "$verbose" >&2
        echo "$name: could not find the lld command for $output, to link it with --fix-cortex-a53-843419" >&2
        return 1
    fi
    # The command is printed space separated without quoting, which is fine as long as no path contains spaces.
    # shellcheck disable=SC2086
    "$ZIG" $lld_cmd --fix-cortex-a53-843419
}

shared=false
compile_only=false
for arg in ${args[@]+"${args[@]}"}; do
    case "$arg" in
        -shared) shared=true ;;
        -c|-E|-S|-M|-MM) compile_only=true ;;
    esac
done

# Not exec, as hide_unexported_symbols may have registered a trap to clean up.
if [[ "$target" == aarch64-linux-gnu* ]] && [ "$shared" = true ] && [ "$compile_only" = false ]; then
    link_with_cortex_a53_843419_fix
else
    "$ZIG" "${zig_cmd[@]}" -target "$target" -Wno-unknown-warning-option ${args[@]+"${args[@]}"}
fi
