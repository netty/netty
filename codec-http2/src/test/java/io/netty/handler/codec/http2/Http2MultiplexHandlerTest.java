/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLException;

import static io.netty.handler.codec.http2.Http2TestUtil.bb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Http2MultiplexHandler}.
 */
public class Http2MultiplexHandlerTest extends Http2MultiplexTest<Http2FrameCodec> {

    @Override
    protected Http2FrameCodec newCodec(TestChannelInitializer childChannelInitializer, Http2FrameWriter frameWriter) {
        return new Http2FrameCodecBuilder(true).frameWriter(frameWriter).build();
    }

    @Override
    protected ChannelHandler newMultiplexer(TestChannelInitializer childChannelInitializer) {
        return new Http2MultiplexHandler(childChannelInitializer, null);
    }

    @Override
    protected boolean useUserEventForResetFrame() {
        return true;
    }

    @Override
    protected boolean ignoreWindowUpdateFrames() {
        return true;
    }

    @Override
    protected boolean useUserEventForPriorityFrame() {
        return true;
    }

    @Test
    public void sslExceptionTriggersChildChannelException() {
        final LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel channel = newInboundStream(3, false, inboundHandler);
        assertTrue(channel.isActive());
        final RuntimeException testExc = new RuntimeException(new SSLException("foo"));
        channel.parent().pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (cause != testExc) {
                    super.exceptionCaught(ctx, cause);
                }
            }
        });
        channel.parent().pipeline().fireExceptionCaught(testExc);

        assertTrue(channel.isActive());
        RuntimeException exc = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                inboundHandler.checkException();
            }
        });
        assertEquals(testExc, exc);
    }

    @Test
    public void customExceptionForwarding() {
        final LastInboundHandler inboundHandler = new LastInboundHandler();
        Http2StreamChannel channel = newInboundStream(3, false, inboundHandler);
        assertTrue(channel.isActive());
        final RuntimeException testExc = new RuntimeException("xyz");
        channel.parent().pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                if (cause != testExc) {
                    super.exceptionCaught(ctx, cause);
                } else {
                    ctx.pipeline().fireExceptionCaught(new Http2MultiplexActiveStreamsException(cause));
                }
            }
        });
        channel.parent().pipeline().fireExceptionCaught(testExc);

        assertTrue(channel.isActive());
        RuntimeException exc = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                inboundHandler.checkException();
            }
        });
        assertEquals(testExc, exc);
    }

    @Test
    public void idleOutboundStreamClosedWhenParentCloses() {
        EmbeddedChannel parent = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(),
                new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()));
        try {
            final AtomicInteger inactive = new AtomicInteger();
            Http2StreamChannel childChannel = new Http2StreamChannelBootstrap(parent)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) {
                            inactive.incrementAndGet();
                            ctx.fireChannelInactive();
                        }
                    }).open().syncUninterruptibly().getNow();
            // No HEADERS frame was written yet, so the stream is not known to the connection.
            assertTrue(childChannel.isActive());
            assertEquals(Http2Stream.State.IDLE, childChannel.stream().state());

            parent.close();
            parent.runPendingTasks();

            assertFalse(childChannel.isOpen());
            assertTrue(childChannel.closeFuture().isDone());
            assertEquals(1, inactive.get());
        } finally {
            parent.finishAndReleaseAll();
        }
    }

    @Test
    public void pushedStreamIsDeliveredToInboundChildChannel() throws Exception {
        final LastInboundHandler pushHandler = new LastInboundHandler();
        EmbeddedChannel parent = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(),
                new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                    @Override
                    protected void initChannel(Http2StreamChannel ch) {
                        ch.pipeline().addLast(pushHandler);
                    }
                }));
        try {
            Http2FrameInboundWriter frameInboundWriter = new Http2FrameInboundWriter(parent);
            frameInboundWriter.writeInboundSettings(new Http2Settings());

            Http2StreamChannel requestChannel = new Http2StreamChannelBootstrap(parent)
                    .handler(new ChannelInboundHandlerAdapter()).open().syncUninterruptibly().getNow();
            Http2Headers request = new DefaultHttp2Headers()
                    .method("GET").scheme("https").authority("example.com").path("/");
            requestChannel.writeAndFlush(new DefaultHttp2HeadersFrame(request, true)).syncUninterruptibly();

            Http2Headers pushRequest = new DefaultHttp2Headers()
                    .method("GET").scheme("https").authority("example.com").path("/push");
            frameInboundWriter.writePushPromise(requestChannel.stream().id(), 2, pushRequest, 0);
            final Http2Headers pushResponse = new DefaultHttp2Headers().status("200");
            frameInboundWriter.writeInboundHeaders(2, pushResponse, 0, false);
            frameInboundWriter.writeInboundData(2, bb("hello"), 0, true);

            Http2HeadersFrame headersFrame = pushHandler.readInbound();
            assertEquals(2, headersFrame.stream().id());
            assertEquals(pushResponse, headersFrame.headers());
            Http2DataFrame dataFrame = pushHandler.readInbound();
            assertEquals("hello", dataFrame.content().toString(CharsetUtil.US_ASCII));
            assertTrue(dataFrame.isEndStream());
            dataFrame.release();
            assertNull(pushHandler.readInbound());
            pushHandler.checkException();

            // A pushed stream is half-closed (local), so writing to it must fail.
            final Http2StreamChannel pushChannel = (Http2StreamChannel) pushHandler.channel();
            assertEquals(2, pushChannel.stream().id());
            assertThrows(ClosedChannelException.class, new Executable() {
                @Override
                public void execute() {
                    pushChannel.writeAndFlush(new DefaultHttp2HeadersFrame(pushResponse)).syncUninterruptibly();
                }
            });
        } finally {
            parent.finishAndReleaseAll();
        }
    }

    @Test
    public void resetOfPromisedStreamBeforeHeaders() {
        final AtomicInteger pushChannels = new AtomicInteger();
        EmbeddedChannel parent = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(),
                new Http2MultiplexHandler(new ChannelInitializer<Http2StreamChannel>() {
                    @Override
                    protected void initChannel(Http2StreamChannel ch) {
                        pushChannels.incrementAndGet();
                    }
                }));
        try {
            Http2FrameInboundWriter frameInboundWriter = new Http2FrameInboundWriter(parent);
            frameInboundWriter.writeInboundSettings(new Http2Settings());

            Http2StreamChannel requestChannel = new Http2StreamChannelBootstrap(parent)
                    .handler(new ChannelInboundHandlerAdapter()).open().syncUninterruptibly().getNow();
            Http2Headers request = new DefaultHttp2Headers()
                    .method("GET").scheme("https").authority("example.com").path("/");
            requestChannel.writeAndFlush(new DefaultHttp2HeadersFrame(request, true)).syncUninterruptibly();

            Http2Headers pushRequest = new DefaultHttp2Headers()
                    .method("GET").scheme("https").authority("example.com").path("/push");
            frameInboundWriter.writePushPromise(requestChannel.stream().id(), 2, pushRequest, 0);
            frameInboundWriter.writeInboundRstStream(2, Http2Error.CANCEL.code());

            parent.checkException();
            assertTrue(parent.isActive());
            assertTrue(requestChannel.isActive());
            assertEquals(0, pushChannels.get());
        } finally {
            parent.finishAndReleaseAll();
        }
    }

    @Test
    public void streamExceptionForStreamWithoutChildChannelIsPropagated() {
        EmbeddedChannel parent = new EmbeddedChannel(Http2FrameCodecBuilder.forClient().build(),
                new Http2MultiplexHandler(new ChannelInboundHandlerAdapter()));
        try {
            Http2FrameCodec.DefaultHttp2FrameStream stream = new Http2FrameCodec.DefaultHttp2FrameStream();
            final Http2FrameStreamException exception = new Http2FrameStreamException(
                    stream, Http2Error.PROTOCOL_ERROR, new IllegalStateException());
            parent.pipeline().context(Http2FrameCodec.class).fireExceptionCaught(exception);

            Http2FrameStreamException caught = assertThrows(Http2FrameStreamException.class, new Executable() {
                @Override
                public void execute() {
                    parent.checkException();
                }
            });
            assertSame(exception, caught);
        } finally {
            parent.finishAndReleaseAll();
        }
    }
}
