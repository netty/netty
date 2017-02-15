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
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import static io.netty.internal.tcnative.SSL.SSL_CVERIFY_IGNORED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Override
    @Test
    public void testMutualAuthInvalidIntermediateCASucceedWithOptionalClientAuth() throws Exception {
        assumeTrue(OpenSsl.supportsKeyManagerFactory());
        super.testMutualAuthInvalidIntermediateCASucceedWithOptionalClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthInvalidIntermediateCAFailWithOptionalClientAuth() throws Exception {
        assumeTrue(OpenSsl.supportsKeyManagerFactory());
        super.testMutualAuthInvalidIntermediateCAFailWithOptionalClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthInvalidIntermediateCAFailWithRequiredClientAuth() throws Exception {
        assumeTrue(OpenSsl.supportsKeyManagerFactory());
        super.testMutualAuthInvalidIntermediateCAFailWithRequiredClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth() throws Exception {
        assumeTrue(OpenSsl.supportsKeyManagerFactory());
        super.testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthValidClientCertChainTooLongFailRequireClientAuth() throws Exception {
        assumeTrue(OpenSsl.supportsKeyManagerFactory());
        super.testMutualAuthValidClientCertChainTooLongFailRequireClientAuth();
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

    @Test
    public void testCalculateOutNetBufSize0() {
        assertEquals(ReferenceCountedOpenSslEngine.MAX_ENCRYPTION_OVERHEAD_LENGTH,
                ReferenceCountedOpenSslEngine.calculateOutNetBufSize(0));
    }

    @Override
    protected void mySetupMutualAuthServerInitSslHandler(SslHandler handler) {
        ReferenceCountedOpenSslEngine engine = (ReferenceCountedOpenSslEngine) handler.engine();
        engine.setVerify(SSL_CVERIFY_IGNORED, 1);
    }

    @Test
    public void testWrapWithDifferentSizesTLSv1() throws Exception {
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build();

        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "ADH-AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "ECDHE-RSA-AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "ADH-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "AECDH-AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "AECDH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "CAMELLIA128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "DHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "SEED-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "RC4-MD5");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "ADH-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "ADH-SEED-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "ADH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "EDH-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "ADH-RC4-MD5");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "IDEA-CBC-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "DHE-RSA-AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "RC4-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "CAMELLIA256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "AECDH-RC4-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "DHE-RSA-SEED-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "AECDH-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "ECDHE-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "ADH-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "DHE-RSA-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "ECDHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "DHE-RSA-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1, "ECDHE-RSA-RC4-SHA");
    }

    @Test
    public void testWrapWithDifferentSizesTLSv1_1() throws Exception {
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build();

        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "ECDHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "DHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "DHE-RSA-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "ADH-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "ADH-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "CAMELLIA256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "AECDH-AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "DHE-RSA-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "ECDHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "ADH-AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "ADH-SEED-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "ADH-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "SEED-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "CAMELLIA128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "IDEA-CBC-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "AECDH-RC4-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "ADH-RC4-MD5");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "RC4-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "ECDHE-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "EDH-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "AECDH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "ADH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_1, "DES-CBC3-SHA");
    }

    @Test
    public void testWrapWithDifferentSizesTLSv1_2() throws Exception {
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build();

        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ADH-AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ECDHE-RSA-AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ADH-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "DHE-RSA-AES256-GCM-SHA384");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "AECDH-AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "AES128-GCM-SHA256");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "DHE-RSA-AES128-GCM-SHA256");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ECDHE-RSA-AES256-SHA384");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "AECDH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "AES256-GCM-SHA384");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "AES256-SHA256");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ECDHE-RSA-AES128-GCM-SHA256");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ECDHE-RSA-AES128-SHA256");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "CAMELLIA128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "DHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "SEED-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "RC4-MD5");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ADH-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ADH-SEED-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "DHE-RSA-AES128-SHA256");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ADH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "EDH-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ADH-RC4-MD5");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "IDEA-CBC-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "DHE-RSA-AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "RC4-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "CAMELLIA256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ADH-AES128-GCM-SHA256");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "AES128-SHA256");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "AECDH-RC4-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ADH-AES256-GCM-SHA384");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "DHE-RSA-SEED-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "DHE-RSA-AES256-SHA256");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "AECDH-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ECDHE-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ADH-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ECDHE-RSA-AES256-GCM-SHA384");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "DHE-RSA-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ADH-AES256-SHA256");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ADH-AES128-SHA256");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ECDHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "DHE-RSA-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_TLS_V1_2, "ECDHE-RSA-RC4-SHA");
    }

    @Test
    public void testWrapWithDifferentSizesSSLv3() throws Exception {
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build();

        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "ADH-AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "ADH-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "AECDH-AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "AECDH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "CAMELLIA128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "DHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "SEED-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "RC4-MD5");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "ADH-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "ADH-SEED-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "ADH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "EDH-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "ADH-RC4-MD5");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "IDEA-CBC-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "DHE-RSA-AES128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "RC4-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "CAMELLIA256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "AECDH-RC4-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "DHE-RSA-SEED-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "AECDH-AES256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "ECDHE-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "ADH-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "DHE-RSA-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "DHE-RSA-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(OpenSsl.PROTOCOL_SSL_V3, "ECDHE-RSA-RC4-SHA");
    }

    private void testWrapWithDifferentSizes(String protocol, String cipher) throws Exception {
        assumeTrue(OpenSsl.SUPPORTED_PROTOCOLS_SET.contains(protocol));
        if (!OpenSsl.isCipherSuiteAvailable(cipher)) {
            return;
        }

        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            serverEngine = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            clientEngine.setEnabledCipherSuites(new String[] { cipher });
            clientEngine.setEnabledProtocols(new String[] { protocol });
            serverEngine.setEnabledCipherSuites(new String[] { cipher });
            serverEngine.setEnabledProtocols(new String[] { protocol });

            try {
                handshake(clientEngine, serverEngine);
            } catch (SSLException e) {
                if (e.getMessage().contains("unsupported protocol")) {
                    Assume.assumeNoException(protocol + " not supported with cipher " + cipher, e);
                }
                throw e;
            }

            int srcLen = 64;
            do {
                testWrapDstBigEnough(clientEngine, srcLen);
                srcLen += 64;
            } while (srcLen < ReferenceCountedOpenSslEngine.MAX_PLAINTEXT_LENGTH);

            testWrapDstBigEnough(clientEngine, ReferenceCountedOpenSslEngine.MAX_PLAINTEXT_LENGTH);
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
        }
    }

    private void testWrapDstBigEnough(SSLEngine engine, int srcLen) throws SSLException {
        ByteBuffer src = allocateBuffer(srcLen);
        ByteBuffer dst = allocateBuffer(srcLen + ReferenceCountedOpenSslEngine.MAX_ENCRYPTION_OVERHEAD_LENGTH);

        SSLEngineResult result = engine.wrap(src, dst);
        assertEquals(SSLEngineResult.Status.OK, result.getStatus());
        int consumed = result.bytesConsumed();
        int produced = result.bytesProduced();
        assertEquals(srcLen, consumed);
        assertTrue(produced > consumed);

        dst.flip();
        assertEquals(produced, dst.remaining());
        assertFalse(src.hasRemaining());
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
