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
# Checks that the native libraries of the given modules can be loaded (and called), with all jars in the given
# directory on the classpath, see check_load_native.sh and ci-verify-load.yml.
#
# Usage: check_load_natives.sh <jar directory> <module>...
#
# Modules: epoll, io_uring, kqueue, dns-macos, quic
set -euo pipefail

if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <jar directory> <module>..." >&2
    exit 2
fi

jars="$1"
shift
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

failed=()
for module in "$@"; do
    case "$module" in
        epoll) check=(io.netty.channel.epoll.Epoll ensureAvailability) ;;
        # Calls into the native library, without depending on whether the kernel supports io_uring (the runners
        # and QEMU may not).
        io_uring) check=(io.netty.channel.uring.Native kernelVersion) ;;
        kqueue) check=(io.netty.channel.kqueue.KQueue ensureAvailability) ;;
        dns-macos) check=(io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider ensureAvailability) ;;
        quic) check=(io.netty.handler.codec.quic.Quic ensureAvailability) ;;
        *) echo "Unknown module: $module" >&2; exit 2 ;;
    esac
    echo "::group::$module"
    if ! bash "$script_dir/check_load_native.sh" "$jars" "${check[@]}"; then
        failed+=("$module")
    fi
    echo "::endgroup::"
done

if [ "${#failed[@]}" -gt 0 ]; then
    echo "Could not load the native libraries of: ${failed[*]}" >&2
    exit 1
fi
