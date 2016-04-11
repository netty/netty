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
import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.tomcat.jni.Buffer;
import org.apache.tomcat.jni.Library;
import org.apache.tomcat.jni.Pool;
import org.apache.tomcat.jni.SSL;
import org.apache.tomcat.jni.SSLContext;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
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

    private static final Set<String> AVAILABLE_CIPHER_SUITES;

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

        UNAVAILABILITY_CAUSE = cause;

        if (cause == null) {
            final Set<String> availableCipherSuites = new LinkedHashSet<String>(128);
            final long aprPool = Pool.create(0);
            try {
                final long sslCtx = SSLContext.make(aprPool, SSL.SSL_PROTOCOL_ALL, SSL.SSL_MODE_SERVER);
                try {
                    SSLContext.setOptions(sslCtx, SSL.SSL_OP_ALL);
                    SSLContext.setCipherSuite(sslCtx, "ALL");
                    final long ssl = SSL.newSSL(sslCtx, true);
                    try {
                        for (String c: SSL.getCiphers(ssl)) {
                            // Filter out bad input.
                            if (c == null || c.length() == 0 || availableCipherSuites.contains(c)) {
                                continue;
                            }
                            availableCipherSuites.add(c);
                        }
                    } finally {
                        SSL.freeSSL(ssl);
                    }
                } finally {
                    SSLContext.free(sslCtx);
                }
            } catch (Exception e) {
                logger.warn("Failed to get the list of available OpenSSL cipher suites.", e);
            } finally {
                Pool.destroy(aprPool);
            }

            AVAILABLE_CIPHER_SUITES = Collections.unmodifiableSet(availableCipherSuites);
        } else {
            AVAILABLE_CIPHER_SUITES = Collections.emptySet();
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
     * Returns all the available OpenSSL cipher suites.
     * Please note that the returned array may include the cipher suites that are insecure or non-functional.
     */
    public static Set<String> availableCipherSuites() {
        return AVAILABLE_CIPHER_SUITES;
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
        return AVAILABLE_CIPHER_SUITES.contains(cipherSuite);
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
}
