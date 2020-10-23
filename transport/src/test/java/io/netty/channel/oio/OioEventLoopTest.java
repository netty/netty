/*
 * Copyright 2013 The Netty Project
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

package io.netty.channel.oio;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.oio.OioServerSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.util.NetUtil;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class OioEventLoopTest {
    @Test
    public void testTooManyServerChannels() throws Exception {
        EventLoopGroup g = new OioEventLoopGroup(1);
        ServerBootstrap b = new ServerBootstrap();
        b.channel(OioServerSocketChannel.class);
        b.group(g);
        b.childHandler(new ChannelInboundHandlerAdapter());
        ChannelFuture f1 = b.bind(0);
        f1.sync();

        ChannelFuture f2 = b.bind(0);
        f2.await();

        assertThat(f2.cause(), is(instanceOf(ChannelException.class)));
        assertThat(f2.cause().getMessage().toLowerCase(), containsString("too many channels"));

        final CountDownLatch notified = new CountDownLatch(1);
        f2.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                notified.countDown();
            }
        });

        notified.await();
        g.shutdownGracefully();
    }

    @Test
    public void testTooManyClientChannels() throws Exception {
        EventLoopGroup g = new OioEventLoopGroup(1);
        ServerBootstrap sb = new ServerBootstrap();
        sb.channel(OioServerSocketChannel.class);
        sb.group(g);
        sb.childHandler(new ChannelInboundHandlerAdapter());
        ChannelFuture f1 = sb.bind(0);
        f1.sync();

        Bootstrap cb = new Bootstrap();
        cb.channel(OioSocketChannel.class);
        cb.group(g);
        cb.handler(new ChannelInboundHandlerAdapter());
        ChannelFuture f2 = cb.connect(NetUtil.LOCALHOST, ((InetSocketAddress) f1.channel().localAddress()).getPort());
        f2.await();

        assertThat(f2.cause(), is(instanceOf(ChannelException.class)));
        assertThat(f2.cause().getMessage().toLowerCase(), containsString("too many channels"));

        final CountDownLatch notified = new CountDownLatch(1);
        f2.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                notified.countDown();
            }
        });

        notified.await();
        g.shutdownGracefully();
    }

    @Test
    public void testTooManyAcceptedChannels() throws Exception {
        EventLoopGroup g = new OioEventLoopGroup(1);
        ServerBootstrap sb = new ServerBootstrap();
        sb.channel(OioServerSocketChannel.class);
        sb.group(g);
        sb.childHandler(new ChannelInboundHandlerAdapter());
        ChannelFuture f1 = sb.bind(0);
        f1.sync();

        Socket s = new Socket(NetUtil.LOCALHOST, ((InetSocketAddress) f1.channel().localAddress()).getPort());
        assertThat(s.getInputStream().read(), is(-1));
        s.close();

        g.shutdownGracefully();
    }
}
