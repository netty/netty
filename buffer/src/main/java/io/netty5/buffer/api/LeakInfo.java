/*
 * Copyright 2021 The Netty Project
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
package io.netty5.buffer.api;

import io.netty5.buffer.api.LeakInfo.TracePoint;
import io.netty5.util.internal.UnstableApi;

import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Information about a resource leak that happened.
 * This information is provided to callbacks of the {@link MemoryManager#onLeakDetected(Consumer)} method.
 */
@UnstableApi
public interface LeakInfo extends Iterable<TracePoint> {
    /**
     * Create a {@link Stream} of all the {@link TracePoint}s in this {@link LeakInfo}.
     * The returned {@link Stream} does not need to be closed.
     *
     * @return A {@link Stream} of {@link TracePoint}s, covering the life-cycle of the leaked resource.
     */
    Stream<TracePoint> stream();

    /**
     * A human-readable description of the object that leaked.
     *
     * @return A description of the leaked object.
     */
    String objectDescription();

    /**
     * A moment in the life of the leaked object, for which some information was recorded.
     */
    interface TracePoint {
        /**
         * Get the hint object from a {@linkplain Resource#touch(Object) touch} call, if any.
         * If this trace point is not a {@code touch} call, or if the hint argument was {@code null},
         * then {@code null} will be returned.
         *
         * @return A hint object, if there is any on this trace point, otherwise {@code null}.
         */
        Object hint();

        /**
         * Get a {@link Throwable} instance that holds the recorded stack trace of this trace point.
         *
         * @return A {@link Throwable} with the stack trace of this trace point.
         */
        Throwable traceback();
    }
}
