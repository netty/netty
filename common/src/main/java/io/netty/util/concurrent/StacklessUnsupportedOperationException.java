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
package io.netty.util.concurrent;

import io.netty.util.internal.ThrowableUtil;

/**
 * The failure of the {@link EventExecutor#terminationFuture()} of executors that can't be terminated, such as
 * {@link GlobalEventExecutor} and {@link ImmediateEventExecutor}.
 */
final class StacklessUnsupportedOperationException extends UnsupportedOperationException {

    private static final long serialVersionUID = -8060232216137960173L;

    private StacklessUnsupportedOperationException() { }

    // Override fillInStackTrace() so we not populate the backtrace via a native call and so leak the
    // Classloader. The executors are singletons that hold on to this exception via their terminationFuture, so a
    // populated backtrace would pin the Classloader of whatever thread happened to trigger the lazy initialization
    // of their INSTANCE (see https://github.com/netty/netty/issues/17128).
    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    static StacklessUnsupportedOperationException newInstance(Class<?> clazz, String method) {
        return ThrowableUtil.unknownStackTrace(new StacklessUnsupportedOperationException(), clazz, method);
    }
}
