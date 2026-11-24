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
package io.netty.channel.nio;

import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoRegistration;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.ThreadAwareExecutor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NioIoHandlerTest {

    @Test
    void testNioHandlerCanBeDrivenByMainThread() throws Exception {
        IoHandlerFactory factory = NioIoHandler.newFactory();
        final Thread current = Thread.currentThread();
        final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        // Create our own IoExecutor that is driven by the main thread
        final ThreadAwareExecutor executor = new ThreadAwareExecutor() {
            @Override
            public boolean isExecutorThread(Thread thread) {
                return current == thread;
            }

            @Override
            public void execute(Runnable command) {
                tasks.add(command);
            }
        };
        IoHandlerContext context = new IoHandlerContext() {
            @Override
            public boolean canBlock() {
                // Just busy spin.
                return false;
            }

            @Override
            public long delayNanos(long currentTimeNanos) {
                return 0;
            }

            @Override
            public long deadlineNanos() {
                return -1;
            }
        };
        IoHandler handler = factory.newHandler(executor);

        // Open a ServerSocketChannel that we can connect to.
        ServerSocketChannel channel = SelectorProvider.provider().openServerSocketChannel();
        channel.configureBlocking(false);
        channel.bind(new InetSocketAddress(NetUtil.LOCALHOST, 0));
        // Do the connect in another thread so we don't block our io execution loop.
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket = new Socket();
                try {
                    socket.connect(channel.getLocalAddress());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        AtomicBoolean acceptedConnection = new AtomicBoolean();
        IoRegistration registration = handler.register(new NioSelectableChannelIoHandle<ServerSocketChannel>(channel) {
            @Override
            protected void handle(ServerSocketChannel channel, SelectionKey key) {
                if (key.isAcceptable()) {
                    try {
                        // Just accept the connection and close it.
                        java.nio.channels.SocketChannel accepted = channel.accept();
                        accepted.close();
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        acceptedConnection.set(true);
                    }
                }
            }
        });
        registration.submit(NioIoOps.ACCEPT);
        t.start();

        // Let's loop until our registration was cancelled.
        while (registration.isValid()) {
            handler.run(context);
            for (;;) {
                // Execute all tasks that were dispatched.
                Runnable r = tasks.poll();
                if (r == null) {
                    break;
                }
                r.run();
            }
            if (acceptedConnection.get()) {
                // We accepted the connection, let's cancel the registration.
                assertTrue(registration.cancel());
            }
        }
        // Wait until the connect thread is done.
        t.join();

        // Let's now close the ServerSocketChannel and after that destroy the IoHandler.
        channel.close();
        handler.prepareToDestroy();
        handler.destroy();
    }

    @Test
    void testMillisBeforeDeadlineRoundsUp() {
        long now = 1_000_000_000L;
        // The timeout is only 0 if the deadline is within 5 microseconds, or already reached.
        assertEquals(0, NioIoHandler.millisBeforeDeadline(now - 1, now));
        assertEquals(0, NioIoHandler.millisBeforeDeadline(now, now));
        assertEquals(0, NioIoHandler.millisBeforeDeadline(now + 1, now));
        assertEquals(0, NioIoHandler.millisBeforeDeadline(now + 4_999, now));
        // Everything from 5 microseconds up to 1 ms is rounded up to 1 ms, also less than 0.5 ms.
        // After that the timeout grows by 1 ms every ms, 5 microseconds early.
        assertEquals(1, NioIoHandler.millisBeforeDeadline(now + 5_000, now));
        assertEquals(1, NioIoHandler.millisBeforeDeadline(now + 100_000, now));
        assertEquals(1, NioIoHandler.millisBeforeDeadline(now + 499_999, now));
        assertEquals(1, NioIoHandler.millisBeforeDeadline(now + 500_000, now));
        assertEquals(1, NioIoHandler.millisBeforeDeadline(now + 999_999, now));
        assertEquals(1, NioIoHandler.millisBeforeDeadline(now + 1_000_000, now));
        assertEquals(1, NioIoHandler.millisBeforeDeadline(now + 1_004_999, now));
        assertEquals(2, NioIoHandler.millisBeforeDeadline(now + 1_005_000, now));
        assertEquals(2, NioIoHandler.millisBeforeDeadline(now + 2_004_999, now));
        assertEquals(3, NioIoHandler.millisBeforeDeadline(now + 2_005_000, now));
        assertEquals(1_000, NioIoHandler.millisBeforeDeadline(now + TimeUnit.SECONDS.toNanos(1), now));
    }

    @Test
    void testMillisBeforeDeadlineDoesNotOverflow() {
        long maxMillis = Long.MAX_VALUE / 1_000_000L;
        // Far deadlines are capped, they must never look like a deadline that is already reached.
        assertEquals(maxMillis, NioIoHandler.millisBeforeDeadline(Long.MAX_VALUE - 1, 0));
        assertEquals(maxMillis, NioIoHandler.millisBeforeDeadline(Long.MAX_VALUE - 995_000L, 0));
        assertEquals(maxMillis, NioIoHandler.millisBeforeDeadline(Long.MAX_VALUE - 995_001L, 0));
        assertEquals(maxMillis, NioIoHandler.millisBeforeDeadline(Long.MAX_VALUE - 1_000_000L, 0));
        assertTrue(NioIoHandler.millisBeforeDeadline(Long.MAX_VALUE - 10_000_000L, 0) > 0);
    }

    @Test
    void testNoBusySpinIfDeadlineIsLessThanHalfAMillisecondAway() throws Exception {
        final long[] deadline = new long[1];
        final ThreadAwareExecutor executor = new ThreadAwareExecutor() {
            @Override
            public boolean isExecutorThread(Thread thread) {
                return true;
            }

            @Override
            public void execute(Runnable command) {
                throw new UnsupportedOperationException();
            }
        };
        IoHandlerContext context = new IoHandlerContext() {
            @Override
            public boolean canBlock() {
                return true;
            }

            @Override
            public long delayNanos(long currentTimeNanos) {
                return Math.max(0, deadline[0] - currentTimeNanos);
            }

            @Override
            public long deadlineNanos() {
                return deadline[0];
            }
        };
        IoHandler handler = NioIoHandler.newFactory().newHandler(executor);
        try {
            deadline[0] = System.nanoTime() + TimeUnit.MICROSECONDS.toNanos(300);
            // An event loop calls run(...) again as long as the scheduled task is not due. If run(...) returns
            // without sleeping, as it did when the timeout was rounded to the nearest millisecond, this loops
            // thousands of times until the deadline is reached.
            int iterations = 0;
            while (System.nanoTime() < deadline[0]) {
                handler.run(context);
                iterations++;
            }
            assertTrue(iterations <= 3, "run(...) returned " + iterations + " times before the deadline");
        } finally {
            handler.prepareToDestroy();
            handler.destroy();
        }
    }
}
