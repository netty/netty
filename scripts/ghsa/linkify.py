#!/usr/bin/env python3
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
"""Turn every GHSA ID in a markdown file into a link, then verify the result.

Usage: linkify.py [MARKDOWN_FILE]   (defaults to .github/ghsa/ghsa-dupes.md)

Idempotent. Exits non-zero if any verification count is not zero.
"""
import os
import re
import sys

DEFAULT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".github", "ghsa", "ghsa-dupes.md")
path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT

ID = r'GHSA-[0-9a-z]{4}-[0-9a-z]{4}-[0-9a-z]{4}'
with open(path) as fh:
    s = fh.read()

# 1. Strip backticks around any GHSA ID (bare or already linked) -- they would kill the link.
s = re.sub(r'`(' + ID + r')`', r'\1', s)
s = re.sub(r'`(\[' + ID + r'\]\([^)]*\))`', r'\1', s)

# 2. Linkify every remaining bare ID. The lookbehind skips IDs that are already
#    link text ("[ID]") or already inside the URL ("/ID"), so this is idempotent.
s = re.sub(r'(?<![\[/])' + ID,
           lambda m: f'[{m.group(0)}](https://github.com/netty/netty/security/advisories/{m.group(0)})', s)

with open(path, "w") as fh:
    fh.write(s)

lines = s.splitlines()
checks = {
    "backticked links": sum(bool(re.search(r'`[^`]*\[GHSA-[^`]*`', l)) for l in lines),
    "double-wrapped": sum('[[GHSA-' in l for l in lines),
    "unlinked": len(re.findall(r'(?<![\[/])' + ID, s)),
}
for name, count in checks.items():
    print(f"{name}: {count}")
sys.exit(1 if any(checks.values()) else 0)
