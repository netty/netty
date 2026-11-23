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
}
