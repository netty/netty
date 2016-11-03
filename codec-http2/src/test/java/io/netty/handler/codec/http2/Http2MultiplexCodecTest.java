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
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.Http2Exception.StreamException;
import io.netty.util.AsciiString;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static io.netty.util.ReferenceCountUtil.release;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link Http2MultiplexCodec} and {@link Http2StreamChannelBootstrap}.
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
        Http2StreamChannelBootstrap bootstrap = new Http2StreamChannelBootstrap().handler(childChannelInitializer);
        parentChannel = new EmbeddedChannel();
        parentChannel.connect(new InetSocketAddress(0));
        parentChannel.pipeline().addLast(new Http2MultiplexCodec(true, bootstrap));
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
    // TODO(buchgr): Test ChannelConfig.setMaxMessagesPerRead

    @Test
    public void headerAndDataFramesShouldBeDelivered() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        childChannelInitializer.handler = inboundHandler;

        Http2StreamActiveEvent streamActive = new Http2StreamActiveEvent(streamId);
        Http2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(request).streamId(streamId);
        Http2DataFrame dataFrame1 = new DefaultHttp2DataFrame(bb("hello")).streamId(streamId);
        Http2DataFrame dataFrame2 = new DefaultHttp2DataFrame(bb("world")).streamId(streamId);

        assertFalse(inboundHandler.isChannelActive());
        parentChannel.pipeline().fireUserEventTriggered(streamActive);
        assertTrue(inboundHandler.isChannelActive());
        // Make sure the stream active event is not delivered as a user event on the child channel.
        assertNull(inboundHandler.readUserEvent());
        parentChannel.pipeline().fireChannelRead(headersFrame);
        parentChannel.pipeline().fireChannelRead(dataFrame1);
        parentChannel.pipeline().fireChannelRead(dataFrame2);

        assertEquals(headersFrame, inboundHandler.readInbound());
        assertEquals(dataFrame1, inboundHandler.readInbound());
        assertEquals(dataFrame2, inboundHandler.readInbound());
        assertNull(inboundHandler.readInbound());

        dataFrame1.release();
        dataFrame2.release();
    }

    @Test
    public void framesShouldBeMultiplexed() {
        LastInboundHandler inboundHandler3 = streamActiveAndWriteHeaders(3);
        LastInboundHandler inboundHandler11 = streamActiveAndWriteHeaders(11);
        LastInboundHandler inboundHandler5 = streamActiveAndWriteHeaders(5);

        verifyFramesMultiplexedToCorrectChannel(3, inboundHandler3, 1);
        verifyFramesMultiplexedToCorrectChannel(5, inboundHandler5, 1);
        verifyFramesMultiplexedToCorrectChannel(11, inboundHandler11, 1);

        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(bb("hello"), false).streamId(5));
        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(bb("foo"), true).streamId(3));
        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(bb("world"), true).streamId(5));
        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(bb("bar"), true).streamId(11));
        verifyFramesMultiplexedToCorrectChannel(5, inboundHandler5, 2);
        verifyFramesMultiplexedToCorrectChannel(3, inboundHandler3, 1);
        verifyFramesMultiplexedToCorrectChannel(11, inboundHandler11, 1);
    }

    @Test
    public void inboundDataFrameShouldEmitWindowUpdateFrame() {
        LastInboundHandler inboundHandler = streamActiveAndWriteHeaders(streamId);
        ByteBuf tenBytes = bb("0123456789");
        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(tenBytes, true).streamId(streamId));
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
                new DefaultHttp2DataFrame(bb("hello world"), false).streamId(streamId));
        parentChannel.pipeline().fireChannelReadComplete();
        Http2DataFrame dataFrame0 = inboundHandler.readInbound();
        assertNotNull(dataFrame0);
        release(dataFrame0);

        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(bb("foo"), false).streamId(streamId));
        parentChannel.pipeline().fireChannelRead(new DefaultHttp2DataFrame(bb("bar"), true).streamId(streamId));
        parentChannel.pipeline().fireChannelReadComplete();

        dataFrame0 = inboundHandler.readInbound();
        assertNull(dataFrame0);

        childChannel.config().setAutoRead(true);
        verifyFramesMultiplexedToCorrectChannel(streamId, inboundHandler, 2);
    }

    /**
     * A child channel for a HTTP/2 stream in IDLE state (that is no headers sent or received),
     * should not emit a RST_STREAM frame on close, as this is a connection error of type protocol error.
     */
    @Test
    public void idleOutboundStreamShouldNotWriteResetFrameOnClose() {
        childChannelInitializer.handler = new LastInboundHandler();

        Http2StreamChannelBootstrap b = new Http2StreamChannelBootstrap();
        b.parentChannel(parentChannel).handler(childChannelInitializer);
        Channel childChannel = b.connect().channel();
        assertTrue(childChannel.isActive());

        childChannel.close();
        parentChannel.runPendingTasks();

        assertFalse(childChannel.isOpen());
        assertFalse(childChannel.isActive());
        assertNull(parentChannel.readOutbound());
    }

    @Test
    public void outboundStreamShouldWriteResetFrameOnClose_headersSent() {
        childChannelInitializer.handler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                ctx.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));
                ctx.fireChannelActive();
            }
        };

        Http2StreamChannelBootstrap b = new Http2StreamChannelBootstrap();
        b.parentChannel(parentChannel).handler(childChannelInitializer);
        Channel childChannel = b.connect().channel();
        assertTrue(childChannel.isActive());

        Http2HeadersFrame headersFrame = parentChannel.readOutbound();
        assertNotNull(headersFrame);
        assertFalse(Http2CodecUtil.isStreamIdValid(headersFrame.streamId()));

        parentChannel.pipeline().fireUserEventTriggered(new Http2StreamActiveEvent(2, headersFrame));

        childChannel.close();
        parentChannel.runPendingTasks();

        Http2ResetFrame reset = parentChannel.readOutbound();
        assertEquals(2, reset.streamId());
        assertEquals(Http2Error.CANCEL.code(), reset.errorCode());
    }

    @Test
    public void inboundStreamClosedShouldFireChannelInactive() {
        LastInboundHandler inboundHandler = streamActiveAndWriteHeaders(streamId);
        assertTrue(inboundHandler.isChannelActive());

        parentChannel.pipeline().fireUserEventTriggered(new Http2StreamClosedEvent(streamId));
        parentChannel.runPendingTasks();
        parentChannel.flush();

        assertFalse(inboundHandler.isChannelActive());
        // A RST_STREAM frame should NOT be emitted, as we received the close.
        assertNull(parentChannel.readOutbound());
    }

    @Test(expected = StreamException.class)
    public void streamExceptionClosesChildChannel() throws Exception {
        LastInboundHandler inboundHandler = streamActiveAndWriteHeaders(streamId);

        assertTrue(inboundHandler.isChannelActive());
        StreamException e = new StreamException(streamId, Http2Error.PROTOCOL_ERROR, "baaam!");
        parentChannel.pipeline().fireExceptionCaught(e);
        parentChannel.runPendingTasks();

        assertFalse(inboundHandler.isChannelActive());
        inboundHandler.checkException();
    }

    @Test
    public void creatingWritingReadingAndClosingOutboundStreamShouldWork() {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        childChannelInitializer.handler = inboundHandler;

        Http2StreamChannelBootstrap b = new Http2StreamChannelBootstrap();
        b.parentChannel(parentChannel).handler(childChannelInitializer);
        AbstractHttp2StreamChannel childChannel = (AbstractHttp2StreamChannel) b.connect().channel();
        assertThat(childChannel, Matchers.instanceOf(Http2MultiplexCodec.Http2StreamChannel.class));
        assertTrue(childChannel.isActive());
        assertTrue(inboundHandler.isChannelActive());

        // Write to the child channel
        Http2Headers headers = new DefaultHttp2Headers().scheme("https").method("GET").path("/foo.txt");
        childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(headers));

        Http2HeadersFrame headersFrame = parentChannel.readOutbound();
        assertNotNull(headersFrame);
        assertSame(headers, headersFrame.headers());
        assertFalse(Http2CodecUtil.isStreamIdValid(headersFrame.streamId()));

        parentChannel.pipeline().fireUserEventTriggered(new Http2StreamActiveEvent(2, headersFrame));

        // Read from the child channel
        headers = new DefaultHttp2Headers().scheme("https").status("200");
        parentChannel.pipeline().fireChannelRead(new DefaultHttp2HeadersFrame(headers).streamId(
                childChannel.streamId()));
        parentChannel.pipeline().fireChannelReadComplete();

        headersFrame = inboundHandler.readInbound();
        assertNotNull(headersFrame);
        assertSame(headers, headersFrame.headers());

        // Close the child channel.
        childChannel.close();

        parentChannel.runPendingTasks();
        // An active outbound stream should emit a RST_STREAM frame.
        Http2ResetFrame rstFrame = parentChannel.readOutbound();
        assertNotNull(rstFrame);
        assertEquals(childChannel.streamId(), rstFrame.streamId());
        assertFalse(childChannel.isOpen());
        assertFalse(childChannel.isActive());
        assertFalse(inboundHandler.isChannelActive());
    }

    /**
     * Test failing the promise of the first headers frame of an outbound stream. In practice this error case would most
     * likely happen due to the max concurrent streams limit being hit or the channel running out of stream identifiers.
     */
    @Test(expected = Http2NoMoreStreamIdsException.class)
    public void failedOutboundStreamCreationThrowsAndClosesChannel() throws Exception {
        parentChannel.pipeline().addFirst(new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                promise.tryFailure(new Http2NoMoreStreamIdsException());
            }
        });

        LastInboundHandler inboundHandler = new LastInboundHandler();
        childChannelInitializer.handler = inboundHandler;

        Http2StreamChannelBootstrap b = new Http2StreamChannelBootstrap();
        Channel childChannel = b.parentChannel(parentChannel).handler(childChannelInitializer).connect().channel();
        assertTrue(childChannel.isActive());

        childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(new DefaultHttp2Headers()));
        parentChannel.flush();

        assertFalse(childChannel.isActive());
        assertFalse(childChannel.isOpen());

        inboundHandler.checkException();
    }

    @Test
    public void settingChannelOptsAndAttrsOnBootstrap() {
        AttributeKey<String> key = AttributeKey.newInstance("foo");
        WriteBufferWaterMark mark = new WriteBufferWaterMark(1024, 4096);
        Http2StreamChannelBootstrap b = new Http2StreamChannelBootstrap();
        b.parentChannel(parentChannel).handler(childChannelInitializer)
         .option(ChannelOption.AUTO_READ, false).option(ChannelOption.WRITE_BUFFER_WATER_MARK, mark)
         .attr(key, "bar");

        Channel channel = b.connect().channel();

        assertFalse(channel.config().isAutoRead());
        assertSame(mark, channel.config().getWriteBufferWaterMark());
        assertEquals("bar", channel.attr(key).get());
    }

    private LastInboundHandler streamActiveAndWriteHeaders(int streamId) {
        LastInboundHandler inboundHandler = new LastInboundHandler();
        childChannelInitializer.handler = inboundHandler;
        assertFalse(inboundHandler.isChannelActive());
        parentChannel.pipeline().fireUserEventTriggered(new Http2StreamActiveEvent(streamId));
        assertTrue(inboundHandler.isChannelActive());
        parentChannel.pipeline().fireChannelRead(new DefaultHttp2HeadersFrame(request).streamId(streamId));
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

    private static ByteBuf bb(String s) {
        return ByteBufUtil.writeUtf8(UnpooledByteBufAllocator.DEFAULT, s);
    }
}
