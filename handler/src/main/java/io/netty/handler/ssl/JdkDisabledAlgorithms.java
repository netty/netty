/*
 * Copyright 2026 The Netty Project
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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Evaluates the {@code jdk.tls.disabledAlgorithms} <strong>security</strong> property (see
 * {@link Security#getProperty(String)}) so the OpenSSL based implementations can honor it in the same way
 * as the JDK's {@link javax.net.ssl.SSLEngine} does.
 * <p>
 * Only plain, unconditional entries are supported: protocol names (e.g. {@code TLSv1}), algorithm names that
 * are a component of a cipher suite name (e.g. {@code RC4}, {@code 3DES_EDE_CBC}, {@code anon}), the standard names
 * the JDK uses for the key exchange and MAC algorithms of a cipher suite (e.g. {@code DH}, {@code SHA1},
 * {@code HmacSHA256}) and cipher suite names with {@code '*'} wildcards (e.g. {@code TLS_RSA_*}). Entries with
 * constraints (e.g. {@code RSA keySize < 1024}) or directives (e.g. {@code include ...}) contain whitespace and are
 * ignored as they can not be evaluated from the name alone.
 * <p>
 * The property is read when {@link #current()} is called (once per {@code SslContext}), so a value set via
 * {@link Security#setProperty(String, String)} before the context is created is honored, even if the OpenSSL classes
 * were initialized earlier.
 */
final class JdkDisabledAlgorithms {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(JdkDisabledAlgorithms.class);

    static final String PROPERTY = "jdk.tls.disabledAlgorithms";

    private static final String[] NO_PATTERNS = new String[0];

    private final Set<String> disabled;
    // Cipher suite name patterns that contain '*' wildcards, in lower case.
    private final String[] wildcards;

    JdkDisabledAlgorithms(String value) {
        Set<String> plain = new HashSet<String>();
        List<String> patterns = new ArrayList<String>();
        parse(value, plain, patterns);
        disabled = plain.isEmpty() ? Collections.<String>emptySet() : plain;
        wildcards = patterns.isEmpty() ? NO_PATTERNS : patterns.toArray(new String[0]);
    }

    /**
     * Returns the algorithms that are currently disabled via the {@code jdk.tls.disabledAlgorithms} security property.
     */
    static JdkDisabledAlgorithms current() {
        return new JdkDisabledAlgorithms(read());
    }

    private static String read() {
        try {
            return Security.getProperty(PROPERTY);
        } catch (Throwable cause) {
            // For example a SecurityException.
            logger.debug("Unable to read the security property '{}'", PROPERTY, cause);
            return null;
        }
    }

    private static void parse(String value, Set<String> plain, List<String> patterns) {
        if (value == null) {
            return;
        }
        for (String entry : value.split(",")) {
            String algorithm = entry.trim();
            if (algorithm.isEmpty() || containsWhitespace(algorithm)) {
                // Constraint or directive, we can only handle plain names.
                continue;
            }
            algorithm = algorithm.toLowerCase(Locale.ROOT);
            if (algorithm.indexOf('*') >= 0) {
                // Same as the JDK, only cipher suites starting with TLS_ are allowed to have wildcard characters.
                if (algorithm.startsWith("tls_")) {
                    patterns.add(algorithm);
                }
                continue;
            }
            plain.add(algorithm);
        }
    }

    private static boolean containsWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    boolean isProtocolDisabled(String protocol) {
        return !disabled.isEmpty() && disabled.contains(protocol.toLowerCase(Locale.ROOT));
    }

    /**
     * Returns {@code true} if the given cipher suite is disabled. Both the JSSE / standard Java name (e.g.
     * {@code TLS_RSA_WITH_RC4_128_SHA}) and the OpenSSL name (e.g. {@code AES128-SHA}) are supported.
     * <p>
     * A cipher suite is disabled if it matches a wildcard pattern, or if it contains a disabled algorithm as one of
     * its {@code '_'} separated components or as one of the standard algorithm names the JDK decomposes a cipher suite
     * into.
     */
    boolean isCipherSuiteDisabled(String cipherSuite) {
        if (disabled.isEmpty() && wildcards.length == 0) {
            return false;
        }
        String name = cipherSuite.toLowerCase(Locale.ROOT);
        if (!name.startsWith("tls_") && !name.startsWith("ssl_")) {
            // Might be an OpenSSL name, we need to evaluate the Java name as that is what the property is based on.
            String javaName = CipherSuiteConverter.toJava(cipherSuite, "TLS");
            if (javaName != null) {
                name = javaName.toLowerCase(Locale.ROOT);
            }
        }
        return isJavaCipherSuiteDisabled(name);
    }

    private boolean isJavaCipherSuiteDisabled(String name) {
        for (String pattern : wildcards) {
            if (matchesWildcard(pattern, name)) {
                return true;
            }
        }
        if (disabled.isEmpty()) {
            return false;
        }
        // Add a delimiter to both sides so we can match components, including multi-part ones like 3DES_EDE_CBC,
        // without matching a part of a component (DES vs 3DES, ECDH vs ECDHE).
        String delimited = '_' + name + '_';
        for (String algorithm : disabled) {
            if (delimited.contains('_' + algorithm + '_')) {
                return true;
            }
        }
        // The JDK also decomposes a cipher suite into the standard names of its algorithms, which do not necessarily
        // match a component of the cipher suite name.
        return containsDisabled(keyExchangeAliases(name)) || containsDisabled(macAliases(name)) ||
                (delimited.contains("_3des_ede_cbc_") && disabled.contains("desede"));
    }

    private boolean containsDisabled(String[] aliases) {
        for (String alias : aliases) {
            if (disabled.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private static String[] keyExchangeAliases(String name) {
        int with = name.indexOf("_with_");
        if (with <= 4) {
            // TLSv1.3 cipher suites have no key exchange component.
            return NO_PATTERNS;
        }
        String keyExchange = name.substring(4, with);
        if (keyExchange.startsWith("dh")) {
            // DH, DHE, DH_anon, ... are all DiffieHellman. This does not include ECDH(E).
            return keyExchange.endsWith("dss") ?
                    new String[] { "dh", "diffiehellman", "dsa" } : new String[] { "dh", "diffiehellman" };
        }
        return NO_PATTERNS;
    }

    private static String[] macAliases(String name) {
        int idx = name.lastIndexOf('_');
        String mac = idx == -1 ? name : name.substring(idx + 1);
        // Also used for the PRF / hash of AEAD cipher suites.
        switch (mac) {
            case "md5":
                return new String[] { "hmacmd5" };
            case "sha":
                return new String[] { "sha1", "sha-1", "hmacsha1" };
            case "sha256":
                return new String[] { "sha-256", "hmacsha256" };
            case "sha384":
                return new String[] { "sha-384", "hmacsha384" };
            default:
                return NO_PATTERNS;
        }
    }

    // Both arguments are expected to be lower case.
    private static boolean matchesWildcard(String pattern, String name) {
        int p = 0;
        int n = 0;
        int starIdx = -1;
        int matchIdx = 0;
        while (n < name.length()) {
            if (p < pattern.length() && pattern.charAt(p) == '*') {
                starIdx = p++;
                matchIdx = n;
            } else if (p < pattern.length() && pattern.charAt(p) == name.charAt(n)) {
                p++;
                n++;
            } else if (starIdx != -1) {
                p = starIdx + 1;
                n = ++matchIdx;
            } else {
                return false;
            }
        }
        while (p < pattern.length() && pattern.charAt(p) == '*') {
            p++;
        }
        return p == pattern.length();
    }
}
