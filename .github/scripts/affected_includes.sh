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
# Exits with 0 if the given module is part of the build as selected by NETTY_AFFECTED_MODULES
# (see mvn_affected.sh) and with 1 otherwise.
#
# Usage: affected_includes.sh <module>
set -e

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <module>" >&2
    exit 1
fi

case "${NETTY_AFFECTED_MODULES:-ALL}" in
    ALL) exit 0 ;;
    NONE) exit 1 ;;
    *) case ",$NETTY_AFFECTED_MODULES," in *",$1,"*) exit 0 ;; *) exit 1 ;; esac ;;
esac
