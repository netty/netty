/*
 * Copyright 2026 The Netty Project
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
import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2TestUtil.Http2Runnable;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static io.netty.handler.codec.http2.Http2TestUtil.runInChannel;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test that wires {@link Http2RequestAcceptEncodingFrameListener} and
 * {@link NegotiatingCompressorHttp2ConnectionEncoder} together over a real client-server socket connection. It
 * verifies that a response already encoded by the application is never recompressed, that a plain response is
 * compressed only when the request negotiates a matching encoding, and that content-length is stripped only when
 * compression is actually applied (see #14277):
 * <ul>
 *     <li>a response whose body is already encoded by the application (has a {@code content-encoding} header)
 *     is never compressed again, regardless of the request's {@code accept-encoding};</li>
 *     <li>a plain response body is compressed only when the request negotiates a matching {@code accept-encoding}
 *     value;</li>
 *     <li>a plain response body is left untouched when the request has no {@code accept-encoding}.</li>
 * </ul>
 */
public class Http2RequestResponseCompressionNegotiationTest {

    private static final AsciiString GET = new AsciiString("GET");
    private static final AsciiString PATH = new AsciiString("/some/path");

    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private volatile Channel serverConnectedChannel;
    private Channel clientChannel;
    private Http2Connection clientConnection;
    private Http2ConnectionEncoder clientEncoder;
    private Http2ConnectionHandler clientHandler;

    private final AtomicReference<Http2Headers> clientResponseHeaders = new AtomicReference<Http2Headers>();
    private ByteArrayOutputStream clientOut;
    private CountDownLatch clientLatch;

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

    /**
     * SPEC-a: a response body that is already compressed by the application (marked with a
     * {@code content-encoding} response header) must never be compressed again by
     * {@link NegotiatingCompressorHttp2ConnectionEncoder}, regardless of whether the request carries an
     * {@code accept-encoding} header.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void alreadyEncodedResponseIsNeverRecompressed(boolean clientSendsAcceptEncoding) throws Exception {
        String plainText = "already-compressed-response-body";
        byte[] gzippedOnce = gzip(plainText.getBytes(CharsetUtil.UTF_8));

        bootstrapEnv(gzippedOnce, HttpHeaderValues.GZIP);
        sendRequestAndAwaitResponse(clientSendsAcceptEncoding);

        // The bytes that hit the wire must be exactly the single gzip payload the application produced.
        assertArrayEquals(gzippedOnce, clientOut.toByteArray());
        assertEquals(HttpHeaderValues.GZIP.toString(),
                clientResponseHeaders.get().get(HttpHeaderNames.CONTENT_ENCODING).toString());

        // A single pass of gunzip must yield the original plaintext; if the encoder had compressed it a
        // second time, one gunzip pass would still yield gzip bytes instead of plaintext.
        assertEquals(plainText, gunzip(clientOut.toByteArray()));
    }

    /**
     * SPEC-b: a plain response body must be compressed when the request negotiates {@code accept-encoding: gzip}
     * and the response has no pre-existing {@code content-encoding}.
     */
    @Test
    public void responseIsCompressedWhenAcceptEncodingNegotiatesGzip() throws Exception {
        String plainText = "hello world, please compress me over the wire";
        byte[] plainBytes = plainText.getBytes(CharsetUtil.UTF_8);

        bootstrapEnv(plainBytes, null);
        sendRequestAndAwaitResponse(true);

        assertEquals(HttpHeaderValues.GZIP.toString(),
                clientResponseHeaders.get().get(HttpHeaderNames.CONTENT_ENCODING).toString());
        byte[] wireBytes = clientOut.toByteArray();
        assertFalse(Arrays.equals(plainBytes, wireBytes), "response body must actually be compressed on the wire");
        assertEquals(plainText, gunzip(wireBytes));
    }

    /**
     * SPEC-c: a plain response body must be left untouched when the request carries no {@code accept-encoding}
     * header.
     */
    @Test
    public void responseIsNotCompressedWhenNoAcceptEncoding() throws Exception {
        String plainText = "hello world, do not compress me";
        byte[] plainBytes = plainText.getBytes(CharsetUtil.UTF_8);

        bootstrapEnv(plainBytes, null);
        sendRequestAndAwaitResponse(false);

        assertNull(clientResponseHeaders.get().get(HttpHeaderNames.CONTENT_ENCODING));
        assertArrayEquals(plainBytes, clientOut.toByteArray());
    }

    /**
     * CL-1: when the response is actually negotiated and compressed, the original (uncompressed) {@code
     * content-length} the application set is no longer valid and must be stripped by
     * {@link NegotiatingCompressorHttp2ConnectionEncoder} (see {@code prepareCompressor}, which removes
     * {@code content-length} right after setting the negotiated {@code content-encoding}).
     */
    @Test
    public void contentLengthIsRemovedWhenResponseIsCompressed() throws Exception {
        String plainText = "hello world, please compress me over the wire";
        byte[] plainBytes = plainText.getBytes(CharsetUtil.UTF_8);

        bootstrapEnv(plainBytes, null, true);
        sendRequestAndAwaitResponse(true);

        assertEquals(HttpHeaderValues.GZIP.toString(),
                clientResponseHeaders.get().get(HttpHeaderNames.CONTENT_ENCODING).toString());
        assertNull(clientResponseHeaders.get().get(HttpHeaderNames.CONTENT_LENGTH));
    }

    /**
     * CL-2: when compression is skipped because the response body is already encoded by the application
     * ({@code content-encoding} already present), {@code prepareCompressor} returns before ever touching
     * {@code content-length}, so a {@code content-length} set by the application must be preserved unchanged.
     */
    @Test
    public void contentLengthIsPreservedWhenAlreadyEncodedResponseSkipsCompression() throws Exception {
        String plainText = "already-compressed-response-body";
        byte[] gzippedOnce = gzip(plainText.getBytes(CharsetUtil.UTF_8));

        bootstrapEnv(gzippedOnce, HttpHeaderValues.GZIP, true);
        sendRequestAndAwaitResponse(true);

        assertEquals(HttpHeaderValues.GZIP.toString(),
                clientResponseHeaders.get().get(HttpHeaderNames.CONTENT_ENCODING).toString());
        assertEquals(String.valueOf(gzippedOnce.length),
                clientResponseHeaders.get().get(HttpHeaderNames.CONTENT_LENGTH).toString());
    }

    /**
     * CL-3: when no {@code accept-encoding} was negotiated at all, {@code prepareCompressor} returns before ever
     * touching {@code content-length}, so a {@code content-length} set by the application must be preserved
     * unchanged.
     */
    @Test
    public void contentLengthIsPreservedWhenNoAcceptEncoding() throws Exception {
        String plainText = "hello world, do not compress me";
        byte[] plainBytes = plainText.getBytes(CharsetUtil.UTF_8);

        bootstrapEnv(plainBytes, null, true);
        sendRequestAndAwaitResponse(false);

        assertNull(clientResponseHeaders.get().get(HttpHeaderNames.CONTENT_ENCODING));
        assertEquals(String.valueOf(plainBytes.length),
                clientResponseHeaders.get().get(HttpHeaderNames.CONTENT_LENGTH).toString());
    }

    private void sendRequestAndAwaitResponse(final boolean sendAcceptEncoding) throws Exception {
        runInChannel(clientChannel, new Http2Runnable() {
            @Override
            public void run() throws Http2Exception {
                Http2Headers requestHeaders = new DefaultHttp2Headers().method(GET).path(PATH);
                if (sendAcceptEncoding) {
                    requestHeaders.set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
                }
                clientEncoder.writeHeaders(ctxClient(), 3, requestHeaders, 0, true, newPromiseClient());
                clientHandler.flush(ctxClient());
            }
        });
        assertTrue(clientLatch.await(5, SECONDS));
    }

    private void bootstrapEnv(final byte[] responseBody, final CharSequence responseContentEncoding)
            throws Exception {
        bootstrapEnv(responseBody, responseContentEncoding, false);
    }

    private void bootstrapEnv(final byte[] responseBody, final CharSequence responseContentEncoding,
            final boolean setContentLength) throws Exception {
        final CountDownLatch prefaceWrittenLatch = new CountDownLatch(1);
        clientOut = new ByteArrayOutputStream();
        clientLatch = new CountDownLatch(1);
        clientResponseHeaders.set(null);
        sb = new ServerBootstrap();
        cb = new Bootstrap();

        final Http2Connection serverConnection = new DefaultHttp2Connection(true);
        clientConnection = new DefaultHttp2Connection(false);
        final Http2RequestAcceptEncodingPropertyKey acceptEncodingKey =
                new Http2RequestAcceptEncodingPropertyKey(serverConnection);

        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        sb.group(new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()));
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

                final Http2ConnectionEncoder encoder = new NegotiatingCompressorHttp2ConnectionEncoder(
                        new DefaultHttp2ConnectionEncoder(serverConnection, frameWriter),
                        acceptEncodingKey, StandardCompressionOptions.gzip());
                Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(
                        serverConnection, encoder, new DefaultHttp2FrameReader());

                Http2FrameListener appServerListener = new Http2FrameAdapter() {
                    @Override
                    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                               int padding, boolean endOfStream) {
                        writeResponse(ctx, streamId);
                    }

                    @Override
                    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                               int streamDependency, short weight, boolean exclusive, int padding,
                                               boolean endOfStream) {
                        writeResponse(ctx, streamId);
                    }

                    private void writeResponse(ChannelHandlerContext ctx, int streamId) {
                        Http2Headers responseHeaders =
                                new DefaultHttp2Headers().status(HttpResponseStatus.OK.codeAsText());
                        if (responseContentEncoding != null) {
                            responseHeaders.set(HttpHeaderNames.CONTENT_ENCODING, responseContentEncoding);
                        }
                        if (setContentLength) {
                            responseHeaders.setInt(HttpHeaderNames.CONTENT_LENGTH, responseBody.length);
                        }
                        ChannelPromise headersPromise = ctx.newPromise();
                        encoder.writeHeaders(ctx, streamId, responseHeaders, 0, false, headersPromise);
                        ChannelPromise dataPromise = ctx.newPromise();
                        encoder.writeData(ctx, streamId, Unpooled.copiedBuffer(responseBody), 0, true, dataPromise);
                        ctx.flush();
                    }
                };

                Http2FrameListener listener = new Http2RequestAcceptEncodingFrameListener(
                        serverConnection, appServerListener, acceptEncodingKey);

                Http2ConnectionHandler connectionHandler = new Http2ConnectionHandlerBuilder()
                        .frameListener(listener)
                        .codec(decoder, encoder)
                        .build();
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
                clientEncoder = new DefaultHttp2ConnectionEncoder(clientConnection, frameWriter);

                Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(
                        clientConnection, clientEncoder, new DefaultHttp2FrameReader());

                Http2FrameListener clientFrameListener = new Http2FrameAdapter() {
                    @Override
                    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                                           boolean endOfStream) throws Http2Exception {
                        int processedBytes = data.readableBytes() + padding;
                        try {
                            data.readBytes(clientOut, data.readableBytes());
                        } catch (IOException e) {
                            throw new Http2Exception(Http2Error.INTERNAL_ERROR, "failed to capture wire bytes", e);
                        }
                        if (endOfStream) {
                            clientLatch.countDown();
                        }
                        return processedBytes;
                    }

                    @Override
                    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                               int padding, boolean endOfStream) {
                        clientResponseHeaders.set(headers);
                        if (endOfStream) {
                            clientLatch.countDown();
                        }
                    }

                    @Override
                    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                               int streamDependency, short weight, boolean exclusive, int padding,
                                               boolean endOfStream) {
                        onHeadersRead(ctx, streamId, headers, padding, endOfStream);
                    }
                };

                clientHandler = new Http2ConnectionHandlerBuilder()
                        .frameListener(clientFrameListener)
                        // By default tests don't wait for server to gracefully shutdown streams
                        .gracefulShutdownTimeoutMillis(0)
                        .codec(decoder, clientEncoder)
                        .build();
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

    private ChannelHandlerContext ctxClient() {
        return clientChannel.pipeline().firstContext();
    }

    private ChannelPromise newPromiseClient() {
        return ctxClient().newPromise();
    }

    private static byte[] gzip(byte[] plain) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(out);
        try {
            gzipOut.write(plain);
        } finally {
            gzipOut.close();
        }
        return out.toByteArray();
    }

    private static String gunzip(byte[] compressed) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(compressed));
        try {
            byte[] buffer = new byte[256];
            int read;
            while ((read = gzipIn.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            gzipIn.close();
        }
        return out.toString(CharsetUtil.UTF_8.name());
    }
}
