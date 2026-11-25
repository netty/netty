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
import io.netty.buffer.Unpooled;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.unix.Errors;
import io.netty.util.NetUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntPredicate;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The polls of a channel must each be cancelled once it closes: a poll that is still armed keeps the socket open
 * after its fd is closed, so the peer never sees the FIN, and the event loop cannot terminate.
 */
public class IoUringPollCancellationTest {

    // The ioState bits of AbstractIoUringChannel.
    private static final int POLL_IN_SCHEDULED = 1;
    private static final int POLL_OUT_SCHEDULED = 1 << 2;
    private static final int POLL_RDHUP_SCHEDULED = 1 << 3;
    private static final int READ_SCHEDULED = 1 << 5;
    // From poll.h, the same on all Linux architectures. Native has no constants for them.
    private static final int POLLERR = 0x008;
    private static final int POLLHUP = 0x010;

    private MultiThreadIoEventLoopGroup group;
    private ServerSocket server;

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @BeforeEach
    public void setUp() throws IOException {
        group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        server = new ServerSocket(0, 1, NetUtil.LOCALHOST);
        server.setSoTimeout(10_000);
    }

    @AfterEach
    public void tearDown() throws Exception {
        server.close();
        assertThat(group.shutdownGracefully(0, 1, SECONDS).await(10, SECONDS))
                .as("the event loop terminated").isTrue();
    }

    // A listener of the connect future runs before channelActive, whose auto-read then calls read() on a channel
    // that is closing: the write in flight delayed the close, which has already cancelled the channel's polls.
    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void closeFromConnectListenerWithWriteInFlightClosesTheSocket() throws Exception {
        Channel channel = newClient(new ChannelInboundHandler() { }, true);
        // Connect on the event loop, so that the listener is added before the connect can complete.
        channel.executor().submit(() -> channel.connect(server.getLocalSocketAddress()).addListener(f -> {
            channel.writeAndFlush(Unpooled.wrappedBuffer(new byte[] {1, 2, 3}));
            channel.close();
        })).sync();

        try (Socket accepted = server.accept()) {
            assertEof(accepted);
        }
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void readDuringDelayedCloseArmsNoPoll() throws Exception {
        AtomicBoolean pollInArmed = new AtomicBoolean(true);
        Channel channel = newClient(new ChannelInboundHandler() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                // Fired after the connect listener closed the channel.
                ctx.fireChannelActive();
                ctx.read();
                pollInArmed.set((ioState(ctx.channel()) & POLL_IN_SCHEDULED) != 0);
            }
        }, true);
        channel.executor().submit(() -> channel.connect(server.getLocalSocketAddress()).addListener(f -> {
            channel.writeAndFlush(Unpooled.wrappedBuffer(new byte[] {1}));
            channel.close();
        })).sync();

        try (Socket accepted = server.accept()) {
            assertThat(channel.closeFuture().await(10, SECONDS)).isTrue();
            assertThat(pollInArmed.get()).as("a POLLIN poll armed on a channel whose close was pending").isFalse();
            assertEof(accepted);
        }
    }

    // The channel arms POLLOUT for a connect in progress or after a short write, and POLLRDHUP once it is active.
    // Those paths cannot be timed against a pending close, so the polls are scheduled directly here, in the same task
    // as the close, before any completion is handled.
    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void pollsScheduledDuringDelayedCloseAreNotArmed() throws Exception {
        Channel channel = connect(false);
        try (Socket accepted = server.accept()) {
            awaitArmed(channel, POLL_RDHUP_SCHEDULED);
            // The POLLRDHUP poll keeps its bit until its completion, and only one can be armed at a time.
            cancel(channel, "pollRdhupId", POLL_RDHUP_SCHEDULED);

            int state = onEventLoop(channel, () -> {
                // The write in flight delays the close.
                channel.writeAndFlush(Unpooled.wrappedBuffer(new byte[] {1}));
                channel.close();
                assertThat(field("delayedClose").get(channel)).as("the pending close").isNotNull();
                schedulePollOut(channel);
                ((AbstractIoUringChannel) channel).schedulePollRdHup();
                return ioState(channel);
            });
            assertThat(state & (POLL_OUT_SCHEDULED | POLL_RDHUP_SCHEDULED)).as("polls armed").isZero();

            assertThat(channel.closeFuture().await(10, SECONDS)).isTrue();
            assertEof(accepted);
        }
    }

    // The -ECANCELED result of one poll was read as a mask of ready events, which matches POLLIN and POLLRDHUP. It
    // completed those polls as well, and they then were never cancelled.
    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void cancellingThePollRdHupLeavesThePollInArmed() throws Exception {
        Channel channel = connect(true);
        try (Socket accepted = server.accept()) {
            awaitArmed(channel, POLL_IN_SCHEDULED | POLL_RDHUP_SCHEDULED);
            cancel(channel, "pollRdhupId", POLL_RDHUP_SCHEDULED);
            assertArmed(channel, POLL_IN_SCHEDULED);

            assertThat(channel.close().await(10, SECONDS)).isTrue();
            assertEof(accepted);
        }
    }

    // POLLIN may be multishot, and then only its final completion, without IORING_CQE_F_MORE, completes it.
    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void cancellingThePollInLeavesThePollRdHupArmed() throws Exception {
        Channel channel = connect(true);
        try (Socket accepted = server.accept()) {
            awaitArmed(channel, POLL_IN_SCHEDULED | POLL_RDHUP_SCHEDULED);
            cancel(channel, "pollInId", POLL_IN_SCHEDULED);
            assertArmed(channel, POLL_RDHUP_SCHEDULED);

            assertThat(channel.close().await(10, SECONDS)).isTrue();
            assertEof(accepted);
        }
    }

    // -ECANCELED has no POLLOUT bit, so the cancelled POLLOUT poll itself was never completed.
    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void cancellingThePollOutLeavesTheOtherPollsArmed() throws Exception {
        Channel channel = connectWithSmallBuffers();
        try (Socket accepted = server.accept()) {
            awaitArmed(channel, POLL_IN_SCHEDULED | POLL_RDHUP_SCHEDULED);
            armPollOutOnFullSocket(channel, accepted);

            cancel(channel, "pollOutId", POLL_OUT_SCHEDULED);
            assertArmed(channel, POLL_IN_SCHEDULED | POLL_RDHUP_SCHEDULED);

            assertThat(channel.close().await(10, SECONDS)).isTrue();
            assertEof(accepted);
        }
    }

    // The kernel reports POLLRDHUP with any poll. A POLLIN poll reporting it does not complete the POLLRDHUP poll,
    // which stays armed until its own completion. The POLLIN poll's completion is made up here: a real one would come
    // with the POLLRDHUP poll's completion, which hides the bookkeeping in between.
    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void pollInReportingPollRdHupLeavesThePollRdHupArmed() throws Exception {
        Channel channel = connect(true);
        try (Socket accepted = server.accept()) {
            awaitArmed(channel, POLL_IN_SCHEDULED | POLL_RDHUP_SCHEDULED);
            onEventLoop(channel, () -> {
                // A completion that keeps the POLLIN poll armed, as one of a multishot poll does.
                complete(channel, field("pollInId").getLong(channel), Native.POLLIN | Native.POLLRDHUP,
                        Native.IORING_CQE_F_MORE);
                return null;
            });
            assertArmed(channel, POLL_RDHUP_SCHEDULED);

            assertThat(channel.close().await(10, SECONDS)).isTrue();
            assertEof(accepted);
        }
    }

    // A poll can end without its own event, for example with POLLERR|POLLHUP only. That completion still ends it,
    // and its bookkeeping must be cleared, while the other polls stay armed. Such a final completion is made up here,
    // so the real poll is cancelled afterwards, to leave nothing pending in the kernel.
    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void pollInEndingWithoutPollInLeavesThePollRdHupArmed() throws Exception {
        Channel channel = connect(false);
        try (Socket accepted = server.accept()) {
            awaitArmed(channel, POLL_RDHUP_SCHEDULED);
            long pollInId = onEventLoop(channel, () -> {
                channel.read();
                return field("pollInId").getLong(channel);
            });
            assertArmed(channel, POLL_IN_SCHEDULED | POLL_RDHUP_SCHEDULED);

            int state = onEventLoop(channel, () -> {
                complete(channel, pollInId, POLLERR | POLLHUP, 0);
                return ioState(channel);
            });
            assertThat(state & (POLL_IN_SCHEDULED | POLL_RDHUP_SCHEDULED)).as("polls armed")
                    .isEqualTo(POLL_RDHUP_SCHEDULED);

            submitCancel(channel, pollInId);
            assertThat(channel.close().await(10, SECONDS)).isTrue();
            assertEof(accepted);
        }
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void pollOutEndingWithoutPollOutLeavesTheOtherPollsArmed() throws Exception {
        Channel channel = connectWithSmallBuffers();
        try (Socket accepted = server.accept()) {
            awaitArmed(channel, POLL_IN_SCHEDULED | POLL_RDHUP_SCHEDULED);
            long pollOutId = armPollOutOnFullSocket(channel, accepted);

            int state = onEventLoop(channel, () -> {
                complete(channel, pollOutId, POLLERR | POLLHUP, 0);
                return ioState(channel);
            });
            assertThat(state & (POLL_IN_SCHEDULED | POLL_OUT_SCHEDULED | POLL_RDHUP_SCHEDULED)).as("polls armed")
                    .isEqualTo(POLL_IN_SCHEDULED | POLL_RDHUP_SCHEDULED);

            submitCancel(channel, pollOutId);
            assertThat(channel.close().await(10, SECONDS)).isTrue();
            assertEof(accepted);
        }
    }

    // Only a positive result of the POLLRDHUP poll is a mask of events. Any error ends the poll, but does not mean that
    // the peer shut down its output. The completion is made up, so the real poll is cancelled afterwards.
    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void pollRdHupEndingWithAnErrorIsNoHalfClose() throws Exception {
        Channel channel = connect(false);
        try (Socket accepted = server.accept()) {
            awaitArmed(channel, POLL_RDHUP_SCHEDULED);
            long pollRdhupId = onEventLoop(channel, () -> field("pollRdhupId").getLong(channel));

            int state = onEventLoop(channel, () -> {
                complete(channel, pollRdhupId, Errors.ERRNO_EBADF_NEGATIVE, 0);
                return ioState(channel);
            });
            assertThat(state & (POLL_RDHUP_SCHEDULED | READ_SCHEDULED)).as("POLLRDHUP armed or read scheduled")
                    .isZero();
            assertThat(onEventLoop(channel, () -> rdHupReceived(channel))).as("POLLRDHUP received").isFalse();

            submitCancel(channel, pollRdhupId);
            assertThat(channel.close().await(10, SECONDS)).isTrue();
            assertEof(accepted);
        }
    }

    private Channel newClient(ChannelInboundHandler handler, boolean autoRead) throws InterruptedException {
        return new Bootstrap()
                .group(group)
                .channel(IoUringSocketChannel.class)
                .handler(handler)
                .option(ChannelOption.AUTO_READ, autoRead)
                .remoteAddress(new InetSocketAddress(NetUtil.LOCALHOST, server.getLocalPort()))
                .register().sync().getNow();
    }

    private Channel connect(boolean autoRead) throws InterruptedException {
        Channel channel = newClient(new ChannelInboundHandler() { }, autoRead);
        channel.connect(server.getLocalSocketAddress()).sync();
        return channel;
    }

    private Channel connectWithSmallBuffers() throws Exception {
        // Small buffers on both ends, so that the socket fills long before a write of 8 MiB ends.
        server.setReceiveBufferSize(4096);
        return new Bootstrap()
                .group(group)
                .channel(IoUringSocketChannel.class)
                .handler(new ChannelInboundHandler() { })
                .option(ChannelOption.SO_SNDBUF, 4096)
                .connect(server.getLocalSocketAddress()).sync().getNow();
    }

    /**
     * Fills the socket with a write that the peer does not read, arms POLLOUT and returns its id. The channel arms
     * POLLOUT itself only after a short write, which io_uring does not produce here: it keeps a send to a full socket
     * pending.
     */
    private static long armPollOutOnFullSocket(Channel channel, Socket accepted) throws Exception {
        channel.writeAndFlush(Unpooled.wrappedBuffer(new byte[8 * 1024 * 1024]));
        // The first byte at the peer shows that the kernel has started on the write, and the peer reads no more. That
        // the socket then has no room left is an assumption: if it had, the POLLOUT poll would complete at once
        // rather than stay armed for what the test does with it.
        accepted.setSoTimeout(10_000);
        assertThat(accepted.getInputStream().read()).isNotEqualTo(-1);
        long pollOutId = onEventLoop(channel, () -> {
            schedulePollOut(channel);
            return field("pollOutId").getLong(channel);
        });
        assertArmed(channel, POLL_OUT_SCHEDULED);
        return pollOutId;
    }

    /**
     * Must be called on the event loop.
     */
    private static void schedulePollOut(Channel channel) throws ReflectiveOperationException {
        Method schedulePollOut = AbstractIoUringChannel.class.getDeclaredMethod("schedulePollOut");
        schedulePollOut.setAccessible(true);
        schedulePollOut.invoke(channel);
    }

    /**
     * Cancels one poll, as cancelOps(...) would, and waits until the channel has handled its completion.
     */
    private static void cancel(Channel channel, String idField, int ioStateBit) throws Exception {
        long id = onEventLoop(channel, () -> field(idField).getLong(channel));
        assertThat(id).as(idField).isNotZero();
        submitCancel(channel, id);
        awaitState(channel, s -> (s & ioStateBit) == 0, "the cancelled poll's completion handled");
    }

    private static void submitCancel(Channel channel, long pollId) throws Exception {
        onEventLoop(channel, () -> ((AbstractIoUringChannel) channel).registration().submit(
                IoUringIoOps.newAsyncCancel((byte) 0, pollId, Native.IORING_OP_POLL_ADD)));
    }

    /**
     * Hands the channel a made-up completion of one of its polls. Must be called on the event loop.
     */
    private static void complete(Channel channel, long pollId, int res, int flags)
            throws ReflectiveOperationException {
        // The data of the user_data, its low 16 bits, tells which poll of the channel the completion is of.
        ((IoUringIoHandle) field("ioHandle").get(channel)).handle(
                ((AbstractIoUringChannel) channel).registration(),
                new IoUringIoEvent(res, flags, Native.IORING_OP_POLL_ADD, (long) (short) pollId));
    }

    private static void awaitArmed(Channel channel, int ioStateBits) throws Exception {
        awaitState(channel, s -> (s & ioStateBits) == ioStateBits, "polls armed: " + ioStateBits);
    }

    private static void assertArmed(Channel channel, int ioStateBits) throws Exception {
        assertThat(onEventLoop(channel, () -> ioState(channel)) & ioStateBits).as("polls armed")
                .isEqualTo(ioStateBits);
    }

    // The channel has no hook for the completions it handles, so its state is checked again on the event loop
    // until it matches or the deadline passes.
    private static void awaitState(Channel channel, IntPredicate condition, String expected) throws Exception {
        long deadline = System.nanoTime() + SECONDS.toNanos(10);
        while (!condition.test(onEventLoop(channel, () -> ioState(channel)))) {
            if (System.nanoTime() - deadline > 0) {
                fail("timed out waiting for: " + expected);
            }
        }
    }

    private static <T> T onEventLoop(Channel channel, Callable<T> task) throws Exception {
        return channel.executor().submit(task).get(10, SECONDS);
    }

    private static void assertEof(Socket socket) throws IOException {
        socket.setSoTimeout(10_000);
        InputStream in = socket.getInputStream();
        byte[] buffer = new byte[65536];
        // Whatever of the data arrived; a SocketTimeoutException means no FIN came.
        while (in.read(buffer) != -1) {
            continue;
        }
    }

    private static int ioState(Channel channel) throws ReflectiveOperationException {
        return field("ioState").getByte(channel) & 0xff;
    }

    private static boolean rdHupReceived(Channel channel) throws ReflectiveOperationException {
        Method recvBufAllocHandle = AbstractChannel.class.getDeclaredMethod("recvBufAllocHandle");
        recvBufAllocHandle.setAccessible(true);
        Field field = IoUringRecvByteAllocatorHandle.class.getDeclaredField("rdHupReceived");
        field.setAccessible(true);
        return field.getBoolean(recvBufAllocHandle.invoke(channel));
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = AbstractIoUringChannel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
