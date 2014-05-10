/*
 * Copyright 2014 The Netty Project
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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests the full HTTP/2 framing stack including the connection and preface handlers.
 */
public class Http2ConnectionRoundtripTest {

    @Mock
    private Http2FrameObserver clientObserver;

    @Mock
    private Http2FrameObserver serverObserver;

    private DelegatingHttp2ConnectionHandler http2Client;
    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private Channel clientChannel;
    private static final int NUM_STREAMS = 1000;
    private final CountDownLatch requestLatch = new CountDownLatch(NUM_STREAMS * 2);

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new DelegatingHttp2ConnectionHandler(true, new FrameCountDown()));
            }
        });

        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new DelegatingHttp2ConnectionHandler(false, serverObserver));
            }
        });

        serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
        http2Client = clientChannel.pipeline().get(DelegatingHttp2ConnectionHandler.class);
    }

    @After
    public void teardown() throws Exception {
        serverChannel.close().sync();
        sb.group().shutdownGracefully();
        cb.group().shutdownGracefully();
    }

    @Test
    public void stressTest() throws Exception {
        final Http2Headers headers =
                new DefaultHttp2Headers.Builder().method("GET").scheme("https")
                        .authority("example.org").path("/some/path/resource2").build();
        final String text = "hello world";

        for (int i = 0, nextStream = 3; i < NUM_STREAMS; ++i, nextStream += 2) {
            final int streamId = nextStream;
            clientChannel.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        http2Client.writeHeaders(ctx(), newPromise(), streamId, headers, 0,
                                (short) 16, false, 0, false, false);
                        http2Client.writeData(ctx(), newPromise(), streamId,
                                Unpooled.copiedBuffer(text.getBytes()), 0, true, true, false);
                    } catch (Http2Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        // Wait for all frames to be received.
        awaitRequests();
        verify(serverObserver, times(NUM_STREAMS)).onHeadersRead(any(ChannelHandlerContext.class),
                anyInt(), eq(headers), eq(0), eq((short) 16), eq(false), eq(0), eq(false),
                eq(false));
        verify(serverObserver, times(NUM_STREAMS)).onDataRead(any(ChannelHandlerContext.class),
                anyInt(), eq(Unpooled.copiedBuffer(text.getBytes())), eq(0), eq(true), eq(true), eq(false));
    }

    private void awaitRequests() throws Exception {
        requestLatch.await(5, SECONDS);
    }

    private ChannelHandlerContext ctx() {
        return clientChannel.pipeline().firstContext();
    }

    private ChannelPromise newPromise() {
        return ctx().newPromise();
    }

    /**
     * A decorator around the serverObserver that counts down the latch so that we can await the
     * completion of the request.
     */
    private final class FrameCountDown implements Http2FrameObserver {

        @Override
        public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                boolean endOfStream, boolean endOfSegment, boolean compressed)
                throws Http2Exception {
            serverObserver.onDataRead(ctx, streamId, copy(data), padding, endOfStream,
                    endOfSegment, compressed);
            requestLatch.countDown();
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                int padding, boolean endStream, boolean endSegment) throws Http2Exception {
            serverObserver.onHeadersRead(ctx, streamId, headers, padding, endStream, endSegment);
            requestLatch.countDown();
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                int streamDependency, short weight, boolean exclusive, int padding,
                boolean endStream, boolean endSegment) throws Http2Exception {
            serverObserver.onHeadersRead(ctx, streamId, headers, streamDependency, weight,
                    exclusive, padding, endStream, endSegment);
            requestLatch.countDown();
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                short weight, boolean exclusive) throws Http2Exception {
            serverObserver.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
            requestLatch.countDown();
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                throws Http2Exception {
            serverObserver.onRstStreamRead(ctx, streamId, errorCode);
            requestLatch.countDown();
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
            serverObserver.onSettingsAckRead(ctx);
            requestLatch.countDown();
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
            serverObserver.onSettingsRead(ctx, settings);
            requestLatch.countDown();
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            serverObserver.onPingRead(ctx, copy(data));
            requestLatch.countDown();
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            serverObserver.onPingAckRead(ctx, copy(data));
            requestLatch.countDown();
        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId,
                int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
            serverObserver.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
            requestLatch.countDown();
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
                throws Http2Exception {
            serverObserver.onGoAwayRead(ctx, lastStreamId, errorCode, copy(debugData));
            requestLatch.countDown();
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId,
                int windowSizeIncrement) throws Http2Exception {
            serverObserver.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
            requestLatch.countDown();
        }

        @Override
        public void onAltSvcRead(ChannelHandlerContext ctx, int streamId, long maxAge, int port,
                ByteBuf protocolId, String host, String origin) throws Http2Exception {
            serverObserver
                    .onAltSvcRead(ctx, streamId, maxAge, port, copy(protocolId), host, origin);
            requestLatch.countDown();
        }

        @Override
        public void onBlockedRead(ChannelHandlerContext ctx, int streamId) throws Http2Exception {
            serverObserver.onBlockedRead(ctx, streamId);
            requestLatch.countDown();
        }

        ByteBuf copy(ByteBuf buffer) {
            return Unpooled.copiedBuffer(buffer);
        }
    }
}
