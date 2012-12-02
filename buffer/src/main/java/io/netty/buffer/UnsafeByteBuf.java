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

import java.nio.ByteBuffer;

public interface UnsafeByteBuf extends ByteBuf {

    /**
     * Returns the internal NIO buffer that is reused for I/O.
     *
     * @throws UnsupportedOperationException if the buffer has no internal NIO buffer
     */
    ByteBuffer internalNioBuffer();

    /**
     * Returns the internal NIO buffer array that is reused for I/O.
     *
     * @throws UnsupportedOperationException if the buffer has no internal NIO buffer array
     */
    ByteBuffer[] internalNioBuffers();

    /**
     * Similar to {@link ByteBuf#discardReadBytes()} except that this method might discard
     * some, all, or none of read bytes depending on its internal implementation to reduce
     * overall memory bandwidth consumption at the cost of potentially additional memory
     * consumption.
     */
    void discardSomeReadBytes();

    /**
     * Returns {@code true} if and only if this buffer has been deallocated by {@link #free()}.
     */
    boolean isFreed();

    /**
     * Deallocates the internal memory block of this buffer or returns it to the {@link ByteBufAllocator} it came
     * from.  The result of accessing a released buffer is unspecified and can even cause JVM crash.
     */
    void free();

    /**
     * Suspends the intermediary deallocation of the internal memory block of this buffer until asked via
     * {@link #resumeIntermediaryDeallocations()}. An intermediary deallocation is usually made when the capacity of
     * a buffer changes.
     */
    void suspendIntermediaryDeallocations();

    /**
     * Resumes the intermediary deallocation of the internal memory block of this buffer, suspended by
     * {@link #suspendIntermediaryDeallocations()}.
     */
    void resumeIntermediaryDeallocations();

    /**
     * Return the underlying buffer instance if this buffer is a wrapper.
     *
     * @return {@code null} if this buffer is not a wrapper
     */
    ByteBuf unwrap();
}
