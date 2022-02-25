/*
 * Copyright 2012 The Netty Project
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
package io.netty5.handler.codec.http;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelOption;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.handler.codec.ByteBufToBufferHandler;
import io.netty5.handler.codec.CodecException;
import io.netty5.handler.codec.PrematureChannelClosureException;
import io.netty5.util.CharsetUtil;
import io.netty5.util.NetUtil;
import io.netty5.util.concurrent.Future;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

import static io.netty5.util.ReferenceCountUtil.release;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class HttpClientCodecTest {

    private static final String EMPTY_RESPONSE = "HTTP/1.0 200 OK\r\nContent-Length: 0\r\n\r\n";
    private static final String RESPONSE = "HTTP/1.0 200 OK\r\n" + "Date: Fri, 31 Dec 1999 23:59:59 GMT\r\n" +
            "Content-Type: text/html\r\n" + "Content-Length: 28\r\n" + "\r\n"
            + "<html><body></body></html>\r\n";
    private static final String INCOMPLETE_CHUNKED_RESPONSE = "HTTP/1.1 200 OK\r\n" + "Content-Type: text/plain\r\n" +
            "Transfer-Encoding: chunked\r\n" + "\r\n" +
            "5\r\n" + "first\r\n" + "6\r\n" + "second\r\n" + "0\r\n";
    private static final String CHUNKED_RESPONSE = INCOMPLETE_CHUNKED_RESPONSE + "\r\n";

    @Test
    public void testConnectWithResponseContent() {
        HttpClientCodec codec = new HttpClientCodec(4096, 8192, true);
        EmbeddedChannel ch = new EmbeddedChannel(codec);

        sendRequestAndReadResponse(ch, HttpMethod.CONNECT, RESPONSE);
        ch.finish();
    }

    @Test
    public void testFailsNotOnRequestResponseChunked() {
        HttpClientCodec codec = new HttpClientCodec(4096, 8192, true);
        EmbeddedChannel ch = new EmbeddedChannel(codec);

        sendRequestAndReadResponse(ch, HttpMethod.GET, CHUNKED_RESPONSE);
        ch.finish();
    }

    @Test
    public void testFailsOnMissingResponse() {
        HttpClientCodec codec = new HttpClientCodec(4096, 8192, true);
        EmbeddedChannel ch = new EmbeddedChannel(codec);

        assertTrue(ch.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                "http://localhost/", ch.bufferAllocator().allocate(0))));
        try (Buffer buffer = ch.readOutbound()) {
            assertNotNull(buffer);
        }
        try {
            ch.finish();
            fail();
        } catch (CodecException e) {
            assertTrue(e instanceof PrematureChannelClosureException);
        }
    }

    @Test
    public void testFailsOnIncompleteChunkedResponse() {
        HttpClientCodec codec = new HttpClientCodec(4096, 8192, true);
        EmbeddedChannel ch = new EmbeddedChannel(codec);

        ch.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost/",
                ch.bufferAllocator().allocate(0)));
        try (Buffer buffer = ch.readOutbound()) {
            assertNotNull(buffer);
        }

        assertNull(ch.readInbound());
        final byte[] responseBytes = INCOMPLETE_CHUNKED_RESPONSE.getBytes(ISO_8859_1);
        ch.writeInbound(ch.bufferAllocator().copyOf(responseBytes));
        assertThat(ch.readInbound(), instanceOf(HttpResponse.class));
        ((HttpContent<?>) ch.readInbound()).close(); // Chunk 'first'
        ((HttpContent<?>) ch.readInbound()).close(); // Chunk 'second'
        assertNull(ch.readInbound());

        assertThrows(PrematureChannelClosureException.class, ch::finish);
    }

    @Test
    public void testServerCloseSocketInputProvidesData() throws Exception {
        ServerBootstrap sb = new ServerBootstrap();
        Bootstrap cb = new Bootstrap();
        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        final CountDownLatch responseReceivedLatch = new CountDownLatch(1);
        try {
            sb.group(new MultithreadEventLoopGroup(2, NioHandler.newFactory()));
            sb.channel(NioServerSocketChannel.class);
            sb.childHandler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(ByteBufToBufferHandler.BYTEBUF_TO_BUFFER_HANDLER);
                    // Don't use the HttpServerCodec, because we don't want to have content-length or anything added.
                    ch.pipeline().addLast(new HttpRequestDecoder(4096, 8192, true));
                    ch.pipeline().addLast(new HttpObjectAggregator<DefaultHttpContent>(4096));
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                        @Override
                        protected void messageReceived(ChannelHandlerContext ctx, FullHttpRequest msg) {
                            // This is just a simple demo...don't block in IO
                            assertTrue(ctx.channel() instanceof SocketChannel);
                            final SocketChannel sChannel = (SocketChannel) ctx.channel();
                            /*
                              The point of this test is to not add any content-length or content-encoding headers
                              and the client should still handle this.
                              See RFC 7230, 3.3.3: https://tools.ietf.org/html/rfc7230#section-3.3.3.
                             */
                            final byte[] bytes = ("HTTP/1.0 200 OK\r\n" +
                                    "Date: Fri, 31 Dec 1999 23:59:59 GMT\r\n" +
                                    "Content-Type: text/html\r\n\r\n").getBytes(CharsetUtil.ISO_8859_1);
                            sChannel.writeAndFlush(ch.bufferAllocator().copyOf(bytes))
                                    .addListener(future -> {
                                        assertTrue(future.isSuccess());
                                        final byte[] bytes1 = "<html><body>hello half closed!</body></html>\r\n"
                                                .getBytes(CharsetUtil.ISO_8859_1);
                                        sChannel.writeAndFlush(ch.bufferAllocator().copyOf(bytes1))
                                                .addListener(future1 -> {
                                                    assertTrue(future1.isSuccess());
                                                    sChannel.shutdownOutput();
                                                });
                                    });
                        }
                    });
                    serverChannelLatch.countDown();
                }
            });

            cb.group(new MultithreadEventLoopGroup(1, NioHandler.newFactory()));
            cb.channel(NioSocketChannel.class);
            cb.option(ChannelOption.ALLOW_HALF_CLOSURE, true);
            cb.handler(new ChannelInitializer<>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(ByteBufToBufferHandler.BYTEBUF_TO_BUFFER_HANDLER);
                    ch.pipeline().addLast(new HttpClientCodec(4096, 8192, true, true));
                    ch.pipeline().addLast(new HttpObjectAggregator<DefaultHttpContent>(4096));
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                        @Override
                        protected void messageReceived(ChannelHandlerContext ctx, FullHttpResponse msg) {
                            responseReceivedLatch.countDown();
                        }
                    });
                }
            });

            Channel serverChannel = sb.bind(new InetSocketAddress(0)).get();
            int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

            Future<Channel> ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
            assertTrue(ccf.awaitUninterruptibly().isSuccess());
            Channel clientChannel = ccf.get();
            assertTrue(serverChannelLatch.await(5, SECONDS));
            clientChannel.writeAndFlush(new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"));
            assertTrue(responseReceivedLatch.await(5, SECONDS));
        } finally {
            sb.config().group().shutdownGracefully();
            sb.config().childGroup().shutdownGracefully();
            cb.config().group().shutdownGracefully();
        }
    }

    @Test
    public void testContinueParsingAfterConnect() {
        testAfterConnect(true);
    }

    @Test
    public void testPassThroughAfterConnect() {
        testAfterConnect(false);
    }

    private static void testAfterConnect(final boolean parseAfterConnect) {
        EmbeddedChannel ch = new EmbeddedChannel(new HttpClientCodec(4096, 8192, true, true, parseAfterConnect));

        Consumer connectResponseConsumer = new Consumer();
        sendRequestAndReadResponse(ch, HttpMethod.CONNECT, EMPTY_RESPONSE, connectResponseConsumer);
        assertTrue(connectResponseConsumer.getReceivedCount() > 0, "No connect response messages received.");
        Consumer responseConsumer = new Consumer() {
            @Override
            void accept(Object object) {
                if (parseAfterConnect) {
                    assertThat("Unexpected response message type.", object, instanceOf(HttpObject.class));
                } else {
                    assertThat("Unexpected response message type.", object, not(instanceOf(HttpObject.class)));
                }
            }
        };
        sendRequestAndReadResponse(ch, HttpMethod.GET, RESPONSE, responseConsumer);
        assertTrue(responseConsumer.getReceivedCount() > 0, "No response messages received.");
        assertFalse(ch.finish(), "Channel finish failed.");
    }

    private static void sendRequestAndReadResponse(EmbeddedChannel ch, HttpMethod httpMethod, String response) {
        sendRequestAndReadResponse(ch, httpMethod, response, new Consumer());
    }

    private static void sendRequestAndReadResponse(EmbeddedChannel ch, HttpMethod httpMethod, String response,
                                                   Consumer responseConsumer) {
        assertTrue(ch.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, httpMethod, "http://localhost/",
                        ch.bufferAllocator().allocate(0))),
                "Channel outbound write failed.");
        final byte[] responseBytes = response.getBytes(ISO_8859_1);
        assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf(responseBytes)),
                "Channel inbound write failed.");

        for (;;) {
            Object msg = ch.readOutbound();
            if (msg == null) {
                break;
            }
            release(msg);
        }
        for (;;) {
            Object msg = ch.readInbound();
            if (msg == null) {
                break;
            }
            responseConsumer.onResponse(msg);
            release(msg);
        }
    }

    private static class Consumer {

        private int receivedCount;

        final void onResponse(Object object) {
            receivedCount++;
            accept(object);
        }

        void accept(Object object) {
            // Default noop.
        }

        int getReceivedCount() {
            return receivedCount;
        }
    }

    @Test
    public void testDecodesFinalResponseAfterSwitchingProtocols() {
        String SWITCHING_PROTOCOLS_RESPONSE = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Connection: Upgrade\r\n" +
                "Upgrade: TLS/1.2, HTTP/1.1\r\n\r\n";

        HttpClientCodec codec = new HttpClientCodec(4096, 8192, true);
        EmbeddedChannel ch = new EmbeddedChannel(codec, new HttpObjectAggregator<DefaultHttpContent>(1024));

        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost/",
                ch.bufferAllocator().allocate(0));
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE);
        request.headers().set(HttpHeaderNames.UPGRADE, "TLS/1.2");
        assertTrue(ch.writeOutbound(request), "Channel outbound write failed.");

        final byte[] bytes = SWITCHING_PROTOCOLS_RESPONSE.getBytes(ISO_8859_1);
        assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf(bytes)),
                "Channel inbound write failed.");
        Object switchingProtocolsResponse = ch.readInbound();
        assertNotNull(switchingProtocolsResponse, "No response received");
        assertThat("Response was not decoded", switchingProtocolsResponse, instanceOf(FullHttpResponse.class));
        ((FullHttpResponse) switchingProtocolsResponse).close();

        final byte[] bytes2 = SWITCHING_PROTOCOLS_RESPONSE.getBytes(ISO_8859_1);
        assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf(bytes2)),
                "Channel inbound write failed");
        Object finalResponse = ch.readInbound();
        assertNotNull(finalResponse, "No response received");
        assertThat("Response was not decoded", finalResponse, instanceOf(FullHttpResponse.class));
        ((FullHttpResponse) finalResponse).close();
        assertTrue(ch.finishAndReleaseAll(), "Channel finish failed");
    }

    @Test
    public void testWebSocket00Response() {
        byte[] data = ("HTTP/1.1 101 WebSocket Protocol Handshake\r\n" +
                "Upgrade: WebSocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Origin: http://localhost:8080\r\n" +
                "Sec-WebSocket-Location: ws://localhost/some/path\r\n" +
                "\r\n" +
                "1234567812345678").getBytes();
        EmbeddedChannel ch = new EmbeddedChannel(new HttpClientCodec());
        assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf(data)));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.SWITCHING_PROTOCOLS));
        HttpContent<?> content = ch.readInbound();
        assertThat(content.payload().readableBytes(), is(16));
        content.close();

        assertThat(ch.finish(), is(false));

        assertThat(ch.readInbound(), is(nullValue()));
    }

    @Test
    public void testWebDavResponse() {
        byte[] data = ("HTTP/1.1 102 Processing\r\n" +
                       "Status-URI: Status-URI:http://status.com; 404\r\n" +
                       "\r\n" +
                       "1234567812345678").getBytes();
        EmbeddedChannel ch = new EmbeddedChannel(new HttpClientCodec());
        assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf(data)));

        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.PROCESSING));
        HttpContent<?> content = ch.readInbound();
        // HTTP 102 is not allowed to have content.
        assertThat(content.payload().readableBytes(), is(0));
        content.close();

        assertThat(ch.finish(), is(false));
    }

    @Test
    public void testInformationalResponseKeepsPairsInSync() {
        byte[] data = ("HTTP/1.1 102 Processing\r\n" +
                "Status-URI: Status-URI:http://status.com; 404\r\n" +
                "\r\n").getBytes();
        byte[] data2 = ("HTTP/1.1 200 OK\r\n" +
                "Content-Length: 8\r\n" +
                "\r\n" +
                "12345678").getBytes();
        EmbeddedChannel ch = new EmbeddedChannel(new HttpClientCodec());
        assertTrue(ch.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, "/",
                ch.bufferAllocator().allocate(0))));
        ((Buffer) ch.readOutbound()).close();
        assertNull(ch.readOutbound());
        assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf(data)));
        HttpResponse res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.PROCESSING));
        HttpContent<?> content = ch.readInbound();
        // HTTP 102 is not allowed to have content.
        assertThat(content.payload().readableBytes(), is(0));
        assertThat(content, CoreMatchers.<HttpContent<?>>instanceOf(LastHttpContent.class));
        content.close();

        assertTrue(ch.writeOutbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/",
                ch.bufferAllocator().allocate(0))));
        ((Buffer) ch.readOutbound()).close();
        assertNull(ch.readOutbound());
        assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf(data2)));

        res = ch.readInbound();
        assertThat(res.protocolVersion(), sameInstance(HttpVersion.HTTP_1_1));
        assertThat(res.status(), is(HttpResponseStatus.OK));
        content = ch.readInbound();
        // HTTP 200 has content.
        assertThat(content.payload().readableBytes(), is(8));
        assertThat(content, CoreMatchers.<HttpContent<?>>instanceOf(LastHttpContent.class));
        content.close();

        assertThat(ch.finish(), is(false));
    }

    @Test
    public void testMultipleResponses() {
        String response = "HTTP/1.1 200 OK\r\n" +
                "Content-Length: 0\r\n\r\n";

        HttpClientCodec codec = new HttpClientCodec(4096, 8192, true);
        EmbeddedChannel ch = new EmbeddedChannel(codec, new HttpObjectAggregator<DefaultHttpContent>(1024));

        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "http://localhost/",
                ch.bufferAllocator().allocate(0));
        assertTrue(ch.writeOutbound(request));

        final byte[] responseBytes = response.getBytes(UTF_8);
        assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf(responseBytes)));
        assertTrue(ch.writeInbound(ch.bufferAllocator().copyOf(responseBytes)));
        FullHttpResponse resp = ch.readInbound();
        assertTrue(resp.decoderResult().isSuccess());
        resp.close();

        resp = ch.readInbound();
        assertTrue(resp.decoderResult().isSuccess());
        resp.close();
        assertTrue(ch.finishAndReleaseAll());
    }

}
