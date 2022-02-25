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
package io.netty5.buffer.api.pool;

import io.netty5.buffer.api.Buffer;
import io.netty5.buffer.api.Drop;

/**
 * Memory that isn't attached to any particular buffer.
 * <p>
 * Used for transporting the details of a buffer allocation through the layers in {@link PooledBufferAllocator}.
 */
public interface UntetheredMemory {
    /**
     * Produces the recoverable memory object associated with this piece of untethered memory.
     *
     * @implNote This method should only be called once, since it might be expensive.
     */
    <Memory> Memory memory();

    /**
     * Produces the drop instance associated with this piece of untethered memory.
     *
     * @implNote This method should only be called once, since it might be expensive, or interact with Cleaners.
     */
    <BufferType extends Buffer> Drop<BufferType> drop();
}
