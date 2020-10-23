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
import io.netty.util.ReferenceCountUtil;
import org.junit.Test;

import javax.net.ssl.SSLEngine;

import static junit.framework.TestCase.*;

public class ReferenceCountedOpenSslEngineTest extends OpenSslEngineTest {

    public ReferenceCountedOpenSslEngineTest(BufferType type, ProtocolCipherCombo combo, boolean delegate,
                                             boolean useTasks) {
        super(type, combo, delegate, useTasks);
    }

    @Override
    protected SslProvider sslClientProvider() {
        return SslProvider.OPENSSL_REFCNT;
    }

    @Override
    protected SslProvider sslServerProvider() {
        return SslProvider.OPENSSL_REFCNT;
    }

    @Override
    protected void cleanupClientSslContext(SslContext ctx) {
        ReferenceCountUtil.release(ctx);
    }

    @Override
    protected void cleanupClientSslEngine(SSLEngine engine) {
        ReferenceCountUtil.release(engine);
    }

    @Override
    protected void cleanupServerSslContext(SslContext ctx) {
        ReferenceCountUtil.release(ctx);
    }

    @Override
    protected void cleanupServerSslEngine(SSLEngine engine) {
        ReferenceCountUtil.release(engine);
    }

    @Test(expected = NullPointerException.class)
    public void testNotLeakOnException() throws Exception {
        clientSslCtx = wrapContext(SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                        .sslProvider(sslClientProvider())
                                        .protocols(protocols())
                                        .ciphers(ciphers())
                                        .build());

        clientSslCtx.newEngine(null);
    }

    @Override
    protected SslContext wrapContext(SslContext context) {
        if (context instanceof ReferenceCountedOpenSslContext) {
            ((ReferenceCountedOpenSslContext) context).setUseTasks(useTasks);
        }
        return context;
    }

    @Test
    public void parentContextIsRetainedByChildEngines() throws Exception {
        SslContext clientSslCtx = SslContextBuilder.forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .sslProvider(sslClientProvider())
            .protocols(protocols())
            .ciphers(ciphers())
            .build();

        SSLEngine engine = clientSslCtx.newEngine(UnpooledByteBufAllocator.DEFAULT);
        assertEquals(ReferenceCountUtil.refCnt(clientSslCtx), 2);

        cleanupClientSslContext(clientSslCtx);
        assertEquals(ReferenceCountUtil.refCnt(clientSslCtx), 1);

        cleanupClientSslEngine(engine);
        assertEquals(ReferenceCountUtil.refCnt(clientSslCtx), 0);
    }
}
