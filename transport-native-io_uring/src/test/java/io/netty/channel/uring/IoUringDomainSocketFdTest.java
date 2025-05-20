/*
 * Copyright 2025 The Netty Project
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

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.unix.DomainSocketReadMode;
import io.netty.channel.unix.FileDescriptor;
import io.netty.testsuite.transport.TestsuitePermutation;
import io.netty.testsuite.transport.socket.AbstractSocketTest;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Timeout;

import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IoUringDomainSocketFdTest extends AbstractSocketTest {
    @Override
    protected SocketAddress newSocketAddress() {
        return IoUringSocketTestPermutation.newDomainSocketAddress();
    }

    @Override
    protected List<TestsuitePermutation.BootstrapComboFactory<ServerBootstrap, Bootstrap>> newFactories() {
        return IoUringSocketTestPermutation.INSTANCE.domainSocket();
    }

    @Test
    @Timeout(value = 30000, unit = TimeUnit.MILLISECONDS)
    public void testSendRecvFd(TestInfo testInfo) throws Throwable {
        run(testInfo, new Runner<ServerBootstrap, Bootstrap>() {
            @Override
            public void run(ServerBootstrap serverBootstrap, Bootstrap bootstrap) throws Throwable {
                testSendRecvFd(serverBootstrap, bootstrap);
            }
        });
    }

    public void testSendRecvFd(ServerBootstrap sb, Bootstrap cb) throws Throwable {

        String expected = "Hello World";
        CompletableFuture<FileDescriptor> recvFdFuture = new CompletableFuture<>();
        CompletableFuture<ByteBuf> recvByteBufFuture = new CompletableFuture<>();

        sb.childHandler(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                // Create new channel and obtain a file descriptor from it.
                final IoUringDomainSocketChannel ch = new IoUringDomainSocketChannel();

                ctx.writeAndFlush(ch.fd()).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            Throwable cause = future.cause();
                            recvFdFuture.completeExceptionally(cause);
                        } else {
                            ByteBuf sendBuffer = ctx.alloc().directBuffer(expected.length());
                            sendBuffer.writeBytes(expected.getBytes());
                            ctx.writeAndFlush(sendBuffer).addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    if (!future.isSuccess()) {
                                        Throwable cause = future.cause();
                                        recvByteBufFuture.completeExceptionally(cause);
                                    }
                                }
                            });
                        }
                    }
                });
            }
        });

        cb.handler(new ChannelInboundHandlerAdapter() {

            private CompositeByteBuf byteBufs;

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof FileDescriptor) {
                    FileDescriptor fd = (FileDescriptor) msg;
                    recvFdFuture.complete(fd);
                    ctx.channel().config()
                            .setOption(IoUringChannelOption.DOMAIN_SOCKET_READ_MODE, DomainSocketReadMode.BYTES);
                } else {
                    byteBufs.addComponent(true, (ByteBuf) msg);
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                DomainSocketReadMode readMode = ctx.channel().config()
                        .getOption(IoUringChannelOption.DOMAIN_SOCKET_READ_MODE);
                if (readMode == DomainSocketReadMode.FILE_DESCRIPTORS) {
                    recvFdFuture.completeExceptionally(cause);
                } else {
                    recvByteBufFuture.completeExceptionally(cause);
                }
                ctx.close();
            }

            @Override
            public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
                if (byteBufs != null) {
                    recvByteBufFuture.complete(byteBufs);
                    byteBufs = null;
                } else {
                    // after receiving the file descriptor, we need to start receiving the data again.
                    byteBufs = ctx.alloc().compositeBuffer();
                }
            }
        });
        cb.option(IoUringChannelOption.DOMAIN_SOCKET_READ_MODE,
                DomainSocketReadMode.FILE_DESCRIPTORS);
        Channel sc = sb.bind().sync().channel();
        Channel cc = cb.connect(sc.localAddress()).sync().channel();

        FileDescriptor fd = recvFdFuture.get();
        assertTrue(fd.isOpen());
        assertNotEquals(0, fd.intValue());
        fd.close();
        assertFalse(fd.isOpen());

        ByteBuf recvBuffer = recvByteBufFuture.get();
       try {
           assertEquals(expected, recvBuffer.toString(Charset.defaultCharset()));
           cc.close().sync();
           sc.close().sync();
       } finally {
           ReferenceCountUtil.release(recvBuffer);
       }
    }
}
