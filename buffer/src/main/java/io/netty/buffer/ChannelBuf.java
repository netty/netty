/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.buffer;

public interface ChannelBuf {
    /**
     * The ChannelBufType which will be handled by the ChannelBuf implementation
     */
    ChannelBufType type();

    /**
     * Provides access to potentially unsafe operations of this buffer.
     */
    Unsafe unsafe();

    /**
     * Provides the potentially unsafe operations of {@link ByteBuf}.
     */
    interface Unsafe {
        /**
         * Returns {@code true} if and only if this buffer has been deallocated by {@link #free()}.
         */
        boolean isFreed();

        /**
         * Deallocates the internal memory block of this buffer or returns it to the {@link ByteBufAllocator} it came
         * from.  The result of accessing a released buffer is unspecified and can even cause JVM crash.
         */
        void free();
    }
}
