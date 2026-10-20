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

import io.netty.channel.AbstractIoHandlerEventCountTest;
import io.netty.channel.IoHandlerFactory;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * On modern kernels with {@code epoll_pwait2} support, timeouts are handled directly by the syscall
 * without producing a timerfd event. To force the timerfd path and fully exercise the timer filtering,
 * run with {@code -Dio.netty.channel.epoll.epollWaitThreshold=0} (JVM-global, must be set before
 * {@code EpollIoHandler} class init).
 */
public class EpollIoHandlerWakeupEventCountTest extends AbstractIoHandlerEventCountTest {

    @BeforeAll
    public static void loadJNI() {
        assumeTrue(Epoll.isAvailable());
    }

    @Override
    protected IoHandlerFactory newIoHandlerFactory() {
        return EpollIoHandler.newFactory();
    }
}
