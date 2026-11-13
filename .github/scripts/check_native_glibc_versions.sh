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
# Fails if any of the given native libraries, or any *.so file below the given directories, requires a glibc symbol
# version that is newer than the oldest glibc we support for the architecture of the library:
#
#   x86_64:  GLIBC_2.14
#   aarch64: GLIBC_2.17
#
# Libraries for other architectures are not checked.
#
# Just referencing a symbol with a newer version is enough to make the dynamic linker refuse to load the whole
# library on an older glibc, even if the code that uses the symbol is never executed. This easily happens by accident
# when building against newer glibc headers. For example the aarch64 cross compile toolchain ships with a glibc 2.33
# sysroot, where stat(), fstat() and lstat() are linked against GLIBC_2.33.
#
# Version needs that are marked as WEAK (VER_FLG_WEAK) are only reported, as the glibc dynamic linker does not refuse
# to load a library if such a version is missing. Note that declaring a symbol as weak (__attribute__((weak))) does
# NOT result in such a version need: linking a weak reference to e.g. epoll_pwait2 against a glibc that has
# epoll_pwait2@GLIBC_2.35 records a regular "GLIBC_2.35" version need, which does prevent loading the library on an
# older glibc. Weak references only help if the symbol does not exist in the glibc we link against, as then no
# version is recorded for it.
#
# readelf from binutils is used, which can read the ELF files of all architectures, so this also works when
# cross compiling.
set -euo pipefail

if [ "$#" -eq 0 ]; then
    echo "Usage: $0 <native library or directory>..." >&2
    exit 2
fi

if ! command -v readelf > /dev/null 2>&1; then
    echo "readelf (binutils) is required to check the glibc symbol versions of the native libraries, but was not found on PATH" >&2
    exit 1
fi

# Prints the newest glibc version that libraries for the given ELF machine (as printed by readelf -h) may require,
# or nothing if libraries for this machine are not checked.
max_glibc_version() {
    case "$1" in
        "Advanced Micro Devices X86-64") echo "2.14" ;;
        "AArch64") echo "2.17" ;;
        *) echo "" ;;
    esac
}

# Returns successfully if version $1 is newer than version $2.
is_newer() {
    [ "$1" != "$2" ] && [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n 1)" = "$1" ]
}

# Prints the glibc versions (without the GLIBC_ prefix) from the version needs of library $1, one per line.
# $2 selects which ones: "weak" for the ones marked as WEAK, "required" for all others.
glibc_version_needs() {
    readelf -W -V "$1" | awk -v want="$2" '
        /Name: GLIBC_/ {
            weak = ($0 ~ /Flags: [^:]*WEAK/) ? 1 : 0
            if ((want == "weak" ? 1 : 0) != weak) {
                next
            }
            for (i = 1; i < NF; i++) {
                if ($i == "Name:") {
                    version = $(i + 1)
                    sub(/^GLIBC_/, "", version)
                    print version
                }
            }
        }' | sort -u -V
}

libs=()
for arg in "$@"; do
    if [ -d "$arg" ]; then
        while IFS= read -r -d '' lib; do
            libs+=("$lib")
        done < <(find "$arg" -type f -name '*.so' -print0)
    elif [ -f "$arg" ]; then
        libs+=("$arg")
    else
        echo "No such file or directory: $arg" >&2
        exit 1
    fi
done

if [ "${#libs[@]}" -eq 0 ]; then
    echo "No native libraries found to check the glibc symbol versions of"
    exit 0
fi

failed=0
for lib in "${libs[@]}"; do
    machine=$(readelf -h "$lib" | sed -n 's/^ *Machine: *//p')
    max=$(max_glibc_version "$machine")
    if [ -z "$max" ]; then
        echo "Not checking the glibc symbol versions of $lib ($machine)"
        continue
    fi

    for version in $(glibc_version_needs "$lib" weak); do
        if [ "$version" = "PRIVATE" ] || is_newer "$version" "$max"; then
            echo "$lib ($machine) has a weak (optional) reference to GLIBC_$version, which does not prevent loading it"
        fi
    done

    violations=()
    for version in $(glibc_version_needs "$lib" required); do
        # GLIBC_PRIVATE symbols are internal to glibc and may change with every glibc release.
        if [ "$version" = "PRIVATE" ] || is_newer "$version" "$max"; then
            violations+=("$version")
        fi
    done

    if [ "${#violations[@]}" -eq 0 ]; then
        echo "$lib ($machine) requires at most GLIBC_$max"
        continue
    fi

    failed=1
    echo "$lib ($machine) must not require a glibc symbol version newer than GLIBC_$max, but requires:" >&2
    for version in "${violations[@]}"; do
        echo "  GLIBC_$version, used by:" >&2
        readelf -W --dyn-syms "$lib" | grep -E "@GLIBC_${version//./\\.}( |$)" | awk '{ print "    " $8 }' >&2 || true
    done
done

if [ "$failed" -ne 0 ]; then
    echo "Avoid using these symbols, or provide a fallback that does not raise the required glibc version." >&2
fi
exit "$failed"
