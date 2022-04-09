/*
 * Copyright 2022 The Netty Project
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

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandlerContext;

import java.nio.ByteOrder;

import static io.netty5.util.internal.ObjectUtil.*;
import static java.util.Objects.*;

/**
 * A decoder that splits the received {@link Buffer}s dynamically by the
 * value of the length field in the message.  It is particularly useful when you
 * decode a binary message which has an integer header field that represents the
 * length of the message body or the whole message.
 * <p>
 * {@link LengthFieldBasedFrameDecoderForBuffer} has many configuration parameters so
 * that it can decode any message with a length field, which is often seen in
 * proprietary client-server protocols. Here are some example that will give
 * you the basic idea on which option does what.
 *
 * <h3>2 bytes length field at offset 0, do not strip header</h3>
 * <p>
 * The value of the length field in this example is <tt>12 (0x0C)</tt> which
 * represents the length of "HELLO, WORLD".  By default, the decoder assumes
 * that the length field represents the number of the bytes that follows the
 * length field.  Therefore, it can be decoded with the simplistic parameter
 * combination.
 * <pre>
 * <b>lengthFieldOffset</b>   = <b>0</b>
 * <b>lengthFieldLength</b>   = <b>2</b>
 * lengthAdjustment    = 0
 * initialBytesToStrip = 0 (= do not strip header)
 *
 * BEFORE DECODE (14 bytes)         AFTER DECODE (14 bytes)
 * +--------+----------------+      +--------+----------------+
 * | Length | Actual Content |----->| Length | Actual Content |
 * | 0x000C | "HELLO, WORLD" |      | 0x000C | "HELLO, WORLD" |
 * +--------+----------------+      +--------+----------------+
 * </pre>
 *
 * <h3>2 bytes length field at offset 0, strip header</h3>
 * <p>
 * Because we can get the length of the content by calling
 * {@link Buffer#readableBytes()}, you might want to strip the length
 * field by specifying <tt>initialBytesToStrip</tt>.  In this example, we
 * specified <tt>2</tt>, that is same with the length of the length field, to
 * strip the first two bytes.
 * <pre>
 * lengthFieldOffset   = 0
 * lengthFieldLength   = 2
 * lengthAdjustment    = 0
 * <b>initialBytesToStrip</b> = <b>2</b> (= the length of the Length field)
 *
 * BEFORE DECODE (14 bytes)         AFTER DECODE (12 bytes)
 * +--------+----------------+      +----------------+
 * | Length | Actual Content |----->| Actual Content |
 * | 0x000C | "HELLO, WORLD" |      | "HELLO, WORLD" |
 * +--------+----------------+      +----------------+
 * </pre>
 *
 * <h3>2 bytes length field at offset 0, do not strip header, the length field
 * represents the length of the whole message</h3>
 * <p>
 * In most cases, the length field represents the length of the message body
 * only, as shown in the previous examples.  However, in some protocols, the
 * length field represents the length of the whole message, including the
 * message header.  In such a case, we specify a non-zero
 * <tt>lengthAdjustment</tt>.  Because the length value in this example message
 * is always greater than the body length by <tt>2</tt>, we specify <tt>-2</tt>
 * as <tt>lengthAdjustment</tt> for compensation.
 * <pre>
 * lengthFieldOffset   =  0
 * lengthFieldLength   =  2
 * <b>lengthAdjustment</b>    = <b>-2</b> (= the length of the Length field)
 * initialBytesToStrip =  0
 *
 * BEFORE DECODE (14 bytes)         AFTER DECODE (14 bytes)
 * +--------+----------------+      +--------+----------------+
 * | Length | Actual Content |----->| Length | Actual Content |
 * | 0x000E | "HELLO, WORLD" |      | 0x000E | "HELLO, WORLD" |
 * +--------+----------------+      +--------+----------------+
 * </pre>
 *
 * <h3>3 bytes length field at the end of 5 bytes header, do not strip header</h3>
 * <p>
 * The following message is a simple variation of the first example.  An extra
 * header value is prepended to the message.  <tt>lengthAdjustment</tt> is zero
 * again because the decoder always takes the length of the prepended data into
 * account during frame length calculation.
 * <pre>
 * <b>lengthFieldOffset</b>   = <b>2</b> (= the length of Header 1)
 * <b>lengthFieldLength</b>   = <b>3</b>
 * lengthAdjustment    = 0
 * initialBytesToStrip = 0
 *
 * BEFORE DECODE (17 bytes)                      AFTER DECODE (17 bytes)
 * +----------+----------+----------------+      +----------+----------+----------------+
 * | Header 1 |  Length  | Actual Content |----->| Header 1 |  Length  | Actual Content |
 * |  0xCAFE  | 0x00000C | "HELLO, WORLD" |      |  0xCAFE  | 0x00000C | "HELLO, WORLD" |
 * +----------+----------+----------------+      +----------+----------+----------------+
 * </pre>
 *
 * <h3>3 bytes length field at the beginning of 5 bytes header, do not strip header</h3>
 * <p>
 * This is an advanced example that shows the case where there is an extra
 * header between the length field and the message body.  You have to specify a
 * positive <tt>lengthAdjustment</tt> so that the decoder counts the extra
 * header into the frame length calculation.
 * <pre>
 * lengthFieldOffset   = 0
 * lengthFieldLength   = 3
 * <b>lengthAdjustment</b>    = <b>2</b> (= the length of Header 1)
 * initialBytesToStrip = 0
 *
 * BEFORE DECODE (17 bytes)                      AFTER DECODE (17 bytes)
 * +----------+----------+----------------+      +----------+----------+----------------+
 * |  Length  | Header 1 | Actual Content |----->|  Length  | Header 1 | Actual Content |
 * | 0x00000C |  0xCAFE  | "HELLO, WORLD" |      | 0x00000C |  0xCAFE  | "HELLO, WORLD" |
 * +----------+----------+----------------+      +----------+----------+----------------+
 * </pre>
 *
 * <h3>2 bytes length field at offset 1 in the middle of 4 bytes header,
 * strip the first header field and the length field</h3>
 * <p>
 * This is a combination of all the examples above.  There are the prepended
 * header before the length field and the extra header after the length field.
 * The prepended header affects the <tt>lengthFieldOffset</tt> and the extra
 * header affects the <tt>lengthAdjustment</tt>.  We also specified a non-zero
 * <tt>initialBytesToStrip</tt> to strip the length field and the prepended
 * header from the frame.  If you don't want to strip the prepended header, you
 * could specify <tt>0</tt> for <tt>initialBytesToSkip</tt>.
 * <pre>
 * lengthFieldOffset   = 1 (= the length of HDR1)
 * lengthFieldLength   = 2
 * <b>lengthAdjustment</b>    = <b>1</b> (= the length of HDR2)
 * <b>initialBytesToStrip</b> = <b>3</b> (= the length of HDR1 + LEN)
 *
 * BEFORE DECODE (16 bytes)                       AFTER DECODE (13 bytes)
 * +------+--------+------+----------------+      +------+----------------+
 * | HDR1 | Length | HDR2 | Actual Content |----->| HDR2 | Actual Content |
 * | 0xCA | 0x000C | 0xFE | "HELLO, WORLD" |      | 0xFE | "HELLO, WORLD" |
 * +------+--------+------+----------------+      +------+----------------+
 * </pre>
 *
 * <h3>2 bytes length field at offset 1 in the middle of 4 bytes header,
 * strip the first header field and the length field, the length field
 * represents the length of the whole message</h3>
 * <p>
 * Let's give another twist to the previous example.  The only difference from
 * the previous example is that the length field represents the length of the
 * whole message instead of the message body, just like the third example.
 * We have to count the length of HDR1 and Length into <tt>lengthAdjustment</tt>.
 * Please note that we don't need to take the length of HDR2 into account
 * because the length field already includes the whole header length.
 * <pre>
 * lengthFieldOffset   =  1
 * lengthFieldLength   =  2
 * <b>lengthAdjustment</b>    = <b>-3</b> (= the length of HDR1 + LEN, negative)
 * <b>initialBytesToStrip</b> = <b> 3</b>
 *
 * BEFORE DECODE (16 bytes)                       AFTER DECODE (13 bytes)
 * +------+--------+------+----------------+      +------+----------------+
 * | HDR1 | Length | HDR2 | Actual Content |----->| HDR2 | Actual Content |
 * | 0xCA | 0x0010 | 0xFE | "HELLO, WORLD" |      | 0xFE | "HELLO, WORLD" |
 * +------+--------+------+----------------+      +------+----------------+
 * </pre>
 *
 * @see LengthFieldPrepender
 */
public class LengthFieldBasedFrameDecoderForBuffer extends ByteToMessageDecoderForBuffer {

    private final ByteOrder byteOrder;
    private final int maxFrameLength;
    private final int lengthFieldOffset;
    private final int lengthFieldLength;
    private final int lengthFieldEndOffset;
    private final int lengthAdjustment;
    private final int initialBytesToStrip;
    private final boolean failFast;

    private long tooLongFrameLength;
    private long bytesToDiscard;
    private int currentFrameLength = -1;

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength    the maximum length of the frame.  If the length of the frame is
     *                          greater than this value, {@link TooLongFrameException} will be
     *                          thrown.
     * @param lengthFieldOffset the offset of the length field
     * @param lengthFieldLength the length of the length field
     */
    public LengthFieldBasedFrameDecoderForBuffer(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength) {
        this(maxFrameLength, lengthFieldOffset, lengthFieldLength, 0, 0);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength      the maximum length of the frame.  If the length of the frame is
     *                            greater than this value, {@link TooLongFrameException} will be
     *                            thrown.
     * @param lengthFieldOffset   the offset of the length field
     * @param lengthFieldLength   the length of the length field
     * @param lengthAdjustment    the compensation value to add to the value of the length field
     * @param initialBytesToStrip the number of first bytes to strip out from the decoded frame
     */
    public LengthFieldBasedFrameDecoderForBuffer(
            int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip) {
        this(maxFrameLength,
             lengthFieldOffset, lengthFieldLength, lengthAdjustment,
             initialBytesToStrip, true);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength      the maximum length of the frame.  If the length of the frame is
     *                            greater than this value, {@link TooLongFrameException} will be
     *                            thrown.
     * @param lengthFieldOffset   the offset of the length field
     * @param lengthFieldLength   the length of the length field
     * @param lengthAdjustment    the compensation value to add to the value of the length field
     * @param initialBytesToStrip the number of first bytes to strip out from the decoded frame
     * @param failFast            If <tt>true</tt>, a {@link TooLongFrameException} is thrown as
     *                            soon as the decoder notices the length of the frame will exceed
     *                            <tt>maxFrameLength</tt> regardless of whether the entire frame
     *                            has been read.  If <tt>false</tt>, a {@link TooLongFrameException}
     *                            is thrown after the entire frame that exceeds <tt>maxFrameLength</tt>
     *                            has been read.
     */
    public LengthFieldBasedFrameDecoderForBuffer(
            int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip, boolean failFast) {
        this(ByteOrder.BIG_ENDIAN, maxFrameLength, lengthFieldOffset, lengthFieldLength,
             lengthAdjustment, initialBytesToStrip, failFast);
    }

    /**
     * Creates a new instance.
     *
     * @param byteOrder           the {@link ByteOrder} of the length field
     * @param maxFrameLength      the maximum length of the frame.  If the length of the frame is
     *                            greater than this value, {@link TooLongFrameException} will be
     *                            thrown.
     * @param lengthFieldOffset   the offset of the length field
     * @param lengthFieldLength   the length of the length field
     * @param lengthAdjustment    the compensation value to add to the value of the length field
     * @param initialBytesToStrip the number of first bytes to strip out from the decoded frame
     * @param failFast            If <tt>true</tt>, a {@link TooLongFrameException} is thrown as
     *                            soon as the decoder notices the length of the frame will exceed
     *                            <tt>maxFrameLength</tt> regardless of whether the entire frame
     *                            has been read.  If <tt>false</tt>, a {@link TooLongFrameException}
     *                            is thrown after the entire frame that exceeds <tt>maxFrameLength</tt>
     *                            has been read.
     */
    public LengthFieldBasedFrameDecoderForBuffer(
            ByteOrder byteOrder, int maxFrameLength, int lengthFieldOffset, int lengthFieldLength,
            int lengthAdjustment, int initialBytesToStrip, boolean failFast) {
        requireNonNull(byteOrder, "byteOrder");
        checkPositive(maxFrameLength, "maxFrameLength");
        checkPositiveOrZero(lengthFieldOffset, "lengthFieldOffset");
        checkPositiveOrZero(initialBytesToStrip, "initialBytesToStrip");

        if (lengthFieldOffset > maxFrameLength - lengthFieldLength) {
            throw new IllegalArgumentException(
                    "maxFrameLength (" + maxFrameLength + ") " +
                    "must be equal to or greater than " +
                    "lengthFieldOffset (" + lengthFieldOffset + ") + " +
                    "lengthFieldLength (" + lengthFieldLength + ").");
        }

        this.byteOrder = byteOrder;
        this.maxFrameLength = maxFrameLength;
        this.lengthFieldOffset = lengthFieldOffset;
        this.lengthFieldLength = lengthFieldLength;
        this.lengthAdjustment = lengthAdjustment;
        lengthFieldEndOffset = lengthFieldOffset + lengthFieldLength;
        this.initialBytesToStrip = initialBytesToStrip;
        this.failFast = failFast;
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, Buffer in) throws Exception {
        final Object decoded = decode0(ctx, in);
        if (decoded != null) {
            ctx.fireChannelRead(decoded);
        }
    }

    private void discardTooLongFrame(Buffer in) {
        final int bytesToDiscardNow = (int) Math.min(bytesToDiscard, in.readableBytes());
        in.skipReadable(bytesToDiscardNow);

        bytesToDiscard -= bytesToDiscardNow;

        failOnLengthExceededIfNecessary(false);
    }

    private static void failOnNegativeLengthField(Buffer buffer, long frameLength, int lengthFieldEndOffset) {
        buffer.skipReadable(lengthFieldEndOffset);
        throw new CorruptedFrameException("negative pre-adjustment length field: " + frameLength);
    }

    private static void failOnFrameLengthLessThanLengthFieldEndOffset(
            Buffer buffer, long frameLength, int lengthFieldEndOffset) {
        buffer.skipReadable(lengthFieldEndOffset);
        throw new CorruptedFrameException(
                "Adjusted frame length (" + frameLength + ") is less " +
                "than lengthFieldEndOffset: " + lengthFieldEndOffset);
    }

    private void handleFrameLengthExceeded(Buffer buffer, long frameLength) {
        final long discard = frameLength - buffer.readableBytes();
        tooLongFrameLength = frameLength;

        if (discard < 0) {
            // buffer contains more bytes then the frameLength so we can discard all now
            buffer.skipReadable((int) frameLength);
        } else {
            // Enter the discard mode and discard everything received so far.
            bytesToDiscard = discard;
            buffer.skipReadable(buffer.readableBytes());
        }
        failOnLengthExceededIfNecessary(true);
    }

    private static void failOnFrameLengthLessThanInitialBytesToStrip(
            Buffer buffer, int frameLength, int initialBytesToStrip) {
        buffer.skipReadable(frameLength);
        throw new CorruptedFrameException(
                "Adjusted frame length (" + frameLength + ") is less " +
                "than initialBytesToStrip: " + initialBytesToStrip);
    }

    /**
     * Create a frame out of the {@link Buffer} and return it.
     *
     * @param ctx    the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param buffer the {@link Buffer} from which to read data
     * @return the {@link Buffer} which represent the frame or {@code null} if no frame could be created.
     */
    protected Object decode0(ChannelHandlerContext ctx, Buffer buffer) throws Exception {
        if (currentFrameLength == -1) { // new frame
            if (bytesToDiscard > 0) {
                discardTooLongFrame(buffer);
            }

            if (buffer.readableBytes() < lengthFieldEndOffset) {
                return null;
            }

            final int actualLengthFieldOffset = buffer.readerOffset() + lengthFieldOffset;
            long frameLength = getUnadjustedFrameLength(buffer, actualLengthFieldOffset, lengthFieldLength);

            if (frameLength < 0) {
                failOnNegativeLengthField(buffer, frameLength, lengthFieldEndOffset);
            }

            frameLength += lengthAdjustment + lengthFieldEndOffset;

            if (frameLength < lengthFieldEndOffset) {
                failOnFrameLengthLessThanLengthFieldEndOffset(buffer, frameLength, lengthFieldEndOffset);
            }

            if (frameLength > maxFrameLength) {
                handleFrameLengthExceeded(buffer, frameLength);
                return null;
            }

            // never overflows because it's less than maxFrameLength
            currentFrameLength = (int) frameLength;
        }

        if (buffer.readableBytes() < currentFrameLength) { // frameLengthInt exist, just check buf
            return null;
        }

        if (initialBytesToStrip > currentFrameLength) {
            failOnFrameLengthLessThanInitialBytesToStrip(buffer, currentFrameLength, initialBytesToStrip);
        }

        buffer.skipReadable(initialBytesToStrip);

        // extract frame
        final Buffer frame = extractFrame(ctx, buffer, currentFrameLength - initialBytesToStrip);
        currentFrameLength = -1; // start processing the next frame
        return frame;
    }

    /**
     * Decodes the specified region of the buffer into an unadjusted frame length.  The default implementation is
     * capable of decoding the specified region into an unsigned 8/16/24/32/64 bit integer.  Override this method to
     * decode the length field encoded differently.  Note that this method must not modify the state of the specified
     * buffer (e.g. {@code readerOffset}, {@code writerOffset}, and the content of the buffer.)
     *
     * @throws DecoderException if failed to decode the specified region
     */
    protected long getUnadjustedFrameLength(Buffer buffer, int offset, int length) {
        final long frameLength;

        switch (length) {
        case 1:
            frameLength = buffer.getUnsignedByte(offset);
            break;
        case 2:
            frameLength = buffer.getUnsignedShort(offset);
            break;
        case 3:
            frameLength = buffer.getUnsignedMedium(offset);
            break;
        case 4:
            frameLength = buffer.getUnsignedInt(offset);
            break;
        case 8:
            frameLength = buffer.getLong(offset);
            break;
        default:
            throw new DecoderException(
                    "unsupported lengthFieldLength: " + lengthFieldLength + " (expected: 1, 2, 3, 4, or 8)");
        }

        return byteOrder == ByteOrder.LITTLE_ENDIAN? Long.reverseBytes(frameLength) : frameLength;
    }

    private void failOnLengthExceededIfNecessary(boolean firstDetectionOfTooLongFrame) {
        if (bytesToDiscard == 0) {
            // Reset to the initial state and tell the handlers that
            // the frame was too large.
            final long tooLongFrameLength = this.tooLongFrameLength;
            this.tooLongFrameLength = 0;

            if (!failFast || firstDetectionOfTooLongFrame) {
                failOnLengthExceeded(tooLongFrameLength);
            }
        } else {
            // Keep discarding and notify handlers if necessary.
            if (failFast && firstDetectionOfTooLongFrame) {
                failOnLengthExceeded(tooLongFrameLength);
            }
        }
    }

    private void failOnLengthExceeded(long frameLength) {
        if (frameLength > 0) {
            throw new TooLongFrameException(
                    "Adjusted frame length exceeds " + maxFrameLength + ": " + frameLength + " - discarded");
        } else {
            throw new TooLongFrameException("Adjusted frame length exceeds " + maxFrameLength + " - discarding");
        }
    }

    /**
     * Extract the sub-region of the specified buffer.  This method must modify the state of the buffer to continue
     * after the frame (e.g. by splitting the buffer or by increasing {@code readerOffset}).
     */
    protected Buffer extractFrame(@SuppressWarnings("unused") ChannelHandlerContext ctx, Buffer buffer, int length) {
        return buffer.readSplit(length);
    }

}
