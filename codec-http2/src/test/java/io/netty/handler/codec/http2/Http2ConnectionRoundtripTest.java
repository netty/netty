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
import io.netty.handler.codec.http2.Http2TestUtil.Http2Runnable;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests the full HTTP/2 framing stack including the connection and preface handlers.
 */
public class Http2ConnectionRoundtripTest {
    private static final int STRESS_TIMEOUT_SECONDS = 30;
    private static final int NUM_STREAMS = 5000;
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
    private Http2TestUtil.FrameCountDown serverFrameCountDown;
    private CountDownLatch requestLatch;
    private CountDownLatch dataLatch;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        requestLatch(new CountDownLatch(NUM_STREAMS * 3));
        dataLatch(new CountDownLatch(NUM_STREAMS * DATA_TEXT.length));
        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                serverFrameCountDown = new Http2TestUtil.FrameCountDown(serverListener, requestLatch, dataLatch);
                p.addLast(new DelegatingHttp2ConnectionHandler(true, serverFrameCountDown));
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
        Future<?> serverGroup = sb.group().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        Future<?> serverChildGroup = sb.childGroup().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        Future<?> clientGroup = cb.group().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        serverGroup.sync();
        serverChildGroup.sync();
        clientGroup.sync();
    }

    @Test
    public void flowControlProperlyChunksLargeMessage() throws Exception {
        final Http2Headers headers = new DefaultHttp2Headers.Builder().method("GET").scheme("https")
                .authority("example.org").path("/some/path/resource2").build();

        // Create a large message to send.
        final int length = 10485760; // 10MB

        // Create a buffer filled with random bytes.
        final byte[] bytes = new byte[length];
        new Random().nextBytes(bytes);
        final ByteBuf data = Unpooled.wrappedBuffer(bytes);
        List<ByteBuf> capturedData = null;
        try {
            // Initialize the data latch based on the number of bytes expected.
            requestLatch(new CountDownLatch(2));
            dataLatch(new CountDownLatch(length));

            // Create the stream and send all of the data at once.
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    http2Client.writeHeaders(ctx(), 3, headers, 0, (short) 16, false, 0, false, newPromise());
                    http2Client.writeData(ctx(), 3, data.retain(), 0, true, newPromise());
                }
            });

            // Wait for all DATA frames to be received at the server.
            assertTrue(dataLatch.await(5, TimeUnit.SECONDS));

            // Verify that headers were received and only one DATA frame was received with endStream set.
            final ArgumentCaptor<ByteBuf> dataCaptor = ArgumentCaptor.forClass(ByteBuf.class);
            verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(headers), eq(0),
                    eq((short) 16), eq(false), eq(0), eq(false));
            verify(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3), dataCaptor.capture(), eq(0),
                    eq(true));

            // Verify we received all the bytes.
            capturedData = dataCaptor.getAllValues();
            assertEquals(data, capturedData.get(0));
        } finally {
            data.release();
            release(capturedData);
        }
    }

    @Test
    public void stressTest() throws Exception {
        final Http2Headers headers = new DefaultHttp2Headers.Builder().method("GET").scheme("https")
                .authority("example.org").path("/some/path/resource2").build();
        final String text = "hello world";
        final String pingMsg = "12345678";
        final ByteBuf data = Unpooled.copiedBuffer(text.getBytes());
        final ByteBuf pingData = Unpooled.copiedBuffer(pingMsg.getBytes());
        List<ByteBuf> capturedData = null;
        List<ByteBuf> capturedPingData = null;
        try {
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    for (int i = 0, nextStream = 3; i < NUM_STREAMS; ++i, nextStream += 2) {
                        http2Client.writeHeaders(ctx(), nextStream, headers, 0, (short) 16, false, 0, false,
                                newPromise());
                        http2Client.writePing(ctx(), pingData.retain(), newPromise());
                        http2Client.writeData(ctx(), nextStream, data.retain(), 0, true, newPromise());
                    }
                }
            });
            // Wait for all frames to be received.
            assertTrue(requestLatch.await(STRESS_TIMEOUT_SECONDS, SECONDS));
            verify(serverListener, times(NUM_STREAMS)).onHeadersRead(any(ChannelHandlerContext.class), anyInt(),
                    eq(headers), eq(0), eq((short) 16), eq(false), eq(0), eq(false));
            final ArgumentCaptor<ByteBuf> dataCaptor = ArgumentCaptor.forClass(ByteBuf.class);
            final ArgumentCaptor<ByteBuf> pingDataCaptor = ArgumentCaptor.forClass(ByteBuf.class);
            verify(serverListener, times(NUM_STREAMS)).onPingRead(any(ChannelHandlerContext.class),
                    pingDataCaptor.capture());
            capturedPingData = pingDataCaptor.getAllValues();
            verify(serverListener, times(NUM_STREAMS)).onDataRead(any(ChannelHandlerContext.class), anyInt(),
                    dataCaptor.capture(), eq(0), eq(true));
            capturedData = dataCaptor.getAllValues();
            data.resetReaderIndex();
            pingData.resetReaderIndex();
            int i;
            for (i = 0; i < capturedPingData.size(); ++i) {
                assertEquals(pingData, capturedPingData.get(i));
            }
            for (i = 0; i < capturedData.size(); ++i) {
                assertEquals(capturedData.get(i).toString(CharsetUtil.UTF_8), data, capturedData.get(i));
            }
        } finally {
            data.release();
            pingData.release();
            release(capturedData);
            release(capturedPingData);
        }
    }

    private void dataLatch(CountDownLatch latch) {
        dataLatch = latch;
        if (serverFrameCountDown != null) {
            serverFrameCountDown.dataLatch(latch);
        }
    }

    private void requestLatch(CountDownLatch latch) {
        requestLatch = latch;
        if (serverFrameCountDown != null) {
            serverFrameCountDown.messageLatch(latch);
        }
    }

    private ChannelHandlerContext ctx() {
        return clientChannel.pipeline().firstContext();
    }

    private ChannelPromise newPromise() {
        return ctx().newPromise();
    }

    private static void release(List<ByteBuf> capturedData) {
        if (capturedData != null) {
            for (int i = 0; i < capturedData.size(); ++i) {
                capturedData.get(i).release();
            }
        }
    }
}
