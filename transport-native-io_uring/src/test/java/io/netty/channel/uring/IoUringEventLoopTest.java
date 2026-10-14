/*
 * Copyright 2024 The Netty Project
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

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.IoEvent;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoRegistration;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.channel.unix.FileDescriptor;
import io.netty.testsuite.transport.AbstractSingleThreadEventLoopTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringEventLoopTest extends AbstractSingleThreadEventLoopTest {

    public static final Runnable EMPTY_RUNNABLE = () -> {
    };

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    protected IoHandlerFactory newIoHandlerFactory() {
        return IoUringIoHandler.newFactory();
    }

    @Override
    protected EventLoopGroup newEventLoopGroup() {
        return new MultiThreadIoEventLoopGroup(1, newIoHandlerFactory());
    }

    @Override
    protected EventLoopGroup newAutoScalingEventLoopGroup() {
        return new MultiThreadIoEventLoopGroup(SCALING_MAX_THREADS, (Executor) null, AUTO_SCALING_CHOOSER_FACTORY,
                IoUringIoHandler.newFactory(new IoUringIoHandlerConfig().setSingleIssuer(false)));
    }

    @Override
    protected Channel newChannel() {
        return new IoUringServerSocketChannel();
    }

    @Override
    protected Class<? extends ServerChannel> serverChannelClass() {
        return IoUringServerSocketChannel.class;
    }

    @Test
    public void testSubmitMultipleTasksAndEnsureTheseAreExecuted() throws Exception {
        EventLoopGroup group =  new MultiThreadIoEventLoopGroup(1, newIoHandlerFactory());
        try {
            EventLoop loop = group.next();
            loop.submit(EMPTY_RUNNABLE).sync();
            loop.submit(EMPTY_RUNNABLE).sync();
            loop.submit(EMPTY_RUNNABLE).sync();
            loop.submit(EMPTY_RUNNABLE).sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    @RepeatedTest(100)
    public void shutdownNotSoGracefully() throws Exception {
        EventLoopGroup group =  new MultiThreadIoEventLoopGroup(1, newIoHandlerFactory());
        CountDownLatch latch = new CountDownLatch(1);
        group.submit(() -> latch.countDown());
        latch.await(5, TimeUnit.SECONDS);
        assertTrue(group.shutdownGracefully(0L, 0L, TimeUnit.NANOSECONDS)
                .await(1500L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void shutsDownGracefully() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, newIoHandlerFactory());
        assertTrue(group.shutdownGracefully(1L, 1L, TimeUnit.MILLISECONDS)
                .await(1500L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSchedule() throws Exception {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        try {
            EventLoop loop = group.next();
            loop.schedule(EMPTY_RUNNABLE, 1, TimeUnit.SECONDS).sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void testCancelledRegistrationWithOutstandingCompletionBeforeSuspend() throws Exception {
        PollHandle handle = new PollHandle();
        Semaphore loopThreadExited = new Semaphore(0);
        AtomicBoolean unregisteredBeforeExit = new AtomicBoolean();
        Executor executor = task -> new Thread(() -> {
            try {
                task.run();
            } finally {
                unregisteredBeforeExit.set(handle.unregistered.getCount() == 0);
                loopThreadExited.release();
            }
        }).start();
        // The loop thread changes when the loop is resumed, so we can't use a single issuer ring.
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, executor,
                IoUringIoHandler.newFactory(new IoUringIoHandlerConfig().setSingleIssuer(false)));
        FileDescriptor eventFd = Native.newBlockingEventFd();
        try {
            SingleThreadIoEventLoop loop = (SingleThreadIoEventLoop) group.next();
            IoRegistration registration = loop.register(handle).syncUninterruptibly().getNow();
            // A poll on the eventfd only completes once we write to it, so the registration has an outstanding
            // completion until then.
            registration.submit(IoUringIoOps.newPollAdd(eventFd.intValue(), (byte) 0, Native.POLLIN, 0, (short) 1));

            // The loop will only suspend once the handle is not registered anymore.
            assertTrue(loop.trySuspend());
            assertTrue(loop.submit(registration::cancel).syncUninterruptibly().getNow());

            // The registration can only be removed once the poll completed, so the loop must not suspend before.
            assertFalse(loopThreadExited.tryAcquire(500, TimeUnit.MILLISECONDS));
            assertFalse(handle.unregistered.await(0, TimeUnit.MILLISECONDS));

            Native.eventFdWrite(eventFd.intValue(), 1L);
            assertTrue(handle.completed.await(5, TimeUnit.SECONDS));
            assertTrue(handle.unregistered.await(5, TimeUnit.SECONDS));
            loopThreadExited.acquire();
            assertTrue(loop.isSuspended());
            assertTrue(unregisteredBeforeExit.get());
        } finally {
            // Complete the poll in any case, as otherwise the shutdown would wait for it forever.
            Native.eventFdWrite(eventFd.intValue(), 1L);
            group.shutdownGracefully().syncUninterruptibly();
            eventFd.close();
        }
    }

    private static final class PollHandle implements IoUringIoHandle {
        final CountDownLatch completed = new CountDownLatch(1);
        final CountDownLatch unregistered = new CountDownLatch(1);

        @Override
        public void handle(IoRegistration registration, IoEvent ioEvent) {
            completed.countDown();
        }

        @Override
        public void unregistered() {
            unregistered.countDown();
        }

        @Override
        public void close() {
            // Noop
        }
    }
}
