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

if [ "$#" -lt 2 ]; then
    echo "Expected branch and maven arguments"
    exit 1
fi

git fetch

ALL_MODULES=$(find . -name pom.xml | cut -d '/' -f 2 )
MODULES=$(git diff --name-only "$1" | cut -d '/' -f 1 | sort -u | sed -n -e 'H;${x;s/\n/,/g;s/^,//;p;}')
MAVEN_ARGUMENTS=${*:2}
if [ -z "$MODULES" ]; then
  echo "No changes detected, skipping build"
  exit 0
fi

ALL_MODULES_ARRAY=($ALL_MODULES)
MODULES_ARRAY=($MODULES)

for module in "${MODULES_ARRAY[@]}"
do
  found=0;
  for m in "${ALL_MODULES_ARRAY[@]}"
  do
    if [ "$module" == "$m" ]; then
      found=1;
      break
    fi
  done

  if [ "$found" != 1 ]; then
    echo "Can't detect module that changed, run a full build"
    echo "./mvnw $MAVEN_ARGUMENTS"
    ./mvnw "${@:2}"
    exit 0
  fi
done

echo "Changes detected, start the build"
echo "./mvnw -pl $MODULES -amd $MAVEN_ARGUMENTS"
./mvnw -pl "$MODULES" -amd "${@:2}"

