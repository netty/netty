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
package io.netty5.buffer;

import io.netty5.buffer.ComponentIterator.Next;
import io.netty5.buffer.internal.InternalBufferUtils;
import io.netty5.util.Resource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;

import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;

/**
 * A life cycled buffer of memory, with separate reader and writer offsets.
 * <p>
 * A buffer is a logically sequential stretch of memory with a certain capacity, an offset for writing,
 * and an offset for reading.
 * Buffers may be {@linkplain CompositeBuffer composed} of multiple {@linkplain #countComponents() components},
 * where each component is a guaranteed contiguous chunk of memory.
 *
 * <h3>Creating a buffer</h3>
 *
 * Buffers are created by {@linkplain BufferAllocator allocators}, and their {@code allocate} family of methods.
 * A number of standard allocators exist, and are available through static methods on the {@code BufferAllocator}
 * interface.
 *
 * <h3>Buffer life cycle</h3>
 *
 * The buffer has a life cycle, where it is allocated, used, and deallocated.
 * When the buffer is initially allocated, a pairing {@link #close()} call will deallocate it.
 * If a buffer is {@linkplain #send() sent} elsewhere, the {@linkplain #close() close} method on the given instance
 * will become a no-op.
 * The buffer can be thought of as a view onto memory, and calling {@link #send()} on the buffer will effectively close
 * that view, and recreate it upon reception at its destination.
 *
 * <h3>Thread-safety</h3>
 *
 * Buffers are <strong>not</strong> thread-safe.
 *
 * <h3>Accessing data</h3>
 *
 * Data access methods fall into two classes:
 * <ol>
 *     <li>Access that are based on, and updates, the read or write offset positions.</li>
 *     <ul><li>These accessor methods are typically called {@code readX} or {@code writeX}.</li></ul>
 *     <li>Access that take offsets as arguments, and do not update read or write offset positions.</li>
 *     <ul><li>These accessor methods are typically called {@code getX} or {@code setX}.</li></ul>
 * </ol>
 *
 * A buffer contains two mutable offset positions: one for reading and one for writing.
 * These positions use <a href="https://en.wikipedia.org/wiki/Zero-based_numbering">zero-based indexing</a>,
 * such that the first byte of data in the buffer is placed at offset {@code 0},
 * and the last byte in the buffer is at offset {@link #capacity() capacity - 1}.
 * The {@link #readerOffset()} is the offset into the buffer from which the next read will take place,
 * and is initially zero.
 * The reader offset must always be less than or equal to the {@link #writerOffset()}.
 * The {@link #writerOffset()} is likewise the offset into the buffer where the next write will take place.
 * The writer offset is also initially zero, and must be less than or equal to the {@linkplain #capacity() capacity}.
 * <p>
 * This carves the buffer into three regions, as demonstrated by this diagram:
 * <pre>
 *      +-------------------+------------------+------------------+
 *      | discardable bytes |  readable bytes  |  writable bytes  |
 *      |                   |     (CONTENT)    |                  |
 *      +-------------------+------------------+------------------+
 *      |                   |                  |                  |
 *      0      <=     readerOffset  <=   writerOffset    <=    capacity
 * </pre>
 *
 * <h3>Byte Order</h3>
 *
 * Buffers are always big endian, and this cannot be changed.
 * Usages that need to get, set, read, or write, little-endian values will have to flip the byte order of the values
 * they read and write.
 *
 * <h3 name="split">Splitting buffers</h3>
 *
 * The {@link #split()} method breaks a buffer into two.
 * The two buffers will share the underlying memory, but their regions will not overlap, ensuring that the memory is
 * safely shared between the two.
 * <p>
 * Splitting a buffer is useful for when you want to hand over a region of a buffer to some other,
 * perhaps unknown, piece of code, and relinquish your ownership of that buffer region in the process.
 * Examples include aggregating messages into an accumulator buffer, and sending messages down the pipeline for
 * further processing, as split buffer regions, once their data has been received in its entirety.
 *
 * If you instead wish to temporarily share a region of a buffer, you will have to pass offset and length along with the
 * buffer, or you will have to make a copy of the region.
 *
 * <h3>Buffers as constants</h3>
 *
 * Sometimes, the same bit of data will be processed or transmitted over and over again. In such cases, it can be
 * tempting to allocate and fill a buffer once, and then reuse it.
 * Such reuse must be done carefully, however, to avoid a number of bugs.
 * The {@link BufferAllocator} has a {@link BufferAllocator#constBufferSupplier(byte[])} method that solves this, and
 * prevents these bugs from occurring.
 */
public interface Buffer extends Resource<Buffer>, BufferAccessor {
    /**
     * The capacity of this buffer, that is, the maximum number of bytes it can contain.
     *
     * @return The capacity in bytes.
     */
    int capacity();

    /**
     * Get the current reader offset. The next read will happen from this byte offset into the buffer.
     *
     * @return The current reader offset.
     */
    int readerOffset();

    /**
     * Move the reader offset forward by the given delta.
     *
     * @param delta to accumulate.
     * @return This buffer instance.
     * @throws IndexOutOfBoundsException if the new reader offset is greater than the current
     * {@link #writerOffset()}.
     * @throws IllegalArgumentException if the given delta is negative.
     * @throws BufferClosedException if this buffer is closed.
     */
    default Buffer skipReadableBytes(int delta) {
        checkPositiveOrZero(delta, "delta");
        readerOffset(readerOffset() + delta);
        return this;
    }

    /**
     * Set the reader offset. Make the next read happen from the given offset into the buffer.
     *
     * @param offset The reader offset to set.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the specified {@code offset} is less than zero or greater than the current
     *                                   {@link #writerOffset()}.
     * @throws BufferClosedException if this buffer is closed.
     */
    Buffer readerOffset(int offset);

    /**
     * Get the current writer offset. The next write will happen at this byte offset into the buffer.
     *
     * @return The current writer offset.
     */
    int writerOffset();

    /**
     * Move the writer offset to ahead by the given delta.
     *
     * @param delta to accumulate.
     * @return This buffer instance.
     * @throws IndexOutOfBoundsException if the new writer offset is greater than {@link #capacity()}.
     * @throws IllegalArgumentException if the given delta is negative.
     * @throws BufferClosedException if this buffer is closed.
     * @throws BufferReadOnlyException if this buffer is {@linkplain #readOnly() read-only}.
     */
    default Buffer skipWritableBytes(int delta) {
        checkPositiveOrZero(delta, "delta");
        writerOffset(writerOffset() + delta);
        return this;
    }

    /**
     * Set the writer offset. Make the next write happen at the given offset.
     *
     * @param offset The writer offset to set.
     * @return This Buffer.
     * @throws IndexOutOfBoundsException if the specified {@code offset} is less than the current
     * {@link #readerOffset()} or greater than {@link #capacity()}.
     * @throws BufferClosedException if this buffer is closed.
     * @throws BufferReadOnlyException if this buffer is {@linkplain #readOnly() read-only}.
     */
    Buffer writerOffset(int offset);

    /**
     * Returns the number of readable bytes which is equal to {@code (writerOffset() - readerOffset())}.
     */
    default int readableBytes() {
        return writerOffset() - readerOffset();
    }

    /**
     * Returns the number of writable bytes which is equal to {@code (capacity() - writerOffset())}.
     * <p>
     * If the buffer is {@linkplain #readOnly() read-only}, then 0 is returned.
     */
    default int writableBytes() {
        return readOnly() ? 0 : capacity() - writerOffset();
    }

    /**
     * Fills the buffer with the given byte value. This method does not respect the {@link #readerOffset()} or {@link
     * #writerOffset()}, but copies the full capacity of the buffer. The {@link #readerOffset()} and {@link
     * #writerOffset()} are not modified.
     *
     * @param value The byte value to write at every offset in the buffer.
     * @return This Buffer.
     * @throws BufferReadOnlyException if this buffer is {@linkplain #readOnly() read-only}.
     */
    Buffer fill(byte value);

    /**
     * Makes this buffer read-only. This is irreversible.
     * This operation is also idempotent, so calling this method multiple times on the same buffer makes no difference.
     *
     * @return This buffer instance.
     */
    Buffer makeReadOnly();

    /**
     * Queries if this buffer is read-only or not.
     *
     * @return {@code true} if this buffer is read-only, {@code false} otherwise.
     */
    boolean readOnly();

    /**
     * Queries if this buffer is backed by native memory, or not.
     *
     * @return {@code true} if this buffer is backed by native, off-heap, memory. Otherwise, {@code false}, if this
     * buffer is backed by on-heap memory.
     */
    boolean isDirect();

    /**
     * Set an upper limit to the implicit capacity growth. Buffer {@code write*} methods may implicitly grow the buffer
     * capacity instead of throwing a bounds check exception. The implicit capacity limit restricts this growth so the
     * buffer capacity does not automatically grow beyond the given limit. When the limit is reached, and there is no
     * more writable space left, then the {@code write*} methods will start throwing exceptions.
     * <p>
     * The default limit is the maximum buffer size.
     * <p>
     * The limit is carried through {@link #send()} calls, but the buffer instances returned from the various
     * {@code split} and {@code copy} methods will have the default limit set.
     * <p>
     * The limit is not impacted by calls to {@code split} methods on this buffer. In other words, even though
     * {@code split} methods reduce the capacity of this buffer, the set limit, if any, remains the same.
     *
     * @param limit The maximum size this buffers capacity will implicitly grow to via {@code write*} methods.
     * @return This buffer instance.
     * @throws IndexOutOfBoundsException if the limit is negative, greater than the maximum buffer size, or if the
     *                                   {@linkplain #capacity() capacity} is already greater than the given limit.
     */
    Buffer implicitCapacityLimit(int limit);

    /**
     * Returns the implicit capacity limit of the buffer. If none was set before via {@link #implicitCapacityLimit(int)}
     * this method will return the default value.
     *
     * @return the limit.
     */
    int implicitCapacityLimit();

    /**
     * Copies the given length of data from this buffer into the given destination array, beginning at the given source
     * position in this buffer, and the given destination position in the destination array.
     * <p>
     * This method does not read or modify the {@linkplain #writerOffset() write offset} or the
     * {@linkplain #readerOffset() read offset}.
     *
     * @param srcPos The byte offset into this buffer from where the copying should start; the byte at this offset in
     *              this buffer will be copied to the {@code destPos} index in the {@code dest} array.
     * @param dest The destination byte array.
     * @param destPos The index into the {@code dest} array from where the copying should start.
     * @param length The number of bytes to copy.
     * @throws NullPointerException if the destination array is null.
     * @throws IndexOutOfBoundsException if the source or destination positions, or the length, are negative,
     * or if the resulting end positions reaches beyond the end of either this buffer, or the destination array.
     * @throws BufferClosedException if this buffer is closed.
     */
    void copyInto(int srcPos, byte[] dest, int destPos, int length);

    /**
     * Copies the given length of data from this buffer into the given destination byte buffer, beginning at the given
     * source position in this buffer, and the given destination position in the destination byte buffer.
     * <p>
     * This method does not read or modify the {@linkplain #writerOffset() write offset} or the
     * {@linkplain #readerOffset() read offset}, nor is the position of the destination buffer changed.
     * <p>
     * The position and limit of the destination byte buffer are also ignored, and do not influence {@code destPos}
     * or {@code length}.
     *
     * @param srcPos The byte offset into this buffer from where the copying should start; the byte at this offset in
     *              this buffer will be copied to the {@code destPos} index in the {@code dest} {@link ByteBuffer}.
     * @param dest The destination byte buffer.
     * @param destPos The index into the {@code dest} {@link ByteBuffer} from where the copying should start.
     * @param length The number of bytes to copy.
     * @throws NullPointerException if the destination buffer is null.
     * @throws IndexOutOfBoundsException if the source or destination positions, or the length, are negative,
     * or if the resulting end positions reach beyond the end of either this buffer or the destination
     * {@link ByteBuffer}.
     * @throws java.nio.ReadOnlyBufferException if the destination byte buffer is read-only.
     * @throws BufferClosedException if this buffer is closed.
     */
    void copyInto(int srcPos, ByteBuffer dest, int destPos, int length);

    /**
     * Copies the given length of data from this buffer into the given destination buffer, beginning at the given
     * source position in this buffer, and the given destination position in the destination buffer.
     * <p>
     * This method does not read or modify the {@linkplain #writerOffset() write offset} or the
     * {@linkplain #readerOffset() read offset} on this buffer, nor on the destination buffer.
     * <p>
     * The read and write offsets of the destination buffer are also ignored, and do not influence {@code destPos}
     * or {@code length}.
     *
     * @param srcPos The byte offset into this buffer from where the copying should start; the byte at this offset in
     *              this buffer will be copied to the {@code destPos} index in the {@code dest} buffer.
     * @param dest The destination buffer.
     * @param destPos The index into the {@code dest} buffer from where the copying should start.
     * @param length The number of bytes to copy.
     * @throws NullPointerException if the destination buffer is null.
     * @throws IndexOutOfBoundsException if the source or destination positions, or the length, are negative,
     * or if the resulting end positions reaches beyond the end of either this buffer, or the destination buffer.
     * @throws BufferReadOnlyException if the destination buffer is read-only.
     * @throws BufferClosedException if this or the destination buffer is closed.
     */
    void copyInto(int srcPos, Buffer dest, int destPos, int length);

    /**
     * Read from this buffer and write to the given channel.
     * The number of bytes actually written to the channel are returned.
     * No more than the given {@code length} of bytes, or the number of {@linkplain #readableBytes() readable bytes},
     * will be written to the channel, whichever is smaller.
     * If the channel has a position, then it will be advanced by the number of bytes written.
     * The {@linkplain #readerOffset() reader-offset} of this buffer will likewise be advanced by the number of bytes
     * written.
     *
     * @implNote {@linkplain CompositeBuffer composite buffers} may offer an optimized implementation of this method,
     * if the given channel implements {@link GatheringByteChannel}.
     *
     * @param channel The channel to write to.
     * @param length The maximum number of bytes to write.
     * @return The actual number of bytes written, possibly zero.
     * @throws IOException If the write-operation on the channel failed for some reason.
     */
    int transferTo(WritableByteChannel channel, int length) throws IOException;

    /**
     * Read from this buffer and write to the given channel at the given position.
     * The number of bytes actually written to the channel are returned.
     * No more than the given {@code length} of bytes, or the number of {@linkplain #readableBytes() readable bytes},
     * will be written to the channel, whichever is smaller.
     * The channel's position is not modified.
     * The {@linkplain #readerOffset() reader-offset} of this buffer will, however, be advanced by the number of bytes
     * written.
     *
     * @param channel The channel to write to.
     * @param position The file position.
     * @param length The maximum number of bytes to write.
     * @return The actual number of bytes written, possibly zero.
     * @throws IOException If the write-operation on the channel failed for some reason.
     */
    int transferTo(FileChannel channel, long position, int length) throws IOException;

    /**
     * Read from the given channel starting from the given position and write to this buffer.
     * The number of bytes actually read from the channel are returned, or -1 is returned if the channel has reached
     * the end-of-stream.
     * No more than the given {@code length} of bytes, or the number of {@linkplain #writableBytes() writable bytes},
     * will be read from the channel, whichever is smaller.
     * The channel's position is not modified.
     * The {@linkplain #writerOffset() writer-offset} of this buffer will likewise be advanced by the number of bytes
     * read.
     *
     * @param channel The channel to read from.
     * @param position The file position.
     * @param length The maximum number of bytes to read.
     * @return The actual number of bytes read, possibly zero, or -1 if the end-of-stream has been reached.
     * @throws IOException If the read-operation on the channel failed for some reason.
     */
    int transferFrom(FileChannel channel, long position, int length) throws IOException;

    /**
     * Read from the given channel and write to this buffer.
     * The number of bytes actually read from the channel are returned, or -1 is returned if the channel has reached
     * the end-of-stream.
     * No more than the given {@code length} of bytes, or the number of {@linkplain #writableBytes() writable bytes},
     * will be read from the channel, whichever is smaller.
     * If the channel has a position, then it will be advanced by the number of bytes read.
     * The {@linkplain #writerOffset() writer-offset} of this buffer will likewise be advanced by the number of bytes
     * read.
     *
     * @implNote {@linkplain CompositeBuffer composite buffers} may offer an optimized implementation of this method,
     * if the given channel implements {@link ScatteringByteChannel}.
     *
     * @param channel The channel to read from.
     * @param length The maximum number of bytes to read.
     * @return The actual number of bytes read, possibly zero, or -1 if the end-of-stream has been reached.
     * @throws IOException If the read-operation on the channel failed for some reason.
     */
    int transferFrom(ReadableByteChannel channel, int length) throws IOException;

    /**
     * Writes into this buffer, all the bytes from the given {@code source} using the passed {@code charset}.
     * This updates the {@linkplain #writerOffset() write offset} of this buffer.
     *
     * @param source {@link CharSequence} to read from.
     * @param charset {@link Charset} to use for writing.
     * @return This buffer.
     */
    default Buffer writeCharSequence(CharSequence source, Charset charset) {
        InternalBufferUtils.writeCharSequence(source, this, charset);
        return this;
    }

    /**
     * Reads a {@link CharSequence} of the passed {@code length} using the passed {@link Charset}.
     * This updates the {@linkplain #readerOffset() reader offset} of this buffer.
     *
     * @param length of {@link CharSequence} to read.
     * @param charset of the bytes to be read.
     * @return {@link CharSequence} read from this buffer.
     * @throws IndexOutOfBoundsException if the passed {@code length} is more than the {@linkplain #readableBytes()} of
     * this buffer.
     */
    default CharSequence readCharSequence(int length, Charset charset) {
        return InternalBufferUtils.readCharSequence(this, length, charset);
    }

    /**
     * Writes into this buffer, all the readable bytes from the given buffer.
     * This updates the {@linkplain #writerOffset() write offset} of this buffer, and the
     * {@linkplain #readerOffset() reader offset} of the given buffer.
     *
     * @param source The buffer to read from.
     * @return This buffer.
     * @throws NullPointerException If the source buffer is {@code null}.
     */
    default Buffer writeBytes(Buffer source) {
        int size = source.readableBytes();
        if (writableBytes() < size && writerOffset() + size <= implicitCapacityLimit()) {
            ensureWritable(size, 1, false);
        }
        int woff = writerOffset();
        source.copyInto(source.readerOffset(), this, woff, size);
        source.skipReadableBytes(size);
        skipWritableBytes(size);
        return this;
    }

    /**
     * Writes into this buffer, all the bytes from the given byte array.
     * This updates the {@linkplain #writerOffset() write offset} of this buffer by the length of the array.
     *
     * @param source The byte array to read from.
     * @return This buffer.
     */
    default Buffer writeBytes(byte[] source) {
        return writeBytes(source, 0, source.length);
    }

    /**
     * Writes into this buffer, the given number of bytes from the byte array.
     * This updates the {@linkplain #writerOffset() write offset} of this buffer by the length argument.
     *
     * @param source The byte array to read from.
     * @param srcPos Position in the {@code source} from where bytes should be written to this buffer.
     * @param length The number of bytes to copy.
     * @return This buffer.
     */
    default Buffer writeBytes(byte[] source, int srcPos, int length) {
        if (source.length < srcPos + length || srcPos < 0) {
            throw new ArrayIndexOutOfBoundsException();
        }
        if (writableBytes() < length && writerOffset() + length <= implicitCapacityLimit()) {
            ensureWritable(length, 1, false);
        }
        int woff = writerOffset();
        for (int i = 0; i < length; i++) {
            setByte(woff + i, source[srcPos + i]);
        }
        skipWritableBytes(length);
        return this;
    }

    /**
     * Writes into this buffer from the source {@link ByteBuffer}.
     * This updates the {@linkplain #writerOffset() write offset} of this buffer and also the position of
     * the source {@link ByteBuffer}.
     * <p>
     * Note: the behaviour is undefined if the given {@link ByteBuffer} is an alias for the memory in this buffer.
     *
     * @param source The {@link ByteBuffer} to read from.
     * @return This buffer.
     */
    default Buffer writeBytes(ByteBuffer source) {
        if (source.hasArray()) {
            writeBytes(source.array(), source.arrayOffset() + source.position(), source.remaining());
            source.position(source.limit());
        } else {
            int woff = writerOffset();
            int length = source.remaining();
            if (writableBytes() < length && woff + length <= implicitCapacityLimit()) {
                ensureWritable(length, 1, false);
            }
            writerOffset(woff + length);
            // Try to reduce bounds-checking by using long and int when possible.
            boolean needReverse = source.order() != ByteOrder.BIG_ENDIAN;
            for (; length >= Long.BYTES; length -= Long.BYTES, woff += Long.BYTES) {
                setLong(woff, needReverse ? Long.reverseBytes(source.getLong()) : source.getLong());
            }
            for (; length >= Integer.BYTES; length -= Integer.BYTES, woff += Integer.BYTES) {
                setInt(woff, needReverse ? Integer.reverseBytes(source.getInt()) : source.getInt());
            }
            for (; length > 0; length--, woff++) {
                setByte(woff, source.get());
            }
        }
        return this;
    }

    /**
     * Read from this buffer, into the destination {@link ByteBuffer}
     * This updates the {@linkplain #readerOffset() read offset} of this buffer and also the position of
     * the destination {@link ByteBuffer}.
     * <p>
     * Note: the behaviour is undefined if the given {@link ByteBuffer} is an alias for the memory in this buffer.
     *
     * @param destination The {@link ByteBuffer} to write into.
     * @return This buffer.
     */
    default Buffer readBytes(ByteBuffer destination) {
        int byteCount = destination.remaining();
        copyInto(readerOffset(), destination, destination.position(), byteCount);
        skipReadableBytes(byteCount);
        destination.position(destination.limit());
        return this;
    }

    /**
     * Read from this buffer, into the destination array, the given number of bytes.
     * This updates the {@linkplain #readerOffset() read offset} of this buffer by the length argument.
     *
     * @param destination The byte array to write into.
     * @param destPos Position in the {@code destination} to where bytes should be written from this buffer.
     * @param length The number of bytes to copy.
     * @return This buffer.
     */
    default Buffer readBytes(byte[] destination, int destPos, int length) {
        int roff = readerOffset();
        copyInto(roff, destination, destPos, length);
        readerOffset(roff + length);
        return this;
    }

    /**
     * Resets the {@linkplain #readerOffset() read offset} and the {@linkplain #writerOffset() write offset} on this
     * buffer to zero, and return this buffer.
     *
     * @return This buffer instance.
     */
    default Buffer resetOffsets() {
        readerOffset(0);
        if (!readOnly()) {
            writerOffset(0);
        }
        return this;
    }

    /**
     * Get the number of {@linkplain #readableBytes() readable bytes}, until the given {@code needle} is found in this
     * buffer.
     * If the needle is not found, {@code -1} is returned.
     * <p>
     * This method does not modify the {@linkplain #readerOffset() reader-offset} or the
     * {@linkplain #writerOffset() write-offset}.
     *
     * @param needle The byte value to search for.
     * @return The offset, relative to the current {@link #readerOffset()}, of the found value, or {@code -1} if none
     * was found.
     */
    int bytesBefore(byte needle);

    /**
     * Get the number of {@linkplain #readableBytes() readable bytes}, until the given {@code needle} is found in this
     * buffer.
     * The found offset will be the offset into this buffer, relative to its {@linkplain #readerOffset() reader-offset},
     * of the first byte of a sequence that matches all readable bytes in the given {@code needle} buffer.
     * If the needle is not found, {@code -1} is returned.
     * <p>
     * This method does not modify the {@linkplain #readerOffset() reader-offset} or the
     * {@linkplain #writerOffset() write-offset}.
     *
     * @param needle The buffer value to search for.
     * @return The offset, relative to the current {@link #readerOffset()}, of the found value, or {@code -1} if none
     * was found.
     */
    int bytesBefore(Buffer needle);

    /**
     * Opens a cursor to iterate the readable bytes of this buffer. The {@linkplain #readerOffset() reader offset} and
     * {@linkplain #writerOffset() writer offset} are not modified by the cursor.
     * <p>
     * Care should be taken to ensure that the buffer's lifetime extends beyond the cursor and the iteration, and that
     * the {@linkplain #readerOffset() reader offset} and {@linkplain #writerOffset() writer offset} are not modified
     * while the iteration takes place. Otherwise, unpredictable behaviour might result.
     *
     * @return A {@link ByteCursor} for iterating the readable bytes of this buffer.
     */
    ByteCursor openCursor();

    /**
     * Opens a cursor to iterate the given number bytes of this buffer, starting at the given offset.
     * The {@linkplain #readerOffset() reader offset} and {@linkplain #writerOffset() writer offset} are not modified by
     * the cursor.
     * <p>
     * Care should be taken to ensure that the buffer's lifetime extends beyond the cursor and the iteration, and that
     * the {@linkplain #readerOffset() reader offset} and {@linkplain #writerOffset() writer offset} are not modified
     * while the iteration takes place. Otherwise, unpredictable behaviour might result.
     *
     * @param fromOffset The offset into the buffer where iteration should start.
     *                  The first byte read from the iterator will be the byte at this offset.
     * @param length The number of bytes to iterate.
     * @return A {@link ByteCursor} for the given stretch of bytes of this buffer.
     * @throws IllegalArgumentException if the length is negative, or if the region given by the {@code fromOffset} and
     * the {@code length} reaches outside the bounds of this buffer.
     */
    ByteCursor openCursor(int fromOffset, int length);

    /**
     * Opens a cursor to iterate the readable bytes of this buffer, in reverse.
     * The {@linkplain #readerOffset() reader offset} and {@linkplain #writerOffset() writer offset} are not modified by
     * the cursor.
     * <p>
     * Care should be taken to ensure that the buffer's lifetime extends beyond the cursor and the iteration, and that
     * the {@linkplain #readerOffset() reader offset} and {@linkplain #writerOffset() writer offset} are not modified
     * while the iteration takes place. Otherwise, unpredictable behaviour might result.
     *
     * @return A {@link ByteCursor} for the readable bytes of this buffer.
     */
    default ByteCursor openReverseCursor() {
        int woff = writerOffset();
        return openReverseCursor(woff == 0? 0 : woff - 1, readableBytes());
    }

    /**
     * Opens a cursor to iterate the given number bytes of this buffer, in reverse, starting at the given offset.
     * The {@linkplain #readerOffset() reader offset} and {@linkplain #writerOffset() writer offset} are not modified by
     * the cursor.
     * <p>
     * Care should be taken to ensure that the buffer's lifetime extends beyond the cursor and the iteration, and that
     * the {@linkplain #readerOffset() reader offset} and {@linkplain #writerOffset() writer offset} are not modified
     * while the iteration takes place. Otherwise, unpredictable behaviour might result.
     *
     * @param fromOffset The offset into the buffer where iteration should start.
     *                  The first byte read from the iterator will be the byte at this offset.
     * @param length The number of bytes to iterate.
     * @return A {@link ByteCursor} for the given stretch of bytes of this buffer.
     * @throws IndexOutOfBoundsException if the length is negative, or if the region given by the {@code fromOffset} and
     * the {@code length} reaches outside the bounds of this buffer.
     */
    ByteCursor openReverseCursor(int fromOffset, int length);

    /**
     * Ensures that this buffer has at least the given number of bytes of
     * {@linkplain #writableBytes() available space for writing}.
     * If this buffer already has the necessary space, then this method returns immediately.
     * If this buffer does not already have the necessary space, then it will be expanded using the
     * {@link BufferAllocator} the buffer was created with.
     * This method is the same as calling {@link #ensureWritable(int, int, boolean)} where {@code allowCompaction} is
     * {@code true}.
     *
     * @param size The requested number of bytes of space that should be available for writing.
     * @return This buffer instance.
     * @throws IllegalStateException if this buffer is in a bad state.
     * @throws BufferClosedException if this buffer is closed.
     * @throws BufferReadOnlyException if this buffer is {@linkplain #readOnly() read-only}.
     */
    default Buffer ensureWritable(int size) {
        ensureWritable(size, capacity(), true);
        return this;
    }

    /**
     * Ensures that this buffer has at least the given number of bytes of
     * {@linkplain #writableBytes() available space for writing}.
     * If this buffer already has the necessary space, then this method returns immediately.
     * If this buffer does not already have the necessary space, then space will be made available in one or all of
     * the following available ways:
     *
     * <ul>
     *     <li>
     *         If {@code allowCompaction} is {@code true}, and sum of the read and writable bytes would be enough to
     *         satisfy the request, and it (depending on the buffer implementation) seems faster and easier to compact
     *         the existing buffer rather than allocation a new buffer, then the requested bytes will be made available
     *         that way. The compaction will not necessarily work the same way as the {@link #compact()} method, as the
     *         implementation may be able to make the requested bytes available with less effort than is strictly
     *         mandated by the {@link #compact()} method.
     *     </li>
     *     <li>
     *         Regardless of the value of the {@code allowCompaction}, the implementation may make more space available
     *         by just allocating more or larger buffers. This allocation would use the same {@link BufferAllocator}
     *         that this buffer was created with.
     *     </li>
     *     <li>
     *         If {@code allowCompaction} is {@code true}, then the implementation may choose to do a combination of
     *         compaction and allocation.
     *     </li>
     * </ul>
     *
     * @param size The requested number of bytes of space that should be available for writing.
     * @return This buffer instance.
     * @param minimumGrowth The minimum number of bytes to grow by. If it is determined that memory should be allocated
     *                     and copied, make sure that the new memory allocation is bigger than the old one by at least
     *                     this many bytes. This way, the buffer can grow by more than what is immediately necessary,
     *                     thus amortising the costs of allocating and copying.
     * @param allowCompaction {@code true} if the method is allowed to modify the
     *                                   {@linkplain #readerOffset() reader offset} and
     *                                   {@linkplain #writerOffset() writer offset}, otherwise {@code false}.
     * @throws BufferReadOnlyException if this buffer is {@linkplain #readOnly() read-only}.
     * @throws IllegalArgumentException if {@code size} or {@code minimumGrowth} are negative.
     * @throws IllegalStateException if this buffer is in a bad state.
     */
    Buffer ensureWritable(int size, int minimumGrowth, boolean allowCompaction);

    /**
     * Returns a copy of this buffer's readable bytes.
     * Modifying the content of the returned buffer will not affect this buffers contents.
     * The two buffers will maintain separate offsets. This method is identical to
     * {@code buf.copy(buf.readerOffset(), buf.readableBytes())}.
     * This method does not modify {@link #readerOffset()} or {@link #writerOffset()} of this buffer.
     * <p>
     * The copy is created with a {@linkplain #writerOffset() write offset} equal to the length of the copied data,
     * so that the entire contents of the copy is ready to be read.
     * <p>
     * The returned buffer will not be read-only, regardless of the {@linkplain #readOnly() read-only state} of this
     * buffer.
     *
     * @return A new buffer instance, with independent {@link #readerOffset()} and {@link #writerOffset()},
     * that contains a copy of the readable region of this buffer.
     * @throws BufferClosedException if this buffer is closed.
     */
    default Buffer copy() {
        int offset = readerOffset();
        int length = readableBytes();
        return copy(offset, length);
    }

    /**
     * Returns a copy of the given region of this buffer.
     * Modifying the content of the returned buffer will not affect this buffers contents.
     * The two buffers will maintain separate offsets.
     * This method does not modify {@link #readerOffset()} or {@link #writerOffset()} of this buffer.
     * <p>
     * The copy is created with a {@linkplain #writerOffset() write offset} equal to the length of the copy,
     * so that the entire contents of the copy is ready to be read.
     * <p>
     * The returned buffer will not be read-only, regardless of the {@linkplain #readOnly() read-only state} of this
     * buffer.
     * This has the same effect as calling {@link #copy(int, int, boolean)} with a {@code false} read-only argument.
     *
     * @param offset The offset where copying should start from. This is the offset of the first byte copied.
     * @param length The number of bytes to copy, and the capacity of the returned buffer.
     * @return A new buffer instance, with independent {@link #readerOffset()} and {@link #writerOffset()},
     * that contains a copy of the given region of this buffer.
     * @throws IllegalArgumentException if the {@code offset} or {@code length} reaches outside the bounds of the
     * buffer.
     * @throws BufferClosedException if this buffer is closed.
     */
    default Buffer copy(int offset, int length) {
        return copy(offset, length, false);
    }

    /**
     * Returns a copy of this buffer's readable bytes, with the given read-only setting.
     * Modifying the content of the returned buffer will not affect this buffers contents.
     * The two buffers will maintain separate offsets.
     * This method does not modify {@link #readerOffset()} or {@link #writerOffset()} of this buffer.
     * <p>
     * The copy is created with a {@linkplain #writerOffset() write offset} equal to the length of the copy,
     * so that the entire contents of the copy is ready to be read.
     * <p>
     * The returned buffer will be read-only if, and only if, the {@code readOnly} argument is {@code true}, and it
     * will not be read-only if the argument is {@code false}.
     * This is the case regardless of the {@linkplain #readOnly() read-only state} of this buffer.
     * <p>
     * If this buffer is read-only, and a read-only copy is requested, then implementations <em>may</em> use structural
     * sharing and have both buffers backed by the same underlying memory.
     *
     * @param readOnly The desired {@link #readOnly()} state of the returned buffer.
     * @return A new buffer instance, with independent {@link #readerOffset()} and {@link #writerOffset()},
     * that contains a copy of the given region of this buffer.
     * @throws IllegalArgumentException if the {@code offset} or {@code length} reaches outside the bounds of the
     * buffer.
     * @throws BufferClosedException if this buffer is closed.
     */
    default Buffer copy(boolean readOnly) {
        return copy(readerOffset(), readableBytes(), readOnly);
    }

    /**
     * Returns a copy of the given region of this buffer.
     * Modifying the content of the returned buffer will not affect this buffers contents.
     * The two buffers will maintain separate offsets.
     * This method does not modify {@link #readerOffset()} or {@link #writerOffset()} of this buffer.
     * <p>
     * The copy is created with a {@linkplain #writerOffset() write offset} equal to the length of the copy,
     * so that the entire contents of the copy is ready to be read.
     * <p>
     * The returned buffer will be read-only if, and only if, the {@code readOnly} argument is {@code true}, and it
     * will not be read-only if the argument is {@code false}.
     * This is the case regardless of the {@linkplain #readOnly() read-only state} of this buffer.
     * <p>
     * If this buffer is read-only, and a read-only copy is requested, then implementations <em>may</em> use structural
     * sharing and have both buffers backed by the same underlying memory.
     *
     * @param offset The offset where copying should start from. This is the offset of the first byte copied.
     * @param length The number of bytes to copy, and the capacity of the returned buffer.
     * @param readOnly The desired {@link #readOnly()} state of the returned buffer.
     * @return A new buffer instance, with independent {@link #readerOffset()} and {@link #writerOffset()},
     * that contains a copy of the given region of this buffer.
     * @throws IllegalArgumentException if the {@code offset} or {@code length} reaches outside the bounds of the
     * buffer.
     * @throws BufferClosedException if this buffer is closed.
     */
    Buffer copy(int offset, int length, boolean readOnly);

    /**
     * Splits the buffer into two, at {@code length} number of bytes from the current
     * {@linkplain #readerOffset() reader offset} position.
     * <p>
     * The region of this buffer that contain the previously read and readable bytes till the
     * {@code readerOffset() + length} position, will be captured and returned in a new buffer,
     * that will hold its own ownership of that region.
     * This allows the returned buffer to be independently {@linkplain #send() sent} to other threads.
     * <p>
     * The returned buffer will change its {@link #readerOffset()} to {@code readerOffset() + length}, and have its
     * {@link #writerOffset()} and {@link #capacity()} both set to the {@code readerOffset() + length} position.
     * <p>
     * The memory region in the returned buffer will become inaccessible through this buffer. This buffer will have its
     * capacity reduced by the capacity of the returned buffer, read offset will become zero and relative position of
     * write offset will be preserved from the provided {@code readerOffset() + length} position,
     * even though their position in memory remain unchanged.
     * <p>
     * Effectively, the following transformation takes place:
     * <pre>{@code
     *         This buffer, where offset = readerOffset() + length:
     *          +------------------------------------------+
     *         0|   |r/o    offset        |w/o             |cap
     *          +---+---------+-----------+----------------+
     *         /   /         / \          \                \
     *        /   /         /   \          \                \
     *       /   /         /     \          \                \
     *      /   /         /       \          \                \
     *     /   /         /         \          \                \
     *    +---+---------+           +----------+----------------+
     *    |   |r/o      |w/o & cap  |r/o      w/o               |cap
     *    +---+---------+           +---------------------------+
     *    Returned buffer.                   This buffer.
     * }</pre>
     * When the buffers are in this state, both of the split parts retain an atomic reference count on the
     * underlying memory. This means that shared underlying memory will not be deallocated or returned to a pool, until
     * all the split parts have been closed.
     * <p>
     * Composite buffers have it a little easier, in that at most only one of the constituent buffers will actually be
     * split. If the split point lands perfectly between two constituent buffers, then a composite buffer can
     * simply split its internal array in two.
     * <p>
     * Split buffers support all operations that normal buffers do, including {@link #ensureWritable(int)}.
     * <p>
     * See the <a href="#split">Splitting buffers</a> section for details.
     *
     * @return A new buffer with independent and exclusive ownership over the previously read and readable bytes from
     * this buffer.
     */
    default Buffer readSplit(int length) {
        return split(readerOffset() + length);
    }

    /**
     * Splits the buffer into two, at {@code length} number of bytes from the current
     * {@linkplain #writerOffset() writer offset} position.
     * <p>
     * The region of this buffer that contain the previously read and readable bytes till the
     * {@code writerOffset() + length} position, will be captured and returned in a new buffer,
     * that will hold its own ownership of that region.
     * This allows the returned buffer to be independently {@linkplain #send() sent} to other threads.
     * <p>
     * The returned buffer will change its {@link #writerOffset()} to {@code writerOffset() + length}, and have its
     * {@link #writerOffset()} and {@link #capacity()} both set to the {@code writerOffset() + length}.
     * <p>
     * The memory region in the returned buffer will become inaccessible through this buffer. This buffer will have its
     * capacity reduced by the capacity of the returned buffer, read offset will become zero and relative position of
     * write offset will be preserved from the provided {@code writerOffset() + length} position,
     * even though their position in memory remain unchanged.
     * <p>
     * Effectively, the following transformation takes place:
     * <pre>{@code
     *         This buffer, where offset = writerOffset() + length:
     *          +------------------------------------------+
     *         0|   |r/o  |w/o  offset                     |cap
     *          +---+----+-------+-------------------------+
     *         /   /    /       / \                        \
     *        /   /    /       /   \                        \
     *       /   /    /       /     \                        \
     *      /   /    /       /       \                        \
     *     /   /    /       /         \                        \
     *    +---+----+-------+           +------------------------+
     *    |   |r/o  |w/o   | cap       |r/o & w/o               |cap
     *    +---+----+-------+           +------------------------+
     *    Returned buffer.                   This buffer.
     * }</pre>
     * When the buffers are in this state, both of the split parts retain an atomic reference count on the
     * underlying memory. This means that shared underlying memory will not be deallocated or returned to a pool, until
     * all the split parts have been closed.
     * <p>
     * Composite buffers have it a little easier, in that at most only one of the constituent buffers will actually be
     * split. If the split point lands perfectly between two constituent buffers, then a composite buffer can
     * simply split its internal array in two.
     * <p>
     * Split buffers support all operations that normal buffers do, including {@link #ensureWritable(int)}.
     * <p>
     * See the <a href="#split">Splitting buffers</a> section for details.
     *
     * @return A new buffer with independent and exclusive ownership over the previously read and readable bytes from
     * this buffer.
     */
    default Buffer writeSplit(int length) {
        return split(writerOffset() + length);
    }

    /**
     * Splits the buffer into two, at the {@linkplain #writerOffset() write offset} position.
     * <p>
     * The region of this buffer that contain the previously read and readable bytes, will be captured and returned in
     * a new buffer, that will hold its own ownership of that region. This allows the returned buffer to be
     * independently {@linkplain #send() sent} to other threads.
     * <p>
     * The returned buffer will adopt the {@link #readerOffset()} of this buffer, and have its {@link #writerOffset()}
     * and {@link #capacity()} both set to the equal to the write-offset of this buffer.
     * <p>
     * The memory region in the returned buffer will become inaccessible through this buffer. This buffer will have its
     * capacity reduced by the capacity of the returned buffer, and the read and write offsets of this buffer will both
     * become zero, even though their position in memory remain unchanged.
     * <p>
     * Effectively, the following transformation takes place:
     * <pre>{@code
     *         This buffer:
     *          +------------------------------------------+
     *         0|   |r/o                  |w/o             |cap
     *          +---+---------------------+----------------+
     *         /   /                     / \               \
     *        /   /                     /   \               \
     *       /   /                     /     \               \
     *      /   /                     /       \               \
     *     /   /                     /         \               \
     *    +---+---------------------+           +---------------+
     *    |   |r/o                  |w/o & cap  |r/o & w/o      |cap
     *    +---+---------------------+           +---------------+
     *    Returned buffer.                      This buffer.
     * }</pre>
     * When the buffers are in this state, both of the split parts retain an atomic reference count on the
     * underlying memory. This means that shared underlying memory will not be deallocated or returned to a pool, until
     * all the split parts have been closed.
     * <p>
     * Composite buffers have it a little easier, in that at most only one of the constituent buffers will actually be
     * split. If the split point lands perfectly between two constituent buffers, then a composite buffer can
     * simply split its internal array in two.
     * <p>
     * Split buffers support all operations that normal buffers do, including {@link #ensureWritable(int)}.
     * <p>
     * See the <a href="#split">Splitting buffers</a> section for details.
     *
     * @return A new buffer with independent and exclusive ownership over the previously read and readable bytes from
     * this buffer.
     */
    default Buffer split() {
        return split(writerOffset());
    }

    /**
     * Splits the buffer into two, at the given {@code splitOffset}.
     * <p>
     * The region of this buffer that precede the {@code splitOffset}, will be captured and returned in a new
     * buffer, that will hold its own ownership of that region. This allows the returned buffer to be independently
     * {@linkplain #send() sent} to other threads.
     * <p>
     * The returned buffer will adopt the {@link #readerOffset()} and {@link #writerOffset()} of this buffer,
     * but truncated to fit within the capacity dictated by the {@code splitOffset}.
     * <p>
     * The memory region in the returned buffer will become inaccessible through this buffer. If the
     * {@link #readerOffset()} or {@link #writerOffset()} of this buffer lie prior to the {@code splitOffset},
     * then those offsets will be moved forward, so they land on offset 0 after the split.
     * <p>
     * Effectively, the following transformation takes place:
     * <pre>{@code
     *         This buffer:
     *          +--------------------------------+
     *         0|               |splitOffset     |cap
     *          +---------------+----------------+
     *         /               / \               \
     *        /               /   \               \
     *       /               /     \               \
     *      /               /       \               \
     *     /               /         \               \
     *    +---------------+           +---------------+
     *    |               |cap        |               |cap
     *    +---------------+           +---------------+
     *    Returned buffer.            This buffer.
     * }</pre>
     * When the buffers are in this state, both of the split parts retain an atomic reference count on the
     * underlying memory. This means that shared underlying memory will not be deallocated or returned to a pool, until
     * all the split parts have been closed.
     * <p>
     * Composite buffers have it a little easier, in that at most only one of the constituent buffers will actually be
     * split. If the split point lands perfectly between two constituent buffers, then a composite buffer can
     * simply split its internal array in two.
     * <p>
     * Split buffers support all operations that normal buffers do, including {@link #ensureWritable(int)}.
     * <p>
     * See the <a href="#split">Splitting buffers</a> section for details.
     *
     * @param splitOffset The offset into this buffer where it should be split. After the split, the data at this offset
     *                    will be at offset zero in this buffer.
     * @return A new buffer with independent and exclusive ownership over the bytes from the beginning to the given
     * offset of this buffer.
     */
    Buffer split(int splitOffset);

    /**
     * Discards the read bytes, and moves the buffer contents to the beginning of the buffer.
     *
     * @return This buffer instance.
     * @throws BufferReadOnlyException if this buffer is {@linkplain #readOnly() read-only}.
     * @throws IllegalStateException if this buffer is in a bad state.
     */
    Buffer compact();

    /**
     * Get the number of {@linkplain BufferComponent components} in this buffer.
     * For composite buffers, this is the number of transitive constituent buffers,
     * while non-composite buffers only have one component.
     *
     * @return The number of components in this buffer.
     */
    int countComponents();

    /**
     * Get the number of "components" in this buffer, that are readable. These are the components that would be
     * processed by {@link #forEachComponent()}, by following the {@link ComponentIterator#firstReadable()} and
     * {@link Next#nextReadable()} methods. For composite buffers, this is the number of transitive constituent buffers
     * that have readable data, while non-composite buffers only have at most one readable component.
     * <p>
     * The number of readable components may be less than the {@link #countComponents() component count}, if not all of
     * them have readable data.
     *
     * @return The number of readable components in this buffer.
     */
    int countReadableComponents();

    /**
     * Get the number of "components" in this buffer, that are writable. These are the components that would be
     * processed by {@link #forEachComponent()}, by following the {@link ComponentIterator#firstWritable()} and
     * {@link Next#nextWritable()} methods. For composite buffers, this is the number of transitive constituent buffers
     * that are writable, while non-composite buffers only have at most one writable component.
     * <p>
     * The number of writable components may be less than the {@link #countComponents() component count}, if not all of
     * them have space for writing.
     *
     * @return The number of writable components in this buffer.
     */
    int countWritableComponents();

    /**
     * Create a {@linkplain ComponentIterator component iterator} for all components in this buffer.
     * <p>
     * This API permits external iteration of the internal components of the buffer,
     * while at the same time protecting the life-cycle of the buffer.
     * <p>
     * The typical code pattern for using this API looks like the following:
     * <pre>{@code
     *      try (var iteration = buffer.forEachReadable()) {
     *          for (var c = iteration.first(); c != null; c = c.next()) {
     *              ByteBuffer componentBuffer = c.readableBuffer();
     *              // ...
     *          }
     *      }
     * }</pre>
     * Note the use of the {@code var} keyword for local variables, which are required for correctly expressing the
     * generic types used in the iteration.
     * Following this code pattern will ensure that the components, and their parent buffer, will be correctly
     * life-cycled.
     * <p>
     * <strong>Note</strong> that the {@link BufferComponent} instances exposed by the iterator could be reused for
     * multiple calls, so the data must be extracted from the component in the context of the iteration.
     * <p>
     * The {@link ByteBuffer} instances obtained from the component, share lifetime with that internal component.
     * This means they can be accessed as long as the internal memory store remain unchanged. Methods that may cause
     * such changes are {@link #split(int)}, {@link #split()}, {@link #readSplit(int)}, {@link #writeSplit(int)},
     * {@link #compact()}, {@link #ensureWritable(int)}, {@link #ensureWritable(int, int, boolean)},
     * and {@link #send()}.
     * <p>
     * The best way to ensure this doesn't cause any trouble, is to use the buffers directly as part of the iteration.
     * <p>
     * <strong>Note</strong> that the arrays, memory addresses, and byte buffers exposed as components by this method,
     * should not be used for changing the buffer contents. Doing so may cause undefined behaviour.
     * <p>
     * Changes to position and limit of the byte buffers exposed via the processed components, are not reflected back to
     * this buffer instance.
     *
     * @return A component iterator of {@linkplain BufferComponent readable components}.
     * @param <T> An intersection type that presents both the {@link BufferComponent} interface,
     *          <em>and</em> the ability to progress the iteration via the {@link Next#next()} method.
     */
    <T extends BufferComponent & ComponentIterator.Next> ComponentIterator<T> forEachComponent();

    /**
     * Decodes this buffer's readable bytes into a string with the specified {@linkplain Charset}.
     * <p>
     * This method does not modify the reader or writer offset of this buffer.
     *
     * @param charset used for decoding.
     * @return Buffer's readable bytes as a string.
     */
    default String toString(Charset charset) {
        return InternalBufferUtils.toString(this, charset);
    }
}
