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
import io.netty.handler.ssl.util.CachedSelfSignedCertificate;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.ByteArrayInputStream;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class SslContextBuilderTest {

    @Test
    public void testClientContextFromFileJdk() throws Exception {
        testClientContextFromFile(SslProvider.JDK);
    }

    @Test
    public void testClientContextFromFileOpenssl() throws Exception {
        OpenSsl.ensureAvailability();
        testClientContextFromFile(SslProvider.OPENSSL);
    }

    @Test
    public void testClientContextJdk() throws Exception {
        testClientContext(SslProvider.JDK);
    }

    @Test
    public void testClientContextOpenssl() throws Exception {
        OpenSsl.ensureAvailability();
        testClientContext(SslProvider.OPENSSL);
    }

    @Test
    public void testCombinedPemFileClientContextJdk() throws Exception {
        testServerContextWithCombinedCertAndKeyInPem(SslProvider.JDK);
    }

    @Test
    public void testCombinedPemFileClientContextOpenssl() throws Exception {
        OpenSsl.ensureAvailability();
        testServerContextWithCombinedCertAndKeyInPem(SslProvider.OPENSSL);
    }

    @Test
    public void testKeyStoreTypeJdk() throws Exception {
        testKeyStoreType(SslProvider.JDK);
    }

    @Test
    public void testKeyStoreTypeOpenssl() throws Exception {
        OpenSsl.ensureAvailability();
        testKeyStoreType(SslProvider.OPENSSL);
    }

    @Test
    public void testServerContextFromFileJdk() throws Exception {
        testServerContextFromFile(SslProvider.JDK);
    }

    @Test
    public void testServerContextFromFileOpenssl() throws Exception {
        OpenSsl.ensureAvailability();
        testServerContextFromFile(SslProvider.OPENSSL);
    }

    @Test
    public void testServerContextJdk() throws Exception {
        testServerContext(SslProvider.JDK);
    }

    @Test
    public void testServerContextOpenssl() throws Exception {
        OpenSsl.ensureAvailability();
        testServerContext(SslProvider.OPENSSL);
    }

    @Test
    public void testContextFromManagersJdk() throws Exception {
        testContextFromManagers(SslProvider.JDK);
    }

    @Test
    public void testContextFromManagersOpenssl() throws Exception {
        OpenSsl.ensureAvailability();
        assumeTrue(OpenSsl.useKeyManagerFactory());
        testContextFromManagers(SslProvider.OPENSSL);
    }

    @Test
    public void testUnsupportedPrivateKeyFailsFastForServer() {
        assumeTrue(OpenSsl.isBoringSSL());
        testUnsupportedPrivateKeyFailsFast(true);
    }

    @Test
    public void testUnsupportedPrivateKeyFailsFastForClient() {
        assumeTrue(OpenSsl.isBoringSSL());
        testUnsupportedPrivateKeyFailsFast(false);
    }

    private static void testUnsupportedPrivateKeyFailsFast(boolean server) {
        assumeTrue(OpenSsl.isBoringSSL());
        String cert = "-----BEGIN CERTIFICATE-----\n" +
                "MIICODCCAY2gAwIBAgIEXKTrajAKBggqhkjOPQQDBDBUMQswCQYDVQQGEwJVUzEM\n" +
                "MAoGA1UECAwDTi9hMQwwCgYDVQQHDANOL2ExDDAKBgNVBAoMA04vYTEMMAoGA1UE\n" +
                "CwwDTi9hMQ0wCwYDVQQDDARUZXN0MB4XDTE5MDQwMzE3MjA0MloXDTIwMDQwMjE3\n" +
                "MjA0MlowVDELMAkGA1UEBhMCVVMxDDAKBgNVBAgMA04vYTEMMAoGA1UEBwwDTi9h\n" +
                "MQwwCgYDVQQKDANOL2ExDDAKBgNVBAsMA04vYTENMAsGA1UEAwwEVGVzdDCBpzAQ\n" +
                "BgcqhkjOPQIBBgUrgQQAJwOBkgAEBPYWoTjlS2pCMGEM2P8qZnmURWA5e7XxPfIh\n" +
                "HA876sjmgjJluPgT0OkweuxI4Y/XjzcPnnEBONgzAV1X93UmXdtRiIau/zvsAeFb\n" +
                "j/q+6sfj1jdnUk6QsMx22kAwplXHmdz1z5ShXQ7mDZPxDbhCPEAUXzIzOqvWIZyA\n" +
                "HgFxZXmQKEhExA8nxgSIvzQ3ucMwMAoGCCqGSM49BAMEA4GYADCBlAJIAdPD6jaN\n" +
                "vGxkxcsIbcHn2gSfP1F1G8iNJYrXIN91KbQm8OEp4wxqnBwX8gb/3rmSoEhIU/te\n" +
                "CcHuFs0guBjfgRWtJ/eDnKB/AkgDbkqrB5wqJFBmVd/rJ5QdwUVNuGP/vDjFVlb6\n" +
                "Esny6//gTL7jYubLUKHOPIMftCZ2Jn4b+5l0kAs62HD5XkZLPDTwRbf7VCE=\n" +
                "-----END CERTIFICATE-----";
        String key = "-----BEGIN PRIVATE KEY-----\n" +
                "MIIBCQIBADAQBgcqhkjOPQIBBgUrgQQAJwSB8TCB7gIBAQRIALNClTXqQWWlYDHw\n" +
                "LjNxXpLk17iPepkmablhbxmYX/8CNzoz1o2gcUidoIO2DM9hm7adI/W31EOmSiUJ\n" +
                "+UsC/ZH3i2qr0wn+oAcGBSuBBAAnoYGVA4GSAAQE9hahOOVLakIwYQzY/ypmeZRF\n" +
                "YDl7tfE98iEcDzvqyOaCMmW4+BPQ6TB67Ejhj9ePNw+ecQE42DMBXVf3dSZd21GI\n" +
                "hq7/O+wB4VuP+r7qx+PWN2dSTpCwzHbaQDCmVceZ3PXPlKFdDuYNk/ENuEI8QBRf\n" +
                "MjM6q9YhnIAeAXFleZAoSETEDyfGBIi/NDe5wzA=\n" +
                "-----END PRIVATE KEY-----";
        ByteArrayInputStream certStream = new ByteArrayInputStream(cert.getBytes(CharsetUtil.US_ASCII));
        ByteArrayInputStream keyStream = new ByteArrayInputStream(key.getBytes(CharsetUtil.US_ASCII));
        final SslContextBuilder builder;
        try {
            if (server) {
                builder = SslContextBuilder.forServer(certStream, keyStream, null);
            } else {
                builder = SslContextBuilder.forClient().keyManager(certStream, keyStream, null);
            }
        } catch (IllegalArgumentException e) {
            assumeFalse("Input stream not contain valid certificates.".equals(e.getMessage())
                        && e.getCause() != null
                        && "java.io.IOException: Unknown named curve: 1.3.132.0.39".equals(
                                e.getCause().getMessage()),
                        "Cannot test that SslProvider rejects certificates with curve " +
                        "1.3.132.0.39 because the key manager does not know the curve either.");
            throw e;
        }
        assertThrows(SSLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                builder.sslProvider(SslProvider.OPENSSL).build();
            }
        });
    }

    private void testServerContextWithCombinedCertAndKeyInPem(SslProvider provider) throws SSLException {
        String pem = "-----BEGIN CERTIFICATE-----\n" +
                     "MIIB1jCCAX0CCQDq4PSOirh7MDAJBgcqhkjOPQQBMHIxCzAJBgNVBAYTAlVTMQsw\n" +
                     "CQYDVQQIDAJDQTEMMAoGA1UEBwwDRm9vMQwwCgYDVQQKDANCYXIxDDAKBgNVBAsM\n" +
                     "A0JhejEQMA4GA1UEAwwHQmFyLmNvbTEaMBgGCSqGSIb3DQEJARYLZm9vQGJhci5j\n" +
                     "b20wHhcNMjIxMDAyMTYzODAyWhcNMjIxMjAxMTYzODAyWjB2MQswCQYDVQQGEwJV\n" +
                     "UzELMAkGA1UECAwCQ0ExDDAKBgNVBAcMA0ZvbzEMMAoGA1UECgwDQmFyMQwwCgYD\n" +
                     "VQQLDANiYXoxFDASBgNVBAMMC2Jhci5iYXIuYmF6MRowGAYJKoZIhvcNAQkBFgtm\n" +
                     "b29AYmFyLmNvbTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABHiEmjPEqQbqXYMB\n" +
                     "nAPOv24rJf6MhTwHB0QC1suZ9q9XFUkalnqGryqf/emHs81RsXWKz4sCsbIJkmHz\n" +
                     "H8HYhmkwCQYHKoZIzj0EAQNIADBFAiBCgzxZ5qviemPdejt2WazSgwNJTbirzoQa\n" +
                     "FMv2XFTTCwIhANS3fZ8BulbYkdRWVEFwm2FGotqLfC60JA/gg/brlWSP\n" +
                     "-----END CERTIFICATE-----\n" +
                     "-----BEGIN EC PRIVATE KEY-----\n" +
                     "MHcCAQEEIF8RlaD0JX8u2Lryq1+AbYfDaTBPJnPSA8+N2L12YuuUoAoGCCqGSM49\n" +
                     "AwEHoUQDQgAEeISaM8SpBupdgwGcA86/bisl/oyFPAcHRALWy5n2r1cVSRqWeoav\n" +
                     "Kp/96YezzVGxdYrPiwKxsgmSYfMfwdiGaQ==\n" +
                     "-----END EC PRIVATE KEY-----";

        ByteArrayInputStream certStream = new ByteArrayInputStream(pem.getBytes(CharsetUtil.US_ASCII));
        ByteArrayInputStream keyStream = new ByteArrayInputStream(pem.getBytes(CharsetUtil.US_ASCII));

        SslContext context = SslContextBuilder.forServer(certStream, keyStream, null)
                                              .sslProvider(provider)
                                              .clientAuth(ClientAuth.OPTIONAL)
                                              .build();

        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        assertTrue(engine.getWantClientAuth());
        assertFalse(engine.getNeedClientAuth());
        engine.closeInbound();
        engine.closeOutbound();
    }

    @Test
    public void testInvalidCipherJdk() throws Exception {
        OpenSsl.ensureAvailability();
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                testInvalidCipher(SslProvider.JDK);
            }
        });
    }

    @Test
    public void testInvalidCipherOpenSSL() throws Exception {
        OpenSsl.ensureAvailability();
        try {
            // This may fail or not depending on the OpenSSL version used
            // See https://github.com/openssl/openssl/issues/7196
            testInvalidCipher(SslProvider.OPENSSL);
            if (!OpenSsl.versionString().contains("1.1.1")) {
                fail();
            }
        } catch (SSLException expected) {
            // ok
        }
    }

    @Test
    public void testServerContextWithSecureRandom() throws Exception {
        testServerContextWithSecureRandom(SslProvider.JDK, new SpySecureRandom());
    }

    @Test
    public void testClientContextWithSecureRandom() throws Exception {
        testClientContextWithSecureRandom(SslProvider.JDK, new SpySecureRandom());
    }

    private static void testKeyStoreType(SslProvider provider) throws Exception {
        SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
        SslContextBuilder builder = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                .sslProvider(provider)
                .keyStoreType("PKCS12");
        SslContext context = builder.build();
        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        engine.closeInbound();
        engine.closeOutbound();
    }

    private static void testInvalidCipher(SslProvider provider) throws Exception {
        SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
        SslContextBuilder builder = SslContextBuilder.forClient()
                .sslProvider(provider)
                .ciphers(Collections.singleton("SOME_INVALID_CIPHER"))
                .keyManager(cert.certificate(),
                        cert.privateKey())
                .trustManager(cert.certificate());
        SslContext context = builder.build();
        context.newEngine(UnpooledByteBufAllocator.DEFAULT);
    }

    private static void testClientContextFromFile(SslProvider provider) throws Exception {
        SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
        SslContextBuilder builder = SslContextBuilder.forClient()
                                                     .sslProvider(provider)
                                                     .keyManager(cert.certificate(),
                                                             cert.privateKey())
                                                     .trustManager(cert.certificate())
                                                     .clientAuth(ClientAuth.OPTIONAL);
        SslContext context = builder.build();
        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        assertFalse(engine.getWantClientAuth());
        assertFalse(engine.getNeedClientAuth());
        engine.closeInbound();
        engine.closeOutbound();
    }

    private static void testClientContext(SslProvider provider) throws Exception {
        SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
        SslContextBuilder builder = SslContextBuilder.forClient()
                                                     .sslProvider(provider)
                                                     .keyManager(cert.key(), cert.cert())
                                                     .trustManager(cert.cert())
                                                     .clientAuth(ClientAuth.OPTIONAL);
        SslContext context = builder.build();
        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        assertFalse(engine.getWantClientAuth());
        assertFalse(engine.getNeedClientAuth());
        engine.closeInbound();
        engine.closeOutbound();
    }

    private static void testServerContextFromFile(SslProvider provider) throws Exception {
        SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
        SslContextBuilder builder = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                                                     .sslProvider(provider)
                                                     .trustManager(cert.certificate())
                                                     .clientAuth(ClientAuth.OPTIONAL);
        SslContext context = builder.build();
        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        assertTrue(engine.getWantClientAuth());
        assertFalse(engine.getNeedClientAuth());
        engine.closeInbound();
        engine.closeOutbound();
    }

    private static void testServerContext(SslProvider provider) throws Exception {
        SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
        SslContextBuilder builder = SslContextBuilder.forServer(cert.key(), cert.cert())
                                                     .sslProvider(provider)
                                                     .trustManager(cert.cert())
                                                     .clientAuth(ClientAuth.REQUIRE);
        SslContext context = builder.build();
        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        assertFalse(engine.getWantClientAuth());
        assertTrue(engine.getNeedClientAuth());
        engine.closeInbound();
        engine.closeOutbound();
    }

    private static void testServerContextWithSecureRandom(SslProvider provider,
                                                          SpySecureRandom secureRandom) throws Exception {
        SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
        SslContextBuilder builder = SslContextBuilder.forServer(cert.key(), cert.cert())
                .sslProvider(provider)
                .secureRandom(secureRandom)
                .trustManager(cert.cert())
                .clientAuth(ClientAuth.REQUIRE);
        SslContext context = builder.build();
        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        assertFalse(engine.getWantClientAuth());
        assertTrue(engine.getNeedClientAuth());
        assertTrue(secureRandom.getCount() > 0);
        engine.closeInbound();
        engine.closeOutbound();
    }

    private static void testClientContextWithSecureRandom(SslProvider provider,
                                                          SpySecureRandom secureRandom) throws Exception {
        SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
        SslContextBuilder builder = SslContextBuilder.forClient()
                .sslProvider(provider)
                .secureRandom(secureRandom)
                .keyManager(cert.key(), cert.cert())
                .trustManager(cert.cert())
                .clientAuth(ClientAuth.OPTIONAL);
        SslContext context = builder.build();
        SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        assertFalse(engine.getWantClientAuth());
        assertFalse(engine.getNeedClientAuth());
        assertTrue(secureRandom.getCount() > 0);
        engine.closeInbound();
        engine.closeOutbound();
    }

    private static void testContextFromManagers(SslProvider provider) throws Exception {
        final SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
        KeyManager customKeyManager = new X509ExtendedKeyManager() {
            @Override
            public String[] getClientAliases(String s,
                                             Principal[] principals) {
                return new String[0];
            }

            @Override
            public String chooseClientAlias(String[] strings,
                                            Principal[] principals,
                                            Socket socket) {
                return "cert_sent_to_server";
            }

            @Override
            public String[] getServerAliases(String s,
                                             Principal[] principals) {
                return new String[0];
            }

            @Override
            public String chooseServerAlias(String s,
                                            Principal[] principals,
                                            Socket socket) {
                return null;
            }

            @Override
            public X509Certificate[] getCertificateChain(String s) {
                X509Certificate[] certificates = new X509Certificate[1];
                certificates[0] = cert.cert();
                return new X509Certificate[0];
            }

            @Override
            public PrivateKey getPrivateKey(String s) {
                return cert.key();
            }
        };
        TrustManager customTrustManager = new X509ExtendedTrustManager() {
            @Override
            public void checkClientTrusted(
                    X509Certificate[] x509Certificates, String s,
                    Socket socket) throws CertificateException { }

            @Override
            public void checkServerTrusted(
                    X509Certificate[] x509Certificates, String s,
                    Socket socket) throws CertificateException { }

            @Override
            public void checkClientTrusted(
                    X509Certificate[] x509Certificates, String s,
                    SSLEngine sslEngine) throws CertificateException { }

            @Override
            public void checkServerTrusted(
                    X509Certificate[] x509Certificates, String s,
                    SSLEngine sslEngine) throws CertificateException { }

            @Override
            public void checkClientTrusted(
                    X509Certificate[] x509Certificates, String s)
                    throws CertificateException { }

            @Override
            public void checkServerTrusted(
                    X509Certificate[] x509Certificates, String s)
                    throws CertificateException { }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        SslContextBuilder client_builder = SslContextBuilder.forClient()
                                                     .sslProvider(provider)
                                                     .keyManager(customKeyManager)
                                                     .trustManager(customTrustManager)
                                                     .clientAuth(ClientAuth.OPTIONAL);
        SslContext client_context = client_builder.build();
        SSLEngine client_engine = client_context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        assertFalse(client_engine.getWantClientAuth());
        assertFalse(client_engine.getNeedClientAuth());
        client_engine.closeInbound();
        client_engine.closeOutbound();
        SslContextBuilder server_builder = SslContextBuilder.forServer(customKeyManager)
                                                     .sslProvider(provider)
                                                     .trustManager(customTrustManager)
                                                     .clientAuth(ClientAuth.REQUIRE);
        SslContext server_context = server_builder.build();
        SSLEngine server_engine = server_context.newEngine(UnpooledByteBufAllocator.DEFAULT);
        assertFalse(server_engine.getWantClientAuth());
        assertTrue(server_engine.getNeedClientAuth());
        server_engine.closeInbound();
        server_engine.closeOutbound();
    }

    private static final class SpySecureRandom extends SecureRandom {
        private int count;

        @Override
        public int nextInt() {
            count++;
            return super.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            count++;
            return super.nextInt(bound);
        }

        @Override
        public long nextLong() {
            count++;
            return super.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            count++;
            return super.nextBoolean();
        }

        @Override
        public float nextFloat() {
            count++;
            return super.nextFloat();
        }

        @Override
        public double nextDouble() {
            count++;
            return super.nextDouble();
        }

        @Override
        public double nextGaussian() {
            count++;
            return super.nextGaussian();
        }

        public int getCount() {
            return count;
        }
    }
}
