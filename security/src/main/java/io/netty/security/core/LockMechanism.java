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
package io.netty.security.core;

/**
 * {@link LockMechanism} provides locking mechanism to ensure data consistency.
 */
public interface LockMechanism {

    /**
     * Lock a {@link SafeListController} instance to prevent it from being modified
     *
     * @throws IllegalStateException If Instance is already unlocked
     */
    void lock();

    /**
     * Unlock a {@link SafeListController} instance for modification
     *
     * @throws IllegalStateException If Instance is already unlocked
     */
    void unlock();

    /**
     * Returns {@code true} if this class is locked else {@code false}
     */
    boolean isLocked();
}
