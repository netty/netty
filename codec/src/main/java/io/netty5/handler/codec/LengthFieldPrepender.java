/*
 * Copyright 2012 The Netty Project
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
package io.netty5.handler.codec;

import io.netty5.buffer.BufferUtil;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandlerContext;

import java.nio.ByteOrder;
import java.util.List;

import static io.netty5.util.internal.ObjectUtil.checkPositiveOrZero;
import static java.util.Objects.requireNonNull;

/**
 * An encoder that prepends the length of the message.  The length value is
 * prepended as a binary form.
 * <p>
 * For example, <tt>{@link LengthFieldPrepender}(2)</tt> will encode the
 * following 12-bytes string:
 * <pre>
 * +----------------+
 * | "HELLO, WORLD" |
 * +----------------+
 * </pre>
 * into the following:
 * <pre>
 * +--------+----------------+
 * + 0x000C | "HELLO, WORLD" |
 * +--------+----------------+
 * </pre>
 * If you turned on the {@code lengthIncludesLengthFieldLength} flag in the
 * constructor, the encoded data would look like the following
 * (12 (original data) + 2 (prepended data) = 14 (0xE)):
 * <pre>
 * +--------+----------------+
 * + 0x000E | "HELLO, WORLD" |
 * +--------+----------------+
 * </pre>
 */
public class LengthFieldPrepender extends MessageToMessageEncoder<Buffer> {

    private final ByteOrder byteOrder;
    private final int lengthFieldLength;
    private final boolean lengthIncludesLengthFieldLength;
    private final int lengthAdjustment;

    /**
     * Creates a new instance.
     *
     * @param lengthFieldLength the length of the prepended length field.
     *                          Only 1, 2, 3, 4, and 8 are supported by default.
     */
    public LengthFieldPrepender(int lengthFieldLength) {
        this(lengthFieldLength, false);
    }

    /**
     * Creates a new instance.
     *
     * @param lengthFieldLength               the length of the prepended length field.
     *                                        Only 1, 2, 3, 4, and 8 are supported by default.
     * @param lengthIncludesLengthFieldLength if {@code true}, the length of the prepended
     *                                        length field is added to the value of the
     *                                        prepended length field.
     */
    public LengthFieldPrepender(int lengthFieldLength, boolean lengthIncludesLengthFieldLength) {
        this(lengthFieldLength, 0, lengthIncludesLengthFieldLength);
    }

    /**
     * Creates a new instance.
     *
     * @param lengthFieldLength the length of the prepended length field.
     *                          Only 1, 2, 3, 4, and 8 are supported by default.
     * @param lengthAdjustment  the compensation value to add to the value
     *                          of the length field
     */
    public LengthFieldPrepender(int lengthFieldLength, int lengthAdjustment) {
        this(lengthFieldLength, lengthAdjustment, false);
    }

    /**
     * Creates a new instance.
     *
     * @param lengthFieldLength               the length of the prepended length field.
     *                                        Only 1, 2, 3, 4, and 8 are supported by default.
     * @param lengthAdjustment                the compensation value to add to the value
     *                                        of the length field
     * @param lengthIncludesLengthFieldLength if {@code true}, the length of the prepended
     *                                        length field is added to the value of the
     *                                        prepended length field.
     */
    public LengthFieldPrepender(int lengthFieldLength, int lengthAdjustment, boolean lengthIncludesLengthFieldLength) {
        this(ByteOrder.BIG_ENDIAN, lengthFieldLength, lengthAdjustment, lengthIncludesLengthFieldLength);
    }

    /**
     * Creates a new instance.
     *
     * @param byteOrder                       the {@link ByteOrder} of the length field
     * @param lengthFieldLength               the length of the prepended length field.
     *                                        Only 1, 2, 3, 4, and 8 are supported by default.
     * @param lengthAdjustment                the compensation value to add to the value
     *                                        of the length field
     * @param lengthIncludesLengthFieldLength if {@code true}, the length of the prepended
     *                                        length field is added to the value of the
     *                                        prepended length field.
     */
    public LengthFieldPrepender(
            ByteOrder byteOrder, int lengthFieldLength,
            int lengthAdjustment, boolean lengthIncludesLengthFieldLength) {
        requireNonNull(byteOrder, "byteOrder");

        this.byteOrder = byteOrder;
        this.lengthFieldLength = lengthFieldLength;
        this.lengthIncludesLengthFieldLength = lengthIncludesLengthFieldLength;
        this.lengthAdjustment = lengthAdjustment;
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Buffer buffer, List<Object> out) throws Exception {
        int length = buffer.readableBytes() + lengthAdjustment;
        if (lengthIncludesLengthFieldLength) {
            length += lengthFieldLength;
        }

        checkPositiveOrZero(length, "length");

        out.add(getLengthFieldBuffer(ctx, length, lengthFieldLength, byteOrder));
        out.add(buffer.split());
    }

    /**
     * Encodes the length into a buffer which will be prepended as the length field.  The default implementation is
     * capable of encoding the length into an 8/16/24/32/64 bit integer.  Override this method to encode the length
     * field differently.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link LengthFieldPrepender} belongs to
     * @param length the length which should be encoded in the length field
     * @param lengthFieldLength the length of the prepended length field
     * @param byteOrder the {@link ByteOrder} of the length field
     * @return A buffer containing the encoded length
     * @throws EncoderException if failed to encode the length
     */
    protected Buffer getLengthFieldBuffer(
            ChannelHandlerContext ctx, int length, int lengthFieldLength, ByteOrder byteOrder) {
        final boolean reverseBytes = byteOrder == ByteOrder.LITTLE_ENDIAN;

        switch (lengthFieldLength) {
        case 1:
            if (length >= 256) {
                throw new IllegalArgumentException("length does not fit into a byte: " + length);
            }
            return ctx.bufferAllocator().allocate(lengthFieldLength).writeByte((byte) length);
        case 2:
            if (length >= 65536) {
                throw new IllegalArgumentException("length does not fit into a short integer: " + length);
            }
            return ctx.bufferAllocator().allocate(lengthFieldLength)
                      .writeShort(reverseBytes ? Short.reverseBytes((short) length) : (short) length);
        case 3:
            if (length >= 16777216) {
                throw new IllegalArgumentException("length does not fit into a medium integer: " + length);
            }
            return ctx.bufferAllocator().allocate(lengthFieldLength)
                      .writeMedium(reverseBytes ? BufferUtil.reverseMedium(length) : length);
        case 4:
            return ctx.bufferAllocator().allocate(lengthFieldLength)
                      .writeInt(reverseBytes ? Integer.reverseBytes(length) : length);
        case 8:
            return ctx.bufferAllocator().allocate(lengthFieldLength)
                      .writeLong(reverseBytes ? Long.reverseBytes(length) : length);
        default:
            throw new EncoderException(
                    "unsupported lengthFieldLength: " + lengthFieldLength + " (expected: 1, 2, 3, 4, or 8)");
        }
    }

}
