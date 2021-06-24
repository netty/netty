/*
 * Copyright 2016 The Netty Project
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

package io.netty.handler.codec.http2;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import org.junit.AfterClass;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.concurrent.CountDownLatch;

import static io.netty.handler.codec.http2.Http2CodecUtil.isStreamIdValid;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for {@link Http2MultiplexCodec}.
 */
public class Http2MultiplexCodecBuilderTest {

    private static EventLoopGroup group;
    private Channel serverChannel;
    private volatile Channel serverConnectedChannel;
    private Channel clientChannel;
    private LastInboundHandler serverLastInboundHandler;

    @BeforeAll
    public static void init() {
        group = new DefaultEventLoop();
    }

    @BeforeEach
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
                        ch.pipeline().addLast(new Http2MultiplexCodecBuilder(true, new ChannelInitializer<Channel>() {

                            @Override
                            protected void initChannel(Channel ch) throws Exception {
                                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                    private boolean writable;

                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                        writable |= ctx.channel().isWritable();
                                        super.channelActive(ctx);
                                    }

                                    @Override
                                    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
                                        writable |= ctx.channel().isWritable();
                                        super.channelWritabilityChanged(ctx);
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                        assertTrue(writable);
                                        super.channelInactive(ctx);
                                    }
                                });
                                ch.pipeline().addLast(serverLastInboundHandler);
                            }
                        }).build());
                        serverChannelLatch.countDown();
                    }
                });
        serverChannel = sb.bind(serverAddress).sync().channel();

        Bootstrap cb = new Bootstrap()
                .channel(LocalChannel.class)
                .group(group)
                .handler(new Http2MultiplexCodecBuilder(false, new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        fail("Should not be called for outbound streams");
                    }
                }).build());
        clientChannel = cb.connect(serverAddress).sync().channel();
        assertTrue(serverChannelLatch.await(5, SECONDS));
    }

    @AfterClass
    public static void shutdown() {
        group.shutdownGracefully(0, 5, SECONDS);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (clientChannel != null) {
            clientChannel.close().syncUninterruptibly();
            clientChannel = null;
        }
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
            serverChannel = null;
        }
        final Channel serverConnectedChannel = this.serverConnectedChannel;
        if (serverConnectedChannel != null) {
            serverConnectedChannel.close().syncUninterruptibly();
            this.serverConnectedChannel = null;
        }
    }

    private Http2StreamChannel newOutboundStream(ChannelHandler handler) {
        return new Http2StreamChannelBootstrap(clientChannel).handler(handler).open().syncUninterruptibly().getNow();
    }

    @Test
    public void multipleOutboundStreams() throws Exception {
        Http2StreamChannel childChannel1 = newOutboundStream(new TestChannelInitializer());
        assertTrue(childChannel1.isActive());
        assertFalse(isStreamIdValid(childChannel1.stream().id()));
        Http2StreamChannel childChannel2 = newOutboundStream(new TestChannelInitializer());
        assertTrue(childChannel2.isActive());
        assertFalse(isStreamIdValid(childChannel2.stream().id()));

        Http2Headers headers1 = new DefaultHttp2Headers();
        Http2Headers headers2 = new DefaultHttp2Headers();
        // Test that streams can be made active (headers sent) in different order than the corresponding channels
        // have been created.
        childChannel2.writeAndFlush(new DefaultHttp2HeadersFrame(headers2));
        childChannel1.writeAndFlush(new DefaultHttp2HeadersFrame(headers1));

        Http2HeadersFrame headersFrame2 = serverLastInboundHandler.blockingReadInbound();
        assertNotNull(headersFrame2);
        assertEquals(3, headersFrame2.stream().id());

        Http2HeadersFrame headersFrame1 = serverLastInboundHandler.blockingReadInbound();
        assertNotNull(headersFrame1);
        assertEquals(5, headersFrame1.stream().id());

        assertEquals(3, childChannel2.stream().id());
        assertEquals(5, childChannel1.stream().id());

        childChannel1.close();
        childChannel2.close();

        serverLastInboundHandler.checkException();
    }

    @Test
    public void createOutboundStream() throws Exception {
        Channel childChannel = newOutboundStream(new TestChannelInitializer());
        assertTrue(childChannel.isRegistered());
        assertTrue(childChannel.isActive());

        Http2Headers headers = new DefaultHttp2Headers();
        childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(headers));
        ByteBuf data = Unpooled.buffer(100).writeZero(100);
        childChannel.writeAndFlush(new DefaultHttp2DataFrame(data, true));

        Http2HeadersFrame headersFrame = serverLastInboundHandler.blockingReadInbound();
        assertNotNull(headersFrame);
        assertEquals(3, headersFrame.stream().id());
        assertEquals(headers, headersFrame.headers());

        Http2DataFrame dataFrame = serverLastInboundHandler.blockingReadInbound();
        assertNotNull(dataFrame);
        assertEquals(3, dataFrame.stream().id());
        assertEquals(data.resetReaderIndex(), dataFrame.content());
        assertTrue(dataFrame.isEndStream());
        dataFrame.release();

        childChannel.close();

        Http2ResetFrame rstFrame = serverLastInboundHandler.blockingReadInbound();
        assertNotNull(rstFrame);
        assertEquals(3, rstFrame.stream().id());

        serverLastInboundHandler.checkException();
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

    private static class SharableChannelHandler1 extends ChannelHandlerAdapter {
        @Override
        public boolean isSharable() {
            return true;
        }
    }

    @Sharable
    private static class SharableChannelHandler2 extends ChannelHandlerAdapter {
    }

    private static class UnsharableChannelHandler extends ChannelHandlerAdapter {
        @Override
        public boolean isSharable() {
            return false;
        }
    }

    @Test
    public void testSharableCheck() {
        assertNotNull(Http2MultiplexCodecBuilder.forServer(new SharableChannelHandler1()));
        assertNotNull(Http2MultiplexCodecBuilder.forServer(new SharableChannelHandler2()));
    }

    @Test
    public void testUnsharableHandler() {
        assertThrows(IllegalArgumentException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                Http2MultiplexCodecBuilder.forServer(new UnsharableChannelHandler());
            }
        });
    }
}
