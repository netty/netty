#!/bin/bash
# ----------------------------------------------------------------------------
# Copyright 2025 The Netty Project
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
set -e

# Keep this version in sync with docker-compose.centos-7.26.yaml
export EXPECTED_VERSION='26.ea.20'

curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
export JAVA_VERSION=$(sdk list java | grep 26.*-open|head -n 1|cut -f 3 -d '|')
echo "Java version: '$JAVA_VERSION'" | tee version.txt

if fgrep -q $EXPECTED_VERSION version.txt ; then
    echo "Version check passed"
    exit 0
else
    echo "Version check failed"
    exit 1
fi
