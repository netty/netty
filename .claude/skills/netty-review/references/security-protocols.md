# Security: protocols

## Protocol Security

### HTTP/1.1 — Request Smuggling and Header Injection

- **Transfer-Encoding / Content-Length conflict:** If both headers are present the implementation must reject the request (RFC 9112 §6.3); a frontend that honors `Content-Length` while a backend honors `Transfer-Encoding` is the canonical request-smuggling vector
- **Bare CR without LF:** A bare `\r` not immediately followed by `\n` in the request line or a header must be rejected; parsers that accept it see different header boundaries than a strict downstream
- **Whitespace before the header-name colon:** `Header : value` (space before `:`) is illegal; a parser that silently accepts it processes the header while a stricter proxy drops it, enabling header hiding
- **Obs-fold (header line folding):** Continuation lines (a line beginning with whitespace following a header value) are deprecated (RFC 9112 §5.2); a parser that treats a continuation line as a new header enables header injection
- **CRLF injection in response headers / status lines:** Never write a network-supplied value into an HTTP response header without rejecting or stripping embedded `\r` and `\n`
- **Obfuscated Transfer-Encoding:** Values like `Transfer-Encoding: chunked, identity` or `Transfer-Encoding: xchunked` may normalize to `chunked` in one parser and be unrecognized in another; the implementation must handle these consistently with what downstream infrastructure will see
- **Header name character set:** Header names must be valid tokens (RFC 9110 §5.6.2): alphanumerics and the characters `!#$%&'*+-.^_`|~` only; control characters, spaces, and delimiter characters such as `( ) < > @ , ; : \ " / [ ] ? = { }` are forbidden. Use `HttpHeaderValidationUtil.validateToken()` and reject any request or response containing an invalid header name.
- **Header value character set:** Header values must match the `field-value` rule (RFC 9110 §5.5): printable ASCII (0x21–0x7E), SP, HTAB, and high bytes (0x80–0xFF) only; all other control characters including NUL, CR, and LF are forbidden. Use `HttpHeaderValidationUtil.validateValidHeaderValue()` and reject values that contain illegal characters.
- **`HttpDecoderConfig` security settings:** Several security-relevant validations in `HttpObjectDecoder` are opt-in for compatibility reasons and are controlled via `HttpDecoderConfig`. New code must not disable or bypass them, and must not introduce decoder construction paths that fall back to unsafe defaults:
  - `setValidateHeaders(false)` disables header name and value character validation — leave at the default (`true`)
  - `setStrictLineParsing(false)` accepts bare LF as a line separator, enabling the smuggling vectors described above — leave at the default (`true`)
  - `setUseRfc9112TransferEncoding(false)` restores the RFC 7230 lenient behavior of silently dropping `Content-Length` when `Transfer-Encoding` is present, rather than rejecting the message — leave at the default (`true`)
  - `setAllowDuplicateContentLengths(true)` permits multiple `Content-Length` headers, a known request-splitting indicator — leave at the default (`false`)

### HTTP/2 and HTTP/3 / QUIC

Most HTTP/2 and HTTP/3 security concerns are symmetric; protocol-specific items are called out explicitly.

**Header field validation:**
- `Connection`, `Transfer-Encoding`, `Upgrade`, `Keep-Alive`, and `Proxy-Connection` are forbidden in HTTP/2 and HTTP/3 (RFC 9113 §8.2.2 / RFC 9114); receiving them is a stream error (`PROTOCOL_ERROR`)
- Pseudo-headers (`:method`, `:path`, etc.) must appear before all regular headers; duplicate pseudo-headers, unknown pseudo-headers, and pseudo-headers in trailers must all be rejected
- These constraints apply equally to `PUSH_PROMISE` (HTTP/2) and server-initiated pushes
- **Lowercase header names:** All HTTP/2 and HTTP/3 field names must be lowercase (RFC 9113 §8.2.1 / RFC 9114 §4.2); a field name containing uppercase characters is malformed and must be treated as a stream error (`PROTOCOL_ERROR`). Use `HttpHeaderValidationUtil.validateToken()` and verify no uppercase ASCII is present.
- **Header name and value character set:** The same token rule for names and `field-value` rule for values applies as in HTTP/1.1; use `HttpHeaderValidationUtil.validateToken()` and `HttpHeaderValidationUtil.validateValidHeaderValue()` respectively.
- **TE header:** In HTTP/2, `TE` is the one connection-specific header that is permitted, but only with the value `trailers` (RFC 9113 §8.2.2); any other value is a stream error. Use `HttpHeaderValidationUtil.isTeNotTrailers()` to detect violations. Note that `isConnectionHeader(name, /*ignoreTeHeader=*/true)` is designed specifically to let `TE` pass the connection-header check so that it can be validated separately.

**Header compression bombs:**
- **HTTP/2 (HPACK):** A compact HPACK block can expand into megabytes via table references; enforce `SETTINGS_MAX_HEADER_LIST_SIZE` and reject header lists that exceed it during decompression, before allocating storage for them
- **HTTP/3 (QPACK):** The same concern applies; enforce a maximum decompressed header list size during QPACK decoding

**HTTP/2 — frame-level attacks:**
- **CONTINUATION flood (CVE-2024-27983):** A stream of `CONTINUATION` frames without `END_HEADERS` forces unbounded header accumulation; cap total accumulated header bytes (or frame count) before `END_HEADERS` and close the connection if exceeded
- **RST_STREAM rapid reset (CVE-2023-44487):** A client that repeatedly opens streams and immediately sends `RST_STREAM` can force per-stream work without completing any request; rate-limit `RST_STREAM` per connection and close connections that exceed the threshold
- **SETTINGS flood:** Bound the number of unacknowledged `SETTINGS` frames; a flood without corresponding `SETTINGS_ACK` can force unbounded state buffering
- **Zero WINDOW_UPDATE increment:** A `WINDOW_UPDATE` with increment 0 is a stream/connection error (RFC 9113 §6.9); reject it, don't silently ignore
- **WINDOW_UPDATE overflow:** A `WINDOW_UPDATE` that causes the flow-control window to exceed 2^31-1 octets must be treated as a stream error (`RST_STREAM` with `FLOW_CONTROL_ERROR`) or connection error (`GOAWAY` with `FLOW_CONTROL_ERROR`) as appropriate (RFC 9113 §6.9.1)
- **Padded frames:** A padding length equal to or exceeding the payload length is a `PROTOCOL_ERROR`
- **MAX_CONCURRENT_STREAMS:** Opening more streams than the negotiated limit is a connection error, not something to queue

**QUIC-specific:**
- **Anti-amplification limit:** Before a client address is validated, the server must not send more than 3× the bytes received from that client (RFC 9000 §8.1); violating this turns the server into a DDoS amplifier against spoofed source addresses
- **Retry tokens:** Retry tokens must be cryptographically bound to the client address and must have a short expiry; a token that is neither bound nor time-limited allows off-path injection and replay
- **Stateless reset tokens:** Stateless reset tokens must be unpredictable; a guessable token allows an on-path attacker to tear down arbitrary connections
- **0-RTT replay:** Application data sent in 0-RTT can be replayed by a network attacker; flag any state-changing operations accepted in 0-RTT that are not idempotent
- **Connection ID unpredictability:** Connection IDs must be cryptographically random; predictable CIDs allow off-path packet injection

**HTTP/3-specific:**
- **Stream count enforcement:** A peer that opens more streams than `MAX_STREAMS` allows has committed a connection error; don't silently queue the excess
- **Extended CONNECT (WebSocket over HTTP/3):** Validate the `:protocol` pseudo-header; an absent or empty value when `CONNECT` is used is a stream error

### DNS

- **Compression pointer loops:** DNS name compression pointers (`0xC0 xx`) can form cycles; a parser that follows pointers without tracking visited offsets will loop forever. Limit total pointer hops and fail if exceeded.
- **Name and label length limits:** A fully-qualified DNS name is at most 255 bytes in wire format; each label is at most 63 bytes; validate during parsing and reject over-length names
- **Bailiwick / additional-section poisoning:** A DNS response may include extra records in the Additional section for names unrelated to the query; reject out-of-bailiwick records to prevent cache poisoning
- **CNAME chain length:** Enforce a maximum CNAME chain depth to prevent unbounded recursive resolution

### MQTT

- **Wildcards in publish topic names:** `#` and `+` are subscription-only wildcards; a PUBLISH to a topic containing them must be rejected (`TOPIC_NAME_INVALID`)
- **Maximum packet size:** Enforce the configured or negotiated maximum packet size; close the connection if exceeded rather than accumulating unbounded data
- **UTF-8 validation:** MQTT strings must be valid UTF-8 and must not contain `U+0000`; null bytes in topic names can bypass topic-filter matching

### Redis (RESP)

- **Array header pre-allocation:** The `*<count>` prefix declares how many bulk strings follow; don't pre-allocate storage using the network-supplied count — a sender can declare millions of elements before sending any data
- **Bulk string length:** The `$<len>` prefix must be capped at a configured maximum before allocating a buffer
- **Inline command injection:** If inline (non-RESP) command mode is supported, embedded CR or LF in an argument value injects additional commands; reject inline input containing `\r` or `\n`

### Memcache

- **Declared body length (binary protocol):** The binary protocol header contains a 32-bit total body length field; cap this before allocating a buffer
- **Key validation (text protocol):** Memcache keys must not contain whitespace, CR, or LF; keys containing these bytes can inject additional commands in the text protocol

### SMTP

- **CRLF injection in envelope fields and headers:** Untrusted data in `MAIL FROM`, `RCPT TO`, or message headers must not contain `\r\n`; an injected CRLF followed by a new verb turns one SMTP transaction into multiple
- **SMTP smuggling:** Some SMTP servers accept bare CR or bare LF as line endings in the DATA phase; a sender that normalizes these differently than the receiving server enables DATA-phase injection. Validate that `\r\n` sequences in message data are canonical and that lone `\r` / `\n` are handled consistently.

### SOCKS

- **Declared length fields:** SOCKS5 username/password authentication includes 1-byte length fields; the decoder must read exactly the declared number of bytes and reject a declared length that contradicts what follows
- **Address type validation:** Unknown `ATYP` values must produce a protocol-level error response, not a parsing attempt that may read past the intended frame boundary

### STOMP

- **Content-Length vs. NUL-byte framing:** STOMP frames optionally include a `content-length` header; when present it must take precedence over NUL-byte body termination. A mismatch in how two parties in the same pipeline resolve this ambiguity is a frame-injection vector analogous to HTTP/1.1 request smuggling.
- **Header injection:** STOMP header values use escape sequences (`\n`, `\r`, `\c`, `\\`); raw CR or LF bytes in a header value must be rejected; decoded escape sequences must not be re-serialized in a way that allows injecting additional header lines

### HAProxy PROXY Protocol

- **v1 bare-LF:** The v1 text format terminates the header with CRLF; a parser that also accepts bare LF creates inconsistencies with stricter consumers
- **v2 address-data length:** The v2 binary format includes a 2-byte length for the address block; cap this before allocating a buffer

### XML

- **External entity (XXE) injection:** XML parsers must disable external entity resolution and DTD loading; an `ENTITY` declaration pointing to a local file or network resource enables reading arbitrary files or making SSRF requests. Explicitly set `XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES = false` and `XMLInputFactory.SUPPORT_DTD = false` on any `XMLInputFactory` or `DocumentBuilderFactory` used in codec-xml handlers.
- **Entity expansion (billion laughs):** Even without external entities, recursive entity definitions can expand to exponentially large strings; if DTD support must remain on, enforce a maximum entity-expansion depth and output size (see also: Attacker-Controlled Resource Consumption in `security-general.md`)
