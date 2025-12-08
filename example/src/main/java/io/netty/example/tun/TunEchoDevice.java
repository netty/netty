/*
 * Copyright 2022 The Netty Project
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
package io.netty.example.tun;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollTunChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueTunChannel;
import io.netty.channel.socket.TunAddress;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import static io.netty.channel.ChannelOption.RCVBUF_ALLOCATOR;
import static io.netty.channel.epoll.EpollTunChannelOption.IFF_MULTI_QUEUE;
import static io.netty.channel.kqueue.KQueueChannelOption.RCV_ALLOC_TRANSPORT_PROVIDES_GUESS;
import static io.netty.channel.socket.TunChannelOption.TUN_MTU;

/**
 * Creates a TUN device that echoes back all received IP packets.
 * <p/>
 * <h2>Usage Example:</h2>
 *
 * <pre>
 *     ./run-example tun-echo-device -Daddress=10.10.10.10 -Dnetmask=24 -Dmtu=1500
 * </pre>
 *
 * In a second shell:
 * <pre>
 *     iperf3 --server
 * </pre>
 *
 * In a third shell:
 * <pre>
 *     iperf3 --client 10.10.10.11
 * </pre>
 */
public final class TunEchoDevice {
    private static final Echo4Handler ECHO_4_HANDLER = new Echo4Handler();
    private static final Echo6Handler ECHO_6_HANDLER = new Echo6Handler();
    static final String NAME = System.getProperty("name", null);
    static final InetAddress ADDRESS;
    static final int NETMASK = Integer.parseInt(System.getProperty("netmask", "24"));
    static final int MTU = Integer.parseInt(System.getProperty("mtu", "1500"));
    static final int QUEUES = Integer.parseInt(System.getProperty("queues", "1"));

    static {
        try {
            ADDRESS = InetAddress.getByName(System.getProperty("address", "10.10.10.10"));
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        EventLoopGroup group;
        Class<? extends Channel> channelClass;
        if (KQueue.isAvailable()) {
            if (QUEUES > 1) {
                throw new RuntimeException("Parallel reading and writing is only supported with epoll");
            }
            group = new KQueueEventLoopGroup(1);
            channelClass = KQueueTunChannel.class;
        } else if (Epoll.isAvailable()) {
            group = new EpollEventLoopGroup(QUEUES);
            channelClass = EpollTunChannel.class;
        } else {
            throw new RuntimeException("Unsupported platform: Neither kqueue nor epoll are available");
        }

        try {
            Bootstrap b = new Bootstrap()
                    .group(group)
                    .channel(channelClass)
                    .option(TUN_MTU, MTU)
                    .option(RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(MTU)) // used by epoll
                    .option(IFF_MULTI_QUEUE, QUEUES > 1) // used by epoll
                    .option(RCV_ALLOC_TRANSPORT_PROVIDES_GUESS, true) // used by kqueue
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline p = ch.pipeline();

                            p.addLast(ECHO_4_HANDLER);
                            p.addLast(ECHO_6_HANDLER);
                        }
                    });
            TunAddress address = new TunAddress(NAME);
            final CountDownLatch latch = new CountDownLatch(QUEUES);
            for (int i = 0; i < QUEUES; i++) {
                Channel ch = b.bind(address).syncUninterruptibly().channel();
                ch.closeFuture().addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(final ChannelFuture future) {
                        latch.countDown();
                        if (future.cause() != null) {
                            future.cause().printStackTrace();
                        }
                    }
                });
                System.out.println("TUN device created: " + ch.localAddress());

                if (i == 0) {
                    // reuse tun address for any further devices
                    address = (TunAddress) ch.localAddress();

                    String name = address.ifName();
                    System.out.println("TUN device created: " + name);

                    if (PlatformDependent.isOsx()) {
                        if (ADDRESS instanceof Inet6Address) {
                            exec("/sbin/ifconfig", name, "inet6", "add", ADDRESS.getHostAddress() + "/" + NETMASK);
                            exec("/sbin/route", "add", "-inet6", ADDRESS.getHostAddress(), "-iface", name);
                        } else {
                            exec("/sbin/ifconfig", name, "add", ADDRESS.getHostAddress(), ADDRESS.getHostAddress());
                            exec("/sbin/route", "add", "-net", ADDRESS.getHostAddress() + '/' + NETMASK, "-iface",
                                    name);
                        }
                    } else if (!PlatformDependent.isWindows()) {
                        String version = ADDRESS instanceof Inet6Address ? "-6" : "-4";
                        exec("/sbin/ip", version, "addr", "add", ADDRESS.getHostAddress() + '/' + NETMASK, "dev",
                                name);
                        exec("/sbin/ip", "link", "set", "dev", name, "up");
                    }

                    System.out.println("Address and netmask assigned: " + ADDRESS.getHostAddress() + '/' + NETMASK);
                    System.out.println("All IP packets addressed to this subnet "
                            + (PlatformDependent.isOsx() ? "" : "(except for " + ADDRESS.getHostAddress() + ") ")
                            + "should now be echoed back.");
                }
            }
            latch.await();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void exec(String... command) throws IOException {
        try {
            int exitCode = Runtime.getRuntime().exec(command).waitFor();
            if (exitCode != 0) {
                CharSequence arguments = StringUtil.join(" ", Arrays.asList(command));
                throw new IOException("Executing `" + arguments + "` returned non-zero exit code (" + exitCode + ").");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
