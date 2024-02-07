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

import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.singletonMap;

/**
 * Converts a Java cipher suite string to an OpenSSL cipher suite string and vice versa.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Cipher_suite">Wikipedia page about cipher suite</a>
 */
@UnstableApi
public final class CipherSuiteConverter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(CipherSuiteConverter.class);

    /**
     * A_B_WITH_C_D, where:
     *
     * A - TLS or SSL (protocol)
     * B - handshake algorithm (key exchange and authentication algorithms to be precise)
     * C - bulk cipher
     * D - HMAC algorithm
     *
     * This regular expression assumes that:
     *
     * 1) A is always TLS or SSL, and
     * 2) D is always a single word.
     */
    private static final Pattern JAVA_CIPHERSUITE_PATTERN =
            Pattern.compile("^(?:TLS|SSL)_((?:(?!_WITH_).)+)_WITH_(.*)_(.*)$");

    /**
     * A-B-C, where:
     *
     * A - handshake algorithm (key exchange and authentication algorithms to be precise)
     * B - bulk cipher
     * C - HMAC algorithm
     *
     * This regular expression assumes that:
     *
     * 1) A has some deterministic pattern as shown below, and
     * 2) C is always a single word
     */
    private static final Pattern OPENSSL_CIPHERSUITE_PATTERN =
            // Be very careful not to break the indentation while editing.
            Pattern.compile(
                    "^(?:(" + // BEGIN handshake algorithm
                        "(?:(?:EXP-)?" +
                            "(?:" +
                                "(?:DHE|EDH|ECDH|ECDHE|SRP|RSA)-(?:DSS|RSA|ECDSA|PSK)|" +
                                "(?:ADH|AECDH|KRB5|PSK|SRP)" +
                            ')' +
                        ")|" +
                        "EXP" +
                    ")-)?" +  // END handshake algorithm
                    "(.*)-(.*)$");

    private static final Pattern JAVA_AES_CBC_PATTERN = Pattern.compile("^(AES)_([0-9]+)_CBC$");
    private static final Pattern JAVA_AES_PATTERN = Pattern.compile("^(AES)_([0-9]+)_(.*)$");
    private static final Pattern OPENSSL_AES_CBC_PATTERN = Pattern.compile("^(AES)([0-9]+)$");
    private static final Pattern OPENSSL_AES_PATTERN = Pattern.compile("^(AES)([0-9]+)-(.*)$");

    /**
     * Used to store nullable values in a CHM
     */
    private static final class CachedValue {

        private static final CachedValue NULL = new CachedValue(null);

        static CachedValue of(String value) {
            return value != null ? new CachedValue(value) : NULL;
        }

        final String value;
        private CachedValue(String value) {
            this.value = value;
        }
    }

    /**
     * Java-to-OpenSSL cipher suite conversion map
     * Note that the Java cipher suite has the protocol prefix (TLS_, SSL_)
     */
    private static final ConcurrentMap<String, CachedValue> j2o = PlatformDependent.newConcurrentHashMap();

    /**
     * OpenSSL-to-Java cipher suite conversion map.
     * Note that one OpenSSL cipher suite can be converted to more than one Java cipher suites because
     * a Java cipher suite has the protocol name prefix (TLS_, SSL_)
     */
    private static final ConcurrentMap<String, Map<String, String>> o2j = PlatformDependent.newConcurrentHashMap();

    private static final Map<String, String> j2oTls13;
    private static final Map<String, Map<String, String>> o2jTls13;

    static {
        Map<String, String> j2oTls13Map = new HashMap<String, String>();
        j2oTls13Map.put("TLS_AES_128_GCM_SHA256", "AEAD-AES128-GCM-SHA256");
        j2oTls13Map.put("TLS_AES_256_GCM_SHA384", "AEAD-AES256-GCM-SHA384");
        j2oTls13Map.put("TLS_CHACHA20_POLY1305_SHA256", "AEAD-CHACHA20-POLY1305-SHA256");
        j2oTls13 = Collections.unmodifiableMap(j2oTls13Map);

        Map<String, Map<String, String>> o2jTls13Map = new HashMap<String, Map<String, String>>();
        o2jTls13Map.put("TLS_AES_128_GCM_SHA256", singletonMap("TLS", "TLS_AES_128_GCM_SHA256"));
        o2jTls13Map.put("TLS_AES_256_GCM_SHA384", singletonMap("TLS", "TLS_AES_256_GCM_SHA384"));
        o2jTls13Map.put("TLS_CHACHA20_POLY1305_SHA256", singletonMap("TLS", "TLS_CHACHA20_POLY1305_SHA256"));
        o2jTls13Map.put("AEAD-AES128-GCM-SHA256", singletonMap("TLS", "TLS_AES_128_GCM_SHA256"));
        o2jTls13Map.put("AEAD-AES256-GCM-SHA384", singletonMap("TLS", "TLS_AES_256_GCM_SHA384"));
        o2jTls13Map.put("AEAD-CHACHA20-POLY1305-SHA256", singletonMap("TLS", "TLS_CHACHA20_POLY1305_SHA256"));
        o2jTls13 = Collections.unmodifiableMap(o2jTls13Map);
    }

    /**
     * Clears the cache for testing purpose.
     */
    static void clearCache() {
        j2o.clear();
        o2j.clear();
    }

    /**
     * Tests if the specified key-value pair has been cached in Java-to-OpenSSL cache.
     */
    static boolean isJ2OCached(String key, String value) {
        CachedValue cached = j2o.get(key);
        return cached != null && value.equals(cached.value);
    }

    /**
     * Tests if the specified key-value pair has been cached in OpenSSL-to-Java cache.
     */
    static boolean isO2JCached(String key, String protocol, String value) {
        Map<String, String> p2j = o2j.get(key);
        if (p2j == null) {
            return false;
        } else {
            return value.equals(p2j.get(protocol));
        }
    }

    /**
     * Converts the specified Java cipher suite to its corresponding OpenSSL cipher suite name.
     *
     * @return {@code null} if the conversion has failed
     */
    public static String toOpenSsl(String javaCipherSuite, boolean boringSSL) {
        CachedValue converted = j2o.get(javaCipherSuite);
        if (converted != null) {
            return converted.value;
        }
        return cacheFromJava(javaCipherSuite, boringSSL);
    }

    private static String cacheFromJava(String javaCipherSuite, boolean boringSSL) {
        String converted = j2oTls13.get(javaCipherSuite);
        if (converted != null) {
            return boringSSL ? converted : javaCipherSuite;
        }

        String openSslCipherSuite = toOpenSslUncached(javaCipherSuite, boringSSL);

        // Cache the mapping.
        j2o.putIfAbsent(javaCipherSuite, CachedValue.of(openSslCipherSuite));

        if (openSslCipherSuite == null) {
            return null;
        }

        // Cache the reverse mapping after stripping the protocol prefix (TLS_ or SSL_)
        final String javaCipherSuiteSuffix = javaCipherSuite.substring(4);
        Map<String, String> p2j = new HashMap<String, String>(4);
        p2j.put("", javaCipherSuiteSuffix);
        p2j.put("SSL", "SSL_" + javaCipherSuiteSuffix);
        p2j.put("TLS", "TLS_" + javaCipherSuiteSuffix);
        o2j.put(openSslCipherSuite, p2j);

        logger.debug("Cipher suite mapping: {} => {}", javaCipherSuite, openSslCipherSuite);

        return openSslCipherSuite;
    }

    static String toOpenSslUncached(String javaCipherSuite, boolean boringSSL) {
        String converted = j2oTls13.get(javaCipherSuite);
        if (converted != null) {
            return boringSSL ? converted : javaCipherSuite;
        }

        Matcher m = JAVA_CIPHERSUITE_PATTERN.matcher(javaCipherSuite);
        if (!m.matches()) {
            return null;
        }

        String handshakeAlgo = toOpenSslHandshakeAlgo(m.group(1));
        String bulkCipher = toOpenSslBulkCipher(m.group(2));
        String hmacAlgo = toOpenSslHmacAlgo(m.group(3));
        if (handshakeAlgo.isEmpty()) {
            return bulkCipher + '-' + hmacAlgo;
        } else if (bulkCipher.contains("CHACHA20")) {
            return handshakeAlgo + '-' + bulkCipher;
        } else {
            return handshakeAlgo + '-' + bulkCipher + '-' + hmacAlgo;
        }
    }

    private static String toOpenSslHandshakeAlgo(String handshakeAlgo) {
        final boolean export = handshakeAlgo.endsWith("_EXPORT");
        if (export) {
            handshakeAlgo = handshakeAlgo.substring(0, handshakeAlgo.length() - 7);
        }

        if ("RSA".equals(handshakeAlgo)) {
            handshakeAlgo = "";
        } else if (handshakeAlgo.endsWith("_anon")) {
            handshakeAlgo = 'A' + handshakeAlgo.substring(0, handshakeAlgo.length() - 5);
        }

        if (export) {
            if (handshakeAlgo.isEmpty()) {
                handshakeAlgo = "EXP";
            } else {
                handshakeAlgo = "EXP-" + handshakeAlgo;
            }
        }

        return handshakeAlgo.replace('_', '-');
    }

    private static String toOpenSslBulkCipher(String bulkCipher) {
        if (bulkCipher.startsWith("AES_")) {
            Matcher m = JAVA_AES_CBC_PATTERN.matcher(bulkCipher);
            if (m.matches()) {
                return m.replaceFirst("$1$2");
            }

            m = JAVA_AES_PATTERN.matcher(bulkCipher);
            if (m.matches()) {
                return m.replaceFirst("$1$2-$3");
            }
        }

        if ("3DES_EDE_CBC".equals(bulkCipher)) {
            return "DES-CBC3";
        }

        if ("RC4_128".equals(bulkCipher) || "RC4_40".equals(bulkCipher)) {
            return "RC4";
        }

        if ("DES40_CBC".equals(bulkCipher) || "DES_CBC_40".equals(bulkCipher)) {
            return "DES-CBC";
        }

        if ("RC2_CBC_40".equals(bulkCipher)) {
            return "RC2-CBC";
        }

        return bulkCipher.replace('_', '-');
    }

    private static String toOpenSslHmacAlgo(String hmacAlgo) {
        // Java and OpenSSL use the same algorithm names for:
        //
        //   * SHA
        //   * SHA256
        //   * MD5
        //
        return hmacAlgo;
    }

    /**
     * Convert from OpenSSL cipher suite name convention to java cipher suite name convention.
     * @param openSslCipherSuite An OpenSSL cipher suite name.
     * @param protocol The cryptographic protocol (i.e. SSL, TLS, ...).
     * @return The translated cipher suite name according to java conventions. This will not be {@code null}.
     */
    public static String toJava(String openSslCipherSuite, String protocol) {
        Map<String, String> p2j = o2j.get(openSslCipherSuite);
        if (p2j == null) {
            p2j = cacheFromOpenSsl(openSslCipherSuite);
            // This may happen if this method is queried when OpenSSL doesn't yet have a cipher setup. It will return
            // "(NONE)" in this case.
            if (p2j == null) {
                return null;
            }
        }

        String javaCipherSuite = p2j.get(protocol);
        if (javaCipherSuite == null) {
            String cipher = p2j.get("");
            if (cipher == null) {
                return null;
            }
            javaCipherSuite = protocol + '_' + cipher;
        }

        return javaCipherSuite;
    }

    private static Map<String, String> cacheFromOpenSsl(String openSslCipherSuite) {
        Map<String, String> converted = o2jTls13.get(openSslCipherSuite);
        if (converted != null) {
            return converted;
        }

        String javaCipherSuiteSuffix = toJavaUncached0(openSslCipherSuite, false);
        if (javaCipherSuiteSuffix == null) {
            return null;
        }

        final String javaCipherSuiteSsl = "SSL_" + javaCipherSuiteSuffix;
        final String javaCipherSuiteTls = "TLS_" + javaCipherSuiteSuffix;

        // Cache the mapping.
        final Map<String, String> p2j = new HashMap<String, String>(4);
        p2j.put("", javaCipherSuiteSuffix);
        p2j.put("SSL", javaCipherSuiteSsl);
        p2j.put("TLS", javaCipherSuiteTls);
        o2j.putIfAbsent(openSslCipherSuite, p2j);

        // Cache the reverse mapping after adding the protocol prefix (TLS_ or SSL_)
        CachedValue cachedValue = CachedValue.of(openSslCipherSuite);
        j2o.putIfAbsent(javaCipherSuiteTls, cachedValue);
        j2o.putIfAbsent(javaCipherSuiteSsl, cachedValue);

        logger.debug("Cipher suite mapping: {} => {}", javaCipherSuiteTls, openSslCipherSuite);
        logger.debug("Cipher suite mapping: {} => {}", javaCipherSuiteSsl, openSslCipherSuite);

        return p2j;
    }

    static String toJavaUncached(String openSslCipherSuite) {
        return toJavaUncached0(openSslCipherSuite, true);
    }

    private static String toJavaUncached0(String openSslCipherSuite, boolean checkTls13) {
        if (checkTls13) {
            Map<String, String> converted = o2jTls13.get(openSslCipherSuite);
            if (converted != null) {
                return converted.get("TLS");
            }
        }

        Matcher m = OPENSSL_CIPHERSUITE_PATTERN.matcher(openSslCipherSuite);
        if (!m.matches()) {
            return null;
        }

        String handshakeAlgo = m.group(1);
        final boolean export;
        if (handshakeAlgo == null) {
            handshakeAlgo = "";
            export = false;
        } else if (handshakeAlgo.startsWith("EXP-")) {
            handshakeAlgo = handshakeAlgo.substring(4);
            export = true;
        } else if ("EXP".equals(handshakeAlgo)) {
            handshakeAlgo = "";
            export = true;
        } else {
            export = false;
        }

        handshakeAlgo = toJavaHandshakeAlgo(handshakeAlgo, export);
        String bulkCipher = toJavaBulkCipher(m.group(2), export);
        String hmacAlgo = toJavaHmacAlgo(m.group(3));

        String javaCipherSuite = handshakeAlgo + "_WITH_" + bulkCipher + '_' + hmacAlgo;
        // For historical reasons the CHACHA20 ciphers do not follow OpenSSL's custom naming convention and omits the
        // HMAC algorithm portion of the name. There is currently no way to derive this information because it is
        // omitted from the OpenSSL cipher name, but they currently all use SHA256 for HMAC [1].
        // [1] https://www.openssl.org/docs/man1.1.0/apps/ciphers.html
        return bulkCipher.contains("CHACHA20") ? javaCipherSuite + "_SHA256" : javaCipherSuite;
    }

    private static String toJavaHandshakeAlgo(String handshakeAlgo, boolean export) {
        if (handshakeAlgo.isEmpty()) {
            handshakeAlgo = "RSA";
        } else if ("ADH".equals(handshakeAlgo)) {
            handshakeAlgo = "DH_anon";
        } else if ("AECDH".equals(handshakeAlgo)) {
            handshakeAlgo = "ECDH_anon";
        }

        handshakeAlgo = handshakeAlgo.replace('-', '_');
        if (export) {
            return handshakeAlgo + "_EXPORT";
        } else {
            return handshakeAlgo;
        }
    }

    private static String toJavaBulkCipher(String bulkCipher, boolean export) {
        if (bulkCipher.startsWith("AES")) {
            Matcher m = OPENSSL_AES_CBC_PATTERN.matcher(bulkCipher);
            if (m.matches()) {
                return m.replaceFirst("$1_$2_CBC");
            }

            m = OPENSSL_AES_PATTERN.matcher(bulkCipher);
            if (m.matches()) {
                return m.replaceFirst("$1_$2_$3");
            }
        }

        if ("DES-CBC3".equals(bulkCipher)) {
            return "3DES_EDE_CBC";
        }

        if ("RC4".equals(bulkCipher)) {
            if (export) {
                return "RC4_40";
            } else {
                return "RC4_128";
            }
        }

        if ("DES-CBC".equals(bulkCipher)) {
            if (export) {
                return "DES_CBC_40";
            } else {
                return "DES_CBC";
            }
        }

        if ("RC2-CBC".equals(bulkCipher)) {
            if (export) {
                return "RC2_CBC_40";
            } else {
                return "RC2_CBC";
            }
        }

        return bulkCipher.replace('-', '_');
    }

    private static String toJavaHmacAlgo(String hmacAlgo) {
        // Java and OpenSSL use the same algorithm names for:
        //
        //   * SHA
        //   * SHA256
        //   * MD5
        //
        return hmacAlgo;
    }

    /**
     * Convert the given ciphers if needed to OpenSSL format and append them to the correct {@link StringBuilder}
     * depending on if its a TLSv1.3 cipher or not. If this methods returns without throwing an exception its
     * guaranteed that at least one of the {@link StringBuilder}s contain some ciphers that can be used to configure
     * OpenSSL.
     */
    static void convertToCipherStrings(Iterable<String> cipherSuites, StringBuilder cipherBuilder,
                                       StringBuilder cipherTLSv13Builder, boolean boringSSL) {
        for (String c: cipherSuites) {
            if (c == null) {
                break;
            }

            String converted = toOpenSsl(c, boringSSL);
            if (converted == null) {
                converted = c;
            }

            if (!OpenSsl.isCipherSuiteAvailable(converted)) {
                throw new IllegalArgumentException("unsupported cipher suite: " + c + '(' + converted + ')');
            }

            if (SslUtils.isTLSv13Cipher(converted) || SslUtils.isTLSv13Cipher(c)) {
                cipherTLSv13Builder.append(converted);
                cipherTLSv13Builder.append(':');
            } else {
                cipherBuilder.append(converted);
                cipherBuilder.append(':');
            }
        }

        if (cipherBuilder.length() == 0 && cipherTLSv13Builder.length() == 0) {
            throw new IllegalArgumentException("empty cipher suites");
        }
        if (cipherBuilder.length() > 0) {
            cipherBuilder.setLength(cipherBuilder.length() - 1);
        }
        if (cipherTLSv13Builder.length() > 0) {
            cipherTLSv13Builder.setLength(cipherTLSv13Builder.length() - 1);
        }
    }

    private CipherSuiteConverter() { }
}
