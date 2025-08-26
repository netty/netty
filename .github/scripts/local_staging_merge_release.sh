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
if [ "$#" -lt 2 ]; then
    echo "Expected target directory and at least one local staging directory"
    exit 1
fi
TARGET=$1

for ((i=2; i<=$#; i++))
do
  DIR="${!i}"

  if [ ! -d "${TARGET}" ]
  then
      mkdir -p "${TARGET}"
  fi
  cp -r "${DIR}"/"${SUB_DIR}"/* "${TARGET}/${SUB_DIR}"/
done
