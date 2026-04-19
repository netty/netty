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
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoRegistration;
import io.netty.channel.ManualIoEventLoop;
import io.netty.util.concurrent.ThreadAwareExecutor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Test
    public void testTokenChangesOnSlotReuseAndCompletionKeepsLongUserData() {
        ManualIoEventLoop loop = new ManualIoEventLoop(Thread.currentThread(), IoUringIoHandler.newFactory());
        loop.runNow();
        final class TestHandle implements IoUringIoHandle {
            private final List<Long> seenUserData = new ArrayList<>();

            @Override
            public void handle(IoRegistration registration, IoEvent ioEvent) {
                IoUringIoEvent event = (IoUringIoEvent) ioEvent;
                assertEquals(Native.IORING_OP_NOP, event.opcode());
                assertTrue(event.res() >= 0);
                seenUserData.add(event.userData());
            }

            @Override
            public void close() {
                // Noop
            }
        }

        TestHandle handle = new TestHandle();
        try {
            IoRegistration registration = loop.register(handle).syncUninterruptibly().getNow();
            long first = registration.submit(new IoUringIoOps(Native.IORING_OP_NOP, (byte) 0,
                    (short) 0, -1, 0, 0, 0, 0, 70_000L, (short) 0, (short) 0, 0, 0));
            runUntilCompletions(loop, handle.seenUserData, 1);

            long second = registration.submit(new IoUringIoOps(Native.IORING_OP_NOP, (byte) 0,
                    (short) 0, -1, 0, 0, 0, 0, 80_000L, (short) 0, (short) 0, 0, 0));
            runUntilCompletions(loop, handle.seenUserData, 2);

            assertNotEquals(first, second);
            assertEquals(Arrays.asList(70_000L, 80_000L), handle.seenUserData);
            assertTrue(registration.cancel());
        } finally {
            loop.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            for (int i = 0; i < 10_000 && !loop.terminationFuture().isDone(); i++) {
                loop.runNow();
            }
            assertTrue(loop.terminationFuture().isDone());
        }
    }

    @Test
    public void testOffExecutorSubmitWithLongUserDataUsesUnifiedSlowPath() throws Exception {
        ManualIoEventLoop loop = new ManualIoEventLoop(Thread.currentThread(), IoUringIoHandler.newFactory());
        loop.runNow();
        final class TestHandle implements IoUringIoHandle {
            private final List<Long> seenUserData = new ArrayList<>();

            @Override
            public void handle(IoRegistration registration, IoEvent ioEvent) {
                IoUringIoEvent event = (IoUringIoEvent) ioEvent;
                assertEquals(Native.IORING_OP_NOP, event.opcode());
                assertTrue(event.res() >= 0);
                seenUserData.add(event.userData());
            }

            @Override
            public void close() {
                // Noop
            }
        }

        TestHandle handle = new TestHandle();
        try {
            IoRegistration registration = loop.register(handle).syncUninterruptibly().getNow();
            long[] submittedId = new long[1];
            Throwable[] submitFailure = new Throwable[1];
            Thread submitter = new Thread(() -> {
                try {
                    submittedId[0] = registration.submit(new IoUringIoOps(Native.IORING_OP_NOP, (byte) 0,
                            (short) 0, -1, 0, 0, 0, 0, 90_000L, (short) 0, (short) 0, 0, 0));
                } catch (Throwable cause) {
                    submitFailure[0] = cause;
                }
            });
            submitter.start();
            submitter.join();

            if (submitFailure[0] != null) {
                throw new AssertionError(submitFailure[0]);
            }
            assertNotEquals(0L, submittedId[0]);
            runUntilCompletions(loop, handle.seenUserData, 1);
            assertEquals(Arrays.asList(90_000L), handle.seenUserData);
            assertTrue(registration.cancel());
        } finally {
            loop.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            for (int i = 0; i < 10_000 && !loop.terminationFuture().isDone(); i++) {
                loop.runNow();
            }
            assertTrue(loop.terminationFuture().isDone());
        }
    }

    private static void runUntilCompletions(ManualIoEventLoop loop, List<Long> seenUserData, int expectedCompletions) {
        for (int i = 0; i < 10_000 && seenUserData.size() < expectedCompletions; i++) {
            loop.runNow();
        }
        assertEquals(expectedCompletions, seenUserData.size());
    }

    private static boolean setUpCQSizeUnavailable() {
        return !IoUring.isSetupCqeSizeSupported();
    }
}
