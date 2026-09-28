/*
 * Copyright 2026 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.flow.FlowControlHandler;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpServerCodecFlowControlTest {

    @Test
    void flowControlledBodyRelayContinuesReadingAfterEachWrite() throws Exception {
        final int bodySize = 1 << 20;
        final AtomicLong targetBytes = new AtomicLong();
        final CountDownLatch targetReceivedBody = new CountDownLatch(1);
        final EventLoopGroup group = new NioEventLoopGroup(1);
        Channel target = null;
        Channel proxy = null;
        try {
            target = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    ByteBuf buf = (ByteBuf) msg;
                                    if (targetBytes.addAndGet(buf.readableBytes()) >= bodySize) {
                                        targetReceivedBody.countDown();
                                    }
                                    buf.release();
                                }
                            });
                        }
                    }).bind("127.0.0.1", 0).sync().channel();
            final int targetPort = ((InetSocketAddress) target.localAddress()).getPort();

            proxy = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new FlowControlHandler());
                            ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                private Channel outbound;

                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                    if (msg instanceof HttpRequest) {
                                        ctx.channel().config().setAutoRead(false);
                                        Bootstrap bootstrap = new Bootstrap().group(ctx.channel().eventLoop())
                                                .channel(NioSocketChannel.class).option(ChannelOption.AUTO_READ, false)
                                                .handler(new ChannelInitializer<Channel>() {
                                                    @Override
                                                    protected void initChannel(Channel ch) {
                                                    }
                                                });
                                        ChannelFuture connect = bootstrap.connect("127.0.0.1", targetPort);
                                        outbound = connect.channel();
                                        connect.addListener(future -> {
                                            if (future.isSuccess()) {
                                                ctx.channel().read();
                                            }
                                        });
                                        ReferenceCountUtil.release(msg);
                                    } else if (msg instanceof HttpContent) {
                                        ByteBuf body = ((HttpContent) msg).content().retain();
                                        ReferenceCountUtil.release(msg);
                                        if (body.isReadable()) {
                                            outbound.writeAndFlush(body).addListener(future -> {
                                                if (future.isSuccess()) {
                                                    ctx.channel().read();
                                                }
                                            });
                                        } else {
                                            body.release();
                                            ctx.channel().read();
                                        }
                                    } else {
                                        ReferenceCountUtil.release(msg);
                                    }
                                }
                            });
                        }
                    }).bind("127.0.0.1", 0).sync().channel();
            final int proxyPort = ((InetSocketAddress) proxy.localAddress()).getPort();

            Thread client = new Thread(() -> {
                try (Socket socket = new Socket("127.0.0.1", proxyPort)) {
                    OutputStream out = socket.getOutputStream();
                    out.write(("PUT /upload HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: " + bodySize
                            + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    byte[] chunk = new byte[16 * 1024];
                    for (int written = 0; written < bodySize; written += chunk.length) {
                        out.write(chunk);
                        out.flush();
                    }
                    targetReceivedBody.await(10, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // A blocked client write is part of the relay-stall failure mode.
                }
            }, "flow-control-relay-client");
            client.setDaemon(true);
            client.start();

            assertTrue(targetReceivedBody.await(10, TimeUnit.SECONDS),
                    "relay stalled after forwarding " + targetBytes.get() + " of " + bodySize + " bytes");
        } finally {
            if (proxy != null) {
                proxy.close().syncUninterruptibly();
            }
            if (target != null) {
                target.close().syncUninterruptibly();
            }
            group.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
}
