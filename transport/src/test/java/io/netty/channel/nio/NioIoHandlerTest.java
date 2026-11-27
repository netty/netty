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

import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoRegistration;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.ThreadAwareExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testWakeupWhileAwakeIsRememberedWithoutWakingSelector(boolean canBlock) throws Exception {
        CountingSelector selector = new CountingSelector();
        IoHandler handler = newHandler(selector);
        try {
            runFromAnotherThread(handler::wakeup);
            handler.run(context(canBlock));

            assertThat(selector.polls).isEqualTo(1);
            assertThat(selector.selects).isZero();
            assertThat(selector.wakeups.get()).isZero();

            // The pending wakeup was consumed, so the next iteration can block again.
            handler.run(context(true));
            assertThat(selector.selects).isEqualTo(1);
        } finally {
            handler.destroy();
        }
    }

    @Test
    void testWakeupDuringSelectionIsCoalesced() throws Exception {
        CountingSelector selector = new CountingSelector();
        IoHandler handler = newHandler(selector);
        selector.onSelect = () -> runFromAnotherThread(() -> {
            handler.wakeup();
            handler.wakeup();
        });
        try {
            handler.run(context(true));

            assertThat(selector.selects).isEqualTo(1);
            assertThat(selector.wakeups.get()).isEqualTo(1);
        } finally {
            handler.destroy();
        }
    }

    @Test
    void testWakeupRemainsPendingWhenSelectorIsRebuilt() throws Exception {
        CountingSelector oldSelector = new CountingSelector();
        CountingSelector newSelector = new CountingSelector();
        NioIoHandler handler = newHandler(oldSelector, newSelector);
        try {
            runFromAnotherThread(handler::wakeup);
            handler.rebuildSelector0();
            handler.run(context(true));

            assertThat(oldSelector.isOpen()).isFalse();
            assertThat(oldSelector.wakeups.get()).isZero();
            assertThat(newSelector.polls).isEqualTo(1);
            assertThat(newSelector.wakeups.get()).isZero();
            assertThat(newSelector.selects).isZero();

            handler.run(context(true));
            assertThat(newSelector.selects).isEqualTo(1);
        } finally {
            handler.destroy();
        }
    }

    private static NioIoHandler newHandler(AbstractSelector selector, AbstractSelector... replacements)
            throws IOException {
        // Use a deterministic selector without implementing unrelated provider methods.
        SelectorProvider provider = mock(SelectorProvider.class);
        when(provider.openSelector()).thenReturn(selector, replacements);
        Thread owner = Thread.currentThread();
        ThreadAwareExecutor executor = new ThreadAwareExecutor() {
            @Override
            public boolean isExecutorThread(Thread thread) {
                return thread == owner;
            }

            @Override
            public void execute(Runnable command) {
                throw new UnsupportedOperationException();
            }
        };
        return (NioIoHandler) NioIoHandler.newFactory(provider).newHandler(executor);
    }

    private static IoHandlerContext context(boolean canBlock) {
        return new IoHandlerContext() {
            @Override
            public boolean canBlock() {
                return canBlock;
            }

            @Override
            public long delayNanos(long currentTimeNanos) {
                return Long.MAX_VALUE;
            }

            @Override
            public long deadlineNanos() {
                return -1;
            }
        };
    }

    private static void runFromAnotherThread(Runnable task) {
        assertThat(CompletableFuture.runAsync(task)).succeedsWithin(5, TimeUnit.SECONDS);
    }

    private static final class CountingSelector extends AbstractSelector {
        private final AtomicInteger wakeups = new AtomicInteger();
        private int polls;
        private int selects;
        private Runnable onSelect;

        CountingSelector() {
            super(SelectorProvider.provider());
        }

        @Override
        public Set<SelectionKey> keys() {
            return Collections.emptySet();
        }

        @Override
        public Set<SelectionKey> selectedKeys() {
            return Collections.emptySet();
        }

        @Override
        public int selectNow() {
            ++polls;
            return 0;
        }

        @Override
        public int select(long timeout) {
            ++selects;
            if (onSelect != null) {
                onSelect.run();
                return 0;
            }
            return 1;
        }

        @Override
        public int select() {
            return select(0);
        }

        @Override
        public Selector wakeup() {
            wakeups.incrementAndGet();
            return this;
        }

        @Override
        protected SelectionKey register(AbstractSelectableChannel channel, int ops, Object attachment) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void implCloseSelector() {
            // No channels are registered.
        }
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
