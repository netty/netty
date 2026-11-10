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
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import java.nio.channels.ClosedChannelException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.netty.handler.ssl.CloseNotifyTest.assertCloseNotify;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ApplicationProtocolNegotiationHandlerTest {

    @Test
    public void testRemoveItselfIfNoSslHandlerPresent() throws NoSuchAlgorithmException {
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testHandshakeFailure(boolean exceptionDuringEvent) {
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger failureCountOnExceptionReturn = new AtomicInteger();
        AtomicInteger downstreamExceptionCount = new AtomicInteger();
        AtomicReference<Throwable> failureCause = new AtomicReference<Throwable>();
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }

            @Override
            protected void handshakeFailure(ChannelHandlerContext ctx, Throwable cause) {
                failureCount.incrementAndGet();
                failureCause.set(cause);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                super.exceptionCaught(ctx, cause);
                failureCountOnExceptionReturn.set(failureCount.get());
            }
        };

        SSLHandshakeException exception = new SSLHandshakeException("error");
        SslHandshakeCompletionEvent completionEvent = new SslHandshakeCompletionEvent(exception);
        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler, new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (exceptionDuringEvent && evt instanceof SslHandshakeCompletionEvent) {
                    ctx.pipeline().fireExceptionCaught(new DecoderException(exception));
                }

                ctx.fireUserEventTriggered(evt);
            }
        }, new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                downstreamExceptionCount.incrementAndGet();
            }
        });

        channel.pipeline().fireUserEventTriggered(completionEvent);

        if (!exceptionDuringEvent) {
            channel.pipeline().fireExceptionCaught(new DecoderException(exception));
        }

        assertEquals(1, failureCount.get());
        assertEquals(1, failureCountOnExceptionReturn.get());
        assertEquals(0, downstreamExceptionCount.get());
        assertSame(exception, failureCause.get());
        assertNull(channel.pipeline().context(alpnHandler));
        assertFalse(channel.finishAndReleaseAll());
    }

    @ParameterizedTest
    @MethodSource("handshakeFailureCauses")
    public void testHandshakeFailureEvent(Throwable cause) {
        AtomicReference<SslHandshakeCompletionEvent> eventRef =
                new AtomicReference<SslHandshakeCompletionEvent>();
        AtomicReference<Throwable> failureCause = new AtomicReference<Throwable>();
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
        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler, new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt instanceof SslHandshakeCompletionEvent) {
                    eventRef.set((SslHandshakeCompletionEvent) evt);
                }
            }
        });
        SslHandshakeCompletionEvent completionEvent = new SslHandshakeCompletionEvent(cause);

        channel.pipeline().fireUserEventTriggered(completionEvent);

        assertSame(cause, failureCause.get());
        assertSame(completionEvent, eventRef.get());
        assertFalse(channel.isOpen());
        assertFalse(channel.finishAndReleaseAll());
    }

    private static Stream<Arguments> handshakeFailureCauses() {
        return Stream.of(
                Arguments.of(new SslHandshakeTimeoutException("timed out")),
                Arguments.of(new ClosedChannelException()));
    }

    @Test
    public void testHandshakeFailureExceptionWithoutEvent() {
        AtomicInteger failureCount = new AtomicInteger();
        AtomicReference<Throwable> failureCause = new AtomicReference<Throwable>();
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }

            @Override
            protected void handshakeFailure(ChannelHandlerContext ctx, Throwable cause) {
                failureCount.incrementAndGet();
                failureCause.set(cause);
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler);
        SSLHandshakeException exception = new SSLHandshakeException("error");

        channel.pipeline().fireExceptionCaught(new DecoderException(exception));

        assertEquals(1, failureCount.get());
        assertSame(exception, failureCause.get());
        assertNull(channel.pipeline().context(alpnHandler));
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void testSniLookupFailure() {
        SSLHandshakeException exception = new SSLHandshakeException("SNI lookup failed");
        AtomicInteger failureCount = new AtomicInteger();
        AtomicInteger handshakeEventCount = new AtomicInteger();
        AtomicInteger downstreamExceptionCount = new AtomicInteger();
        AtomicReference<Throwable> failureCause = new AtomicReference<Throwable>();
        AtomicReference<SniCompletionEvent> sniEvent = new AtomicReference<SniCompletionEvent>();
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }

            @Override
            protected void handshakeFailure(ChannelHandlerContext ctx, Throwable cause) {
                failureCount.incrementAndGet();
                failureCause.set(cause);
            }
        };
        SniHandler sniHandler = new SniHandler((hostname, promise) -> promise.setFailure(exception));
        EmbeddedChannel channel = new EmbeddedChannel(sniHandler, alpnHandler, new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt instanceof SniCompletionEvent) {
                    sniEvent.set((SniCompletionEvent) evt);
                } else if (evt instanceof SslHandshakeCompletionEvent) {
                    handshakeEventCount.incrementAndGet();
                }

                ctx.fireUserEventTriggered(evt);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                downstreamExceptionCount.incrementAndGet();
            }
        });

        try {
            channel.writeInbound(Unpooled.buffer(5).writeByte(SslUtils.SSL_CONTENT_TYPE_APPLICATION_DATA)
                    .writeShort(0x0303).writeShort(0));

            assertNotNull(sniEvent.get());
            assertSame(exception, sniEvent.get().cause());
            assertSame(exception, failureCause.get());
            assertEquals(1, failureCount.get());
            assertEquals(0, handshakeEventCount.get());
            assertEquals(0, downstreamExceptionCount.get());
        } finally {
            channel.pipeline().remove(sniHandler);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void testHandshakeSuccess() throws NoSuchAlgorithmException {
        testHandshakeSuccess0(false);
    }

    @Test
    public void testHandshakeSuccessWithSslHandlerAddedLater() throws NoSuchAlgorithmException {
        testHandshakeSuccess0(true);
    }

    private static void testHandshakeSuccess0(boolean addLater) throws NoSuchAlgorithmException {
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
    public void testHandshakeSuccessButNoSslHandler() {
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }
        };
        final EmbeddedChannel channel = new EmbeddedChannel(alpnHandler);
        channel.pipeline().fireUserEventTriggered(SslHandshakeCompletionEvent.SUCCESS);
        assertNull(channel.pipeline().context(alpnHandler));
        assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                channel.finishAndReleaseAll();
            }
        });
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
                        ctx.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
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
                ctx.pipeline().addLast(new ChannelInboundHandlerAdapter() {
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

        EmbeddedChannel channel = new EmbeddedChannel(new SslHandler(engine), new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt == SslHandshakeCompletionEvent.SUCCESS) {
                    ctx.fireChannelRead(someBytes);
                }
                ctx.fireUserEventTriggered(evt);
            }
        }, alpnHandler);
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
