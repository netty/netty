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
 * Cipher suites
 */
public final class Ciphers {

    /**
     * TLS_AES_256_GCM_SHA384
     */
    public static final String TLS_AES_256_GCM_SHA384 = "TLS_AES_256_GCM_SHA384";

    /**
     * TLS_CHACHA20_POLY1305_SHA256
     */
    public static final String TLS_CHACHA20_POLY1305_SHA256 = "TLS_CHACHA20_POLY1305_SHA256";

    /**
     * TLS_AES_128_GCM_SHA256
     */
    public static final String TLS_AES_128_GCM_SHA256 = "TLS_AES_128_GCM_SHA256";

    /**
     * TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
     */
    public static final String TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384 = "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384";

    /**
     * TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
     */
    public static final String TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384 = "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384";

    /**
     * TLS_DHE_DSS_WITH_AES_256_GCM_SHA384
     */
    public static final String TLS_DHE_DSS_WITH_AES_256_GCM_SHA384 = "TLS_DHE_DSS_WITH_AES_256_GCM_SHA384";

    /**
     * TLS_DHE_RSA_WITH_AES_256_GCM_SHA384
     */
    public static final String TLS_DHE_RSA_WITH_AES_256_GCM_SHA384 = "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384";

    /**
     * TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256
     */
    public static final String TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256 =
            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256";

    /**
     * TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256
     */
    public static final String TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256 =
            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256";

    /**
     * TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256
     */
    public static final String TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256 = "TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256";

    /**
     * TLS_ECDHE_ECDSA_WITH_AES_256_CBC_CCM8
     */
    public static final String TLS_ECDHE_ECDSA_WITH_AES_256_CBC_CCM8 = "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_CCM8";

    /**
     * TLS_ECDHE_ECDSA_WITH_AES_256_CBC_CCM
     */
    public static final String TLS_ECDHE_ECDSA_WITH_AES_256_CBC_CCM = "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_CCM";

    /**
     * TLS_DHE_RSA_WITH_AES_256_CBC_CCM8
     */
    public static final String TLS_DHE_RSA_WITH_AES_256_CBC_CCM8 = "TLS_DHE_RSA_WITH_AES_256_CBC_CCM8";

    /**
     * TLS_DHE_RSA_WITH_AES_256_CBC_CCM
     */
    public static final String TLS_DHE_RSA_WITH_AES_256_CBC_CCM = "TLS_DHE_RSA_WITH_AES_256_CBC_CCM";

    /**
     * TLS_ECDHE_ECDSA_WITH_ARIA256_GCM_SHA384
     */
    public static final String TLS_ECDHE_ECDSA_WITH_ARIA256_GCM_SHA384 = "TLS_ECDHE_ECDSA_WITH_ARIA256_GCM_SHA384";

    /**
     * TLS_RSA_WITH_ECDHE_ARIA256_GCM_SHA384
     */
    public static final String TLS_RSA_WITH_ECDHE_ARIA256_GCM_SHA384 = "TLS_RSA_WITH_ECDHE_ARIA256_GCM_SHA384";

    /**
     * TLS_DHE_DSS_WITH_ARIA256_GCM_SHA384
     */
    public static final String TLS_DHE_DSS_WITH_ARIA256_GCM_SHA384 = "TLS_DHE_DSS_WITH_ARIA256_GCM_SHA384";

    /**
     * TLS_DHE_RSA_WITH_ARIA256_GCM_SHA384
     */
    public static final String TLS_DHE_RSA_WITH_ARIA256_GCM_SHA384 = "TLS_DHE_RSA_WITH_ARIA256_GCM_SHA384";

    /**
     * TLS_DH_anon_WITH_AES_256_GCM_SHA384
     */
    public static final String TLS_DH_anon_WITH_AES_256_GCM_SHA384 = "TLS_DH_anon_WITH_AES_256_GCM_SHA384";

    /**
     * TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
     */
    public static final String TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256 = "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256";

    /**
     * TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
     */
    public static final String TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256 = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256";

    /**
     * TLS_DHE_DSS_WITH_AES_128_GCM_SHA256
     */
    public static final String TLS_DHE_DSS_WITH_AES_128_GCM_SHA256 = "TLS_DHE_DSS_WITH_AES_128_GCM_SHA256";

    /**
     * TLS_DHE_RSA_WITH_AES_128_GCM_SHA256
     */
    public static final String TLS_DHE_RSA_WITH_AES_128_GCM_SHA256 = "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256";

    /**
     * TLS_ECDHE_ECDSA_WITH_AES_128_CBC_CCM8
     */
    public static final String TLS_ECDHE_ECDSA_WITH_AES_128_CBC_CCM8 = "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_CCM8";

    /**
     * TLS_ECDHE_ECDSA_WITH_AES_128_CBC_CCM
     */
    public static final String TLS_ECDHE_ECDSA_WITH_AES_128_CBC_CCM = "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_CCM";

    /**
     * TLS_DHE_RSA_WITH_AES_128_CBC_CCM8
     */
    public static final String TLS_DHE_RSA_WITH_AES_128_CBC_CCM8 = "TLS_DHE_RSA_WITH_AES_128_CBC_CCM8";

    /**
     * TLS_DHE_RSA_WITH_AES_128_CBC_CCM
     */
    public static final String TLS_DHE_RSA_WITH_AES_128_CBC_CCM = "TLS_DHE_RSA_WITH_AES_128_CBC_CCM";

    /**
     * TLS_ECDHE_ECDSA_WITH_ARIA128_GCM_SHA256
     */
    public static final String TLS_ECDHE_ECDSA_WITH_ARIA128_GCM_SHA256 = "TLS_ECDHE_ECDSA_WITH_ARIA128_GCM_SHA256";

    /**
     * TLS_RSA_WITH_ECDHE_ARIA128_GCM_SHA256
     */
    public static final String TLS_RSA_WITH_ECDHE_ARIA128_GCM_SHA256 = "TLS_RSA_WITH_ECDHE_ARIA128_GCM_SHA256";

    /**
     * TLS_DHE_DSS_WITH_ARIA128_GCM_SHA256
     */
    public static final String TLS_DHE_DSS_WITH_ARIA128_GCM_SHA256 = "TLS_DHE_DSS_WITH_ARIA128_GCM_SHA256";

    /**
     * TLS_DHE_RSA_WITH_ARIA128_GCM_SHA256
     */
    public static final String TLS_DHE_RSA_WITH_ARIA128_GCM_SHA256 = "TLS_DHE_RSA_WITH_ARIA128_GCM_SHA256";

    /**
     * TLS_DH_anon_WITH_AES_128_GCM_SHA256
     */
    public static final String TLS_DH_anon_WITH_AES_128_GCM_SHA256 = "TLS_DH_anon_WITH_AES_128_GCM_SHA256";

    /**
     * TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384
     */
    public static final String TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384 = "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384";

    /**
     * TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384
     */
    public static final String TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384 = "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384";

    /**
     * TLS_DHE_RSA_WITH_AES_256_CBC_SHA256
     */
    public static final String TLS_DHE_RSA_WITH_AES_256_CBC_SHA256 = "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256";

    /**
     * TLS_DHE_DSS_WITH_AES_256_CBC_SHA256
     */
    public static final String TLS_DHE_DSS_WITH_AES_256_CBC_SHA256 = "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256";

    /**
     * TLS_ECDHE_ECDSA_WITH_CAMELLIA256_SHA384
     */
    public static final String TLS_ECDHE_ECDSA_WITH_CAMELLIA256_SHA384 = "TLS_ECDHE_ECDSA_WITH_CAMELLIA256_SHA384";

    /**
     * TLS_ECDHE_RSA_WITH_CAMELLIA256_SHA384
     */
    public static final String TLS_ECDHE_RSA_WITH_CAMELLIA256_SHA384 = "TLS_ECDHE_RSA_WITH_CAMELLIA256_SHA384";

    /**
     * TLS_DHE_RSA_WITH_CAMELLIA256_SHA256
     */
    public static final String TLS_DHE_RSA_WITH_CAMELLIA256_SHA256 = "TLS_DHE_RSA_WITH_CAMELLIA256_SHA256";

    /**
     * TLS_DHE_DSS_WITH_CAMELLIA256_SHA256
     */
    public static final String TLS_DHE_DSS_WITH_CAMELLIA256_SHA256 = "TLS_DHE_DSS_WITH_CAMELLIA256_SHA256";

    /**
     * TLS_DH_anon_WITH_AES_256_CBC_SHA256
     */
    public static final String TLS_DH_anon_WITH_AES_256_CBC_SHA256 = "TLS_DH_anon_WITH_AES_256_CBC_SHA256";

    /**
     * TLS_DH_anon_WITH_CAMELLIA256_SHA256
     */
    public static final String TLS_DH_anon_WITH_CAMELLIA256_SHA256 = "TLS_DH_anon_WITH_CAMELLIA256_SHA256";

    /**
     * TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256
     */
    public static final String TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256 = "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256";

    /**
     * TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256
     */
    public static final String TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256 = "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256";

    /**
     * TLS_DHE_RSA_WITH_AES_128_CBC_SHA256
     */
    public static final String TLS_DHE_RSA_WITH_AES_128_CBC_SHA256 = "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256";

    /**
     * TLS_DHE_DSS_WITH_AES_128_CBC_SHA256
     */
    public static final String TLS_DHE_DSS_WITH_AES_128_CBC_SHA256 = "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256";

    /**
     * TLS_ECDHE_ECDSA_WITH_CAMELLIA128_SHA256
     */
    public static final String TLS_ECDHE_ECDSA_WITH_CAMELLIA128_SHA256 = "TLS_ECDHE_ECDSA_WITH_CAMELLIA128_SHA256";

    /**
     * TLS_ECDHE_RSA_WITH_CAMELLIA128_SHA256
     */
    public static final String TLS_ECDHE_RSA_WITH_CAMELLIA128_SHA256 = "TLS_ECDHE_RSA_WITH_CAMELLIA128_SHA256";

    /**
     * TLS_DHE_RSA_WITH_CAMELLIA128_SHA256
     */
    public static final String TLS_DHE_RSA_WITH_CAMELLIA128_SHA256 = "TLS_DHE_RSA_WITH_CAMELLIA128_SHA256";

    /**
     * TLS_DHE_DSS_WITH_CAMELLIA128_SHA256
     */
    public static final String TLS_DHE_DSS_WITH_CAMELLIA128_SHA256 = "TLS_DHE_DSS_WITH_CAMELLIA128_SHA256";

    /**
     * TLS_DH_anon_WITH_AES_128_CBC_SHA256
     */
    public static final String TLS_DH_anon_WITH_AES_128_CBC_SHA256 = "TLS_DH_anon_WITH_AES_128_CBC_SHA256";

    /**
     * TLS_DH_anon_WITH_CAMELLIA128_SHA256
     */
    public static final String TLS_DH_anon_WITH_CAMELLIA128_SHA256 = "TLS_DH_anon_WITH_CAMELLIA128_SHA256";

    /**
     * TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA = "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA";

    /**
     * TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA = "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA";

    /**
     * TLS_DHE_RSA_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_DHE_RSA_WITH_AES_256_CBC_SHA = "TLS_DHE_RSA_WITH_AES_256_CBC_SHA";

    /**
     * TLS_DHE_DSS_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_DHE_DSS_WITH_AES_256_CBC_SHA = "TLS_DHE_DSS_WITH_AES_256_CBC_SHA";

    /**
     * TLS_DHE_RSA_WITH_CAMELLIA256_SHA
     */
    public static final String TLS_DHE_RSA_WITH_CAMELLIA256_SHA = "TLS_DHE_RSA_WITH_CAMELLIA256_SHA";

    /**
     * TLS_DHE_DSS_WITH_CAMELLIA256_SHA
     */
    public static final String TLS_DHE_DSS_WITH_CAMELLIA256_SHA = "TLS_DHE_DSS_WITH_CAMELLIA256_SHA";

    /**
     * TLS_ECDH_anon_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_ECDH_anon_WITH_AES_256_CBC_SHA = "TLS_ECDH_anon_WITH_AES_256_CBC_SHA";

    /**
     * TLS_DH_anon_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_DH_anon_WITH_AES_256_CBC_SHA = "TLS_DH_anon_WITH_AES_256_CBC_SHA";

    /**
     * TLS_DH_anon_WITH_CAMELLIA256_SHA
     */
    public static final String TLS_DH_anon_WITH_CAMELLIA256_SHA = "TLS_DH_anon_WITH_CAMELLIA256_SHA";

    /**
     * TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA = "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA";

    /**
     * TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA = "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA";

    /**
     * TLS_DHE_RSA_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_DHE_RSA_WITH_AES_128_CBC_SHA = "TLS_DHE_RSA_WITH_AES_128_CBC_SHA";

    /**
     * TLS_DHE_DSS_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_DHE_DSS_WITH_AES_128_CBC_SHA = "TLS_DHE_DSS_WITH_AES_128_CBC_SHA";

    /**
     * TLS_DHE_RSA_WITH_SEED_SHA
     */
    public static final String TLS_DHE_RSA_WITH_SEED_SHA = "TLS_DHE_RSA_WITH_SEED_SHA";

    /**
     * TLS_DHE_DSS_WITH_SEED_SHA
     */
    public static final String TLS_DHE_DSS_WITH_SEED_SHA = "TLS_DHE_DSS_WITH_SEED_SHA";

    /**
     * TLS_DHE_RSA_WITH_CAMELLIA128_SHA
     */
    public static final String TLS_DHE_RSA_WITH_CAMELLIA128_SHA = "TLS_DHE_RSA_WITH_CAMELLIA128_SHA";

    /**
     * TLS_DHE_DSS_WITH_CAMELLIA128_SHA
     */
    public static final String TLS_DHE_DSS_WITH_CAMELLIA128_SHA = "TLS_DHE_DSS_WITH_CAMELLIA128_SHA";

    /**
     * TLS_ECDH_anon_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_ECDH_anon_WITH_AES_128_CBC_SHA = "TLS_ECDH_anon_WITH_AES_128_CBC_SHA";

    /**
     * TLS_DH_anon_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_DH_anon_WITH_AES_128_CBC_SHA = "TLS_DH_anon_WITH_AES_128_CBC_SHA";

    /**
     * TLS_DH_anon_WITH_SEED_SHA
     */
    public static final String TLS_DH_anon_WITH_SEED_SHA = "TLS_DH_anon_WITH_SEED_SHA";

    /**
     * TLS_DH_anon_WITH_CAMELLIA128_SHA
     */
    public static final String TLS_DH_anon_WITH_CAMELLIA128_SHA = "TLS_DH_anon_WITH_CAMELLIA128_SHA";

    /**
     * TLS_RSA_PSK_WITH_AES_256_GCM_SHA384
     */
    public static final String TLS_RSA_PSK_WITH_AES_256_GCM_SHA384 = "TLS_RSA_PSK_WITH_AES_256_GCM_SHA384";

    /**
     * TLS_DHE_PSK_WITH_AES_256_GCM_SHA384
     */
    public static final String TLS_DHE_PSK_WITH_AES_256_GCM_SHA384 = "TLS_DHE_PSK_WITH_AES_256_GCM_SHA384";

    /**
     * TLS_RSA_PSK_WITH_CHACHA20_POLY1305_SHA256
     */
    public static final String TLS_RSA_PSK_WITH_CHACHA20_POLY1305_SHA256 = "TLS_RSA_PSK_WITH_CHACHA20_POLY1305_SHA256";

    /**
     * TLS_DHE_PSK_WITH_CHACHA20_POLY1305_SHA256
     */
    public static final String TLS_DHE_PSK_WITH_CHACHA20_POLY1305_SHA256 = "TLS_DHE_PSK_WITH_CHACHA20_POLY1305_SHA256";

    /**
     * TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256
     */
    public static final String TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256 =
            "TLS_ECDHE_PSK_WITH_CHACHA20_POLY1305_SHA256";

    /**
     * TLS_DHE_PSK_WITH_AES_256_CBC_CCM8
     */
    public static final String TLS_DHE_PSK_WITH_AES_256_CBC_CCM8 = "TLS_DHE_PSK_WITH_AES_256_CBC_CCM8";

    /**
     * TLS_DHE_PSK_WITH_AES_256_CBC_CCM
     */
    public static final String TLS_DHE_PSK_WITH_AES_256_CBC_CCM = "TLS_DHE_PSK_WITH_AES_256_CBC_CCM";

    /**
     * TLS_RSA_PSK_WITH_ARIA256_GCM_SHA384
     */
    public static final String TLS_RSA_PSK_WITH_ARIA256_GCM_SHA384 = "TLS_RSA_PSK_WITH_ARIA256_GCM_SHA384";

    /**
     * TLS_DHE_PSK_WITH_ARIA256_GCM_SHA384
     */
    public static final String TLS_DHE_PSK_WITH_ARIA256_GCM_SHA384 = "TLS_DHE_PSK_WITH_ARIA256_GCM_SHA384";

    /**
     * TLS_RSA_WITH_AES_256_GCM_SHA384
     */
    public static final String TLS_RSA_WITH_AES_256_GCM_SHA384 = "TLS_RSA_WITH_AES_256_GCM_SHA384";

    /**
     * TLS_RSA_WITH_AES_256_CBC_CCM8
     */
    public static final String TLS_RSA_WITH_AES_256_CBC_CCM8 = "TLS_RSA_WITH_AES_256_CBC_CCM8";

    /**
     * TLS_RSA_WITH_AES_256_CBC_CCM
     */
    public static final String TLS_RSA_WITH_AES_256_CBC_CCM = "TLS_RSA_WITH_AES_256_CBC_CCM";

    /**
     * TLS_RSA_WITH_ARIA256_GCM_SHA384
     */
    public static final String TLS_RSA_WITH_ARIA256_GCM_SHA384 = "TLS_RSA_WITH_ARIA256_GCM_SHA384";

    /**
     * TLS_PSK_WITH_AES_256_GCM_SHA384
     */
    public static final String TLS_PSK_WITH_AES_256_GCM_SHA384 = "TLS_PSK_WITH_AES_256_GCM_SHA384";

    /**
     * TLS_PSK_WITH_CHACHA20_POLY1305_SHA256
     */
    public static final String TLS_PSK_WITH_CHACHA20_POLY1305_SHA256 = "TLS_PSK_WITH_CHACHA20_POLY1305_SHA256";

    /**
     * TLS_PSK_WITH_AES_256_CBC_CCM8
     */
    public static final String TLS_PSK_WITH_AES_256_CBC_CCM8 = "TLS_PSK_WITH_AES_256_CBC_CCM8";

    /**
     * TLS_PSK_WITH_AES_256_CBC_CCM
     */
    public static final String TLS_PSK_WITH_AES_256_CBC_CCM = "TLS_PSK_WITH_AES_256_CBC_CCM";

    /**
     * TLS_PSK_WITH_ARIA256_GCM_SHA384
     */
    public static final String TLS_PSK_WITH_ARIA256_GCM_SHA384 = "TLS_PSK_WITH_ARIA256_GCM_SHA384";

    /**
     * TLS_RSA_PSK_WITH_AES_128_GCM_SHA256
     */
    public static final String TLS_RSA_PSK_WITH_AES_128_GCM_SHA256 = "TLS_RSA_PSK_WITH_AES_128_GCM_SHA256";

    /**
     * TLS_DHE_PSK_WITH_AES_128_GCM_SHA256
     */
    public static final String TLS_DHE_PSK_WITH_AES_128_GCM_SHA256 = "TLS_DHE_PSK_WITH_AES_128_GCM_SHA256";

    /**
     * TLS_DHE_PSK_WITH_AES_128_CBC_CCM8
     */
    public static final String TLS_DHE_PSK_WITH_AES_128_CBC_CCM8 = "TLS_DHE_PSK_WITH_AES_128_CBC_CCM8";

    /**
     * TLS_DHE_PSK_WITH_AES_128_CBC_CCM
     */
    public static final String TLS_DHE_PSK_WITH_AES_128_CBC_CCM = "TLS_DHE_PSK_WITH_AES_128_CBC_CCM";

    /**
     * TLS_RSA_PSK_WITH_ARIA128_GCM_SHA256
     */
    public static final String TLS_RSA_PSK_WITH_ARIA128_GCM_SHA256 = "TLS_RSA_PSK_WITH_ARIA128_GCM_SHA256";

    /**
     * TLS_DHE_PSK_WITH_ARIA128_GCM_SHA256
     */
    public static final String TLS_DHE_PSK_WITH_ARIA128_GCM_SHA256 = "TLS_DHE_PSK_WITH_ARIA128_GCM_SHA256";

    /**
     * TLS_RSA_WITH_AES_128_GCM_SHA256
     */
    public static final String TLS_RSA_WITH_AES_128_GCM_SHA256 = "TLS_RSA_WITH_AES_128_GCM_SHA256";

    /**
     * TLS_RSA_WITH_AES_128_CBC_CCM8
     */
    public static final String TLS_RSA_WITH_AES_128_CBC_CCM8 = "TLS_RSA_WITH_AES_128_CBC_CCM8";

    /**
     * TLS_RSA_WITH_AES_128_CBC_CCM
     */
    public static final String TLS_RSA_WITH_AES_128_CBC_CCM = "TLS_RSA_WITH_AES_128_CBC_CCM";

    /**
     * TLS_RSA_WITH_ARIA128_GCM_SHA256
     */
    public static final String TLS_RSA_WITH_ARIA128_GCM_SHA256 = "TLS_RSA_WITH_ARIA128_GCM_SHA256";

    /**
     * TLS_PSK_WITH_AES_128_GCM_SHA256
     */
    public static final String TLS_PSK_WITH_AES_128_GCM_SHA256 = "TLS_PSK_WITH_AES_128_GCM_SHA256";

    /**
     * TLS_PSK_WITH_AES_128_CBC_CCM8
     */
    public static final String TLS_PSK_WITH_AES_128_CBC_CCM8 = "TLS_PSK_WITH_AES_128_CBC_CCM8";

    /**
     * TLS_PSK_WITH_AES_128_CBC_CCM
     */
    public static final String TLS_PSK_WITH_AES_128_CBC_CCM = "TLS_PSK_WITH_AES_128_CBC_CCM";

    /**
     * TLS_PSK_WITH_ARIA128_GCM_SHA256
     */
    public static final String TLS_PSK_WITH_ARIA128_GCM_SHA256 = "TLS_PSK_WITH_ARIA128_GCM_SHA256";

    /**
     * TLS_RSA_WITH_AES_256_CBC_SHA256
     */
    public static final String TLS_RSA_WITH_AES_256_CBC_SHA256 = "TLS_RSA_WITH_AES_256_CBC_SHA256";

    /**
     * TLS_RSA_WITH_CAMELLIA256_SHA256
     */
    public static final String TLS_RSA_WITH_CAMELLIA256_SHA256 = "TLS_RSA_WITH_CAMELLIA256_SHA256";

    /**
     * TLS_RSA_WITH_AES_128_CBC_SHA256
     */
    public static final String TLS_RSA_WITH_AES_128_CBC_SHA256 = "TLS_RSA_WITH_AES_128_CBC_SHA256";

    /**
     * TLS_RSA_WITH_CAMELLIA128_SHA256
     */
    public static final String TLS_RSA_WITH_CAMELLIA128_SHA256 = "TLS_RSA_WITH_CAMELLIA128_SHA256";

    /**
     * TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384
     */
    public static final String TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384 = "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384";

    /**
     * TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA = "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA";

    /**
     * TLS_SRP_DSS_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_SRP_DSS_WITH_AES_256_CBC_SHA = "TLS_SRP_DSS_WITH_AES_256_CBC_SHA";

    /**
     * TLS_SRP_RSA_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_SRP_RSA_WITH_AES_256_CBC_SHA = "TLS_SRP_RSA_WITH_AES_256_CBC_SHA";

    /**
     * TLS_SRP_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_SRP_WITH_AES_256_CBC_SHA = "TLS_SRP_WITH_AES_256_CBC_SHA";

    /**
     * TLS_RSA_PSK_WITH_AES_256_CBC_SHA384
     */
    public static final String TLS_RSA_PSK_WITH_AES_256_CBC_SHA384 = "TLS_RSA_PSK_WITH_AES_256_CBC_SHA384";

    /**
     * TLS_DHE_PSK_WITH_AES_256_CBC_SHA384
     */
    public static final String TLS_DHE_PSK_WITH_AES_256_CBC_SHA384 = "TLS_DHE_PSK_WITH_AES_256_CBC_SHA384";

    /**
     * TLS_RSA_PSK_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_RSA_PSK_WITH_AES_256_CBC_SHA = "TLS_RSA_PSK_WITH_AES_256_CBC_SHA";

    /**
     * TLS_DHE_PSK_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_DHE_PSK_WITH_AES_256_CBC_SHA = "TLS_DHE_PSK_WITH_AES_256_CBC_SHA";

    /**
     * TLS_ECDHE_PSK_WITH_CAMELLIA256_SHA384
     */
    public static final String TLS_ECDHE_PSK_WITH_CAMELLIA256_SHA384 = "TLS_ECDHE_PSK_WITH_CAMELLIA256_SHA384";

    /**
     * TLS_RSA_PSK_WITH_CAMELLIA256_SHA384
     */
    public static final String TLS_RSA_PSK_WITH_CAMELLIA256_SHA384 = "TLS_RSA_PSK_WITH_CAMELLIA256_SHA384";

    /**
     * TLS_DHE_PSK_WITH_CAMELLIA256_SHA384
     */
    public static final String TLS_DHE_PSK_WITH_CAMELLIA256_SHA384 = "TLS_DHE_PSK_WITH_CAMELLIA256_SHA384";

    /**
     * TLS_RSA_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_RSA_WITH_AES_256_CBC_SHA = "TLS_RSA_WITH_AES_256_CBC_SHA";

    /**
     * TLS_RSA_WITH_CAMELLIA256_SHA
     */
    public static final String TLS_RSA_WITH_CAMELLIA256_SHA = "TLS_RSA_WITH_CAMELLIA256_SHA";

    /**
     * TLS_PSK_WITH_AES_256_CBC_SHA384
     */
    public static final String TLS_PSK_WITH_AES_256_CBC_SHA384 = "TLS_PSK_WITH_AES_256_CBC_SHA384";

    /**
     * TLS_PSK_WITH_AES_256_CBC_SHA
     */
    public static final String TLS_PSK_WITH_AES_256_CBC_SHA = "TLS_PSK_WITH_AES_256_CBC_SHA";

    /**
     * TLS_PSK_WITH_CAMELLIA256_SHA384
     */
    public static final String TLS_PSK_WITH_CAMELLIA256_SHA384 = "TLS_PSK_WITH_CAMELLIA256_SHA384";

    /**
     * TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256
     */
    public static final String TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256 = "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256";

    /**
     * TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA = "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA";

    /**
     * TLS_SRP_DSS_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_SRP_DSS_WITH_AES_128_CBC_SHA = "TLS_SRP_DSS_WITH_AES_128_CBC_SHA";

    /**
     * TLS_SRP_RSA_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_SRP_RSA_WITH_AES_128_CBC_SHA = "TLS_SRP_RSA_WITH_AES_128_CBC_SHA";

    /**
     * TLS_SRP_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_SRP_WITH_AES_128_CBC_SHA = "TLS_SRP_WITH_AES_128_CBC_SHA";

    /**
     * TLS_RSA_PSK_WITH_AES_128_CBC_SHA256
     */
    public static final String TLS_RSA_PSK_WITH_AES_128_CBC_SHA256 = "TLS_RSA_PSK_WITH_AES_128_CBC_SHA256";

    /**
     * TLS_DHE_PSK_WITH_AES_128_CBC_SHA256
     */
    public static final String TLS_DHE_PSK_WITH_AES_128_CBC_SHA256 = "TLS_DHE_PSK_WITH_AES_128_CBC_SHA256";

    /**
     * TLS_RSA_PSK_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_RSA_PSK_WITH_AES_128_CBC_SHA = "TLS_RSA_PSK_WITH_AES_128_CBC_SHA";

    /**
     * TLS_DHE_PSK_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_DHE_PSK_WITH_AES_128_CBC_SHA = "TLS_DHE_PSK_WITH_AES_128_CBC_SHA";

    /**
     * TLS_ECDHE_PSK_WITH_CAMELLIA128_SHA256
     */
    public static final String TLS_ECDHE_PSK_WITH_CAMELLIA128_SHA256 = "TLS_ECDHE_PSK_WITH_CAMELLIA128_SHA256";

    /**
     * TLS_RSA_PSK_WITH_CAMELLIA128_SHA256
     */
    public static final String TLS_RSA_PSK_WITH_CAMELLIA128_SHA256 = "TLS_RSA_PSK_WITH_CAMELLIA128_SHA256";

    /**
     * TLS_DHE_PSK_WITH_CAMELLIA128_SHA256
     */
    public static final String TLS_DHE_PSK_WITH_CAMELLIA128_SHA256 = "TLS_DHE_PSK_WITH_CAMELLIA128_SHA256";

    /**
     * TLS_RSA_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_RSA_WITH_AES_128_CBC_SHA = "TLS_RSA_WITH_AES_128_CBC_SHA";

    /**
     * TLS_RSA_WITH_SEED_SHA
     */
    public static final String TLS_RSA_WITH_SEED_SHA = "TLS_RSA_WITH_SEED_SHA";

    /**
     * TLS_RSA_WITH_CAMELLIA128_SHA
     */
    public static final String TLS_RSA_WITH_CAMELLIA128_SHA = "TLS_RSA_WITH_CAMELLIA128_SHA";

    /**
     * TLS_RSA_WITH_IDEA_CBC_SHA
     */
    public static final String TLS_RSA_WITH_IDEA_CBC_SHA = "TLS_RSA_WITH_IDEA_CBC_SHA";

    /**
     * TLS_PSK_WITH_AES_128_CBC_SHA256
     */
    public static final String TLS_PSK_WITH_AES_128_CBC_SHA256 = "TLS_PSK_WITH_AES_128_CBC_SHA256";

    /**
     * TLS_PSK_WITH_AES_128_CBC_SHA
     */
    public static final String TLS_PSK_WITH_AES_128_CBC_SHA = "TLS_PSK_WITH_AES_128_CBC_SHA";

    /**
     * TLS_PSK_WITH_CAMELLIA128_SHA256
     */
    public static final String TLS_PSK_WITH_CAMELLIA128_SHA256 = "TLS_PSK_WITH_CAMELLIA128_SHA256";

    private Ciphers() {
        // Prevent outside initialization
    }
}
