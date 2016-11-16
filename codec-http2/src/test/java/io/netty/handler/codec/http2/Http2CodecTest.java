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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http2.Http2CodecUtil.isStreamIdValid;
import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link Http2Codec}.
 */
public class Http2CodecTest {

    private static EventLoopGroup group;
    private Channel serverChannel;
    private volatile Channel serverConnectedChannel;
    private Channel clientChannel;
    private LastInboundHandler serverLastInboundHandler;

    @BeforeClass
    public static void init() {
        group = new DefaultEventLoop();
    }

    @Before
    public void setUp() throws InterruptedException {
        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        LocalAddress serverAddress = new LocalAddress(getClass().getName());
        serverLastInboundHandler = new SharableLastInboundHandler();
        ServerBootstrap sb = new ServerBootstrap()
                .channel(LocalServerChannel.class)
                .group(group)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        serverConnectedChannel = ch;
                        ch.pipeline().addLast(new Http2Codec(true, serverLastInboundHandler));
                        serverChannelLatch.countDown();
                    }
                });
        serverChannel = sb.bind(serverAddress).sync().channel();

        Bootstrap cb = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .handler(new Http2Codec(false, new TestChannelInitializer()));
        clientChannel = cb.connect(serverAddress).sync().channel();
        assertTrue(serverChannelLatch.await(2, TimeUnit.SECONDS));
    }

    @AfterClass
    public static void shutdown() {
        group.shutdownGracefully();
    }

    @After
    public void tearDown() throws Exception {
        clientChannel.close().sync();
        serverChannel.close().sync();
        if (serverConnectedChannel != null) {
            serverConnectedChannel.close().sync();
            serverConnectedChannel = null;
        }
    }

    @Test
    public void multipleOutboundStreams() {
        Http2StreamChannelBootstrap b = new Http2StreamChannelBootstrap();
        b.parentChannel(clientChannel).handler(new TestChannelInitializer());

        Channel childChannel1 = b.connect().syncUninterruptibly().channel();
        assertTrue(childChannel1.isActive());
        assertFalse(isStreamIdValid(((AbstractHttp2StreamChannel) childChannel1).streamId()));
        Channel childChannel2 = b.connect().channel();
        assertTrue(childChannel2.isActive());
        assertFalse(isStreamIdValid(((AbstractHttp2StreamChannel) childChannel2).streamId()));

        Http2Headers headers1 = new DefaultHttp2Headers();
        Http2Headers headers2 = new DefaultHttp2Headers();
        // Test that streams can be made active (headers sent) in different order than the corresponding channels
        // have been created.
        childChannel2.writeAndFlush(new DefaultHttp2HeadersFrame(headers2));
        childChannel1.writeAndFlush(new DefaultHttp2HeadersFrame(headers1));

        Http2HeadersFrame headersFrame2 = serverLastInboundHandler.blockingReadInbound();
        assertNotNull(headersFrame2);
        assertEquals(3, headersFrame2.streamId());

        Http2HeadersFrame headersFrame1 = serverLastInboundHandler.blockingReadInbound();
        assertNotNull(headersFrame1);
        assertEquals(5, headersFrame1.streamId());

        assertEquals(3, ((AbstractHttp2StreamChannel) childChannel2).streamId());
        assertEquals(5, ((AbstractHttp2StreamChannel) childChannel1).streamId());

        childChannel1.close();
        childChannel2.close();
    }

    @Test
    public void createOutboundStream() {
        Http2StreamChannelBootstrap b = new Http2StreamChannelBootstrap();
        Channel childChannel = b.parentChannel(clientChannel).handler(new TestChannelInitializer())
                                       .connect().syncUninterruptibly().channel();
        assertTrue(childChannel.isRegistered());
        assertTrue(childChannel.isActive());

        Http2Headers headers = new DefaultHttp2Headers();
        childChannel.write(new DefaultHttp2HeadersFrame(headers));
        ByteBuf data = Unpooled.buffer(100).writeZero(100);
        childChannel.writeAndFlush(new DefaultHttp2DataFrame(data, true));

        Http2HeadersFrame headersFrame = serverLastInboundHandler.blockingReadInbound();
        assertNotNull(headersFrame);
        assertEquals(3, headersFrame.streamId());
        assertEquals(headers, headersFrame.headers());

        Http2DataFrame dataFrame = serverLastInboundHandler.blockingReadInbound();
        assertNotNull(dataFrame);
        assertEquals(3, dataFrame.streamId());
        assertEquals(data.resetReaderIndex(), dataFrame.content());
        assertTrue(dataFrame.isEndStream());
        dataFrame.release();

        childChannel.close();

        Http2ResetFrame rstFrame = serverLastInboundHandler.blockingReadInbound();
        assertNotNull(rstFrame);
        assertEquals(3, rstFrame.streamId());
    }

    @Sharable
    private static class SharableLastInboundHandler extends LastInboundHandler {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelActive();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelInactive();
        }
    }
}
