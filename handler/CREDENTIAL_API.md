# OpenSSL Credential API

## Overview

The OpenSSL Credential API provides advanced SSL/TLS credential management for BoringSSL-based contexts. This API enables features like multiple certificates per context (e.g., RSA + ECDSA), OCSP stapling per credential, delegated credentials, and more.

## Availability

**BoringSSL Only**: This feature is only available when using BoringSSL as the SSL provider. Check availability:

```java
if (OpenSsl.isBoringSSL()) {
    // Credential API is available
}
```

## Core Classes

### OpenSslCredential

Reference-counted credential object representing an SSL_CREDENTIAL.

```java
public interface OpenSslCredential extends ReferenceCounted {
    long credentialAddress();
    CredentialType type();

    enum CredentialType {
        X509,      // Standard X.509 certificate
        DELEGATED  // Delegated credential (RFC 9345)
    }
}
```

### OpenSslCredentialBuilder

Fluent API for building credentials.

#### X.509 Credentials

```java
OpenSslCredential credential = OpenSslCredentialBuilder.newX509()
    .privateKey(privateKey)
    .certificateChain(cert1, cert2, cert3)
    .ocspResponse(ocspBytes)
    .signedCertificateTimestamps(sctBytes)
    .signingAlgorithmPreferences(0x0804, 0x0403) // TLS 1.3 signature schemes
    .certificateProperties(propsBytes)
    .trustAnchorId(anchorId)
    .mustMatchIssuer(true)
    .build();
```

### Engine-Level Credentials

Add credentials dynamically at the engine level:

```java
ReferenceCountedOpenSslEngine engine =
    (ReferenceCountedOpenSslEngine) sslContext.newEngine(allocator);

// Must be called before handshake starts
engine.addCredential(credential);
```

Query selected credential after handshake:

```java
OpenSslCredential selected = engine.getSelectedCredential();
// Note: Currently returns null (implementation limitation)
```

## API Reference

### SslContextBuilder Methods

```java
// Add single credential
SslContextBuilder credential(OpenSslCredential credential)

// Add multiple credentials
SslContextBuilder credentials(OpenSslCredential... credentials)
SslContextBuilder credentials(Iterable<? extends OpenSslCredential> credentials)
```

## Limitations

1. **BoringSSL Only**: Not available with OpenSSL or LibreSSL
2. **getSelectedCredential Returns Null**: Wrapper implementation pending (raw pointer available)
3. **Engine Credentials Before Handshake**: `addCredential()` must be called before handshake starts

## Testing

Comprehensive test suite included:

- **OpenSslCredentialBuilderTest**: Unit tests for builder API
- **OpenSslCredentialIntegrationTest**: End-to-end TLS handshake tests
- **OpenSslCredentialMultiCertTest**: Multi-certificate scenarios

### Multi-Certificate (New Capability)

```java
// Not possible with traditional API!

OpenSslCredential rsaCred = buildRsaCredential();
OpenSslCredential ecdsaCred = buildEcdsaCredential();

SslContext ctx = SslContextBuilder.forServer(rsaKey, rsaCert)
    .sslProvider(SslProvider.OPENSSL_REFCNT)
    .credential(ecdsaCred)
    .build();
```

## Further Reading

- [BoringSSL SSL_CREDENTIAL Documentation](https://commondatastorage.googleapis.com/chromium-boringssl-docs/ssl.h.html#SSL_CREDENTIAL_free)
- [RFC 9345 - Delegated Credentials for TLS](https://datatracker.ietf.org/doc/html/rfc9345)
- [Netty SSL Handler Documentation](https://netty.io/wiki/requirements-for-4.x.html#wiki-h3-9)

## Version

- **Introduced**: Netty 5.x (pending)
- **netty-tcnative Requirement**: Version with SSL_CREDENTIAL JNI bindings
