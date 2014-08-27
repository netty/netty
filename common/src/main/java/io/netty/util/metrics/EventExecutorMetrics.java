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

/**
 * Derive from this interface to implement your own {@link EventExecutorMetrics} class.
 */
public interface EventExecutorMetrics<T extends EventExecutor> {
    /**
     * This method is called immediately after an {@link EventExecutorMetrics} object is passed to an
     * {@link EventExecutor} and can be used for any additional initialization work that requires access to the
     * {@link EventExecutor}. With exception to the constructor, this method is called before any other method of
     * {@link EventExecutorMetrics}. This method is called exactly once.
     *
     * @param eventExecutor the {@link EventExecutor} to which the object is attached to.
     */
    void init(T eventExecutor);
}
