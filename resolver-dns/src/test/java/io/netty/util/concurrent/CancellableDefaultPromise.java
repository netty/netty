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

/**
 * A {@link DefaultPromise} that supports cancellation. {@link DefaultPromise#isCancellationSupported()} is
 * package-private and the promises created by {@link EventExecutor#newPromise()} are not cancellable, so tests that
 * need to cancel a promise from outside of {@code io.netty.util.concurrent} have to use this class.
 */
public final class CancellableDefaultPromise<V> extends DefaultPromise<V> {

    public CancellableDefaultPromise(EventExecutor executor) {
        super(executor);
    }

    @Override
    boolean isCancellationSupported() {
        return true;
    }
}
