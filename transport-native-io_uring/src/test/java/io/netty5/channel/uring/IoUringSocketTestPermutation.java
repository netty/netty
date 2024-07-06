/*
 * Copyright 2020 The Netty Project
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
package io.netty5.channel.uring;

import io.netty5.bootstrap.Bootstrap;
import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.AdaptiveReadHandleFactory;
import io.netty5.channel.Channel;
import io.netty5.channel.ChannelFactory;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.socket.SocketChannelWriteHandleFactory;
import io.netty5.channel.socket.nio.NioDatagramChannel;
import io.netty5.channel.socket.nio.NioServerSocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;
import io.netty5.testsuite.transport.TestsuitePermutation;
import io.netty5.testsuite.transport.TestsuitePermutation.BootstrapComboFactory;
import io.netty5.testsuite.transport.TestsuitePermutation.BootstrapFactory;
import io.netty5.testsuite.transport.socket.SocketTestPermutation;
import io.netty5.util.concurrent.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.ProtocolFamily;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.netty5.channel.unix.Limits.SSIZE_MAX;

public class IoUringSocketTestPermutation extends SocketTestPermutation {

    static final IoUringSocketTestPermutation INSTANCE = new IoUringSocketTestPermutation();

    static final EventLoopGroup IO_URING_BOSS_GROUP = new MultithreadEventLoopGroup(
            BOSSES, new DefaultThreadFactory("testsuite-io_uring-boss", true), IoUringIoHandler.newFactory());
    static final EventLoopGroup IO_URING_WORKER_GROUP = new MultithreadEventLoopGroup(
            WORKERS, new DefaultThreadFactory("testsuite-io_uring-worker", true), IoUringIoHandler.newFactory());

    private static final Logger logger = LoggerFactory.getLogger(IoUringSocketTestPermutation.class);

    @Override
    public List<BootstrapComboFactory<ServerBootstrap, Bootstrap>> socket() {

        List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> list =
                combo(serverSocket(), clientSocket());

        list.remove(list.size() - 1); // Exclude NIO x NIO test

        return list;
    }

    @Override
    public List<BootstrapFactory<ServerBootstrap>> serverSocket() {
        List<BootstrapFactory<ServerBootstrap>> toReturn = new ArrayList<>();
        toReturn.add(serverIouBootstrapBase());
        if (isServerFastOpen()) {
            toReturn.add(() -> {
                ServerBootstrap serverBootstrap = new ServerBootstrap().group(IO_URING_BOSS_GROUP,
                                                                              IO_URING_WORKER_GROUP)
                                                                       .channel(IoUringServerSocketChannel.class);
                serverBootstrap.option(IoUringChannelOption.TCP_FASTOPEN, 5);
                return serverBootstrap;
            });
        }
        toReturn.add(serverNioBootstrapBase());

        return toReturn;
    }

    private BootstrapFactory<ServerBootstrap> serverNioBootstrapBase() {
        return () -> new ServerBootstrap().group(nioBossGroup, nioWorkerGroup)
                .channel(NioServerSocketChannel.class);
    }

    private BootstrapFactory<ServerBootstrap> serverIouBootstrapBase() {
        return () -> new ServerBootstrap().group(IO_URING_BOSS_GROUP, IO_URING_WORKER_GROUP)
                .channel(IoUringServerSocketChannel.class);
    }

    @Override
    public List<BootstrapFactory<Bootstrap>> clientSocket() {
        return Arrays.asList(
                clientIouBootstrapBase(),
                clientNioBoostrapBase()
        );
    }

    private BootstrapFactory<Bootstrap> clientIouBootstrapBase() {
        return () -> new Bootstrap().group(IO_URING_WORKER_GROUP).channel(IoUringSocketChannel.class);
    }

    private BootstrapFactory<Bootstrap> clientNioBoostrapBase() {
        return () -> new Bootstrap().group(nioWorkerGroup).channel(NioSocketChannel.class);
    }

    @Override
    public List<TestsuitePermutation.BootstrapComboFactory<Bootstrap, Bootstrap>> datagram(
            final ProtocolFamily family) {
        // Make the list of Bootstrap factories.
        List<BootstrapFactory<Bootstrap>> bfs = Arrays.asList(
                () -> new Bootstrap()
                        .group(IO_URING_WORKER_GROUP)
                        .channelFactory(new ChannelFactory<>() {
                    @Override
                    public Channel newChannel(EventLoop eventLoop) {
                        return new IoUringDatagramChannel(
                                null, eventLoop, true,
                                new AdaptiveReadHandleFactory(),
                                new SocketChannelWriteHandleFactory(Integer.MAX_VALUE, SSIZE_MAX),
                                LinuxSocket.newSocketDgram(family), false);
                    }

                    @Override
                    public String toString() {
                        return ProtocolFamily.class.getSimpleName() + ".class";
                    }
                }),
                () -> new Bootstrap().group(nioWorkerGroup).channelFactory(new ChannelFactory<>() {
                    @Override
                    public Channel newChannel(EventLoop eventLoop) {
                        return new NioDatagramChannel(eventLoop, family);
                    }

                    @Override
                    public String toString() {
                        return NioDatagramChannel.class.getSimpleName() + ".class";
                    }
                })
        );

        return combo(bfs, bfs);
    }

    public boolean isServerFastOpen() {
        int fastopen = 0;
        File file = new File("/proc/sys/net/ipv4/tcp_fastopen");
        if (file.exists()) {
            BufferedReader in = null;
            try {
                in = new BufferedReader(new FileReader(file));
                fastopen = Integer.parseInt(in.readLine());
                if (logger.isDebugEnabled()) {
                    logger.debug("{}: {}", file, fastopen);
                }
            } catch (Exception e) {
                logger.debug("Failed to get TCP_FASTOPEN from: {}", file, e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception e) {
                        // Ignored.
                    }
                }
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("{}: {} (non-existent)", file, fastopen);
            }
        }
        return fastopen == 3;
    }
}
