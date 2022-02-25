/*
 * Copyright 2017 The Netty Project
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
package io.netty5.channel.epoll;

import io.netty5.channel.DefaultSelectStrategyFactory;
import io.netty5.channel.EventLoop;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.IoHandlerFactory;
import io.netty5.channel.ServerChannel;
import io.netty5.channel.SingleThreadEventLoop;
import io.netty5.channel.unix.FileDescriptor;
import io.netty5.testsuite.transport.AbstractSingleThreadEventLoopTest;
import io.netty5.util.concurrent.DefaultThreadFactory;
import io.netty5.util.concurrent.Future;
import io.netty5.util.concurrent.ThreadPerTaskExecutor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EpollEventLoopTest extends AbstractSingleThreadEventLoopTest {

    @Test
    public void testScheduleBigDelayNotOverflow() {
        final AtomicReference<Throwable> capture = new AtomicReference<>();

        final EventLoopGroup group = new SingleThreadEventLoop(
                new ThreadPerTaskExecutor(new DefaultThreadFactory(getClass())),
                new EpollHandler(0, DefaultSelectStrategyFactory.INSTANCE.newSelectStrategy()) {
                    @Override
                    void handleLoopException(Throwable t) {
                        capture.set(t);
                        super.handleLoopException(t);
                    }
                });

        try {
            final EventLoop eventLoop = group.next();
            Future<?> future = eventLoop.schedule(() -> {
                // NOOP
            }, Long.MAX_VALUE, TimeUnit.MILLISECONDS);

            assertFalse(future.awaitUninterruptibly(1000));
            assertTrue(future.cancel());
            assertNull(capture.get());
        } finally {
            group.shutdownGracefully();
        }
    }

    @Test
    public void testEventFDETSemantics() throws Throwable {
        final FileDescriptor epoll = Native.newEpollCreate();
        final FileDescriptor eventFd = Native.newEventFd();
        final FileDescriptor timerFd = Native.newTimerFd();
        final EpollEventArray array = new EpollEventArray(1024);
        try {
            Native.epollCtlAdd(epoll.intValue(), eventFd.intValue(), Native.EPOLLIN | Native.EPOLLET);
            final AtomicReference<Throwable> causeRef = new AtomicReference<>();
            final AtomicInteger integer = new AtomicInteger();
            final Thread t = new Thread(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        int ready = Native.epollWait(epoll, array, timerFd, -1, -1);
                        assertEquals(1, ready);
                        assertEquals(eventFd.intValue(), array.fd(0));
                        integer.incrementAndGet();
                    }
                } catch (IOException e) {
                    causeRef.set(e);
                }
            });
            t.start();
            Native.eventFdWrite(eventFd.intValue(), 1);

            // Spin until we was the wakeup.
            while (integer.get() != 1) {
                Thread.sleep(10);
            }
            // Sleep for a short moment to ensure there is not other wakeup.
            Thread.sleep(1000);
            assertEquals(1, integer.get());
            Native.eventFdWrite(eventFd.intValue(), 1);
            t.join();
            Throwable cause = causeRef.get();
            if (cause != null) {
                throw cause;
            }
            assertEquals(2, integer.get());
        } finally {
            array.free();
            epoll.close();
            eventFd.close();
            timerFd.close();
        }
    }

    @Override
    protected IoHandlerFactory newIoHandlerFactory() {
        return EpollHandler.newFactory();
    }

    @Override
    protected Class<? extends ServerChannel> serverChannelClass() {
        return EpollServerSocketChannel.class;
    }
}
