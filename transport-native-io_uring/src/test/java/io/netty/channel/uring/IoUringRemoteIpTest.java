/*
 * Copyright 2024 The Netty Project
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
package io.netty.channel.uring;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IoUringRemoteIpTest {

    @BeforeAll
    public static void loadJNI() {
        Assumptions.assumeTrue(IoUring.isAvailable());
    }

    @Test
    public void testRemoteAddressIpv4() throws Exception {
        testRemoteAddress(NetUtil.LOCALHOST4, NetUtil.LOCALHOST4);
    }

    @Test
    public void testRemoteAddressIpv6() throws Exception {
        testRemoteAddress(NetUtil.LOCALHOST6, NetUtil.LOCALHOST6);
    }

    @Test
    public void testRemoteAddressIpv4AndServerAutoDetect() throws Exception {
        testRemoteAddress(null, NetUtil.LOCALHOST4);
    }

    @Test
    public void testRemoteAddressIpv6ServerAutoDetect() throws Exception {
        testRemoteAddress(null, NetUtil.LOCALHOST6);
    }

    private static void testRemoteAddress(InetAddress server, InetAddress client) throws Exception {
        final Promise<SocketAddress> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        Socket socket = new Socket();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup)
                    .channel(IoUringServerSocketChannel.class)
                    .childHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            promise.setSuccess(ctx.channel().remoteAddress());
                            ctx.close();
                        }
                    });

            // Start the server.
            ChannelFuture f;
            InetSocketAddress connectAddress;
            if (server == null) {
                f = b.bind(0).sync();
                connectAddress = new InetSocketAddress(client,
                        ((InetSocketAddress) f.channel().localAddress()).getPort());
            } else {
                try {
                    f = b.bind(server, 0).sync();
                } catch (Throwable cause) {
                    throw new TestAbortedException("Bind failed, address family not supported ?", cause);
                }
                connectAddress = (InetSocketAddress) f.channel().localAddress();
            }

            try {
                socket.bind(new InetSocketAddress(client, 0));
            } catch (SocketException e) {
                throw new TestAbortedException("Bind failed, address family not supported ?", e);
            }
            socket.connect(connectAddress);

            InetSocketAddress addr = (InetSocketAddress) promise.get();
            assertEquals(socket.getLocalSocketAddress(), addr);
            f.channel().close().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();

            socket.close();
        }
    }
}
