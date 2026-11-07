---
description: Find duplicate reports among the GitHub private vulnerability (GHSA) exports and write .github/ghsa/ghsa-dupes.md
argument-hint: "[use existing files]"
allowed-tools: Bash(scripts/ghsa/*), Bash(scripts/collect-ghsa-reports.sh), Read, Write, Edit, Agent
---

# GHSA Duplicate Detection

Find duplicate reports among the GitHub private vulnerability (GHSA) exports in
`.github/ghsa`. The output of a run is `.github/ghsa/ghsa-dupes.md`.

All paths are relative to the repository root; run the scripts from there. The helper
scripts live in `scripts/ghsa/` and are run as-is -- **do not rewrite or inline them**.

User arguments: `$ARGUMENTS`

## Goal

Given the JSON exports of GitHub Security Advisory reports (in `triage` and `draft`
states), identify clusters of reports that describe the **same underlying vulnerability**,
so they can be de-duplicated during triage. The `published` advisories are searched as
well, but only in one direction: an open report may turn out to duplicate something already
published. Published advisories are set in stone and are never changed by this exercise.

**Definition of a true duplicate** (the bar every cluster must clear):

> Two or more reports share the **same root cause** AND the **same vulnerable code
> location / function** AND essentially the **same attack and impact**.

Explicitly **not** duplicates:
- Same component/file but a *different* function or root cause.
- Different protocol or code path (e.g. HTTP/1 codec-http vs HTTP/2 codec-http2) even if
  the *class* of bug is identical ("sibling" findings).
- Most "incomplete fix" variants -- they usually target a different code path than the
  original. Only group them if they point at the same line / root cause.

When in doubt, keep them separate and add a note. It is cheaper to under-merge (flag an
overlap for a human) than to wrongly collapse two distinct vulnerabilities.

## Inputs

- `.github/ghsa/triage/GHSA-*.json` -- reports in the triage state.
- `.github/ghsa/draft/GHSA-*.json` -- reports in the draft state.
- `.github/ghsa/published/GHSA-*.json` -- published advisories, read-only reference: an open
  report matching one of these is a re-report of a known CVE.

`.github/ghsa/closed/*.json` is **not** an input. Closed reports were already dispositioned
as duplicates or as not relevant, and a real duplicate of one would have its original in
`draft` or `published` anyway.

All three input directories share the same JSON schema. Each file is named
`<GHSA-ID>.json`. Relevant fields:

| Field | Use |
|-------|-----|
| `ghsa_id` | Identity. |
| `summary` | One-line title -- primary clustering signal. |
| `description` | Full technical detail: root cause, vulnerable file/function, PoC, fix. **This is what confirms a duplicate.** |
| `vulnerabilities[].package` + `vulnerable_version_range` | Component -- primary clustering signal. |
| `cwe_ids` | Weak signal for clustering. |
| `created_at` | Ordering within a cluster (oldest = presumptive original). |
| `author.login` | Provenance; helps spot maintainer-authored drafts. |
| `state` | `triage` / `draft` (open, actionable) vs `published` (reference only). |
| `cve_id` | Set on published advisories -- the handle to cite when an open report duplicates one. |

The bulk of each file is author/URL boilerplate -- ignore it.

## Why not just read everything

At over 150 reports and several MB of raw JSON, the full corpus is large and mostly boilerplate.
The efficient path is: **extract a tiny index, cluster from the index, then read only the
full descriptions of candidate clusters** (fan out to sub-agents so no single context
holds all descriptions).

## Procedure

### Step 0 -- Make sure the extracted reports are fresh

1. Run `scripts/ghsa/check-freshness.sh`. It exits 0 if every extracted `GHSA-*.json` file is
   at most 4 hours old (override with `GHSA_MAX_AGE_MINUTES`), 1 if some file is older, and 2 if
   there are no files at all.
2. Exit 0: continue to step 1.
3. Exit 1 or 2: run `scripts/collect-ghsa-reports.sh`. It needs `gh` (authenticated against
   `netty/netty` with access to security advisories) and `jq`, and it first deletes every
   `*.json` file under `.github/ghsa` before re-downloading, so it can take a while.
4. If the collect script succeeds, run `scripts/ghsa/check-freshness.sh` again and continue
   only once it exits 0.
5. If the collect script fails (missing tool, no auth, no network, sandbox restrictions, ...),
   do not try to work around it. Tell the user what failed and ask them to run it
   themselves, then continue once they confirm.
   The only exception is when the user explicitly insists on deduplicating the
   existing files (including via the arguments above): then skip the refresh, say in the final
   report that the data may be outdated, and proceed. Because the script deletes the old files
   before downloading, a failed run may leave the directory empty or partial: re-run
   `scripts/ghsa/check-freshness.sh` and, if it exits 2, there is nothing to deduplicate, so
   the user must run the collect script.

If the user's arguments already say to use the existing files, skip this step.

### Step 1 -- Extract a compact index

Run `python3 scripts/ghsa/extract-index.py`. It walks the three input directories and writes:

- `/tmp/reports.json` -- per report: `ghsa_id`, `state`, `created_at`, `severity`,
  `author`, `cve_id`, `cwe_ids`, `packages`, `summary`, `description`. Sorted by `created_at`.
- `/tmp/index.json` -- the same **minus `description`** (the scannable overview).

It prints the number of reports per state; sanity-check that against the directories.

### Step 2 -- Cluster candidates from the index

Read `/tmp/index.json` and group reports into **candidate clusters** by (a) affected
package/component and (b) summary/mechanism. Err toward *over*-grouping here -- this stage
only decides what to read together; false candidates get rejected in step 3.

Cluster **across states**: a `triage` report and a `published` advisory on the same
component belong in the same candidate cluster. Drop any candidate cluster made up
entirely of `published` advisories -- duplicates among those are not actionable. Every
cluster carried into step 3 must contain at least one `triage` or `draft` report.

Watch for these recurring, easy-to-confuse families (they need care in step 3):
- Multiple "incomplete fix of CVE-XXXX" reports on the same component.
- Protocol-downgrade / request-smuggling variants (H1/H2/H3, `:authority`/`Host`/`:path`,
  Transfer-Encoding, chunk-size parsing).
- `maxAllocation` / decompression-bomb reports across different codecs (br/zstd/snappy,
  websocket deflate, bzip2, lz4).
- QPACK / HPACK DoS variants.

### Step 3 -- Confirm clusters by reading full descriptions (fan out)

Split the candidate clusters across several sub-agents (about 4 worked well; keep related
clusters with the same agent so it has the context to judge). Each agent reads the full
`description` of every report in its assigned clusters directly from the JSON files and
returns, per cluster:

1. Which GHSA-IDs are **true duplicates** (grouped), and which stand alone.
2. For every report: `ghsa_id`, `created_at`, `author`, `state`, and `cve_id` if set.
3. A 1-2 sentence rationale citing the **specific shared or differing root cause /
   vulnerable function / file**.

Give every agent the same duplicate definition (above). Instruct them to be
evidence-based, to explicitly call out non-duplicates, and to write no files.

**Boundary risk:** duplicates that straddle two agents' assignments can be missed. Keep
topically related clusters (e.g. all request-smuggling variants) with a single agent, and
in the assignment note tell each agent which neighboring reports exist so it can flag a
cross-boundary match.

### Step 4 -- Synthesize the report

Consolidate agent verdicts into `.github/ghsa/ghsa-dupes.md`:

- Keep only confirmed duplicate clusters (2+ reports).
- **Order reports within each cluster by `created_at`, oldest first**; mark the oldest as
  "(original)".
- Order the clusters themselves by their oldest member's `created_at`.
- For each cluster: a "Same bug:" paragraph (shared root cause + vulnerable function) and
  a table of `created_at | GHSA | state | author`.
- Give clusters where an open report duplicates a `published` advisory their own section --
  the disposition is "close as duplicate of <CVE/GHSA>", not a merge of two open reports.
- Add a **"Notable non-duplicates"** section listing the families that were considered and
  rejected, with a one-line reason each -- this shows they were examined, not missed.
- Flag caveats inline: near-duplicates/overlaps, reports that bridge two clusters, and
  maintainer-authored drafts (which may be the project's own canonical write-up rather than
  a true duplicate -- consider which report in a cluster should be called the "original").
- If step 0 was skipped because the user insisted on the existing files, say so at the top
  of the report, together with the age of the data if known.

**Never put a GHSA ID in backticks.** A markdown link inside a code span renders as literal
text, not a clickable link. Write GHSA IDs bare and let step 5 wrap them. Backticks are for
code identifiers only (file names, methods, fields); a GHSA ID is neither. Bold is fine:
`**[id](url)**` renders correctly.

### Step 5 -- Linkify and verify

Run `python3 scripts/ghsa/linkify.py`. It makes every `GHSA-####-####-####` reference in
`.github/ghsa/ghsa-dupes.md` a link to the advisory (idempotent), then verifies the result and
prints three counts -- backticked links, double-wrapped links, unlinked IDs. It exits non-zero
unless all three are zero; if it does, fix the markdown and re-run.

## Notes / gotchas

- **`created_at` is the tiebreaker for "original".** But a maintainer's `draft` may be an
  internal tracking advisory filed *after* an external report -- note this rather than
  blindly calling the earliest the original.
- **Sibling is not duplicate.** The same bug class in codec-http vs codec-http2 vs codec-http3,
  or in encode vs decode paths, is almost always two distinct findings.
- **Read the description, not just the summary.** Summaries of distinct bugs often look
  near-identical (e.g. several "SmtpResponseDecoder ... DoS" titles that are genuinely the
  same, vs several "Transfer-Encoding smuggling" titles that are not).
- Re-running is cheap: only the contents of the input directories change between runs.
  Re-run step 0, regenerate the index and repeat.
