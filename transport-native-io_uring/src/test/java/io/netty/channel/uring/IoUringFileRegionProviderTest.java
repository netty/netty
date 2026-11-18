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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.IoEvent;
import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoRegistration;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
public class IoUringFileRegionProviderTest {

    private static final int POSITION = 37;

    @TempDir
    Path directory;

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
        assumeTrue(IoUring.isSpliceSupported());
    }

    @Test
    public void testFileChannelUsesSplice() throws Exception {
        testFileRegion(directory.resolve("file"), false, Native.IORING_OP_SPLICE);
    }

    @Test
    public void testLazyFileRegionUsesSplice() throws Exception {
        testFileRegion(directory.resolve("file"), true, Native.IORING_OP_SPLICE);
    }

    @Test
    public void testZipFileChannelUsesSend() throws Exception {
        URI uri = URI.create("jar:" + directory.resolve("file.zip").toUri());
        try (FileSystem zip = FileSystems.newFileSystem(uri, Collections.singletonMap("create", "true"))) {
            testFileRegion(zip.getPath("/file"), false, Native.IORING_OP_SEND);
        }
    }

    private static void testFileRegion(Path path, boolean lazy, byte expectedOpcode) throws Exception {
        // Exercise partial splice transfers and more than one chunk on the generic FileRegion path.
        byte[] data = new byte[256 * 1024 + 73];
        new Random(0).nextBytes(data);
        Files.write(path, data);
        byte[] expected = Arrays.copyOfRange(data, POSITION, data.length - 19);

        CompletionRecorder recorder = new CompletionRecorder();
        IoHandlerFactory factory = executor -> new RecordingIoUringIoHandler(
                IoUringIoHandler.newFactory().newHandler(executor), recorder);
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, factory);
        CompletableFuture<Channel> accepted = new CompletableFuture<>();
        CompletableFuture<byte[]> received = new CompletableFuture<>();
        Channel server = null;
        Channel client = null;
        Channel child = null;
        try {
            server = new ServerBootstrap()
                    .group(group)
                    .channel(IoUringServerSocketChannel.class)
                    .childHandler(new SimpleChannelInboundHandler<ByteBuf>() {
                        private final byte[] bytes = new byte[expected.length];
                        private int offset;

                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            accepted.complete(ctx.channel());
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                            int length = msg.readableBytes();
                            msg.readBytes(bytes, offset, length);
                            offset += length;
                            if (offset == bytes.length) {
                                received.complete(bytes);
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            received.completeExceptionally(cause);
                            ctx.close();
                        }
                    })
                    .bind(NetUtil.LOCALHOST4, 0).sync().channel();
            client = new Bootstrap()
                    .group(group)
                    .channel(IoUringSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .connect(server.localAddress()).sync().channel();
            child = accepted.get(10, TimeUnit.SECONDS);

            DefaultFileRegion region = lazy ? new DefaultFileRegion(path.toFile(), POSITION, expected.length) :
                    new DefaultFileRegion(FileChannel.open(path, StandardOpenOption.READ), POSITION, expected.length);
            ChannelFuture write = client.write(region);
            if (lazy) {
                // Filtering a queued write must preserve the File constructor's lazy opening.
                client.eventLoop().submit(() -> {
                    assertThat(region.isOpen()).isFalse();
                }).sync();
            }
            client.flush();
            write.sync();

            assertThat(received.get(10, TimeUnit.SECONDS)).containsExactly(expected);
            assertThat(recorder.opcodes()).isNotEmpty().containsOnly(expectedOpcode);
            assertThat(region.refCnt()).isZero();
        } finally {
            if (client != null) {
                client.close().syncUninterruptibly();
            }
            if (child != null) {
                child.close().syncUninterruptibly();
            }
            if (server != null) {
                server.close().syncUninterruptibly();
            }
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    private static final class CompletionRecorder {
        private final List<Byte> opcodes = new ArrayList<>();

        synchronized void record(IoUringIoEvent event) {
            byte opcode = event.opcode();
            if (opcode == Native.IORING_OP_SPLICE || opcode == Native.IORING_OP_SEND ||
                    opcode == Native.IORING_OP_SEND_ZC || opcode == Native.IORING_OP_WRITE ||
                    opcode == Native.IORING_OP_WRITEV) {
                opcodes.add(opcode);
            }
        }

        synchronized List<Byte> opcodes() {
            return new ArrayList<>(opcodes);
        }
    }

    private static final class RecordingIoUringIoHandler implements IoHandler {
        private final IoHandler delegate;
        private final CompletionRecorder recorder;

        RecordingIoUringIoHandler(IoHandler delegate, CompletionRecorder recorder) {
            this.delegate = delegate;
            this.recorder = recorder;
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
            IoUringIoHandle ioHandle = (IoUringIoHandle) handle;
            return delegate.register(new IoUringIoHandle() {
                @Override
                public void handle(IoRegistration registration, IoEvent event) {
                    recorder.record((IoUringIoEvent) event);
                    ioHandle.handle(registration, event);
                }

                @Override
                public void registered() {
                    ioHandle.registered();
                }

                @Override
                public void unregistered() {
                    ioHandle.unregistered();
                }

                @Override
                public void close() throws Exception {
                    ioHandle.close();
                }
            });
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
