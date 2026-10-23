# Security: resource consumption, input handling, serialization

## Attacker-Controlled Resource Consumption

Any value read from the network that controls how much memory, CPU, or I/O is consumed is a potential denial-of-service vector. Review every allocation whose size or iteration count is derived from network data:

- **ByteBuf allocation:** `ctx.alloc().buffer(n)` where `n` comes from a decoded length field must be guarded by a configurable maximum. A negative or extremely large value causes an OOM-quality allocation attempt. Always check `0 <= n && n <= MAX_ALLOWED_SIZE`; use `ByteBuf.readUnsignedShort()` / `readUnsignedInt()` for fields that are semantically unsigned.
- **Array / collection pre-sizing:** Don't pass a network-supplied count to `new byte[n]`, `new ArrayList<>(n)`, `new HashMap<>(n)`, or similar. A sender can declare millions of elements before sending any data, forcing a large allocation before any validation occurs. Grow lazily or cap before allocating.
- **Integer overflow in size arithmetic:** When computing a size from multiple network-supplied values (e.g., `count * elementSize`), use `Math.multiplyExact` or check `count <= MAX / elementSize` before multiplying, or do the multiplication as `long` if both variables are of `int` type; a wrapped result can appear small enough to bypass the cap check. Watch out for overflow in _addition_ operations as well, when the inputs are potentially attacker controlled.
- **Decompression bombs:** Any use of `codec-compression` decoders (gzip, zlib/deflate, Brotli, LZ4, Snappy, Zstd, BZip2, FastLZ) must configure a maximum output size; leaving it unbounded allows an attacker to exhaust heap with a tiny compressed input. The same applies to `Content-Encoding` decompression in HTTP handlers.
- **XML bombs:** see the XML section in `security-protocols.md`.
- **Loops and recursion bounded by network data:** Any loop whose iteration count or recursion depth is derived from a network-supplied value (e.g., a CNAME chain, a nested message structure) must enforce an explicit maximum.
- **Loop progress:** Every iteration of a decode loop must consume input, change state, or fail. Look for states that return without consuming input and are re-entered unchanged, and for truncating casts of wire lengths (`long` to `int`) that produce a zero or negative length.
- **Per-connection accumulation:** Any list, map, queue, `CompositeByteBuf`, nested buffer or other accumulator that grows once per received frame, header, fragment, continuation line or setting needs both a count cap and a byte cap, even when each individual item is size-checked (for example multi-line SMTP responses, STOMP headers, SCTP fragments, SPDY settings, QPACK trackers).
- **Limits that are off by default:** A limit that is unbounded, zero or disabled in the default configuration is a finding. Check the default-constructed decoder or handler, not only the configurable one.
- **Limits that multiply:** Independently bounded limits can combine into a much larger worst case, e.g. maximum nesting depth times elements per level when each nested header eagerly preallocates. Check the product, not each limit in isolation.
- **Quadratic work:** Re-parsing or re-scanning all buffered input on every read (ClientHello reassembly, prefixed integers that never terminate, decoders that restart from the beginning) makes cost quadratic in attacker-controlled input size. Require incremental parsing or a bound on buffered bytes.
- **Signedness and sentinels:** An in-band sentinel such as `-1` applied to a signed `byte` or `short` read from the wire can collide with legitimate data (`0xFF`), and signed/unsigned mismatches between a declared length and the bytes consumed desynchronize framing.

## Reference Counting and Cleanup in Codecs

Leaked `ByteBuf`s on error paths have been a recurring source of remotely triggerable memory exhaustion.

- **Validate before retaining:** Don't call `retain*`, `readRetainedSlice` or `retainedDuplicate`, or allocate, until the input has been validated. A frame that is rejected after the retain leaks.
- **Every exit path:** For each exit from `decode` / `channelRead` (normal return, early return, exception, handler removal, `channelInactive`), every buffer that was retained or stored in handler state must be released exactly once.
- **Stateful handlers:** Handlers that hold partial aggregates or cumulation buffers need cleanup in `handlerRemoved`.
- **Nested structures:** Nested TLVs, arrays and similar structures must release children at every depth, including when a nested element fails.
- **Consistent limit handling:** When input is rejected because one limit is exceeded, reset the related aggregate state the same way every other limit-failure path does. A sibling limit that doesn't clear the state leaves retained buffers behind and corrupts the handling of later messages.
- **Helper channels:** Work handed to a helper channel (for example a decompressor) may arrive after that channel has already been closed; make sure the buffer is still released.

## Input Handling and Sensitive Data

### Path Traversal
- Never incorporate network-supplied data into a file path without first canonicalizing it (`Path.toRealPath()` or `File.getCanonicalPath()`) and verifying the result is still under the intended root
- Reject or strip path components containing `..`, URL-encoded variants (`%2F`, `%2e%2e`), and null bytes before any path construction

### Local Files
- Temp files that hold non-public data must be created with owner-only permissions. On branches whose Java baseline lacks `java.nio.file.Files`, set the permissions explicitly
- Reads of local files (such as `os-release`) need a size cap that counts every byte, including NUL bytes, and should be skipped on platforms where the file doesn't apply

### Logging and PII
- **Sensitive credentials:** Never log passwords, authentication tokens, or private keys in the clear. This includes HTTP `Authorization` and `Cookie` headers, MQTT passwords, Redis `AUTH` commands, SMTP credentials, and TLS private key material.
- **PII in logs:** Avoid logging data that may contain personally identifiable information — IP addresses, usernames, email addresses, message bodies, or other user-supplied content — without explicit justification. Be especially careful with exception messages that include decoded field values from network input.
- **Log injection:** Never write raw network-supplied data directly into log messages. A value containing embedded newlines (`\r`, `\n`) can forge additional log lines and mislead operators. Sanitize or quote values before including them in log output.
- **`toString()` and exception messages:** Implementations on message and header types, and error messages produced in codec error paths, must not surface field values that could contain credentials or PII.

## Serialization and Deserialization

The `codec-marshalling` codec and any code path that uses Java serialization (`ObjectInputStream`) are deprecated and known to be inherently unsafe when processing untrusted input. When reviewing changes to these codecs, the goal is to avoid making the current situation worse:
- Don't introduce new `readObject`, `readResolve`, or `readObjectNoData` implementations on any class that could serve as a deserialization gadget
- Don't remove or relax any existing class filtering or allowlisting
- Don't add new code paths that trigger deserialization of data arriving from untrusted sources

## Access-Control Logic

- ACL and filter code (`IpSubnetFilterRule`, `CorsHandler`) must be reviewed for IPv6 as well as IPv4. Mask the address with the subnet mask, not with the network address
- Rejection paths must actually reject. A short-circuit that an unusual input such as `Origin: null` can skip forwards unauthorized requests to the application. Header handling such as `Vary` must not overwrite existing values
