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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdkDisabledAlgorithmsTest {

    @Test
    void nullDisablesNothing() {
        JdkDisabledAlgorithms algorithms = new JdkDisabledAlgorithms(null);
        assertThat(algorithms.isProtocolDisabled("TLSv1")).isFalse();
        assertThat(algorithms.isCipherSuiteDisabled("TLS_RSA_WITH_RC4_128_SHA")).isFalse();
    }

    @Test
    void protocolsAreMatchedCaseInsensitively() {
        JdkDisabledAlgorithms algorithms = new JdkDisabledAlgorithms(" SSLv3 , tlsv1,TLSv1.1 ");
        assertThat(algorithms.isProtocolDisabled("SSLv3")).isTrue();
        assertThat(algorithms.isProtocolDisabled("TLSv1")).isTrue();
        assertThat(algorithms.isProtocolDisabled("TLSv1.1")).isTrue();
        assertThat(algorithms.isProtocolDisabled("TLSv1.2")).isFalse();
        assertThat(algorithms.isProtocolDisabled("TLSv1.3")).isFalse();
    }

    @Test
    void cipherSuitesAreMatchedByComponent() {
        JdkDisabledAlgorithms algorithms = new JdkDisabledAlgorithms("RC4, DES, 3DES_EDE_CBC, anon, NULL, ECDH");
        assertThat(algorithms.isCipherSuiteDisabled("TLS_RSA_WITH_RC4_128_SHA")).isTrue();
        assertThat(algorithms.isCipherSuiteDisabled("TLS_RSA_WITH_DES_CBC_SHA")).isTrue();
        assertThat(algorithms.isCipherSuiteDisabled("TLS_RSA_WITH_3DES_EDE_CBC_SHA")).isTrue();
        assertThat(algorithms.isCipherSuiteDisabled("TLS_DH_anon_WITH_AES_128_CBC_SHA")).isTrue();
        assertThat(algorithms.isCipherSuiteDisabled("TLS_RSA_WITH_NULL_SHA")).isTrue();
        assertThat(algorithms.isCipherSuiteDisabled("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA")).isTrue();

        // Must not match on parts of a component.
        assertThat(algorithms.isCipherSuiteDisabled("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")).isFalse();
        assertThat(algorithms.isCipherSuiteDisabled("TLS_AES_128_GCM_SHA256")).isFalse();
        assertThat(algorithms.isCipherSuiteDisabled("TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256")).isFalse();
    }

    @Test
    void constraintsAndDirectivesAreIgnored() {
        JdkDisabledAlgorithms algorithms = new JdkDisabledAlgorithms(
                "include jdk.disabled.namedCurves, RSA keySize < 1024, SHA1 usage TLSServer, , TLSv1");
        assertThat(algorithms.isProtocolDisabled("TLSv1")).isTrue();
        assertThat(algorithms.isProtocolDisabled("RSA")).isFalse();
        assertThat(algorithms.isProtocolDisabled("include")).isFalse();
        assertThat(algorithms.isCipherSuiteDisabled("TLS_RSA_WITH_AES_128_CBC_SHA")).isFalse();
    }

    @Test
    void cipherSuiteWildcardsAreSupported() {
        JdkDisabledAlgorithms algorithms = new JdkDisabledAlgorithms("TLS_RSA_*, tls_ecdh_*_with_aes_*_cbc_sha, *");
        assertThat(algorithms.isCipherSuiteDisabled("TLS_RSA_WITH_AES_128_CBC_SHA")).isTrue();
        assertThat(algorithms.isCipherSuiteDisabled("TLS_ECDH_RSA_WITH_AES_256_CBC_SHA")).isTrue();
        assertThat(algorithms.isCipherSuiteDisabled("TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384")).isFalse();
        assertThat(algorithms.isCipherSuiteDisabled("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")).isFalse();
        // Only cipher suites are allowed to use wildcards.
        assertThat(algorithms.isProtocolDisabled("TLSv1.2")).isFalse();
    }

    @Test
    void jdkAlgorithmNamesAreSupported() {
        JdkDisabledAlgorithms sha1 = new JdkDisabledAlgorithms("SHA1");
        assertThat(sha1.isCipherSuiteDisabled("TLS_RSA_WITH_AES_128_CBC_SHA")).isTrue();
        assertThat(sha1.isCipherSuiteDisabled("TLS_RSA_WITH_AES_128_CBC_SHA256")).isFalse();
        assertThat(sha1.isCipherSuiteDisabled("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")).isFalse();

        JdkDisabledAlgorithms hmacSha256 = new JdkDisabledAlgorithms("HmacSHA256");
        assertThat(hmacSha256.isCipherSuiteDisabled("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")).isTrue();
        assertThat(hmacSha256.isCipherSuiteDisabled("TLS_AES_256_GCM_SHA384")).isFalse();

        JdkDisabledAlgorithms dh = new JdkDisabledAlgorithms("DH");
        assertThat(dh.isCipherSuiteDisabled("TLS_DHE_RSA_WITH_AES_128_CBC_SHA")).isTrue();
        assertThat(dh.isCipherSuiteDisabled("TLS_DH_anon_WITH_AES_128_CBC_SHA")).isTrue();
        assertThat(dh.isCipherSuiteDisabled("TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")).isFalse();
        assertThat(dh.isCipherSuiteDisabled("TLS_ECDH_RSA_WITH_AES_128_CBC_SHA")).isFalse();

        JdkDisabledAlgorithms desede = new JdkDisabledAlgorithms("DESede");
        assertThat(desede.isCipherSuiteDisabled("TLS_RSA_WITH_3DES_EDE_CBC_SHA")).isTrue();
    }

    @Test
    void openSslCipherSuiteNamesAreSupported() {
        JdkDisabledAlgorithms algorithms = new JdkDisabledAlgorithms("TLS_RSA_WITH_AES_128_CBC_SHA");
        assertThat(algorithms.isCipherSuiteDisabled("AES128-SHA")).isTrue();
        assertThat(algorithms.isCipherSuiteDisabled("AES256-SHA")).isFalse();
    }
}
