/*
 * Copyright 2016 The Netty Project
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
package io.netty.channel;

import io.netty.util.concurrent.InstrumentedEventExecutor;
import io.netty.util.internal.UnstableApi;

/**
 * An {@link EventLoop} that allows to obtain metrics.
 */
@UnstableApi
public interface InstrumentedEventLoop extends EventLoop, InstrumentedEventExecutor {

    /**
     * Returns the number of registered {@link Channel}s.
     */
    int registeredChannels();

    /**
     * Returns the number of milliseconds the last processing of all IO took.
     */
    long lastIoProcessingTime();

    /**
     * Returns the number of milliseconds the last processing of all tasks took.
     */
    long lastTaskProcessingTime();

    /**
     * Returns the number {@link Channel}s that where processed in the last run.
     */
    int lastProcessedChannels();

    /**
     * Returns the number of milliseconds the {@link EventLoop} blocked before run to pick up IO and tasks.
     */
    long lastBlockingAmount();
}
