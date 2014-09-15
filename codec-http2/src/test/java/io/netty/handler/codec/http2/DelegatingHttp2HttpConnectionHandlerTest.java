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
import static org.junit.Assert.assertEquals;
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
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Testing the {@link DelegatingHttp2HttpConnectionHandler} for {@link FullHttpRequest} objects into HTTP/2 frames
 */
public class DelegatingHttp2HttpConnectionHandlerTest {
    private static final int CONNECTION_SETUP_READ_COUNT = 2;

    private List<ByteBuf> capturedData;

    @Mock
    private Http2FrameListener clientListener;

    @Mock
    private Http2FrameListener serverListener;

    private ServerBootstrap sb;
    private Bootstrap cb;
    private Channel serverChannel;
    private Channel clientChannel;
    private CountDownLatch requestLatch;
    private Http2TestUtil.FrameCountDown serverFrameCountDown;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        requestLatch(new CountDownLatch(CONNECTION_SETUP_READ_COUNT + 1));

        sb = new ServerBootstrap();
        cb = new Bootstrap();

        sb.group(new NioEventLoopGroup(), new NioEventLoopGroup());
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                serverFrameCountDown = new Http2TestUtil.FrameCountDown(serverListener, requestLatch);
                p.addLast(new DelegatingHttp2ConnectionHandler(true, serverFrameCountDown));
                p.addLast(ignoreSettingsHandler());
            }
        });

        cb.group(new NioEventLoopGroup());
        cb.channel(NioSocketChannel.class);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast(new DelegatingHttp2HttpConnectionHandler(false, clientListener));
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
        if (capturedData != null) {
            for (int i = 0; i < capturedData.size(); ++i) {
                capturedData.get(i).release();
            }
            capturedData = null;
        }
        serverChannel.close().sync();
        Future<?> serverGroup = sb.group().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        Future<?> serverChildGroup = sb.childGroup().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        Future<?> clientGroup = cb.group().shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        serverGroup.sync();
        serverChildGroup.sync();
        clientGroup.sync();
    }

    @Test
    public void testJustHeadersRequest() throws Exception {
        final FullHttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, GET, "/example");
        try {
            final HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpUtil.ExtensionHeaders.Names.STREAM_ID, 5);
            httpHeaders.set(HttpHeaders.Names.HOST, "http://my-user_name@www.example.org:5555/example");
            httpHeaders.set(HttpUtil.ExtensionHeaders.Names.AUTHORITY, "www.example.org:5555");
            httpHeaders.set(HttpUtil.ExtensionHeaders.Names.SCHEME, "http");
            httpHeaders.add("foo", "goo");
            httpHeaders.add("foo", "goo2");
            httpHeaders.add("foo2", "goo2");
            final Http2Headers http2Headers = new DefaultHttp2Headers.Builder().method("GET").path("/example")
                    .authority("www.example.org:5555").scheme("http").add("foo", "goo").add("foo", "goo2")
                    .add("foo2", "goo2").build();
            ChannelPromise writePromise = newPromise();
            ChannelFuture writeFuture = clientChannel.writeAndFlush(request, writePromise);

            writePromise.awaitUninterruptibly(2, SECONDS);
            assertTrue(writePromise.isSuccess());
            writeFuture.awaitUninterruptibly(2, SECONDS);
            assertTrue(writeFuture.isSuccess());
            awaitRequests();
            final ArgumentCaptor<ByteBuf> dataCaptor = ArgumentCaptor.forClass(ByteBuf.class);
            verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(5), eq(http2Headers), eq(0),
                    anyShort(), anyBoolean(), eq(0), eq(true));
            verify(serverListener, never()).onDataRead(any(ChannelHandlerContext.class), anyInt(),
                    dataCaptor.capture(), anyInt(), anyBoolean());
        } finally {
            request.release();
        }
    }

    @Test
    public void testRequestWithBody() throws Exception {
        requestLatch(new CountDownLatch(CONNECTION_SETUP_READ_COUNT + 2));
        final String text = "foooooogoooo";
        final ByteBuf data = Unpooled.copiedBuffer(text, UTF_8);
        try {
            final HttpRequest request = new DefaultFullHttpRequest(HTTP_1_1, POST, "/example", data.retain());
            final HttpHeaders httpHeaders = request.headers();
            httpHeaders.set(HttpHeaders.Names.HOST, "http://your_user-name123@www.example.org:5555/example");
            httpHeaders.add("foo", "goo");
            httpHeaders.add("foo", "goo2");
            httpHeaders.add("foo2", "goo2");
            final Http2Headers http2Headers = new DefaultHttp2Headers.Builder().method("POST").path("/example")
                    .authority("www.example.org:5555").scheme("http").add("foo", "goo").add("foo", "goo2")
                    .add("foo2", "goo2").build();
            ChannelPromise writePromise = newPromise();
            ChannelFuture writeFuture = clientChannel.writeAndFlush(request, writePromise);

            writePromise.awaitUninterruptibly(2, SECONDS);
            assertTrue(writePromise.isSuccess());
            writeFuture.awaitUninterruptibly(2, SECONDS);
            assertTrue(writeFuture.isSuccess());
            awaitRequests();
            final ArgumentCaptor<ByteBuf> dataCaptor = ArgumentCaptor.forClass(ByteBuf.class);
            verify(serverListener).onHeadersRead(any(ChannelHandlerContext.class), eq(3), eq(http2Headers), eq(0),
                    anyShort(), anyBoolean(), eq(0), eq(false));
            verify(serverListener).onDataRead(any(ChannelHandlerContext.class), eq(3), dataCaptor.capture(), eq(0),
                    eq(true));
            capturedData = dataCaptor.getAllValues();
            assertEquals(data, capturedData.get(0));
        } finally {
            data.release();
        }
    }

    private void requestLatch(CountDownLatch latch) {
        requestLatch = latch;
        if (serverFrameCountDown != null) {
            serverFrameCountDown.messageLatch(latch);
        }
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
}
