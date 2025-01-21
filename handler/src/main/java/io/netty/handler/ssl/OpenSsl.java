/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
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
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.netty.handler.ssl.SslUtils.*;

/**
 * Tells if <a href="https://netty.io/wiki/forked-tomcat-native.html">{@code netty-tcnative}</a> and its OpenSSL support
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
    private static final Set<String> CLIENT_DEFAULT_PROTOCOLS;
    private static final Set<String> SERVER_DEFAULT_PROTOCOLS;
    static final Set<String> SUPPORTED_PROTOCOLS_SET;
    static final String[] EXTRA_SUPPORTED_TLS_1_3_CIPHERS;
    static final String EXTRA_SUPPORTED_TLS_1_3_CIPHERS_STRING;
    static final String[] NAMED_GROUPS;

    static final boolean JAVAX_CERTIFICATE_CREATION_SUPPORTED;

    // Use default that is supported in java 11 and earlier and also in OpenSSL / BoringSSL.
    // See https://github.com/netty/netty-tcnative/issues/567
    // See https://www.java.com/en/configure_crypto.html for ordering
    private static final String[] DEFAULT_NAMED_GROUPS = { "x25519", "secp256r1", "secp384r1", "secp521r1" };

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
                Class.forName("io.netty.internal.tcnative.SSLContext", false,
                        PlatformDependent.getClassLoader(OpenSsl.class));
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
                                    "See https://netty.io/wiki/forked-tomcat-native.html for more information.", t);
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
                                    "See https://netty.io/wiki/forked-tomcat-native.html for more information.", t);
                }
            }
        }

        UNAVAILABILITY_CAUSE = cause;
        CLIENT_DEFAULT_PROTOCOLS = defaultProtocols("jdk.tls.client.protocols");
        SERVER_DEFAULT_PROTOCOLS = defaultProtocols("jdk.tls.server.protocols");

        if (cause == null) {
            logger.debug("netty-tcnative using native library: {}", SSL.versionString());

            final List<String> defaultCiphers = new ArrayList<String>();
            final Set<String> availableOpenSslCipherSuites = new LinkedHashSet<String>(128);
            boolean supportsKeyManagerFactory = false;
            boolean useKeyManagerFactory = false;
            boolean tlsv13Supported = false;
            String[] namedGroups = DEFAULT_NAMED_GROUPS;
            Set<String> defaultConvertedNamedGroups = new LinkedHashSet<String>(namedGroups.length);
            for (int i = 0; i < namedGroups.length; i++) {
                defaultConvertedNamedGroups.add(GroupsConverter.toOpenSsl(namedGroups[i]));
            }

            IS_BORINGSSL = "BoringSSL".equals(versionString());
            if (IS_BORINGSSL) {
                EXTRA_SUPPORTED_TLS_1_3_CIPHERS = new String [] { "TLS_AES_128_GCM_SHA256",
                        "TLS_AES_256_GCM_SHA384" ,
                        "TLS_CHACHA20_POLY1305_SHA256" };

                StringBuilder ciphersBuilder = new StringBuilder(128);
                for (String cipher: EXTRA_SUPPORTED_TLS_1_3_CIPHERS) {
                    ciphersBuilder.append(cipher).append(":");
                }
                ciphersBuilder.setLength(ciphersBuilder.length() - 1);
                EXTRA_SUPPORTED_TLS_1_3_CIPHERS_STRING = ciphersBuilder.toString();
            }  else {
                EXTRA_SUPPORTED_TLS_1_3_CIPHERS = EmptyArrays.EMPTY_STRINGS;
                EXTRA_SUPPORTED_TLS_1_3_CIPHERS_STRING = StringUtil.EMPTY_STRING;
            }

            try {
                final long sslCtx = SSLContext.make(SSL.SSL_PROTOCOL_ALL, SSL.SSL_MODE_SERVER);

                // Let's filter out any group that is not supported from the default.
                Iterator<String> defaultGroupsIter = defaultConvertedNamedGroups.iterator();
                while (defaultGroupsIter.hasNext()) {
                    if (!SSLContext.setCurvesList(sslCtx, defaultGroupsIter.next())) {
                        // Not supported, let's remove it. This could for example be the case if we use
                        // fips and the configure group is not supported when using FIPS.
                        // See https://github.com/netty/netty-tcnative/issues/883
                        defaultGroupsIter.remove();
                    }
                }
                namedGroups = defaultConvertedNamedGroups.toArray(EmptyArrays.EMPTY_STRINGS);

                long certBio = 0;
                long keyBio = 0;
                long cert = 0;
                long key = 0;
                try {
                    // As we delegate to the KeyManager / TrustManager of the JDK we need to ensure it can actually
                    // handle TLSv13 as otherwise we may see runtime exceptions
                    if (SslProvider.isTlsv13Supported(SslProvider.JDK)) {
                        try {
                            StringBuilder tlsv13Ciphers = new StringBuilder();

                            for (String cipher : TLSV13_CIPHERS) {
                                String converted = CipherSuiteConverter.toOpenSsl(cipher, IS_BORINGSSL);
                                if (converted != null) {
                                    tlsv13Ciphers.append(converted).append(':');
                                }
                            }
                            if (tlsv13Ciphers.length() == 0) {
                                tlsv13Supported = false;
                            } else {
                                tlsv13Ciphers.setLength(tlsv13Ciphers.length() - 1);
                                SSLContext.setCipherSuite(sslCtx, tlsv13Ciphers.toString(), true);
                                tlsv13Supported = true;
                            }

                        } catch (Exception ignore) {
                            tlsv13Supported = false;
                        }
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
                            Collections.addAll(availableOpenSslCipherSuites, EXTRA_SUPPORTED_TLS_1_3_CIPHERS);
                            Collections.addAll(availableOpenSslCipherSuites,
                                               "AEAD-AES128-GCM-SHA256",
                                               "AEAD-AES256-GCM-SHA384",
                                               "AEAD-CHACHA20-POLY1305-SHA256");
                        }

                        PemEncoded privateKey = PemPrivateKey.valueOf(PROBING_KEY.getBytes(CharsetUtil.US_ASCII));
                        try {
                            // Let's check if we can set a callback, which may not work if the used OpenSSL version
                            // is to old.
                            SSLContext.setCertificateCallback(sslCtx, null);

                            X509Certificate certificate = selfSignedCertificate();
                            certBio = ReferenceCountedOpenSslContext.toBIO(ByteBufAllocator.DEFAULT, certificate);
                            cert = SSL.parseX509Chain(certBio);

                            keyBio = ReferenceCountedOpenSslContext.toBIO(
                                    UnpooledByteBufAllocator.DEFAULT, privateKey.retain());
                            key = SSL.parsePrivateKey(keyBio, null);

                            SSL.setKeyMaterial(ssl, cert, key);
                            supportsKeyManagerFactory = true;
                            try {
                                boolean propertySet = SystemPropertyUtil.contains(
                                        "io.netty.handler.ssl.openssl.useKeyManagerFactory");
                                if (!IS_BORINGSSL) {
                                    useKeyManagerFactory = SystemPropertyUtil.getBoolean(
                                            "io.netty.handler.ssl.openssl.useKeyManagerFactory", true);

                                    if (propertySet) {
                                        logger.info("System property " +
                                                "'io.netty.handler.ssl.openssl.useKeyManagerFactory'" +
                                                " is deprecated and so will be ignored in the future");
                                    }
                                } else {
                                    useKeyManagerFactory = true;
                                    if (propertySet) {
                                        logger.info("System property " +
                                                "'io.netty.handler.ssl.openssl.useKeyManagerFactory'" +
                                                " is deprecated and will be ignored when using BoringSSL");
                                    }
                                }
                            } catch (Throwable ignore) {
                                logger.debug("Failed to get useKeyManagerFactory system property.");
                            }
                        } catch (Exception e) {
                            logger.debug("KeyManagerFactory not supported", e);
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

                    String groups = SystemPropertyUtil.get("jdk.tls.namedGroups", null);
                    if (groups != null) {
                        String[] nGroups = groups.split(",");
                        Set<String> supportedNamedGroups = new LinkedHashSet<String>(nGroups.length);
                        Set<String> supportedConvertedNamedGroups = new LinkedHashSet<String>(nGroups.length);

                        Set<String> unsupportedNamedGroups = new LinkedHashSet<String>();
                        for (String namedGroup : nGroups) {
                            String converted = GroupsConverter.toOpenSsl(namedGroup);
                            if (SSLContext.setCurvesList(sslCtx, converted)) {
                                supportedConvertedNamedGroups.add(converted);
                                supportedNamedGroups.add(namedGroup);
                            } else {
                                unsupportedNamedGroups.add(namedGroup);
                            }
                        }

                        if (supportedNamedGroups.isEmpty()) {
                            namedGroups = defaultConvertedNamedGroups.toArray(EmptyArrays.EMPTY_STRINGS);
                            logger.info("All configured namedGroups are not supported: {}. Use default: {}.",
                                    Arrays.toString(unsupportedNamedGroups.toArray(EmptyArrays.EMPTY_STRINGS)),
                                    Arrays.toString(DEFAULT_NAMED_GROUPS));
                        } else {
                            String[] groupArray = supportedNamedGroups.toArray(EmptyArrays.EMPTY_STRINGS);
                            if (unsupportedNamedGroups.isEmpty()) {
                                logger.info("Using configured namedGroups -D 'jdk.tls.namedGroup': {} ",
                                        Arrays.toString(groupArray));
                            } else {
                                logger.info("Using supported configured namedGroups: {}. Unsupported namedGroups: {}. ",
                                        Arrays.toString(groupArray),
                                        Arrays.toString(unsupportedNamedGroups.toArray(EmptyArrays.EMPTY_STRINGS)));
                            }
                            namedGroups =  supportedConvertedNamedGroups.toArray(EmptyArrays.EMPTY_STRINGS);
                        }
                    } else {
                        namedGroups = defaultConvertedNamedGroups.toArray(EmptyArrays.EMPTY_STRINGS);
                    }
                } finally {
                    SSLContext.free(sslCtx);
                }
            } catch (Exception e) {
                logger.warn("Failed to get the list of available OpenSSL cipher suites.", e);
            }
            NAMED_GROUPS = namedGroups;
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
            // Also handle the extra supported ciphers as these will contain some more stuff on BoringSSL.
            addIfSupported(availableJavaCipherSuites, defaultCiphers, EXTRA_SUPPORTED_TLS_1_3_CIPHERS);

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
            protocols.add(SslProtocols.SSL_v2_HELLO);
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_SSLV2, SSL.SSL_OP_NO_SSLv2)) {
                protocols.add(SslProtocols.SSL_v2);
            }
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_SSLV3, SSL.SSL_OP_NO_SSLv3)) {
                protocols.add(SslProtocols.SSL_v3);
            }
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_TLSV1, SSL.SSL_OP_NO_TLSv1)) {
                protocols.add(SslProtocols.TLS_v1);
            }
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_TLSV1_1, SSL.SSL_OP_NO_TLSv1_1)) {
                protocols.add(SslProtocols.TLS_v1_1);
            }
            if (doesSupportProtocol(SSL.SSL_PROTOCOL_TLSV1_2, SSL.SSL_OP_NO_TLSv1_2)) {
                protocols.add(SslProtocols.TLS_v1_2);
            }

            // This is only supported by java8u272 and later.
            if (tlsv13Supported && doesSupportProtocol(SSL.SSL_PROTOCOL_TLSV1_3, SSL.SSL_OP_NO_TLSv1_3)) {
                protocols.add(SslProtocols.TLS_v1_3);
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

            // Check if we can create a javax.security.cert.X509Certificate from our cert. This might fail on
            // JDK17 and above. In this case we will later throw an UnsupportedOperationException if someone
            // tries to access these via SSLSession. See https://github.com/netty/netty/issues/13560.
            boolean javaxCertificateCreationSupported;
            try {
                javax.security.cert.X509Certificate.getInstance(PROBING_CERT.getBytes(CharsetUtil.US_ASCII));
                javaxCertificateCreationSupported = true;
            } catch (javax.security.cert.CertificateException ex) {
                javaxCertificateCreationSupported = false;
            }
            JAVAX_CERTIFICATE_CREATION_SUPPORTED = javaxCertificateCreationSupported;
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
            EXTRA_SUPPORTED_TLS_1_3_CIPHERS = EmptyArrays.EMPTY_STRINGS;
            EXTRA_SUPPORTED_TLS_1_3_CIPHERS_STRING = StringUtil.EMPTY_STRING;
            NAMED_GROUPS = DEFAULT_NAMED_GROUPS;
            JAVAX_CERTIFICATE_CREATION_SUPPORTED = false;
        }
    }

    static String checkTls13Ciphers(InternalLogger logger, String ciphers) {
        if (IS_BORINGSSL && !ciphers.isEmpty()) {
            assert EXTRA_SUPPORTED_TLS_1_3_CIPHERS.length > 0;
            Set<String> boringsslTlsv13Ciphers = new HashSet<String>(EXTRA_SUPPORTED_TLS_1_3_CIPHERS.length);
            Collections.addAll(boringsslTlsv13Ciphers, EXTRA_SUPPORTED_TLS_1_3_CIPHERS);
            boolean ciphersNotMatch = false;
            for (String cipher: ciphers.split(":")) {
                if (boringsslTlsv13Ciphers.isEmpty()) {
                    ciphersNotMatch = true;
                    break;
                }
                if (!boringsslTlsv13Ciphers.remove(cipher) &&
                        !boringsslTlsv13Ciphers.remove(CipherSuiteConverter.toJava(cipher, "TLS"))) {
                    ciphersNotMatch = true;
                    break;
                }
            }

            // Also check if there are ciphers left.
            ciphersNotMatch |= !boringsslTlsv13Ciphers.isEmpty();

            if (ciphersNotMatch) {
                if (logger.isInfoEnabled()) {
                    StringBuilder javaCiphers = new StringBuilder(128);
                    for (String cipher : ciphers.split(":")) {
                        javaCiphers.append(CipherSuiteConverter.toJava(cipher, "TLS")).append(":");
                    }
                    javaCiphers.setLength(javaCiphers.length() - 1);
                    logger.info(
                            "BoringSSL doesn't allow to enable or disable TLSv1.3 ciphers explicitly." +
                                    " Provided TLSv1.3 ciphers: '{}', default TLSv1.3 ciphers that will be used: '{}'.",
                            javaCiphers, EXTRA_SUPPORTED_TLS_1_3_CIPHERS_STRING);
                }
                return EXTRA_SUPPORTED_TLS_1_3_CIPHERS_STRING;
            }
        }
        return ciphers;
    }

    static boolean isSessionCacheSupported() {
        return version() >= 0x10100000L;
    }

    /**
     * Returns a self-signed {@link X509Certificate} for {@code netty.io}.
     */
    static X509Certificate selfSignedCertificate() throws CertificateException {
        return (X509Certificate) SslContext.X509_CERT_FACTORY.generateCertificate(
                new ByteArrayInputStream(PROBING_CERT.getBytes(CharsetUtil.US_ASCII))
        );
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
     * <a href="https://netty.io/wiki/forked-tomcat-native.html">{@code netty-tcnative}</a> and its OpenSSL support
     * are available.
     */
    public static boolean isAvailable() {
        return UNAVAILABILITY_CAUSE == null;
    }

    /**
     * Returns {@code true} if the used version of openssl supports
     * <a href="https://tools.ietf.org/html/rfc7301">ALPN</a>.
     *
     * @deprecated use {@link SslProvider#isAlpnSupported(SslProvider)} with {@link SslProvider#OPENSSL}.
     */
    @Deprecated
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
     * Ensure that <a href="https://netty.io/wiki/forked-tomcat-native.html">{@code netty-tcnative}</a> and
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
     * <a href="https://netty.io/wiki/forked-tomcat-native.html">{@code netty-tcnative}</a> and its OpenSSL support.
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
        return buf.hasMemoryAddress() ? buf.memoryAddress() :
                // Use internalNioBuffer to reduce object creation.
                Buffer.address(buf.internalNioBuffer(0, buf.readableBytes()));
    }

    private OpenSsl() { }

    private static void loadTcNative() throws Exception {
        String os = PlatformDependent.normalizedOs();
        String arch = PlatformDependent.normalizedArch();

        Set<String> libNames = new LinkedHashSet<String>(5);
        String staticLibName = "netty_tcnative";

        // First, try loading the platform-specific library. Platform-specific
        // libraries will be available if using a tcnative uber jar.
        if ("linux".equals(os)) {
            Set<String> classifiers = PlatformDependent.normalizedLinuxClassifiers();
            for (String classifier : classifiers) {
                libNames.add(staticLibName + "_" + os + '_' + arch + "_" + classifier);
            }
            // generic arch-dependent library
            libNames.add(staticLibName + "_" + os + '_' + arch);

            // Fedora SSL lib so naming (libssl.so.10 vs libssl.so.1.0.0).
            // note: should already be included from the classifiers but if not, we use this as an
            //       additional fallback option here
            libNames.add(staticLibName + "_" + os + '_' + arch + "_fedora");
        } else {
            libNames.add(staticLibName + "_" + os + '_' + arch);
        }
        libNames.add(staticLibName + "_" + arch);
        libNames.add(staticLibName);

        NativeLibraryLoader.loadFirstAvailable(PlatformDependent.getClassLoader(SSLContext.class),
            libNames.toArray(EmptyArrays.EMPTY_STRINGS));
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

    static boolean isOptionSupported(SslContextOption<?> option) {
        if (isAvailable()) {
            if (option == OpenSslContextOption.USE_TASKS) {
                return true;
            }
            // Check for options that are only supported by BoringSSL atm.
            if (isBoringSSL()) {
                return option == OpenSslContextOption.ASYNC_PRIVATE_KEY_METHOD ||
                        option == OpenSslContextOption.PRIVATE_KEY_METHOD ||
                        option == OpenSslContextOption.CERTIFICATE_COMPRESSION_ALGORITHMS ||
                        option == OpenSslContextOption.TLS_FALSE_START ||
                        option == OpenSslContextOption.MAX_CERTIFICATE_LIST_BYTES;
            }
        }
        return false;
    }

    private static Set<String> defaultProtocols(String property) {
        String protocolsString = SystemPropertyUtil.get(property, null);
        Set<String> protocols = new HashSet<String>();
        if (protocolsString != null) {
            for (String proto : protocolsString.split(",")) {
                String p = proto.trim();
                protocols.add(p);
            }
        } else {
            protocols.add(SslProtocols.TLS_v1_2);
            protocols.add(SslProtocols.TLS_v1_3);
        }
        return protocols;
    }

    static String[] defaultProtocols(boolean isClient) {
        final Collection<String> defaultProtocols = isClient ? CLIENT_DEFAULT_PROTOCOLS : SERVER_DEFAULT_PROTOCOLS;
        assert defaultProtocols != null;
        List<String> protocols = new ArrayList<String>(defaultProtocols.size());
        for (String proto : defaultProtocols) {
            if (SUPPORTED_PROTOCOLS_SET.contains(proto)) {
                protocols.add(proto);
            }
        }
        return protocols.toArray(EmptyArrays.EMPTY_STRINGS);
    }

    static boolean isBoringSSL() {
        return IS_BORINGSSL;
    }
}
