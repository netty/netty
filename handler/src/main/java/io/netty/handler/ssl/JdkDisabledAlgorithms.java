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
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Evaluates the {@code jdk.tls.disabledAlgorithms} <strong>security</strong> property (see
 * {@link Security#getProperty(String)}) so the OpenSSL based implementations can honor it in the same way
 * as the JDK's {@link javax.net.ssl.SSLEngine} does.
 * <p>
 * Only plain, unconditional entries are supported: protocol names (e.g. {@code TLSv1}) and algorithm names that
 * are a component of a cipher suite name (e.g. {@code RC4}, {@code 3DES_EDE_CBC}, {@code anon}). Entries with
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

    private final Set<String> disabled;

    JdkDisabledAlgorithms(String value) {
        disabled = parse(value);
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

    private static Set<String> parse(String value) {
        if (value == null) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<String>();
        for (String entry : value.split(",")) {
            String algorithm = entry.trim();
            if (algorithm.isEmpty() || containsWhitespace(algorithm)) {
                // Constraint or directive, we can only handle plain names.
                continue;
            }
            result.add(algorithm.toLowerCase(Locale.ROOT));
        }
        return result;
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
     * Returns {@code true} if the given cipher suite (using the JSSE / standard Java name, e.g.
     * {@code TLS_RSA_WITH_RC4_128_SHA}) contains a disabled algorithm as one of its {@code '_'} separated components.
     */
    boolean isCipherSuiteDisabled(String cipherSuite) {
        if (disabled.isEmpty()) {
            return false;
        }
        // Add a delimiter to both sides so we can match components, including multi-part ones like 3DES_EDE_CBC,
        // without matching a part of a component (DES vs 3DES, ECDH vs ECDHE).
        String name = '_' + cipherSuite.toLowerCase(Locale.ROOT) + '_';
        for (String algorithm : disabled) {
            if (name.contains('_' + algorithm + '_')) {
                return true;
            }
        }
        return false;
    }
}
