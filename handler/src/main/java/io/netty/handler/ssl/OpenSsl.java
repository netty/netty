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
import io.netty.handler.ssl.util.SelfSignedCertificate;
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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.netty.handler.ssl.SslUtils.DEFAULT_CIPHER_SUITES;
import static io.netty.handler.ssl.SslUtils.addIfSupported;
import static io.netty.handler.ssl.SslUtils.useFallbackCiphersIfDefaultIsEmpty;
import static io.netty.handler.ssl.SslUtils.PROTOCOL_SSL_V2;
import static io.netty.handler.ssl.SslUtils.PROTOCOL_SSL_V2_HELLO;
import static io.netty.handler.ssl.SslUtils.PROTOCOL_SSL_V3;
import static io.netty.handler.ssl.SslUtils.PROTOCOL_TLS_V1;
import static io.netty.handler.ssl.SslUtils.PROTOCOL_TLS_V1_1;
import static io.netty.handler.ssl.SslUtils.PROTOCOL_TLS_V1_2;

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

    static final Set<String> SUPPORTED_PROTOCOLS_SET;

    static {
        Throwable cause = null;

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
                initializeTcNative();

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

        UNAVAILABILITY_CAUSE = cause;

        if (cause == null) {
            logger.debug("netty-tcnative using native library: {}", SSL.versionString());

            final List<String> defaultCiphers = new ArrayList<String>();
            final Set<String> availableOpenSslCipherSuites = new LinkedHashSet<String>(128);
            boolean supportsKeyManagerFactory = false;
            boolean useKeyManagerFactory = false;
            boolean supportsHostNameValidation = false;
            try {
                final long sslCtx = SSLContext.make(SSL.SSL_PROTOCOL_ALL, SSL.SSL_MODE_SERVER);
                long certBio = 0;
                SelfSignedCertificate cert = null;
                try {
                    SSLContext.setCipherSuite(sslCtx, "ALL");
                    final long ssl = SSL.newSSL(sslCtx, true);
                    try {
                        for (String c: SSL.getCiphers(ssl)) {
                            // Filter out bad input.
                            if (c == null || c.isEmpty() || availableOpenSslCipherSuites.contains(c)) {
                                continue;
                            }
                            availableOpenSslCipherSuites.add(c);
                        }

                        try {
                            SSL.setHostNameValidation(ssl, 0, "netty.io");
                            supportsHostNameValidation = true;
                        } catch (Throwable ignore) {
                            logger.debug("Hostname Verification not supported.");
                        }
                        try {
                            cert = new SelfSignedCertificate();
                            certBio = ReferenceCountedOpenSslContext.toBIO(cert.cert());
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
                        if (cert != null) {
                            cert.delete();
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
                availableJavaCipherSuites.add(CipherSuiteConverter.toJava(cipher, "TLS"));
                availableJavaCipherSuites.add(CipherSuiteConverter.toJava(cipher, "SSL"));
            }

            addIfSupported(availableJavaCipherSuites, defaultCiphers, DEFAULT_CIPHER_SUITES);
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
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_SSLV2)) {
                protocols.add(PROTOCOL_SSL_V2);
            }
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_SSLV3)) {
                protocols.add(PROTOCOL_SSL_V3);
            }
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_TLSV1)) {
                protocols.add(PROTOCOL_TLS_V1);
            }
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_TLSV1_1)) {
                protocols.add(PROTOCOL_TLS_V1_1);
            }
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_TLSV1_2)) {
                protocols.add(PROTOCOL_TLS_V1_2);
            }

            SUPPORTED_PROTOCOLS_SET = Collections.unmodifiableSet(protocols);
            SUPPORTS_OCSP = doesSupportOcsp();

            if (logger.isDebugEnabled()) {
                logger.debug("Supported protocols (OpenSSL): {} ", Arrays.asList(SUPPORTED_PROTOCOLS_SET));
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
        }
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
    private static boolean doesSupportProtocol(int protocol) {
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
        String converted = CipherSuiteConverter.toOpenSsl(cipherSuite);
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
            libNames.toArray(new String[libNames.size()]));
    }

    private static boolean initializeTcNative() throws Exception {
        return Library.initialize();
    }

    static void releaseIfNeeded(ReferenceCounted counted) {
        if (counted.refCnt() > 0) {
            ReferenceCountUtil.safeRelease(counted);
        }
    }
}
