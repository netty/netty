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

/**
 * A buffer to operate on
 */
public interface Buf extends Freeable {
    /**
     * The BufType which will be handled by the Buf implementation
     */
    BufType type();

    /**
     * Returns the maximum allowed capacity of this buffer.
     */
    int maxCapacity();

    /**
     * Returns {@code true} if and only if this buffer contains at least one readable element.
     */
    boolean isReadable();

    /**
     * Returns {@code true} if and only if this buffer contains equal to or more than the specified number of elements.
     */
    boolean isReadable(int size);

    /**
     * Returns {@code true} if and only if this buffer has enough room to allow writing one element.
     */
    boolean isWritable();

    /**
     * Returns {@code true} if and only if this buffer has enough room to allow writing the specified number of
     * elements.
     */
    boolean isWritable(int size);
}
