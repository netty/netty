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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.IoEvent;
import io.netty.channel.IoHandle;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoRegistration;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.SocketProtocolFamily;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.ThreadAwareExecutor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.channel.unix.Errors.ERRNO_EAGAIN_NEGATIVE;
import static io.netty.channel.unix.Errors.ERRNO_EBADF_NEGATIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
public class IoUringDatagramHardlinkTest {

    private static final int DATAGRAM_SIZE = 512;
    private static final int BATCH_SIZE = 4;

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    public void testRecvmmsgHardlinkOrder() throws Exception {
        CompletionRecorder recorder = new CompletionRecorder();
        IoHandlerFactory factory = executor -> new RecordingIoUringIoHandler(
                IoUringIoHandler.newFactory().newHandler(executor), recorder);
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, factory);
        Channel receiver = null;
        Channel sender = null;
        try {
            receiver = newBootstrap(group)
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0))
                    .sync()
                    .channel();

            sender = new Bootstrap()
                    .group(group)
                    .channelFactory(() -> new IoUringDatagramChannel(SocketProtocolFamily.INET))
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0))
                    .sync()
                    .channel();

            // The hard linked recvmsg chain must park as a whole. Without hard linking the
            // MSG_DONTWAIT recvmsg ops would already have completed with EAGAIN here.
            Thread.sleep(100);
            assertEquals(0, recorder.size());

            sender.writeAndFlush(new DatagramPacket(
                    sender.alloc().buffer(DATAGRAM_SIZE).writeZero(DATAGRAM_SIZE),
                    (InetSocketAddress) receiver.localAddress())).sync();
            recorder.await(BATCH_SIZE);

            assertEquals(Arrays.asList(
                    DATAGRAM_SIZE,
                    ERRNO_EAGAIN_NEGATIVE,
                    ERRNO_EAGAIN_NEGATIVE,
                    ERRNO_EAGAIN_NEGATIVE), recorder.completions());
        } finally {
            if (sender != null) {
                sender.close().syncUninterruptibly();
            }
            if (receiver != null) {
                receiver.close().syncUninterruptibly();
            }
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    @Test
    public void testClosePendingRecvmmsgHardlink() throws Exception {
        CompletionRecorder recorder = new CompletionRecorder();
        IoHandlerFactory factory = executor -> new RecordingIoUringIoHandler(
                IoUringIoHandler.newFactory().newHandler(executor), recorder);
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, factory);
        Channel receiver = null;
        Channel sender = null;
        try {
            receiver = newBootstrap(group)
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0))
                    .sync()
                    .channel();

            sender = new Bootstrap()
                    .group(group)
                    .channelFactory(() -> new IoUringDatagramChannel(SocketProtocolFamily.INET))
                    .handler(new ChannelInboundHandlerAdapter())
                    .bind(new InetSocketAddress(NetUtil.LOCALHOST4, 0))
                    .sync()
                    .channel();

            // Let the read loop schedule the first batch and the AUTO_READ re-scheduled batch.
            Thread.sleep(100);
            assertEquals(0, recorder.size());

            sender.writeAndFlush(new DatagramPacket(
                    sender.alloc().buffer(DATAGRAM_SIZE).writeZero(DATAGRAM_SIZE),
                    (InetSocketAddress) receiver.localAddress())).sync();
            recorder.await(BATCH_SIZE);

            // Close while the re-scheduled (hard linked) batch is still pending. Without the fix in
            // readComplete(...) the fd would be closed before all recvmsg ops completed and so some
            // of them would complete with EBADF.
            receiver.close().syncUninterruptibly();
            recorder.await(BATCH_SIZE * 2);

            assertFalse(recorder.completions().contains(ERRNO_EBADF_NEGATIVE), recorder.completions().toString());
        } finally {
            if (sender != null) {
                sender.close().syncUninterruptibly();
            }
            if (receiver != null) {
                receiver.close().syncUninterruptibly();
            }
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    private static Bootstrap newBootstrap(MultiThreadIoEventLoopGroup group) {
        return new Bootstrap()
                .group(group)
                .channelFactory(() -> new IoUringDatagramChannel(SocketProtocolFamily.INET))
                .option(ChannelOption.RECVBUF_ALLOCATOR,
                        new FixedRecvByteBufAllocator(BATCH_SIZE * DATAGRAM_SIZE))
                .option(IoUringChannelOption.MAX_DATAGRAM_PAYLOAD_SIZE, DATAGRAM_SIZE);
    }

    private static final class CompletionRecorder {
        private final List<Integer> completions = new ArrayList<Integer>();

        synchronized void record(IoUringIoEvent event) {
            if (event.opcode() == Native.IORING_OP_RECVMSG) {
                completions.add(event.res());
                notifyAll();
            }
        }

        synchronized int size() {
            return completions.size();
        }

        synchronized List<Integer> completions() {
            return new ArrayList<Integer>(completions);
        }

        synchronized void await(int count) throws InterruptedException {
            while (completions.size() < count) {
                wait();
            }
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
