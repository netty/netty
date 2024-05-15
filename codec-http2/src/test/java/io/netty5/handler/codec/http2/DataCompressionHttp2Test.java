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
package io.netty5.handler.codec.http2;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioIoHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.codec.http.HttpHeaderNames;
import io.netty5.handler.codec.http.HttpHeaderValues;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import io.netty5.util.AsciiString;
import io.netty5.util.NetUtil;
import io.netty5.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import static io.netty5.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty5.handler.codec.http2.Http2TestUtil.bb;
import static io.netty5.handler.codec.http2.Http2TestUtil.runInChannel;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyShort;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
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

    @BeforeEach
    public void setup() throws InterruptedException, Http2Exception {
        MockitoAnnotations.initMocks(this);
        doAnswer(invocation -> {
            if (invocation.getArgument(4)) {
                serverConnection.stream((Integer) invocation.getArgument(1)).close();
            }
            return null;
        }).when(serverListener).onHeadersRead(any(ChannelHandlerContext.class), anyInt(), any(Http2Headers.class),
                anyInt(), anyBoolean());
        doAnswer(invocation -> {
            if (invocation.getArgument(7)) {
                serverConnection.stream((Integer) invocation.getArgument(1)).close();
            }
            return null;
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
            clientChannel.close().asStage().sync();
            clientChannel = null;
        }
        if (serverChannel != null) {
            serverChannel.close().asStage().sync();
            serverChannel = null;
        }
        final Channel serverConnectedChannel = this.serverConnectedChannel;
        if (serverConnectedChannel != null) {
            serverConnectedChannel.close().asStage().sync();
            this.serverConnectedChannel = null;
        }
        Future<?> serverGroup = sb.config().group().shutdownGracefully(0, 0, MILLISECONDS);
        Future<?> serverChildGroup = sb.config().childGroup().shutdownGracefully(0, 0, MILLISECONDS);
        Future<?> clientGroup = cb.config().group().shutdownGracefully(0, 0, MILLISECONDS);
        serverGroup.asStage().sync();
        serverChildGroup.asStage().sync();
        clientGroup.asStage().sync();
    }

    @Test
    public void justHeadersNoData() throws Exception {
        bootstrapEnv(0);
        final Http2Headers headers = Http2Headers.newHeaders().method(GET).path(PATH)
                .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);

        runInChannel(clientChannel, () -> {
            clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, true);
            clientHandler.flush(ctxClient());
        });
        awaitServer();
        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(headers), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(0), eq(true));
    }

    @Test
    public void gzipEncodingSingleEmptyMessage() throws Exception {
        final String text = "";
        final Buffer data = bb(text);
        bootstrapEnv(data.readableBytes());
        final Http2Headers headers = Http2Headers.newHeaders()
                .method(POST).path(PATH).set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);

        runInChannel(clientChannel, () -> {
            clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false);
            clientEncoder.writeData(ctxClient(), 3, data, 0, true);
            clientHandler.flush(ctxClient());
        });
        awaitServer();
        assertEquals(text, serverOut.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void gzipEncodingSingleMessage() throws Exception {
        final String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final Buffer data = bb(text);
        bootstrapEnv(data.readableBytes());
        final Http2Headers headers = Http2Headers.newHeaders()
                .method(POST).path(PATH).set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);

        runInChannel(clientChannel, () -> {
            clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false);
            clientEncoder.writeData(ctxClient(), 3, data, 0, true);
            clientHandler.flush(ctxClient());
        });
        awaitServer();
        assertEquals(text, serverOut.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void gzipEncodingMultipleMessages() throws Exception {
        final String text1 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final String text2 = "dddddddddddddddddddeeeeeeeeeeeeeeeeeeeffffffffffffffffffff";
        final Buffer data1 = bb(text1);
        final Buffer data2 = bb(text2);
        bootstrapEnv(data1.readableBytes() + data2.readableBytes());
        final Http2Headers headers = Http2Headers.newHeaders()
                .method(POST).path(PATH).set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);

        runInChannel(clientChannel, () -> {
            clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false);
            clientEncoder.writeData(ctxClient(), 3, data1, 0, false);
            clientEncoder.writeData(ctxClient(), 3, data2, 0, true);
            clientHandler.flush(ctxClient());
        });
        awaitServer();
        assertEquals(text1 + text2, serverOut.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void brotliEncodingSingleEmptyMessage() throws Exception {
        final String text = "";
        final Buffer data = bb(text);
        bootstrapEnv(data.readableBytes());
        final Http2Headers headers = Http2Headers.newHeaders()
                .method(POST).path(PATH).set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.BR);

        runInChannel(clientChannel, () -> {
            clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false);
            clientEncoder.writeData(ctxClient(), 3, data, 0, true);
            clientHandler.flush(ctxClient());
        });
        awaitServer();
        assertEquals(text, serverOut.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void brotliEncodingSingleMessage() throws Exception {
        final String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final Buffer data = bb(text);
        bootstrapEnv(data.readableBytes());
        final Http2Headers headers = Http2Headers.newHeaders()
                .method(POST).path(PATH).set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.BR);

        runInChannel(clientChannel, () -> {
            clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false);
            clientEncoder.writeData(ctxClient(), 3, data, 0, true);
            clientHandler.flush(ctxClient());
        });
        awaitServer();
        assertEquals(text, serverOut.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void zstdEncodingSingleEmptyMessage() throws Exception {
        final String text = "";
        final Buffer data = bb(text);
        bootstrapEnv(data.readableBytes());
        final Http2Headers headers = Http2Headers.newHeaders()
                .method(POST).path(PATH).set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.ZSTD);

        runInChannel(clientChannel, () -> {
            clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false);
            clientEncoder.writeData(ctxClient(), 3, data, 0, true);
            clientHandler.flush(ctxClient());
        });
        awaitServer();
        assertEquals(text, serverOut.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void zstdEncodingSingleMessage() throws Exception {
        final String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final Buffer data = bb(text);
        bootstrapEnv(data.readableBytes());
        final Http2Headers headers = Http2Headers.newHeaders()
                .method(POST).path(PATH).set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.ZSTD);

        runInChannel(clientChannel, () -> {
            clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false);
            clientEncoder.writeData(ctxClient(), 3, data, 0, true);
            clientHandler.flush(ctxClient());
        });
        awaitServer();
        assertEquals(text, serverOut.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void snappyEncodingSingleEmptyMessage() throws Exception {
        final String text = "";
        final Buffer data = bb(text);
        bootstrapEnv(data.readableBytes());
        final Http2Headers headers = Http2Headers.newHeaders().method(POST).path(PATH)
                .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.SNAPPY);

        runInChannel(clientChannel, () -> {
            clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false);
            clientEncoder.writeData(ctxClient(), 3, data, 0, true);
            clientHandler.flush(ctxClient());
        });
        awaitServer();
        assertEquals(text, serverOut.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void snappyEncodingSingleMessage() throws Exception {
        final String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final Buffer data = bb(text);
        bootstrapEnv(data.readableBytes());
        final Http2Headers headers = Http2Headers.newHeaders().method(POST).path(PATH)
                .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.SNAPPY);

        runInChannel(clientChannel, () -> {
            clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false);
            clientEncoder.writeData(ctxClient(), 3, data, 0, true);
            clientHandler.flush(ctxClient());
        });
        awaitServer();
        assertEquals(text, serverOut.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void deflateEncodingWriteLargeMessage() throws Exception {
        final int BUFFER_SIZE = 1 << 12;
        final byte[] bytes = new byte[BUFFER_SIZE];
        new Random().nextBytes(bytes);
        bootstrapEnv(BUFFER_SIZE);
        final Buffer data = bb(bytes);
        final Http2Headers headers = Http2Headers.newHeaders()
                .method(POST).path(PATH).set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.DEFLATE);

        runInChannel(clientChannel, () -> {
            clientEncoder.writeHeaders(ctxClient(), 3, headers, 0, false);
            clientEncoder.writeData(ctxClient(), 3, data, 0, true);
            clientHandler.flush(ctxClient());
        });
        awaitServer();
        assertEquals(new String(bytes, StandardCharsets.UTF_8),
                     serverOut.toString(StandardCharsets.UTF_8));
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

        doAnswer(in -> {
            Buffer buf = (Buffer) in.getArguments()[2];
            int padding = (Integer) in.getArguments()[3];
            int processedBytes = buf.readableBytes() + padding;

            try (var iterator = buf.forEachComponent()) {
                for (var c = iterator.firstReadable(); c != null; c = c.nextReadable()) {
                    byte[] bytes = new byte[c.readableBytes()];
                    c.readableBuffer().get(bytes);
                    serverOut.write(bytes);
                }
            }

            if (in.getArgument(4)) {
                serverConnection.stream(in.getArgument(1)).close();
            }
            return processedBytes;
        }).when(serverListener).onDataRead(any(ChannelHandlerContext.class), anyInt(),
                any(Buffer.class), anyInt(), anyBoolean());

        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        sb.group(new MultithreadEventLoopGroup(NioIoHandler.newFactory()),
                new MultithreadEventLoopGroup(NioIoHandler.newFactory()));
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
                        .frameListener(new DelegatingDecompressorFrameListener(serverConnection, serverListener))
                        .codec(decoder, encoder).build();
                p.addLast(connectionHandler);
                serverChannelLatch.countDown();
            }
        });

        cb.group(new MultithreadEventLoopGroup(NioIoHandler.newFactory()));
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
                        .frameListener(new DelegatingDecompressorFrameListener(clientConnection, clientListener))
                        // By default tests don't wait for server to gracefully shutdown streams
                        .gracefulShutdownTimeoutMillis(0)
                        .codec(decoder, clientEncoder).build();
                p.addLast(clientHandler);
                p.addLast(new ChannelHandler() {
                    @Override
                    public void channelInboundEvent(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt == Http2ConnectionPrefaceAndSettingsFrameWrittenEvent.INSTANCE) {
                            prefaceWrittenLatch.countDown();
                            ctx.pipeline().remove(this);
                        }
                    }
                });
            }
        });

        serverChannel = sb.bind(new InetSocketAddress(0)).asStage().get();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        clientChannel = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port)).asStage().get();
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
}
