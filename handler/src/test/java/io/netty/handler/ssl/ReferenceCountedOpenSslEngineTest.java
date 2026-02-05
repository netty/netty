/*
 * Copyright 2016 The Netty Project
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
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ReferenceCountedOpenSslEngineTest extends OpenSslEngineTest {

    private static X509Bundle rsaCert;
    private static X509Bundle ecdsaCert;
    private static OpenSslCredential rsaCredential;
    private static OpenSslCredential ecdsaCredential;

    @BeforeAll
    public static void setUpCredentialCerts() throws Exception {
        assumeTrue(OpenSslCredential.isAvailable());

        // Create RSA certificate for credential tests
        rsaCert = new CertificateBuilder()
                .subject("cn=rsa.localhost")
                .rsa2048()
                .setIsCertificateAuthority(true)
                .buildSelfSigned();

        // Create ECDSA certificate for credential tests
        ecdsaCert = new CertificateBuilder()
                .subject("cn=ecdsa.localhost")
                .ecp256()
                .setIsCertificateAuthority(true)
                .buildSelfSigned();

        // Create credentials
        rsaCredential = OpenSslCredentialBuilder
                .forX509(rsaCert.getKeyPair().getPrivate(), rsaCert.getCertificate())
                .build();

        ecdsaCredential = OpenSslCredentialBuilder
                .forX509(ecdsaCert.getKeyPair().getPrivate(), ecdsaCert.getCertificate())
                .build();
    }

    @AfterAll
    public static void tearDownCredentials() {
        if (rsaCredential != null) {
            rsaCredential.release();
        }
        if (ecdsaCredential != null) {
            ecdsaCredential.release();
        }
    }

    @Override
    protected SslProvider sslClientProvider() {
        return SslProvider.OPENSSL_REFCNT;
    }

    @Override
    protected SslProvider sslServerProvider() {
        return SslProvider.OPENSSL_REFCNT;
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testNotLeakOnException(SSLEngineTestParam param) throws Exception {
        clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .sslProvider(sslClientProvider())
                                        .protocols(param.protocols())
                                        .ciphers(param.ciphers())
                                        .build());

        assertThrows(NullPointerException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                clientSslCtx.newEngine(null);
            }
        });
    }

    @Override
    protected SslContext wrapContext(SSLEngineTestParam param, SslContext context) {
        return OpenSslEngineTestParam.wrapContext(param, context);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void parentContextIsRetainedByChildEngines(SSLEngineTestParam param) throws Exception {
        SslContext clientSslCtx = wrapContext(param, SslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .sslProvider(sslClientProvider())
            .protocols(param.protocols())
            .ciphers(param.ciphers())
            .build());

        SSLEngine engine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
        assertEquals(ReferenceCountUtil.refCnt(clientSslCtx), 2);

        cleanupClientSslContext(clientSslCtx);
        assertEquals(ReferenceCountUtil.refCnt(clientSslCtx), 1);

        cleanupClientSslEngine(engine);
        assertEquals(ReferenceCountUtil.refCnt(clientSslCtx), 0);
    }

    @MethodSource("newTestParams")
    @ParameterizedTest
    public void testHandshakeWithMultipleCredentials(SSLEngineTestParam param) throws Exception {
        assumeTrue(OpenSsl.isBoringSSL(), "SSL_CREDENTIAL API is only supported with BoringSSL");

        // Server context with both credentials
        SslContext serverSslContext = wrapContext(param,
                SslContextBuilder.forServer(rsaCert.getKeyPair().getPrivate(), rsaCert.getCertificate())
                        .sslProvider(SslProvider.OPENSSL_REFCNT)
                        .credentials(rsaCredential, ecdsaCredential)
                        .protocols(param.protocols())
                        .ciphers(param.ciphers())
                        .build());

        SslContext clientSslContext = wrapContext(param, SslContextBuilder.forClient()
                .sslProvider(SslProvider.OPENSSL_REFCNT)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .protocols(param.protocols())
                .ciphers(param.ciphers())
                .build());

        SSLEngine clientEngine = wrapEngine(clientSslContext.newEngine(UnpooledByteBufAllocator.DEFAULT));
        SSLEngine serverEngine = wrapEngine(serverSslContext.newEngine(UnpooledByteBufAllocator.DEFAULT));

        try {
            // Perform handshake using base class helper
            handshake(param.type(), param.delegate(), clientEngine, serverEngine);

            // Verify handshake succeeded
            assertTrue(serverEngine.getSession().isValid());
        } finally {
            cleanupClientSslContext(clientSslContext);
            cleanupServerSslContext(serverSslContext);
            cleanupClientSslEngine(clientEngine);
            cleanupServerSslEngine(serverEngine);
        }
    }
}
