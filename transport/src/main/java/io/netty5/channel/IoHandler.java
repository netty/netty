/*
 * Copyright 2018 The Netty Project
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
package io.netty5.channel;

/**
 * Handles IO dispatching for an {@link EventLoop}
 * All operations except {@link #wakeup(boolean)} and {@link #isCompatible(Class)} <strong>MUST</strong> be executed
 * on the {@link EventLoop} thread and should never be called from the user-directly.
 */
public interface IoHandler {
    /**
     * Run the IO handled by this {@link IoHandler}. The {@link IoExecutionContext} should be used
     * to ensure we not execute too long and so block the processing of other task that are
     * scheduled on the {@link EventLoop}. This is done by taking {@link IoExecutionContext#delayNanos(long)} or
     * {@link IoExecutionContext#deadlineNanos()} into account.
     *
     * @return the number of {@link IoHandle} for which I/O was handled.
     */
    int run(IoExecutionContext context);

    /**
     * Prepare to destroy this {@link IoHandler}. This method will be called before {@link #destroy()} and may be
     * called multiple times.
     */
    void prepareToDestroy();

    /**
     * Destroy the {@link IoHandler} and free all its resources.
     */
    void destroy();

    /**
     * Register a {@link Channel} for IO.
     *
     * @param channel       the {@link Channel} to register..
     * @throws Exception    thrown if an error happens during registration.
     */
    void register(IoHandle channel) throws Exception;

    /**
     * Deregister a {@link IoHandle} for IO.
     *
     * @param handle        the {@link IoHandle} to deregister..
     * @throws Exception    thrown if an error happens during deregistration.
     */
    void deregister(IoHandle handle) throws Exception;

    /**
     * Wakeup the {@link IoHandler}, which means if any operation blocks it should be unblocked and
     * return as soon as possible.
     */
    void wakeup(boolean inEventLoop);

    /**
     * Returns {@code true} if the given type is compatible with this {@link IoHandler} and so can be registered,
     * {@code false} otherwise.
     *
     * @param handleType    the type of the {@link IoHandle}.
     * @return              if compatible of not.
     */
    boolean isCompatible(Class<? extends IoHandle> handleType);
}
