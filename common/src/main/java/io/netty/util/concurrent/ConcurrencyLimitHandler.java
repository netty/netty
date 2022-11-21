/*
 * Copyright 2022 The Netty Project
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

/**
 * Handles the events notified by {@link ConcurrencyLimit}.
 */
@UnstableApi
public interface ConcurrencyLimitHandler {
    /**
     * Invoked when the requested permit has been acquired successfully.
     * Once this method is invoked, no other methods in this interface will be invoked.
     *
     * @param releaser the {@link Runnable} that releases the permit
     */
    void permitAcquired(Runnable releaser);

    /**
     * Invoked when the requested permit was not acquired within the given timeout.
     * Once this method is invoked, no other methods in this interface will be invoked.
     */
    void permitAcquisitionTimedOut();
}
