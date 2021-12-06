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
import java.nio.charset.Charset;

/**
 * The {@code CompositeBuffer} is a concrete {@link Buffer} implementation that make a number of other buffers appear
 * as one. A composite buffer behaves the same as a normal, non-composite buffer in every way, so you normally don't
 * need to handle them specially.
 * <p>
 * A composite buffer is constructed using one of the {@code compose} methods:
 * <ul>
 *     <li>
 *         {@link BufferAllocator#compose(Iterable)} creates a composite buffer from the buffers that are sent to it via
 *         the passed in send objects. Since {@link Send#receive()} transfers ownership, the resulting composite buffer
 *         will have ownership, because it is guaranteed that there are no other references to its constituent buffers.
 *     </li>
 *     <li>
 *         {@link BufferAllocator#compose(Send)} creates a composite buffer with a single component.
 *         Since {@link Send#receive()} transfers ownership, the resulting composite buffer
 *         will have ownership, because it is guaranteed that there are no other references to its constituent buffer.
 *     </li>
 *     <li>
 *         {@link BufferAllocator#compose()} creates an empty, zero capacity, composite buffer. Such empty buffers may
 *         change their {@linkplain #readOnly() read-only} states when they gain their first component.
 *     </li>
 * </ul>
 * Composite buffers can later be extended with internally allocated components, with {@link #ensureWritable(int)},
 * or with externally allocated buffers, using {@link #extendWith(Send)}.
 *
 * <h3>How buffers compose</h3>
 *
 * A buffer can be thought of as having three distinct regions, in order:
 * <ol>
 *     <li>Memory that have been read.</li>
 *     <li>Memory that is readable.</li>
 *     <li>Memory that can be written to.</li>
 * </ol>
 *
 * A composite buffer must present itself similarly, but may be composed of buffers where their offsets don't line up,
 * and thus end up producing "gaps" in the composite buffer.
 * The solution is that the composite buffer hide these gaps from view.
 * <p>
 * For example, if we compose two buffers that both have non-zero read-offset, then the composite buffer will get the
 * read-offset of the first buffer, followed by a concatenation of the readable memory from both.
 * Similarly, the write-offset of the composite buffer will come from the last buffer with a non-zero write-offset,
 * and any writable memory prior to the last readable memory region will be hidden:
 *
 * <pre>
 *     First buffer                Second buffer
 *      +----------------------+    +--------------------+
 *     0|    |r/o      |w/o    |   0|    |r/o     |w/o   |
 *      +----+---------+-------+    +----+--------+------+
 *       \    \         \  ,____________/   ,___________/
 *        \    \         \/                /
 *         +----+---------+--------+------+
 *        0|    |r/o      :        |w/o   |  Composite buffer
 *         +------------------------------+
 * </pre>
 *
 * Components in the middle can have both their end-regions hidden in the same way, so only their readable memory is
 * included in the composite buffer.
 * Buffers that consist entirely of memory that has already been read, or memory that is writable, can also concatenate
 * onto those regions at the ends.
 * The final capacity of the composite buffer, will be the sum of all the visible regions.
 * <p>
 * Reads and writes to the composite buffer that modifies the read or write offsets, will also modify the relevant
 * offsets in the constituent buffers.
 *
 * <h3>Constituent buffer requirements</h3>
 *
 * The buffers that are being composed must all have the same writability.
 * Either all components must be {@linkplain Buffer#readOnly() read-only}, or they must all be writable.
 * <p>
 * It is not a requirement that the buffers have the same size.
 * <p>
 * It is not a requirement that the buffers are allocated by this allocator, but if
 * {@link Buffer#ensureWritable(int)} is called on the composed buffer, and the composed buffer needs to be
 * expanded, then this allocator instance will be used for allocation the extra memory.
 *
 * <h3>Ownership and Send</h3>
 *
 * {@linkplain Resource#send() Sending} a composite buffer implies sending all of its constituent buffers.
 * For sending to be possible, both the composite buffer itself, and all of its constituent buffers, must be in a
 * state that permits them being sent. This should be the case by default, as it shouldn't be possible to create
 * composite buffers that can't be sent.
 */
public interface CompositeBuffer extends Buffer {

    /**
     * Create an empty composite buffer, that has no components. The buffer can be extended with components using either
     * {@link #ensureWritable(int)} or {@link #extendWith(Send)}.
     *
     * @param allocator The allocator for the composite buffer. This allocator will be used e.g. to service
     * {@link #ensureWritable(int)} calls.
     * @return A composite buffer that has no components, and has a capacity of zero.
     */
    static CompositeBuffer compose(BufferAllocator allocator) {
        return DefaultCompositeBuffer.compose(allocator);
    }

    /**
     * Check if the given buffer is a composite buffer or not.
     * @param composite The buffer to check.
     * @return {@code true} if the given buffer was created with {@link BufferAllocator#compose()},
     * {@link BufferAllocator#compose(Send)} or {@link BufferAllocator#compose(Iterable)}, {@code false} otherwise.
     */
    static boolean isComposite(Buffer composite) {
        return composite instanceof CompositeBuffer;
    }

    /**
     * Extend this composite buffer with the given extension buffer.
     * This works as if the extension had originally been included at the end of the list of constituent buffers when
     * the composite buffer was created.
     * The extension buffer is added to the end of this composite buffer, which is modified in-place.
     *
     * @see BufferAllocator#compose(Send)
     * @param extension The buffer to extend the composite buffer with.
     * @return This composite buffer instance.
     */
    CompositeBuffer extendWith(Send<Buffer> extension);

    /**
     * Split this buffer at a component boundary that is less than or equal to the given offset.
     * <p>
     * This method behaves the same as {@link #split(int)}, except no components are split.
     *
     * @param splitOffset The maximum split offset. The real split offset will be at a component boundary that is less
     *                   than or equal to this offset.
     * @return A new buffer with independent and exclusive ownership over the bytes from the beginning to a component
     * boundary less than or equal to the given offset of this buffer.
     */
    CompositeBuffer splitComponentsFloor(int splitOffset);

    /**
     * Split this buffer at a component boundary that is greater than or equal to the given offset.
     * <p>
     * This method behaves the same as {@link #split(int)}, except no components are split.
     *
     * @param splitOffset The minimum split offset. The real split offset will be at a component boundary that is
     *                   greater than or equal to this offset.
     * @return A new buffer with independent and exclusive ownership over the bytes from the beginning to a component
     * boundary greater than or equal to the given offset of this buffer.
     */
    CompositeBuffer splitComponentsCeil(int splitOffset);

    /**
     * Break a composite buffer into its constituent components.
     * <p>
     * This "consumes" the composite buffer, leaving the composite buffer instance as if it had been closed.
     * The buffers in the returned array are not closed, and become owned by the caller.
     *
     * @return An array of the constituent buffer components.
     */
    Buffer[] decomposeBuffer();

    @Override
    CompositeBuffer readerOffset(int offset);

    @Override
    CompositeBuffer writerOffset(int offset);

    @Override
    default CompositeBuffer skipReadable(int delta) {
        return (CompositeBuffer) Buffer.super.skipReadable(delta);
    }

    @Override
    default CompositeBuffer skipWritable(int delta) {
        return (CompositeBuffer) Buffer.super.skipWritable(delta);
    }

    @Override
    CompositeBuffer fill(byte value);

    @Override
    CompositeBuffer makeReadOnly();

    @Override
    default CompositeBuffer writeBytes(Buffer source) {
        return (CompositeBuffer) Buffer.super.writeBytes(source);
    }

    @Override
    default CompositeBuffer writeBytes(byte[] source) {
        return (CompositeBuffer) Buffer.super.writeBytes(source);
    }

    @Override
    default CompositeBuffer writeBytes(byte[] source, int srcPos, int length) {
        return (CompositeBuffer) Buffer.super.writeBytes(source, srcPos, length);
    }

    @Override
    default CompositeBuffer writeCharSequence(CharSequence source, Charset charset) {
        return (CompositeBuffer) Buffer.super.writeCharSequence(source, charset);
    }

    @Override
    default CompositeBuffer readBytes(byte[] destination, int destPos, int length) {
        return (CompositeBuffer) Buffer.super.readBytes(destination, destPos, length);
    }

    @Override
    default CompositeBuffer writeBoolean(boolean value) {
        return (CompositeBuffer) Buffer.super.writeBoolean(value);
    }

    @Override
    default CompositeBuffer setBoolean(int woff, boolean value) {
        return (CompositeBuffer) Buffer.super.setBoolean(woff, value);
    }

    @Override
    default CompositeBuffer writeBytes(ByteBuffer source) {
        return (CompositeBuffer) Buffer.super.writeBytes(source);
    }

    @Override
    default CompositeBuffer readBytes(ByteBuffer destination) {
        return (CompositeBuffer) Buffer.super.readBytes(destination);
    }

    @Override
    default CompositeBuffer resetOffsets() {
        return (CompositeBuffer) Buffer.super.resetOffsets();
    }

    @Override
    default CompositeBuffer ensureWritable(int size) {
        return (CompositeBuffer) Buffer.super.ensureWritable(size);
    }

    @Override
    CompositeBuffer ensureWritable(int size, int minimumGrowth, boolean allowCompaction);

    @Override
    default CompositeBuffer copy() {
        return (CompositeBuffer) Buffer.super.copy();
    }

    @Override
    default CompositeBuffer copy(int offset, int length) {
        return (CompositeBuffer) Buffer.super.copy(offset, length);
    }

    @Override
    CompositeBuffer copy(int offset, int length, boolean readOnly);

    @Override
    default CompositeBuffer split() {
        return (CompositeBuffer) Buffer.super.split();
    }

    @Override
    CompositeBuffer split(int splitOffset);

    @Override
    CompositeBuffer compact();

    @Override
    CompositeBuffer writeByte(byte value);

    @Override
    CompositeBuffer setByte(int woff, byte value);

    @Override
    CompositeBuffer writeUnsignedByte(int value);

    @Override
    CompositeBuffer setUnsignedByte(int woff, int value);

    @Override
    CompositeBuffer writeChar(char value);

    @Override
    CompositeBuffer setChar(int woff, char value);

    @Override
    CompositeBuffer writeShort(short value);

    @Override
    CompositeBuffer setShort(int woff, short value);

    @Override
    CompositeBuffer writeUnsignedShort(int value);

    @Override
    CompositeBuffer setUnsignedShort(int woff, int value);

    @Override
    CompositeBuffer writeMedium(int value);

    @Override
    CompositeBuffer setMedium(int woff, int value);

    @Override
    CompositeBuffer writeUnsignedMedium(int value);

    @Override
    CompositeBuffer setUnsignedMedium(int woff, int value);

    @Override
    CompositeBuffer writeInt(int value);

    @Override
    CompositeBuffer setInt(int woff, int value);

    @Override
    CompositeBuffer writeUnsignedInt(long value);

    @Override
    CompositeBuffer setUnsignedInt(int woff, long value);

    @Override
    CompositeBuffer writeFloat(float value);

    @Override
    CompositeBuffer setFloat(int woff, float value);

    @Override
    CompositeBuffer writeLong(long value);

    @Override
    CompositeBuffer setLong(int woff, long value);

    @Override
    CompositeBuffer writeDouble(double value);

    @Override
    CompositeBuffer setDouble(int woff, double value);

    @Override
    CompositeBuffer implicitCapacityLimit(int limit);
}
