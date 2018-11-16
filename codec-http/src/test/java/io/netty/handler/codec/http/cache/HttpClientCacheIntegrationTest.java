/*
 * Copyright 2018 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.handler.codec.http.cache;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BinaryOperator;

import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static java.util.concurrent.TimeUnit.*;
import static org.junit.Assert.*;

public class HttpClientCacheIntegrationTest {

    private CountDownLatch serverChannelLatch;
    private AtomicReference<CountDownLatch> responseReceivedLatch;
    private AtomicReference<String> responseContent;
    //    private AtomicInteger serverRequestCount;
    private ConcurrentHashMap<HttpResponseStatus, Integer> serverResponseCountPerStatus;
    private int port;

    private ServerBootstrap serverBootstrap;
    private Bootstrap clientBootstrap;

    private static ServerBootstrap getLocalServerBootstrap(
            final ConcurrentHashMap<HttpResponseStatus, Integer> countPerStatus,
            final CountDownLatch serverChannelLatch) {
        ServerBootstrap sb = new ServerBootstrap();
        sb.group(new NioEventLoopGroup(2));
        sb.channel(NioServerSocketChannel.class);
        sb.childHandler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new HttpServerCodec(4096, 8192, 8192, true));
                ch.pipeline().addLast(new HttpObjectAggregator(4096));
                ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                    private void incrementRequestCountForStatus(HttpResponseStatus status) {
                        countPerStatus.putIfAbsent(status, 0);
                        Integer count = countPerStatus.get(status);
                        while (!countPerStatus.replace(status, count != null? count + 1 : 0).equals(count)) {
                            count = countPerStatus.get(status);
                        }
                    }

                    @Override
                    protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request) {
                        assertTrue(ctx.channel() instanceof SocketChannel);
                        final SocketChannel sChannel = (SocketChannel) ctx.channel();

                        if (request.headers().contains(IF_NONE_MATCH)) {
                            final ReadOnlyHttpHeaders headers = new ReadOnlyHttpHeaders(false,
                                                                                        DATE,
                                                                                        DateFormatter
                                                                                                .format(new Date()));
                            ctx.writeAndFlush(
                                    new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_MODIFIED, EMPTY_BUFFER,
                                                                headers, new ReadOnlyHttpHeaders(false)))
                               .addListener(ChannelFutureListener.CLOSE);

                            incrementRequestCountForStatus(HttpResponseStatus.NOT_MODIFIED);
                            return;
                        }

                        incrementRequestCountForStatus(HttpResponseStatus.OK);
                        final ReadOnlyHttpHeaders headers = new ReadOnlyHttpHeaders(false,
                                                                                    DATE,
                                                                                    DateFormatter.format(new Date()),
                                                                                    CACHE_CONTROL,
                                                                                    "max-age=5");
                        sChannel.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK,
                                                                           copiedBuffer("Hello World",
                                                                                        CharsetUtil.UTF_8), headers,
                                                                           new ReadOnlyHttpHeaders(false)))
                                .addListener(ChannelFutureListener.CLOSE);
                    }
                });
                serverChannelLatch.countDown();
            }
        });

        return sb;
    }

    private static Bootstrap getLocalClientBootstrap(final AtomicReference<CountDownLatch> responseReceivedLatch,
                                                     final AtomicReference<String> responseContent) {
        final Bootstrap cb = new Bootstrap();
        final NioEventLoopGroup clientGroup = new NioEventLoopGroup(1);
        final HttpCacheMemoryStorage cacheStorage = new HttpCacheMemoryStorage();
        cb.group(clientGroup);
        cb.channel(NioSocketChannel.class);
        cb.option(ChannelOption.ALLOW_HALF_CLOSURE, true);
        cb.handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new HttpClientCodec(4096, 8192, 8192, true, true));
                ch.pipeline()
                  .addLast(new HttpClientCacheHandler(cacheStorage, CacheConfig.DEFAULT, clientGroup.next()));
                ch.pipeline().addLast(new SimpleChannelInboundHandler<HttpContent>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, HttpContent msg) throws Exception {
                        responseContent.getAndAccumulate(msg.content().toString(CharsetUtil.UTF_8),
                                                         new BinaryOperator<String>() {
                                                             @Override
                                                             public String apply(String s, String s2) {
                                                                 if (s == null) {
                                                                     return s2;
                                                                 }

                                                                 if (s2 == null) {
                                                                     return s;
                                                                 }

                                                                 return s + s2;
                                                             }
                                                         });

                        if (msg instanceof LastHttpContent) {
                            responseReceivedLatch.get().countDown();
                        }
                    }
                });
            }
        });

        return cb;
    }

    private Channel connect(Bootstrap cb, int port) throws InterruptedException {
        ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
        assertTrue(ccf.awaitUninterruptibly().isSuccess());
        assertTrue(serverChannelLatch.await(5, SECONDS));
        return ccf.channel();
    }

    @Before
    public void setUp() throws Exception {
        serverChannelLatch = new CountDownLatch(1);
        responseReceivedLatch = new AtomicReference<CountDownLatch>(new CountDownLatch(1));
        responseContent = new AtomicReference<String>();
//        serverRequestCount = new AtomicInteger(0);
        serverResponseCountPerStatus = new ConcurrentHashMap<HttpResponseStatus, Integer>();

        serverBootstrap = getLocalServerBootstrap(serverResponseCountPerStatus, serverChannelLatch);

        Channel serverChannel = serverBootstrap.bind(new InetSocketAddress(0)).sync().channel();
        port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

        clientBootstrap = getLocalClientBootstrap(responseReceivedLatch, responseContent);
    }

    @After
    public void tearDown() throws Exception {
        serverBootstrap.config().group().shutdownGracefully();
        serverBootstrap.config().childGroup().shutdownGracefully();
        clientBootstrap.config().group().shutdownGracefully();
    }

    @Test
    public void shouldLoadFromCache() throws InterruptedException {
        Channel clientChannel = connect(clientBootstrap, port);
        clientChannel.writeAndFlush(new DefaultHttpRequest(HTTP_1_1, HttpMethod.GET, "/",
                                                           new ReadOnlyHttpHeaders(false, HOST,
                                                                                   "localhost")));
        assertTrue(responseReceivedLatch.get().await(5, SECONDS));
        assertTrue(clientChannel.close().awaitUninterruptibly().isSuccess());

        responseReceivedLatch.set(new CountDownLatch(1));
        assertEquals("Server should have been called.", 1,
                     serverResponseCountPerStatus.get(HttpResponseStatus.OK).intValue());
        assertEquals("Hello World", responseContent.getAndSet(null));

        clientChannel = connect(clientBootstrap, port);
        clientChannel.writeAndFlush(new DefaultHttpRequest(HTTP_1_1, HttpMethod.GET, "/",
                                                           new ReadOnlyHttpHeaders(false, HOST,
                                                                                   "localhost")));
        assertTrue(responseReceivedLatch.get().await(5, SECONDS));

        assertEquals("Server should not have been called.", 1,
                     serverResponseCountPerStatus.get(HttpResponseStatus.OK).intValue());
        assertEquals("Hello World", responseContent.getAndSet(null));
    }

    @Test
    public void shouldNotLoadFromCache() throws InterruptedException {
        Channel clientChannel = connect(clientBootstrap, port);
        clientChannel.writeAndFlush(new DefaultHttpRequest(HTTP_1_1, HttpMethod.POST, "/",
                                                           new ReadOnlyHttpHeaders(false, HOST,
                                                                                   "localhost")));
        assertTrue(responseReceivedLatch.get().await(5, SECONDS));
        assertTrue(clientChannel.close().awaitUninterruptibly().isSuccess());

        responseReceivedLatch.set(new CountDownLatch(1));
        assertEquals("Server should have been called.", 1,
                     serverResponseCountPerStatus.get(HttpResponseStatus.OK).intValue());

        clientChannel = connect(clientBootstrap, port);
        clientChannel.writeAndFlush(new DefaultHttpRequest(HTTP_1_1, HttpMethod.POST, "/",
                                                           new ReadOnlyHttpHeaders(false, HOST,
                                                                                   "localhost")));
        assertTrue(responseReceivedLatch.get().await(5, SECONDS));

        assertEquals("Server should have been called.", 2,
                     serverResponseCountPerStatus.get(HttpResponseStatus.OK).intValue());
    }

    @Test
    public void shouldRevalidateCacheEntry() throws InterruptedException {
        Channel clientChannel = connect(clientBootstrap, port);
        clientChannel.writeAndFlush(new DefaultHttpRequest(HTTP_1_1, HttpMethod.GET, "/",
                                                           new ReadOnlyHttpHeaders(false, HOST,
                                                                                   "localhost")));
        assertTrue(responseReceivedLatch.get().await(5, SECONDS));
        assertTrue(clientChannel.close().awaitUninterruptibly().isSuccess());

        responseReceivedLatch.set(new CountDownLatch(1));
        assertEquals("Server should have been called.", 1,
                     serverResponseCountPerStatus.get(HttpResponseStatus.OK).intValue());
        assertEquals("Hello World", responseContent.getAndSet(null));

        clientChannel = connect(clientBootstrap, port);
        clientChannel.writeAndFlush(new DefaultHttpRequest(HTTP_1_1, HttpMethod.GET, "/",
                                                           new ReadOnlyHttpHeaders(false, HOST,
                                                                                   "localhost",
                                                                                   IF_NONE_MATCH,
                                                                                   "etagValue")));
        assertTrue(responseReceivedLatch.get().await(5, SECONDS));

        assertEquals("Server should have been called for revalidation.", 1,
                     serverResponseCountPerStatus.get(HttpResponseStatus.NOT_MODIFIED).intValue());
        assertEquals("Hello World", responseContent.getAndSet(null));
    }
}
