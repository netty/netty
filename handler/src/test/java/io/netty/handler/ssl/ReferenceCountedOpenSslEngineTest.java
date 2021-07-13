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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.net.ssl.SSLEngine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ReferenceCountedOpenSslEngineTest extends OpenSslEngineTest {

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
        ReferenceCountUtil.release(unwrapEngine(engine));
    }

    @Override
    protected void cleanupServerSslContext(SslContext ctx) {
        ReferenceCountUtil.release(ctx);
    }

    @Override
    protected void cleanupServerSslEngine(SSLEngine engine) {
        ReferenceCountUtil.release(unwrapEngine(engine));
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

    @SuppressWarnings("deprecation")
    @Override
    protected SslContext wrapContext(SSLEngineTestParam param, SslContext context) {
        if (context instanceof ReferenceCountedOpenSslContext) {
            if (param instanceof OpenSslEngineTestParam) {
                ((ReferenceCountedOpenSslContext) context).setUseTasks(((OpenSslEngineTestParam) param).useTasks);
            }
            // Explicit enable the session cache as its disabled by default on the client side.
            ((ReferenceCountedOpenSslContext) context).sessionContext().setSessionCacheEnabled(true);
        }
        return context;
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
}
