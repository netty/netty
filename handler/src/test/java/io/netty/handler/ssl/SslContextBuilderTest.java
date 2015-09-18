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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import org.junit.Assume;
import org.junit.Test;

import javax.net.ssl.SSLEngine;

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
