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
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.ssl.CloseNotifyTest.assertCloseNotify;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ApplicationProtocolNegotiationHandlerTest {

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
        SslHandshakeCompletionEvent completionEvent = new SslHandshakeCompletionEvent(exception);
        channel.pipeline().fireUserEventTriggered(completionEvent);
        channel.pipeline().fireExceptionCaught(new DecoderException(exception));
        assertNull(channel.pipeline().context(alpnHandler));
        assertFalse(channel.finishAndReleaseAll());
    }

    @Test
    public void testHandshakeSuccess() throws NoSuchAlgorithmException {
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

        EmbeddedChannel channel = new EmbeddedChannel(new SslHandler(engine), alpnHandler);
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
