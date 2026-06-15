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
package io.netty.channel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link IoHandler#run(IoHandlerContext)} returns only the count of real I/O events,
 * excluding internal events such as wakeups and timer expirations.
 * <p>
 * Subclasses provide the {@link IoHandlerFactory} for each transport. The contract tested here
 * must hold regardless of the transport implementation.
 */
public abstract class AbstractIoHandlerEventCountTest {

    protected abstract IoHandlerFactory newIoHandlerFactory();

    @Test
    @Timeout(5)
    public void testTimerExpirationDoesNotCountAsIoEvent() throws Exception {
        ManualIoEventLoop loop = new ManualIoEventLoop(Thread.currentThread(), newIoHandlerFactory());
        try {
            int result = loop.run(TimeUnit.MILLISECONDS.toNanos(100), -1);
            assertEquals(0, result, "timer expiration should not be counted as an I/O event");
        } finally {
            loop.shutdown();
            while (!loop.isTerminated()) {
                loop.runNow();
            }
        }
    }

    @Test
    @Timeout(5)
    public void testWakeupDoesNotCountAsIoEvent() throws Exception {
        ManualIoEventLoop loop = new ManualIoEventLoop(Thread.currentThread(), newIoHandlerFactory());
        try {
            CountDownLatch aboutToBlock = new CountDownLatch(1);
            Thread waker = new Thread(() -> {
                try {
                    aboutToBlock.await();
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                loop.wakeup();
            });
            waker.start();

            aboutToBlock.countDown();
            int result = loop.run(0, -1);
            assertEquals(0, result, "wakeup should not be counted as an I/O event");

            waker.join();
        } finally {
            loop.shutdown();
            while (!loop.isTerminated()) {
                loop.runNow();
            }
        }
    }

    @Test
    @Timeout(5)
    public void testScheduledTaskDoesNotInflateIoCount() throws Exception {
        ManualIoEventLoop loop = new ManualIoEventLoop(Thread.currentThread(), newIoHandlerFactory());
        try {
            AtomicBoolean taskRan = new AtomicBoolean();
            loop.schedule(() -> taskRan.set(true), 100, TimeUnit.MILLISECONDS);

            // Block until the scheduled task fires and is picked up by runAllTasks.
            // The handler may return slightly before the task deadline due to timer precision,
            // so we loop until the task actually executes.
            int result = 0;
            while (!taskRan.get()) {
                result += loop.run(0);
            }
            assertEquals(1, result,
                    "only the scheduled task should be counted, not internal timer events");
        } finally {
            loop.shutdown();
            while (!loop.isTerminated()) {
                loop.runNow();
            }
        }
    }
}
