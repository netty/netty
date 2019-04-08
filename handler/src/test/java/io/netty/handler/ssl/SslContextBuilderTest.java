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
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.CharsetUtil;
import org.junit.Assume;
import org.junit.Test;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import java.io.ByteArrayInputStream;
import java.util.Collections;

import static org.junit.Assert.*;

public class SslContextBuilderTest {

    @Test
    public void testClientContextFromFileJdk() throws Exception {
        testClientContextFromFile(SslProvider.JDK);
    }

    @Test
    public void testClientContextFromFileOpenssl() throws Exception {
        Assume.assumeTrue(OpenSsl.isAvailable());
        testClientContextFromFile(SslProvider.OPENSSL);
    }

    @Test
    public void testClientContextJdk() throws Exception {
        testClientContext(SslProvider.JDK);
    }

    @Test
    public void testClientContextOpenssl() throws Exception {
        Assume.assumeTrue(OpenSsl.isAvailable());
        testClientContext(SslProvider.OPENSSL);
    }

    @Test
    public void testServerContextFromFileJdk() throws Exception {
        testServerContextFromFile(SslProvider.JDK);
    }

    @Test
    public void testServerContextFromFileOpenssl() throws Exception {
        Assume.assumeTrue(OpenSsl.isAvailable());
        testServerContextFromFile(SslProvider.OPENSSL);
    }

    @Test
    public void testServerContextJdk() throws Exception {
        testServerContext(SslProvider.JDK);
    }

    @Test
    public void testServerContextOpenssl() throws Exception {
        Assume.assumeTrue(OpenSsl.isAvailable());
        testServerContext(SslProvider.OPENSSL);
    }

    @Test(expected = SSLException.class)
    public void testUnsupportedPrivateKeyFailsFastForServer() throws Exception {
        Assume.assumeTrue(OpenSsl.isBoringSSL());
        testUnsupportedPrivateKeyFailsFast(true);
    }

    @Test(expected = SSLException.class)
    public void testUnsupportedPrivateKeyFailsFastForClient() throws Exception {
        Assume.assumeTrue(OpenSsl.isBoringSSL());
        testUnsupportedPrivateKeyFailsFast(false);
    }
    private static void testUnsupportedPrivateKeyFailsFast(boolean server) throws Exception {
        Assume.assumeTrue(OpenSsl.isBoringSSL());
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
        if (server) {
            SslContextBuilder.forServer(new ByteArrayInputStream(cert.getBytes(CharsetUtil.US_ASCII)),
                    new ByteArrayInputStream(key.getBytes(CharsetUtil.US_ASCII)), null)
                    .sslProvider(SslProvider.OPENSSL).build();
        } else {
            SslContextBuilder.forClient().keyManager(new ByteArrayInputStream(cert.getBytes(CharsetUtil.US_ASCII)),
                new ByteArrayInputStream(key.getBytes(CharsetUtil.US_ASCII)), null)
                    .sslProvider(SslProvider.OPENSSL).build();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidCipherJdk() throws Exception {
        Assume.assumeTrue(OpenSsl.isAvailable());
        testInvalidCipher(SslProvider.JDK);
    }

    @Test
    public void testInvalidCipherOpenSSL() throws Exception {
        Assume.assumeTrue(OpenSsl.isAvailable());
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

    private static void testInvalidCipher(SslProvider provider) throws Exception {
        SelfSignedCertificate cert = new SelfSignedCertificate();
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
        SelfSignedCertificate cert = new SelfSignedCertificate();
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
        SelfSignedCertificate cert = new SelfSignedCertificate();
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
        SelfSignedCertificate cert = new SelfSignedCertificate();
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
        SelfSignedCertificate cert = new SelfSignedCertificate();
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
}
