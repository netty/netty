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
 * Handles IO dispatching for an {@link ThreadAwareExecutor}.
 * All operations except {@link #wakeup()} and {@link #isCompatible(Class)} <strong>MUST</strong> be executed
 * on the {@link ThreadAwareExecutor} thread (which means {@link ThreadAwareExecutor#isExecutorThread(Thread)} must
 * return {@code true}) and should never be called from the user-directly.
 * <p>
 * Once a {@link IoHandle} is registered via the {@link #register(IoHandle)} method it's possible
 * to submit {@link IoOps} related to the {@link IoHandle} via {@link IoRegistration#submit(IoOps)}.
 * These submitted {@link IoOps} are the "source" of {@link IoEvent}s that are dispatched to the registered
 * {@link IoHandle} via the {@link IoHandle#handle(IoRegistration, IoEvent)} method.
 * These events must be consumed (and handled) as otherwise they might be reported again until handled.
 *
 */
public interface IoHandler {

    /**
     * Initialize this {@link IoHandler}.
     */
    default void initialize() { }

    /**
     * Run the IO handled by this {@link IoHandler}. The {@link IoHandlerContext} should be used
     * to ensure we not execute too long and so block the processing of other task that are
     * scheduled on the {@link ThreadAwareExecutor}. This is done by taking {@link IoHandlerContext#delayNanos(long)}
     * or {@link IoHandlerContext#deadlineNanos()} into account.
     *
     * @param  context  the {@link IoHandlerContext}.
     * @return          the number of {@link IoHandle} for which I/O was handled.
     */
    int run(IoHandlerContext context);

    /**
     * Prepare to destroy this {@link IoHandler}. This method will be called before {@link #destroy()} and may be
     * called multiple times.
     */
    default void prepareToDestroy() { }

    /**
     * Destroy the {@link IoHandler} and free all its resources. Once destroyed using the {@link IoHandler} will
     * cause undefined behaviour.
     */
    default void destroy() { }

    /**
     * Register a {@link IoHandle} for IO.
     *
     * @param handle        the {@link IoHandle} to register.
     * @throws Exception    thrown if an error happens during registration.
     */
    IoRegistration register(IoHandle handle) throws Exception;

    /**
     * Wakeup the {@link IoHandler}, which means if any operation blocks it should be unblocked and
     * return as soon as possible.
     */
    void wakeup();

    /**
     * Returns {@code true} if the given type is compatible with this {@link IoHandler} and so can be registered,
     * {@code false} otherwise.
     *
     * @param handleType the type of the {@link IoHandle}.
     * @return if compatible of not.
     */
    boolean isCompatible(Class<? extends IoHandle> handleType);
}
