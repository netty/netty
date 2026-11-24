/*
 * Copyright 2020 The Netty Project
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelShutdownType;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.util.CachedSelfSignedCertificate;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.CompletionHandler;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.netty.handler.ssl.CloseNotifyTest.assertCloseNotify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ApplicationProtocolNegotiationHandlerTest {

    @Test
    public void nestedTlsWaitsForInnerHandshake() throws Exception {
        assumeTrue(SslProvider.isAlpnSupported(SslProvider.JDK));
        SelfSignedCertificate cert = CachedSelfSignedCertificate.getCachedCertificate();
        ApplicationProtocolConfig outerAlpn = alpn(ApplicationProtocolNames.HTTP_1_1);
        ApplicationProtocolConfig innerAlpn = alpn(ApplicationProtocolNames.HTTP_2);
        SslContext outerServerContext = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                .sslProvider(SslProvider.JDK).applicationProtocolConfig(outerAlpn).build();
        SslContext innerServerContext = SslContextBuilder.forServer(cert.certificate(), cert.privateKey())
                .sslProvider(SslProvider.JDK).applicationProtocolConfig(innerAlpn).build();
        SslContext outerClientContext = SslContextBuilder.forClient().sslProvider(SslProvider.JDK)
                .trustManager(InsecureTrustManagerFactory.INSTANCE).applicationProtocolConfig(outerAlpn).build();
        SslContext innerClientContext = SslContextBuilder.forClient().sslProvider(SslProvider.JDK)
                .trustManager(InsecureTrustManagerFactory.INSTANCE).applicationProtocolConfig(innerAlpn).build();
        SslHandler outer = outerClientContext.newHandler(UnpooledByteBufAllocator.DEFAULT);
        final SslHandler inner = innerClientContext.newHandler(UnpooledByteBufAllocator.DEFAULT);
        final AtomicBoolean configuredBeforeInnerHandshake = new AtomicBoolean();
        final AtomicReference<String> negotiatedProtocol = new AtomicReference<String>();
        final AtomicInteger configured = new AtomicInteger();
        final AtomicInteger events = new AtomicInteger();
        EmbeddedChannel client = new EmbeddedChannel(outer, inner,
                new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                    @Override
                    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                        configuredBeforeInnerHandshake.set(!inner.handshakeFuture().isSuccess());
                        negotiatedProtocol.set(protocol);
                        configured.incrementAndGet();
                    }
                }, new ChannelInboundHandler() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                        if (evt instanceof SslHandshakeCompletionEvent &&
                                ((SslHandshakeCompletionEvent) evt).isSuccess()) {
                            events.incrementAndGet();
                        }
                        ctx.fireUserEventTriggered(evt);
                    }
                });
        EmbeddedChannel server = new EmbeddedChannel(
                outerServerContext.newHandler(UnpooledByteBufAllocator.DEFAULT),
                innerServerContext.newHandler(UnpooledByteBufAllocator.DEFAULT));
        try {
            for (int i = 0; i < 100 && !inner.handshakeFuture().isDone(); i++) {
                transferTlsRecords(client, server);
                transferTlsRecords(server, client);
            }
            assertThat(outer.handshakeFuture().isSuccess()).isTrue();
            assertThat(inner.handshakeFuture().isSuccess())
                    .as(String.valueOf(inner.handshakeFuture().cause())).isTrue();
            assertThat(outer.applicationProtocol()).isEqualTo(ApplicationProtocolNames.HTTP_1_1);
            assertThat(inner.applicationProtocol()).isEqualTo(ApplicationProtocolNames.HTTP_2);
            assertThat(configured.get()).isEqualTo(1);
            assertThat(configuredBeforeInnerHandshake.get()).isFalse();
            assertThat(negotiatedProtocol.get()).isEqualTo(ApplicationProtocolNames.HTTP_2);
            assertThat(events.get()).isEqualTo(2);
        } finally {
            client.finishAndReleaseAll();
            server.finishAndReleaseAll();
            ReferenceCountUtil.release(outerClientContext);
            ReferenceCountUtil.release(innerClientContext);
            ReferenceCountUtil.release(outerServerContext);
            ReferenceCountUtil.release(innerServerContext);
        }
    }

    @Test
    public void ignoresSslHandlerAfterNegotiationHandler() throws Exception {
        SSLEngine beforeEngine = SSLContext.getDefault().createSSLEngine();
        beforeEngine.setUseClientMode(true);
        SSLEngine afterEngine = SSLContext.getDefault().createSSLEngine();
        afterEngine.setUseClientMode(true);
        SslHandler before = new SslHandler(beforeEngine);
        SslHandler after = new SslHandler(afterEngine);
        final AtomicInteger configured = new AtomicInteger();
        ApplicationProtocolNegotiationHandler negotiationHandler =
                new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                    @Override
                    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                        assertThat(protocol).isEqualTo(ApplicationProtocolNames.HTTP_1_1);
                        configured.incrementAndGet();
                    }
                };
        EmbeddedChannel channel = new EmbeddedChannel(before, negotiationHandler, after);
        try {
            // Simulate completion of only the preceding handler, as in the existing success tests.
            ((Promise<Channel>) before.handshakeFuture()).setSuccess(channel);
            channel.pipeline().context(before).fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
            assertThat(after.handshakeFuture().isDone()).isFalse();
            assertThat(configured.get()).isEqualTo(1);
            assertThat(channel.pipeline().context(negotiationHandler)).isNull();
        } finally {
            channel.pipeline().remove(after);
            channel.pipeline().remove(before);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void removesItselfWhenSslHandlerOnlyFollows() throws Exception {
        SSLEngine afterEngine = SSLContext.getDefault().createSSLEngine();
        afterEngine.setUseClientMode(true);
        SslHandler after = new SslHandler(afterEngine);
        ApplicationProtocolNegotiationHandler negotiationHandler =
                new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
                    @Override
                    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                        fail("No preceding SslHandler should configure the pipeline");
                    }
                };
        EmbeddedChannel channel = new EmbeddedChannel(negotiationHandler, after);
        try {
            assertThat(channel.writeInbound("first")).isTrue();
            assertThat(channel.pipeline().context(negotiationHandler)).isNull();
            assertThat(channel.pipeline().context(after)).isNotNull();
            assertThat(after.handshakeFuture().isDone()).isFalse();
            assertThat(channel.writeInbound("second")).isTrue();
            assertThat(channel.<String>readInbound()).isEqualTo("first");
            assertThat(channel.<String>readInbound()).isEqualTo("second");
            assertThat(channel.<Object>readInbound()).isNull();
        } finally {
            channel.pipeline().remove(after);
            channel.finishAndReleaseAll();
        }
    }

    private static ApplicationProtocolConfig alpn(String protocol) {
        return new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT, protocol);
    }

    private static void transferTlsRecords(EmbeddedChannel from, EmbeddedChannel to) throws Exception {
        ByteBuf record;
        while ((record = from.readOutbound()) != null) {
            to.writeInbound(record);
        }
    }

    @Test
    public void testRemoveItselfIfNoSslHandlerPresent() throws Exception {
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }
        };

        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        // This test is mocked/simulated and doesn't go through full TLS handshake. Currently only JDK SSLEngineImpl
        // client mode will generate a close_notify.
        engine.setUseClientMode(true);

        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler);
        String msg = "msg";
        String msg2 = "msg2";

        assertTrue(channel.writeInbound(msg));
        assertTrue(channel.writeInbound(msg2));
        assertNull(channel.pipeline().context(alpnHandler));
        assertEquals(msg, channel.readInbound());
        assertEquals(msg2, channel.readInbound());

        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void testHandshakeFailure() throws Exception {
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler);
        SSLHandshakeException exception = new SSLHandshakeException("error");
        SslHandshakeCompletionEvent completionEvent = new SslHandshakeCompletionEvent(exception);
        channel.pipeline().fireUserEventTriggered(completionEvent);
        channel.pipeline().fireExceptionCaught(new DecoderException(exception));
        assertNull(channel.pipeline().context(alpnHandler));
        assertFalse(channel.finishAndReleaseAll());
    }

    @ParameterizedTest
    @MethodSource("handshakeFailureCauses")
    public void testHandshakeFailureEvent(Throwable cause) throws Exception {
        AtomicReference<Throwable> failureCause = new AtomicReference<Throwable>();
        AtomicReference<Object> forwardedEvent = new AtomicReference<Object>();
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }

            @Override
            protected void handshakeFailure(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                failureCause.set(cause);
                super.handshakeFailure(ctx, cause);
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler, new ChannelInboundHandler() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                forwardedEvent.set(evt);
            }
        });

        SslHandshakeCompletionEvent event = new SslHandshakeCompletionEvent(cause);
        try {
            channel.pipeline().fireUserEventTriggered(event);

            assertSame(cause, failureCause.get());
            assertSame(event, forwardedEvent.get());
            assertFalse(channel.isOpen());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @MethodSource("handshakeFailureCauses")
    public void nestedTlsOuterHandshakeFailureIsHandled(Throwable cause) throws Exception {
        SSLEngine outerEngine = SSLContext.getDefault().createSSLEngine();
        outerEngine.setUseClientMode(true);
        SSLEngine innerEngine = SSLContext.getDefault().createSSLEngine();
        innerEngine.setUseClientMode(true);
        SslHandler outer = new SslHandler(outerEngine);
        SslHandler inner = new SslHandler(innerEngine);
        final AtomicReference<Throwable> failureCause = new AtomicReference<Throwable>();
        final AtomicReference<Object> forwardedEvent = new AtomicReference<Object>();
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail("A failed outer handshake must not configure the pipeline");
            }

            @Override
            protected void handshakeFailure(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                failureCause.set(cause);
                super.handshakeFailure(ctx, cause);
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(outer, inner, alpnHandler, new ChannelInboundHandler() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                forwardedEvent.set(evt);
            }
        });

        SslHandshakeCompletionEvent event = new SslHandshakeCompletionEvent(cause);
        try {
            channel.freezeTime();
            assertThat(inner.handshakeFuture().isDone()).isFalse();
            channel.pipeline().context(outer).fireUserEventTriggered(event);

            assertThat(failureCause.get()).isSameAs(cause);
            assertThat(forwardedEvent.get()).isSameAs(event);

            // Each layer may wait for its close_notify to flush before closing the preceding layer.
            channel.advanceTimeBy(inner.getCloseNotifyFlushTimeoutMillis(), TimeUnit.MILLISECONDS);
            channel.runPendingTasks();
            channel.advanceTimeBy(outer.getCloseNotifyFlushTimeoutMillis(), TimeUnit.MILLISECONDS);
            channel.runPendingTasks();
            assertThat(channel.isOpen()).isFalse();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static Stream<Throwable> handshakeFailureCauses() {
        return Stream.of(new SslHandshakeTimeoutException("timed out"), new ClosedChannelException());
    }

    @Test
    public void testHandshakeFailureCallbackThrows() throws Exception {
        AtomicInteger eventCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger exceptionCount = new AtomicInteger();
        AtomicReference<Throwable> propagatedCause = new AtomicReference<Throwable>();
        Exception callbackFailure = new Exception("handshake failure callback failed");
        SslHandshakeTimeoutException cause = new SslHandshakeTimeoutException("timed out");
        SslHandshakeCompletionEvent event = new SslHandshakeCompletionEvent(cause);
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }

            @Override
            protected void handshakeFailure(ChannelHandlerContext ctx, Throwable failure) throws Exception {
                assertSame(cause, failure);
                failureCount.incrementAndGet();
                throw callbackFailure;
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable failure) {
                ctx.fireExceptionCaught(failure);
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler, new ChannelInboundHandler() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                assertSame(event, evt);
                eventCount.incrementAndGet();
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable failure) {
                exceptionCount.incrementAndGet();
                propagatedCause.set(failure);
            }
        });

        try {
            channel.pipeline().fireUserEventTriggered(event);

            assertEquals(1, eventCount.get());
            assertEquals(1, failureCount.get());
            assertEquals(1, exceptionCount.get());
            assertSame(callbackFailure, propagatedCause.get());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testHandshakeTimeoutEventAndException(boolean exceptionDuringEvent) throws Exception {
        AtomicInteger failureCount = new AtomicInteger();
        SslHandshakeTimeoutException cause = new SslHandshakeTimeoutException("timed out");
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }

            @Override
            protected void handshakeFailure(ChannelHandlerContext ctx, Throwable failure) {
                assertSame(cause, failure);
                failureCount.incrementAndGet();
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler, new ChannelInboundHandler() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (exceptionDuringEvent && evt instanceof SslHandshakeCompletionEvent) {
                    ctx.pipeline().fireExceptionCaught(new DecoderException(cause));
                }

                ctx.fireUserEventTriggered(evt);
            }
        });

        try {
            channel.pipeline().fireUserEventTriggered(new SslHandshakeCompletionEvent(cause));
            assertEquals(1, failureCount.get());

            if (!exceptionDuringEvent) {
                channel.pipeline().fireExceptionCaught(new DecoderException(cause));
            }

            assertEquals(1, failureCount.get());
            assertNull(channel.pipeline().context(alpnHandler));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void testHandshakeTransportFailureUsesChannelInactive() throws Exception {
        AtomicReference<Throwable> failureCause = new AtomicReference<Throwable>();
        AtomicReference<Throwable> eventCause = new AtomicReference<Throwable>();
        IOException writeFailure = new IOException("write failed");
        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        engine.setUseClientMode(true);
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }

            @Override
            protected void handshakeFailure(ChannelHandlerContext ctx, Throwable cause) {
                failureCause.compareAndSet(null, cause);
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                failureCause.compareAndSet(null, new ClosedChannelException());
                super.channelInactive(ctx);
            }
        };
        ChannelHandler writeFailureHandler = new ChannelOutboundHandler() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, CompletionHandler<Void> handler) {
                ReferenceCountUtil.release(msg);
                handler.failure(writeFailure);
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(writeFailureHandler, new ChannelInboundHandler() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt instanceof SslHandshakeCompletionEvent) {
                    eventCause.set(((SslHandshakeCompletionEvent) evt).cause());
                }

                ctx.fireUserEventTriggered(evt);
            }
        }, alpnHandler);

        try {
            channel.pipeline().addAfter(channel.pipeline().context(writeFailureHandler).name(),
                    "ssl", new SslHandler(engine));

            assertInstanceOf(SSLException.class, eventCause.get());
            assertSame(writeFailure, eventCause.get().getCause());
            assertInstanceOf(ClosedChannelException.class, failureCause.get());
            assertFalse(channel.isOpen());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void testHandshakeSuccess() throws Exception {
        testHandshakeSuccess0(false);
    }

    @Test
    public void testHandshakeSuccessWithSslHandlerAddedLater() throws Exception {
        testHandshakeSuccess0(true);
    }

    private static void testHandshakeSuccess0(boolean addLater) throws Exception {
        final AtomicBoolean configureCalled = new AtomicBoolean(false);
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                configureCalled.set(true);
                assertEquals(ApplicationProtocolNames.HTTP_1_1, protocol);
            }
        };

        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        // This test is mocked/simulated and doesn't go through full TLS handshake. Currently only JDK SSLEngineImpl
        // client mode will generate a close_notify.
        engine.setUseClientMode(true);

        EmbeddedChannel channel = new EmbeddedChannel();
        if (addLater) {
            channel.pipeline().addLast(alpnHandler);
            channel.pipeline().addFirst(new SslHandler(engine));
        } else {
            channel.pipeline().addLast(new SslHandler(engine));
            channel.pipeline().addLast(alpnHandler);
        }
        SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
        ((Promise<Channel>) sslHandler.handshakeFuture()).setSuccess(channel);
        channel.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
        assertNull(channel.pipeline().context(alpnHandler));
        // Should produce the close_notify messages
        channel.releaseOutbound();
        channel.close();
        assertCloseNotify((ByteBuf) channel.readOutbound());
        channel.finishAndReleaseAll();
        assertTrue(configureCalled.get());
    }

    @Test
    public void testHandshakeSuccessButNoSslHandler() throws Exception {
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }
        };
        final EmbeddedChannel channel = new EmbeddedChannel(alpnHandler);
        channel.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
        assertNull(channel.pipeline().context(alpnHandler));
        assertThrows(IllegalStateException.class, channel::finishAndReleaseAll);
    }

    @Test
    public void testBufferMessagesUntilHandshakeComplete() throws Exception {
        testBufferMessagesUntilHandshakeComplete(null);
    }

    @Test
    public void testBufferMessagesUntilHandshakeCompleteWithClose() throws Exception {
        testBufferMessagesUntilHandshakeComplete(
                new ApplicationProtocolNegotiationHandlerTest.Consumer<ChannelHandlerContext>() {
                    @Override
                    public void consume(ChannelHandlerContext ctx) {
                        ctx.channel().close();
                    }
                });
    }

    @Test
    public void testBufferMessagesUntilHandshakeCompleteWithInputShutdown() throws Exception {
        testBufferMessagesUntilHandshakeComplete(
                new ApplicationProtocolNegotiationHandlerTest.Consumer<ChannelHandlerContext>() {
                    @Override
                    public void consume(ChannelHandlerContext ctx) {
                        ctx.fireChannelShutdown(ChannelShutdownType.newInbound());
                    }
                });
    }

    private void testBufferMessagesUntilHandshakeComplete(final Consumer<ChannelHandlerContext> pipelineConfigurator)
            throws Exception {
        final AtomicReference<byte[]> channelReadData = new AtomicReference<byte[]>();
        final AtomicBoolean channelReadCompleteCalled = new AtomicBoolean(false);
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                assertEquals(ApplicationProtocolNames.HTTP_1_1, protocol);
                ctx.pipeline().addLast(new ChannelInboundHandler() {
                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                        channelReadData.set((byte[]) msg);
                    }

                    @Override
                    public void channelReadComplete(ChannelHandlerContext ctx) {
                        channelReadCompleteCalled.set(true);
                    }
                });
                if (pipelineConfigurator != null) {
                    pipelineConfigurator.consume(ctx);
                }
            }
        };

        SSLEngine engine = SSLContext.getDefault().createSSLEngine();
        // This test is mocked/simulated and doesn't go through full TLS handshake. Currently only JDK SSLEngineImpl
        // client mode will generate a close_notify.
        engine.setUseClientMode(true);

        final byte[] someBytes = new byte[1024];

        EmbeddedChannel channel = new EmbeddedChannel(new SslHandler(engine), new ChannelInboundHandler() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt == SslHandshakeCompletionEvent.SUCCESS) {
                    ctx.fireChannelRead(someBytes);
                }
                ctx.fireUserEventTriggered(evt);
            }
        }, alpnHandler);
        SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
        ((Promise<Channel>) sslHandler.handshakeFuture()).setSuccess(channel);
        channel.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
        assertNull(channel.pipeline().context(alpnHandler));
        assertArrayEquals(someBytes, channelReadData.get());
        assertTrue(channelReadCompleteCalled.get());
        assertNull(channel.readInbound());
        assertTrue(channel.finishAndReleaseAll());
    }

    private interface Consumer<T> {
        void consume(T t);
    }
}
