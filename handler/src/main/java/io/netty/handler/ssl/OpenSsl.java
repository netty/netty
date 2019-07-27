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
import java.security.cert.CertificateFactory;
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
    private static final boolean SUPPORTS_HOSTNAME_VALIDATION;
    private static final boolean USE_KEYMANAGER_FACTORY;
    private static final boolean SUPPORTS_OCSP;
    private static final boolean TLSV13_SUPPORTED;
    private static final boolean IS_BORINGSSL;
    static final Set<String> SUPPORTED_PROTOCOLS_SET;

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
            boolean supportsHostNameValidation = false;
            boolean tlsv13Supported = false;

            IS_BORINGSSL = "BoringSSL".equals(versionString());

            try {
                final long sslCtx = SSLContext.make(SSL.SSL_PROTOCOL_ALL, SSL.SSL_MODE_SERVER);
                long certBio = 0;
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
                        try {
                            SSL.setHostNameValidation(ssl, 0, "netty.io");
                            supportsHostNameValidation = true;
                        } catch (Throwable ignore) {
                            logger.debug("Hostname Verification not supported.");
                        }
                        try {
                            X509Certificate certificate = selfSignedCertificate();
                            certBio = ReferenceCountedOpenSslContext.toBIO(ByteBufAllocator.DEFAULT, certificate);
                            SSL.setCertificateChainBio(ssl, certBio, false);
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
                        }
                    } finally {
                        SSL.freeSSL(ssl);
                        if (certBio != 0) {
                            SSL.freeBIO(certBio);
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
            SUPPORTS_HOSTNAME_VALIDATION = supportsHostNameValidation;
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
            SUPPORTS_HOSTNAME_VALIDATION = false;
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
        // Bytes of self-signed certificate for netty.io
        byte[] certBytes = {
                48, -126, 1, -92, 48, -126, 1, 13, -96, 3, 2, 1, 2, 2, 9, 0, -9, 61,
                44, 121, -118, -4, -45, -120, 48, 13, 6, 9, 42, -122, 72, -122,
                -9, 13, 1, 1, 5, 5, 0, 48, 19, 49, 17, 48, 15, 6, 3, 85, 4, 3, 19,
                8, 110, 101, 116, 116, 121, 46, 105, 111, 48, 32, 23, 13, 49, 55,
                49, 48, 50, 48, 49, 56, 49, 54, 51, 54, 90, 24, 15, 57, 57, 57, 57,
                49, 50, 51, 49, 50, 51, 53, 57, 53, 57, 90, 48, 19, 49, 17, 48, 15,
                6, 3, 85, 4, 3, 19, 8, 110, 101, 116, 116, 121, 46, 105, 111, 48, -127,
                -97, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 1, 5, 0, 3, -127,
                -115, 0, 48, -127, -119, 2, -127, -127, 0, -116, 37, 122, -53, 28, 46,
                13, -90, -14, -33, 111, -108, -41, 59, 90, 124, 113, -112, -66, -17,
                -102, 44, 13, 7, -33, -28, 24, -79, -126, -76, 40, 111, -126, -103,
                -102, 34, 11, 45, 16, -38, 63, 24, 80, 24, 76, 88, -93, 96, 11, 38,
                -19, -64, -11, 87, -49, -52, -65, 24, 36, -22, 53, 8, -42, 14, -121,
                114, 6, 17, -82, 10, 92, -91, -127, 81, -12, -75, 105, -10, -106, 91,
                -38, 111, 50, 57, -97, -125, 109, 42, -87, -1, -19, 80, 78, 49, -97, -4,
                23, -2, -103, 122, -107, -43, 4, -31, -21, 90, 39, -9, -106, 34, -101,
                -116, 31, -94, -84, 80, -6, -78, -33, 87, -90, 31, 103, 100, 56, -103,
                -5, 11, 2, 3, 1, 0, 1, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1,
                5, 5, 0, 3, -127, -127, 0, 112, 45, -73, 5, 64, 49, 59, 101, 51, 73,
                -96, 62, 23, -84, 90, -41, -58, 83, -20, -72, 38, 123, -108, -45, 28,
                96, -122, -18, 30, 42, 86, 87, -87, -28, 107, 110, 11, -59, 91, 100,
                101, -18, 26, -103, -78, -80, -3, 38, 113, 83, -48, -108, 109, 41, -15,
                6, 112, 105, 7, -46, -11, -3, -51, 40, -66, -73, -83, -46, -94, -121,
                -88, 51, -106, -77, 109, 53, -7, 123, 91, 75, -105, -22, 64, 121, -72,
                -59, -21, -44, 84, 12, 9, 120, 21, -26, 13, 49, -81, -58, -47, 117,
                -44, -18, -17, 124, 49, -48, 19, 16, -41, 71, -52, -107, 99, -19, -29,
                105, -93, -71, -38, -97, -128, -2, 118, 119, 49, -126, 109, 119 };

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(certBytes));
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
     * Returns {@code true} if <a href="https://wiki.openssl.org/index.php/Hostname_validation">Hostname Validation</a>
     * is supported when using OpenSSL.
     */
    public static boolean supportsHostnameValidation() {
        return SUPPORTS_HOSTNAME_VALIDATION;
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
