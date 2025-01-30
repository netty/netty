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

import io.netty.util.concurrent.ThreadAwareExecutor;
/**
 * A handle that can be registered to a {@link IoHandler}.
 * All methods must be called from the {@link ThreadAwareExecutor} thread (which means
 * {@link ThreadAwareExecutor#isExecutorThread(Thread)} must return {@code true})
 */
public interface IoHandle extends AutoCloseable {

    /**
     * Be called once there is something to handle.
     *
     * @param registration  the {@link IoRegistration} for this {@link IoHandle}.
     * @param ioEvent       the {@link IoEvent} that must be handled. The {@link IoEvent} is only valid
     *                      while this method is executed and so must not escape it.
     */
    void handle(IoRegistration registration, IoEvent ioEvent);
}
