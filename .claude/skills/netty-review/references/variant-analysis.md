# Variant analysis for security fixes

When a change fixes a security vulnerability, the fix itself is only half the review. The same root cause often has sibling instances: other paths into the vulnerable code, other inputs that trigger the same bug, or neighboring code with the same flaw. This checklist is for finding those.

Work from the root cause outward. Do not just re-review the changed lines.

## 1. State the root cause

Before searching for variants, write down in one or two sentences:
- the invariant that was violated (e.g. "a declared length was trusted before being checked against the cap")
- the attacker-controlled input that violates it
- the sink where the damage happens (allocation, parser state, header emission, log line, native memory access, ...)

Every variant search below is derived from this statement. If you can't state the root cause from the diff, read the commit/PR description and the linked issue or advisory, and say so if it is still unclear.

## 2. Different paths into the same code

Find every route by which attacker-controlled data can reach the vulnerable code, not only the one the fix addresses:
- All callers of the changed method, and all callers of its callers up to the network boundary
- Sibling implementations of the same logic: HTTP/1.1 vs HTTP/2 vs HTTP/3, client-side vs server-side decoders, decoder vs encoder, Java vs native implementations, JDK `SSLEngine` vs OpenSSL/BoringSSL engine
- Alternate buffer types and states: heap vs direct, composite, slices/duplicates/read-only views, non-zero reader index, `Unsafe` vs non-`Unsafe` code paths
- Alternate framing of the same bytes: input split across reads at every possible boundary, pipelined messages, one byte at a time, trailers, continuation frames, upgrade paths (e.g. h2c), proxied/tunneled traffic
- Alternate configuration: default vs non-default decoder settings, limits set to their maximum or to "unlimited"
- Error and cleanup paths: what state is the decoder/connection in after the fix rejects the input? Is the buffer released, is the connection closed, can the peer continue to send on the same connection and reach the code again?

## 3. Different inputs that trigger the same or a similar bug

Take the input that triggers the bug and mutate it:
- Boundary values: 0, 1, -1, `MAX`, `MAX + 1`, `Integer.MAX_VALUE`, `Integer.MIN_VALUE`, values that overflow when added or multiplied, signed vs unsigned interpretation
- Different representations of the same thing: case variants, percent-encoding, duplicate and repeated fields, empty values, whitespace variants, line-folding, bare CR / bare LF, non-ASCII bytes, NUL
- Omission and ordering: missing fields, fields in a different order, repeated fields with conflicting values
- Truncation: input that ends exactly at, one before, and one after each boundary the parser checks
- Compression and encoding layers: the same malicious payload inside a compressed or encoded body
- Accumulation: no single value exceeds the limit, but the sum, count or total over time does

## 4. Sibling code with the same flaw

- Search the repository for the same pattern (same helper, same idiom, copy-pasted logic) in other modules and other protocols. Use `grep` for the exact construct, not only the method name.
- Check history for earlier fixes of the same class of bug (`git log -S`, `git log --grep`). Were they applied to all siblings, or only to the one that was reported?
- Check whether the fix is complete within its own code: all branches, all limits, all error paths. Look for the check being performed on a different value than the one later used (time-of-check vs time-of-use, check before decoding vs use after decoding).
- Check whether the fix is applied everywhere its logic is needed:
  - constructors but not setters or mutators
  - the HTTP/1.1 path but not the HTTP/1.0, RTSP or WebSocket handshaker branches
  - the decoder but not the encoder
  - the failing branch but not its sibling limit or the nested case
  - the server codec but not the client codec, or HTTP/2 but not HTTP/3

## 5. Related uncovered edge cases

- Read the tests added by the fix. Which neighboring behaviors are not covered?
- Look for adjacent requirements in the specification that the code (or the fix) does not enforce.

## 6. Consult the specifications

Base recommendations and exact behaviors on the specification, not on memory or on what other implementations do. Use the latest revision of each RFC, and check whether the RFC has been obsoleted or updated by a newer one before relying on it.

Reference points (verify that nothing newer applies):
- HTTP semantics, HTTP/1.1, HTTP/2, HTTP/3: RFC 9110, 9112, 9113, 9114 (and RFC 9111 for caching)
- Header compression: HPACK RFC 7541, QPACK RFC 9204
- QUIC: RFC 9000 (transport), RFC 9001 (TLS), RFC 9002 (loss detection)
- TLS 1.3: RFC 8446
- WebSocket: RFC 6455; over HTTP/2 RFC 8441; over HTTP/3 RFC 9220
- DNS: RFC 1035 and its updates
- SMTP: RFC 5321, message format RFC 5322
- SOCKS5: RFC 1928, RFC 1929
- MQTT, STOMP, Redis RESP, Memcache, and the HAProxy PROXY protocol: use the respective project or standards-body specification

Rules for citing specifications:
- Cite RFC and section number for every behavior you rely on.
- Only quote or cite a section you have actually read. If you cannot access the specification, say so in the finding and mark the citation as unverified; don't reconstruct section text from memory.

## 7. Reporting

Report each variant as a normal finding in the Output Format, plus:

- **Root cause link:** which part of the root cause statement (step 1) this variant shares.
- **Specification:** RFC and section that defines the correct behavior, when one applies.
- **Test case:** a test that fails unless the finding is fixed.
  - It must be copy-pasteable into the most suitable *existing* test class. Name that class by path, and list any imports and helpers the snippet needs. Look the class up; don't guess its name.
  - Follow the test rules in `AGENTS.md` (JUnit 5, AssertJ, no mocking unless the class already does, no sleeps) and the resource rules in `tests-and-style.md` (release buffers, close channels).
  - State what the unfixed code does and what the fixed code must do, so the failure is meaningful.
  - Reviewing is read-only, so do not run the test. Say that it has not been run, and explain by tracing the code why it fails today.
- If a finding cannot be expressed as a failing test (for example a design-level gap), say so and explain why, rather than omitting the test silently.

If no variants are found, say so explicitly and list what you examined (paths, inputs, sibling code, specification sections), so that a negative result can be audited.
