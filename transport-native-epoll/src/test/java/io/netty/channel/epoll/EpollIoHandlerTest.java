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
package io.netty.channel.epoll;

import io.netty.channel.IoEvent;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerContext;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.IoRegistration;
import io.netty.channel.unix.FileDescriptor;
import io.netty.util.concurrent.ThreadAwareExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EpollIoHandlerTest {
    @Test
    public void testRegisterWillNotTriggerEvent() throws Exception {
        IoHandlerFactory ioHandlerFactory = EpollIoHandler.newFactory();
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

        LinuxSocket socket = LinuxSocket.newSocketStream();
        AtomicReference<IoEvent> eventRef = new AtomicReference<>();
        IoRegistration registration = handler.register(new EpollIoHandle() {
            @Override
            public FileDescriptor fd() {
                return socket;
            }

            @Override
            public void handle(IoRegistration registration, IoEvent ioEvent) {
                eventRef.set(ioEvent);
            }

            @Override
            public void close() {
            }
        });
        handler.run(new IoHandlerContext() {
            @Override
            public boolean canBlock() {
                return false;
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
        assertTrue(registration.cancel());
        handler.prepareToDestroy();
        handler.destroy();
        socket.close();
        assertNull(eventRef.get());
    }
}
