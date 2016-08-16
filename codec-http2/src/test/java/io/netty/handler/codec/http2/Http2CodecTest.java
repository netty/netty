/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyShort;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link Http2Codec}.
 */
public class Http2CodecTest {

    private EmbeddedChannel channel;

    private Http2FrameWriter frameWriter;
    private TestChannelInitializer testChannelInitializer;

    @Before
    public void setUp() {
        frameWriter = spy(new VerifiableHttp2FrameWriter());
        testChannelInitializer = new TestChannelInitializer();
        Http2StreamChannelBootstrap bootstrap = new Http2StreamChannelBootstrap().handler(testChannelInitializer);
        channel = new EmbeddedChannel();
        channel.connect(new InetSocketAddress(0));
        channel.pipeline().addLast(new Http2Codec(true, bootstrap, frameWriter));
    }

    @After
    public void tearDown() throws Exception {
        channel.finishAndReleaseAll();
    }

    @Test
    public void multipleOutboundStreams() {
        Http2StreamChannelBootstrap b = new Http2StreamChannelBootstrap();
        b.parentChannel(channel).handler(testChannelInitializer);

        Channel channel1 = b.connect().channel();
        assertTrue(channel1.isActive());
        assertFalse(((AbstractHttp2StreamChannel) channel1).hasStreamId());
        Channel channel2 = b.connect().channel();
        assertTrue(channel2.isActive());
        assertFalse(((AbstractHttp2StreamChannel) channel2).hasStreamId());

        Http2Headers headers1 = new DefaultHttp2Headers();
        Http2Headers headers2 = new DefaultHttp2Headers();
        // Test that streams can be made active (headers sent) in different order than the corresponding channels
        // have been created.
        channel2.writeAndFlush(new DefaultHttp2HeadersFrame(headers2));
        channel1.writeAndFlush(new DefaultHttp2HeadersFrame(headers1));

        verify(frameWriter).writeHeaders(any(ChannelHandlerContext.class), eq(2), same(headers2), anyInt(), anyShort(),
                                         eq(false), eq(0), eq(false), any(ChannelPromise.class));
        verify(frameWriter).writeHeaders(any(ChannelHandlerContext.class), eq(4), same(headers1), anyInt(), anyShort(),
                                         eq(false), eq(0), eq(false), any(ChannelPromise.class));

        assertTrue(((AbstractHttp2StreamChannel) channel1).hasStreamId());
        assertTrue(((AbstractHttp2StreamChannel) channel2).hasStreamId());

        channel1.close();
        channel2.close();
    }

    @Test
    public void createOutboundStream() {
        Http2StreamChannelBootstrap b = new Http2StreamChannelBootstrap();
        ChannelFuture channelFuture = b.parentChannel(channel).handler(testChannelInitializer).connect();
        assertTrue(channelFuture.isSuccess());
        Channel childChannel = channelFuture.channel();
        assertTrue(childChannel.isRegistered());
        assertTrue(childChannel.isActive());

        Http2Headers headers = new DefaultHttp2Headers();
        childChannel.write(new DefaultHttp2HeadersFrame(headers));
        ByteBuf data = Unpooled.buffer(100).writeZero(100);
        childChannel.writeAndFlush(new DefaultHttp2DataFrame(data));
        verify(frameWriter).writeHeaders(any(ChannelHandlerContext.class), eq(2), eq(headers), anyInt(), anyShort(),
                                         eq(false), eq(0), eq(false), any(ChannelPromise.class));
        verify(frameWriter).writeData(any(ChannelHandlerContext.class), eq(2), eq(data), eq(0), eq(false),
                                      any(ChannelPromise.class));
        childChannel.close();
        verify(frameWriter).writeRstStream(any(ChannelHandlerContext.class), eq(2), eq(Http2Error.CANCEL.code()),
                                           any(ChannelPromise.class));
    }

    @Sharable
    private static class TestChannelInitializer extends ChannelInitializer<Channel> {
        ChannelHandler handler;

        @Override
        public void initChannel(Channel channel) {
            if (handler != null) {
                channel.pipeline().addLast(handler);
                handler = null;
            }
        }
    }

    private static class VerifiableHttp2FrameWriter extends DefaultHttp2FrameWriter {
        @Override
        public ChannelFuture writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                       int padding, boolean endStream, ChannelPromise promise) {
            // duplicate 'data' to prevent readerIndex from being changed, to ease verification
            return super.writeData(ctx, streamId, data.duplicate(), padding, endStream, promise);
        }
    }
}
