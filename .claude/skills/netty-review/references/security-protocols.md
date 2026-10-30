# Security: protocols

## Protocol Security

### All protocols: encoders and request builders

- Every encoder or request builder that writes caller-supplied text must reject CR, LF and NUL (and any other delimiter of its wire format) in that text, and must not depend on a validation flag that a caller can switch off. This applies to HTTP request lines and URIs, multipart filenames, proxy `CONNECT` headers, SMTP command names and arguments, SOCKS credentials, MQTT topics and client IDs, Redis and STOMP fields, DNS names, and HAProxy v1 addresses (including `AF_UNIX`).
- Validation that exists in a constructor must also exist in every setter or mutator that can change the same field afterwards (for example a constructor that rejects CRLF in a URI while `setUri()` accepts it).
- When a decoder validates a field, check that the corresponding encoder does too, and vice versa.

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
- **Chunked framing (RFC 9112 §7.1):** In the chunk-size line accept only hex digits followed by an optional valid chunk extension; reject whitespace after the digits and control characters. Chunk-extension quoted-strings and bare CR must be parsed according to the grammar
- **Transfer-Encoding across field lines:** `chunked` must be the final coding of the combined value, whether it is written as one comma-separated field, as several field lines, or folded. Use the same parsing for "is `chunked` present" and "is `chunked` final", so that the two checks can't disagree
- **Every decoder branch:** Apply the smuggling rules in every branch, not only the HTTP/1.1 main path: HTTP/1.0 (TE+CL coexistence), RTSP method and request-line parsing, and the WebSocket handshakers (the V07/V08 handshaker lacked Connection/Upgrade validation)
- **Leading bytes:** Only an initial CRLF may precede the start line; other leading control characters must be rejected
- **Request/response pairing:** Codecs that pair responses with requests (`HttpClientCodec`, `HttpServerCodec`) must not consume a queued request or method for a `1xx` interim response; otherwise a pipelined `HEAD` is paired with the wrong response and the body is mis-framed
- **Pipelining:** Handlers that enqueue per-request state (`HttpServerCodec`, `HttpContentEncoder`, `WebSocketServerExtensionHandler`) must bound the queue against pipelined requests

### HTTP/2 and HTTP/3 / QUIC

Most HTTP/2 and HTTP/3 security concerns are symmetric; protocol-specific items are called out explicitly.

**Header field validation:**
- `Connection`, `Transfer-Encoding`, `Upgrade`, `Keep-Alive`, and `Proxy-Connection` are forbidden in HTTP/2 and HTTP/3 (RFC 9113 §8.2.2 / RFC 9114); receiving them is a stream error (`PROTOCOL_ERROR`)
- Pseudo-headers (`:method`, `:path`, etc.) must appear before all regular headers; duplicate pseudo-headers, unknown pseudo-headers, and pseudo-headers in trailers must all be rejected
- These constraints apply equally to `PUSH_PROMISE` (HTTP/2) and server-initiated pushes
- **Lowercase header names:** All HTTP/2 and HTTP/3 field names must be lowercase (RFC 9113 §8.2.1 / RFC 9114 §4.2); a field name containing uppercase characters is malformed and must be treated as a stream error (`PROTOCOL_ERROR`). Use `HttpHeaderValidationUtil.validateToken()` and verify no uppercase ASCII is present.
- **Header name and value character set:** The same token rule for names and `field-value` rule for values applies as in HTTP/1.1; use `HttpHeaderValidationUtil.validateToken()` and `HttpHeaderValidationUtil.validateValidHeaderValue()` respectively.
- **TE header:** In HTTP/2, `TE` is the one connection-specific header that is permitted, but only with the value `trailers` (RFC 9113 §8.2.2); any other value is a stream error. Use `HttpHeaderValidationUtil.isTeNotTrailers()` to detect violations. Note that `isConnectionHeader(name, /*ignoreTeHeader=*/true)` is designed specifically to let `TE` pass the connection-header check so that it can be validated separately.

**Translation between HTTP versions:**
- `Host` and `:authority` must agree and be deduplicated. Reject duplicate or conflicting `Host` headers when converting between HTTP/2, HTTP/3 and HTTP/1.x
- An absolute-form request target must not have its authority overridden by a differing `Host` header when it is translated to `:authority`
- Authority-form `CONNECT` and Extended `CONNECT` must map to the correct pseudo-headers, and `:authority` must not be taken from a `Host` header
- Validate `content-length` against the body actually received, including requests that consist of a single `HEADERS` frame with `END_STREAM`. After conversion to HTTP/1.1 a mismatch becomes request smuggling

**Header compression bombs:**
- **HTTP/2 (HPACK):** A compact HPACK block can expand into megabytes via table references; enforce `SETTINGS_MAX_HEADER_LIST_SIZE` and reject header lists that exceed it during decompression, before allocating storage for them
- **HTTP/3 (QPACK):** The same concern applies; enforce a maximum decompressed header list size during QPACK decoding
- **HTTP/3 (QPACK) encoder stream:** Cap the length of literal names and values in encoder-stream instructions (for example Insert With Literal Name), the number of blocked streams, and the per-stream state the encoder retains while a peer withholds Section Acknowledgments
- **SPDY (zlib header blocks):** Stop inflating once the header size cap has been reached; truncating the output while the decompressor keeps expanding still consumes the memory

**HTTP/2 — frame-level attacks:**
- **CONTINUATION flood (CVE-2024-27983):** A stream of `CONTINUATION` frames without `END_HEADERS` forces unbounded header accumulation; cap total accumulated header bytes (or frame count) before `END_HEADERS` and close the connection if exceeded
- **RST_STREAM rapid reset (CVE-2023-44487):** A client that repeatedly opens streams and immediately sends `RST_STREAM` can force per-stream work without completing any request; rate-limit `RST_STREAM` per connection and close connections that exceed the threshold
- **SETTINGS flood:** Bound the number of unacknowledged `SETTINGS` frames; a flood without corresponding `SETTINGS_ACK` can force unbounded state buffering
- **Zero WINDOW_UPDATE increment:** A `WINDOW_UPDATE` with increment 0 is a stream/connection error (RFC 9113 §6.9); reject it, don't silently ignore
- **WINDOW_UPDATE overflow:** A `WINDOW_UPDATE` that causes the flow-control window to exceed 2^31-1 octets must be treated as a stream error (`RST_STREAM` with `FLOW_CONTROL_ERROR`) or connection error (`GOAWAY` with `FLOW_CONTROL_ERROR`) as appropriate (RFC 9113 §6.9.1)
- **Padded frames:** A padding length equal to or exceeding the payload length is a `PROTOCOL_ERROR`
- **MAX_CONCURRENT_STREAMS:** Opening more streams than the negotiated limit is a connection error, not something to queue
- **Server-generated resets:** Resets that the server sends because of a peer's malformed frames or flows count too. A peer that makes the server reset streams on its behalf can exceed the `MAX_CONCURRENT_STREAMS` bookkeeping and force per-stream work (MadeYouReset). A peer-chosen `SETTINGS_MAX_HEADER_LIST_SIZE` that makes the server fail every response can produce a similar signature
- **Peer-controlled encoder state:** Encoder state sized by a peer's setting, such as the HPACK dynamic table size, must be capped by local configuration

**QUIC-specific:**
- **Anti-amplification limit:** Before a client address is validated, the server must not send more than 3× the bytes received from that client (RFC 9000 §8.1); violating this turns the server into a DDoS amplifier against spoofed source addresses
- **Retry tokens:** Retry tokens must be cryptographically bound to the client address and must have a short expiry; a token that is neither bound nor time-limited allows off-path injection and replay. A default token handler must not accept arbitrary client-supplied tokens
- **Stateless reset tokens:** Stateless reset tokens must be unpredictable; a guessable token allows an on-path attacker to tear down arbitrary connections. Reset token material must also not be derivable from header-visible connection IDs
- **0-RTT replay:** Application data sent in 0-RTT can be replayed by a network attacker; flag any state-changing operations accepted in 0-RTT that are not idempotent
- **Connection ID unpredictability:** Connection IDs must be cryptographically random; predictable CIDs allow off-path packet injection

**HTTP/3-specific:**
- **Stream count enforcement:** A peer that opens more streams than `MAX_STREAMS` allows has committed a connection error; don't silently queue the excess
- **Extended CONNECT (WebSocket over HTTP/3):** Validate the `:protocol` pseudo-header; an absent or empty value when `CONNECT` is used is a stream error
- **Unknown and reserved frame types:** Discard or bound unknown and reserved frame types instead of buffering their payload without limit

### DNS

- **Compression pointer loops:** DNS name compression pointers (`0xC0 xx`) can form cycles; a parser that follows pointers without tracking visited offsets will loop forever. Limit total pointer hops and fail if exceeded.
- **Name and label length limits:** A fully-qualified DNS name is at most 255 bytes in wire format; each label is at most 63 bytes; validate during parsing and reject over-length names
- **Bailiwick / out-of-bailiwick records:** A DNS response may include records for names unrelated to the query, in the Additional section but also as `CNAME`, `NS` and answer records; reject out-of-bailiwick records of every type to prevent cache poisoning
- **Unpredictable query IDs and source ports:** Use a cryptographically secure source of randomness for transaction IDs and randomize the UDP source port; a predictable ID combined with a static source port enables Kaminsky-style cache poisoning
- **CNAME chain length:** Enforce a maximum CNAME chain depth to prevent unbounded recursive resolution

### MQTT

- **Wildcards in publish topic names:** `#` and `+` are subscription-only wildcards; a PUBLISH to a topic containing them must be rejected (`TOPIC_NAME_INVALID`)
- **Maximum packet size:** Enforce the configured or negotiated maximum packet size; close the connection if exceeded rather than accumulating unbounded data
- **UTF-8 validation:** MQTT strings must be valid UTF-8 and must not contain `U+0000`; null bytes in topic names can bypass topic-filter matching

### Redis (RESP)

- **Array header pre-allocation:** The `*<count>` prefix declares how many bulk strings follow; don't pre-allocate storage using the network-supplied count — a sender can declare millions of elements before sending any data
- **Nested arrays:** Bound the nesting depth and the total number of elements across all levels, don't allocate per nested header, and reset the aggregate state on every limit failure
- **Bulk string length:** The `$<len>` prefix must be capped at a configured maximum before allocating a buffer
- **Inline command injection:** If inline (non-RESP) command mode is supported, embedded CR or LF in an argument value injects additional commands; reject inline input containing `\r` or `\n`

### Memcache

- **Declared body length (binary protocol):** The binary protocol header contains a 32-bit total body length field; cap this before allocating a buffer
- **Signed vs. unsigned fields (binary protocol):** Treat the length fields consistently as unsigned; a signed/unsigned mismatch desynchronizes framing and enables response smuggling
- **Key validation (text protocol):** Memcache keys must not contain whitespace, CR, or LF; keys containing these bytes can inject additional commands in the text protocol

### SMTP

- **CRLF injection in envelope fields and headers:** Untrusted data in `MAIL FROM`, `RCPT TO`, or message headers must not contain `\r\n`; an injected CRLF followed by a new verb turns one SMTP transaction into multiple
- **SMTP smuggling:** Some SMTP servers accept bare CR or bare LF as line endings in the DATA phase; a sender that normalizes these differently than the receiving server enables DATA-phase injection. Validate that `\r\n` sequences in message data are canonical and that lone `\r` / `\n` are handled consistently.

### SOCKS

- **Declared length fields:** SOCKS5 username/password authentication includes 1-byte length fields; the decoder must read exactly the declared number of bytes and reject a declared length that contradicts what follows
- **Address type validation:** Unknown `ATYP` values must produce a protocol-level error response, not a parsing attempt that may read past the intended frame boundary

### STOMP

- **Content-Length vs. NUL-byte framing:** STOMP frames optionally include a `content-length` header; when present it must take precedence over NUL-byte body termination. A mismatch in how two parties in the same pipeline resolve this ambiguity is a frame-injection vector analogous to HTTP/1.1 request smuggling.
- **Declared content-length range:** A `content-length` outside the `int` range must be rejected, not truncated; truncation can cause an endless decode loop
- **Header injection:** STOMP header values use escape sequences (`\n`, `\r`, `\c`, `\\`); raw CR or LF bytes in a header value must be rejected; decoded escape sequences must not be re-serialized in a way that allows injecting additional header lines

### HAProxy PROXY Protocol

- **v1 bare-LF:** The v1 text format terminates the header with CRLF; a parser that also accepts bare LF creates inconsistencies with stricter consumers
- **v2 address-data length:** The v2 binary format includes a 2-byte length for the address block; cap this before allocating a buffer
- **v2 TLV nesting:** `PP2_TYPE_SSL` TLVs can contain further TLVs. Bound the nesting depth and length (unbounded recursion causes a `StackOverflowError`), and release retained slices on every error path
- **v1 address fields:** Validate address strings, including `AF_UNIX` addresses, for CR and LF

### XML

- **External entity (XXE) injection:** XML parsers must disable external entity resolution and DTD loading; an `ENTITY` declaration pointing to a local file or network resource enables reading arbitrary files or making SSRF requests. Explicitly set `XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES = false` and `XMLInputFactory.SUPPORT_DTD = false` on any `XMLInputFactory` or `DocumentBuilderFactory` used in codec-xml handlers.
- **Entity expansion (billion laughs):** Even without external entities, recursive entity definitions can expand to exponentially large strings; if DTD support must remain on, enforce a maximum entity-expansion depth and output size (see also: Attacker-Controlled Resource Consumption in `security-general.md`)
