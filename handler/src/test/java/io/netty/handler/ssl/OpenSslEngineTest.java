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
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.internal.ThreadLocalRandom;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

public class OpenSslEngineTest extends SSLEngineTest {
    private static final String PREFERRED_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http2";
    private static final String FALLBACK_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http1_1";

    public OpenSslEngineTest(BufferType type) {
        super(type);
    }

    @BeforeClass
    public static void checkOpenSsl() {
        assumeTrue(OpenSsl.isAvailable());
    }

    @Test
    public void testNpn() throws Exception {
        ApplicationProtocolConfig apn = acceptingNegotiator(Protocol.NPN,
                PREFERRED_APPLICATION_LEVEL_PROTOCOL);
        setupHandlers(apn);
        runTest(PREFERRED_APPLICATION_LEVEL_PROTOCOL);
    }

    @Test
    public void testAlpn() throws Exception {
        assumeTrue(OpenSsl.isAlpnSupported());
        ApplicationProtocolConfig apn = acceptingNegotiator(Protocol.ALPN,
                PREFERRED_APPLICATION_LEVEL_PROTOCOL);
        setupHandlers(apn);
        runTest(PREFERRED_APPLICATION_LEVEL_PROTOCOL);
    }

    @Test
    public void testAlpnCompatibleProtocolsDifferentClientOrder() throws Exception {
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
        testEnablingAnAlreadyDisabledSslProtocol(new String[]{PROTOCOL_SSL_V2_HELLO},
            new String[]{PROTOCOL_SSL_V2_HELLO, PROTOCOL_TLS_V1_2});
    }
    @Test
    public void testWrapBuffersNoWritePendingError() throws Exception {
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            serverEngine = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            handshake(clientEngine, serverEngine);

            ByteBuffer src = allocateBuffer(1024 * 10);
            byte[] data = new byte[src.capacity()];
            ThreadLocalRandom.current().nextBytes(data);
            src.put(data).flip();
            ByteBuffer dst = allocateBuffer(1);
            // Try to wrap multiple times so we are more likely to hit the issue.
            for (int i = 0; i < 100; i++) {
                src.position(0);
                dst.position(0);
                assertSame(SSLEngineResult.Status.BUFFER_OVERFLOW, clientEngine.wrap(src, dst).getStatus());
            }
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
        }
    }

    @Test
    public void testOnlySmallBufferNeededForWrap() throws Exception {
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            serverEngine = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            handshake(clientEngine, serverEngine);

            // Allocate a buffer which is small enough and set the limit to the capacity to mark its whole content
            // as readable.
            int srcLen = 1024;
            ByteBuffer src = allocateBuffer(srcLen);

            ByteBuffer dstTooSmall = allocateBuffer(
                    src.capacity() + ReferenceCountedOpenSslEngine.MAX_ENCRYPTION_OVERHEAD_LENGTH - 1);
            ByteBuffer dst = allocateBuffer(
                    src.capacity() + ReferenceCountedOpenSslEngine.MAX_ENCRYPTION_OVERHEAD_LENGTH);

            // Check that we fail to wrap if the dst buffers capacity is not at least
            // src.capacity() + ReferenceCountedOpenSslEngine.MAX_ENCRYPTION_OVERHEAD_LENGTH
            SSLEngineResult result = clientEngine.wrap(src, dstTooSmall);
            assertEquals(SSLEngineResult.Status.BUFFER_OVERFLOW, result.getStatus());
            assertEquals(0, result.bytesConsumed());
            assertEquals(0, result.bytesProduced());
            assertEquals(src.remaining(), src.capacity());
            assertEquals(dst.remaining(), dst.capacity());

            // Check that we can wrap with a dst buffer that has the capacity of
            // src.capacity() + ReferenceCountedOpenSslEngine.MAX_ENCRYPTION_OVERHEAD_LENGTH
            result = clientEngine.wrap(src, dst);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(srcLen, result.bytesConsumed());
            assertEquals(0, src.remaining());
            assertTrue(result.bytesProduced() > srcLen);
            assertEquals(src.capacity() - result.bytesConsumed(), src.remaining());
            assertEquals(dst.capacity() - result.bytesProduced(), dst.remaining());
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
        }
    }

    @Test
    public void testNeededDstCapacityIsCorrectlyCalculated() throws Exception {
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            serverEngine = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            handshake(clientEngine, serverEngine);

            ByteBuffer src = allocateBuffer(1024);
            ByteBuffer src2 = src.duplicate();

            ByteBuffer dst = allocateBuffer(src.capacity()
                    + ReferenceCountedOpenSslEngine.MAX_ENCRYPTION_OVERHEAD_LENGTH);

            SSLEngineResult result = clientEngine.wrap(new ByteBuffer[] { src, src2 }, dst);
            assertEquals(SSLEngineResult.Status.BUFFER_OVERFLOW, result.getStatus());
            assertEquals(0, src.position());
            assertEquals(0, src2.position());
            assertEquals(0, dst.position());
            assertEquals(0, result.bytesConsumed());
            assertEquals(0, result.bytesProduced());
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
        }
    }

    @Test
    public void testSrcsLenOverFlowCorrectlyHandled() throws Exception {
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            serverEngine = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            handshake(clientEngine, serverEngine);

            ByteBuffer src = allocateBuffer(1024);
            List<ByteBuffer> srcList = new ArrayList<ByteBuffer>();
            long srcsLen = 0;
            long maxLen = ((long) Integer.MAX_VALUE) * 2;

            while (srcsLen < maxLen) {
                ByteBuffer dup = src.duplicate();
                srcList.add(dup);
                srcsLen += dup.capacity();
            }

            ByteBuffer[] srcs = srcList.toArray(new ByteBuffer[srcList.size()]);

            ByteBuffer dst = allocateBuffer(ReferenceCountedOpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH - 1);

            SSLEngineResult result = clientEngine.wrap(srcs, dst);
            assertEquals(SSLEngineResult.Status.BUFFER_OVERFLOW, result.getStatus());

            for (ByteBuffer buffer : srcs) {
                assertEquals(0, buffer.position());
            }
            assertEquals(0, dst.position());
            assertEquals(0, result.bytesConsumed());
            assertEquals(0, result.bytesProduced());
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
        }
    }

    @Test
    public void testCalculateOutNetBufSizeOverflow() {
        assertEquals(ReferenceCountedOpenSslEngine.MAX_ENCRYPTED_PACKET_LENGTH,
                ReferenceCountedOpenSslEngine.calculateOutNetBufSize(Integer.MAX_VALUE));
    }

    @Override
    protected SslProvider sslClientProvider() {
        return SslProvider.OPENSSL;
    }

    @Override
    protected SslProvider sslServerProvider() {
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
