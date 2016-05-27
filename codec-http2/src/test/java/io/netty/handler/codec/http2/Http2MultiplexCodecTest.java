/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.Http2Exception.StreamException;
import io.netty.util.AsciiString;
import io.netty.util.internal.PlatformDependent;

import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Queue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static io.netty.util.ReferenceCountUtil.release;
import static io.netty.util.ReferenceCountUtil.releaseLater;
import static org.junit.Assert.*;

/**
 * Unit tests for {@link Http2MultiplexCodec}.
 */
public class Http2MultiplexCodecTest {

    private EmbeddedChannel parentChannel;

    private TestChannelInitializer childChannelInitializer;

    private static final Http2Headers request = new DefaultHttp2Headers()
            .method(HttpMethod.GET.asciiName()).scheme(HttpScheme.HTTPS.name())
            .authority(new AsciiString("example.org")).path(new AsciiString("/foo"));

    private static final int streamId = 3;

    @Before
    public void setUp() {
        childChannelInitializer = new TestChannelInitializer();
        parentChannel = new EmbeddedChannel();
        parentChannel.connect(new InetSocketAddress(0));
        parentChannel.pipeline().addLast(new Http2MultiplexCodec(true, null, childChannelInitializer));
    }

    @After
    public void tearDown() throws Exception {
        if (childChannelInitializer.handler != null) {
            ((LastInboundHandler) childChannelInitializer.handler).finishAndReleaseAll();
        }
        parentChannel.finishAndReleaseAll();
    }

    // TODO(buchgr): Thread model of child channel
    // TODO(buchgr): Flush from child channel
    // TODO(buchgr): ChildChannel.childReadComplete()
    // TODO(buchgr): GOAWAY Logic
    // TODO(buchgr): Reset frame on close
    // TODO(buchgr): Test ChannelConfig.setMaxMessagesPerRead

    @Test
    public void headerAndDataFramesShouldBeDelivered() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        childChannelInitializer.handler = inboundHandler;

        Http2StreamActiveEvent streamActive = new Http2StreamActiveEvent(streamId);
        Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(request).setStreamId(streamId);
        Http2DataFrame dataFrame1 = releaseLater(new DefaultHttp2DataFrame(bb("hello")).setStreamId(streamId));
        Http2DataFrame dataFrame2 = releaseLater(new DefaultHttp2DataFrame(bb("world")).setStreamId(streamId));

        assertFalse(inboundHandler.channelActive);
        parentChannel.pipeline().fireUserEventTriggered(streamActive);
        assertTrue(inboundHandler.channelActive);
        // Make sure the stream active event is not delivered as a user event on the child channel.
        assertNull(inboundHandler.readUserEvent());
        parentChannel.pipeline().fireChannelRead(headersFrame);
        parentChannel.pipeline().fireChannelRead(dataFrame1);
        parentChannel.pipeline().fireChannelRead(dataFrame2);

        assertEquals(headersFrame, inboundHandler.readInbound());
        assertEquals(dataFrame1, inboundHandler.readInbound());
        assertEquals(dataFrame2, inboundHandler.readInbound());
        assertNull(inboundHandler.readInbound());
    }

    @Test
    public void framesShouldBeMultiplexed() {
        LastInboundHandler inboundHandler3 = streamActiveAndWriteHeaders(3);
        LastInboundHandler inboundHandler11 = streamActiveAndWriteHeaders(11);
        LastInboundHandler inboundHandler5 = streamActiveAndWriteHeaders(5);

        verifyFramesMultiplexedToCorrectChannel(3, inboundHandler3, 1);
        verifyFramesMultiplexedToCorrectChannel(5, inboundHandler5, 1);
        verifyFramesMultiplexedToCorrectChannel(11, inboundHandler11, 1);

        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(bb("hello"), false).setStreamId(5));
        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(bb("foo"), true).setStreamId(3));
        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(bb("world"), true).setStreamId(5));
        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(bb("bar"), true).setStreamId(11));
        verifyFramesMultiplexedToCorrectChannel(5, inboundHandler5, 2);
        verifyFramesMultiplexedToCorrectChannel(3, inboundHandler3, 1);
        verifyFramesMultiplexedToCorrectChannel(11, inboundHandler11, 1);
    }

    @Test
    public void inboundDataFrameShouldEmitWindowUpdateFrame() {
        LastInboundHandler inboundHandler = streamActiveAndWriteHeaders(streamId);
        ByteBuf tenBytes = bb("0123456789");
        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(tenBytes, true).setStreamId(streamId));
        parentChannel.pipeline().flush();

        Http2WindowUpdateFrame windowUpdate = parentChannel.readOutbound();
        assertNotNull(windowUpdate);
        assertEquals(streamId, windowUpdate.streamId());
        assertEquals(10, windowUpdate.windowSizeIncrement());

        // headers and data frame
        verifyFramesMultiplexedToCorrectChannel(streamId, inboundHandler, 2);
    }

    @Test
    public void channelReadShouldRespectAutoRead() {
        LastInboundHandler inboundHandler = streamActiveAndWriteHeaders(streamId);
        Channel childChannel = inboundHandler.channel();
        assertTrue(childChannel.config().isAutoRead());
        Http2HeadersFrame headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);

        childChannel.config().setAutoRead(false);
        parentChannel.pipeline().fireChannelRead(
                new DefaultHttp2DataFrame(bb("hello world"), false).setStreamId(streamId));
        parentChannel.pipeline().fireChannelReadComplete();
        Http2DataFrame dataFrame0 = inboundHandler.readInbound();
        assertNotNull(dataFrame0);
        release(dataFrame0);

        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(bb("foo"), false).setStreamId(streamId));
        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(bb("bar"), true).setStreamId(streamId));
        parentChannel.pipeline().fireChannelReadComplete();

        dataFrame0 = inboundHandler.readInbound();
        assertNull(dataFrame0);

        childChannel.config().setAutoRead(true);
        verifyFramesMultiplexedToCorrectChannel(streamId, inboundHandler, 2);
    }

    @Test
    public void streamClosedShouldFireChannelInactive() {
        LastInboundHandler inboundHandler = streamActiveAndWriteHeaders(streamId);
        assertTrue(inboundHandler.channelActive);

        parentChannel.pipeline().fireUserEventTriggered(new Http2StreamClosedEvent(streamId));

        parentChannel.runPendingTasks();
        parentChannel.checkException();

        assertFalse(inboundHandler.channelActive);
    }

    @Test(expected = StreamException.class)
    public void streamExceptionTriggersChildChannelExceptionCaught() throws Exception {
        LastInboundHandler inboundHandler = streamActiveAndWriteHeaders(streamId);

        StreamException e = new StreamException(streamId, Http2Error.PROTOCOL_ERROR, "baaam!");
        parentChannel.pipeline().fireExceptionCaught(e);

        inboundHandler.checkException();
    }

    @Test
    public void streamExceptionClosesChildChannel() {
        LastInboundHandler inboundHandler = streamActiveAndWriteHeaders(streamId);

        assertTrue(inboundHandler.channelActive);
        StreamException e = new StreamException(streamId, Http2Error.PROTOCOL_ERROR, "baaam!");
        parentChannel.pipeline().fireExceptionCaught(e);

        parentChannel.runPendingTasks();
        parentChannel.checkException();

        assertFalse(inboundHandler.channelActive);
    }

    private LastInboundHandler streamActiveAndWriteHeaders(int streamId) {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        childChannelInitializer.handler = inboundHandler;
        assertFalse(inboundHandler.channelActive);
        parentChannel.pipeline().fireUserEventTriggered(new Http2StreamActiveEvent(streamId));
        assertTrue(inboundHandler.channelActive);
        parentChannel.pipeline().fireChannelRead(new DefaultHttp2HeadersFrame(request).setStreamId(streamId));
        parentChannel.pipeline().fireChannelReadComplete();

        return inboundHandler;
    }

    private static void verifyFramesMultiplexedToCorrectChannel(int streamId, LastInboundHandler inboundHandler,
                                                                int numFrames) {
        for (int i = 0; i < numFrames; i++) {
            Http2StreamFrame frame = inboundHandler.readInbound();
            assertNotNull(frame);
            assertEquals(streamId, frame.streamId());
            release(frame);
        }
        assertNull(inboundHandler.readInbound());
    }

    @Sharable
    static class TestChannelInitializer extends ChannelInitializer<Channel> {
        ChannelHandler handler;

        @Override
        public void initChannel(Channel channel) {
            if (handler != null) {
                channel.pipeline().addLast(handler);
                handler = null;
            }
        }
    }

    private static ByteBuf bb(String s) {
        return ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, s);
    }

    static final class LastInboundHandler extends ChannelDuplexHandler {
        private final Queue<Object> inboundMessages = new ArrayDeque<Object>();
        private final Queue<Object> userEvents = new ArrayDeque<Object>();
        private Throwable lastException;
        private ChannelHandlerContext ctx;
        private boolean channelActive;

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            channelActive = true;
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            channelActive = false;
            super.channelInactive(ctx);
        }

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            inboundMessages.add(msg);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            userEvents.add(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (lastException != null) {
                cause.printStackTrace();
            } else {
                lastException = cause;
            }
        }

        public void checkException() throws Exception {
            if (lastException == null) {
                return;
            }
            Throwable t = lastException;
            lastException = null;
            PlatformDependent.throwException(t);
        }

        @SuppressWarnings("unchecked")
        public <T> T readInbound() {
            return (T) inboundMessages.poll();
        }

        @SuppressWarnings("unchecked")
        public <T> T readUserEvent() {
            return (T) userEvents.poll();
        }

        public Channel channel() {
            return ctx.channel();
        }

        public void finishAndReleaseAll() throws Exception {
            checkException();
            Object o;
            while ((o = readInbound()) != null) {
                release(o);
            }
            while ((o = readUserEvent()) != null) {
                release(o);
            }
        }
    }
}
