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
import io.netty.util.concurrent.ThreadAwareExecutor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;


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

    private static boolean setUpCQSizeUnavailable() {
        return !IoUring.isSetupCqeSizeSupported();
    }
}
