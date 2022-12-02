/*
 * Copyright 2015 The Netty Project
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
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.internal.tcnative.SSL;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.TestAbortedException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.X509ExtendedKeyManager;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.AlgorithmConstraints;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.Key;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static io.netty.handler.ssl.OpenSslTestUtils.checkShouldUseKeyManagerFactory;
import static io.netty.handler.ssl.ReferenceCountedOpenSslEngine.MAX_PLAINTEXT_LENGTH;
import static io.netty.internal.tcnative.SSL.SSL_CVERIFY_IGNORED;
import static java.lang.Integer.MAX_VALUE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class OpenSslEngineTest extends SSLEngineTest {
    private static final String PREFERRED_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http2";
    private static final String FALLBACK_APPLICATION_LEVEL_PROTOCOL = "my-protocol-http1_1";

    public OpenSslEngineTest() {
        super(SslProvider.isTlsv13Supported(SslProvider.OPENSSL));
    }

    @Override
    protected List<SSLEngineTestParam> newTestParams() {
        List<SSLEngineTestParam> params = super.newTestParams();
        List<SSLEngineTestParam> testParams = new ArrayList<SSLEngineTestParam>();
        for (SSLEngineTestParam param: params) {
            testParams.add(new OpenSslEngineTestParam(true, param));
            testParams.add(new OpenSslEngineTestParam(false, param));
        }
        return testParams;
    }

    @BeforeAll
    public static void checkOpenSsl() {
        OpenSsl.ensureAvailability();
    }

    @AfterEach
    @Override
    public void tearDown() throws InterruptedException {
        super.tearDown();
        assertEquals(0, SSL.getLastErrorNumber(), "SSL error stack not correctly consumed");
    }

    @Override
    public void testSessionAfterHandshakeKeyManagerFactory(SSLEngineTestParam param) throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testSessionAfterHandshakeKeyManagerFactory(param);
    }

    @Override
    public void testSessionAfterHandshakeKeyManagerFactoryMutualAuth(SSLEngineTestParam param) throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testSessionAfterHandshakeKeyManagerFactoryMutualAuth(param);
    }

    @Override
    public void testMutualAuthInvalidIntermediateCASucceedWithOptionalClientAuth(SSLEngineTestParam param)
            throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthInvalidIntermediateCASucceedWithOptionalClientAuth(param);
    }

    @Override
    public void testMutualAuthInvalidIntermediateCAFailWithOptionalClientAuth(SSLEngineTestParam param)
            throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthInvalidIntermediateCAFailWithOptionalClientAuth(param);
    }

    @Override
    public void testMutualAuthInvalidIntermediateCAFailWithRequiredClientAuth(SSLEngineTestParam param)
            throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthInvalidIntermediateCAFailWithRequiredClientAuth(param);
    }

    @Override
    public void testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth(SSLEngineTestParam param)
            throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthValidClientCertChainTooLongFailOptionalClientAuth(param);
    }

    @Override
    public void testMutualAuthValidClientCertChainTooLongFailRequireClientAuth(SSLEngineTestParam param)
            throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testMutualAuthValidClientCertChainTooLongFailRequireClientAuth(param);
    }

    @Override
    public void testHandshakeSession(SSLEngineTestParam param) throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testHandshakeSession(param);
    }

    @Override
    public void testSupportedSignatureAlgorithms(SSLEngineTestParam param) throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testSupportedSignatureAlgorithms(param);
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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testNpn(SSLEngineTestParam param) throws Exception {
        String versionString = OpenSsl.versionString();
        assumeTrue(isNpnSupported(versionString), "LibreSSL 2.6.1 removed NPN support, detected " + versionString);
        ApplicationProtocolConfig apn = acceptingNegotiator(Protocol.NPN,
                PREFERRED_APPLICATION_LEVEL_PROTOCOL);
        setupHandlers(param, apn);
        runTest(PREFERRED_APPLICATION_LEVEL_PROTOCOL);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testAlpn(SSLEngineTestParam param) throws Exception {
        assumeTrue(OpenSsl.isAlpnSupported());
        ApplicationProtocolConfig apn = acceptingNegotiator(Protocol.ALPN,
                PREFERRED_APPLICATION_LEVEL_PROTOCOL);
        setupHandlers(param, apn);
        runTest(PREFERRED_APPLICATION_LEVEL_PROTOCOL);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testAlpnCompatibleProtocolsDifferentClientOrder(SSLEngineTestParam param) throws Exception {
        assumeTrue(OpenSsl.isAlpnSupported());
        ApplicationProtocolConfig clientApn = acceptingNegotiator(Protocol.ALPN,
                FALLBACK_APPLICATION_LEVEL_PROTOCOL, PREFERRED_APPLICATION_LEVEL_PROTOCOL);
        ApplicationProtocolConfig serverApn = acceptingNegotiator(Protocol.ALPN,
                PREFERRED_APPLICATION_LEVEL_PROTOCOL, FALLBACK_APPLICATION_LEVEL_PROTOCOL);
        setupHandlers(param, serverApn, clientApn);
        assertNull(serverException);
        runTest(PREFERRED_APPLICATION_LEVEL_PROTOCOL);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testEnablingAnAlreadyDisabledSslProtocol(SSLEngineTestParam param) throws Exception {
        testEnablingAnAlreadyDisabledSslProtocol(param, new String[]{SslProtocols.SSL_v2_HELLO},
            new String[]{SslProtocols.SSL_v2_HELLO, SslProtocols.TLS_v1_2});
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testWrapBuffersNoWritePendingError(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .sslProvider(sslClientProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                                        .sslProvider(sslServerProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);

            ByteBuffer src = allocateBuffer(param.type(), 1024 * 10);
            byte[] data = new byte[src.capacity()];
            PlatformDependent.threadLocalRandom().nextBytes(data);
            src.put(data).flip();
            ByteBuffer dst = allocateBuffer(param.type(), 1);
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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testOnlySmallBufferNeededForWrap(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .sslProvider(sslClientProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                                        .sslProvider(sslServerProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);

            // Allocate a buffer which is small enough and set the limit to the capacity to mark its whole content
            // as readable.
            int srcLen = 1024;
            ByteBuffer src = allocateBuffer(param.type(), srcLen);

            ByteBuffer dstTooSmall = allocateBuffer(
                    param.type(), src.capacity() + unwrapEngine(clientEngine).maxWrapOverhead() - 1);
            ByteBuffer dst = allocateBuffer(
                    param.type(), src.capacity() + unwrapEngine(clientEngine).maxWrapOverhead());

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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testNeededDstCapacityIsCorrectlyCalculated(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .sslProvider(sslClientProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                                        .sslProvider(sslServerProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);

            ByteBuffer src = allocateBuffer(param.type(), 1024);
            ByteBuffer src2 = src.duplicate();

            ByteBuffer dst = allocateBuffer(param.type(), src.capacity()
                    + unwrapEngine(clientEngine).maxWrapOverhead());

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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSrcsLenOverFlowCorrectlyHandled(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .sslProvider(sslClientProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                                        .sslProvider(sslServerProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);

            ByteBuffer src = allocateBuffer(param.type(), 1024);
            List<ByteBuffer> srcList = new ArrayList<ByteBuffer>();
            long srcsLen = 0;
            long maxLen = ((long) MAX_VALUE) * 2;

            while (srcsLen < maxLen) {
                ByteBuffer dup = src.duplicate();
                srcList.add(dup);
                srcsLen += dup.capacity();
            }

            ByteBuffer[] srcs = srcList.toArray(new ByteBuffer[0]);
            ByteBuffer dst = allocateBuffer(
                    param.type(), unwrapEngine(clientEngine).maxEncryptedPacketLength() - 1);

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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testCalculateOutNetBufSizeOverflow(SSLEngineTestParam param) throws SSLException {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .sslProvider(sslClientProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        SSLEngine clientEngine = null;
        try {
            clientEngine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            int value = ((ReferenceCountedOpenSslEngine) clientEngine).calculateMaxLengthForWrap(MAX_VALUE, 1);
            assertTrue(value > 0);
        } finally {
            cleanupClientSslEngine(clientEngine);
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testCalculateOutNetBufSize0(SSLEngineTestParam param) throws SSLException {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .sslProvider(sslClientProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        SSLEngine clientEngine = null;
        try {
            clientEngine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
            assertTrue(((ReferenceCountedOpenSslEngine) clientEngine).calculateMaxLengthForWrap(0, 1) > 0);
        } finally {
            cleanupClientSslEngine(clientEngine);
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testCorrectlyCalculateSpaceForAlert(SSLEngineTestParam param) throws Exception {
        testCorrectlyCalculateSpaceForAlert(param, true);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testCorrectlyCalculateSpaceForAlertJDKCompatabilityModeOff(SSLEngineTestParam param) throws Exception {
        testCorrectlyCalculateSpaceForAlert(param, false);
    }

    private void testCorrectlyCalculateSpaceForAlert(SSLEngineTestParam param, boolean jdkCompatabilityMode)
            throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                                        .sslProvider(sslServerProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());

        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .sslProvider(sslClientProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());
        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            if (jdkCompatabilityMode) {
                clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
                serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            } else {
                clientEngine = wrapEngine(clientSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine());
                serverEngine = wrapEngine(serverSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine());
            }
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);

            // This should produce an alert
            clientEngine.closeOutbound();

            ByteBuffer empty = allocateBuffer(param.type(), 0);
            ByteBuffer dst = allocateBuffer(param.type(), clientEngine.getSession().getPacketBufferSize());
            // Limit to something that is guaranteed to be too small to hold an SSL Record.
            dst.limit(1);

            // As we called closeOutbound() before this should produce a BUFFER_OVERFLOW.
            SSLEngineResult result = clientEngine.wrap(empty, dst);
            assertEquals(SSLEngineResult.Status.BUFFER_OVERFLOW, result.getStatus());

            // This must calculate a length that can hold an alert at least (or more).
            dst.limit(dst.capacity());

            result = clientEngine.wrap(empty, dst);
            assertEquals(SSLEngineResult.Status.CLOSED, result.getStatus());

            // flip the buffer so we can verify we produced a full length buffer.
            dst.flip();

            int length = SslUtils.getEncryptedPacketLength(new ByteBuffer[] { dst }, 0);
            assertEquals(length, dst.remaining());
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            ssc.delete();
        }
    }

    @Override
    protected void mySetupMutualAuthServerInitSslHandler(SslHandler handler) {
        ReferenceCountedOpenSslEngine engine = (ReferenceCountedOpenSslEngine) handler.engine();
        engine.setVerify(SSL_CVERIFY_IGNORED, 1);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testWrapWithDifferentSizesTLSv1(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .sslProvider(sslClientProvider())
                                        .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                                        .sslProvider(sslServerProvider())
                                        .build());

        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "AES128-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "ECDHE-RSA-AES128-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "AECDH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "CAMELLIA128-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "SEED-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "RC4-MD5");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "AES256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "ADH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "EDH-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "ADH-RC4-MD5");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "IDEA-CBC-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "RC4-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "CAMELLIA256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "AECDH-RC4-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "ECDHE-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "ECDHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1, "ECDHE-RSA-RC4-SHA");
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testWrapWithDifferentSizesTLSv1_1(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .sslProvider(sslClientProvider())
                                        .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                                        .sslProvider(sslServerProvider())
                                        .build());

        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "ECDHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "AES256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "CAMELLIA256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "ECDHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "SEED-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "CAMELLIA128-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "IDEA-CBC-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "AECDH-RC4-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "ADH-RC4-MD5");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "RC4-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "ECDHE-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "EDH-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "AECDH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "ADH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_1, "DES-CBC3-SHA");
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testWrapWithDifferentSizesTLSv1_2(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build());

        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "AES128-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "ECDHE-RSA-AES128-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "AES128-GCM-SHA256");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "ECDHE-RSA-AES256-SHA384");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "AECDH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "AES256-GCM-SHA384");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "AES256-SHA256");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "ECDHE-RSA-AES128-GCM-SHA256");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "ECDHE-RSA-AES128-SHA256");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "CAMELLIA128-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "SEED-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "RC4-MD5");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "AES256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "ADH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "EDH-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "ADH-RC4-MD5");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "RC4-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "CAMELLIA256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "AES128-SHA256");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "AECDH-RC4-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "ECDHE-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "ECDHE-RSA-AES256-GCM-SHA384");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "ECDHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.TLS_v1_2, "ECDHE-RSA-RC4-SHA");
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testWrapWithDifferentSizesSSLv3(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .build());

        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "ADH-AES128-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "ADH-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "AECDH-AES128-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "AECDH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "CAMELLIA128-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "DHE-RSA-AES256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "SEED-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "RC4-MD5");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "ADH-AES256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "ADH-SEED-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "ADH-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "EDH-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "ADH-RC4-MD5");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "IDEA-CBC-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "DHE-RSA-AES128-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "RC4-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "CAMELLIA256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "AECDH-RC4-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "DHE-RSA-SEED-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "AECDH-AES256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "ECDHE-RSA-DES-CBC3-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "ADH-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "DHE-RSA-CAMELLIA256-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "DHE-RSA-CAMELLIA128-SHA");
        testWrapWithDifferentSizes(param, SslProtocols.SSL_v3, "ECDHE-RSA-RC4-SHA");
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testMultipleRecordsInOneBufferWithNonZeroPositionJDKCompatabilityModeOff(SSLEngineTestParam param)
            throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine());

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine());

        try {
            // Choose buffer size small enough that we can put multiple buffers into one buffer and pass it into the
            // unwrap call without exceed MAX_ENCRYPTED_PACKET_LENGTH.
            final int plainClientOutLen = 1024;
            ByteBuffer plainClientOut = allocateBuffer(param.type(), plainClientOutLen);
            ByteBuffer plainServerOut = allocateBuffer(param.type(), server.getSession().getApplicationBufferSize());

            ByteBuffer encClientToServer = allocateBuffer(param.type(), client.getSession().getPacketBufferSize());

            int positionOffset = 1;
            // We need to be able to hold 2 records + positionOffset
            ByteBuffer combinedEncClientToServer = allocateBuffer(
                    param.type(), encClientToServer.capacity() * 2 + positionOffset);
            combinedEncClientToServer.position(positionOffset);

            handshake(param.type(), param.delegate(), client, server);

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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testInputTooBigAndFillsUpBuffersJDKCompatabilityModeOff(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine());

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine());

        try {
            ByteBuffer plainClient = allocateBuffer(param.type(), MAX_PLAINTEXT_LENGTH + 100);
            ByteBuffer plainClient2 = allocateBuffer(param.type(), 512);
            ByteBuffer plainClientTotal =
                    allocateBuffer(param.type(), plainClient.capacity() + plainClient2.capacity());
            plainClientTotal.put(plainClient);
            plainClientTotal.put(plainClient2);
            plainClient.clear();
            plainClient2.clear();
            plainClientTotal.flip();

            // The capacity is designed to trigger an overflow condition.
            ByteBuffer encClientToServerTooSmall = allocateBuffer(param.type(), MAX_PLAINTEXT_LENGTH + 28);
            ByteBuffer encClientToServer = allocateBuffer(param.type(), client.getSession().getApplicationBufferSize());
            ByteBuffer encClientToServerTotal =
                    allocateBuffer(param.type(), client.getSession().getApplicationBufferSize() << 1);
            ByteBuffer plainServer = allocateBuffer(param.type(), server.getSession().getApplicationBufferSize() << 1);

            handshake(param.type(), param.delegate(), client, server);

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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testPartialPacketUnwrapJDKCompatabilityModeOff(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine());

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine());

        try {
            ByteBuffer plainClient = allocateBuffer(param.type(), 1024);
            ByteBuffer plainClient2 = allocateBuffer(param.type(), 512);
            ByteBuffer plainClientTotal =
                    allocateBuffer(param.type(), plainClient.capacity() + plainClient2.capacity());
            plainClientTotal.put(plainClient);
            plainClientTotal.put(plainClient2);
            plainClient.clear();
            plainClient2.clear();
            plainClientTotal.flip();

            ByteBuffer encClientToServer = allocateBuffer(param.type(), client.getSession().getPacketBufferSize());
            ByteBuffer plainServer = allocateBuffer(param.type(), server.getSession().getApplicationBufferSize());

            handshake(param.type(), param.delegate(), client, server);

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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testBufferUnderFlowAvoidedIfJDKCompatabilityModeOff(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();

        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(cert.cert())
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine client = wrapEngine(clientSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine());

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(cert.certificate(), cert.privateKey())
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        SSLEngine server = wrapEngine(serverSslCtx.newHandler(UnpooledByteBufAllocator.DEFAULT).engine());

        try {
            ByteBuffer plainClient = allocateBuffer(param.type(), 1024);
            plainClient.limit(plainClient.capacity());

            ByteBuffer encClientToServer = allocateBuffer(param.type(), client.getSession().getPacketBufferSize());
            ByteBuffer plainServer = allocateBuffer(param.type(), server.getSession().getApplicationBufferSize());

            handshake(param.type(), param.delegate(), client, server);

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

    private void testWrapWithDifferentSizes(SSLEngineTestParam param, String protocol, String cipher) throws Exception {
        assumeTrue(OpenSsl.SUPPORTED_PROTOCOLS_SET.contains(protocol));
        if (!OpenSsl.isCipherSuiteAvailable(cipher)) {
            return;
        }

        SSLEngine clientEngine = null;
        SSLEngine serverEngine = null;
        try {
            clientEngine = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            serverEngine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
            clientEngine.setEnabledCipherSuites(new String[] { cipher });
            clientEngine.setEnabledProtocols(new String[] { protocol });
            serverEngine.setEnabledCipherSuites(new String[] { cipher });
            serverEngine.setEnabledProtocols(new String[] { protocol });

            try {
                handshake(param.type(), param.delegate(), clientEngine, serverEngine);
            } catch (SSLException e) {
                if (e.getMessage().contains("unsupported protocol") ||
                        e.getMessage().contains("no protocols available")) {
                    throw new TestAbortedException(protocol + " not supported with cipher " + cipher, e);
                }
                throw e;
            }

            int srcLen = 64;
            do {
                testWrapDstBigEnough(param.type(), clientEngine, srcLen);
                srcLen += 64;
            } while (srcLen < MAX_PLAINTEXT_LENGTH);

            testWrapDstBigEnough(param.type(), clientEngine, MAX_PLAINTEXT_LENGTH);
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
        }
    }

    private void testWrapDstBigEnough(BufferType type, SSLEngine engine, int srcLen) throws SSLException {
        ByteBuffer src = allocateBuffer(type, srcLen);
        ByteBuffer dst = allocateBuffer(type, srcLen + unwrapEngine(engine).maxWrapOverhead());

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

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSNIMatchersDoesNotThrow(SSLEngineTestParam param) throws Exception {
        assumeTrue(PlatformDependent.javaVersion() >= 8);
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                                        .sslProvider(sslServerProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());

        SSLEngine engine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
        try {
            SSLParameters parameters = new SSLParameters();
            Java8SslTestUtils.setSNIMatcher(parameters, EmptyArrays.EMPTY_BYTES);
            engine.setSSLParameters(parameters);
        } finally {
            cleanupServerSslEngine(engine);
            ssc.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testSNIMatchersWithSNINameWithUnderscore(SSLEngineTestParam param) throws Exception {
        assumeTrue(PlatformDependent.javaVersion() >= 8);
        byte[] name = "rb8hx3pww30y3tvw0mwy.v1_1".getBytes(CharsetUtil.UTF_8);
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                                        .sslProvider(sslServerProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());

        SSLEngine engine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
        try {
            SSLParameters parameters = new SSLParameters();
            Java8SslTestUtils.setSNIMatcher(parameters, name);
            engine.setSSLParameters(parameters);
            assertFalse(unwrapEngine(engine).checkSniHostnameMatch("other".getBytes(CharsetUtil.UTF_8)));
        } finally {
            cleanupServerSslEngine(engine);
            ssc.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testAlgorithmConstraintsThrows(SSLEngineTestParam param) throws Exception {
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                                        .sslProvider(sslServerProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());

        final SSLEngine engine = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
        final SSLParameters parameters = new SSLParameters();
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
        try {
            assertThrows(IllegalArgumentException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    engine.setSSLParameters(parameters);
                }
            });
        } finally {
            cleanupServerSslEngine(engine);
            ssc.delete();
        }
    }

    private static void runTasksIfNeeded(SSLEngine engine) {
        if (engine.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            for (;;) {
                Runnable task = engine.getDelegatedTask();
                if (task == null) {
                    assertNotEquals(HandshakeStatus.NEED_TASK, engine.getHandshakeStatus());
                    break;
                }
                task.run();
            }
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testExtractMasterkeyWorksCorrectly(SSLEngineTestParam param) throws Exception {
        if (param.combo() != ProtocolCipherCombo.tlsv12()) {
            return;
        }
        SelfSignedCertificate cert = new SelfSignedCertificate();
        serverSslCtx = wrapContext(param, SslContextBuilder.forServer(cert.key(), cert.cert())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .sslProvider(SslProvider.OPENSSL).build());
        final SSLEngine serverEngine =
                wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                .trustManager(cert.certificate())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .sslProvider(SslProvider.OPENSSL).build());
        final SSLEngine clientEngine =
                wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        final String enabledCipher = "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256";
        try {
            //lets set the cipher suite to a specific one with DHE
            assumeTrue(Arrays.asList(clientEngine.getSupportedCipherSuites()).contains(enabledCipher),
                    "The diffie hellman cipher is not supported on your runtime.");

            //https://www.ietf.org/rfc/rfc5289.txt
            //For cipher suites ending with _SHA256, the PRF is the TLS PRF
            //[RFC5246] with SHA-256 as the hash function.  The MAC is HMAC
            //[RFC2104] with SHA-256 as the hash function.
            clientEngine.setEnabledCipherSuites(new String[] { enabledCipher });
            serverEngine.setEnabledCipherSuites(new String[] { enabledCipher });

            int appBufferMax = clientEngine.getSession().getApplicationBufferSize();
            int netBufferMax = clientEngine.getSession().getPacketBufferSize();

            /*
             * We'll make the input buffers a bit bigger than the max needed
             * size, so that unwrap()s following a successful data transfer
             * won't generate BUFFER_OVERFLOWS.
             */
            ByteBuffer clientIn = ByteBuffer.allocate(appBufferMax + 50);
            ByteBuffer serverIn = ByteBuffer.allocate(appBufferMax + 50);

            ByteBuffer cTOs = ByteBuffer.allocate(netBufferMax);
            ByteBuffer sTOc = ByteBuffer.allocate(netBufferMax);

            ByteBuffer clientOut = ByteBuffer.wrap("Hi Server, I'm Client".getBytes(CharsetUtil.US_ASCII));
            ByteBuffer serverOut = ByteBuffer.wrap("Hello Client, I'm Server".getBytes(CharsetUtil.US_ASCII));

            // This implementation is largely imitated from
            // https://docs.oracle.com/javase/8/docs/technotes/
            // guides/security/jsse/samples/sslengine/SSLEngineSimpleDemo.java
            // It has been simplified however without the need for running delegation tasks

            // Do handshake for SSL
            // A typical handshake will usually contain the following steps:
            // 1. wrap:     ClientHello
            // 2. unwrap:   ServerHello/Cert/ServerHelloDone
            // 3. wrap:     ClientKeyExchange
            // 4. wrap:     ChangeCipherSpec
            // 5. wrap:     Finished
            // 6. unwrap:   ChangeCipherSpec
            // 7. unwrap:   Finished

            //set a for loop; instead of a while loop to guarantee we quit out eventually
            boolean asserted = false;
            for (int i = 0; i < 1000; i++) {

                clientEngine.wrap(clientOut, cTOs);
                serverEngine.wrap(serverOut, sTOc);

                cTOs.flip();
                sTOc.flip();

                runTasksIfNeeded(clientEngine);
                runTasksIfNeeded(serverEngine);

                clientEngine.unwrap(sTOc, clientIn);
                serverEngine.unwrap(cTOs, serverIn);

                runTasksIfNeeded(clientEngine);
                runTasksIfNeeded(serverEngine);

                // check when the application data has fully been consumed and sent
                // for both the client and server
                if ((clientOut.limit() == serverIn.position()) &&
                        (serverOut.limit() == clientIn.position())) {
                    byte[] serverRandom = SSL.getServerRandom(unwrapEngine(serverEngine).sslPointer());
                    byte[] clientRandom = SSL.getClientRandom(unwrapEngine(clientEngine).sslPointer());
                    byte[] serverMasterKey = SSL.getMasterKey(unwrapEngine(serverEngine).sslPointer());
                    byte[] clientMasterKey = SSL.getMasterKey(unwrapEngine(clientEngine).sslPointer());

                    asserted = true;
                    assertArrayEquals(serverMasterKey, clientMasterKey);

                    // let us re-read the encrypted data and decrypt it ourselves!
                    cTOs.flip();
                    sTOc.flip();

                    // See https://tools.ietf.org/html/rfc5246#section-6.3:
                    // key_block = PRF(SecurityParameters.master_secret, "key expansion",
                    //                 SecurityParameters.server_random + SecurityParameters.client_random);
                    //
                    // partitioned:
                    //       client_write_MAC_secret[SecurityParameters.hash_size]
                    //       server_write_MAC_secret[SecurityParameters.hash_size]
                    //       client_write_key[SecurityParameters.key_material_length]
                    //       server_write_key[SecurityParameters.key_material_length]

                    int keySize = 16; // AES is 16 bytes or 128 bits
                    int macSize = 32; // SHA256 is 32 bytes or 256 bits
                    int keyBlockSize = (2 * keySize) + (2 * macSize);

                    byte[] seed = new byte[serverRandom.length + clientRandom.length];
                    System.arraycopy(serverRandom, 0, seed, 0, serverRandom.length);
                    System.arraycopy(clientRandom, 0, seed, serverRandom.length, clientRandom.length);
                    byte[] keyBlock = PseudoRandomFunction.hash(serverMasterKey,
                            "key expansion".getBytes(CharsetUtil.US_ASCII), seed, keyBlockSize, "HmacSha256");

                    int offset = 0;
                    byte[] clientWriteMac = Arrays.copyOfRange(keyBlock, offset, offset + macSize);
                    offset += macSize;

                    byte[] serverWriteMac = Arrays.copyOfRange(keyBlock, offset, offset + macSize);
                    offset += macSize;

                    byte[] clientWriteKey = Arrays.copyOfRange(keyBlock, offset, offset + keySize);
                    offset += keySize;

                    byte[] serverWriteKey = Arrays.copyOfRange(keyBlock, offset, offset + keySize);
                    offset += keySize;

                    //advance the cipher text by 5
                    //to take into account the TLS Record Header
                    cTOs.position(cTOs.position() + 5);

                    byte[] ciphertext = new byte[cTOs.remaining()];
                    cTOs.get(ciphertext);

                    //the initialization vector is the first 16 bytes (128 bits) of the payload
                    byte[] clientWriteIV = Arrays.copyOfRange(ciphertext, 0, 16);
                    ciphertext = Arrays.copyOfRange(ciphertext, 16, ciphertext.length);

                    SecretKeySpec secretKey = new SecretKeySpec(clientWriteKey, "AES");
                    final IvParameterSpec ivForCBC = new IvParameterSpec(clientWriteIV);
                    Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, ivForCBC);
                    byte[] plaintext = cipher.doFinal(ciphertext);
                    assertTrue(new String(plaintext).startsWith("Hi Server, I'm Client"));
                    break;
                } else {
                    cTOs.compact();
                    sTOc.compact();
                }
            }

            assertTrue(asserted, "The assertions were never executed.");
        } finally {
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
            cert.delete();
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testNoKeyFound(final SSLEngineTestParam param) throws Exception {
        checkShouldUseKeyManagerFactory();
        clientSslCtx = wrapContext(param, SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        final SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        serverSslCtx = wrapContext(param, SslContextBuilder
                .forServer(new X509ExtendedKeyManager() {
                    @Override
                    public String[] getClientAliases(String keyType, Principal[] issuers) {
                        return new String[0];
                    }

                    @Override
                    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                        return null;
                    }

                    @Override
                    public String[] getServerAliases(String keyType, Principal[] issuers) {
                        return new String[0];
                    }

                    @Override
                    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                        return null;
                    }

                    @Override
                    public X509Certificate[] getCertificateChain(String alias) {
                        return new X509Certificate[0];
                    }

                    @Override
                    public PrivateKey getPrivateKey(String alias) {
                        return null;
                    }
                })
                .sslProvider(sslServerProvider())
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());
        final SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            assertThrows(SSLException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    handshake(param.type(), param.delegate(), client, server);
                }
            });
        } finally {
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
        }
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Override
    public void testSessionLocalWhenNonMutualWithKeyManager(SSLEngineTestParam param) throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testSessionLocalWhenNonMutualWithKeyManager(param);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Override
    public void testSessionLocalWhenNonMutualWithoutKeyManager(SSLEngineTestParam param) throws Exception {
        // This only really works when the KeyManagerFactory is supported as otherwise we not really know when
        // we need to provide a cert.
        assumeTrue(OpenSsl.supportsKeyManagerFactory());
        super.testSessionLocalWhenNonMutualWithoutKeyManager(param);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testDefaultTLS1NotAcceptedByDefaultServer(SSLEngineTestParam param) throws Exception {
        testDefaultTLS1NotAcceptedByDefault(param, null, SslProtocols.TLS_v1);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testDefaultTLS11NotAcceptedByDefaultServer(SSLEngineTestParam param) throws Exception {
        testDefaultTLS1NotAcceptedByDefault(param, null, SslProtocols.TLS_v1_1);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testDefaultTLS1NotAcceptedByDefaultClient(SSLEngineTestParam param) throws Exception {
        testDefaultTLS1NotAcceptedByDefault(param, SslProtocols.TLS_v1, null);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testDefaultTLS11NotAcceptedByDefaultClient(SSLEngineTestParam param) throws Exception {
        testDefaultTLS1NotAcceptedByDefault(param, SslProtocols.TLS_v1_1, null);
    }

    private void testDefaultTLS1NotAcceptedByDefault(final SSLEngineTestParam param,
                                                     String clientProtocol, String serverProtocol) throws Exception {
        SslContextBuilder clientCtxBuilder = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .sslProvider(sslClientProvider())
                .sslContextProvider(clientSslContextProvider());
        if (clientProtocol != null) {
            clientCtxBuilder.protocols(clientProtocol);
        }
        clientSslCtx = wrapContext(param, clientCtxBuilder.build());
        SelfSignedCertificate ssc = new SelfSignedCertificate();

        SslContextBuilder serverCtxBuilder = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
                .sslProvider(sslServerProvider())
                .sslContextProvider(serverSslContextProvider());
        if (serverProtocol != null) {
            serverCtxBuilder.protocols(serverProtocol);
        }
        serverSslCtx = wrapContext(param, serverCtxBuilder.build());
        final SSLEngine client = wrapEngine(clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));
        final SSLEngine server = wrapEngine(serverSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            assertThrows(SSLHandshakeException.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    handshake(param.type(), param.delegate(), client, server);
                }
            });
        } finally {
            cleanupClientSslEngine(client);
            cleanupServerSslEngine(server);
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

    @Override
    protected SSLEngine wrapEngine(SSLEngine engine) {
        if (PlatformDependent.javaVersion() >= 8) {
            return Java8SslTestUtils.wrapSSLEngineForTesting(engine);
        }
        return engine;
    }

    ReferenceCountedOpenSslEngine unwrapEngine(SSLEngine engine) {
        if (engine instanceof JdkSslEngine) {
            return (ReferenceCountedOpenSslEngine) ((JdkSslEngine) engine).getWrappedEngine();
        }
        return (ReferenceCountedOpenSslEngine) engine;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected SslContext wrapContext(SSLEngineTestParam param, SslContext context) {
        if (context instanceof OpenSslContext) {
            if (param instanceof OpenSslEngineTestParam) {
                ((OpenSslContext) context).setUseTasks(((OpenSslEngineTestParam) param).useTasks);
            }
            // Explicit enable the session cache as its disabled by default on the client side.
            ((OpenSslContext) context).sessionContext().setSessionCacheEnabled(true);
        }
        return context;
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Override
    public void testSessionCache(SSLEngineTestParam param) throws Exception {
        assumeTrue(OpenSsl.isSessionCacheSupported());
        super.testSessionCache(param);
        assertSessionContext(clientSslCtx);
        assertSessionContext(serverSslCtx);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Override
    public void testSessionCacheTimeout(SSLEngineTestParam param) throws Exception {
        assumeTrue(OpenSsl.isSessionCacheSupported());
        super.testSessionCacheTimeout(param);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Override
    public void testSessionCacheSize(SSLEngineTestParam param) throws Exception {
        assumeTrue(OpenSsl.isSessionCacheSupported());
        super.testSessionCacheSize(param);
    }

    private static void assertSessionContext(SslContext context) {
        if (context == null) {
            return;
        }
        OpenSslSessionContext serverSessionCtx = (OpenSslSessionContext) context.sessionContext();
        assertTrue(serverSessionCtx.isSessionCacheEnabled());
        if (serverSessionCtx.getIds().hasMoreElements()) {
            serverSessionCtx.setSessionCacheEnabled(false);
            assertFalse(serverSessionCtx.getIds().hasMoreElements());
            assertFalse(serverSessionCtx.isSessionCacheEnabled());
        }
    }

    @Override
    protected void assertSessionReusedForEngine(SSLEngine clientEngine, SSLEngine serverEngine, boolean reuse) {
        assertEquals(reuse, unwrapEngine(clientEngine).isSessionReused());
        assertEquals(reuse, unwrapEngine(serverEngine).isSessionReused());
    }

    @Override
    protected boolean isSessionMaybeReused(SSLEngine engine) {
        return unwrapEngine(engine).isSessionReused();
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    @Override
    public void testRSASSAPSS(SSLEngineTestParam param) throws Exception {
        checkShouldUseKeyManagerFactory();
        super.testRSASSAPSS(param);
    }
}
