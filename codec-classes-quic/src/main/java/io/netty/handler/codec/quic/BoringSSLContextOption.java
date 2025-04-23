/*
 * Copyright 2024 The Netty Project
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
package io.netty.handler.codec.quic;

import io.netty.handler.ssl.SslContextOption;

import java.util.Map;
import java.util.Set;

/**
 * {@link SslContextOption}s that are specific to BoringSSL.
 *
 * @param <T>   the type of the value.
 */
public final class BoringSSLContextOption<T> extends SslContextOption<T> {
    private BoringSSLContextOption(String name) {
        super(name);
    }

    /**
     * Set the groups that should be used. This will override curves set with {@code -Djdk.tls.namedGroups}.
     * <p>
     * See <a href="https://github.com/google/boringssl/blob/master/include/openssl/ssl.h#L2632">
     *     SSL_CTX_set1_groups_list</a>.
     */
    public static final BoringSSLContextOption<String[]> GROUPS = new BoringSSLContextOption<>("GROUPS");

    /**
     * Set the signature algorithms that should be used.
     * <p>
     * See <a href="https://github.com/google/boringssl/blob/master/include/openssl/ssl.h#L5166">
     *     SSL_CTX_set1_sigalgs</a>.
     */
    public static final BoringSSLContextOption<String[]> SIGNATURE_ALGORITHMS =
            new BoringSSLContextOption<>("SIGNATURE_ALGORITHMS");

    /**
     * Set the supported client key/certificate types used in BoringSSLCertificateCallback
     */
    public static final BoringSSLContextOption<Set<String>> CLIENT_KEY_TYPES =
            new BoringSSLContextOption<>("CLIENT_KEY_TYPES");

    /**
     * Set the supported server key/certificate types used in BoringSSLCertificateCallback
     */
    public static final BoringSSLContextOption<Map<String, String>> SERVER_KEY_TYPES =
            new BoringSSLContextOption<>("SERVER_KEY_TYPES");
}
