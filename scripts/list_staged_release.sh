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

RC_LIST=$(mvn -B org.sonatype.plugins:nexus-staging-maven-plugin:rc-list -DserverId=sonatype-nexus-staging -DnexusUrl=https://oss.sonatype.org | grep -A 2 "\[INFO\] ID                   State    Description")
STAGED=$(echo "$RC_LIST" | grep 'OPEN' | cut -f 2 -d ' ')
echo "$STAGED"
