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
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.ReadOnlyHttpHeaders;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.buffer.Unpooled.*;
import static io.netty.handler.codec.http.HttpVersion.*;
import static java.util.concurrent.TimeUnit.*;
import static org.junit.Assert.*;

public class HttpClientCacheIntegrationTest {

    @Test
    public void loadFromCache() throws InterruptedException {
        ServerBootstrap sb = new ServerBootstrap();
        Bootstrap cb = new Bootstrap();
        final CountDownLatch serverChannelLatch = new CountDownLatch(1);
        final AtomicReference<CountDownLatch> responseReceivedLatch = new AtomicReference<CountDownLatch>(new CountDownLatch(1));
        final AtomicInteger serverRequestCount = new AtomicInteger(0);
        try {
            sb.group(new NioEventLoopGroup(2));
            sb.channel(NioServerSocketChannel.class);
            sb.childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    // Don't use the HttpServerCodec, because we don't want to have content-length or anything added.
                    ch.pipeline().addLast(new HttpServerCodec(4096, 8192, 8192, true));
                    ch.pipeline().addLast(new HttpObjectAggregator(4096));
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                        @Override
                        protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
                            serverRequestCount.incrementAndGet();
                            assertTrue(ctx.channel() instanceof SocketChannel);
                            final SocketChannel sChannel = (SocketChannel) ctx.channel();
                            final ReadOnlyHttpHeaders headers = new ReadOnlyHttpHeaders(false,
                                    HttpHeaderNames.DATE, DateFormatter.format(new Date()),
                                    HttpHeaderNames.CACHE_CONTROL, "max-age=5");
                            sChannel.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK,
                                    copiedBuffer("Hello World", CharsetUtil.UTF_8), headers, new ReadOnlyHttpHeaders(false)))
                                    .addListener(new ChannelFutureListener() {
                                        @Override
                                        public void operationComplete(final ChannelFuture future) {
                                            sChannel.shutdownOutput();
                                        }
                                    });
                        }
                    });
                    serverChannelLatch.countDown();
                }
            });

            final HttpCacheMemoryStorage cacheStorage = new HttpCacheMemoryStorage();
            cb.group(new NioEventLoopGroup(1));
            cb.channel(NioSocketChannel.class);
            cb.option(ChannelOption.ALLOW_HALF_CLOSURE, true);
            cb.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new HttpClientCodec(4096, 8192, 8192, true, true));
                    ch.pipeline().addLast(new HttpObjectAggregator(4096));
                    ch.pipeline().addLast(new HttpClientCacheHandler(cacheStorage, false));
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
                            responseReceivedLatch.get().countDown();
                        }
                    });
                }
            });

            Channel serverChannel = sb.bind(new InetSocketAddress(0)).sync().channel();
            int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();

            ChannelFuture ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
            assertTrue(ccf.awaitUninterruptibly().isSuccess());
            Channel clientChannel = ccf.channel();
            assertTrue(serverChannelLatch.await(5, SECONDS));
            clientChannel.writeAndFlush(new DefaultHttpRequest(HTTP_1_1, HttpMethod.GET, "/", new ReadOnlyHttpHeaders(false, HttpHeaderNames.HOST, "localhost")));
            assertTrue(responseReceivedLatch.get().await(5, SECONDS));
            assertTrue(clientChannel.close().awaitUninterruptibly().isSuccess());

            responseReceivedLatch.set(new CountDownLatch(1));

            ccf = cb.connect(new InetSocketAddress(NetUtil.LOCALHOST, port));
            assertTrue(ccf.awaitUninterruptibly().isSuccess());
            clientChannel = ccf.channel();
            clientChannel.writeAndFlush(new DefaultHttpRequest(HTTP_1_1, HttpMethod.GET, "/", new ReadOnlyHttpHeaders(false, HttpHeaderNames.HOST, "localhost")));
            assertTrue(responseReceivedLatch.get().await(5, SECONDS));

            assertEquals(1, serverRequestCount.get());
        } finally {
            sb.config().group().shutdownGracefully();
            sb.config().childGroup().shutdownGracefully();
            cb.config().group().shutdownGracefully();
        }

    }
}
