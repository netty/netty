/*
 * Copyright 2015 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.NetUtil;
import io.netty.util.internal.PlatformDependent;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class EpollSpliceTest {

    private static final int SPLICE_LEN = 32 * 1024;
    private static final Random random = new Random();
    private static final byte[] data = new byte[1048576];

    static {
        random.nextBytes(data);
    }

    @Test
    public void spliceToSocket() throws Throwable {
        final EchoHandler sh = new EchoHandler();
        final EchoHandler ch = new EchoHandler();

        EventLoopGroup group = new EpollEventLoopGroup(1);
        ServerBootstrap bs = new ServerBootstrap();
        bs.channel(EpollServerSocketChannel.class);
        bs.group(group).childHandler(sh);
        final Channel sc = bs.bind(NetUtil.LOCALHOST, 0).syncUninterruptibly().channel();

        ServerBootstrap bs2 = new ServerBootstrap();
        bs2.channel(EpollServerSocketChannel.class);
        bs2.childOption(EpollChannelOption.EPOLL_MODE, EpollMode.LEVEL_TRIGGERED);
        bs2.group(group).childHandler(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(final ChannelHandlerContext ctx) throws Exception {
                ctx.channel().config().setAutoRead(false);
                Bootstrap bs = new Bootstrap();
                bs.option(EpollChannelOption.EPOLL_MODE, EpollMode.LEVEL_TRIGGERED);

                bs.channel(EpollSocketChannel.class);
                bs.group(ctx.channel().eventLoop()).handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext context) throws Exception {
                        final EpollSocketChannel ch = (EpollSocketChannel) ctx.channel();
                        final EpollSocketChannel ch2 = (EpollSocketChannel) context.channel();
                        // We are splicing two channels together, at this point we have a tcp proxy which handles all
                        // the data transfer only in kernel space!

                        // Integer.MAX_VALUE will splice infinitly.
                        ch.spliceTo(ch2, Integer.MAX_VALUE).addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (!future.isSuccess()) {
                                    future.channel().close();
                                }
                            }
                        });
                        // Trigger multiple splices to see if partial splicing works as well.
                        ch2.spliceTo(ch, SPLICE_LEN).addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (!future.isSuccess()) {
                                    future.channel().close();
                                } else {
                                    ch2.spliceTo(ch, SPLICE_LEN).addListener(this);
                                }
                            }
                        });
                        ctx.channel().config().setAutoRead(true);
                    }

                    @Override
                    public void channelInactive(ChannelHandlerContext context) throws Exception {
                        context.close();
                    }
                });
                bs.connect(sc.localAddress()).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            ctx.close();
                        } else {
                            future.channel().closeFuture().addListener(new ChannelFutureListener() {
                                @Override
                                public void operationComplete(ChannelFuture future) throws Exception {
                                    ctx.close();
                                }
                            });
                        }
                    }
                });
            }
        });
        Channel pc = bs2.bind(NetUtil.LOCALHOST, 0).syncUninterruptibly().channel();

        Bootstrap cb = new Bootstrap();
        cb.group(group);
        cb.channel(EpollSocketChannel.class);
        cb.handler(ch);
        Channel cc = cb.connect(pc.localAddress()).syncUninterruptibly().channel();

        for (int i = 0; i < data.length;) {
            int length = Math.min(random.nextInt(1024 * 64), data.length - i);
            ByteBuf buf = Unpooled.wrappedBuffer(data, i, length);
            cc.writeAndFlush(buf);
            i += length;
        }

        while (ch.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        while (sh.counter < data.length) {
            if (sh.exception.get() != null) {
                break;
            }
            if (ch.exception.get() != null) {
                break;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        sh.channel.close().sync();
        ch.channel.close().sync();
        sc.close().sync();
        pc.close().sync();
        group.shutdownGracefully();

        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null && !(ch.exception.get() instanceof IOException)) {
            throw ch.exception.get();
        }
        if (sh.exception.get() != null) {
            throw sh.exception.get();
        }
        if (ch.exception.get() != null) {
            throw ch.exception.get();
        }
    }

    @Test(timeout = 10000)
    public void spliceToFile() throws Throwable {
        EventLoopGroup group = new EpollEventLoopGroup(1);
        File file = PlatformDependent.createTempFile("netty-splice", null, null);
        file.deleteOnExit();

        SpliceHandler sh = new SpliceHandler(file);
        ServerBootstrap bs = new ServerBootstrap();
        bs.channel(EpollServerSocketChannel.class);
        bs.group(group).childHandler(sh);
        bs.childOption(EpollChannelOption.EPOLL_MODE, EpollMode.LEVEL_TRIGGERED);
        Channel sc = bs.bind(NetUtil.LOCALHOST, 0).syncUninterruptibly().channel();

        Bootstrap cb = new Bootstrap();
        cb.group(group);
        cb.channel(EpollSocketChannel.class);
        cb.handler(new ChannelInboundHandlerAdapter());
        Channel cc = cb.connect(sc.localAddress()).syncUninterruptibly().channel();

        for (int i = 0; i < data.length;) {
            int length = Math.min(random.nextInt(1024 * 64), data.length - i);
            ByteBuf buf = Unpooled.wrappedBuffer(data, i, length);
            cc.writeAndFlush(buf);
            i += length;
        }

        while (sh.future2 == null || !sh.future2.isDone() || !sh.future.isDone()) {
            if (sh.exception.get() != null) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        sc.close().sync();
        cc.close().sync();

        if (sh.exception.get() != null && !(sh.exception.get() instanceof IOException)) {
            throw sh.exception.get();
        }

        byte[] written = new byte[data.length];
        FileInputStream in = new FileInputStream(file);

        try {
            Assert.assertEquals(written.length, in.read(written));
            Assert.assertArrayEquals(data, written);
        } finally {
            in.close();
            group.shutdownGracefully();
        }
    }

    private static class EchoHandler extends SimpleChannelInboundHandler<ByteBuf> {
        volatile Channel channel;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
        volatile int counter;

        @Override
        public void channelActive(ChannelHandlerContext ctx)
                throws Exception {
            channel = ctx.channel();
        }

        @Override
        public void channelRead0(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
            byte[] actual = new byte[in.readableBytes()];
            in.readBytes(actual);

            int lastIdx = counter;
            for (int i = 0; i < actual.length; i ++) {
                assertEquals(data[i + lastIdx], actual[i]);
            }

            if (channel.parent() != null) {
                channel.write(Unpooled.wrappedBuffer(actual));
            }

            counter += actual.length;
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            ctx.flush();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                                    Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                cause.printStackTrace();
                ctx.close();
            }
        }
    }

    private static class SpliceHandler extends ChannelInboundHandlerAdapter {
        private final File file;

        volatile ChannelFuture future;
        volatile ChannelFuture future2;
        final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();

        SpliceHandler(File file) {
            this.file = file;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            final EpollSocketChannel ch = (EpollSocketChannel) ctx.channel();
            final FileDescriptor fd = FileDescriptor.from(file);

            // splice two halves separately to test starting offset
            future = ch.spliceTo(fd, 0, data.length / 2);
            future2 = ch.spliceTo(fd, data.length / 2, data.length / 2);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                                    Throwable cause) throws Exception {
            if (exception.compareAndSet(null, cause)) {
                cause.printStackTrace();
                ctx.close();
            }
        }
    }
}
