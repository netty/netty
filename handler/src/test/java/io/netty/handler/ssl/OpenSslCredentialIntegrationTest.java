/*
 * Copyright 2025 The Netty Project
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

import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.pkitesting.CertificateBuilder;
import io.netty.pkitesting.X509Bundle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static io.netty.buffer.UnpooledByteBufAllocator.DEFAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link OpenSslCredential} with actual TLS handshakes.
 */
public class OpenSslCredentialIntegrationTest {

    private static X509Bundle rsaCert;
    private static X509Bundle ecdsaCert;

    private final List<SslContext> contextsToRelease = new ArrayList<>();
    private final List<OpenSslCredential> credentialsToRelease = new ArrayList<>();

    @BeforeAll
    public static void setUp() throws Exception {
        OpenSsl.ensureAvailability();
        assumeTrue(OpenSsl.isBoringSSL(), "SSL_CREDENTIAL API is only supported with BoringSSL");

        // Create RSA certificate
        rsaCert = new CertificateBuilder()
                .subject("cn=rsa.localhost")
                .keyAlgorithm("RSA")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();

        // Create ECDSA certificate
        ecdsaCert = new CertificateBuilder()
                .subject("cn=ecdsa.localhost")
                .keyAlgorithm("EC")
                .setIsCertificateAuthority(true)
                .buildSelfSigned();
    }

    @AfterAll
    public static void cleanUp() throws InterruptedException {
        rsaCert = null;
        ecdsaCert = null;
        // Force garbage collection to clean up certificate resources
        System.gc();
        Thread.sleep(100);
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        for (SslContext context : contextsToRelease) {
            if (context instanceof ReferenceCountedOpenSslContext) {
                ((ReferenceCountedOpenSslContext) context).release();
            }
        }
        contextsToRelease.clear();

        for (OpenSslCredential credential : credentialsToRelease) {
            credential.release();
        }
        credentialsToRelease.clear();

        // Force garbage collection to clean up any remaining native resources
        System.gc();
        Thread.sleep(10);
    }

    @Test
    public void testHandshakeWithMultipleCredentials() throws Exception {
        // Create both RSA and ECDSA credentials
        OpenSslCredential rsaCredential = OpenSslCredentialBuilder
                .forX509(rsaCert.getKeyPair().getPrivate(), rsaCert.getCertificate())
                .build();

        OpenSslCredential ecdsaCredential = OpenSslCredentialBuilder
                .forX509(ecdsaCert.getKeyPair().getPrivate(), ecdsaCert.getCertificate())
                .build();

        // Server context with both credentials
        SslContext serverContext = SslContextBuilder.forServer(rsaCert.getKeyPair().getPrivate(),
                        rsaCert.getCertificate())
                .sslProvider(SslProvider.OPENSSL_REFCNT)
                .credentials(rsaCredential, ecdsaCredential)
                .build();
        contextsToRelease.add(serverContext);

        SslContext clientContext = SslContextBuilder.forClient()
                .sslProvider(SslProvider.OPENSSL_REFCNT)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        contextsToRelease.add(clientContext);

        ReferenceCountedOpenSslEngine serverEngine =
                (ReferenceCountedOpenSslEngine) serverContext.newEngine(DEFAULT);
        SSLEngine clientEngine = clientContext.newEngine(DEFAULT);

        try {
            performHandshake(clientEngine, serverEngine);

            // Handshake should succeed with credentials
            assertTrue(serverEngine.getSession().isValid());

            OpenSslCredential selectedCredential = serverEngine.getSelectedCredential();
            assertEquals(OpenSslCredential.CredentialType.X509, selectedCredential.type());
        } finally {
            cleanupEngine(serverEngine);
            cleanupEngine(clientEngine);
        }
    }

    @Test
    public void testEngineAddCredential() throws Exception {
        OpenSslCredential credential = OpenSslCredentialBuilder
                .forX509(rsaCert.getKeyPair().getPrivate(), rsaCert.getCertificate())
                .build();
        credentialsToRelease.add(credential);

        SslContext serverContext = SslContextBuilder.forServer(rsaCert.getKeyPair().getPrivate(),
                        rsaCert.getCertificate())
                .sslProvider(SslProvider.OPENSSL_REFCNT)
                .build();
        contextsToRelease.add(serverContext);

        ReferenceCountedOpenSslEngine serverEngine =
                (ReferenceCountedOpenSslEngine) serverContext.newEngine(DEFAULT);

        // Add credential at engine level
        serverEngine.addCredential(credential);

        SslContext clientContext = SslContextBuilder.forClient()
                .sslProvider(SslProvider.OPENSSL_REFCNT)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        contextsToRelease.add(clientContext);

        SSLEngine clientEngine = clientContext.newEngine(DEFAULT);

        try {
            performHandshake(clientEngine, serverEngine);
            assertTrue(serverEngine.getSession().isValid());
        } finally {
            cleanupEngine(serverEngine);
            cleanupEngine(clientEngine);
        }
    }

    // Helper method to properly cleanup an SSLEngine
    private void cleanupEngine(SSLEngine engine) {
        try {
            engine.closeOutbound();
            engine.closeInbound();
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        if (engine instanceof ReferenceCountedOpenSslEngine) {
            ((ReferenceCountedOpenSslEngine) engine).release();
        }
    }

    // Helper method to perform a complete TLS handshake
    private void performHandshake(SSLEngine clientEngine, SSLEngine serverEngine) throws SSLException {
        clientEngine.setUseClientMode(true);
        serverEngine.setUseClientMode(false);

        ByteBuffer clientOut = ByteBuffer.allocate(clientEngine.getSession().getPacketBufferSize());
        ByteBuffer clientIn = ByteBuffer.allocate(clientEngine.getSession().getApplicationBufferSize());
        ByteBuffer serverOut = ByteBuffer.allocate(serverEngine.getSession().getPacketBufferSize());
        ByteBuffer serverIn = ByteBuffer.allocate(serverEngine.getSession().getApplicationBufferSize());

        ByteBuffer clientToServer = ByteBuffer.allocate(65536);
        ByteBuffer serverToClient = ByteBuffer.allocate(65536);

        clientEngine.beginHandshake();
        serverEngine.beginHandshake();

        SSLEngineResult.HandshakeStatus clientStatus = clientEngine.getHandshakeStatus();
        SSLEngineResult.HandshakeStatus serverStatus = serverEngine.getHandshakeStatus();

        int iterations = 0;
        int maxIterations = 100; // Prevent infinite loops

        while (iterations++ < maxIterations &&
                !((clientStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING ||
                   clientStatus == SSLEngineResult.HandshakeStatus.FINISHED) &&
                  (serverStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING ||
                   serverStatus == SSLEngineResult.HandshakeStatus.FINISHED))) {

            // Client handshake
            if (clientStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                clientOut.clear();
                SSLEngineResult result = clientEngine.wrap(clientIn, clientOut);
                clientStatus = result.getHandshakeStatus();
                clientOut.flip();
                clientToServer.put(clientOut);
            } else if (clientStatus == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                serverToClient.flip();
                SSLEngineResult result = clientEngine.unwrap(serverToClient, clientIn);
                clientStatus = result.getHandshakeStatus();
                serverToClient.compact();
            } else if (clientStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                Runnable task;
                while ((task = clientEngine.getDelegatedTask()) != null) {
                    task.run();
                }
                clientStatus = clientEngine.getHandshakeStatus();
            }

            // Server handshake
            if (serverStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
                serverOut.clear();
                SSLEngineResult result = serverEngine.wrap(serverIn, serverOut);
                serverStatus = result.getHandshakeStatus();
                serverOut.flip();
                serverToClient.put(serverOut);
            } else if (serverStatus == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
                clientToServer.flip();
                SSLEngineResult result = serverEngine.unwrap(clientToServer, serverIn);
                serverStatus = result.getHandshakeStatus();
                clientToServer.compact();
            } else if (serverStatus == SSLEngineResult.HandshakeStatus.NEED_TASK) {
                Runnable task;
                while ((task = serverEngine.getDelegatedTask()) != null) {
                    task.run();
                }
                serverStatus = serverEngine.getHandshakeStatus();
            }
        }

        if (iterations >= maxIterations) {
            throw new SSLException("Handshake did not complete within " + maxIterations + " iterations");
        }
    }
}
