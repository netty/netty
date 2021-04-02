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
package io.netty.handler.codec.http2;

import io.netty.util.internal.UnstableApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Provides utilities related to security requirements specific to HTTP/2.
 */
@UnstableApi
public final class Http2SecurityUtil {
    /**
     * The following list is derived from <a
     * href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/SunProviders.html">SunJSSE Supported
     * Ciphers</a> and <a
     * href="https://wiki.mozilla.org/Security/Server_Side_TLS#Modern_compatibility">Mozilla Modern Cipher
     * Suites</a> in accordance with the <a
     * href="https://tools.ietf.org/html/rfc7540#section-9.2.2">HTTP/2 Specification</a>.
     *
     * According to the <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html">
     * JSSE documentation</a> "the names mentioned in the TLS RFCs prefixed with TLS_ are functionally equivalent
     * to the JSSE cipher suites prefixed with SSL_".
     * Both variants are used to support JVMs supporting the one or the other.
     */
    public static final List<String> CIPHERS;

    /**
     * <a href="https://wiki.mozilla.org/Security/Server_Side_TLS#Intermediate_compatibility_.28recommended.29"
     * >Mozilla Modern Cipher Suites Intermediate compatibility</a> minus the following cipher suites that are black
     * listed by the <a href="https://tools.ietf.org/html/rfc7540#appendix-A">HTTP/2 RFC</a>.
     */
    private static final List<String> CIPHERS_JAVA_MOZILLA_MODERN_SECURITY = Collections.unmodifiableList(Arrays
            .asList(
            /* openssl = ECDHE-ECDSA-AES128-GCM-SHA256 */
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",

            /* REQUIRED BY HTTP/2 SPEC */
            /* openssl = ECDHE-RSA-AES128-GCM-SHA256 */
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            /* REQUIRED BY HTTP/2 SPEC */

            /* openssl = ECDHE-ECDSA-AES256-GCM-SHA384 */
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            /* openssl = ECDHE-RSA-AES256-GCM-SHA384 */
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            /* openssl = ECDHE-ECDSA-CHACHA20-POLY1305 */
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
            /* openssl = ECDHE-RSA-CHACHA20-POLY1305 */
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256",

            /* TLS 1.3 ciphers */
            "TLS_AES_128_GCM_SHA256",
            "TLS_AES_256_GCM_SHA384",
            "TLS_CHACHA20_POLY1305_SHA256"
            ));

    static {
        CIPHERS = Collections.unmodifiableList(new ArrayList<String>(CIPHERS_JAVA_MOZILLA_MODERN_SECURITY));
    }

    private Http2SecurityUtil() { }
}
