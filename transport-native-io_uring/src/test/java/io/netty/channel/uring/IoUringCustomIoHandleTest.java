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

import io.netty.channel.IoEvent;
import io.netty.channel.IoEventLoop;
import io.netty.channel.IoEventLoopGroup;
import io.netty.channel.IoRegistration;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Timeout(value = 1, unit = TimeUnit.MINUTES)
public class IoUringCustomIoHandleTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(IoUring.isAvailable());
    }

    @Test
    public void testLongUserDataCompletionPreservesSubmittedUserData() throws Exception {
        IoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        try {
            TestHandle handle = new TestHandle();
            IoRegistration registration = register(group, handle);

            long firstSubmittedId = registration.submit(nop(70_000L));
            assertNotEquals(0L, firstSubmittedId);
            assertEquals(70_000L, handle.awaitUserData());

            long secondSubmittedId = registration.submit(nop(80_000L));
            assertNotEquals(firstSubmittedId, secondSubmittedId);
            assertEquals(80_000L, handle.awaitUserData());

            assertTrue(registration.cancel());
        } finally {
            shutdown(group);
        }
    }

    @Test
    public void testShortUserDataUsesFastPath() throws Exception {
        IoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        try {
            TestHandle handle = new TestHandle();
            IoRegistration registration = register(group, handle);

            long firstSubmittedId = registration.submit(nop(123L));
            long secondSubmittedId = registration.submit(nop(123L));

            assertNotEquals(0L, firstSubmittedId);
            assertNotEquals(0L, secondSubmittedId);
            assertEquals(123L, handle.awaitUserData());
            assertEquals(123L, handle.awaitUserData());

            assertTrue(registration.cancel());
        } finally {
            shutdown(group);
        }
    }

    @Test
    public void testOffExecutorSubmitWithLongUserData() throws Exception {
        IoEventLoopGroup group = new MultiThreadIoEventLoopGroup(1, IoUringIoHandler.newFactory());
        try {
            TestHandle handle = new TestHandle();
            IoRegistration registration = register(group, handle);

            long[] submittedId = new long[1];
            Throwable[] submitFailure = new Throwable[1];
            Thread submitter = new Thread(() -> {
                try {
                    submittedId[0] = registration.submit(nop(90_000L));
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
            assertEquals(90_000L, handle.awaitUserData());

            assertTrue(registration.cancel());
        } finally {
            shutdown(group);
        }
    }

    private static IoRegistration register(IoEventLoopGroup group, IoUringIoHandle handle) {
        IoEventLoop loop = group.next();
        return loop.register(handle).syncUninterruptibly().getNow();
    }

    private static void shutdown(IoEventLoopGroup group) {
        group.shutdownGracefully().syncUninterruptibly();
    }

    private static IoUringIoOps nop(long userData) {
        return new IoUringIoOps(Native.IORING_OP_NOP, (byte) 0,
                (short) 0, -1, 0, 0, 0, 0, userData, (short) 0, (short) 0, 0, 0);
    }

    private static final class TestHandle implements IoUringIoHandle {
        private final BlockingQueue<Long> completions = new LinkedBlockingQueue<>();

        @Override
        public void handle(IoRegistration registration, IoEvent ioEvent) {
            IoUringIoEvent event = (IoUringIoEvent) ioEvent;
            assertEquals(Native.IORING_OP_NOP, event.opcode());
            assertTrue(event.res() >= 0);
            completions.add(event.userData());
        }

        long awaitUserData() throws InterruptedException {
            Long userData = completions.poll(10, TimeUnit.SECONDS);
            assertNotNull(userData);
            return userData;
        }

        @Override
        public void close() {
            // Noop
        }
    }
}
