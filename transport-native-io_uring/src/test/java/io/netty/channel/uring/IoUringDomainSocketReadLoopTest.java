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
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ThreadAwareExecutor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.channel.unix.Errors.ERRNO_EAGAIN_NEGATIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
public class IoUringDomainSocketReadLoopTest {

    private static final int DATA_LENGTH = 4;

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    public void testReadLoop() throws Exception {
        List<Completion> expected = expectedCompletions();
        CompletionRecorder recorder = new CompletionRecorder(expected.size());
        IoHandlerFactory factory = executor -> new RecordingIoUringIoHandler(
                IoUringIoHandler.newFactory().newHandler(executor), recorder);
        MultiThreadIoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, factory);
        DomainSocketAddress address = IoUringSocketTestPermutation.newDomainSocketAddress();
        Channel server = null;
        Channel client = null;
        try {
            server = new ServerBootstrap()
                    .group(group)
                    .channel(IoUringServerDomainSocketChannel.class)
                    .childOption(ChannelOption.RECVBUF_ALLOCATOR,
                            new FixedRecvByteBufAllocator(1).maxMessagesPerRead(DATA_LENGTH + 1))
                    .childHandler(new SimpleChannelInboundHandler<ByteBuf>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                            // Discard.
                        }

                        @Override
                        public void channelReadComplete(ChannelHandlerContext ctx) {
                            recorder.readComplete();
                        }
                    })
                    .bind(address)
                    .sync()
                    .channel();

            client = new Bootstrap()
                    .group(group)
                    .channel(IoUringDomainSocketChannel.class)
                    .handler(new ChannelInboundHandlerAdapter())
                    .connect(address)
                    .sync()
                    .channel();

            client.writeAndFlush(client.alloc().buffer(DATA_LENGTH).writeZero(DATA_LENGTH)).sync();
            recorder.await();

            assertEquals(expected, recorder.completions);
            assertEquals(1, recorder.readCompleteCount);
        } finally {
            if (client != null) {
                client.close().syncUninterruptibly();
            }
            if (server != null) {
                server.close().syncUninterruptibly();
            }
            group.shutdownGracefully().syncUninterruptibly();
        }
    }

    private static List<Completion> expectedCompletions() {
        List<Completion> expected = new ArrayList<Completion>();
        expected.add(completion(Native.IORING_OP_POLL_ADD, Native.POLLIN));
        for (int i = 0; i < DATA_LENGTH; ++i) {
            expected.add(completion(Native.IORING_OP_RECV, 1));
        }
        if (!IoUring.isCqeFSockNonEmptySupported() || !IoUring.isUnixDomainSocketInqSupported()) {
            expected.add(completion(Native.IORING_OP_RECV, ERRNO_EAGAIN_NEGATIVE));
        }
        return expected;
    }

    private static Completion completion(byte opcode, int res) {
        return new Completion(opcode, res);
    }

    private static final class Completion {
        private final byte opcode;
        private final int res;

        Completion(byte opcode, int res) {
            this.opcode = opcode;
            this.res = res;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Completion)) {
                return false;
            }
            Completion other = (Completion) obj;
            return opcode == other.opcode && res == other.res;
        }

        @Override
        public int hashCode() {
            return 31 * opcode + res;
        }

        @Override
        public String toString() {
            return Native.opToStr(opcode) + '(' + res + ')';
        }
    }

    private static final class CompletionRecorder {
        private final Promise<Void> done = ImmediateEventExecutor.INSTANCE.newPromise();
        private final List<Completion> completions = new ArrayList<Completion>();
        private final int expectedCompletions;
        private int readCompleteCount;

        CompletionRecorder(int expectedCompletions) {
            this.expectedCompletions = expectedCompletions;
        }

        void record(IoUringIoEvent event) {
            byte opcode = event.opcode();
            if (opcode == Native.IORING_OP_POLL_ADD || opcode == Native.IORING_OP_RECV) {
                completions.add(completion(opcode, event.res()));
            }
        }

        void handled() {
            if (completions.size() >= expectedCompletions && readCompleteCount > 0) {
                done.trySuccess(null);
            }
        }

        void readComplete() {
            readCompleteCount++;
        }

        void await() throws InterruptedException {
            done.sync();
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
                    recorder.handled();
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
