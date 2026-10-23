# Security: resource consumption, input handling, serialization

## Attacker-Controlled Resource Consumption

Any value read from the network that controls how much memory, CPU, or I/O is consumed is a potential denial-of-service vector. Review every allocation whose size or iteration count is derived from network data:

- **ByteBuf allocation:** `ctx.alloc().buffer(n)` where `n` comes from a decoded length field must be guarded by a configurable maximum. A negative or extremely large value causes an OOM-quality allocation attempt. Always check `0 <= n && n <= MAX_ALLOWED_SIZE`; use `ByteBuf.readUnsignedShort()` / `readUnsignedInt()` for fields that are semantically unsigned.
- **Array / collection pre-sizing:** Don't pass a network-supplied count to `new byte[n]`, `new ArrayList<>(n)`, `new HashMap<>(n)`, or similar. A sender can declare millions of elements before sending any data, forcing a large allocation before any validation occurs. Grow lazily or cap before allocating.
- **Integer overflow in size arithmetic:** When computing a size from multiple network-supplied values (e.g., `count * elementSize`), use `Math.multiplyExact` or check `count <= MAX / elementSize` before multiplying, or do the multiplication as `long` if both variables are of `int` type; a wrapped result can appear small enough to bypass the cap check. Watch out for overflow in _addition_ operations as well, when the inputs are potentially attacker controlled.
- **Decompression bombs:** Any use of `codec-compression` decoders (gzip, zlib/deflate, Brotli, LZ4, Snappy, Zstd, BZip2, FastLZ) must configure a maximum output size; leaving it unbounded allows an attacker to exhaust heap with a tiny compressed input. The same applies to `Content-Encoding` decompression in HTTP handlers.
- **XML bombs:** see the XML section in `security-protocols.md`.
- **Loops and recursion bounded by network data:** Any loop whose iteration count or recursion depth is derived from a network-supplied value (e.g., a CNAME chain, a nested message structure) must enforce an explicit maximum.

## Input Handling and Sensitive Data

### Path Traversal
- Never incorporate network-supplied data into a file path without first canonicalizing it (`Path.toRealPath()` or `File.getCanonicalPath()`) and verifying the result is still under the intended root
- Reject or strip path components containing `..`, URL-encoded variants (`%2F`, `%2e%2e`), and null bytes before any path construction

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
