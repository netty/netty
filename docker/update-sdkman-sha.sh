#!/bin/bash -e
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
# The Dockerfiles verify the SDKMAN! installer script against a pinned SHA-256 before running it.
# SDKMAN! does not version this script, so the pinned value needs to be refreshed whenever it changes
# (the docker build fails with a checksum mismatch in that case).
#
# Usage:
#   docker/update-sdkman-sha.sh           download the current installer and update all Dockerfiles
#   docker/update-sdkman-sha.sh --check   only compare, exit with 1 if a Dockerfile is out of date
#
# IMPORTANT: Review the installer before committing a new hash, for example with
#   curl -fsSL "https://beta.sdkman.io?ci=true" | less
# The hash only protects against the script changing unnoticed, it does not make a malicious script safe.
# ----------------------------------------------------------------------------

set -euo pipefail
SDKMAN_INSTALL_URL="https://beta.sdkman.io?ci=true"
DOCKER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

CHECK_ONLY=false
case "$1" in
  "") ;;
  --check) CHECK_ONLY=true ;;
  *) echo "Usage: $0 [--check]" >&2; exit 2 ;;
esac

if command -v sha256sum > /dev/null; then
  sha256() { sha256sum "$1" | cut -d' ' -f1; }
else
  sha256() { shasum -a 256 "$1" | cut -d' ' -f1; }
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

curl -fsSL -o "$TMP_DIR/sdkman-install.sh" "$SDKMAN_INSTALL_URL"
NEW_SHA="$(sha256 "$TMP_DIR/sdkman-install.sh")"
echo "Current SDKMAN! installer SHA-256: $NEW_SHA"

OUTDATED=0
for dockerfile in "$DOCKER_DIR"/Dockerfile.*; do
  line="$(grep -E '^ARG sdkman_install_sha256=' "$dockerfile" || true)"
  [ -n "$line" ] || continue

  old_sha="${line#ARG sdkman_install_sha256=}"
  name="$(basename "$dockerfile")"
  if [ "$old_sha" = "$NEW_SHA" ]; then
    echo "up to date: $name"
    continue
  fi

  OUTDATED=1
  if [ "$CHECK_ONLY" = true ]; then
    echo "OUTDATED:   $name ($old_sha)"
  else
    sed -i.bak "s/^ARG sdkman_install_sha256=.*/ARG sdkman_install_sha256=$NEW_SHA/" "$dockerfile"
    rm -f "$dockerfile.bak"
    echo "updated:    $name ($old_sha -> $NEW_SHA)"
  fi
done

if [ "$CHECK_ONLY" = true ] && [ "$OUTDATED" -ne 0 ]; then
  exit 1
fi
