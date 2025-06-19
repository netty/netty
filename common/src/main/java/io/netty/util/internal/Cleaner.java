/*
* Copyright 2017 The Netty Project
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
package io.netty.util.internal;

import java.nio.ByteBuffer;

/**
 * Allows to free direct {@link ByteBuffer}s.
 */
interface Cleaner {
    /**
     * Create a direct {@link ByteBuffer} and return it alongside its cleaning mechanism,
     * in a {@link CleanableDirectBuffer}.
     *
     * @param capacity The desired capacity of the direct buffer.
     * @return The new {@link CleanableDirectBuffer} instance.
     */
    CleanableDirectBuffer allocate(int capacity);

    /**
     * Free a direct {@link ByteBuffer} if possible
     *
     * @deprecated Instead allocate buffers from {@link #allocate(int)}
     * and use the associated {@link CleanableDirectBuffer#clean()} method.
     */
    @Deprecated
    void freeDirectBuffer(ByteBuffer buffer);
}
