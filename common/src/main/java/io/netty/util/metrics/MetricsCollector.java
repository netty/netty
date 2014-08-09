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

package io.netty.util.metrics;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.MultithreadEventExecutorGroup;

/**
 * Derive from this interface to implement your own {@link MetricsCollector} to be used in an
 * {@link EventExecutorChooser} implementation.
 *
 * Take a look at the {@link DefaultMetricsCollector} class for more details.
 */
public interface MetricsCollector {
    /**
     * If used with a {@link MultithreadEventExecutorGroup} implementation, this method is called
     * immediately after the current instance is passed to an eventExecutor and can be used for any additional
     * initialization work that requires access to the eventExecutor. With the exception of the object's constructor,
     * this method is called before any other method. This method is called exactly once.
     *
     * @param eventExecutor     the {@link EventExecutor} to which the object is attached to.
     */
    <T extends EventExecutor> void init(T eventExecutor);
}
