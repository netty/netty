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

import io.netty.channel.IoHandlerFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IoUringIoHandlerTest {

    @Test
    public void testOptions() {
        IoUringIoHandlerOption handlerOption = new IoUringIoHandlerOption();
        handlerOption
                .setMaxBoundedWorker(2)
                .setMaxUnboundedWorker(2);
        IoHandlerFactory ioHandlerFactory = IoUringIoHandler.newFactory(handlerOption);
        Assertions.assertDoesNotThrow(() -> {
            ioHandlerFactory.newHandler();
        });
    }
}
