/*
 * Copyright 2017 The Netty Project
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
package io.netty.microbench.handler.ssl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.microbench.channel.EmbeddedChannelWriteAccumulatingHandlerContext;
import io.netty.microbench.util.AbstractMicrobenchmark;
import io.netty.util.ReferenceCountUtil;
import org.openjdk.jmh.annotations.Param;

import java.io.File;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

import static io.netty.handler.codec.ByteToMessageDecoder.COMPOSITE_CUMULATOR;
import static org.junit.Assert.assertNull;

public class AbstractSslHandlerBenchmark extends AbstractMicrobenchmark {
    private static final String PROTOCOL_TLS_V1_2 = "TLSv1.2";

    public enum SslEngineProvider {
        JDK {
            @Override
            SslProvider sslProvider() {
                return SslProvider.JDK;
            }
        },
        OPENSSL {
            @Override
            SslProvider sslProvider() {
                return SslProvider.OPENSSL;
            }
        },
        OPENSSL_REFCNT {
            @Override
            SslProvider sslProvider() {
                return SslProvider.OPENSSL_REFCNT;
            }
        };
        private final SslContext clientContext = newClientContext();
        private final SslContext serverContext = newServerContext();

        private SslContext newClientContext() {
            try {
                return SslContextBuilder.forClient()
                        .sslProvider(sslProvider())
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
            } catch (SSLException e) {
                throw new IllegalStateException(e);
            }
        }

        private SslContext newServerContext() {
            try {
                File keyFile = new File(getClass().getResource("test_unencrypted.pem").getFile());
                File crtFile = new File(getClass().getResource("test.crt").getFile());

                return SslContextBuilder.forServer(crtFile, keyFile)
                        .sslProvider(sslProvider())
                        .build();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        SslHandler newClientHandler(ByteBufAllocator allocator, String cipher) {
            SslHandler handler = clientContext.newHandler(allocator);
            configureEngine(handler.engine(), cipher);
            return handler;
        }

        SslHandler newServerHandler(ByteBufAllocator allocator, String cipher) {
            SslHandler handler = serverContext.newHandler(allocator);
            configureEngine(handler.engine(), cipher);
            return handler;
        }

        abstract SslProvider sslProvider();

        static SSLEngine configureEngine(SSLEngine engine, String cipher) {
            engine.setEnabledProtocols(new String[]{ PROTOCOL_TLS_V1_2 });
            engine.setEnabledCipherSuites(new String[]{ cipher });
            return engine;
        }
    }

    @Param
    public SslEngineProvider sslProvider;

    // Includes cipher required by HTTP/2
    @Param({ "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" })
    public String cipher;

    protected SslHandler clientSslHandler;
    protected SslHandler serverSslHandler;
    protected EmbeddedChannelWriteAccumulatingHandlerContext clientCtx;
    protected EmbeddedChannelWriteAccumulatingHandlerContext serverCtx;

    protected final void initSslHandlers(ByteBufAllocator allocator) {
        clientSslHandler = newClientHandler(allocator);
        serverSslHandler = newServerHandler(allocator);
        clientCtx = new SslThroughputBenchmarkHandlerContext(allocator, clientSslHandler, COMPOSITE_CUMULATOR);
        serverCtx = new SslThroughputBenchmarkHandlerContext(allocator, serverSslHandler, COMPOSITE_CUMULATOR);
    }

    protected final void destroySslHandlers() {
        try {
            if (clientSslHandler != null) {
                ReferenceCountUtil.release(clientSslHandler.engine());
            }
        } finally {
            if (serverSslHandler != null) {
                ReferenceCountUtil.release(serverSslHandler.engine());
            }
        }
    }

    protected final void doHandshake() throws Exception {
        serverSslHandler.handlerAdded(serverCtx);
        clientSslHandler.handlerAdded(clientCtx);
        do {
            ByteBuf clientCumulation = clientCtx.cumulation();
            if (clientCumulation != null) {
                serverSslHandler.channelRead(serverCtx, clientCumulation.retain());
                clientCtx.releaseCumulation();
            }
            ByteBuf serverCumulation = serverCtx.cumulation();
            if (serverCumulation != null) {
                clientSslHandler.channelRead(clientCtx, serverCumulation.retain());
                serverCtx.releaseCumulation();
            }
        } while (!clientSslHandler.handshakeFuture().isDone() || !serverSslHandler.handshakeFuture().isDone());
    }

    protected final SslHandler newClientHandler(ByteBufAllocator allocator) {
        return sslProvider.newClientHandler(allocator, cipher);
    }

    protected final SslHandler newServerHandler(ByteBufAllocator allocator) {
        return sslProvider.newServerHandler(allocator, cipher);
    }

    private static final class SslThroughputBenchmarkHandlerContext extends
            EmbeddedChannelWriteAccumulatingHandlerContext {
        SslThroughputBenchmarkHandlerContext(ByteBufAllocator alloc, ChannelHandler handler,
                                                    ByteToMessageDecoder.Cumulator writeCumulator) {
            super(alloc, handler, writeCumulator);
        }

        @Override
        protected void handleException(Throwable t) {
            handleUnexpectedException(t);
        }
    }

    public static void handleUnexpectedException(Throwable t) {
        assertNull(t);
    }
}
