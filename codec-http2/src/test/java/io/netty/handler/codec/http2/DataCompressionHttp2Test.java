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
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http2.Http2TestUtil.Http2Runnable;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT;
import static io.netty.handler.codec.http2.Http2TestUtil.runInChannel;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
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
    private int maxServerOutBufferSize;
    private int maxAllocation;
    private final AtomicReference<Throwable> serverException = new AtomicReference<Throwable>();

    @BeforeAll
    public static void beforeAllTests() throws Throwable {
        Brotli.ensureAvailability();
    }

    @BeforeEach
    public void setup() throws InterruptedException, Http2Exception {
        maxAllocation = 0;
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
        if (serverOut != null) {
            serverOut.close();
        }
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
        if (sb != null) {
            Future<?> serverGroup = sb.config().group().shutdownGracefully(0, 0, MILLISECONDS);
            Future<?> serverChildGroup = sb.config().childGroup().shutdownGracefully(0, 0, MILLISECONDS);
            serverGroup.sync();
            serverChildGroup.sync();
        }
        if (cb != null) {
            Future<?> clientGroup = cb.config().group().shutdownGracefully(0, 0, MILLISECONDS);
            clientGroup.sync();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 10 })
    public void justHeadersNoData(final int padding) throws Exception {
        bootstrapEnv(0);
        final Http2Headers headers = new DefaultHttp2Headers().method(GET).path(PATH)
                .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);

        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                clientEncoder.writeHeaders(ctxClient(), 3, headers, padding, true, newPromiseClient());
                clientHandler.flush(ctxClient());
            }
        });
        awaitServer();
        verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(headers), eq(0),
                eq(DEFAULT_PRIORITY_WEIGHT), eq(false), eq(padding), eq(true));
    }

    public static List<Arguments> paddingAndCompression() {
        List<Arguments> arguments = new ArrayList<Arguments>();
        for (int padding : new int[]{0, 10}) {
            for (AsciiString compression : new AsciiString[]{
                    HttpHeaderValues.GZIP, HttpHeaderValues.BR, HttpHeaderValues.ZSTD, HttpHeaderValues.SNAPPY}) {
                final Object[] args = {padding, compression};
                arguments.add(new Arguments() {
                    @Override
                    public Object[] get() {
                        return args;
                    }
                });
            }
        }
        return arguments;
    }

    @ParameterizedTest
    @MethodSource("paddingAndCompression")
    public void encodingSingleEmptyMessage(final int padding, AsciiString compressionAlgorithm) throws Exception {
        final String text = "";
        testEncodingMessage(padding, text, compressionAlgorithm);
    }

    @ParameterizedTest
    @MethodSource("paddingAndCompression")
    public void encodingSingleMessage(final int padding, AsciiString compressionAlgorithm) throws Exception {
        final String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        testEncodingMessage(padding, text, compressionAlgorithm);
    }

    @ParameterizedTest
    @MethodSource("paddingAndCompression")
    public void encodingMultipleMessages(final int padding, AsciiString compressionAlgorithm) throws Exception {
        final String text1 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbbbbbbbbbbbbbbccccccccccccccccccccccc";
        final String text2 = "dddddddddddddddddddeeeeeeeeeeeeeeeeeeeffffffffffffffffffff";
        final ByteBuf data1 = Unpooled.copiedBuffer(text1.getBytes());
        final ByteBuf data2 = Unpooled.copiedBuffer(text2.getBytes());
        bootstrapEnv(data1.readableBytes() + data2.readableBytes());
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, compressionAlgorithm);

            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, padding, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data1.retain(), padding, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data2.retain(), padding, true, newPromiseClient());
                    clientHandler.flush(ctxClient());
                }
            });
            awaitServer();
            assertEquals(text1 + text2, serverOut.toString(CharsetUtil.ISO_8859_1.name()));
        } finally {
            data1.release();
            data2.release();
        }
    }

    @ParameterizedTest
    @MethodSource("paddingAndCompression")
    public void encodingTooBigMessage(final int padding, AsciiString compressionAlgorithm) throws Exception {
        // Make the compressed message produce half a megabyte of text, then limit the output buffer size to 64 KiB.
        byte[] text = PlatformDependent.allocateUninitializedArray(524288);
        final int inputLength = text.length;
        maxAllocation = inputLength / 8;

        testEncodingMessage(padding, text, compressionAlgorithm, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                assertTrue(serverLatch.await(5, SECONDS));
                serverOut.flush();
                Throwable cause = serverException.get();
                if (cause == null) {
                    // Compression codec must have mitigations
                    assertThat(maxServerOutBufferSize)
                            .as("check that the original string of size %s, " +
                                    "got compressed and decompressed into max %s sized buffers",
                                    inputLength, maxAllocation)
                            .isLessThanOrEqualTo(maxAllocation);
                } else {
                    // Compression codec must reject
                    assertThat(cause)
                            .isInstanceOf(Http2Exception.StreamException.class)
                            .rootCause()
                            .isInstanceOf(DecompressionException.class)
                            .hasMessageContaining("maximum size");
                }
                return null;
            }
        });
    }

    private void testEncodingMessage(final int padding, final String text, AsciiString compressionAlgorithmName)
            throws Exception {
        testEncodingMessage(padding, text, compressionAlgorithmName, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                awaitServer();
                assertEquals(text, serverOut.toString(CharsetUtil.ISO_8859_1.name()));
                return null;
            }
        });
    }

    private void testEncodingMessage(int padding, String text, AsciiString compressionAlgorithmName,
                                     Callable<Void> assertions) throws Exception {
        testEncodingMessage(padding, text.getBytes(CharsetUtil.ISO_8859_1), compressionAlgorithmName, assertions);
    }

    private void testEncodingMessage(final int padding,
                                     final byte[] text,
                                     final AsciiString compressionAlgorithmName,
                                     final Callable<Void> assertions) throws Exception {
        final ByteBuf data = Unpooled.copiedBuffer(text);
        bootstrapEnv(data.readableBytes());
        try {
            final Http2Headers headers = new DefaultHttp2Headers().method(POST).path(PATH)
                    .set(HttpHeaderNames.CONTENT_ENCODING, compressionAlgorithmName);

            runInChannel(clientChannel, new Http2Runnable() {
                @Override
                public void run() throws Http2Exception {
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, padding, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), padding, true, newPromiseClient());
                    clientHandler.flush(ctxClient());
                }
            });
            assertions.call();
        } finally {
            data.release();
        }
    }

    @ParameterizedTest
    @MethodSource("paddingAndCompression")
    public void deflateEncodingWriteLargeMessage(final int padding) throws Exception {
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
                    clientEncoder.writeHeaders(ctxClient(), 3, headers, padding, false, newPromiseClient());
                    clientEncoder.writeData(ctxClient(), 3, data.retain(), padding, true, newPromiseClient());
                    clientHandler.flush(ctxClient());
                }
            });
            awaitServer();
            assertEquals(data.resetReaderIndex().toString(CharsetUtil.ISO_8859_1),
                    serverOut.toString(CharsetUtil.ISO_8859_1.name()));
        } finally {
            data.release();
        }
    }

    private void bootstrapEnv(int serverOutSize) throws Exception {
        final CountDownLatch prefaceWrittenLatch = new CountDownLatch(1);
        serverOut = new ByteArrayOutputStream(serverOutSize);
        serverLatch = new CountDownLatch(1);
        serverException.set(null);
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

                maxServerOutBufferSize = Math.max(maxServerOutBufferSize, buf.readableBytes());
                buf.readBytes(serverOut, buf.readableBytes());

                if (in.getArgument(4)) {
                    Http2Stream stream = serverConnection.stream((Integer) in.getArgument(1));
                    if (stream != null) {
                        stream.close();
                    }
                }
                return processedBytes;
            }
        }).when(serverListener).onDataRead(any(ChannelHandlerContext.class), anyInt(),
                any(ByteBuf.class), anyInt(), anyBoolean());

        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        sb.group(new NioEventLoopGroup());
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
                        .frameListener(new DelegatingDecompressorFrameListener(serverConnection, serverListener,
                                maxAllocation) {
                            @Override
                            public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                                  int padding, boolean endOfStream) throws Http2Exception {
                                try {
                                    return super.onDataRead(ctx, streamId, data, padding, endOfStream);
                                } catch (Http2Exception e) {
                                    serverException.set(e);
                                    throw e;
                                }
                            }
                        })
                        .codec(decoder, encoder).build();
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
        Throwable cause = serverException.get();
        if (cause != null) {
            throw new AssertionError("Server-side decompression error", cause);
        }
    }

    private ChannelHandlerContext ctxClient() {
        return clientChannel.pipeline().firstContext();
    }

    private ChannelPromise newPromiseClient() {
        return ctxClient().newPromise();
    }
}
