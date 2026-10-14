/*
 * Copyright 2026 The Netty Project
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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoOps;
import io.netty.channel.IoRegistration;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.unix.Buffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class IoUringSendmsgZCIovTest {

    private static final int SENDMSG_ZC_COUNT = 2 * Native.MAX_SKB_FRAGS;

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
        assumeTrue(IoUring.isSendmsgZcSupported());
    }

    @Test
    public void testSendmsgZcPollutesNextWritev() throws Exception {
        ArrayList<IoUringIoOps> ioOps = new ArrayList<>();
        IoHandlerFactory factory = executor ->
            new RecordingIoUringIoHandler(IoUringIoHandler.newFactory().newHandler(executor), ioOps);
        MultiThreadIoEventLoopGroup ioEventLoopGroup = new MultiThreadIoEventLoopGroup(1, factory);
        try {
            prepChannels(ioEventLoopGroup).join();
            assertEquals(2, ioOps.size());
            IoUringIoOps sendmsgzcOps = ioOps.get(0);
            IoUringIoOps writevOps = ioOps.get(1);
            int sendmsgIovLength = sendmsgIovLength(sendmsgzcOps);
            int writevIovLength = writevIovLength(writevOps);
            assertEquals(SENDMSG_ZC_COUNT, sendmsgIovLength);
            assertEquals(2, writevIovLength);
        } finally {
            ioEventLoopGroup.shutdownGracefully();
        }
    }

    public CompletableFuture<Void> prepChannels(MultiThreadIoEventLoopGroup ioEventLoopGroup)
        throws InterruptedException {
        ServerChannel server = server(ioEventLoopGroup);
        Channel sendmsgzcChannel = connect(ioEventLoopGroup, server);
        sendmsgzcChannel.setOption(
            IoUringChannelOption.IO_URING_WRITE_ZERO_COPY_THRESHOLD, 0
        );
        Channel writvChannel = connect(ioEventLoopGroup, server);
        ByteBuf byteBuf = Unpooled.directBuffer(1024);
        byteBuf.writerIndex(1024);
        byteBuf.retain(SENDMSG_ZC_COUNT + 2 - 1);
        CompletableFuture<Void> prepFuture = new CompletableFuture<>();
        ioEventLoopGroup.execute(() -> {
            for (int i = 0; i < SENDMSG_ZC_COUNT; i++) {
                sendmsgzcChannel.write(byteBuf);
            }
            for (int i = 0; i < 2; i++) {
                writvChannel.write(byteBuf);
            }
            sendmsgzcChannel.flush();
            writvChannel.flush();
            prepFuture.complete(null);
        });
        return prepFuture;
    }

    private static Channel connect(MultiThreadIoEventLoopGroup group, ServerChannel server)
        throws InterruptedException {
        return new Bootstrap()
                .group(group)
                .channel(IoUringSocketChannel.class)
                .option(ChannelOption.AUTO_READ, false)
                .handler(new ChannelInboundHandlerAdapter())
                .connect(server.localAddress())
                .sync().channel();
    }

    private static ServerChannel server(MultiThreadIoEventLoopGroup group) throws InterruptedException {
        return (ServerChannel) new ServerBootstrap()
            .channel(IoUringServerSocketChannel.class)
            .group(group, group)
            .childHandler(new ChannelInitializer<Channel>() {
                @Override
                public void initChannel(Channel ch) throws Exception {
                }
            })
            .bind(0)
            .sync().channel();
    }

    private static int sendmsgIovLength(IoUringIoOps ops) {
        ByteBuffer msgHdr = Buffer.wrapMemoryAddressWithNativeOrder(ops.union2(), Native.SIZEOF_MSGHDR);
        if (Native.SIZEOF_SIZE_T == 4) {
            return msgHdr.getInt(Native.MSGHDR_OFFSETOF_MSG_IOVLEN);
        } else {
            return (int) msgHdr.getLong(Native.MSGHDR_OFFSETOF_MSG_IOVLEN);
        }
    }

    private static int writevIovLength(IoUringIoOps ops) {
        return ops.len();
    }

    private static final class RecordingIoUringIoHandler implements IoHandler {
        private final IoHandler delegate;
        private final List<IoUringIoOps> writes;

        RecordingIoUringIoHandler(IoHandler delegate, List<IoUringIoOps> ioUringIoOps) {
            this.delegate = delegate;
            this.writes = ioUringIoOps;
        }

        @Override
        public void initialize() {
            delegate.initialize();
        }

        @Override
        public int run(IoHandlerContext context) {
            return delegate.run(context);
        }

        @Override
        public void prepareToDestroy() {
            delegate.prepareToDestroy();
        }

        @Override
        public void destroy() {
            delegate.destroy();
        }

        @Override
        public IoRegistration register(IoHandle handle) throws Exception {
            IoRegistration registration = delegate.register(handle);
            return new IoRegistration() {
                @Override
                public <T> T attachment() {
                    return registration.attachment();
                }

                @Override
                public long submit(IoOps ops) {
                    IoUringIoOps operation = (IoUringIoOps) ops;
                    if (operation.opcode() == Native.IORING_OP_SENDMSG_ZC ||
                            operation.opcode() == Native.IORING_OP_WRITEV) {
                        writes.add(operation);
                    }
                    return registration.submit(ops);
                }

                @Override
                public boolean isValid() {
                    return registration.isValid();
                }

                @Override
                public boolean cancel() {
                    return registration.cancel();
                }
            };
        }

        @Override
        public void wakeup() {
            delegate.wakeup();
        }

        @Override
        public boolean isCompatible(Class<? extends IoHandle> handleType) {
            return delegate.isCompatible(handleType);
        }
    }
}
