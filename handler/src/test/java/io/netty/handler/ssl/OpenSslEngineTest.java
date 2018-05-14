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
import io.netty.util.internal.PlatformDependent;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.ByteBuffer;
import java.security.AlgorithmConstraints;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

import static io.netty.handler.ssl.OpenSslTestUtils.checkShouldUseKeyManagerFactory;
import static io.netty.handler.ssl.ReferenceCountedOpenSslEngine.MAX_PLAINTEXT_LENGTH;
import static io.netty.handler.ssl.SslUtils.PROTOCOL_SSL_V2_HELLO;
import static io.netty.handler.ssl.SslUtils.PROTOCOL_SSL_V3;
import static io.netty.handler.ssl.SslUtils.PROTOCOL_TLS_V1;
import static io.netty.handler.ssl.SslUtils.PROTOCOL_TLS_V1_1;
import static io.netty.handler.ssl.SslUtils.PROTOCOL_TLS_V1_2;
import static io.netty.internal.tcnative.SSL.SSL_CVERIFY_IGNORED;
import static java.lang.Integer.MAX_VALUE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;


@RunWith(Parameterized.class)
public class OpenSslEngineTest extends SSLEngineTest {
    private static final String PREFERRED_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http2";
    private static final String FALLBACK_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http1_1";

    @Parameterized.Parameters(name = "{index}: bufferType = {0}")
    public static Collection<Object> data() {
        List<Object> params = new ArrayList<Object>();
        for (BufferType type: BufferType.values()) {
            params.add(type);
        }
        return params;
    }

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
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthInvalidIntermediateCASucceedWithOptionalClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthInvalidIntermediateCAFailWithOptionalClientAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthInvalidIntermediateCAFailWithOptionalClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthInvalidIntermediateCAFailWithRequiredClientAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthInvalidIntermediateCAFailWithRequiredClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth();
    }

    @Override
    @Test
    public void testMutualAuthValidClientCertChainTooLongFailRequireClientAuth() throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthValidClientCertChainTooLongFailRequireClientAuth();
    }

    @Override
    @Test
    public void testClientHostnameValidationSuccess() throws InterruptedException, SSLException {
        assumeTrue(OpenSsl.supportsHostnameValidation());
        super.testClientHostnameValidationSuccess();
    }

    @Override
    @Test
    public void testClientHostnameValidationFail() throws InterruptedException, SSLException {
        assumeTrue(OpenSsl.supportsHostnameValidation());
        super.testClientHostnameValidationFail();
    }

    private static boolean isNpnSupported(String versionString) {
        String[] versionStringParts = versionString.split(" ", -1);
        if (versionStringParts.length == 2 && "LibreSSL".equals(versionStringParts[0])) {
            String[] versionParts = versionStringParts[1].split("\\.", -1);
            if (versionParts.length == 3) {
                int major = Integer.parseInt(versionParts[0]);
                if (major < 2) {
                    return true;
                }
                if (major > 2) {
                    return false;
                }
                int minor = Integer.parseInt(versionParts[1]);
                if (minor < 6) {
                    return true;
                }
                if (minor > 6) {
                    return false;
                }
                int bugfix = Integer.parseInt(versionParts[2]);
                if (bugfix > 0) {
                    return false;
                }
            }
        }
        return true;
    }
    @Test
    public void testNpn() throws Exception {
        String versionString = OpenSsl.versionString();
        assumeTrue("LibreSSL 2.6.1 removed NPN support, detected " + versionString, isNpnSupported(versionString));
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
            PlatformDependent.threadLocalRandom().nextBytes(data);
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
                    src.capacity() + ((ReferenceCountedOpenSslEngine) clientEngine).maxWrapOverhead() - 1);
            ByteBuffer dst = allocateBuffer(
                    src.capacity() + ((ReferenceCountedOpenSslEngine) clientEngine).maxWrapOverhead());

            // Check that we fail to wrap if the dst buffers capacity is not at least
            // src.capacity() + ReferenceCountedOpenSslEngine.MAX_TLS_RECORD_OVERHEAD_LENGTH
            SSLEngineResult result = clientEngine.wrap(src, dstTooSmall);
            assertEquals(SSLEngineResult.Status.BUFFER_OVERFLOW, result.getStatus());
            assertEquals(0, result.bytesConsumed());
            assertEquals(0, result.bytesProduced());
            assertEquals(src.remaining(), src.capacity());
            assertEquals(dst.remaining(), dst.capacity());

            // Check that we can wrap with a dst buffer that has the capacity of
            // src.capacity() + ReferenceCountedOpenSslEngine.MAX_TLS_RECORD_OVERHEAD_LENGTH
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
                    + ((ReferenceCountedOpenSslEngine) clientEngine).maxWrapOverhead());

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
            long maxLen = ((long) MAX_VALUE) * 2;

            while (srcsLen < maxLen) {
                ByteBuffer dup = src.duplicate();
                srcList.add(dup);
                srcsLen += dup.capacity();
            }

            ByteBuffer[] srcs = srcList.toArray(new ByteBuffer[srcList.size()]);
            ByteBuffer dst = allocateBuffer(
                    ((ReferenceCountedOpenSslEngine) clientEngine).maxEncryptedPacketLength() - 1);

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
    public void testCalculateOutNetBufSizeOverflow() throws SSLException {
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine clientEngine = null;
        try {
            clientEngine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            int value = ((ReferenceCountedOpenSslEngine) clientEngine).calculateMaxLengthForWrap(MAX_VALUE, 1);
            assertTrue("unexpected value: " + value, value > 0);
        } finally {
            cleanupClientSslEngine(clientEngine);
        }
    }

    @Test
    public void testCalculateOutNetBufSize0() throws SSLException {
        clientSslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine clientEngine = null;
        try {
            clientEngine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            assertTrue(((ReferenceCountedOpenSslEngine) clientEngine).calculateMaxLengthForWrap(0, 1) > 0);
        } finally {
            cleanupClientSslEngine(clientEngine);
        }
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

        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "ADH-AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "ECDHE-RSA-AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "ADH-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "AECDH-AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "AECDH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "CAMELLIA128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "DHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "SEED-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "RC4-MD5");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "ADH-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "ADH-SEED-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "ADH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "EDH-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "ADH-RC4-MD5");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "IDEA-CBC-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "DHE-RSA-AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "RC4-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "CAMELLIA256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "AECDH-RC4-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "DHE-RSA-SEED-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "AECDH-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "ECDHE-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "ADH-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "DHE-RSA-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "ECDHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "DHE-RSA-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1, "ECDHE-RSA-RC4-SHA");
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

        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "ECDHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "DHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "DHE-RSA-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "ADH-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "ADH-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "CAMELLIA256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "AECDH-AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "DHE-RSA-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "ECDHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "ADH-AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "ADH-SEED-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "ADH-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "SEED-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "CAMELLIA128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "IDEA-CBC-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "AECDH-RC4-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "ADH-RC4-MD5");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "RC4-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "ECDHE-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "EDH-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "AECDH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "ADH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_1, "DES-CBC3-SHA");
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

        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ADH-AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ECDHE-RSA-AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ADH-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "DHE-RSA-AES256-GCM-SHA384");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "AECDH-AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "AES128-GCM-SHA256");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "DHE-RSA-AES128-GCM-SHA256");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ECDHE-RSA-AES256-SHA384");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "AECDH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "AES256-GCM-SHA384");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "AES256-SHA256");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ECDHE-RSA-AES128-GCM-SHA256");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ECDHE-RSA-AES128-SHA256");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "CAMELLIA128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "DHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "SEED-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "RC4-MD5");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ADH-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ADH-SEED-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "DHE-RSA-AES128-SHA256");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ADH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "EDH-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ADH-RC4-MD5");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "IDEA-CBC-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "DHE-RSA-AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "RC4-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "CAMELLIA256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ADH-AES128-GCM-SHA256");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "AES128-SHA256");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "AECDH-RC4-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ADH-AES256-GCM-SHA384");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "DHE-RSA-SEED-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "DHE-RSA-AES256-SHA256");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "AECDH-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ECDHE-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ADH-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ECDHE-RSA-AES256-GCM-SHA384");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "DHE-RSA-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ADH-AES256-SHA256");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ADH-AES128-SHA256");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ECDHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "DHE-RSA-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_TLS_V1_2, "ECDHE-RSA-RC4-SHA");
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

        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "ADH-AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "ADH-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "AECDH-AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "AECDH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "CAMELLIA128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "DHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "SEED-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "RC4-MD5");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "ADH-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "ADH-SEED-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "ADH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "EDH-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "ADH-RC4-MD5");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "IDEA-CBC-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "DHE-RSA-AES128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "RC4-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "CAMELLIA256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "AECDH-RC4-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "DHE-RSA-SEED-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "AECDH-AES256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "ECDHE-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "ADH-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "DHE-RSA-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "DHE-RSA-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(PROTOCOL_SSL_V3, "ECDHE-RSA-RC4-SHA");
    }

    @Test
    public void testMultipleRecordsInOneBufferWithNonZeroPositionJDKCompatabilityModeOff() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine();

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine server = serverSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine();

        try {
            // Choose buffer size small enough that we can put multiple buffers into one buffer and pass it into the
            // unwrap call without exceed MAX_ENCRYPTED_PACKET_LENGTH.
            final int plainClientOutLen = 1024;
            ByteBuffer plainClientOut = allocateBuffer(plainClientOutLen);
            ByteBuffer plainServerOut = allocateBuffer(server.getSession().getApplicationBufferSize());

            ByteBuffer encClientToServer = allocateBuffer(client.getSession().getPacketBufferSize());

            int positionOffset = 1;
            // We need to be able to hold 2 records + positionOffset
            ByteBuffer combinedEncClientToServer = allocateBuffer(
                    encClientToServer.capacity() * 2 + positionOffset);
            combinedEncClientToServer.position(positionOffset);

            handshake(client, server);

            plainClientOut.limit(plainClientOut.capacity());
            SSLEngineResult result = client.wrap(plainClientOut, encClientToServer);
            assertEquals(plainClientOut.capacity(), result.bytesConsumed());
            assertTrue(result.bytesProduced() > 0);

            encClientToServer.flip();

            // Copy the first record into the combined buffer
            combinedEncClientToServer.put(encClientToServer);

            plainClientOut.clear();
            encClientToServer.clear();

            result = client.wrap(plainClientOut, encClientToServer);
            assertEquals(plainClientOut.capacity(), result.bytesConsumed());
            assertTrue(result.bytesProduced() > 0);

            encClientToServer.flip();

            // Copy the first record into the combined buffer
            combinedEncClientToServer.put(encClientToServer);

            encClientToServer.clear();

            combinedEncClientToServer.flip();
            combinedEncClientToServer.position(positionOffset);

            // Make sure the limit takes positionOffset into account to the content we are looking at is correct.
            combinedEncClientToServer.limit(
                    combinedEncClientToServer.limit() - positionOffset);
            final int combinedEncClientToServerLen = combinedEncClientToServer.remaining();

            result = server.unwrap(combinedEncClientToServer, plainServerOut);
            assertEquals(0, combinedEncClientToServer.remaining());
            assertEquals(combinedEncClientToServerLen, result.bytesConsumed());
            assertEquals(plainClientOutLen, result.bytesProduced());
        } finally {
            cert.delete();
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
        }
    }

    @Test
    public void testInputTooBigAndFillsUpBuffersJDKCompatabilityModeOff() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine();

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine server = serverSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine();

        try {
            ByteBuffer plainClient = allocateBuffer(MAX_PLAINTEXT_LENGTH + 100);
            ByteBuffer plainClient2 = allocateBuffer(512);
            ByteBuffer plainClientTotal = allocateBuffer(plainClient.capacity() + plainClient2.capacity());
            plainClientTotal.put(plainClient);
            plainClientTotal.put(plainClient2);
            plainClient.clear();
            plainClient2.clear();
            plainClientTotal.flip();

            // The capacity is designed to trigger an overflow condition.
            ByteBuffer encClientToServerTooSmall = allocateBuffer(MAX_PLAINTEXT_LENGTH + 28);
            ByteBuffer encClientToServer = allocateBuffer(client.getSession().getApplicationBufferSize());
            ByteBuffer encClientToServerTotal = allocateBuffer(client.getSession().getApplicationBufferSize() << 1);
            ByteBuffer plainServer = allocateBuffer(server.getSession().getApplicationBufferSize() << 1);

            handshake(client, server);

            int plainClientRemaining = plainClient.remaining();
            int encClientToServerTooSmallRemaining = encClientToServerTooSmall.remaining();
            SSLEngineResult result = client.wrap(plainClient, encClientToServerTooSmall);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(plainClientRemaining - plainClient.remaining(), result.bytesConsumed());
            assertEquals(encClientToServerTooSmallRemaining - encClientToServerTooSmall.remaining(),
                    result.bytesProduced());

            result = client.wrap(plainClient, encClientToServerTooSmall);
            assertEquals(SSLEngineResult.Status.BUFFER_OVERFLOW, result.getStatus());
            assertEquals(0, result.bytesConsumed());
            assertEquals(0, result.bytesProduced());

            plainClientRemaining = plainClient.remaining();
            int encClientToServerRemaining = encClientToServer.remaining();
            result = client.wrap(plainClient, encClientToServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(plainClientRemaining, result.bytesConsumed());
            assertEquals(encClientToServerRemaining - encClientToServer.remaining(), result.bytesProduced());
            assertEquals(0, plainClient.remaining());

            final int plainClient2Remaining = plainClient2.remaining();
            encClientToServerRemaining = encClientToServer.remaining();
            result = client.wrap(plainClient2, encClientToServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(plainClient2Remaining, result.bytesConsumed());
            assertEquals(encClientToServerRemaining - encClientToServer.remaining(), result.bytesProduced());

            // Concatenate the too small buffer
            encClientToServerTooSmall.flip();
            encClientToServer.flip();
            encClientToServerTotal.put(encClientToServerTooSmall);
            encClientToServerTotal.put(encClientToServer);
            encClientToServerTotal.flip();

            // Unwrap in a single call.
            final int encClientToServerTotalRemaining = encClientToServerTotal.remaining();
            result = server.unwrap(encClientToServerTotal, plainServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(encClientToServerTotalRemaining, result.bytesConsumed());
            plainServer.flip();
            assertEquals(plainClientTotal, plainServer);
        } finally {
            cert.delete();
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
        }
    }

    @Test
    public void testPartialPacketUnwrapJDKCompatabilityModeOff() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine();

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine server = serverSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine();

        try {
            ByteBuffer plainClient = allocateBuffer(1024);
            ByteBuffer plainClient2 = allocateBuffer(512);
            ByteBuffer plainClientTotal = allocateBuffer(plainClient.capacity() + plainClient2.capacity());
            plainClientTotal.put(plainClient);
            plainClientTotal.put(plainClient2);
            plainClient.clear();
            plainClient2.clear();
            plainClientTotal.flip();

            ByteBuffer encClientToServer = allocateBuffer(client.getSession().getPacketBufferSize());
            ByteBuffer plainServer = allocateBuffer(server.getSession().getApplicationBufferSize());

            handshake(client, server);

            SSLEngineResult result = client.wrap(plainClient, encClientToServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(result.bytesConsumed(), plainClient.capacity());
            final int encClientLen = result.bytesProduced();

            result = client.wrap(plainClient2, encClientToServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(result.bytesConsumed(), plainClient2.capacity());
            final int encClientLen2 = result.bytesProduced();

            // Flip so we can read it.
            encClientToServer.flip();

            // Consume a partial TLS packet.
            ByteBuffer encClientFirstHalf = encClientToServer.duplicate();
            encClientFirstHalf.limit(encClientLen / 2);
            result = server.unwrap(encClientFirstHalf, plainServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(result.bytesConsumed(), encClientLen / 2);
            encClientToServer.position(result.bytesConsumed());

            // We now have half of the first packet and the whole second packet, so lets decode all but the last byte.
            ByteBuffer encClientAllButLastByte = encClientToServer.duplicate();
            final int encClientAllButLastByteLen = encClientAllButLastByte.remaining() - 1;
            encClientAllButLastByte.limit(encClientAllButLastByte.limit() - 1);
            result = server.unwrap(encClientAllButLastByte, plainServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(result.bytesConsumed(), encClientAllButLastByteLen);
            encClientToServer.position(encClientToServer.position() + result.bytesConsumed());

            // Read the last byte and verify the original content has been decrypted.
            result = server.unwrap(encClientToServer, plainServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(result.bytesConsumed(), 1);
            plainServer.flip();
            assertEquals(plainClientTotal, plainServer);
        } finally {
            cert.delete();
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
        }
    }

    @Test
    public void testBufferUnderFlowAvoidedIfJDKCompatabilityModeOff() throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .build();
        SSLEngine client = clientSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine();

        serverSslCtx = SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .build();
        SSLEngine server = serverSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine();

        try {
            ByteBuffer plainClient = allocateBuffer(1024);
            plainClient.limit(plainClient.capacity());

            ByteBuffer encClientToServer = allocateBuffer(client.getSession().getPacketBufferSize());
            ByteBuffer plainServer = allocateBuffer(server.getSession().getApplicationBufferSize());

            handshake(client, server);

            SSLEngineResult result = client.wrap(plainClient, encClientToServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(result.bytesConsumed(), plainClient.capacity());

            // Flip so we can read it.
            encClientToServer.flip();
            int remaining = encClientToServer.remaining();

            // We limit the buffer so we have less then the header to read, this should result in an BUFFER_UNDERFLOW.
            encClientToServer.limit(SslUtils.SSL_RECORD_HEADER_LENGTH - 1);
            result = server.unwrap(encClientToServer, plainServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(SslUtils.SSL_RECORD_HEADER_LENGTH - 1, result.bytesConsumed());
            assertEquals(0, result.bytesProduced());
            remaining -= result.bytesConsumed();

            // We limit the buffer so we can read the header but not the rest, this should result in an
            // BUFFER_UNDERFLOW.
            encClientToServer.limit(SslUtils.SSL_RECORD_HEADER_LENGTH);
            result = server.unwrap(encClientToServer, plainServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(1, result.bytesConsumed());
            assertEquals(0, result.bytesProduced());
            remaining -= result.bytesConsumed();

            // We limit the buffer so we can read the header and partly the rest, this should result in an
            // BUFFER_UNDERFLOW.
            encClientToServer.limit(
                    SslUtils.SSL_RECORD_HEADER_LENGTH  + remaining - 1 - SslUtils.SSL_RECORD_HEADER_LENGTH);
            result = server.unwrap(encClientToServer, plainServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(encClientToServer.limit() - SslUtils.SSL_RECORD_HEADER_LENGTH, result.bytesConsumed());
            assertEquals(0, result.bytesProduced());
            remaining -= result.bytesConsumed();

            // Reset limit so we can read the full record.
            encClientToServer.limit(remaining);
            assertEquals(0, encClientToServer.remaining());
            result = server.unwrap(encClientToServer, plainServer);
            assertEquals(SSLEngineResult.Status.BUFFER_UNDERFLOW, result.getStatus());
            assertEquals(0, result.bytesConsumed());
            assertEquals(0, result.bytesProduced());

            encClientToServer.position(0);
            result = server.unwrap(encClientToServer, plainServer);
            assertEquals(SSLEngineResult.Status.OK, result.getStatus());
            assertEquals(remaining, result.bytesConsumed());
            assertEquals(0, result.bytesProduced());
        } finally {
            cert.delete();
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
        }
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
            } while (srcLen < MAX_PLAINTEXT_LENGTH);

            testWrapDstBigEnough(clientEngine, MAX_PLAINTEXT_LENGTH);
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
        }
    }

    private void testWrapDstBigEnough(SSLEngine engine, int srcLen) throws SSLException {
        ByteBuffer src = allocateBuffer(srcLen);
        ByteBuffer dst = allocateBuffer(srcLen + ((ReferenceCountedOpenSslEngine) engine).maxWrapOverhead());

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

    @Test
    public void testSNIMatchersDoesNotThrow() throws Exception {
        assumeTrue(PlatformDependent.javaVersion() >= 8);
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build();

        SSLEngine engine = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
        try {
            SSLParameters parameters = new SSLParameters();
            Java8SslTestUtils.setSNIMatcher(parameters);
            engine.setSSLParameters(parameters);
        } finally {
            cleanupServerSslEngine(engine);
            ssc.delete();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAlgorithmConstraintsThrows() throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build();

        SSLEngine engine = serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
        try {
            SSLParameters parameters = new SSLParameters();
            parameters.setAlgorithmConstraints(new AlgorithmConstraints() {
                @Override
                public boolean permits(
                        Set<CryptoPrimitive> primitives, String algorithm, AlgorithmParameters parameters) {
                    return false;
                }

                @Override
                public boolean permits(Set<CryptoPrimitive> primitives, Key key) {
                    return false;
                }

                @Override
                public boolean permits(
                        Set<CryptoPrimitive> primitives, String algorithm, Key key, AlgorithmParameters parameters) {
                    return false;
                }
            });
            engine.setSSLParameters(parameters);
        } finally {
            cleanupServerSslEngine(engine);
            ssc.delete();
        }
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
