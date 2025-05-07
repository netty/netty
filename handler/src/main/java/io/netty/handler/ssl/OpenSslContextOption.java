/*
 * Copyright 2021 The Netty Project
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

/**
 * {@link SslContextOption}s that are specific to the {@link SslProvider#OPENSSL} / {@link SslProvider#OPENSSL_REFCNT}.
 *
 * @param <T>   the type of the value.
 */
public final class OpenSslContextOption<T> extends SslContextOption<T> {

    private OpenSslContextOption(String name) {
        super(name);
    }

    /**
     * If enabled heavy-operations may be offloaded from the {@link io.netty.channel.EventLoop} if possible.
     */
    public static final OpenSslContextOption<Boolean> USE_TASKS =
            new OpenSslContextOption<Boolean>("USE_TASKS");
    /**
     * If enabled <a href="https://tools.ietf.org/html/rfc7918">TLS false start</a> will be enabled if supported.
     * When TLS false start is enabled the flow of {@link SslHandshakeCompletionEvent}s may be different compared when,
     * not enabled.
     *
     * This is currently only supported when {@code BoringSSL} and ALPN is used.
     */
    public static final OpenSslContextOption<Boolean> TLS_FALSE_START =
            new OpenSslContextOption<Boolean>("TLS_FALSE_START");

    /**
     * Set the {@link OpenSslPrivateKeyMethod} to use. This allows to offload private-key operations
     * if needed.
     *
     * This is currently only supported when {@code BoringSSL} is used.
     */
    public static final OpenSslContextOption<OpenSslPrivateKeyMethod> PRIVATE_KEY_METHOD =
            new OpenSslContextOption<OpenSslPrivateKeyMethod>("PRIVATE_KEY_METHOD");

    /**
     * Set the {@link OpenSslAsyncPrivateKeyMethod} to use. This allows to offload private-key operations
     * if needed.
     *
     * This is currently only supported when {@code BoringSSL} is used.
     */
    public static final OpenSslContextOption<OpenSslAsyncPrivateKeyMethod> ASYNC_PRIVATE_KEY_METHOD =
            new OpenSslContextOption<OpenSslAsyncPrivateKeyMethod>("ASYNC_PRIVATE_KEY_METHOD");

    /**
     * Set the {@link OpenSslCertificateCompressionConfig} to use. This allows for the configuration of certificate
     * compression algorithms which should be used, the priority of those algorithms and the directions in which
     * they should be used.
     *
     * This is currently only supported when {@code BoringSSL} is used.
     */
    public static final OpenSslContextOption<OpenSslCertificateCompressionConfig> CERTIFICATE_COMPRESSION_ALGORITHMS =
            new OpenSslContextOption<OpenSslCertificateCompressionConfig>("CERTIFICATE_COMPRESSION_ALGORITHMS");

    /**
     * Set the maximum number of bytes that is allowed during the handshake for certificate chain.
     */
    public static final OpenSslContextOption<Integer> MAX_CERTIFICATE_LIST_BYTES =
            new OpenSslContextOption<Integer>("MAX_CERTIFICATE_LIST_BYTES");

    /**
     * Set the groups that should be used. This will override curves set with {@code -Djdk.tls.namedGroups}.
     * <p>
     * See <a href="https://docs.openssl.org/master/man3/SSL_CTX_set1_groups_list/#description">
     *     SSL_CTX_set1_groups_list</a>.
     */
    public static final OpenSslContextOption<String[]> GROUPS = new OpenSslContextOption<String[]>("GROUPS");

    /**
     * Set the desired length of the Diffie-Hellman ephemeral session keys.
     * This will override the key length set with {@code -Djdk.tls.ephemeralDHKeySize}.
     * <p>
     * The only supported values are {@code 512}, {@code 1024}, {@code 2048}, and {@code 4096}.
     * <p>
     * See <a href="https://docs.openssl.org/1.0.2/man3/SSL_CTX_set_tmp_dh_callback/">SSL_CTX_set_tmp_dh_callback</a>.
     */
    public static final OpenSslContextOption<Integer> TMP_DH_KEYLENGTH =
            new OpenSslContextOption<Integer>("TMP_DH_KEYLENGTH");
}
