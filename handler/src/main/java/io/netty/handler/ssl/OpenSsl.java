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
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.tomcat.Apr;
import org.apache.tomcat.jni.Buffer;
import org.apache.tomcat.jni.Library;
import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;

import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/**
 * Tells if <a href="http://netty.io/wiki/forked-tomcat-native.html">{@code netty-tcnative}</a> and its OpenSSL support
 * are available.
 */
public final class OpenSsl {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(OpenSsl.class);
    private static final String LINUX = "linux";
    private static final String UNKNOWN = "unknown";
    private static final Throwable UNAVAILABILITY_CAUSE;

    static final Set<String> AVAILABLE_CIPHER_SUITES;
    private static final Set<String> AVAILABLE_OPENSSL_CIPHER_SUITES;
    private static final Set<String> AVAILABLE_JAVA_CIPHER_SUITES;
    private static final boolean SUPPORTS_KEYMANAGER_FACTORY;
    private static final boolean USE_KEYMANAGER_FACTORY;

    // Protocols
    static final String PROTOCOL_SSL_V2_HELLO = "SSLv2Hello";
    static final String PROTOCOL_SSL_V2 = "SSLv2";
    static final String PROTOCOL_SSL_V3 = "SSLv3";
    static final String PROTOCOL_TLS_V1 = "TLSv1";
    static final String PROTOCOL_TLS_V1_1 = "TLSv1.1";
    static final String PROTOCOL_TLS_V1_2 = "TLSv1.2";

    private static final String[] SUPPORTED_PROTOCOLS = {
            PROTOCOL_SSL_V2_HELLO,
            PROTOCOL_SSL_V2,
            PROTOCOL_SSL_V3,
            PROTOCOL_TLS_V1,
            PROTOCOL_TLS_V1_1,
            PROTOCOL_TLS_V1_2
    };
    static final Set<String> SUPPORTED_PROTOCOLS_SET = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(SUPPORTED_PROTOCOLS)));

    static {
        Throwable cause = null;

        // Test if netty-tcnative is in the classpath first.
        try {
            Class.forName("org.apache.tomcat.jni.SSL", false, OpenSsl.class.getClassLoader());
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

        if (cause == null && !isNettyTcnative()) {
            logger.debug("incompatible tcnative in the classpath; "
                    + OpenSslEngine.class.getSimpleName() + " will be unavailable.");
            cause = new ClassNotFoundException("incompatible tcnative in the classpath");
        }

        UNAVAILABILITY_CAUSE = cause;

        if (cause == null) {
            final Set<String> availableOpenSslCipherSuites = new LinkedHashSet<String>(128);
            boolean supportsKeyManagerFactory = false;
            boolean useKeyManagerFactory = false;
            final long aprPool = Pool.create(0);
            try {
                final long sslCtx = SSLContext.make(aprPool, SSL.SSL_PROTOCOL_ALL, SSL.SSL_MODE_SERVER);
                long privateKeyBio = 0;
                long certBio = 0;
                try {
                    SSLContext.setOptions(sslCtx, SSL.SSL_OP_ALL);
                    SSLContext.setCipherSuite(sslCtx, "ALL");
                    final long ssl = SSL.newSSL(sslCtx, true);
                    try {
                        for (String c: SSL.getCiphers(ssl)) {
                            // Filter out bad input.
                            if (c == null || c.length() == 0 || availableOpenSslCipherSuites.contains(c)) {
                                continue;
                            }
                            availableOpenSslCipherSuites.add(c);
                        }
                        try {
                            SelfSignedCertificate cert = new SelfSignedCertificate();
                            certBio = OpenSslContext.toBIO(cert.cert());
                            SSL.setCertificateChainBio(ssl, certBio, false);
                            supportsKeyManagerFactory = true;
                            useKeyManagerFactory = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
                                @Override
                                public Boolean run() {
                                    return SystemPropertyUtil.getBoolean(
                                            "io.netty.handler.ssl.openssl.useKeyManagerFactory", true);
                                }
                            });
                        } catch (Throwable ignore) {
                            logger.debug("KeyManagerFactory not supported.");
                        }
                    } finally {
                        SSL.freeSSL(ssl);
                        if (privateKeyBio != 0) {
                            SSL.freeBIO(privateKeyBio);
                        }
                        if (certBio != 0) {
                            SSL.freeBIO(certBio);
                        }
                    }
                } finally {
                    SSLContext.free(sslCtx);
                }
            } catch (Exception e) {
                logger.warn("Failed to get the list of available OpenSSL cipher suites.", e);
            } finally {
                Pool.destroy(aprPool);
            }
            AVAILABLE_OPENSSL_CIPHER_SUITES = Collections.unmodifiableSet(availableOpenSslCipherSuites);

            final Set<String> availableJavaCipherSuites = new LinkedHashSet<String>(
                    AVAILABLE_OPENSSL_CIPHER_SUITES.size() * 2);
            for (String cipher: AVAILABLE_OPENSSL_CIPHER_SUITES) {
                // Included converted but also openssl cipher name
                availableJavaCipherSuites.add(CipherSuiteConverter.toJava(cipher, "TLS"));
                availableJavaCipherSuites.add(CipherSuiteConverter.toJava(cipher, "SSL"));
            }
            AVAILABLE_JAVA_CIPHER_SUITES = Collections.unmodifiableSet(availableJavaCipherSuites);

            final Set<String> availableCipherSuites = new LinkedHashSet<String>(
                    AVAILABLE_OPENSSL_CIPHER_SUITES.size() + AVAILABLE_JAVA_CIPHER_SUITES.size());
            for (String cipher: AVAILABLE_OPENSSL_CIPHER_SUITES) {
                availableCipherSuites.add(cipher);
            }
            for (String cipher: AVAILABLE_JAVA_CIPHER_SUITES) {
                availableCipherSuites.add(cipher);
            }
            AVAILABLE_CIPHER_SUITES = availableCipherSuites;
            SUPPORTS_KEYMANAGER_FACTORY = supportsKeyManagerFactory;
            USE_KEYMANAGER_FACTORY = useKeyManagerFactory;
        } else {
            AVAILABLE_OPENSSL_CIPHER_SUITES = Collections.emptySet();
            AVAILABLE_JAVA_CIPHER_SUITES = Collections.emptySet();
            AVAILABLE_CIPHER_SUITES = Collections.emptySet();
            SUPPORTS_KEYMANAGER_FACTORY = false;
            USE_KEYMANAGER_FACTORY = false;
        }
    }

    private static boolean isNettyTcnative() {
        return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            @Override
            public Boolean run() {
                InputStream is = null;
                try {
                    is = Apr.class.getResourceAsStream("/org/apache/tomcat/apr.properties");
                    Properties props = new Properties();
                    props.load(is);
                    String info = props.getProperty("tcn.info");
                    return info != null && info.startsWith("netty-tcnative");
                } catch (Throwable ignore) {
                    return false;
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ignore) {
                            // ignore
                        }
                    }
                }
            }
        });
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
     * Returns the version of the used available OpenSSL library or {@code -1} if {@link #isAvailable()}
     * returns {@code false}.
     */
    public static int version() {
        if (isAvailable()) {
            return SSL.version();
        }
        return -1;
    }

    /**
     * Returns the version string of the used available OpenSSL library or {@code null} if {@link #isAvailable()}
     * returns {@code false}.
     */
    public static String versionString() {
        if (isAvailable()) {
            return SSL.versionString();
        }
        return null;
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

    static boolean useKeyManagerFactory() {
        return USE_KEYMANAGER_FACTORY;
    }

    static boolean isError(long errorCode) {
        return errorCode != SSL.SSL_ERROR_NONE;
    }

    static long memoryAddress(ByteBuf buf) {
        assert buf.isDirect();
        return buf.hasMemoryAddress() ? buf.memoryAddress() : Buffer.address(buf.nioBuffer());
    }

    private OpenSsl() { }

    private static void loadTcNative() throws Exception {
        String os = normalizeOs(SystemPropertyUtil.get("os.name", ""));
        String arch = normalizeArch(SystemPropertyUtil.get("os.arch", ""));

        Set<String> libNames = new LinkedHashSet<String>(3);
        // First, try loading the platform-specific library. Platform-specific
        // libraries will be available if using a tcnative uber jar.
        libNames.add("netty-tcnative-" + os + '-' + arch);
        if (LINUX.equalsIgnoreCase(os)) {
            // Fedora SSL lib so naming (libssl.so.10 vs libssl.so.1.0.0)..
            libNames.add("netty-tcnative-" + os + '-' + arch + "-fedora");
        }
        // finally the default library.
        libNames.add("netty-tcnative");

        NativeLibraryLoader.loadFirstAvailable(SSL.class.getClassLoader(),
            libNames.toArray(new String[libNames.size()]));
    }

    private static void initializeTcNative() throws Exception {
        Library.initialize("provided");
        SSL.initialize(null);
    }

    private static String normalizeOs(String value) {
        value = normalize(value);
        if (value.startsWith("aix")) {
            return "aix";
        }
        if (value.startsWith("hpux")) {
            return "hpux";
        }
        if (value.startsWith("os400")) {
            // Avoid the names such as os4000
            if (value.length() <= 5 || !Character.isDigit(value.charAt(5))) {
                return "os400";
            }
        }
        if (value.startsWith(LINUX)) {
            return LINUX;
        }
        if (value.startsWith("macosx") || value.startsWith("osx")) {
            return "osx";
        }
        if (value.startsWith("freebsd")) {
            return "freebsd";
        }
        if (value.startsWith("openbsd")) {
            return "openbsd";
        }
        if (value.startsWith("netbsd")) {
            return "netbsd";
        }
        if (value.startsWith("solaris") || value.startsWith("sunos")) {
            return "sunos";
        }
        if (value.startsWith("windows")) {
            return "windows";
        }

        return UNKNOWN;
    }

    private static String normalizeArch(String value) {
        value = normalize(value);
        if (value.matches("^(x8664|amd64|ia32e|em64t|x64)$")) {
            return "x86_64";
        }
        if (value.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) {
            return "x86_32";
        }
        if (value.matches("^(ia64|itanium64)$")) {
            return "itanium_64";
        }
        if (value.matches("^(sparc|sparc32)$")) {
            return "sparc_32";
        }
        if (value.matches("^(sparcv9|sparc64)$")) {
            return "sparc_64";
        }
        if (value.matches("^(arm|arm32)$")) {
            return "arm_32";
        }
        if ("aarch64".equals(value)) {
            return "aarch_64";
        }
        if (value.matches("^(ppc|ppc32)$")) {
            return "ppc_32";
        }
        if ("ppc64".equals(value)) {
            return "ppc_64";
        }
        if ("ppc64le".equals(value)) {
            return "ppcle_64";
        }
        if ("s390".equals(value)) {
            return "s390_32";
        }
        if ("s390x".equals(value)) {
            return "s390_64";
        }

        return UNKNOWN;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
    }

    static void releaseIfNeeded(ReferenceCounted counted) {
        if (counted.refCnt() > 0) {
            ReferenceCountUtil.safeRelease(counted);
        }
    }
}
