/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.ssl;

import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.Test;

import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;

import javax.net.ssl.SSLEngine;
import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

public class OpenSslEngineTest extends SSLEngineTest {
    private static final String PREFERRED_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http2";
    private static final String FALLBACK_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http1_1";
    private static final String PROTOCOL_SSL_V2_HELLO = "SSLv2Hello";

    @Test
    public void testNpn() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        ApplicationProtocolConfig apn = acceptingNegotiator(Protocol.NPN,
                PREFERRED_APPLICATION_LEVEL_PROTOCOL);
        setupHandlers(apn);
        runTest(PREFERRED_APPLICATION_LEVEL_PROTOCOL);
    }

    @Test
    public void testAlpn() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        assumeTrue(OpenSsl.isAlpnSupported());
        ApplicationProtocolConfig apn = acceptingNegotiator(Protocol.ALPN,
                PREFERRED_APPLICATION_LEVEL_PROTOCOL);
        setupHandlers(apn);
        runTest(PREFERRED_APPLICATION_LEVEL_PROTOCOL);
    }

    @Test
    public void testAlpnCompatibleProtocolsDifferentClientOrder() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        assumeTrue(OpenSsl.isAlpnSupported());
        ApplicationProtocolConfig clientApn = acceptingNegotiator(Protocol.ALPN,
                FALLBACK_APPLICATION_LEVEL_PROTOCOL, PREFERRED_APPLICATION_LEVEL_PROTOCOL);
        ApplicationProtocolConfig serverApn = acceptingNegotiator(Protocol.ALPN,
                PREFERRED_APPLICATION_LEVEL_PROTOCOL, FALLBACK_APPLICATION_LEVEL_PROTOCOL);
        setupHandlers(serverApn, clientApn);
        assertNull(serverException);
        runTest(PREFERRED_APPLICATION_LEVEL_PROTOCOL);
    }

    @Test
    public void testEnablingAnAlreadyDisabledSslProtocol() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        SSLEngine sslEngine = null;
        try {
            File serverKeyFile = new File(getClass().getResource("test_unencrypted.pem").getFile());
            File serverCrtFile = new File(getClass().getResource("test.crt").getFile());
            SslContext sslContext = SslContextBuilder.forServer(serverCrtFile, serverKeyFile)
               .sslProvider(sslProvider())
               .build();

            sslEngine = sslContext.newEngine(UnpooledByteBufAllocator.DEFAULT);

            // Disable all protocols
            sslEngine.setEnabledProtocols(new String[]{});

            // The only protocol that should be enabled is SSLv2Hello
            String[] enabledProtocols = sslEngine.getEnabledProtocols();
            assertEquals(1, enabledProtocols.length);
            assertEquals(PROTOCOL_SSL_V2_HELLO, enabledProtocols[0]);

            // Enable a protocol that is currently disabled
            sslEngine.setEnabledProtocols(new String[]{PROTOCOL_TLS_V1_2});

            // The protocol that was just enabled should be returned
            enabledProtocols = sslEngine.getEnabledProtocols();
            assertEquals(2, enabledProtocols.length);
            assertEquals(PROTOCOL_SSL_V2_HELLO, enabledProtocols[0]);
            assertEquals(PROTOCOL_TLS_V1_2, enabledProtocols[1]);
        } finally {
            if (sslEngine != null) {
                sslEngine.closeInbound();
                sslEngine.closeOutbound();
            }
        }
    }

    @Override
    public void testMutualAuthSameCerts() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        super.testMutualAuthSameCerts();
    }

    @Override
    public void testMutualAuthDiffCerts() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        super.testMutualAuthDiffCerts();
    }

    @Override
    public void testMutualAuthDiffCertsServerFailure() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        super.testMutualAuthDiffCertsServerFailure();
    }

    @Override
    public void testMutualAuthDiffCertsClientFailure() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        super.testMutualAuthDiffCertsClientFailure();
    }

    @Override
    public void testGetCreationTime() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        super.testGetCreationTime();
    }

    @Override
    public void testSessionInvalidate() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        super.testSessionInvalidate();
    }

    @Override
    protected SslProvider sslProvider() {
        return SslProvider.OPENSSL;
    }

    private static ApplicationProtocolConfig acceptingNegotiator(Protocol protocol,
            String... supportedProtocols) {
        return new ApplicationProtocolConfig(protocol,
                SelectorFailureBehavior.NO_ADVERTISE,
                SelectedListenerFailureBehavior.ACCEPT,
                supportedProtocols);
    }
}
