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

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static io.netty.handler.codec.http2.Http2CodecUtil.ignoreSettingsHandler;
import static io.netty.util.CharsetUtil.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyShort;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
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
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.NetUtil;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Testing the {@link DelegatingHttp2HttpConnectionHandler} for {@link FullHttpRequest} objects into HTTP/2 frames
 */
public class DelegatingHttp2HttpConnectionHandlerTest {

    @Mock
    private Http2FrameObserver clientObserver;

    @Mock
    private Http2FrameObserver serverObserver;

    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private Channel clientChannel;
    private CountDownLatch requestLatch;
    private static final int CONNECTION_SETUP_READ_COUNT = 2;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        requestLatch = new CountDownLatch(CONNECTION_SETUP_READ_COUNT + 1);

        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new DelegatingHttp2ConnectionHandler(true, new FrameCountDown()));
                p.addLast(ignoreSettingsHandler());
            }
        });

        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new DelegatingHttp2HttpConnectionHandler(false, clientObserver));
                p.addLast(ignoreSettingsHandler());
            }
        });

        serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();
        int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        clientChannel = ccf.channel();
    }

    @After
    public void teardown() throws Exception {
        serverChannel.close().sync();
        sb.group().shutdownGracefully();
        cb.group().shutdownGracefully();
    }

    @Test
    public void testJustHeadersRequest() throws Exception {
        final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/example");
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(Http2HttpHeaders.Names.STREAM_ID, 5);
        httpHeaders.set(HttpHeaders.Names.HOST, "http://my-user_name@www.example.org:5555/example");
        httpHeaders.set(Http2HttpHeaders.Names.AUTHORITY, "www.example.org:5555");
        httpHeaders.set(Http2HttpHeaders.Names.SCHEME, "http");
        httpHeaders.add("foo", "goo");
        httpHeaders.add("foo", "goo2");
        httpHeaders.add("foo2", "goo2");
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder()
            .method("GET").path("/example").authority("www.example.org:5555").scheme("http")
            .add("foo", "goo").add("foo", "goo2").add("foo2", "goo2").build();
        ChannelPromise writePromise = newPromise();
        ChannelFuture writeFuture = clientChannel.writeAndFlush(request, writePromise);

        writePromise.awaitUninterruptibly(2, SECONDS);
        assertTrue(writePromise.isSuccess());
        writeFuture.awaitUninterruptibly(2, SECONDS);
        assertTrue(writeFuture.isSuccess());
        awaitRequests();
        verify(serverObserver).onHeadersRead(any(ChannelHandlerContext.class), eq(5), eq(http2Headers), eq(0),
            anyShort(), anyBoolean(), eq(0), eq(true));
        verify(serverObserver, never()).onDataRead(any(ChannelHandlerContext.class),
            anyInt(), any(ByteBuf.class), anyInt(), anyBoolean());
    }

    @Test
    public void testRequestWithBody() throws Exception {
        requestLatch = new CountDownLatch(CONNECTION_SETUP_READ_COUNT + 2);
        final String text = "foooooogoooo";
        final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, POST, "/example",
            Unpooled.copiedBuffer(text, UTF_8));
        final HttpHeaders httpHeaders = request.headers();
        httpHeaders.set(HttpHeaders.Names.HOST, "http://your_user-name123@www.example.org:5555/example");
        httpHeaders.add("foo", "goo");
        httpHeaders.add("foo", "goo2");
        httpHeaders.add("foo2", "goo2");
        final Http2Headers http2Headers = new DefaultHttp2Headers.Builder()
            .method("POST").path("/example").authority("www.example.org:5555").scheme("http")
            .add("foo", "goo").add("foo", "goo2").add("foo2", "goo2").build();
        ChannelPromise writePromise = newPromise();
        ChannelFuture writeFuture = clientChannel.writeAndFlush(request, writePromise);

        writePromise.awaitUninterruptibly(2, SECONDS);
        assertTrue(writePromise.isSuccess());
        writeFuture.awaitUninterruptibly(2, SECONDS);
        assertTrue(writeFuture.isSuccess());
        awaitRequests();
        verify(serverObserver).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(http2Headers), eq(0),
            anyShort(), anyBoolean(), eq(0), eq(false));
        verify(serverObserver).onDataRead(any(ChannelHandlerContext.class),
            eq(3), eq(Unpooled.copiedBuffer(text.getBytes())), eq(0), eq(true));
    }

    private void awaitRequests() throws Exception {
        requestLatch.await(2, SECONDS);
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
                               boolean endOfStream)
                throws Http2Exception {
            serverObserver.onDataRead(ctx, streamId, copy(data), padding, endOfStream);
            requestLatch.countDown();
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                  int padding, boolean endStream) throws Http2Exception {
            serverObserver.onHeadersRead(ctx, streamId, headers, padding, endStream);
            requestLatch.countDown();
        }

        @Override
        public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                                  int streamDependency, short weight, boolean exclusive, int padding,
                                  boolean endStream) throws Http2Exception {
            serverObserver.onHeadersRead(ctx, streamId, headers, streamDependency, weight,
                    exclusive, padding, endStream);
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
        public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                Http2Flags flags, ByteBuf payload) {
            serverObserver.onUnknownFrame(ctx, frameType, streamId, flags, payload);
            requestLatch.countDown();
        }

        ByteBuf copy(ByteBuf buffer) {
            return Unpooled.copiedBuffer(buffer);
        }
    }
}
