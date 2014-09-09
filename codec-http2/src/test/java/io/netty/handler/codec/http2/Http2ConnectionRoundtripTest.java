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

import static io.netty.handler.codec.http2.Http2TestUtil.runInChannel;
import static io.netty.util.CharsetUtil.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import io.netty.handler.codec.http2.Http2TestUtil.Http2Runnable;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Tests the full HTTP/2 framing stack including the connection and preface handlers.
 */
public class Http2ConnectionRoundtripTest {

    private static final int NUM_STREAMS = 1000;
    private final byte[] DATA_TEXT = "hello world".getBytes(UTF_8);

    @Mock
    private Http2FrameListener clientListener;

    @Mock
    private Http2FrameListener serverListener;

    private DelegatingHttp2ConnectionHandler http2Client;
    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private Channel clientChannel;
    private final CountDownLatch requestLatch = new CountDownLatch(NUM_STREAMS * 3);
    private CountDownLatch dataLatch = new CountDownLatch(NUM_STREAMS * DATA_TEXT.length);

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
                p.addLast(Http2CodecUtil.ignoreSettingsHandler());
            }
        });

        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new DelegatingHttp2ConnectionHandler(false, clientListener));
                p.addLast(Http2CodecUtil.ignoreSettingsHandler());
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
        sb.childGroup().shutdownGracefully();
        cb.group().shutdownGracefully();
    }

    @Test
    public void flowControlProperlyChunksLargeMessage() throws Exception {
        final Http2Headers headers =
                new DefaultHttp2Headers.Builder().method("GET").scheme("https")
                        .authority("example.org").path("/some/path/resource2").build();

        // Create a large message to send.
        int length = 10485760; // 10MB

        // Create a buffer filled with random bytes.
        byte[] bytes = new byte[length];
        new Random().nextBytes(bytes);
        final ByteBuf data = Unpooled.wrappedBuffer(bytes);

        // Prepare a receive buffer and populate it as DATA frames are received by the server.
        final ByteBuf receivedData = Unpooled.buffer(length);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                ByteBuf buf = (ByteBuf) invocation.getArguments()[2];
                receivedData.writeBytes(buf);
                return null;
            }
        }).when(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3),
                any(ByteBuf.class), eq(0), anyBoolean());

        // Initialize the data latch based on the number of bytes expected.
        dataLatch = new CountDownLatch(length);

        // Create the stream and send all of the data at once.
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                http2Client.writeHeaders(ctx(), 3, headers, 0, (short) 16, false, 0, false,
                        newPromise());
                http2Client.writeData(ctx(), 3, data.copy(), 0, true, newPromise());
            }
        });

        // Wait for all DATA frames to be received at the server.
        assertTrue(dataLatch.await(5, TimeUnit.SECONDS));

        // Verify that headers were received and only one DATA frame was received with endStream set.
        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(headers),
                eq(0), eq((short) 16), eq(false), eq(0), eq(false));
        verify(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3),
                any(ByteBuf.class), eq(0), eq(true));

        // Verify we received all the bytes.
        assertEquals(data, receivedData);
    }

    @Test
    public void stressTest() throws Exception {
        final Http2Headers headers =
                new DefaultHttp2Headers.Builder().method("GET").scheme("https")
                        .authority("example.org").path("/some/path/resource2").build();
        final String text = "hello world";
        final String pingMsg = "12345678";
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                for (int i = 0, nextStream = 3; i < NUM_STREAMS; ++i, nextStream += 2) {
                    http2Client.writeHeaders(ctx(), nextStream, headers, 0, (short) 16, false, 0,
                            false, newPromise());
                    http2Client.writePing(ctx(), Unpooled.copiedBuffer(pingMsg.getBytes()),
                            newPromise());
                    http2Client.writeData(ctx(), nextStream,
                            Unpooled.copiedBuffer(text.getBytes()), 0, true, newPromise());
                }
            }
        });
        // Wait for all frames to be received.
        assertTrue(requestLatch.await(5, SECONDS));
        verify(serverListener, times(NUM_STREAMS)).onHeadersRead(any(ChannelHandlerContext.class),
                anyInt(), eq(headers), eq(0), eq((short) 16), eq(false), eq(0), eq(false));
        verify(serverListener, times(NUM_STREAMS)).onPingRead(any(ChannelHandlerContext.class),
                eq(Unpooled.copiedBuffer(pingMsg.getBytes())));
        verify(serverListener, times(NUM_STREAMS)).onDataRead(any(ChannelHandlerContext.class),
                anyInt(), eq(Unpooled.copiedBuffer(text.getBytes())), eq(0), eq(true));
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
    private final class FrameCountDown implements Http2FrameListener {

        @Override
        public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                               boolean endOfStream)
                throws Http2Exception {
            serverListener.onDataRead(ctx, streamId, copy(data), padding, endOfStream);
            requestLatch.countDown();
            for (int i = 0; i < data.readableBytes(); ++i) {
                dataLatch.countDown();
            }
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                  int padding, boolean endStream) throws Http2Exception {
            serverListener.onHeadersRead(ctx, streamId, headers, padding, endStream);
            requestLatch.countDown();
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                  int streamDependency, short weight, boolean exclusive, int padding,
                                  boolean endStream) throws Http2Exception {
            serverListener.onHeadersRead(ctx, streamId, headers, streamDependency, weight,
                    exclusive, padding, endStream);
            requestLatch.countDown();
        }

        @Override
        public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
                                   short weight, boolean exclusive) throws Http2Exception {
            serverListener.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
            requestLatch.countDown();
        }

        @Override
        public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                throws Http2Exception {
            serverListener.onRstStreamRead(ctx, streamId, errorCode);
            requestLatch.countDown();
        }

        @Override
        public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
            serverListener.onSettingsAckRead(ctx);
            requestLatch.countDown();
        }

        @Override
        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception {
            serverListener.onSettingsRead(ctx, settings);
            requestLatch.countDown();
        }

        @Override
        public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            serverListener.onPingRead(ctx, copy(data));
            requestLatch.countDown();
        }

        @Override
        public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
            serverListener.onPingAckRead(ctx, copy(data));
            requestLatch.countDown();
        }

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId,
                                      int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
            serverListener.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
            requestLatch.countDown();
        }

        @Override
        public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
                throws Http2Exception {
            serverListener.onGoAwayRead(ctx, lastStreamId, errorCode, copy(debugData));
            requestLatch.countDown();
        }

        @Override
        public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId,
                                       int windowSizeIncrement) throws Http2Exception {
            serverListener.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
            requestLatch.countDown();
        }

        @Override
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                Http2Flags flags, ByteBuf payload) {
            serverListener.onUnknownFrame(ctx, frameType, streamId, flags, payload);
            requestLatch.countDown();
        }

        ByteBuf copy(ByteBuf buffer) {
            return Unpooled.copiedBuffer(buffer);
        }
    }
}
