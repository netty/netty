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

import io.netty.util.internal.NativeLibraryLoader;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.apache.tomcat.jni.Library;
import org.apache.tomcat.jni.SSL;

import java.util.HashMap;
import java.util.Map;

/**
 * Tells if <a href="http://netty.io/wiki/forked-tomcat-native.html">{@code netty-tcnative}</a> and its OpenSSL support
 * are available.
 */
public final class OpenSsl {

    private static final Map<String, String> OPENSSL_TO_JDK_CIPHER_SUITES = new HashMap<String, String>();
    private static final Map<String, String> JDK_TO_OPENSSL_CIPHER_SUITES = new HashMap<String, String>();

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(OpenSsl.class);
    private static final Throwable UNAVAILABILITY_CAUSE;

    static {
        Throwable cause = null;
        try {
            NativeLibraryLoader.load("netty-tcnative", SSL.class.getClassLoader());
            Library.initialize("provided");
            SSL.initialize(null);
        } catch (Throwable t) {
            cause = t;
            logger.debug(
                    "Failed to load netty-tcnative; " +
                            OpenSslEngine.class.getSimpleName() + " will be unavailable.", t);
        }
        UNAVAILABILITY_CAUSE = cause;
        initCipherSuiteMappings();
    }

    private static void initCipherSuiteMappings() {
        // This conversation was adapted from android source.
        // See https://android.googlesource.com/platform/libcore/+/android-cts-4.1_r2/
        //     luni/src/main/java/org/apache/harmony/xnet/provider/jsse/NativeCrypto.java
        // Note these are added in priority order
        addCipherSuite("SSL_RSA_WITH_RC4_128_MD5",              "RC4-MD5");
        addCipherSuite("SSL_RSA_WITH_RC4_128_SHA",              "RC4-SHA");
        addCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA",          "AES128-SHA");
        addCipherSuite("TLS_RSA_WITH_AES_256_CBC_SHA",          "AES256-SHA");
        addCipherSuite("TLS_ECDH_ECDSA_WITH_RC4_128_SHA",       "ECDH-ECDSA-RC4-SHA");
        addCipherSuite("TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",   "ECDH-ECDSA-AES128-SHA");
        addCipherSuite("TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",   "ECDH-ECDSA-AES256-SHA");
        addCipherSuite("TLS_ECDH_RSA_WITH_RC4_128_SHA",         "ECDH-RSA-RC4-SHA");
        addCipherSuite("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",     "ECDH-RSA-AES128-SHA");
        addCipherSuite("TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",     "ECDH-RSA-AES256-SHA");
        addCipherSuite("TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",      "ECDHE-ECDSA-RC4-SHA");
        addCipherSuite("TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",  "ECDHE-ECDSA-AES128-SHA");
        addCipherSuite("TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",  "ECDHE-ECDSA-AES256-SHA");
        addCipherSuite("TLS_ECDHE_RSA_WITH_RC4_128_SHA",        "ECDHE-RSA-RC4-SHA");
        addCipherSuite("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",    "ECDHE-RSA-AES128-SHA");
        addCipherSuite("TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",    "ECDHE-RSA-AES256-SHA");
        addCipherSuite("TLS_DHE_RSA_WITH_AES_128_CBC_SHA",      "DHE-RSA-AES128-SHA");
        addCipherSuite("TLS_DHE_RSA_WITH_AES_256_CBC_SHA",      "DHE-RSA-AES256-SHA");
        addCipherSuite("TLS_DHE_DSS_WITH_AES_128_CBC_SHA",      "DHE-DSS-AES128-SHA");
        addCipherSuite("TLS_DHE_DSS_WITH_AES_256_CBC_SHA",      "DHE-DSS-AES256-SHA");
        addCipherSuite("SSL_RSA_WITH_3DES_EDE_CBC_SHA",         "DES-CBC3-SHA");
        addCipherSuite("TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",  "ECDH-ECDSA-DES-CBC3-SHA");
        addCipherSuite("TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",    "ECDH-RSA-DES-CBC3-SHA");
        addCipherSuite("TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA", "ECDHE-ECDSA-DES-CBC3-SHA");
        addCipherSuite("TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",   "ECDHE-RSA-DES-CBC3-SHA");
        addCipherSuite("SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",     "EDH-RSA-DES-CBC3-SHA");
        addCipherSuite("SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA",     "EDH-DSS-DES-CBC3-SHA");
        addCipherSuite("SSL_RSA_WITH_DES_CBC_SHA",              "DES-CBC-SHA");
        addCipherSuite("SSL_DHE_RSA_WITH_DES_CBC_SHA",          "EDH-RSA-DES-CBC-SHA");
        addCipherSuite("SSL_DHE_DSS_WITH_DES_CBC_SHA",          "EDH-DSS-DES-CBC-SHA");
        addCipherSuite("SSL_RSA_EXPORT_WITH_RC4_40_MD5",        "EXP-RC4-MD5");
        addCipherSuite("SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",     "EXP-DES-CBC-SHA");
        addCipherSuite("SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", "EXP-EDH-RSA-DES-CBC-SHA");
        addCipherSuite("SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA", "EXP-EDH-DSS-DES-CBC-SHA");
        addCipherSuite("SSL_RSA_WITH_NULL_MD5",                 "NULL-MD5");
        addCipherSuite("SSL_RSA_WITH_NULL_SHA",                 "NULL-SHA");
        addCipherSuite("TLS_ECDH_ECDSA_WITH_NULL_SHA",          "ECDH-ECDSA-NULL-SHA");
        addCipherSuite("TLS_ECDH_RSA_WITH_NULL_SHA",            "ECDH-RSA-NULL-SHA");
        addCipherSuite("TLS_ECDHE_ECDSA_WITH_NULL_SHA",         "ECDHE-ECDSA-NULL-SHA");
        addCipherSuite("TLS_ECDHE_RSA_WITH_NULL_SHA",           "ECDHE-RSA-NULL-SHA");
        addCipherSuite("SSL_DH_anon_WITH_RC4_128_MD5",          "ADH-RC4-MD5");
        addCipherSuite("TLS_DH_anon_WITH_AES_128_CBC_SHA",      "ADH-AES128-SHA");
        addCipherSuite("TLS_DH_anon_WITH_AES_256_CBC_SHA",      "ADH-AES256-SHA");
        addCipherSuite("SSL_DH_anon_WITH_3DES_EDE_CBC_SHA",     "ADH-DES-CBC3-SHA");
        addCipherSuite("SSL_DH_anon_WITH_DES_CBC_SHA",          "ADH-DES-CBC-SHA");
        addCipherSuite("TLS_ECDH_anon_WITH_RC4_128_SHA",        "AECDH-RC4-SHA");
        addCipherSuite("TLS_ECDH_anon_WITH_AES_128_CBC_SHA",    "AECDH-AES128-SHA");
        addCipherSuite("TLS_ECDH_anon_WITH_AES_256_CBC_SHA",    "AECDH-AES256-SHA");
        addCipherSuite("TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",   "AECDH-DES-CBC3-SHA");
        addCipherSuite("SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",    "EXP-ADH-RC4-MD5");
        addCipherSuite("SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA", "EXP-ADH-DES-CBC-SHA");
        addCipherSuite("TLS_ECDH_anon_WITH_NULL_SHA",           "AECDH-NULL-SHA");
        addCipherSuite("TLS_KRB5_WITH_RC4_128_SHA",           "KRB5-RC4-SHA");
        addCipherSuite("TLS_KRB5_WITH_RC4_128_MD5",           "KRB5-RC4-MD5");
        addCipherSuite("TLS_KRB5_WITH_3DES_EDE_CBC_SHA",      "KRB5-DES-CBC3-SHA");
        addCipherSuite("TLS_KRB5_WITH_3DES_EDE_CBC_MD5",      "KRB5-DES-CBC3-MD5");
        addCipherSuite("TLS_KRB5_WITH_DES_CBC_SHA",           "KRB5-DES-CBC-SHA");
        addCipherSuite("TLS_KRB5_WITH_DES_CBC_MD5",           "KRB5-DES-CBC-MD5");
        addCipherSuite("TLS_KRB5_EXPORT_WITH_RC4_40_SHA",     "EXP-KRB5-RC4-SHA");
        addCipherSuite("TLS_KRB5_EXPORT_WITH_RC4_40_MD5",     "EXP-KRB5-RC4-MD5");
        addCipherSuite("TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA", "EXP-KRB5-DES-CBC-SHA");
        addCipherSuite("TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5", "EXP-KRB5-DES-CBC-MD5");
        addCipherSuite("SSL_RSA_EXPORT_WITH_RC2_CBC_40_MD5",  "EXP-RC2-CBC-MD5");
        addCipherSuite("TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA", "EXP-KRB5-RC2-CBC-SHA");
        addCipherSuite("TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5", "EXP-KRB5-RC2-CBC-MD5");
    }

    private static void addCipherSuite(String jdk, String openssl) {
        JDK_TO_OPENSSL_CIPHER_SUITES.put(jdk, openssl);
        OPENSSL_TO_JDK_CIPHER_SUITES.put(openssl, jdk);
    }

    static String openSslCipher(String cipher) {
        return JDK_TO_OPENSSL_CIPHER_SUITES.get(cipher);
    }

    static String jdkSslCipher(String cipher) {
        return OPENSSL_TO_JDK_CIPHER_SUITES.get(cipher);
    }

    static String[] supportedCiphers() {
        return JDK_TO_OPENSSL_CIPHER_SUITES.keySet().toArray(new String[JDK_TO_OPENSSL_CIPHER_SUITES.size()]);
    }

    static String cipherIfSupported(String cipher) {
        String mapped = JDK_TO_OPENSSL_CIPHER_SUITES.get(cipher);
        if (mapped == null) {
            if (OPENSSL_TO_JDK_CIPHER_SUITES.containsKey(cipher)) {
                mapped = cipher;
            }
        }
        return mapped;
    }

    static String ciphers(String[] ciphers) {
        // Convert the cipher list into a colon-separated string.
        StringBuilder cipherBuf = new StringBuilder();
        for (String c: ciphers) {
            // Support JDK cipher names and OpenSSL ones.
            String mapped = JDK_TO_OPENSSL_CIPHER_SUITES.get(c);
            if (mapped == null) {
                mapped = c;
            }
            cipherBuf.append(mapped);
            cipherBuf.append(':');
        }
        cipherBuf.setLength(cipherBuf.length() - 1);
        return cipherBuf.toString();
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

    static boolean isError(long errorCode) {
        return errorCode != SSL.SSL_ERROR_NONE;
    }

    private OpenSsl() { }
}
