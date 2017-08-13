/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel.kqueue;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.unix.DomainSocketReadMode;
import io.netty.channel.unix.FileDescriptor;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.AbstractSocketTest;
import org.junit.Assert;
import org.junit.Test;

import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class KQueueDomainSocketFdTest extends AbstractSocketTest {
    @Override
    protected SocketAddress newSocketAddress() {
        return KQueueSocketTestPermutation.newSocketAddress();
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return KQueueSocketTestPermutation.INSTANCE.domainSocket();
    }

    @Test(timeout = 30000)
    public void testSendRecvFd() throws Throwable {
        run();
    }

    public void testSendRecvFd(ServerBootstrap sb, Bootstrap cb) throws Throwable {
        final BlockingQueue<Object> queue = new LinkedBlockingQueue<Object>(1);
        sb.childHandler(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                // Create new channel and obtain a file descriptor from it.
                final KQueueDomainSocketChannel ch = new KQueueDomainSocketChannel();

                ctx.writeAndFlush(ch.fd()).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            Throwable cause = future.cause();
                            queue.offer(cause);
                        }
                    }
                });
            }
        });
        cb.handler(new ChannelInboundHandlerAdapter() {
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
        cb.option(KQueueChannelOption.DOMAIN_SOCKET_READ_MODE,
                DomainSocketReadMode.FILE_DESCRIPTORS);
        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();

        Object received = queue.take();
        cc.close().sync();
        sc.close().sync();

        if (received instanceof FileDescriptor) {
            FileDescriptor fd = (FileDescriptor) received;
            Assert.assertTrue(fd.isOpen());
            fd.close();
            Assert.assertFalse(fd.isOpen());
            Assert.assertNull(queue.poll());
        } else {
            throw (Throwable) received;
        }
    }
}
