---
name: netty-review
description: Review Netty code changes (the current diff, a branch, or a PR) for bugs, security vulnerabilities, thread-safety problems, native/JNI issues and style, using Netty's project-specific checklists. Use when asked to review a change, diff, branch or PR in this repository.
argument-hint: "[PR number | branch]"
---

Review the current changes for bugs, security, and thread-safety issues.

This skill is read-only, and that rule supersedes everything else in it, including every checklist and sub-agent briefing. Do not edit files, run builds or tests, or apply fixes. Deciding what to do with the report is up to the user.

## Scope

Determine what to review based on context:
1. If arguments specify a PR number or branch, review those commits against the base branch. For a PR, the base is the PR's target branch.
2. Otherwise, review the current diff (staged + unstaged changes) against the base branch. The base is the branch the current branch was created from; if that is unclear, use `4.2`, the default branch.
3. Use `git diff` to obtain the changed code, then read full files for surrounding context where needed

## Instructions

Examine every changed line (and its surrounding context) for the defects described in the checklists below.

### Checklists

Read the checklist files that apply to the diff, from the `references/` directory next to this file. Don't load checklists that don't apply.

| File | Read when the diff... |
| ---- | --------------------- |
| `general.md` | touches any Java code (always) |
| `tests-and-style.md` | touches any Java code (always); also check tests against the rules in `AGENTS.md` |
| `security-general.md` | handles network input, allocates based on input sizes, accumulates per-connection state, retains or releases buffers in codecs, logs, builds file paths, reads or creates local files, implements access-control filters, uses serialization or XML |
| `security-protocols.md` | touches a codec or protocol implementation (HTTP/1.1, HTTP/2, HTTP/3, QUIC, DNS, MQTT, Redis, Memcache, SMTP, SOCKS, STOMP, HAProxy, XML). Read only the sections for the protocols touched |
| `security-tls.md` | touches TLS, trust or key managers, certificates, OCSP, SNI or ClientHello parsing, SSL contexts, or OpenSSL/BoringSSL code |
| `concurrency.md` | touches shared mutable state, locks, channel handlers (also for handler reentrancy), event loops, executors or futures |
| `native-jni.md` | touches C or header files, or Java `native` declarations |
| `variant-analysis.md` | fixes a security vulnerability: the commit or PR text mentions a CVE, GHSA, vulnerability or security issue, or the diff adds validation, a limit or a rejection for attacker-controlled input. If in doubt whether the change is a security fix, apply it |

### Splitting the review across sub-agents

If your runner supports sub-agents, and the diff is large or spans several areas of the table above (as a rough guide: more than ~500 changed lines, or three or more checklists apply), split the review instead of doing it in one pass. For small diffs, review in a single pass; the overhead isn't worth it.

1. **Triage (you):** obtain the diff, list the changed files, and decide which checklists apply using the table above.
2. **Review in parallel:** start one reviewer sub-agent per applicable group, all at once:
   - `general.md` + `tests-and-style.md`
   - `security-general.md` + the relevant sections of `security-protocols.md`
   - `security-tls.md`
   - `concurrency.md`
   - `native-jni.md`
   - `variant-analysis.md`, when the change fixes a security vulnerability. This reviewer runs regardless of the size thresholds above, and should use a high-capability model (for example Claude Opus 5.5 or GPT-6-Sol, or an equivalent or better model); if the runner can't select a model, run it anyway. In addition to the items below, its briefing must include the fix's commit or PR description and any linked issue or advisory, and the instruction to include a failing test case with each finding. Without sub-agents, do this analysis yourself after the other checklists.

   Sub-agents start with no context, so each briefing must be self-contained:
   - the base branch and the exact `git diff` command to obtain the changes
   - the files in scope
   - the checklist file(s) to read, and only those
   - that it should read full files for surrounding context
   - the Verification rules below, and the Output Format
   - that it is read-only: no edits, no builds, no tests
   - that it should stay within its checklist, but may report an obvious defect it comes across
3. **Verify independently:** for every CRITICAL and HIGH finding, start a separate verifier sub-agent. Give it only the finding and its location, and ask it to try to refute it against the actual code. Drop findings that are refuted. For variant-analysis findings, the verifier must also check that any proposed test compiles against the existing API and would actually fail on the current code. Try to refute MEDIUM and LOW findings yourself in the same way; the point is to attempt refutation, not to confirm.
4. **Consolidate (you):** merge duplicates raised by more than one reviewer, order by severity, and produce the final Summary and Findings in the Output Format. Sub-agents report to you, not to the user.

### Verification

Before reporting a finding, verify its veracity and avoid reporting findings that aren't actually problems:
- Check that bugs aren't prevented by prior checks and conditions earlier in the code path
- If you make assumptions during the review, verify that they hold
- Double-check that any issues around boundary conditions of numeric overflow and casts aren't already handled
- For apparent data-races, verify that the code will actually experience multithreaded access

DON'T REPORT FINDINGS THAT TURN OUT TO BE NON-ISSUES UPON CLOSER REVIEW.

## Output Format

Structure your review as:

### Summary
One paragraph: overall assessment of the correctness in these changes.

### Findings

For each issue found, report:

**[SEVERITY] Category — short title**
- **Location:** `file:line`
- **Problem:** What the bug is and how it can manifest (e.g. for concurrency issues, a specific interleaving or timing)
- **Suggestion:** Concrete fix (code sketch if helpful)

Variant-analysis findings additionally include the fields described in `variant-analysis.md` (root cause link, specification reference, and a failing test case).

Severity levels:
- **CRITICAL** — Unsafe memory access; undefined behavior; data-race or deadlock; parsing bugs on non-trusted inputs (e.g., from the network); high-severity security issue, including: request smuggling, authentication or TLS validation bypass, credential or key material exposure, deserialization RCE, decompression or XML bomb, log injection of sensitive data
- **HIGH** — Likely to produce incorrect behavior; logic errors; parsing bugs on trusted inputs
- **MEDIUM** — Defensively questionable; correct today but fragile or relying on undocumented assumptions
- **LOW** — Minor concern or style issue

Remember to review and verify findings before reporting them.
Verification is analysis only, as reviewing is a read-only activity; running tests, etc. is for the CI.
Don't report findings that turn out to be non-issues.

If no issues are found, say so explicitly, and include a brief summary of the soundness of the design or changes.

$ARGUMENTS
