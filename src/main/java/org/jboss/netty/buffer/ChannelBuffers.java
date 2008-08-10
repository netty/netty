/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @author tags. See the COPYRIGHT.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.netty.buffer;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.UnsupportedCharsetException;


/**
 * Creates a new {@link ChannelBuffer} by allocating new space or by wrapping
 * or copying existing byte arrays, byte buffer and string.
 *
 * <h3>Use static import</h3>
 * This classes is intended to be used with Java 5 static import statement:
 *
 * <pre>
 * import static org.jboss.netty.buffer.ChannelBuffers.*;
 *
 * ChannelBuffer heapBuffer = buffer(128);
 * ChannelBuffer directBuffer = directBuffer(256);
 * ChannelBuffer dynamicBuffer = dynamicBuffer(512);
 * ChannelBuffer wrappedBuffer = wrappedBuffer(new byte[128], new byte[256]);
 * ChannelBuffer copiedBuffer = copiedBuffer(ByteBuffer.allocate(128));
 * </pre>
 *
 * <h3>Allocating a new buffer</h3>
 *
 * Three buffer types are provided out of the box.
 *
 * <ul>
 * <li>{@link #buffer(int)} allocates a new fixed-capacity heap buffer.</li>
 * <li>{@link #directBuffer(int)} allocates a new fixed-capacity direct buffer.</li>
 * <li>{@link #dynamicBuffer(int)} allocates a new dynamic-capacity heap
 *     buffer, whose capacity increases automatically as needed by a write
 *     operation.</li>
 * </ul>
 *
 * <h3>Creating a wrapped buffer</h3>
 *
 * Wrapped buffer is a buffer which is a view of one or more existing
 * byte arrays and byte buffers.  Any changes in the content of the original
 * array or buffer will be reflected in the wrapped buffer.  Various wrapper
 * methods are provided and their name is all {@code wrappedBuffer()}.
 * You might want to take a look at this method closely if you want to create
 * a buffer which is composed of more than one array to reduce the number of
 * memory copy.
 *
 * <h3>Creating a copied buffer</h3>
 *
 * Copied buffer is a deep copy of one or more existing byte arrays, byte
 * buffers or a string.  Unlike a wrapped buffer, there's no shared data
 * between the original data and the copied buffer.  Various copy methods are
 * provided and their name is all {@code copiedBuffer()}.  It's also convenient
 * to use this operation to merge multiple buffers into one buffer.
 *
 * <h3>Miscellaneous utility methods</h3>
 *
 * This class also provides various utility methods to help implementation
 * of a new buffer type, generation of hex dump and swapping an integer's
 * byte order.
 *
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 *
 * @version $Rev$, $Date$
 *
 * @apiviz.landmark
 * @apiviz.has org.jboss.netty.buffer.ChannelBuffer oneway - - creates
 */
public class ChannelBuffers {

    /**
     * Big endian byte order.
     */
    public static final ByteOrder BIG_ENDIAN = ByteOrder.BIG_ENDIAN;

    /**
     * Little endian byte order.
     */
    public static final ByteOrder LITTLE_ENDIAN = ByteOrder.LITTLE_ENDIAN;

    private static final char[][] HEXDUMP_TABLE = new char[65536][];

    static {
        for (int i = 0; i < 65536; i ++) {
            HEXDUMP_TABLE[i] = String.format("%04x", i).toCharArray();
        }
    }

    /**
     * Creates a new big-endian Java heap buffer with the specified
     * {@code capacity}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer buffer(int capacity) {
        return buffer(BIG_ENDIAN, capacity);
    }

    /**
     * Creates a new Java heap buffer with the specified {@code endianness}
     * and {@code capacity}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer buffer(ByteOrder endianness, int capacity) {
        if (capacity == 0) {
            return ChannelBuffer.EMPTY_BUFFER;
        }
        if (endianness == BIG_ENDIAN) {
            return new BigEndianHeapChannelBuffer(capacity);
        } else if (endianness == LITTLE_ENDIAN) {
            return new LittleEndianHeapChannelBuffer(capacity);
        } else {
            throw new NullPointerException("endianness");
        }
    }

    /**
     * Creates a new big-endian direct buffer with the specified
     * {@code capacity}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer directBuffer(int capacity) {
        return directBuffer(BIG_ENDIAN, capacity);
    }

    /**
     * Creates a new direct buffer with the specified {@code endianness} and
     * {@code capacity}.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer directBuffer(ByteOrder endianness, int capacity) {
        if (capacity == 0) {
            return ChannelBuffer.EMPTY_BUFFER;
        }
        ChannelBuffer buffer = new ByteBufferBackedChannelBuffer(
                ByteBuffer.allocateDirect(capacity).order(endianness));
        buffer.clear();
        return buffer;
    }

    /**
     * Creates a new big-endian dynamic buffer whose estimated data length is
     * {@code 256} bytes.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer dynamicBuffer() {
        return dynamicBuffer(BIG_ENDIAN, 256);
    }

    /**
     * Creates a new big-endian dynamic buffer with the specified estimated
     * data length.  More accurate estimation yields less unexpected
     * reallocation overhead.  The new buffer's {@code readerIndex} and
     * {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer dynamicBuffer(int estimatedLength) {
        return dynamicBuffer(BIG_ENDIAN, estimatedLength);
    }

    /**
     * Creates a new dynamic buffer with the specified endianness and
     * the specified estimated data length.  More accurate estimation yields
     * less unexpected reallocation overhead.  The new buffer's
     * {@code readerIndex} and {@code writerIndex} are {@code 0}.
     */
    public static ChannelBuffer dynamicBuffer(ByteOrder endianness, int estimatedLength) {
        return new DynamicChannelBuffer(endianness, estimatedLength);
    }

    /**
     * Creates a new big-endian buffer which wraps the specified {@code array}.
     * A modification on the specified array's content will be visible to the
     * returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(byte[] array) {
        return wrappedBuffer(BIG_ENDIAN, array);
    }

    /**
     * Creates a new buffer which wraps the specified {@code array} with the
     * specified {@code endianness}.  A modification on the specified array's
     * content will be visible to the returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(ByteOrder endianness, byte[] array) {
        if (array.length == 0) {
            return ChannelBuffer.EMPTY_BUFFER;
        }
        if (endianness == BIG_ENDIAN) {
            return new BigEndianHeapChannelBuffer(array);
        } else if (endianness == LITTLE_ENDIAN) {
            return new LittleEndianHeapChannelBuffer(array);
        } else {
            throw new NullPointerException("endianness");
        }
    }

    /**
     * Creates a new big-endian buffer which wraps the sub-region of the
     * specified {@code array}.  A modification on the specified array's
     * content will be visible to the returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(byte[] array, int offset, int length) {
        return wrappedBuffer(BIG_ENDIAN, array, offset, length);
    }

    /**
     * Creates a new buffer which wraps the sub-region of the specified
     * {@code array} with the specified {@code endianness}.  A modification on
     * the specified array's content will be visible to the returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(ByteOrder endianness, byte[] array, int offset, int length) {
        if (length == 0) {
            return ChannelBuffer.EMPTY_BUFFER;
        }
        if (offset == 0) {
            if (length == array.length) {
                return wrappedBuffer(endianness, array);
            } else {
                return new TruncatedChannelBuffer(wrappedBuffer(endianness, array), length);
            }
        } else {
            return new SlicedChannelBuffer(wrappedBuffer(endianness, array), offset, length);
        }
    }

    /**
     * Creates a new buffer which wraps the specified NIO buffer's current
     * slice.  A modification on the specified buffer's content and endianness
     * will be visible to the returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return ChannelBuffer.EMPTY_BUFFER;
        }
        if (!buffer.isReadOnly() && buffer.hasArray()) {
            return wrappedBuffer(buffer.array(), buffer.arrayOffset(),buffer.remaining());
        } else {
            return new ByteBufferBackedChannelBuffer(buffer);
        }
    }

    /**
     * Creates a new buffer which wraps the specified buffer's readable bytes.
     * A modification on the specified buffer's content will be visible to the
     * returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(ChannelBuffer buffer) {
        if (buffer.readable()) {
            return buffer.slice();
        } else {
            return ChannelBuffer.EMPTY_BUFFER;
        }
    }

    /**
     * Creates a new big-endian composite buffer which wraps the specified
     * arrays without copying them.  A modification on the specified arrays'
     * content will be visible to the returned buffer.
     */
    public static ChannelBuffer wrappedBuffer(byte[]... arrays) {
        return wrappedBuffer(BIG_ENDIAN, arrays);
    }

    /**
     * Creates a new composite buffer which wraps the specified arrays without
     * copying them.  A modification on the specified arrays' content will be
     * visible to the returned buffer.
     *
     * @param endianness the endianness of the new buffer
     */
    public static ChannelBuffer wrappedBuffer(ByteOrder endianness, byte[]... arrays) {
        switch (arrays.length) {
        case 0:
            return ChannelBuffer.EMPTY_BUFFER;
        case 1:
            return wrappedBuffer(endianness, arrays[0]);
        }
        ChannelBuffer[] wrappedBuffers = new ChannelBuffer[arrays.length];
        for (int i = 0; i < arrays.length; i ++) {
            wrappedBuffers[i] = wrappedBuffer(endianness, arrays[i]);
        }
        return wrappedBuffer(wrappedBuffers);
    }

    /**
     * Creates a new composite buffer which wraps the specified buffers without
     * copying them.  A modification on the specified buffers' content will be
     * visible to the returned buffer.
     *
     * @throws IllegalArgumentException
     *         if the specified buffers' endianness are different from each
     *         other
     */
    public static ChannelBuffer wrappedBuffer(ChannelBuffer... buffers) {
        switch (buffers.length) {
        case 0:
            return ChannelBuffer.EMPTY_BUFFER;
        case 1:
            return wrappedBuffer(buffers[0]);
        default:
            return new CompositeChannelBuffer(buffers);
        }
    }

    /**
     * Creates a new composite buffer which wraps the specified NIO buffers
     * without copying them.  A modification on the specified buffers' content
     * will be visible to the returned buffer.
     *
     * @throws IllegalArgumentException
     *         if the specified buffers' endianness are different from each
     *         other
     */
    public static ChannelBuffer wrappedBuffer(ByteBuffer... buffers) {
        switch (buffers.length) {
        case 0:
            return ChannelBuffer.EMPTY_BUFFER;
        case 1:
            return wrappedBuffer(buffers[0]);
        }
        ChannelBuffer[] wrappedBuffers = new ChannelBuffer[buffers.length];
        for (int i = 0; i < buffers.length; i ++) {
            wrappedBuffers[i] = wrappedBuffer(buffers[i]);
        }
        return wrappedBuffer(wrappedBuffers);
    }

    public static ChannelBuffer copiedBuffer(byte[] array) {
        return copiedBuffer(BIG_ENDIAN, array);
    }

    public static ChannelBuffer copiedBuffer(ByteOrder endianness, byte[] array) {
        if (array.length == 0) {
            return ChannelBuffer.EMPTY_BUFFER;
        }
        if (endianness == BIG_ENDIAN) {
            return new BigEndianHeapChannelBuffer(array.clone());
        } else if (endianness == LITTLE_ENDIAN) {
            return new LittleEndianHeapChannelBuffer(array.clone());
        } else {
            throw new NullPointerException("endianness");
        }
    }

    public static ChannelBuffer copiedBuffer(byte[] array, int offset, int length) {
        return copiedBuffer(BIG_ENDIAN, array, offset, length);
    }

    public static ChannelBuffer copiedBuffer(ByteOrder endianness, byte[] array, int offset, int length) {
        if (length == 0) {
            return ChannelBuffer.EMPTY_BUFFER;
        }
        byte[] copy = new byte[length];
        System.arraycopy(array, offset, copy, 0, length);
        return wrappedBuffer(endianness, copy);
    }

    public static ChannelBuffer copiedBuffer(ByteBuffer buffer) {
        int length = buffer.remaining();
        if (length == 0) {
            return ChannelBuffer.EMPTY_BUFFER;
        }
        byte[] copy = new byte[length];
        int position = buffer.position();
        try {
            buffer.get(copy);
        } finally {
            buffer.position(position);
        }
        return wrappedBuffer(buffer.order(), copy);
    }

    public static ChannelBuffer copiedBuffer(ChannelBuffer buffer) {
        return buffer.copy();
    }

    public static ChannelBuffer copiedBuffer(byte[]... arrays) {
        return copiedBuffer(BIG_ENDIAN, arrays);
    }

    public static ChannelBuffer copiedBuffer(ByteOrder endianness, byte[]... arrays) {
        switch (arrays.length) {
        case 0:
            return ChannelBuffer.EMPTY_BUFFER;
        case 1:
            return copiedBuffer(endianness, arrays[0]);
        }

        // Merge the specified arrays into one array.
        int length = 0;
        for (byte[] a: arrays) {
            if (Integer.MAX_VALUE - length < a.length) {
                throw new IllegalArgumentException(
                        "The total length of the specified arrays is too big.");
            }
            length += a.length;
        }

        byte[] mergedArray = new byte[length];
        for (int i = 0, j = 0; i < arrays.length; i ++) {
            byte[] a = arrays[i];
            System.arraycopy(a, 0, mergedArray, j, a.length);
            j += a.length;
        }

        return wrappedBuffer(endianness, mergedArray);
    }

    public static ChannelBuffer copiedBuffer(ChannelBuffer... buffers) {
        switch (buffers.length) {
        case 0:
            return ChannelBuffer.EMPTY_BUFFER;
        case 1:
            return copiedBuffer(buffers[0]);
        }

        ChannelBuffer[] copiedBuffers = new ChannelBuffer[buffers.length];
        for (int i = 0; i < buffers.length; i ++) {
            copiedBuffers[i] = buffers[i].copy();
        }
        return wrappedBuffer(copiedBuffers);
    }

    public static ChannelBuffer copiedBuffer(ByteBuffer... buffers) {
        switch (buffers.length) {
        case 0:
            return ChannelBuffer.EMPTY_BUFFER;
        case 1:
            return copiedBuffer(buffers[0]);
        }

        ChannelBuffer[] copiedBuffers = new ChannelBuffer[buffers.length];
        for (int i = 0; i < buffers.length; i ++) {
            copiedBuffers[i] = wrappedBuffer(buffers[i]).copy();
        }
        return wrappedBuffer(copiedBuffers);
    }

    public static ChannelBuffer copiedBuffer(String string, String charsetName) {
        try {
            return wrappedBuffer(string.getBytes(charsetName));
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedCharsetException(charsetName);
        }
    }

    public static ChannelBuffer unmodifiableBuffer(ChannelBuffer buffer) {
        if (buffer instanceof ReadOnlyChannelBuffer) {
            buffer = ((ReadOnlyChannelBuffer) buffer).unwrap();
        }
        return new ReadOnlyChannelBuffer(buffer);
    }

    public static String hexDump(ChannelBuffer buffer) {
        return hexDump(buffer, buffer.readerIndex(), buffer.readableBytes());
    }

    public static String hexDump(ChannelBuffer buffer, int fromIndex, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length: " + length);
        }
        if (length == 0) {
            return "";
        }

        int endIndex = fromIndex + (length >>> 1 << 1);
        boolean oddLength = length % 2 != 0;
        char[] buf = new char[length << 1];

        int srcIdx = fromIndex;
        int dstIdx = 0;
        for (; srcIdx < endIndex; srcIdx += 2, dstIdx += 4) {
            System.arraycopy(
                    HEXDUMP_TABLE[buffer.getShort(srcIdx) & 0xFFFF],
                    0, buf, dstIdx, 4);
        }

        if (oddLength) {
            System.arraycopy(
                    HEXDUMP_TABLE[buffer.getByte(srcIdx) & 0xFF],
                    2, buf, dstIdx, 2);
        }

        return new String(buf);
    }

    public static int hashCode(ChannelBuffer buffer) {
        final int aLen = buffer.readableBytes();
        final int intCount = aLen >>> 2;
        final int byteCount = aLen & 3;

        int hashCode = 1;
        int arrayIndex = buffer.readerIndex();
        for (int i = intCount; i > 0; i --) {
            hashCode = 31 * hashCode + buffer.getInt(arrayIndex);
            arrayIndex += 4;
        }
        for (int i = byteCount; i > 0; i --) {
            hashCode = 31 * hashCode + buffer.getByte(arrayIndex ++);
        }

        if (hashCode == 0) {
            hashCode = 1;
        }
        return hashCode;
    }

    public static boolean equals(ChannelBuffer bufferA, ChannelBuffer bufferB) {
        final int aLen = bufferA.readableBytes();
        if (aLen != bufferB.readableBytes()) {
            return false;
        }

        final int longCount = aLen >>> 3;
        final int byteCount = aLen & 7;

        int aIndex = bufferA.readerIndex();
        int bIndex = bufferB.readerIndex();
        for (int i = longCount; i > 0; i --) {
            if (bufferA.getLong(aIndex) != bufferB.getLong(bIndex)) {
                return false;
            }
            aIndex += 8;
            bIndex += 8;
        }

        for (int i = byteCount; i > 0; i --) {
            if (bufferA.getByte(aIndex) != bufferB.getByte(bIndex)) {
                return false;
            }
            aIndex ++;
            bIndex ++;
        }

        return true;
    }

    public static int compare(ChannelBuffer bufferA, ChannelBuffer bufferB) {
        final int aLen = bufferA.readableBytes();
        final int bLen = bufferB.readableBytes();
        final int minLength = Math.min(aLen, bLen);
        final int longCount = minLength >>> 3;
        final int byteCount = minLength & 7;

        int aIndex = bufferA.readerIndex();
        int bIndex = bufferB.readerIndex();
        for (int i = longCount; i > 0; i --) {
            long va = bufferA.getLong(aIndex);
            long vb = bufferB.getLong(bIndex);
            if (va > vb) {
                return 1;
            } else if (va < vb) {
                return -1;
            }
            aIndex += 8;
            bIndex += 8;
        }

        for (int i = byteCount; i > 0; i --) {
            byte va = bufferA.getByte(aIndex);
            byte vb = bufferB.getByte(bIndex);
            if (va > vb) {
                return 1;
            } else if (va < vb) {
                return -1;
            }
            aIndex ++;
            bIndex ++;
        }

        return aLen - bLen;
    }

    public static int indexOf(ChannelBuffer buffer, int fromIndex, int toIndex, byte value) {
        if (fromIndex <= toIndex) {
            return firstIndexOf(buffer, fromIndex, toIndex, value);
        } else {
            return lastIndexOf(buffer, fromIndex, toIndex, value);
        }
    }

    public static int indexOf(ChannelBuffer buffer, int fromIndex, int toIndex, ChannelBufferIndexFinder indexFinder) {
        if (fromIndex <= toIndex) {
            return firstIndexOf(buffer, fromIndex, toIndex, indexFinder);
        } else {
            return lastIndexOf(buffer, fromIndex, toIndex, indexFinder);
        }
    }

    public static short swapShort(short value) {
        return (short) (value << 8 | value >>> 8 & 0xff);
    }

    public static int swapMedium(int value) {
        return value << 16 & 0xff0000 | value & 0xff00 | value >>> 16 & 0xff;
    }

    public static int swapInt(int value) {
        return swapShort((short) value) <<  16 |
               swapShort((short) (value >>> 16)) & 0xffff;
    }

    public static long swapLong(long value) {
        return (long) swapInt((int) value) <<  32 |
                      swapInt((int) (value >>> 32)) & 0xffffffffL;
    }

    private static int firstIndexOf(ChannelBuffer buffer, int fromIndex, int toIndex, byte value) {
        fromIndex = Math.max(fromIndex, 0);
        if (fromIndex >= toIndex || buffer.capacity() == 0) {
            return -1;
        }

        for (int i = fromIndex; i < toIndex; i ++) {
            if (buffer.getByte(i) == value) {
                return i;
            }
        }

        return -1;
    }

    private static int lastIndexOf(ChannelBuffer buffer, int fromIndex, int toIndex, byte value) {
        fromIndex = Math.min(fromIndex, buffer.capacity());
        if (fromIndex < 0 || buffer.capacity() == 0) {
            return -1;
        }

        for (int i = fromIndex - 1; i >= toIndex; i --) {
            if (buffer.getByte(i) == value) {
                return i;
            }
        }

        return -1;
    }

    private static int firstIndexOf(ChannelBuffer buffer, int fromIndex, int toIndex, ChannelBufferIndexFinder indexFinder) {
        fromIndex = Math.max(fromIndex, 0);
        if (fromIndex >= toIndex || buffer.capacity() == 0) {
            return -1;
        }

        for (int i = fromIndex; i < toIndex; i ++) {
            if (indexFinder.find(buffer, i)) {
                return i;
            }
        }

        return -1;
    }

    private static int lastIndexOf(ChannelBuffer buffer, int fromIndex, int toIndex, ChannelBufferIndexFinder indexFinder) {
        fromIndex = Math.min(fromIndex, buffer.capacity());
        if (fromIndex < 0 || buffer.capacity() == 0) {
            return -1;
        }

        for (int i = fromIndex - 1; i >= toIndex; i --) {
            if (indexFinder.find(buffer, i)) {
                return i;
            }
        }

        return -1;
    }

    private ChannelBuffers() {
        // Unused
    }
}
