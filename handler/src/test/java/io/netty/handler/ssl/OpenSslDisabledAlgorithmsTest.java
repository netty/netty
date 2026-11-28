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

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.internal.tcnative.SSL;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.ResourcesUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.io.File;
import java.security.Security;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests that the {@code jdk.tls.disabledAlgorithms} security property is respected by the OpenSSL implementation.
 * <p>
 * The security property is global state, so this test must not run concurrently with others.
 */
@Isolated
class OpenSslDisabledAlgorithmsTest {

    private static final String PROPERTY = "jdk.tls.disabledAlgorithms";

    private String originalValue;

    @BeforeAll
    static void setUpClass() throws Exception {
        OpenSsl.ensureAvailability();
        // Let the JDK initialize its own (cached) view of the property before we change it, so we do not leak the
        // changes made by this test into tests that use the JDK implementation.
        SSLContext.getDefault().createSSLEngine().getEnabledProtocols();
    }

    @BeforeEach
    void saveProperty() {
        originalValue = Security.getProperty(PROPERTY);
    }

    @AfterEach
    void restoreProperty() {
        Security.setProperty(PROPERTY, originalValue == null ? "" : originalValue);
    }

    private static SslContextBuilder newServerBuilder() {
        File keyFile = ResourcesUtil.getFile(OpenSslDisabledAlgorithmsTest.class, "test_unencrypted.pem");
        File crtFile = ResourcesUtil.getFile(OpenSslDisabledAlgorithmsTest.class, "test.crt");
        return SslContextBuilder.forServer(crtFile, keyFile).sslProvider(SslProvider.OPENSSL);
    }

    @Test
    void protocolsDisabledViaSecurityPropertyAreNotEnabled() throws Exception {
        assumeTrue(OpenSsl.isProtocolSupported(SslProtocols.TLS_v1));
        Security.setProperty(PROPERTY, "SSLv3, TLSv1");
        SslContext context = newServerBuilder().protocols(SslProtocols.TLS_v1, SslProtocols.TLS_v1_2).build();
        SSLEngine engine = null;
        try {
            engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
            assertThat(engine.getEnabledProtocols()).containsExactly(SslProtocols.SSL_v2_HELLO, SslProtocols.TLS_v1_2);

            engine.setEnabledProtocols(new String[] { SslProtocols.TLS_v1, SslProtocols.TLS_v1_2 });
            assertThat(engine.getEnabledProtocols()).containsExactly(SslProtocols.SSL_v2_HELLO, SslProtocols.TLS_v1_2);
        } finally {
            ReferenceCountUtil.release(engine);
            ReferenceCountUtil.release(context);
        }
    }

    @Test
    void protocolsNotDisabledViaSecurityPropertyAreEnabled() throws Exception {
        assumeTrue(OpenSsl.isProtocolSupported(SslProtocols.TLS_v1));
        Security.setProperty(PROPERTY, "SSLv3");
        SslContext context = newServerBuilder().protocols(SslProtocols.TLS_v1, SslProtocols.TLS_v1_2).build();
        SSLEngine engine = null;
        try {
            engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
            assertThat(engine.getEnabledProtocols())
                    .containsExactly(SslProtocols.SSL_v2_HELLO, SslProtocols.TLS_v1, SslProtocols.TLS_v1_2);
        } finally {
            ReferenceCountUtil.release(engine);
            ReferenceCountUtil.release(context);
        }
    }

    @Test
    void changeOfSecurityPropertyIsPickedUpByNewContexts() throws Exception {
        assumeTrue(OpenSsl.isProtocolSupported(SslProtocols.TLS_v1));
        Security.setProperty(PROPERTY, "");
        SslContext first = newServerBuilder().protocols(SslProtocols.TLS_v1, SslProtocols.TLS_v1_2).build();
        Security.setProperty(PROPERTY, "TLSv1");
        SslContext second = newServerBuilder().protocols(SslProtocols.TLS_v1, SslProtocols.TLS_v1_2).build();
        SSLEngine firstEngine = null;
        SSLEngine secondEngine = null;
        try {
            firstEngine = first.newEngine(UnpooledByteBufAllocator.DEFAULT);
            secondEngine = second.newEngine(UnpooledByteBufAllocator.DEFAULT);
            assertThat(firstEngine.getEnabledProtocols())
                    .containsExactly(SslProtocols.SSL_v2_HELLO, SslProtocols.TLS_v1, SslProtocols.TLS_v1_2);
            assertThat(secondEngine.getEnabledProtocols())
                    .containsExactly(SslProtocols.SSL_v2_HELLO, SslProtocols.TLS_v1_2);
        } finally {
            ReferenceCountUtil.release(firstEngine);
            ReferenceCountUtil.release(secondEngine);
            ReferenceCountUtil.release(first);
            ReferenceCountUtil.release(second);
        }
    }

    @Test
    void cipherSuitesDisabledViaSecurityPropertyAreRemoved() throws Exception {
        String disabled = "TLS_RSA_WITH_AES_128_CBC_SHA";
        String enabled = "TLS_RSA_WITH_AES_256_CBC_SHA";
        assumeTrue(OpenSsl.isCipherSuiteAvailable(disabled) && OpenSsl.isCipherSuiteAvailable(enabled));
        Security.setProperty(PROPERTY, disabled);
        SslContext context = newServerBuilder()
                .protocols(SslProtocols.TLS_v1_2)
                .ciphers(Arrays.asList(disabled, enabled))
                .build();
        try {
            assertThat(context.cipherSuites()).containsExactly(enabled);
        } finally {
            ReferenceCountUtil.release(context);
        }
    }

    @Test
    void contextCreationFailsIfAllCipherSuitesAreDisabledViaSecurityProperty() {
        String cipher = "TLS_RSA_WITH_AES_128_CBC_SHA";
        assumeTrue(OpenSsl.isCipherSuiteAvailable(cipher));
        Security.setProperty(PROPERTY, cipher);
        SslContextBuilder builder = newServerBuilder()
                .protocols(SslProtocols.TLS_v1_2)
                .ciphers(Collections.singletonList(cipher));
        // Must not fall back to the OpenSSL defaults, which would silently enable the disabled cipher suite.
        assertThatThrownBy(builder::build).isInstanceOf(SSLException.class).hasMessageContaining(PROPERTY);
    }

    @Test
    void cipherSuiteWildcardPatternsAreRespected() throws Exception {
        Security.setProperty(PROPERTY, "TLS_RSA_*");
        SslContext context = newServerBuilder().build();
        try {
            assertThat(context.cipherSuites()).noneMatch(suite -> suite.startsWith("TLS_RSA_"));
        } finally {
            ReferenceCountUtil.release(context);
        }
    }

    @Test
    void cipherSuitesDisabledViaJdkAlgorithmNamesAreRemoved() throws Exception {
        Security.setProperty(PROPERTY, "SHA1");
        SslContext context = newServerBuilder().build();
        try {
            assertThat(context.cipherSuites()).noneMatch(suite -> suite.endsWith("_SHA"));
        } finally {
            ReferenceCountUtil.release(context);
        }
    }

    @Test
    void cipherSuitesInOpenSslFormatDisabledViaSecurityPropertyAreRemoved() throws Exception {
        String disabled = "TLS_RSA_WITH_AES_128_CBC_SHA";
        String enabled = "TLS_RSA_WITH_AES_256_CBC_SHA";
        assumeTrue(OpenSsl.isCipherSuiteAvailable(disabled) && OpenSsl.isCipherSuiteAvailable(enabled));
        Security.setProperty(PROPERTY, disabled);
        SslContext context = newServerBuilder()
                .protocols(SslProtocols.TLS_v1_2)
                .ciphers(Arrays.asList("AES128-SHA", "AES256-SHA"))
                .build();
        SSLEngine engine = null;
        try {
            engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
            assertThat(engine.getEnabledCipherSuites()).contains(enabled).doesNotContain(disabled);
        } finally {
            ReferenceCountUtil.release(engine);
            ReferenceCountUtil.release(context);
        }
    }

    @Test
    void cipherSuitesDisabledViaSecurityPropertyAreNotEnabledOnTheEngine() throws Exception {
        String disabled = "TLS_RSA_WITH_AES_128_CBC_SHA";
        String enabled = "TLS_RSA_WITH_AES_256_CBC_SHA";
        assumeTrue(OpenSsl.isCipherSuiteAvailable(disabled) && OpenSsl.isCipherSuiteAvailable(enabled));
        Security.setProperty(PROPERTY, disabled);
        SslContext context = newServerBuilder().protocols(SslProtocols.TLS_v1_2).build();
        SSLEngine engine = null;
        try {
            engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
            engine.setEnabledCipherSuites(new String[] { disabled, enabled });
            assertThat(engine.getEnabledCipherSuites()).contains(enabled).doesNotContain(disabled);
        } finally {
            ReferenceCountUtil.release(engine);
            ReferenceCountUtil.release(context);
        }
    }

    @Test
    void protocolDisabledInsideTheEnabledRangeIsNotEnabled() throws Exception {
        assumeTrue(OpenSsl.isProtocolSupported(SslProtocols.TLS_v1) &&
                OpenSsl.isProtocolSupported(SslProtocols.TLS_v1_1));
        Security.setProperty(PROPERTY, SslProtocols.TLS_v1_1);
        SslContext context = newServerBuilder()
                .protocols(SslProtocols.TLS_v1, SslProtocols.TLS_v1_1, SslProtocols.TLS_v1_2)
                .build();
        ReferenceCountedOpenSslEngine engine = null;
        try {
            engine = (ReferenceCountedOpenSslEngine) context.newEngine(UnpooledByteBufAllocator.DEFAULT);
            int options = SSL.getOptions(engine.sslPointer());
            assertThat(options & SSL.SSL_OP_NO_TLSv1_1).as("SSL_OP_NO_TLSv1_1").isEqualTo(SSL.SSL_OP_NO_TLSv1_1);
            // The range must stay contiguous, so everything below the disabled protocol is disabled as well.
            assertThat(options & SSL.SSL_OP_NO_TLSv1).as("SSL_OP_NO_TLSv1").isEqualTo(SSL.SSL_OP_NO_TLSv1);
            assertThat(options & SSL.SSL_OP_NO_TLSv1_2).as("SSL_OP_NO_TLSv1_2").isZero();
        } finally {
            ReferenceCountUtil.release(engine);
            ReferenceCountUtil.release(context);
        }
    }

    @Test
    void settingOnlyDisabledCipherSuitesOnTheEngineFails() throws Exception {
        String disabled = "TLS_RSA_WITH_AES_128_CBC_SHA";
        assumeTrue(OpenSsl.isCipherSuiteAvailable(disabled));
        Security.setProperty(PROPERTY, disabled);
        SslContext context = newServerBuilder().protocols(SslProtocols.TLS_v1_2).build();
        SSLEngine engine = null;
        try {
            engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
            SSLEngine sslEngine = engine;
            assertThatThrownBy(() -> sslEngine.setEnabledCipherSuites(new String[] { disabled }))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(PROPERTY);
        } finally {
            ReferenceCountUtil.release(engine);
            ReferenceCountUtil.release(context);
        }
    }

    @Test
    void protocolsAreNotEnabledIfOnlyTlsv13CipherSuitesAreLeft() throws Exception {
        String disabled = "TLS_RSA_WITH_AES_128_CBC_SHA";
        assumeTrue(OpenSsl.isTlsv13Supported() && OpenSsl.isCipherSuiteAvailable(disabled));
        Security.setProperty(PROPERTY, disabled);
        SslContext context = newServerBuilder()
                .protocols(SslProtocols.TLS_v1_2, SslProtocols.TLS_v1_3)
                .ciphers(Arrays.asList(disabled, "TLS_AES_128_GCM_SHA256"))
                .build();
        ReferenceCountedOpenSslEngine engine = null;
        try {
            engine = (ReferenceCountedOpenSslEngine) context.newEngine(UnpooledByteBufAllocator.DEFAULT);
            assertThat(engine.getEnabledProtocols()).doesNotContain(SslProtocols.TLS_v1_2);
            assertThat(SSL.getOptions(engine.sslPointer()) & SSL.SSL_OP_NO_TLSv1_2).isNotZero();
        } finally {
            ReferenceCountUtil.release(engine);
            ReferenceCountUtil.release(context);
        }
    }

    @Test
    void tlsv12IsNotEnabledIfOnlyTlsv13CipherSuitesAreConfigured() throws Exception {
        assumeTrue(OpenSsl.isTlsv13Supported());
        Security.setProperty(PROPERTY, "TLS_RSA_*");
        SslContext context = newServerBuilder()
                .protocols(SslProtocols.TLS_v1_2, SslProtocols.TLS_v1_3)
                .ciphers(Collections.singletonList("TLS_AES_128_GCM_SHA256"))
                .build();
        ReferenceCountedOpenSslEngine engine = null;
        try {
            engine = (ReferenceCountedOpenSslEngine) context.newEngine(UnpooledByteBufAllocator.DEFAULT);
            assertThat(engine.getEnabledProtocols()).doesNotContain(SslProtocols.TLS_v1_2);
            assertThat(SSL.getOptions(engine.sslPointer()) & SSL.SSL_OP_NO_TLSv1_2).isNotZero();
        } finally {
            ReferenceCountUtil.release(engine);
            ReferenceCountUtil.release(context);
        }
    }

    @Test
    void emptyCipherSuitesAreFilteredByTheSecurityProperty() throws Exception {
        Security.setProperty(PROPERTY, "TLS_RSA_*");
        // An empty list would use the native defaults, which are not aware of the security property.
        SslContext context = newServerBuilder().ciphers(Collections.<String>emptyList()).build();
        try {
            assertThat(context.cipherSuites()).isNotEmpty().noneMatch(suite -> suite.startsWith("TLS_RSA_"));
        } finally {
            ReferenceCountUtil.release(context);
        }
    }
}
