/*
 * Copyright 2014 The Netty Project
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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.Http2TestUtil.Http2Runnable;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2TestUtil.runInChannel;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

/**
 * Test for data decompression in the HTTP/2 codec.
 */
public class DataCompressionHttp2Test {
    private static final AsciiString GET = new AsciiString("GET");
    private static final AsciiString POST = new AsciiString("POST");
    private static final AsciiString PATH = new AsciiString("/some/path");

    @Mock
    private Http2FrameListener serverListener;
    @Mock
    private Http2FrameListener clientListener;

    private Http2ConnectionEncoder clientEncoder;
    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private Channel clientChannel;
    private volatile Channel serverConnectedChannel;
    private CountDownLatch serverLatch;
    private Http2Connection serverConnection;
    private Http2Connection clientConnection;
    private Http2ConnectionHandler clientHandler;
    private ByteArrayOutputStream serverOut;

    @BeforeAll
    public static void beforeAllTests() throws Throwable {
        Brotli.ensureAvailability();
    }

    @BeforeEach
    public void setup() throws InterruptedException, Http2Exception {
        MockitoAnnotations.initMocks(this);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                if (invocation.getArgument(4)) {
                    serverConnection.stream((Integer) invocation.getArgument(1)).close();
                }
                return null;
            }
        }).when(serverListener).onHeadersRead(any(ChannelHandlerContext.class), anyInt(), any(Http2Headers.class),
                anyInt(), anyBoolean());
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                if (invocation.getArgument(7)) {
                    serverConnection.stream((Integer) invocation.getArgument(1)).close();
                }
                return null;
            }
        }).when(serverListener).onHeadersRead(any(ChannelHandlerContext.class), anyInt(), any(Http2Headers.class),
                anyInt(), anyShort(), anyBoolean(), anyInt(), anyBoolean());
    }

    @AfterEach
    public void cleanup() throws IOException {
        serverOut.close();
    }

    @AfterEach
    public void teardown() throws InterruptedException {
        if (clientChannel != null) {
            clientChannel.close().sync();
            clientChannel = null;
        }
        if (serverChannel != null) {
            serverChannel.close().sync();
            serverChannel = null;
        }
        final Channel serverConnectedChannel = this.serverConnectedChannel;
        if (serverConnectedChannel != null) {
            serverConnectedChannel.close().sync();
            this.serverConnectedChannel = null;
        }
        Future<?> serverGroup = sb.config().group().shutdownGracefully(0, 0, MILLISECONDS);
        Future<?> serverChildGroup = sb.config().childGroup().shutdownGracefully(0, 0, MILLISECONDS);
        Future<?> clientGroup = cb.config().group().shutdownGracefully(0, 0, MILLISECONDS);
        serverGroup.sync();
        serverChildGroup.sync();
        clientGroup.sync();
    }

    @Test
    public void justHeadersNoData() throws Exception {
        bootstrapEnv(0);
        final Http2Headers headers = new DefaultHttp2Headers().method(GET).path(PATH)
                .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);

        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, true, newPromiseClient());
                clientHandler.flush(ctxClient());
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
        bootstrapEnv(data.readableBytes());
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);

            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), 0, true, newPromiseClient());
                    clientHandler.flush(ctxClient());
                }
            });
            awaitServer();
            assertEquals(text, serverOut.toString(CharsetUtil.UTF_8.name()));
        } finally {
            data.release();
        }
    }

    @Test
    public void gzipEncodingSingleMessage() throws Exception {
        final String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final ByteBuf data = Unpooled.copiedBuffer(text.getBytes());
        bootstrapEnv(data.readableBytes());
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);

            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), 0, true, newPromiseClient());
                    clientHandler.flush(ctxClient());
                }
            });
            awaitServer();
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
        bootstrapEnv(data1.readableBytes() + data2.readableBytes());
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);

            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data1.retain(), 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data2.retain(), 0, true, newPromiseClient());
                    clientHandler.flush(ctxClient());
                }
            });
            awaitServer();
            assertEquals(text1 + text2, serverOut.toString(CharsetUtil.UTF_8.name()));
        } finally {
            data1.release();
            data2.release();
        }
    }

    @Test
    public void brotliEncodingSingleEmptyMessage() throws Exception {
        final String text = "";
        final ByteBuf data = Unpooled.copiedBuffer(text.getBytes());
        bootstrapEnv(data.readableBytes());
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.BR);

            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), 0, true, newPromiseClient());
                    clientHandler.flush(ctxClient());
                }
            });
            awaitServer();
            assertEquals(text, serverOut.toString(CharsetUtil.UTF_8.name()));
        } finally {
            data.release();
        }
    }

    @Test
    public void brotliEncodingSingleMessage() throws Exception {
        final String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final ByteBuf data = Unpooled.copiedBuffer(text.getBytes(CharsetUtil.UTF_8));
        bootstrapEnv(data.readableBytes());
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.BR);

            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), 0, true, newPromiseClient());
                    clientHandler.flush(ctxClient());
                }
            });
            awaitServer();
            assertEquals(text, serverOut.toString(CharsetUtil.UTF_8.name()));
        } finally {
            data.release();
        }
    }

    @Test
    public void zstdEncodingSingleEmptyMessage() throws Exception {
        final String text = "";
        final ByteBuf data = Unpooled.copiedBuffer(text.getBytes());
        bootstrapEnv(data.readableBytes());
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.ZSTD);

            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), 0, true, newPromiseClient());
                    clientHandler.flush(ctxClient());
                }
            });
            awaitServer();
            assertEquals(text, serverOut.toString(CharsetUtil.UTF_8.name()));
        } finally {
            data.release();
        }
    }

    @Test
    public void zstdEncodingSingleMessage() throws Exception {
        final String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final ByteBuf data = Unpooled.copiedBuffer(text.getBytes(CharsetUtil.UTF_8));
        bootstrapEnv(data.readableBytes());
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.ZSTD);

            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), 0, true, newPromiseClient());
                    clientHandler.flush(ctxClient());
                }
            });
            awaitServer();
            assertEquals(text, serverOut.toString(CharsetUtil.UTF_8.name()));
        } finally {
            data.release();
        }
    }

    @Test
    public void snappyEncodingSingleEmptyMessage() throws Exception {
        final String text = "";
        final ByteBuf data = Unpooled.copiedBuffer(text.getBytes(CharsetUtil.US_ASCII));
        bootstrapEnv(data.readableBytes());
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.SNAPPY);

            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), 0, true, newPromiseClient());
                    clientHandler.flush(ctxClient());
                }
            });
            awaitServer();
            assertEquals(text, serverOut.toString(CharsetUtil.UTF_8.name()));
        } finally {
            data.release();
        }
    }

    @Test
    public void snappyEncodingSingleMessage() throws Exception {
        final String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final ByteBuf data = Unpooled.copiedBuffer(text.getBytes(CharsetUtil.US_ASCII));
        bootstrapEnv(data.readableBytes());
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.SNAPPY);

            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), 0, true, newPromiseClient());
                    clientHandler.flush(ctxClient());
                }
            });
            awaitServer();
            assertEquals(text, serverOut.toString(CharsetUtil.UTF_8.name()));
        } finally {
            data.release();
        }
    }

    @Test
    public void deflateEncodingWriteLargeMessage() throws Exception {
        final int BUFFER_SIZE = 1 << 12;
        final byte[] bytes = new byte[BUFFER_SIZE];
        new Random().nextBytes(bytes);
        bootstrapEnv(BUFFER_SIZE);
        final ByteBuf data = Unpooled.wrappedBuffer(bytes);
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.DEFLATE);

            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), 0, true, newPromiseClient());
                    clientHandler.flush(ctxClient());
                }
            });
            awaitServer();
            assertEquals(data.resetReaderIndex().toString(CharsetUtil.UTF_8),
                    serverOut.toString(CharsetUtil.UTF_8.name()));
        } finally {
            data.release();
        }
    }

    private void bootstrapEnv(int serverOutSize) throws Exception {
        final CountDownLatch prefaceWrittenLatch = new CountDownLatch(1);
        serverOut = new ByteArrayOutputStream(serverOutSize);
        serverLatch = new CountDownLatch(1);
        sb = new ServerBootstrap();
        cb = new Bootstrap();

        // Streams are created before the normal flow for this test, so these connection must be initialized up front.
        serverConnection = new DefaultHttp2Connection(true);
        clientConnection = new DefaultHttp2Connection(false);

        serverConnection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void onStreamClosed(Http2Stream stream) {
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

                if (in.getArgument(4)) {
                    serverConnection.stream((Integer) in.getArgument(1)).close();
                }
                return processedBytes;
            }
        }).when(serverListener).onDataRead(any(ChannelHandlerContext.class), anyInt(),
                any(ByteBuf.class), anyInt(), anyBoolean());

        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        sb.group(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()),
                new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()));
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                serverConnectedChannel = ch;
                ChannelPipeline p = ch.pipeline();
                Http2FrameWriter frameWriter = new DefaultHttp2FrameWriter();
                serverConnection.remote().flowController(
                        new DefaultHttp2RemoteFlowController(serverConnection));
                serverConnection.local().flowController(
                        new DefaultHttp2LocalFlowController(serverConnection).frameWriter(frameWriter));
                Http2ConnectionEncoder encoder = new CompressorHttp2ConnectionEncoder(
                        new DefaultHttp2ConnectionEncoder(serverConnection, frameWriter));
                Http2ConnectionDecoder decoder =
                        new DefaultHttp2ConnectionDecoder(serverConnection, encoder, new DefaultHttp2FrameReader());
                Http2ConnectionHandler connectionHandler = new Http2ConnectionHandlerBuilder()
                        .frameListener(new DelegatingDecompressorFrameListener(serverConnection, serverListener, 0))
                        .codec(decoder, encoder).build();
                p.addLast(connectionHandler);
                serverChannelLatch.countDown();
            }
        });

        cb.group(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()));
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                Http2FrameWriter frameWriter = new DefaultHttp2FrameWriter();
                clientConnection.remote().flowController(
                        new DefaultHttp2RemoteFlowController(clientConnection));
                clientConnection.local().flowController(
                        new DefaultHttp2LocalFlowController(clientConnection).frameWriter(frameWriter));
                clientEncoder = new CompressorHttp2ConnectionEncoder(
                        new DefaultHttp2ConnectionEncoder(clientConnection, frameWriter));

                Http2ConnectionDecoder decoder =
                        new DefaultHttp2ConnectionDecoder(clientConnection, clientEncoder,
                                new DefaultHttp2FrameReader());
                clientHandler = new Http2ConnectionHandlerBuilder()
                        .frameListener(new DelegatingDecompressorFrameListener(clientConnection, clientListener, 0))
                        // By default tests don't wait for server to gracefully shutdown streams
                        .gracefulShutdownTimeoutMillis(0)
                        .codec(decoder, clientEncoder).build();
                p.addLast(clientHandler);
                p.addLast(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == Http2ConnectionPrefaceAndSettingsFrameWrittenEvent.INSTANCE) {
                            prefaceWrittenLatch.countDown();
                            ctx.pipeline().remove(this);
                        }
                    }
                });
            }
        });

        serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
        assertTrue(prefaceWrittenLatch.await(5, SECONDS));
        assertTrue(serverChannelLatch.await(5, SECONDS));
    }

    private void awaitServer() throws Exception {
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
