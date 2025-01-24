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

import io.netty.channel.IoExecutor;
import io.netty.channel.IoHandler;
import io.netty.channel.IoHandlerFactory;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


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
                .setMaxUnboundedWorker(2);
        IoHandlerFactory ioHandlerFactory = IoUringIoHandler.newFactory(config);
        IoHandler handler = ioHandlerFactory.newHandler(new IoExecutor() {
            @Override
            public boolean inExecutorThread(Thread thread) {
                return ImmediateEventExecutor.INSTANCE.inEventLoop(thread);
            }

            @Override
            public <V> Promise<V> newPromise() {
                return ImmediateEventExecutor.INSTANCE.newPromise();
            }

            @Override
            public void execute(Runnable command) {
                ImmediateEventExecutor.INSTANCE.execute(command);
            }
        });
        handler.initialize();
        handler.destroy();
    }
}
