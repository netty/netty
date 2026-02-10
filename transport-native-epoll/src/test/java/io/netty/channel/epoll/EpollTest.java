/*
 * Copyright 2019 The Netty Project
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
package io.netty.channel.epoll;

import io.netty.channel.unix.FileDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EpollTest {

    @Test
    public void testIsAvailable() {
        assertTrue(Epoll.isAvailable());
    }

    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testEpollWaitTimeoutAccuracy() throws Exception {
        final int timeoutMs = 200;
        final FileDescriptor epoll = Native.newEpollCreate();
        final EpollEventArray eventArray = new EpollEventArray(8);
        try {
            long startNs = System.nanoTime();
            // No fds registered, so this will just wait for the timeout.
            int ready = Native.epollWait(epoll, eventArray, timeoutMs);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

            assertEquals(0, ready);
            // Should have waited at least close to the timeout
            assertThat(elapsedMs).isGreaterThanOrEqualTo(timeoutMs - 20);
            // Should not have waited vastly longer than the timeout
            assertThat(elapsedMs).isLessThan(timeoutMs + 200);
        } finally {
            eventArray.free();
            epoll.close();
        }
    }

    // Testcase for https://github.com/netty/netty/issues/8444
    @Test
    @Timeout(value = 5000, unit = TimeUnit.MILLISECONDS)
    public void testEpollWaitWithTimeOutMinusOne() throws Exception  {
        final EpollEventArray eventArray = new EpollEventArray(8);
        try {
            final FileDescriptor epoll = Native.newEpollCreate();
            final FileDescriptor timerFd = Native.newTimerFd();
            final FileDescriptor eventfd = Native.newEventFd();
            Native.epollCtlAdd(epoll.intValue(), timerFd.intValue(), Native.EPOLLIN);
            Native.epollCtlAdd(epoll.intValue(), eventfd.intValue(), Native.EPOLLIN);

            final AtomicReference<Throwable> ref = new AtomicReference<Throwable>();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        assertEquals(1, Native.epollWait(epoll, eventArray, false));
                        // This should have been woken up because of eventfd_write.
                        assertEquals(eventfd.intValue(), eventArray.fd(0));
                    } catch (Throwable cause) {
                        ref.set(cause);
                    }
                }
            });
            t.start();
            t.join(1000);
            assertTrue(t.isAlive());
            Native.eventFdWrite(eventfd.intValue(), 1);

            t.join();
            assertNull(ref.get());
            epoll.close();
            timerFd.close();
            eventfd.close();
        } finally {
            eventArray.free();
        }
    }
}
