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

import io.netty.channel.IoEvent;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoRegistration;
import io.netty.util.concurrent.ThreadAwareExecutor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.DisabledIf;

import java.util.concurrent.TimeUnit;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class IoUringIoHandlerTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    public void testOptions() {
        IoUringIoHandlerConfig config = new IoUringIoHandlerConfig();
        config.setMaxBoundedWorker(2)
                .setMaxUnboundedWorker(2)
                .setRingSize(4);
        IoHandlerFactory ioHandlerFactory = IoUringIoHandler.newFactory(config);
        IoHandler handler = ioHandlerFactory.newHandler(new ThreadAwareExecutor() {

            @Override
            public boolean isExecutorThread(Thread thread) {
                return false;
            }

            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });
        handler.initialize();
        handler.prepareToDestroy();
        handler.destroy();
    }

    @Test
    public void testSkipNotSupported() throws Exception {
        IoHandlerFactory ioHandlerFactory = IoUringIoHandler.newFactory();
        IoHandler handler = ioHandlerFactory.newHandler(new ThreadAwareExecutor() {

            @Override
            public boolean isExecutorThread(Thread thread) {
                return true;
            }

            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });
        handler.initialize();
        IoRegistration registration = handler.register(new IoUringIoHandle() {
            @Override
            public void handle(IoRegistration registration, IoEvent ioEvent) {
                fail();
            }

            @Override
            public void close() {
                // Noop
            }
        });
        assertThrows(IllegalArgumentException.class, () ->
                registration.submit(new IoUringIoOps(Native.IORING_OP_NOP, (byte) Native.IOSQE_CQE_SKIP_SUCCESS,
                            (short) 0, -1, 0, 0, 0, 0, (short) 0, (short) 0, (short) 0, 0, 0)));
        assertTrue(registration.cancel());
        assertFalse(registration.isValid());
        handler.prepareToDestroy();
        handler.destroy();
    }

    @Test
    @DisabledIf("setUpCQSizeUnavailable")
    public void testSetCqSizeOptions() {
        IoUringIoHandlerConfig config = new IoUringIoHandlerConfig();
        config.setMaxBoundedWorker(2)
                .setMaxUnboundedWorker(2)
                .setRingSize(4)
                .setCqSize(32);
        IoHandlerFactory ioHandlerFactory = IoUringIoHandler.newFactory(config);
        IoHandler handler = ioHandlerFactory.newHandler(new ThreadAwareExecutor() {

            @Override
            public boolean isExecutorThread(Thread thread) {
                return false;
            }

            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });
        handler.initialize();
        handler.prepareToDestroy();
        handler.destroy();
    }

    @Test
    public void testSubmitAfterDestroy() throws  Exception {
        IoHandlerFactory ioHandlerFactory = IoUringIoHandler.newFactory();
        IoHandler handler = ioHandlerFactory.newHandler(new ThreadAwareExecutor() {

            @Override
            public boolean isExecutorThread(Thread thread) {
                return true;
            }

            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });
        handler.initialize();
        IoRegistration registration = handler.register(new IoUringIoHandle() {
            @Override
            public void handle(IoRegistration registration, IoEvent ioEvent) {
                fail();
            }

            @Override
            public void close() {
                // Noop
            }
        });
        handler.prepareToDestroy();
        handler.destroy();
        assertThrows(IllegalStateException.class,  () ->
                registration.submit(new IoUringIoOps(Native.IORING_OP_NOP, (byte) 0,
                        (short) 0, -1, 0, 0, 0, 0, (short) 0, (short) 0, (short) 0, 0, 0)));
    }

    // See https://github.com/netty/netty/issues/18483
    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void testDestroyWaitsForEventFdRead() throws Exception {
        // Use a tiny ring and only a few iterations: the kernel releases the memory of a closed ring asynchronously, so
        // creating many rings in a row can hit the memlock / memory limits of CI runners (ENOMEM in io_uring_setup).
        IoHandlerFactory factory = IoUringIoHandler.newFactory(4);
        for (int i = 0; i < 10; i++) {
            IoUringIoHandler handler = (IoUringIoHandler) factory.newHandler(
                    new ThreadAwareExecutor() {
                        @Override
                        public boolean isExecutorThread(Thread thread) {
                            return true;
                        }

                        @Override
                        public void execute(Runnable command) {
                            command.run();
                        }
                    });
            handler.initialize();
            handler.register(new IoUringIoHandle() {
                @Override
                public void handle(IoRegistration registration, IoEvent ioEvent) {
                    // Noop
                }

                @Override
                public void close() {
                    // Noop
                }
            });
            // The eventfd read is only submitted from run(), so run once to make sure a read is actually in flight.
            // Without this the test would not exercise the eventfd handling in prepareToDestroy() / destroy() at all.
            runOnceWithoutBlocking(handler);
            assertTrue(handler.isEventFdReadPending());
            // SingleThreadIoEventLoop may call prepareToDestroy() several times in a row before destroy(). Each call
            // writes to the eventfd and re-arms the eventfd read behind an IOSQE_IO_DRAIN request, so the read can
            // end up executing in an io-wq worker that still copies into eventfdReadBuf after destroy() returned.
            handler.prepareToDestroy();
            handler.prepareToDestroy();
            handler.destroy();
            assertFalse(handler.isEventFdReadPending());
        }
    }

    // See https://github.com/netty/netty/issues/18483
    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void testDestroyWaitsForEventFdReadWithPendingWakeup() throws Exception {
        for (int i = 0; i < 100; i++) {
            IoUringIoHandler handler = (IoUringIoHandler) IoUringIoHandler.newFactory().newHandler(
                    new ThreadAwareExecutor() {
                        @Override
                        public boolean isExecutorThread(Thread thread) {
                            // Not the executor thread, so wakeup() writes to the eventfd.
                            return false;
                        }

                        @Override
                        public void execute(Runnable command) {
                            command.run();
                        }
                    });
            handler.initialize();
            runOnceWithoutBlocking(handler);
            assertTrue(handler.isEventFdReadPending());
            // Close the wakeup gate with an event written but its completion not yet processed. destroy() must wait
            // for the read to complete, as the kernel may still be copying into the read buffer.
            handler.wakeup();
            handler.destroy();
            assertFalse(handler.isEventFdReadPending());
        }
    }

    // Runs the handler once so that it submits its eventfd read. The context pretends to be allowed to block but has
    // an already expired deadline, so run() returns immediately and the eventfd read stays pending.
    private static void runOnceWithoutBlocking(IoUringIoHandler handler) {
        handler.run(new IoHandlerContext() {
            @Override
            public boolean canBlock() {
                return true;
            }

            @Override
            public long delayNanos(long currentTimeNanos) {
                return 0;
            }

            @Override
            public long deadlineNanos() {
                return 0;
            }
        });
    }

    private static boolean setUpCQSizeUnavailable() {
        return !IoUring.isSetupCqeSizeSupported();
    }
}
