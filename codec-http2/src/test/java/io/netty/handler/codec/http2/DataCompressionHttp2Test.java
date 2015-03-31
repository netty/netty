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

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2TestUtil.as;
import static io.netty.handler.codec.http2.Http2TestUtil.runInChannel;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.Http2TestUtil.FrameAdapter;
import io.netty.handler.codec.http2.Http2TestUtil.FrameCountDown;
import io.netty.handler.codec.http2.Http2TestUtil.Http2Runnable;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Test for data decompression in the HTTP/2 codec.
 */
public class DataCompressionHttp2Test {
    private static final AsciiString GET = as("GET");
    private static final AsciiString POST = as("POST");
    private static final AsciiString PATH = as("/some/path");

    @Mock
    private Http2FrameListener serverListener;
    @Mock
    private Http2FrameListener clientListener;

    private Http2ConnectionEncoder clientEncoder;
    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private Channel clientChannel;
    private CountDownLatch serverLatch;
    private CountDownLatch clientLatch;
    private CountDownLatch clientSettingsAckLatch;
    private Http2Connection serverConnection;
    private Http2Connection clientConnection;
    private ByteArrayOutputStream serverOut;

    @Before
    public void setup() throws InterruptedException {
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void cleaup() throws IOException {
        serverOut.close();
    }

    @After
    public void teardown() throws InterruptedException {
        serverChannel.close().sync();
        Future<?> serverGroup = sb.group().shutdownGracefully(0, 0, MILLISECONDS);
        Future<?> serverChildGroup = sb.childGroup().shutdownGracefully(0, 0, MILLISECONDS);
        Future<?> clientGroup = cb.group().shutdownGracefully(0, 0, MILLISECONDS);
        serverGroup.sync();
        serverChildGroup.sync();
        clientGroup.sync();
    }

    @Test
    public void justHeadersNoData() throws Exception {
        bootstrapEnv(1, 1, 0, 1);
        final Http2Headers headers = new DefaultHttp2Headers().method(GET).path(PATH)
                .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);

        // Required because the decompressor intercepts the onXXXRead events before
        // our {@link Http2TestUtil$FrameAdapter} does.
        FrameAdapter.getOrCreateStream(serverConnection, 3, false);
        FrameAdapter.getOrCreateStream(clientConnection, 3, false);
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() {
                clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, true, newPromiseClient());
                ctxClient().flush();
            }
        });
        awaitServer();
        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(headers), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true));
    }

    @Test
    public void gzipEncodingSingleEmptyMessage() throws Exception {
        final String text = "";
        final ByteBuf data = Unpooled.copiedBuffer(text.getBytes());
        bootstrapEnv(1, 1, data.readableBytes(), 1);
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);

            // Required because the decompressor intercepts the onXXXRead events before
            // our {@link Http2TestUtil$FrameAdapter} does.
            Http2Stream stream = FrameAdapter.getOrCreateStream(serverConnection, 3, false);
            FrameAdapter.getOrCreateStream(clientConnection, 3, false);
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitServer();
            assertEquals(0, serverConnection.local().flowController().unconsumedBytes(stream));
            assertEquals(text, serverOut.toString(CharsetUtil.UTF_8.name()));
        } finally {
            data.release();
        }
    }

    @Test
    public void gzipEncodingSingleMessage() throws Exception {
        final String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final ByteBuf data = Unpooled.copiedBuffer(text.getBytes());
        bootstrapEnv(1, 1, data.readableBytes(), 1);
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);

            // Required because the decompressor intercepts the onXXXRead events before
            // our {@link Http2TestUtil$FrameAdapter} does.
            Http2Stream stream = FrameAdapter.getOrCreateStream(serverConnection, 3, false);
            FrameAdapter.getOrCreateStream(clientConnection, 3, false);
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitServer();
            assertEquals(0, serverConnection.local().flowController().unconsumedBytes(stream));
            assertEquals(text, serverOut.toString(CharsetUtil.UTF_8.name()));
        } finally {
            data.release();
        }
    }

    @Test
    public void gzipEncodingMultipleMessages() throws Exception {
        final String text1 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final String text2 = "dddddddddddddddddddeeeeeeeeeeeeeeeeeeeffffffffffffffffffff";
        final ByteBuf data1 = Unpooled.copiedBuffer(text1.getBytes());
        final ByteBuf data2 = Unpooled.copiedBuffer(text2.getBytes());
        bootstrapEnv(1, 1, data1.readableBytes() + data2.readableBytes(), 1);
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);

            // Required because the decompressor intercepts the onXXXRead events before
            // our {@link Http2TestUtil$FrameAdapter} does.
            Http2Stream stream = FrameAdapter.getOrCreateStream(serverConnection, 3, false);
            FrameAdapter.getOrCreateStream(clientConnection, 3, false);
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data1.retain(), 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data2.retain(), 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitServer();
            assertEquals(0, serverConnection.local().flowController().unconsumedBytes(stream));
            assertEquals(text1 + text2,
                    serverOut.toString(CharsetUtil.UTF_8.name()));
        } finally {
            data1.release();
            data2.release();
        }
    }

    @Test
    public void deflateEncodingWriteLargeMessage() throws Exception {
        final int BUFFER_SIZE = 1 << 12;
        final byte[] bytes = new byte[BUFFER_SIZE];
        new Random().nextBytes(bytes);
        bootstrapEnv(1, 1, BUFFER_SIZE, 1);
        final ByteBuf data = Unpooled.wrappedBuffer(bytes);
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.DEFLATE);

            // Required because the decompressor intercepts the onXXXRead events before
            // our {@link Http2TestUtil$FrameAdapter} does.
            Http2Stream stream = FrameAdapter.getOrCreateStream(serverConnection, 3, false);
            FrameAdapter.getOrCreateStream(clientConnection, 3, false);
            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), 0, true, newPromiseClient());
                    ctxClient().flush();
                }
            });
            awaitServer();
            assertEquals(0, serverConnection.local().flowController().unconsumedBytes(stream));
            assertEquals(data.resetReaderIndex().toString(CharsetUtil.UTF_8),
                    serverOut.toString(CharsetUtil.UTF_8.name()));
        } finally {
            data.release();
        }
    }

    private void bootstrapEnv(int serverHalfClosedCount, int clientSettingsAckLatchCount,
            int serverOutSize, int clientCount) throws Exception {
        serverOut = new ByteArrayOutputStream(serverOutSize);
        serverLatch = new CountDownLatch(serverHalfClosedCount);
        clientLatch = new CountDownLatch(clientCount);
        clientSettingsAckLatch = new CountDownLatch(clientSettingsAckLatchCount);
        sb = new ServerBootstrap();
        cb = new Bootstrap();

        // Streams are created before the normal flow for this test, so these connection must be initialized up front.
        serverConnection = new DefaultHttp2Connection(true);
        clientConnection = new DefaultHttp2Connection(false);

        serverConnection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void onStreamHalfClosed(Http2Stream stream) {
                serverLatch.countDown();
            }
        });

        doAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock in) throws Throwable {
                ByteBuf buf = (ByteBuf) in.getArguments()[2];
                int padding = (Integer) in.getArguments()[3];
                int processedBytes = buf.readableBytes() + padding;

                buf.readBytes(serverOut, buf.readableBytes());
                return processedBytes;
            }
        }).when(serverListener).onDataRead(any(ChannelHandlerContext.class), anyInt(),
                any(ByteBuf.class), anyInt(), anyBoolean());

        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                Http2ConnectionEncoder encoder = new CompressorHttp2ConnectionEncoder(
                        new DefaultHttp2ConnectionEncoder(serverConnection, new DefaultHttp2FrameWriter()));
                Http2ConnectionDecoder decoder =
                        new DefaultHttp2ConnectionDecoder(serverConnection, encoder, new DefaultHttp2FrameReader(),
                                new DelegatingDecompressorFrameListener(serverConnection,
                                        serverListener));
                Http2ConnectionHandler connectionHandler = new Http2ConnectionHandler(decoder, encoder);
                p.addLast(connectionHandler);
                serverChannelLatch.countDown();
            }
        });

        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                FrameCountDown clientFrameCountDown = new FrameCountDown(clientListener,
                        clientSettingsAckLatch, clientLatch);
                clientEncoder = new CompressorHttp2ConnectionEncoder(
                        new DefaultHttp2ConnectionEncoder(clientConnection, new DefaultHttp2FrameWriter()));
                Http2ConnectionDecoder decoder =
                        new DefaultHttp2ConnectionDecoder(clientConnection, clientEncoder,
                                new DefaultHttp2FrameReader(),
                                new DelegatingDecompressorFrameListener(clientConnection,
                                        clientFrameCountDown));
                Http2ConnectionHandler connectionHandler = new Http2ConnectionHandler(decoder, clientEncoder);
                p.addLast(connectionHandler);
            }
        });

        serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
        assertTrue(serverChannelLatch.await(5, SECONDS));
    }

    private void awaitServer() throws Exception {
        assertTrue(clientSettingsAckLatch.await(5, SECONDS));
        assertTrue(serverLatch.await(5, SECONDS));
        serverOut.flush();
    }

    private ChannelHandlerContext ctxClient() {
        return clientChannel.pipeline().firstContext();
    }

    private ChannelPromise newPromiseClient() {
        return ctxClient().newPromise();
    }
}
