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
"""Extract a compact index of the GHSA reports to deduplicate.

Usage: extract-index.py [OUT_DIR]   (OUT_DIR defaults to /tmp)

Writes OUT_DIR/reports.json (all fields, sorted by created_at) and
OUT_DIR/index.json (the same without 'description').
"""
import glob
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".github", "ghsa")
STATES = ["triage", "draft", "published"]  # closed is deliberately excluded
OUT_DIR = sys.argv[1] if len(sys.argv) > 1 else "/tmp"


def load(state):
    out = []
    for f in sorted(glob.glob(os.path.join(ROOT, state, "GHSA-*.json"))):
        with open(f) as fh:
            j = json.load(fh)
        pkgs = []
        for v in (j.get("vulnerabilities") or []):
            p = (v.get("package") or {})
            pkgs.append(f"{p.get('name')} ({v.get('vulnerable_version_range')})")
        out.append({
            "ghsa_id": j.get("ghsa_id"), "state": state,
            "created_at": j.get("created_at"), "severity": j.get("severity"),
            "author": (j.get("author") or {}).get("login"),
            "cve_id": j.get("cve_id"),
            "cwe_ids": j.get("cwe_ids"), "packages": pkgs,
            "summary": j.get("summary"), "description": j.get("description"),
        })
    return out


reports = [r for s in STATES for r in load(s)]
reports.sort(key=lambda r: r["created_at"] or "")

with open(os.path.join(OUT_DIR, "reports.json"), "w") as fh:
    json.dump(reports, fh, indent=1)
keys = ("ghsa_id", "state", "created_at", "severity", "author", "cve_id", "cwe_ids", "packages", "summary")
with open(os.path.join(OUT_DIR, "index.json"), "w") as fh:
    json.dump([{k: r[k] for k in keys} for r in reports], fh, indent=1)

print({s: sum(r["state"] == s for r in reports) for s in STATES}, "total:", len(reports))
