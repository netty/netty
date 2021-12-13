#!/bin/bash
# ----------------------------------------------------------------------------
# Copyright 2021 The Netty Project
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

PARENT_TCNATIVE_VERSION=$(grep "<tcnative.version>" pom.xml | sed -n  's/.*<tcnative.version>\(.*\)<\/tcnative.version>/\1/p')
BOM_TCNATIVE_VERSION=$(grep "<tcnative.version>" bom/pom.xml | sed -n  's/.*<tcnative.version>\(.*\)<\/tcnative.version>/\1/p')

if [ "$PARENT_TCNATIVE_VERSION" != "$BOM_TCNATIVE_VERSION" ]; then
    echo "tcnative version missmatch, parent: $PARENT_TCNATIVE_VERSION, bom: $BOM_TCNATIVE_VERSION"
    exit 1
fi
exit 0


