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

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class CompletionHandlers {

    private CompletionHandlers() { }

    static final CompletionHandler<?> IGNORE = new WrappingCompletionHandler<>(null, null);

    static <V> CompletionHandler<V> onExecutor(CompletionHandler<V> handler, EventExecutor executor) {
        return new WrappingCompletionHandler<>(handler, executor);
    }

    private static final class WrappingCompletionHandler<V> implements CompletionHandler<V> {
        private final CompletionHandler<V> wrapped;
        private final EventExecutor executor;

        WrappingCompletionHandler(CompletionHandler<V> wrapped, EventExecutor executor) {
            this.executor = executor;
            this.wrapped = wrapped;
        }

        @Override
        public void success(@Nullable V result) {
            if (wrapped == null) {
                // if wrapped is null we know that this is the static IGNORE instance
                return;
            }
            if (executor.inEventLoop()) {
                wrapped.success(result);
            } else {
                executor.execute(() -> wrapped.success(result));
            }
        }

        @Override
        public void failure(Throwable cause) {
            if (wrapped == null) {
                // if wrapped is null we know that this is the static IGNORE instance
                return;
            }
            if (executor.inEventLoop()) {
                wrapped.failure(cause);
            } else {
                executor.execute(() -> wrapped.failure(cause));
            }
        }

        @Override
        public Promise<V> toPromise(EventExecutor executor) {
            // Just return a new instance and not call addHandler as we ignore the notification anyway.
            return executor.newPromise();
        }

        @Override
        public CompletionHandler<V> andThen(CompletionHandler<? super V> after, EventExecutor executor) {
            if (after == this) {
                Objects.requireNonNull(executor, "executor");
                return this;
            }
            return CompletionHandler.super.andThen(after, executor);
        }

        @Override
        public CompletionHandler<V> onExecutor(EventExecutor executor) {
            if (wrapped == null) {
                // if wrapped is null we know that this is the static IGNORE instance
                Objects.requireNonNull(executor, "executor");
                return this;
            }
            if (Objects.requireNonNull(executor, "executor") == this.executor) {
                // It uses the same executor so just turne itself.
                return this;
            }
            return CompletionHandler.super.onExecutor(executor);
        }
    }
}
