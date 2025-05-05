/*
 * Copyright 2019 The Netty Project
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

import java.util.function.BiConsumer;

/**
 * Allows to customize private key signing / decrypt (when using RSA).
 */
interface BoringSSLPrivateKeyMethod {
    int SSL_SIGN_RSA_PKCS1_SHA1 = BoringSSLNativeStaticallyReferencedJniMethods.ssl_sign_rsa_pkcs_sha1();
    int SSL_SIGN_RSA_PKCS1_SHA256 = BoringSSLNativeStaticallyReferencedJniMethods.ssl_sign_rsa_pkcs_sha256();
    int SSL_SIGN_RSA_PKCS1_SHA384 = BoringSSLNativeStaticallyReferencedJniMethods.ssl_sign_rsa_pkcs_sha384();
    int SSL_SIGN_RSA_PKCS1_SHA512 = BoringSSLNativeStaticallyReferencedJniMethods.ssl_sign_rsa_pkcs_sha512();
    int SSL_SIGN_ECDSA_SHA1 = BoringSSLNativeStaticallyReferencedJniMethods.ssl_sign_ecdsa_pkcs_sha1();
    int SSL_SIGN_ECDSA_SECP256R1_SHA256 =
            BoringSSLNativeStaticallyReferencedJniMethods.ssl_sign_ecdsa_secp256r1_sha256();
    int SSL_SIGN_ECDSA_SECP384R1_SHA384 =
            BoringSSLNativeStaticallyReferencedJniMethods.ssl_sign_ecdsa_secp384r1_sha384();
    int SSL_SIGN_ECDSA_SECP521R1_SHA512 =
            BoringSSLNativeStaticallyReferencedJniMethods.ssl_sign_ecdsa_secp521r1_sha512();
    int SSL_SIGN_RSA_PSS_RSAE_SHA256 = BoringSSLNativeStaticallyReferencedJniMethods.ssl_sign_rsa_pss_rsae_sha256();
    int SSL_SIGN_RSA_PSS_RSAE_SHA384 = BoringSSLNativeStaticallyReferencedJniMethods.ssl_sign_rsa_pss_rsae_sha384();
    int SSL_SIGN_RSA_PSS_RSAE_SHA512 = BoringSSLNativeStaticallyReferencedJniMethods.ssl_sign_rsa_pss_rsae_sha512();
    int SSL_SIGN_ED25519 = BoringSSLNativeStaticallyReferencedJniMethods.ssl_sign_ed25519();
    int SSL_SIGN_RSA_PKCS1_MD5_SHA1 = BoringSSLNativeStaticallyReferencedJniMethods.ssl_sign_rsa_pkcs1_md5_sha1();

    /**
     * Sign the input with given EC key and returns the signed bytes.
     *
     * @param ssl the SSL instance
     * @param signatureAlgorithm the algorithm to use for signing
     * @param input the input itself
     * @param callback the callback provides the signed bytes and an error if such occurs while signing
     */
    void sign(long ssl, int signatureAlgorithm, byte[] input, BiConsumer<byte[], Throwable> callback);

    /**
     * Decrypts the input with the given RSA key and returns the decrypted bytes.
     *
     * @param ssl the SSL instance
     * @param input the input which should be decrypted
     * @param callback the callback provides the decrypted bytes and an error if such occurs while decrypting
     */
    void decrypt(long ssl, byte[] input, BiConsumer<byte[], Throwable> callback);
}
