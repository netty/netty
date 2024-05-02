/*
 * Copyright 2024 The Netty Project
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

import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SingleThreadIoEventLoopTest {

    @Test
    void testIsIoType() {
        IoHandler handler = new TestIoHandler();
        IoHandler handler2 = new TestIoHandler() { };

        IoEventLoopGroup group = new SingleThreadIoEventLoop(null, Executors.defaultThreadFactory(), handler);
        assertTrue(group.isIoType(handler.getClass()));
        assertFalse(group.isIoType(handler2.getClass()));
        group.shutdownGracefully();
    }

    @Test
    void testIsCompatible() {
        IoHandler handler = new TestIoHandler() {
            @Override
            public boolean isCompatible(Class<? extends IoHandle> handleType) {
                return handleType.equals(TestIoHandle.class);
            }
        };

        IoHandle handle = new TestIoHandle() { };
        IoEventLoopGroup group = new SingleThreadIoEventLoop(null, Executors.defaultThreadFactory(), handler);
        assertTrue(group.isCompatible(TestIoHandle.class));
        assertFalse(group.isCompatible(handle.getClass()));
        group.shutdownGracefully();
    }

    private static class TestIoHandler implements IoHandler {
        @Override
        public int run(IoExecutionContext context) {
            return 0;
        }

        @Override
        public void prepareToDestroy() {
            // NOOP
        }

        @Override
        public void destroy() {
            // NOOP
        }

        @Override
        public IoRegistration register(IoEventLoop eventLoop, IoHandle handle) {
            return null;
        }

        @Override
        public void wakeup(IoEventLoop eventLoop) {
            // NOOP
        }

        @Override
        public boolean isCompatible(Class<? extends IoHandle> handleType) {
            return false;
        }
    }

    private class TestIoHandle implements IoHandle {
        @Override
        public void handle(IoRegistration registration, IoEvent readyOps) {
            // NOOP
        }

        @Override
        public void close() {
            // NOOP
        }
    }
}
