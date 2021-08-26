/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.unix.DomainSocketReadMode;
import io.netty.channel.unix.FileDescriptor;
import io.netty.channel.unix.UnixChannelOption;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.AbstractSocketTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class KQueueDomainSocketFdTest extends AbstractSocketTest {
    @Override
    protected SocketAddress newSocketAddress() {
        return KQueueSocketTestPermutation.newSocketAddress();
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return KQueueSocketTestPermutation.INSTANCE.domainSocket();
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSendRecvFd(TestInfo testInfo) throws Throwable {
        run(testInfo, this::testSendRecvFd);
    }

    public void testSendRecvFd(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final BlockingQueue<Object> queue = new LinkedBlockingQueue<>(1);
        sb.childHandler(new ChannelHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                // Create new channel and obtain a file descriptor from it.
                final KQueueDomainSocketChannel ch = new KQueueDomainSocketChannel(ctx.channel().executor());

                ctx.writeAndFlush(ch.fd()).addListener(future -> {
                    if (future.isFailed()) {
                        Throwable cause = future.cause();
                        queue.offer(cause);
                    }
                });
            }
        });
        cb.handler(new ChannelHandler() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                FileDescriptor fd = (FileDescriptor) msg;
                queue.offer(fd);
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                queue.add(cause);
                ctx.close();
            }
        });
        cb.option(UnixChannelOption.DOMAIN_SOCKET_READ_MODE,
                  DomainSocketReadMode.FILE_DESCRIPTORS);
        Channel sc = sb.bind().get();
        Channel cc = cb.connect(sc.localAddress()).get();

        Object received = queue.take();
        cc.close().sync();
        sc.close().sync();

        if (received instanceof FileDescriptor) {
            FileDescriptor fd = (FileDescriptor) received;
            assertTrue(fd.isOpen());
            fd.close();
            assertFalse(fd.isOpen());
            assertNull(queue.poll());
        } else {
            throw (Throwable) received;
        }
    }
}
