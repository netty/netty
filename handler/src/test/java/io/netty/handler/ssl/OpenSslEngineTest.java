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
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.internal.ThreadLocalRandom;
import org.junit.Test;

import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assume.assumeTrue;

public class OpenSslEngineTest extends SSLEngineTest {
    private static final String PREFERRED_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http2";
    private static final String FALLBACK_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http1_1";

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
        testEnablingAnAlreadyDisabledSslProtocol(new String[]{PROTOCOL_SSL_V2_HELLO},
            new String[]{PROTOCOL_SSL_V2_HELLO, PROTOCOL_TLS_V1_2});
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

    @Test
    public void testWrapHeapBuffersNoWritePendingError() throws Exception {
        assumeTrue(OpenSsl.isAvailable());
        final SslContext clientContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslProvider())
                .build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        SslContext serverContext = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslProvider())
                .build();
        SSLEngine clientEngine = clientContext.newEngine(UnpooledByteBufAllocator.DEFAULT);
        SSLEngine serverEngine = serverContext.newEngine(UnpooledByteBufAllocator.DEFAULT);
        handshake(clientEngine, serverEngine);

        ByteBuffer src = ByteBuffer.allocate(1024 * 10);
        ThreadLocalRandom.current().nextBytes(src.array());
        ByteBuffer dst = ByteBuffer.allocate(1);
        // Try to wrap multiple times so we are more likely to hit the issue.
        for (int i = 0; i < 100; i++) {
            src.position(0);
            dst.position(0);
            assertSame(SSLEngineResult.Status.BUFFER_OVERFLOW, clientEngine.wrap(src, dst).getStatus());
        }
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
