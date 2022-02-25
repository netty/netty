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

import java.nio.ByteBuffer;

/**
 * A processor of {@linkplain ReadableComponent readable components}.
 */
@FunctionalInterface
public interface ReadableComponentProcessor<E extends Exception> {
    /**
     * Process the given component at the given index in the
     * {@link Buffer#forEachReadable(int, ReadableComponentProcessor) iteration}.
     * <p>
     * The component object itself is only valid during this call, but the {@link ByteBuffer byte buffers}, arrays, and
     * native address pointers obtained from it, will be valid until any operation is performed on the buffer, which
     * changes the internal memory.
     *
     * @param index The current index of the given buffer component, based on the initial index passed to the
     * {@link Buffer#forEachReadable(int, ReadableComponentProcessor)} method.
     * @param component The current buffer component being processed.
     * @return {@code true} if the iteration should continue and more components should be processed, otherwise
     * {@code false} to stop the iteration early.
     */
    boolean process(int index, ReadableComponent component) throws E;
}
