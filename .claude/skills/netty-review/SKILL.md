---
name: netty-review
description: Review Netty code changes (the current diff, a branch, or a PR) for bugs, security vulnerabilities, thread-safety problems, native/JNI issues and style, using Netty's project-specific checklists. Use when asked to review a change, diff, branch or PR in this repository.
argument-hint: "[PR number | branch]"
---

Review the current changes for bugs, security, and thread-safety issues.

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
| `security-general.md` | handles network input, allocates based on input sizes, logs, builds file paths, uses serialization or XML |
| `security-protocols.md` | touches a codec or protocol implementation (HTTP/1.1, HTTP/2, HTTP/3, QUIC, DNS, MQTT, Redis, Memcache, SMTP, SOCKS, STOMP, HAProxy, XML). Read only the sections for the protocols touched |
| `security-tls.md` | touches TLS, trust or key managers, certificates, SSL contexts, or OpenSSL/BoringSSL code |
| `concurrency.md` | touches shared mutable state, locks, channel handlers, event loops, executors or futures |
| `native-jni.md` | touches C or header files, or Java `native` declarations |

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
