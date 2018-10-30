/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.epoll;

import io.netty.channel.unix.FileDescriptor;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EpollTest {

    @Test
    public void testIsAvailable() {
        assertTrue(Epoll.isAvailable());
    }

    // Testcase for https://github.com/netty/netty/issues/8444
    @Test(timeout = 5000)
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
                        assertEquals(1, Native.epollWait(epoll, eventArray, timerFd, -1, -1));
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
