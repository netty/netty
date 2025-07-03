/*
 * Copyright 2025 The Netty Project
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
package io.netty.buffer;

import jdk.jfr.Event;

/**
 * An abstract memory allocator event.
 */
@SuppressWarnings("Since15")
abstract class AbstractAllocatorEvent extends Event {
    /**
     * Obtain the memory address of the given buffer, if it has any.
     * This method is safe to call even on buffers that has a zero reference count,
     * but the returned address must not be used to access memory.
     */
    protected long getMemoryAddressOf(AbstractByteBuf buf) {
        return buf._memoryAddress();
    }
}
