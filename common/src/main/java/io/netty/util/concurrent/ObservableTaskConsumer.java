/*
 * Copyright 2019 The Netty Project
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
package io.netty.util.concurrent;

import io.netty.util.internal.UnstableApi;

@UnstableApi
public interface ObservableTaskConsumer {

    long UNSUPPORTED = -1;

    /**
     * Returns the number of submitted tasks: it's safe to be read by different threads.<br>
     * <p>
     * If {@code this} don't support this metrics it returns a negative value, likely {@link #UNSUPPORTED}.
     */
    long submittedTasks();

    /**
     * Returns the number of completed tasks: it's safe to be read by different threads.<br>
     * <p>
     * If {@code this} don't support this metrics it returns a negative value, likely {@link #UNSUPPORTED}.
     */
    long completedTasks();
}
