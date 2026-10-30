# Security: TLS / PKI

**TrustManager implementation and wrapping:**
- Always extend `X509ExtendedTrustManager` rather than `X509TrustManager` when implementing or wrapping a trust manager. The extended form adds `checkClientTrusted` and `checkServerTrusted` overloads that receive the `SSLEngine` or `Socket` — the JDK and Netty's OpenSSL engine use these overloads to pass the endpoint identification algorithm context. A wrapper that only delegates to the 2-argument form silently drops that context; the delegate cannot perform hostname verification because it has no access to the configured algorithm.
- When an `X509ExtendedTrustManager` wraps another `X509ExtendedTrustManager`, the `(chain, authType, SSLEngine)` and `(chain, authType, Socket)` overloads must delegate to the corresponding extended methods on the delegate, not fall back to the basic 2-argument form. Falling back to the 2-arg form drops the engine/socket context and disables hostname verification on the delegate.
- Netty's `X509TrustManagerWrapper` intentionally wraps a plain `X509TrustManager` as an `X509ExtendedTrustManager` by delegating all extended calls to the 2-arg form — that is the best possible when the delegate has no engine context. New wrappers whose delegate is already an `X509ExtendedTrustManager` must not apply the same pattern; they have access to the extended methods and must use them.

**Disabling or weakening validation:**
- Never use `InsecureTrustManagerFactory.INSTANCE` or any `TrustManager` whose `checkServerTrusted` / `checkClientTrusted` never throws in non-test code; these disable all certificate validation.
- Never set `SSL_VERIFY_NONE` (BoringSSL verify mode) in any production code path.
- Hostname verification is controlled by `endpointIdentificationAlgorithm` (e.g., `"HTTPS"`) on the `SslContext` / `SSLParameters`. Clearing this field, setting it to an empty string, or failing to propagate it through a new code path silently disables hostname verification.
- `wantClientAuth` and `needClientAuth` have different semantics: `want` accepts a connection even when no client certificate is presented; `need` rejects it. Downgrading from `need` to `want` silently disables mutual TLS enforcement.
- A `checkServerTrusted` / `checkClientTrusted` implementation that catches `CertificateException` and does not rethrow it silently accepts invalid certificates; treat any such swallowing as a CRITICAL finding.

**KeyManager implementation and wrapping:**
- Extend `X509ExtendedKeyManager` rather than `X509KeyManager`. The extended form provides `chooseEngineClientAlias` / `chooseEngineServerAlias` that receive the `SSLEngine`, enabling SNI-based certificate selection. A wrapper that routes engine-alias calls to the non-engine versions (passing `null` for the socket) loses SNI-based selection, which can cause the wrong certificate to be presented.

**Protocol versions and cipher suites:**
- Don't enable SSL 3.0, TLS 1.0, or TLS 1.1; these are deprecated and have known attacks. Changes that widen the set of enabled protocol versions require justification.
- Don't add cipher suites containing NULL, EXPORT, RC4, DES, 3DES, anonymous (aNULL / eNULL), or MD5-HMAC. Don't remove existing cipher suite restrictions without understanding the security impact.

**Factory delegation:**
- When a `TrustManagerFactory` or `KeyManagerFactory` wrapper delegates to an inner factory, ensure all `engineGetTrustManagers()` / `engineGetKeyManagers()` code paths delegate correctly; a missing branch can silently return an empty or default manager set.

**Hostname verification on every engine path:**
- Hostname verification must hold on every path that validates a certificate: the JDK `SSLEngine` path, the OpenSSL/BoringSSL callbacks, and QUIC certificate verification. Check that each path calls the engine-aware trust manager overload and that endpoint identification is configured.
- When trust-manager wrapping is unavailable (for example on newer JDKs), verify the hostname explicitly instead of silently skipping it.

**OCSP validation:**
- The response must be tied to the certificate being checked: verify that the `CertificateID` in the response matches the request.
- Check freshness using `thisUpdate` and `nextUpdate`. An absent `nextUpdate` must not skip validation. When a check fails, make sure the control flow actually stops: a missing `return` after flagging an out-of-date response would still report it as valid.
- Check the responder's Extended Key Usage.
- Don't release the handshake-complete event, or let application data flow, before the asynchronous revocation check has finished.

**SNI / ClientHello parsing:**
- A ClientHello can be split across multiple TLS records at any offset; parsing must handle every split. A parse failure must never silently fall back to the default `SslContext`.
- Bound reassembly in both bytes and CPU, and don't preallocate based on an attacker-declared length.
