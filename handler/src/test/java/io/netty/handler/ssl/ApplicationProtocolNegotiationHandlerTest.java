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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.ssl.CloseNotifyTest.assertCloseNotify;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

        final SSLEngine engine = SSLContext.getDefault().createSSLEngine();
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
    public void testHandshakeFailure() {
        ChannelHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                fail();
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler);
        SSLHandshakeException exception = new SSLHandshakeException("error");
        final SslHandshakeCompletionEvent completionEvent = new SslHandshakeCompletionEvent(exception);
        channel.pipeline().fireUserEventTriggered(completionEvent);
        channel.pipeline().fireExceptionCaught(new DecoderException(exception));
        assertNull(channel.pipeline().context(alpnHandler));
        assertFalse(channel.finishAndReleaseAll());
    }

    @ParameterizedTest
    @MethodSource("handshakeFailureCauses")
    public void testHandshakeFailureEvent(final Throwable cause) {
        final AtomicReference<Throwable> failureCause = new AtomicReference<Throwable>();
        final AtomicReference<Object> forwardedEvent = new AtomicReference<Object>();
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
                forwardedEvent.set(evt);
            }
        });

        final SslHandshakeCompletionEvent event = new SslHandshakeCompletionEvent(cause);
        try {
            channel.pipeline().fireUserEventTriggered(event);

            assertSame(cause, failureCause.get());
            assertSame(event, forwardedEvent.get());
            assertFalse(channel.isOpen());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static List<Throwable> handshakeFailureCauses() {
        return Arrays.<Throwable>asList(new SslHandshakeTimeoutException("timed out"), new ClosedChannelException());
    }

    @Test
    public void testHandshakeFailureCallbackThrows() {
        final AtomicInteger eventCount = new AtomicInteger();
        final AtomicInteger failureCount = new AtomicInteger();
        final AtomicInteger exceptionCount = new AtomicInteger();
        final AtomicReference<Throwable> propagatedCause = new AtomicReference<Throwable>();
        final Exception callbackFailure = new Exception("handshake failure callback failed");
        final SslHandshakeTimeoutException cause = new SslHandshakeTimeoutException("timed out");
        final SslHandshakeCompletionEvent event = new SslHandshakeCompletionEvent(cause);
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
        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler, new ChannelInboundHandlerAdapter() {
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
    public void testHandshakeTimeoutEventAndException(final boolean exceptionDuringEvent) {
        final AtomicInteger failureCount = new AtomicInteger();
        final SslHandshakeTimeoutException cause = new SslHandshakeTimeoutException("timed out");
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
        EmbeddedChannel channel = new EmbeddedChannel(alpnHandler, new ChannelInboundHandlerAdapter() {
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
    public void testHandshakeTransportFailureUsesChannelInactive() throws NoSuchAlgorithmException {
        final AtomicReference<Throwable> failureCause = new AtomicReference<Throwable>();
        final AtomicReference<Throwable> eventCause = new AtomicReference<Throwable>();
        final IOException writeFailure = new IOException("write failed");
        final SSLEngine engine = SSLContext.getDefault().createSSLEngine();
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
        ChannelHandler writeFailureHandler = new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg);
                promise.setFailure(writeFailure);
            }
        };
        EmbeddedChannel channel = new EmbeddedChannel(writeFailureHandler, new ChannelInboundHandlerAdapter() {
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

        final SSLEngine engine = SSLContext.getDefault().createSSLEngine();
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

        final SSLEngine engine = SSLContext.getDefault().createSSLEngine();
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
