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

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.util.ByteString;
import io.netty.util.CharsetUtil;
import io.netty.util.Recycler;
import io.netty.util.Recycler.Handle;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.Arrays;
import java.util.Locale;

/**
 * A collection of utility methods that is related with handling {@link ByteBuf},
 * such as the generation of hex dump and swapping an integer's byte order.
 */
public final class ByteBufUtil {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ByteBufUtil.class);

    private static final char[] HEXDUMP_TABLE = new char[256 * 4];

    static final ByteBufAllocator DEFAULT_ALLOCATOR;

    private static final int THREAD_LOCAL_BUFFER_SIZE;

    static {
        final char[] DIGITS = "0123456789abcdef".toCharArray();
        for (int i = 0; i < 256; i ++) {
            HEXDUMP_TABLE[ i << 1     ] = DIGITS[i >>> 4 & 0x0F];
            HEXDUMP_TABLE[(i << 1) + 1] = DIGITS[i       & 0x0F];
        }

        String allocType = SystemPropertyUtil.get(
                "io.netty.allocator.type", PlatformDependent.isAndroid() ? "unpooled" : "pooled");
        allocType = allocType.toLowerCase(Locale.US).trim();

        ByteBufAllocator alloc;
        if ("unpooled".equals(allocType)) {
            alloc = UnpooledByteBufAllocator.DEFAULT;
            logger.debug("-Dio.netty.allocator.type: {}", allocType);
        } else if ("pooled".equals(allocType)) {
            alloc = PooledByteBufAllocator.DEFAULT;
            logger.debug("-Dio.netty.allocator.type: {}", allocType);
        } else {
            alloc = PooledByteBufAllocator.DEFAULT;
            logger.debug("-Dio.netty.allocator.type: pooled (unknown: {})", allocType);
        }

        DEFAULT_ALLOCATOR = alloc;

        THREAD_LOCAL_BUFFER_SIZE = SystemPropertyUtil.getInt("io.netty.threadLocalDirectBufferSize", 64 * 1024);
        logger.debug("-Dio.netty.threadLocalDirectBufferSize: {}", THREAD_LOCAL_BUFFER_SIZE);
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified buffer's readable bytes.
     */
    public static String hexDump(ByteBuf buffer) {
        return hexDump(buffer, buffer.readerIndex(), buffer.readableBytes());
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified buffer's sub-region.
     */
    public static String hexDump(ByteBuf buffer, int fromIndex, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length: " + length);
        }
        if (length == 0) {
            return "";
        }

        int endIndex = fromIndex + length;
        char[] buf = new char[length << 1];

        int srcIdx = fromIndex;
        int dstIdx = 0;
        for (; srcIdx < endIndex; srcIdx ++, dstIdx += 2) {
            System.arraycopy(
                    HEXDUMP_TABLE, buffer.getUnsignedByte(srcIdx) << 1,
                    buf, dstIdx, 2);
        }

        return new String(buf);
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified byte array.
     */
    public static String hexDump(byte[] array) {
        return hexDump(array, 0, array.length);
    }

    /**
     * Returns a <a href="http://en.wikipedia.org/wiki/Hex_dump">hex dump</a>
     * of the specified byte array's sub-region.
     */
    public static String hexDump(byte[] array, int fromIndex, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length: " + length);
        }
        if (length == 0) {
            return "";
        }

        int endIndex = fromIndex + length;
        char[] buf = new char[length << 1];

        int srcIdx = fromIndex;
        int dstIdx = 0;
        for (; srcIdx < endIndex; srcIdx ++, dstIdx += 2) {
            System.arraycopy(HEXDUMP_TABLE, (array[srcIdx] & 0xFF) << 1, buf, dstIdx, 2);
        }

        return new String(buf);
    }

    /**
     * Calculates the hash code of the specified buffer.  This method is
     * useful when implementing a new buffer type.
     */
    public static int hashCode(ByteBuf buffer) {
        final int aLen = buffer.readableBytes();
        final int intCount = aLen >>> 2;
        final int byteCount = aLen & 3;

        int hashCode = 1;
        int arrayIndex = buffer.readerIndex();
        if (buffer.order() == ByteOrder.BIG_ENDIAN) {
            for (int i = intCount; i > 0; i --) {
                hashCode = 31 * hashCode + buffer.getInt(arrayIndex);
                arrayIndex += 4;
            }
        } else {
            for (int i = intCount; i > 0; i --) {
                hashCode = 31 * hashCode + swapInt(buffer.getInt(arrayIndex));
                arrayIndex += 4;
            }
        }

        for (int i = byteCount; i > 0; i --) {
            hashCode = 31 * hashCode + buffer.getByte(arrayIndex ++);
        }

        if (hashCode == 0) {
            hashCode = 1;
        }

        return hashCode;
    }

    /**
     * Returns {@code true} if and only if the two specified buffers are
     * identical to each other for {@code length} bytes starting at {@code aStartIndex}
     * index for the {@code a} buffer and {@code bStartIndex} index for the {@code b} buffer.
     * A more compact way to express this is:
     * <p>
     * {@code a[aStartIndex : aStartIndex + length] == b[bStartIndex : bStartIndex + length]}
     */
    public static boolean equals(ByteBuf a, int aStartIndex, ByteBuf b, int bStartIndex, int length) {
        if (aStartIndex < 0 || bStartIndex < 0 || length < 0) {
            throw new IllegalArgumentException("All indexes and lengths must be non-negative");
        }
        if (a.writerIndex() - length < aStartIndex || b.writerIndex() - length < bStartIndex) {
            return false;
        }

        final int longCount = length >>> 3;
        final int byteCount = length & 7;

        if (a.order() == b.order()) {
            for (int i = longCount; i > 0; i --) {
                if (a.getLong(aStartIndex) != b.getLong(bStartIndex)) {
                    return false;
                }
                aStartIndex += 8;
                bStartIndex += 8;
            }
        } else {
            for (int i = longCount; i > 0; i --) {
                if (a.getLong(aStartIndex) != swapLong(b.getLong(bStartIndex))) {
                    return false;
                }
                aStartIndex += 8;
                bStartIndex += 8;
            }
        }

        for (int i = byteCount; i > 0; i --) {
            if (a.getByte(aStartIndex) != b.getByte(bStartIndex)) {
                return false;
            }
            aStartIndex ++;
            bStartIndex ++;
        }

        return true;
    }

    /**
     * Returns {@code true} if and only if the two specified buffers are
     * identical to each other as described in {@link ByteBuf#equals(Object)}.
     * This method is useful when implementing a new buffer type.
     */
    public static boolean equals(ByteBuf bufferA, ByteBuf bufferB) {
        final int aLen = bufferA.readableBytes();
        if (aLen != bufferB.readableBytes()) {
            return false;
        }
        return equals(bufferA, bufferA.readerIndex(), bufferB, bufferB.readerIndex(), aLen);
    }

    /**
     * Compares the two specified buffers as described in {@link ByteBuf#compareTo(ByteBuf)}.
     * This method is useful when implementing a new buffer type.
     */
    public static int compare(ByteBuf bufferA, ByteBuf bufferB) {
        final int aLen = bufferA.readableBytes();
        final int bLen = bufferB.readableBytes();
        final int minLength = Math.min(aLen, bLen);
        final int uintCount = minLength >>> 2;
        final int byteCount = minLength & 3;

        int aIndex = bufferA.readerIndex();
        int bIndex = bufferB.readerIndex();

        if (bufferA.order() == bufferB.order()) {
            for (int i = uintCount; i > 0; i --) {
                long va = bufferA.getUnsignedInt(aIndex);
                long vb = bufferB.getUnsignedInt(bIndex);
                if (va > vb) {
                    return 1;
                }
                if (va < vb) {
                    return -1;
                }
                aIndex += 4;
                bIndex += 4;
            }
        } else {
            for (int i = uintCount; i > 0; i --) {
                long va = bufferA.getUnsignedInt(aIndex);
                long vb = swapInt(bufferB.getInt(bIndex)) & 0xFFFFFFFFL;
                if (va > vb) {
                    return 1;
                }
                if (va < vb) {
                    return -1;
                }
                aIndex += 4;
                bIndex += 4;
            }
        }

        for (int i = byteCount; i > 0; i --) {
            short va = bufferA.getUnsignedByte(aIndex);
            short vb = bufferB.getUnsignedByte(bIndex);
            if (va > vb) {
                return 1;
            }
            if (va < vb) {
                return -1;
            }
            aIndex ++;
            bIndex ++;
        }

        return aLen - bLen;
    }

    /**
     * The default implementation of {@link ByteBuf#indexOf(int, int, byte)}.
     * This method is useful when implementing a new buffer type.
     */
    public static int indexOf(ByteBuf buffer, int fromIndex, int toIndex, byte value) {
        if (fromIndex <= toIndex) {
            return firstIndexOf(buffer, fromIndex, toIndex, value);
        } else {
            return lastIndexOf(buffer, fromIndex, toIndex, value);
        }
    }

    /**
     * Toggles the endianness of the specified 16-bit short integer.
     */
    public static short swapShort(short value) {
        return Short.reverseBytes(value);
    }

    /**
     * Toggles the endianness of the specified 24-bit medium integer.
     */
    public static int swapMedium(int value) {
        int swapped = value << 16 & 0xff0000 | value & 0xff00 | value >>> 16 & 0xff;
        if ((swapped & 0x800000) != 0) {
            swapped |= 0xff000000;
        }
        return swapped;
    }

    /**
     * Toggles the endianness of the specified 32-bit integer.
     */
    public static int swapInt(int value) {
        return Integer.reverseBytes(value);
    }

    /**
     * Toggles the endianness of the specified 64-bit long integer.
     */
    public static long swapLong(long value) {
        return Long.reverseBytes(value);
    }

    /**
     * Read the given amount of bytes into a new {@link ByteBuf} that is allocated from the {@link ByteBufAllocator}.
     */
    public static ByteBuf readBytes(ByteBufAllocator alloc, ByteBuf buffer, int length) {
        boolean release = true;
        ByteBuf dst = alloc.buffer(length);
        try {
            buffer.readBytes(dst);
            release = false;
            return dst;
        } finally {
            if (release) {
                dst.release();
            }
        }
    }

    private static int firstIndexOf(ByteBuf buffer, int fromIndex, int toIndex, byte value) {
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

    private static int lastIndexOf(ByteBuf buffer, int fromIndex, int toIndex, byte value) {
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

    /**
     * Encode a {@link CharSequence} in <a href="http://en.wikipedia.org/wiki/UTF-8">UTF-8</a> and write
     * it to a {@link ByteBuf}.
     *
     * This method returns the actual number of bytes written.
     */
    public static int writeUtf8(ByteBuf buf, CharSequence seq) {
        if (buf == null) {
            throw new NullPointerException("buf");
        }
        if (seq == null) {
            throw new NullPointerException("seq");
        }
        // UTF-8 uses max. 3 bytes per char, so calculate the worst case.
        final int len = seq.length();
        final int maxSize = len * 3;
        buf.ensureWritable(maxSize);
        if (buf instanceof AbstractByteBuf) {
            // Fast-Path
            AbstractByteBuf buffer = (AbstractByteBuf) buf;
            int oldWriterIndex = buffer.writerIndex;
            int writerIndex = oldWriterIndex;

            // We can use the _set methods as these not need to do any index checks and reference checks.
            // This is possible as we called ensureWritable(...) before.
            for (int i = 0; i < len; i++) {
                char c = seq.charAt(i);
                if (c < 0x80) {
                    buffer._setByte(writerIndex++, (byte) c);
                } else if (c < 0x800) {
                    buffer._setByte(writerIndex++, (byte) (0xc0 | (c >> 6)));
                    buffer._setByte(writerIndex++, (byte) (0x80 | (c & 0x3f)));
                } else {
                    buffer._setByte(writerIndex++, (byte) (0xe0 | (c >> 12)));
                    buffer._setByte(writerIndex++, (byte) (0x80 | ((c >> 6) & 0x3f)));
                    buffer._setByte(writerIndex++, (byte) (0x80 | (c & 0x3f)));
                }
            }
            // update the writerIndex without any extra checks for performance reasons
            buffer.writerIndex = writerIndex;
            return writerIndex - oldWriterIndex;
        } else {
            // Maybe we could also check if we can unwrap() to access the wrapped buffer which
            // may be an AbstractByteBuf. But this may be overkill so let us keep it simple for now.
            byte[] bytes = seq.toString().getBytes(CharsetUtil.UTF_8);
            buf.writeBytes(bytes);
            return bytes.length;
        }
    }

    /**
     * Encode a {@link CharSequence} in <a href="http://en.wikipedia.org/wiki/ASCII">ASCII</a> and write it
     * to a {@link ByteBuf}.
     *
     * This method returns the actual number of bytes written.
     */
    public static int writeAscii(ByteBuf buf, CharSequence seq) {
        if (buf == null) {
            throw new NullPointerException("buf");
        }
        if (seq == null) {
            throw new NullPointerException("seq");
        }
        // ASCII uses 1 byte per char
        final int len = seq.length();
        buf.ensureWritable(len);
        if (buf instanceof AbstractByteBuf) {
            // Fast-Path
            AbstractByteBuf buffer = (AbstractByteBuf) buf;
            int writerIndex = buffer.writerIndex;

            // We can use the _set methods as these not need to do any index checks and reference checks.
            // This is possible as we called ensureWritable(...) before.
            for (int i = 0; i < len; i++) {
                buffer._setByte(writerIndex++, (byte) seq.charAt(i));
            }
            // update the writerIndex without any extra checks for performance reasons
            buffer.writerIndex = writerIndex;
        } else {
            // Maybe we could also check if we can unwrap() to access the wrapped buffer which
            // may be an AbstractByteBuf. But this may be overkill so let us keep it simple for now.
            buf.writeBytes(seq.toString().getBytes(CharsetUtil.US_ASCII));
        }
        return len;
    }

    /**
     * Encode the given {@link CharBuffer} using the given {@link Charset} into a new {@link ByteBuf} which
     * is allocated via the {@link ByteBufAllocator}.
     */
    public static ByteBuf encodeString(ByteBufAllocator alloc, CharBuffer src, Charset charset) {
        return encodeString0(alloc, false, src, charset);
    }

    static ByteBuf encodeString0(ByteBufAllocator alloc, boolean enforceHeap, CharBuffer src, Charset charset) {
        final CharsetEncoder encoder = CharsetUtil.getEncoder(charset);
        int length = (int) ((double) src.remaining() * encoder.maxBytesPerChar());
        boolean release = true;
        final ByteBuf dst;
        if (enforceHeap) {
            dst = alloc.heapBuffer(length);
        } else {
            dst = alloc.buffer(length);
        }
        try {
            final ByteBuffer dstBuf = dst.internalNioBuffer(0, length);
            final int pos = dstBuf.position();
            CoderResult cr = encoder.encode(src, dstBuf, true);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            cr = encoder.flush(dstBuf);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            dst.writerIndex(dst.writerIndex() + dstBuf.position() - pos);
            release = false;
            return dst;
        } catch (CharacterCodingException x) {
            throw new IllegalStateException(x);
        } finally {
            if (release) {
                dst.release();
            }
        }
    }

    static String decodeString(ByteBuffer src, Charset charset) {
        final CharsetDecoder decoder = CharsetUtil.getDecoder(charset);
        final CharBuffer dst = CharBuffer.allocate(
                (int) ((double) src.remaining() * decoder.maxCharsPerByte()));
        try {
            CoderResult cr = decoder.decode(src, dst, true);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            cr = decoder.flush(dst);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
        } catch (CharacterCodingException x) {
            throw new IllegalStateException(x);
        }
        return dst.flip().toString();
    }

    /**
     * Returns a cached thread-local direct buffer, if available.
     *
     * @return a cached thread-local direct buffer, if available.  {@code null} otherwise.
     */
    public static ByteBuf threadLocalDirectBuffer() {
        if (THREAD_LOCAL_BUFFER_SIZE <= 0) {
            return null;
        }

        if (PlatformDependent.hasUnsafe()) {
            return ThreadLocalUnsafeDirectByteBuf.newInstance();
        } else {
            return ThreadLocalDirectByteBuf.newInstance();
        }
    }

    /**
     * Create a copy of the underlying storage from {@link value} into a byte array.
     * The copy will start at {@link ByteBuf#readerIndex()} and copy {@link ByteBuf#readableBytes()} bytes.
     */
    public static byte[] getBytes(ByteBuf buf) {
        return getBytes(buf, checkNotNull(buf, "buf").readerIndex(), buf.readableBytes());
    }

    /**
     * Create a copy of the underlying storage from {@link buf} into a byte array.
     * The copy will start at {@code start} and copy {@code length} bytes.
     */
    public static byte[] getBytes(ByteBuf buf, int start, int length) {
        return getBytes(buf, start, length, true);
    }

    /**
     * Return an array of the underlying storage from {@link buf} into a byte array.
     * The copy will start at {@code start} and copy {@code length} bytes.
     * If {@code copy} is true a copy will be made of the memory.
     * If {@code copy} is false the underlying storage will be shared, if possible.
     */
    public static byte[] getBytes(ByteBuf buf, int start, int length, boolean copy) {
        if (start < 0 || length > checkNotNull(buf, "buf").capacity() - start) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= start(" + start + ") <= start + length(" + length
                    + ") <= " + "buf.capacity(" + buf.capacity() + ')');
        }

        if (buf.hasArray()) {
            if (copy || start != 0 || length != buf.capacity()) {
                int baseOffset = buf.arrayOffset() + start;
                return Arrays.copyOfRange(buf.array(), baseOffset, baseOffset + length);
            } else {
                return buf.array();
            }
        }

        byte[] v = new byte[length];
        buf.getBytes(start, v);
        return v;
    }

    /**
     * Copies the content of {@code src} to a {@link ByteBuf} using {@link ByteBuf#writeBytes(byte[], int, int)}.
     * @param src The source of the data to copy.
     * @param srcIdx the starting offset of characters to copy.
     * @param dst the destination byte array.
     * @param dstIdx the starting offset in the destination byte array.
     * @param length the number of characters to copy.
     */
    public static void copy(ByteString src, int srcIdx, ByteBuf dst, int dstIdx, int length) {
        final int thisLen = src.length();

        if (srcIdx < 0 || length > thisLen - srcIdx) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= srcIdx(" + srcIdx + ") <= srcIdx + length("
                            + length + ") <= srcLen(" + thisLen + ')');
        }

        checkNotNull(dst, "dst").setBytes(dstIdx, src.array(), srcIdx, length);
    }

    /**
     * Copies the content of {@code src} to a {@link ByteBuf} using {@link ByteBuf#writeBytes(byte[], int, int)}.
     * @param src The source of the data to copy.
     * @param srcIdx the starting offset of characters to copy.
     * @param dst the destination byte array.
     * @param length the number of characters to copy.
     */
    public static void copy(ByteString src, int srcIdx, ByteBuf dst, int length) {
        final int thisLen = src.length();

        if (srcIdx < 0 || length > thisLen - srcIdx) {
            throw new IndexOutOfBoundsException("expected: " + "0 <= srcIdx(" + srcIdx + ") <= srcIdx + length("
                            + length + ") <= srcLen(" + thisLen + ')');
        }

        checkNotNull(dst, "dst").writeBytes(src.array(), srcIdx, length);
    }

    static final class ThreadLocalUnsafeDirectByteBuf extends UnpooledUnsafeDirectByteBuf {

        private static final Recycler<ThreadLocalUnsafeDirectByteBuf> RECYCLER =
                new Recycler<ThreadLocalUnsafeDirectByteBuf>() {
                    @Override
                    protected ThreadLocalUnsafeDirectByteBuf newObject(Handle handle) {
                        return new ThreadLocalUnsafeDirectByteBuf(handle);
                    }
                };

        static ThreadLocalUnsafeDirectByteBuf newInstance() {
            ThreadLocalUnsafeDirectByteBuf buf = RECYCLER.get();
            buf.setRefCnt(1);
            return buf;
        }

        private final Handle handle;

        private ThreadLocalUnsafeDirectByteBuf(Handle handle) {
            super(UnpooledByteBufAllocator.DEFAULT, 256, Integer.MAX_VALUE);
            this.handle = handle;
        }

        @Override
        protected void deallocate() {
            if (capacity() > THREAD_LOCAL_BUFFER_SIZE) {
                super.deallocate();
            } else {
                clear();
                RECYCLER.recycle(this, handle);
            }
        }
    }

    static final class ThreadLocalDirectByteBuf extends UnpooledDirectByteBuf {

        private static final Recycler<ThreadLocalDirectByteBuf> RECYCLER = new Recycler<ThreadLocalDirectByteBuf>() {
            @Override
            protected ThreadLocalDirectByteBuf newObject(Handle handle) {
                return new ThreadLocalDirectByteBuf(handle);
            }
        };

        static ThreadLocalDirectByteBuf newInstance() {
            ThreadLocalDirectByteBuf buf = RECYCLER.get();
            buf.setRefCnt(1);
            return buf;
        }

        private final Handle handle;

        private ThreadLocalDirectByteBuf(Handle handle) {
            super(UnpooledByteBufAllocator.DEFAULT, 256, Integer.MAX_VALUE);
            this.handle = handle;
        }

        @Override
        protected void deallocate() {
            if (capacity() > THREAD_LOCAL_BUFFER_SIZE) {
                super.deallocate();
            } else {
                clear();
                RECYCLER.recycle(this, handle);
            }
        }
    }

    private ByteBufUtil() { }
}
