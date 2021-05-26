/*
 * Copyright 2020 The Netty Project
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

import io.netty.util.CharsetUtil;
import io.netty.util.internal.MathUtil;
import io.netty.util.internal.ObjectUtil;

import java.nio.CharBuffer;
import java.nio.charset.Charset;

public class UnpooledNewVersion {
    private static final ByteBufAllocator ALLOC = UnpooledByteBufAllocator.DEFAULT;

    /**
     * A buffer whose capacity is {@code 0}.
     */
    public static final ByteBuf EMPTY_BUFFER = ALLOC.buffer(0, 0);


    /**
     * Creates a new big-endian buffer whose content is the specified
     * {@code string} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ByteBuf copiedBuffer(CharSequence string, Charset charset) {
        ObjectUtil.checkNotNull(string, "string");
        if (CharsetUtil.UTF_8.equals(charset)) {
            return copiedBufferUtf8(string, 0, string.length());
        }
        if (CharsetUtil.US_ASCII.equals(charset)) {
            return copiedBufferAscii(string);
        }
        if (string instanceof CharBuffer) {
            return copiedBuffer((CharBuffer) string, charset);
        }

        return copiedBuffer(CharBuffer.wrap(string), charset);
    }

    private static ByteBuf copiedBufferUtf8(CharSequence seq, int start, int length) {
        int capacity = ByteBufUtil.utf8MaxBytes(length);
        boolean release = true;
        // Mimic the same behavior as other copiedBuffer implementations.
        ByteBuf buffer = ALLOC.heapBuffer(capacity);
        try {
            ByteBufUtilNewVersion.reserveAndWriteUtf8Seq(buffer, seq, start, start + length, capacity);
            release = false;
            return buffer;
        } finally {
            if (release) {
                buffer.release();
            }
        }
    }

    private static ByteBuf copiedBufferAscii(CharSequence string) {
        boolean release = true;
        // Mimic the same behavior as other copiedBuffer implementations.
        ByteBuf buffer = ALLOC.heapBuffer(string.length());
        try {
            ByteBufUtil.writeAscii(buffer, string);
            release = false;
            return buffer;
        } finally {
            if (release) {
                buffer.release();
            }
        }
    }

    private static ByteBuf copiedBufferAscii(CharSequence string, int start, int length) {
        boolean release = true;
        // Mimic the same behavior as other copiedBuffer implementations.
        ByteBuf buffer = ALLOC.heapBuffer(length);
        try {
            ByteBufUtilNewVersion.writeAscii(buffer, string, start, length);
            release = false;
            return buffer;
        } finally {
            if (release) {
                buffer.release();
            }
        }
    }

    /**
     * Creates a new big-endian buffer whose content is a subregion of
     * the specified {@code string} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ByteBuf copiedBuffer(
            CharSequence string, int offset, int length, Charset charset) {
        ObjectUtil.checkNotNull(string, "string");
        checkBounds(offset, length, string.length());
        if (length == 0) {
            return EMPTY_BUFFER;
        }
        if (CharsetUtil.UTF_8.equals(charset)) {
            return copiedBufferUtf8(string, offset, length);
        }
        if (CharsetUtil.US_ASCII.equals(charset)) {
            return copiedBufferAscii(string, offset, length);
        }
        if (string instanceof CharBuffer) {
            CharBuffer buf = (CharBuffer) string;
            if (buf.hasArray()) {
                return copiedBuffer(
                        CharBuffer.wrap(buf.array(), buf.arrayOffset() + buf.position() + offset, length),
                        charset);
            }

            buf = buf.slice();
            buf.limit(length);
            buf.position(offset);
            return copiedBuffer(buf, charset);
        }

        return copiedBuffer(CharBuffer.wrap(string, offset, offset + length), charset);
    }

    /**
     * Creates a new big-endian buffer whose content is the specified
     * {@code array} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ByteBuf copiedBuffer(char[] array, Charset charset) {
        ObjectUtil.checkNotNull(array, "array");
        int length = array.length;
        if (length == 0) {
            return EMPTY_BUFFER;
        }
        if (CharsetUtil.UTF_8.equals(charset)) {
            return copiedBufferUtf8(CharBuffer.wrap(array), 0, length);
        }
        if (CharsetUtil.US_ASCII.equals(charset)) {
            return copiedBufferAscii(CharBuffer.wrap(array));
        }
        return copiedBuffer(CharBuffer.wrap(array), charset);
    }

    /**
     * Creates a new big-endian buffer whose content is a subregion of
     * the specified {@code array} encoded in the specified {@code charset}.
     * The new buffer's {@code readerIndex} and {@code writerIndex} are
     * {@code 0} and the length of the encoded string respectively.
     */
    public static ByteBuf copiedBuffer(char[] array, int offset, int length, Charset charset) {
        ObjectUtil.checkNotNull(array, "array");
        checkBounds(offset, length, array.length);
        if (length == 0) {
            return EMPTY_BUFFER;
        }
        if (CharsetUtil.UTF_8.equals(charset)) {
            return copiedBufferUtf8(CharBuffer.wrap(array), offset, length);
        }
        if (CharsetUtil.US_ASCII.equals(charset)) {
            return copiedBufferAscii(CharBuffer.wrap(array), offset, length);
        }
        return copiedBuffer(CharBuffer.wrap(array, offset, length), charset);
    }

    private static ByteBuf copiedBuffer(CharBuffer buffer, Charset charset) {
        return ByteBufUtil.encodeString0(ALLOC, true, buffer, charset, 0);
    }

    private static void checkBounds(int start, int length, int capacity) {
        if (MathUtil.isOutOfBounds(start, length, capacity)) {
            throw new IndexOutOfBoundsException("expected: 0 <= start(" + start + ") <= start + length ("
                    + (start + length) + ") <= capacity(" + capacity + ')');
        }
    }
}
