/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.internal.tcnative.Buffer;
import io.netty.internal.tcnative.Library;
import io.netty.internal.tcnative.SSL;
import io.netty.internal.tcnative.SSLContext;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.ByteArrayInputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.netty.handler.ssl.SslUtils.*;

/**
 * Tells if <a href="http://netty.io/wiki/forked-tomcat-native.html">{@code netty-tcnative}</a> and its OpenSSL support
 * are available.
 */
public final class OpenSsl {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(OpenSsl.class);
    private static final Throwable UNAVAILABILITY_CAUSE;

    static final List<String> DEFAULT_CIPHERS;
    static final Set<String> AVAILABLE_CIPHER_SUITES;
    private static final Set<String> AVAILABLE_OPENSSL_CIPHER_SUITES;
    private static final Set<String> AVAILABLE_JAVA_CIPHER_SUITES;
    private static final boolean SUPPORTS_KEYMANAGER_FACTORY;
    private static final boolean USE_KEYMANAGER_FACTORY;
    private static final boolean SUPPORTS_OCSP;
    private static final boolean TLSV13_SUPPORTED;
    private static final boolean IS_BORINGSSL;
    static final Set<String> SUPPORTED_PROTOCOLS_SET;

    // Bytes of self-signed certificate for netty.io and the matching private-key
    private static byte[] CERT_BYTES = {
            48, -126, 1, -93, 48, -126, 1, 12, -96, 3, 2, 1, 2, 2, 8, 31, 127, -24, 79, 67,
            -72, -128, 124, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 11, 5, 0, 48,
            19, 49, 17, 48, 15, 6, 3, 85, 4, 3, 19, 8, 110, 101, 116, 116, 121, 46, 105,
            111, 48, 32, 23, 13, 49, 56, 48, 51, 50, 55, 49, 50, 52, 49, 50, 49, 90, 24,
            15, 57, 57, 57, 57, 49, 50, 51, 49, 50, 51, 53, 57, 53, 57, 90, 48, 19, 49, 17,
            48, 15, 6, 3, 85, 4, 3, 19, 8, 110, 101, 116, 116, 121, 46, 105, 111, 48, -127,
            -97, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 1, 5, 0, 3, -127, -115, 0,
            48, -127, -119, 2, -127, -127, 0, -105, 81, 76, -56, -118, -35, 54, -61, -39,
            69, 77, -56, 36, -126, 15, -35, -97, 126, -59, 2, -110, -39, -122, -116, -62,
            -83, -43, -102, 98, 46, -33, 6, 33, 74, -68, -121, -64, -9, -3, 45, 102, -121,
            50, -86, 93, 125, -82, -110, -2, -22, -114, 18, -93, 51, -86, 63, -63, 46, 96,
            -37, 16, 105, -11, 96, -97, -77, 98, -2, 117, -66, -118, 31, -62, -94, 109, -61,
            -82, 31, -103, 29, -53, -6, 47, 13, -78, -30, -128, 95, -76, 18, 5, -43, -80,
            51, 22, 39, 11, -93, 101, -66, -105, -68, -110, -80, 89, -105, -116, 10, -42,
            16, 51, 4, 113, -23, 69, -111, 85, -61, -59, -33, -83, 5, 114, -112, 34, 34,
            -107, 79, 2, 3, 1, 0, 1, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 11, 5,
            0, 3, -127, -127, 0, 8, -18, -42, -73, 54, 95, 39, -58, -98, 62, -26, 50, -3,
            71, -125, -128, -19, -87, -46, -85, 72, 17, 46, 75, -104, 125, -51, 27, 123,
            84, 34, 100, -112, 122, -28, 29, -33, 127, -20, -54, 30, -77, 109, -81, -3,
            -73, -113, 17, 28, 98, 127, 77, 53, -76, -49, -119, 98, 113, 71, -107, -33,
            -57, 37, -55, -60, 89, 65, 83, -96, -54, -22, 122, 10, -11, 11, -67, -58, -57,
            85, -119, 46, -26, -41, 15, -77, 19, 4, -32, -64, -12, 49, 104, -101, 42, 88,
            75, 27, 41, 122, 126, 70, -99, -91, -33, -36, -57, -63, -7, 94, -71, -15, -108,
            59, -32, 50, 47, -35, 71, 104, 47, 97, 43, 93, -128, -65, 11, 29, -88
    };
    private static byte[] KEY_BYTES = {
            48, -126, 2, 120, 2, 1, 0, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 1, 5,
            0, 4, -126, 2, 98, 48, -126, 2, 94, 2, 1, 0, 2, -127, -127, 0, -105, 81, 76, -56,
            -118, -35, 54, -61, -39, 69, 77, -56, 36, -126, 15, -35, -97, 126, -59, 2, -110,
            -39, -122, -116, -62, -83, -43, -102, 98, 46, -33, 6, 33, 74, -68, -121, -64, -9,
            -3, 45, 102, -121, 50, -86, 93, 125, -82, -110, -2, -22, -114, 18, -93, 51, -86,
            63, -63, 46, 96, -37, 16, 105, -11, 96, -97, -77, 98, -2, 117, -66, -118, 31, -62,
            -94, 109, -61, -82, 31, -103, 29, -53, -6, 47, 13, -78, -30, -128, 95, -76, 18, 5,
            -43, -80, 51, 22, 39, 11, -93, 101, -66, -105, -68, -110, -80, 89, -105, -116, 10,
            -42, 16, 51, 4, 113, -23, 69, -111, 85, -61, -59, -33, -83, 5, 114, -112, 34, 34,
            -107, 79, 2, 3, 1, 0, 1, 2, -127, -128, 68, 52, 93, 11, -73, -85, -26, 87, 120, -61,
            -120, 63, -62, 84, -19, -103, -45, -98, 108, 102, -80, -110, 99, -41, 102, -104,
            -68, 67, 14, 38, 90, 88, -123, 1, 14, -31, -111, -43, 53, -59, 21, 5, -77, -116,
            -98, -1, 91, -124, -34, 106, 19, 7, -53, -112, 42, 24, -6, -106, 81, 9, -20, -24,
            21, -75, 119, -49, 70, 70, -106, -6, -56, -6, 28, 104, 33, -104, 27, 65, -75, -12,
            -93, 75, 87, 82, -64, -70, -127, 60, 91, -60, -76, 13, -115, 19, -77, -16, -3, 119,
            -88, 111, 96, 78, -103, -30, -87, -118, 106, -7, 97, -21, 20, -31, -43, 28, -18,
            -2, 117, 63, 111, -71, 84, -77, -42, 78, 20, -28, -54, -63, 2, 65, 0, -23, 7, -72,
            -18, -122, 34, 90, 107, -103, 119, 105, 46, -10, -109, -7, 3, 21, 16, 91, 110, -13,
            120, 95, 122, -77, -60, 18, -52, 103, -1, -90, 39, -3, 99, -10, 18, -14, 47, -104,
            -87, -110, 7, -48, -23, -37, 104, -125, 97, 88, -1, -86, -90, -11, -79, -20, 41,
            -128, 15, -35, -104, 60, 25, 121, -41, 2, 65, 0, -90, 59, -92, -31, -117, 35, -79,
            16, -76, 57, 90, 15, -6, 84, 47, -113, -42, 19, -56, 121, 123, -121, -91, 91, -37,
            -71, 78, -40, 12, 82, -25, -125, -58, 115, -123, 97, 10, -99, -59, 38, -48, -103,
            -128, -125, 36, 108, 18, -86, -85, -17, -40, 8, -14, -108, -24, -20, 63, -59, -81,
            5, 11, 35, 1, 73, 2, 65, 0, -30, 11, -8, -85, -128, 120, 80, -121, -15, -35, -80,
            -83, -70, -55, 125, -109, 44, -38, -86, 39, 45, -116, 69, -22, 75, -7, 86, 86, -20,
            71, 68, -111, -92, 46, 84, 100, -70, -125, -53, 46, 42, -106, -28, 100, 5, -49, 19,
            42, -38, 95, 95, -42, 7, -99, -23, 61, -76, -103, 47, 86, -34, 109, -60, 15, 2, 65,
            0, -126, -72, -22, -101, 87, 0, -75, 80, 110, 121, -97, 98, 107, 55, -30, -61, 24,
            -43, 43, -44, -92, -104, -14, 39, 127, 109, -123, 28, 14, -20, -17, 20, -56, 109,
            -75, -40, -81, 49, -116, -123, 78, -117, 55, -19, 105, 41, -9, -81, -15, 79, -58,
            50, -101, 25, 16, -26, 31, -20, 68, 11, 18, 75, -17, -55, 2, 65, 0, -126, -11, 56,
            -83, -60, 1, -125, 109, 74, 74, -1, -17, 54, 111, -111, 100, 125, 21, 77, 34, 119,
            -33, 23, -13, 66, 74, -78, 80, -67, 57, -42, 65, 65, 58, 96, 0, 72, -122, 3, -78,
            119, 68, -76, 5, 50, 37, 51, 10, -54, 54, -102, 90, -6, 127, -93, 97, 53, 24, 57,
            77, 81, 53, -13, -127
    };

    static {
        Throwable cause = null;

        if (SystemPropertyUtil.getBoolean("io.netty.handler.ssl.noOpenSsl", false)) {
            cause = new UnsupportedOperationException(
                    "OpenSSL was explicit disabled with -Dio.netty.handler.ssl.noOpenSsl=true");

            logger.debug(
                    "netty-tcnative explicit disabled; " +
                            OpenSslEngine.class.getSimpleName() + " will be unavailable.", cause);
        } else {
            // Test if netty-tcnative is in the classpath first.
            try {
                Class.forName("io.netty.internal.tcnative.SSL", false, OpenSsl.class.getClassLoader());
            } catch (ClassNotFoundException t) {
                cause = t;
                logger.debug(
                        "netty-tcnative not in the classpath; " +
                                OpenSslEngine.class.getSimpleName() + " will be unavailable.");
            }

            // If in the classpath, try to load the native library and initialize netty-tcnative.
            if (cause == null) {
                try {
                    // The JNI library was not already loaded. Load it now.
                    loadTcNative();
                } catch (Throwable t) {
                    cause = t;
                    logger.debug(
                            "Failed to load netty-tcnative; " +
                                    OpenSslEngine.class.getSimpleName() + " will be unavailable, unless the " +
                                    "application has already loaded the symbols by some other means. " +
                                    "See http://netty.io/wiki/forked-tomcat-native.html for more information.", t);
                }

                try {
                    String engine = SystemPropertyUtil.get("io.netty.handler.ssl.openssl.engine", null);
                    if (engine == null) {
                        logger.debug("Initialize netty-tcnative using engine: 'default'");
                    } else {
                        logger.debug("Initialize netty-tcnative using engine: '{}'", engine);
                    }
                    initializeTcNative(engine);

                    // The library was initialized successfully. If loading the library failed above,
                    // reset the cause now since it appears that the library was loaded by some other
                    // means.
                    cause = null;
                } catch (Throwable t) {
                    if (cause == null) {
                        cause = t;
                    }
                    logger.debug(
                            "Failed to initialize netty-tcnative; " +
                                    OpenSslEngine.class.getSimpleName() + " will be unavailable. " +
                                    "See http://netty.io/wiki/forked-tomcat-native.html for more information.", t);
                }
            }
        }

        UNAVAILABILITY_CAUSE = cause;

        if (cause == null) {
            logger.debug("netty-tcnative using native library: {}", SSL.versionString());

            final List<String> defaultCiphers = new ArrayList<String>();
            final Set<String> availableOpenSslCipherSuites = new LinkedHashSet<String>(128);
            boolean supportsKeyManagerFactory = false;
            boolean useKeyManagerFactory = false;
            boolean tlsv13Supported = false;

            IS_BORINGSSL = "BoringSSL".equals(versionString());

            try {
                final long sslCtx = SSLContext.make(SSL.SSL_PROTOCOL_ALL, SSL.SSL_MODE_SERVER);
                long certBio = 0;
                long keyBio = 0;
                long cert = 0;
                long key = 0;
                try {
                    try {
                        StringBuilder tlsv13Ciphers = new StringBuilder();

                        for (String cipher: TLSV13_CIPHERS) {
                            String converted = CipherSuiteConverter.toOpenSsl(cipher, IS_BORINGSSL);
                            if (converted != null) {
                                tlsv13Ciphers.append(converted).append(':');
                            }
                        }
                        if (tlsv13Ciphers.length() == 0) {
                            tlsv13Supported = false;
                        } else {
                            tlsv13Ciphers.setLength(tlsv13Ciphers.length() - 1);
                            SSLContext.setCipherSuite(sslCtx, tlsv13Ciphers.toString() , true);
                            tlsv13Supported = true;
                        }

                    } catch (Exception ignore) {
                        tlsv13Supported = false;
                    }

                    SSLContext.setCipherSuite(sslCtx, "ALL", false);

                    final long ssl = SSL.newSSL(sslCtx, true);
                    try {
                        for (String c: SSL.getCiphers(ssl)) {
                            // Filter out bad input.
                            if (c == null || c.isEmpty() || availableOpenSslCipherSuites.contains(c) ||
                                // Filter out TLSv1.3 ciphers if not supported.
                                !tlsv13Supported && isTLSv13Cipher(c)) {
                                continue;
                            }
                            availableOpenSslCipherSuites.add(c);
                        }
                        if (IS_BORINGSSL) {
                            // Currently BoringSSL does not include these when calling SSL.getCiphers() even when these
                            // are supported.
                            Collections.addAll(availableOpenSslCipherSuites,
                                               "TLS_AES_128_GCM_SHA256",
                                               "TLS_AES_256_GCM_SHA384" ,
                                               "TLS_CHACHA20_POLY1305_SHA256",
                                               "AEAD-AES128-GCM-SHA256",
                                               "AEAD-AES256-GCM-SHA384",
                                               "AEAD-CHACHA20-POLY1305-SHA256");
                        }

                        PemEncoded privateKey = PemPrivateKey.toPEM(UnpooledByteBufAllocator.DEFAULT, true, KEY_BYTES);
                        try {
                             X509Certificate certificate = selfSignedCertificate();
                            certBio = ReferenceCountedOpenSslContext.toBIO(ByteBufAllocator.DEFAULT, certificate);
                            cert = SSL.parseX509Chain(certBio);

                            keyBio = ReferenceCountedOpenSslContext.toBIO(
                                    UnpooledByteBufAllocator.DEFAULT, privateKey.retain());
                            key = SSL.parsePrivateKey(keyBio, null);

                            SSL.setKeyMaterial(ssl, cert, key);
                            supportsKeyManagerFactory = true;
                            try {
                                useKeyManagerFactory = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                                    @Override
                                    public Boolean run() {
                                        return SystemPropertyUtil.getBoolean(
                                                "io.netty.handler.ssl.openssl.useKeyManagerFactory", true);
                                    }
                                });
                            } catch (Throwable ignore) {
                                logger.debug("Failed to get useKeyManagerFactory system property.");
                            }
                        } catch (Throwable ignore) {
                            logger.debug("KeyManagerFactory not supported.");
                        } finally {
                            privateKey.release();
                        }
                    } finally {
                        SSL.freeSSL(ssl);
                        if (certBio != 0) {
                            SSL.freeBIO(certBio);
                        }
                        if (keyBio != 0) {
                            SSL.freeBIO(keyBio);
                        }
                        if (cert != 0) {
                            SSL.freeX509Chain(cert);
                        }
                        if (key != 0) {
                            SSL.freePrivateKey(key);
                        }
                    }
                } finally {
                    SSLContext.free(sslCtx);
                }
            } catch (Exception e) {
                logger.warn("Failed to get the list of available OpenSSL cipher suites.", e);
            }
            AVAILABLE_OPENSSL_CIPHER_SUITES = Collections.unmodifiableSet(availableOpenSslCipherSuites);
            final Set<String> availableJavaCipherSuites = new LinkedHashSet<String>(
                    AVAILABLE_OPENSSL_CIPHER_SUITES.size() * 2);
            for (String cipher: AVAILABLE_OPENSSL_CIPHER_SUITES) {
                // Included converted but also openssl cipher name
                if (!isTLSv13Cipher(cipher)) {
                    availableJavaCipherSuites.add(CipherSuiteConverter.toJava(cipher, "TLS"));
                    availableJavaCipherSuites.add(CipherSuiteConverter.toJava(cipher, "SSL"));
                } else {
                    // TLSv1.3 ciphers have the correct format.
                    availableJavaCipherSuites.add(cipher);
                }
            }

            addIfSupported(availableJavaCipherSuites, defaultCiphers, DEFAULT_CIPHER_SUITES);
            addIfSupported(availableJavaCipherSuites, defaultCiphers, TLSV13_CIPHER_SUITES);

            useFallbackCiphersIfDefaultIsEmpty(defaultCiphers, availableJavaCipherSuites);
            DEFAULT_CIPHERS = Collections.unmodifiableList(defaultCiphers);

            AVAILABLE_JAVA_CIPHER_SUITES = Collections.unmodifiableSet(availableJavaCipherSuites);

            final Set<String> availableCipherSuites = new LinkedHashSet<String>(
                    AVAILABLE_OPENSSL_CIPHER_SUITES.size() + AVAILABLE_JAVA_CIPHER_SUITES.size());
            availableCipherSuites.addAll(AVAILABLE_OPENSSL_CIPHER_SUITES);
            availableCipherSuites.addAll(AVAILABLE_JAVA_CIPHER_SUITES);

            AVAILABLE_CIPHER_SUITES = availableCipherSuites;
            SUPPORTS_KEYMANAGER_FACTORY = supportsKeyManagerFactory;
            USE_KEYMANAGER_FACTORY = useKeyManagerFactory;

            Set<String> protocols = new LinkedHashSet<String>(6);
            // Seems like there is no way to explicitly disable SSLv2Hello in openssl so it is always enabled
            protocols.add(PROTOCOL_SSL_V2_HELLO);
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_SSLV2, SSL.SSL_OP_NO_SSLv2)) {
                protocols.add(PROTOCOL_SSL_V2);
            }
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_SSLV3, SSL.SSL_OP_NO_SSLv3)) {
                protocols.add(PROTOCOL_SSL_V3);
            }
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_TLSV1, SSL.SSL_OP_NO_TLSv1)) {
                protocols.add(PROTOCOL_TLS_V1);
            }
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_TLSV1_1, SSL.SSL_OP_NO_TLSv1_1)) {
                protocols.add(PROTOCOL_TLS_V1_1);
            }
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_TLSV1_2, SSL.SSL_OP_NO_TLSv1_2)) {
                protocols.add(PROTOCOL_TLS_V1_2);
            }

            // This is only supported by java11 and later.
            if (tlsv13Supported && doesSupportProtocol(SSL.SSL_PROTOCOL_TLSV1_3, SSL.SSL_OP_NO_TLSv1_3)) {
                protocols.add(PROTOCOL_TLS_V1_3);
                TLSV13_SUPPORTED = true;
            } else {
                TLSV13_SUPPORTED = false;
            }

            SUPPORTED_PROTOCOLS_SET = Collections.unmodifiableSet(protocols);
            SUPPORTS_OCSP = doesSupportOcsp();

            if (logger.isDebugEnabled()) {
                logger.debug("Supported protocols (OpenSSL): {} ", SUPPORTED_PROTOCOLS_SET);
                logger.debug("Default cipher suites (OpenSSL): {}", DEFAULT_CIPHERS);
            }
        } else {
            DEFAULT_CIPHERS = Collections.emptyList();
            AVAILABLE_OPENSSL_CIPHER_SUITES = Collections.emptySet();
            AVAILABLE_JAVA_CIPHER_SUITES = Collections.emptySet();
            AVAILABLE_CIPHER_SUITES = Collections.emptySet();
            SUPPORTS_KEYMANAGER_FACTORY = false;
            USE_KEYMANAGER_FACTORY = false;
            SUPPORTED_PROTOCOLS_SET = Collections.emptySet();
            SUPPORTS_OCSP = false;
            TLSV13_SUPPORTED = false;
            IS_BORINGSSL = false;
        }
    }

    /**
     * Returns a self-signed {@link X509Certificate} for {@code netty.io}.
     */
    static X509Certificate selfSignedCertificate() throws CertificateException {
        return (X509Certificate) SslContext.X509_CERT_FACTORY.generateCertificate(new ByteArrayInputStream(CERT_BYTES));
    }

    private static boolean doesSupportOcsp() {
        boolean supportsOcsp = false;
        if (version() >= 0x10002000L) {
            long sslCtx = -1;
            try {
                sslCtx = SSLContext.make(SSL.SSL_PROTOCOL_TLSV1_2, SSL.SSL_MODE_SERVER);
                SSLContext.enableOcsp(sslCtx, false);
                supportsOcsp = true;
            } catch (Exception ignore) {
                // ignore
            } finally {
                if (sslCtx != -1) {
                    SSLContext.free(sslCtx);
                }
            }
        }
        return supportsOcsp;
    }
    private static boolean doesSupportProtocol(int protocol, int opt) {
        if (opt == 0) {
            // If the opt is 0 the protocol is not supported. This is for example the case with BoringSSL and SSLv2.
            return false;
        }
        long sslCtx = -1;
        try {
            sslCtx = SSLContext.make(protocol, SSL.SSL_MODE_COMBINED);
            return true;
        } catch (Exception ignore) {
            return false;
        } finally {
            if (sslCtx != -1) {
                SSLContext.free(sslCtx);
            }
        }
    }

    /**
     * Returns {@code true} if and only if
     * <a href="http://netty.io/wiki/forked-tomcat-native.html">{@code netty-tcnative}</a> and its OpenSSL support
     * are available.
     */
    public static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    /**
     * Returns {@code true} if the used version of openssl supports
     * <a href="https://tools.ietf.org/html/rfc7301">ALPN</a>.
     */
    public static boolean isAlpnSupported() {
        return version() >= 0x10002000L;
    }

    /**
     * Returns {@code true} if the used version of OpenSSL supports OCSP stapling.
     */
    public static boolean isOcspSupported() {
      return SUPPORTS_OCSP;
    }

    /**
     * Returns the version of the used available OpenSSL library or {@code -1} if {@link #isAvailable()}
     * returns {@code false}.
     */
    public static int version() {
        return isAvailable() ? SSL.version() : -1;
    }

    /**
     * Returns the version string of the used available OpenSSL library or {@code null} if {@link #isAvailable()}
     * returns {@code false}.
     */
    public static String versionString() {
        return isAvailable() ? SSL.versionString() : null;
    }

    /**
     * Ensure that <a href="http://netty.io/wiki/forked-tomcat-native.html">{@code netty-tcnative}</a> and
     * its OpenSSL support are available.
     *
     * @throws UnsatisfiedLinkError if unavailable
     */
    public static void ensureAvailability() {
        if (UNAVAILABILITY_CAUSE != null) {
            throw (Error) new UnsatisfiedLinkError(
                    "failed to load the required native library").initCause(UNAVAILABILITY_CAUSE);
        }
    }

    /**
     * Returns the cause of unavailability of
     * <a href="http://netty.io/wiki/forked-tomcat-native.html">{@code netty-tcnative}</a> and its OpenSSL support.
     *
     * @return the cause if unavailable. {@code null} if available.
     */
    public static Throwable unavailabilityCause() {
        return UNAVAILABILITY_CAUSE;
    }

    /**
     * @deprecated use {@link #availableOpenSslCipherSuites()}
     */
    @Deprecated
    public static Set<String> availableCipherSuites() {
        return availableOpenSslCipherSuites();
    }

    /**
     * Returns all the available OpenSSL cipher suites.
     * Please note that the returned array may include the cipher suites that are insecure or non-functional.
     */
    public static Set<String> availableOpenSslCipherSuites() {
        return AVAILABLE_OPENSSL_CIPHER_SUITES;
    }

    /**
     * Returns all the available cipher suites (Java-style).
     * Please note that the returned array may include the cipher suites that are insecure or non-functional.
     */
    public static Set<String> availableJavaCipherSuites() {
        return AVAILABLE_JAVA_CIPHER_SUITES;
    }

    /**
     * Returns {@code true} if and only if the specified cipher suite is available in OpenSSL.
     * Both Java-style cipher suite and OpenSSL-style cipher suite are accepted.
     */
    public static boolean isCipherSuiteAvailable(String cipherSuite) {
        String converted = CipherSuiteConverter.toOpenSsl(cipherSuite, IS_BORINGSSL);
        if (converted != null) {
            cipherSuite = converted;
        }
        return AVAILABLE_OPENSSL_CIPHER_SUITES.contains(cipherSuite);
    }

    /**
     * Returns {@code true} if {@link javax.net.ssl.KeyManagerFactory} is supported when using OpenSSL.
     */
    public static boolean supportsKeyManagerFactory() {
        return SUPPORTS_KEYMANAGER_FACTORY;
    }

    /**
     * Always returns {@code true} if {@link #isAvailable()} returns {@code true}.
     *
     * @deprecated Will be removed because hostname validation is always done by a
     * {@link javax.net.ssl.TrustManager} implementation.
     */
    @Deprecated
    public static boolean supportsHostnameValidation() {
        return isAvailable();
    }

    static boolean useKeyManagerFactory() {
        return USE_KEYMANAGER_FACTORY;
    }

    static long memoryAddress(ByteBuf buf) {
        assert buf.isDirect();
        return buf.hasMemoryAddress() ? buf.memoryAddress() : Buffer.address(buf.nioBuffer());
    }

    private OpenSsl() { }

    private static void loadTcNative() throws Exception {
        String os = PlatformDependent.normalizedOs();
        String arch = PlatformDependent.normalizedArch();

        Set<String> libNames = new LinkedHashSet<String>(4);
        String staticLibName = "netty_tcnative";

        // First, try loading the platform-specific library. Platform-specific
        // libraries will be available if using a tcnative uber jar.
        libNames.add(staticLibName + "_" + os + '_' + arch);
        if ("linux".equalsIgnoreCase(os)) {
            // Fedora SSL lib so naming (libssl.so.10 vs libssl.so.1.0.0)..
            libNames.add(staticLibName + "_" + os + '_' + arch + "_fedora");
        }
        libNames.add(staticLibName + "_" + arch);
        libNames.add(staticLibName);

        NativeLibraryLoader.loadFirstAvailable(SSL.class.getClassLoader(),
            libNames.toArray(new String[0]));
    }

    private static boolean initializeTcNative(String engine) throws Exception {
        return Library.initialize("provided", engine);
    }

    static void releaseIfNeeded(ReferenceCounted counted) {
        if (counted.refCnt() > 0) {
            ReferenceCountUtil.safeRelease(counted);
        }
    }

    static boolean isTlsv13Supported() {
        return TLSV13_SUPPORTED;
    }

    static boolean isBoringSSL() {
        return IS_BORINGSSL;
    }
}
