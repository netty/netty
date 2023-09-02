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
package io.netty.handler.codec;

import static io.netty.util.internal.ObjectUtil.checkPositive;

import java.nio.charset.Charset;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * A decoder that implements the Octet Counting framing mechanism described in
 * <a href="https://datatracker.ietf.org/doc/html/rfc6587#section-3.4.1">RFC 6587 section 3.4.1</a>.
 * Octet Counting is a method for framing messages by prefixing each message with its length.
 * <p>
 * This class extends {@link ByteToMessageDecoder} to decode the incoming {@link ByteBuf} based on the
 * Octet Counting framing. The core logic of this class is heavily inspired by {@link LengthFieldBasedFrameDecoder}.
 * </p>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6587#section-3.4.1">RFC 6587 section 3.4.1</a>
 * @see LengthFieldBasedFrameDecoder
 */
public class OctetCountingFrameDecoder extends ByteToMessageDecoder {

    private final int maxFrameLength;
    private final int maxLengthFieldLength;
    private final boolean failFast;
    private final ByteToMessageDecoder secondaryDecoder;
    private boolean discardingTooLongFrame;
    private long tooLongFrameLength;
    private long bytesToDiscard;
    private int frameLengthInt = -1;

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength       the maximum length of the frame.  If the length of the frame is
     *                             greater than this value, {@link TooLongFrameException} will be
     *                             thrown.
     * @param maxLengthFieldLength the maximum length of the length string
     * @param secondaryDecoder     decoder used when OctetCounting fail
     */
    public OctetCountingFrameDecoder(
            int maxFrameLength, int maxLengthFieldLength, ByteToMessageDecoder secondaryDecoder) {
        this(maxFrameLength, maxLengthFieldLength, secondaryDecoder, true);
    }

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength       the maximum length of the frame.  If the length of the frame is
     *                             greater than this value, {@link TooLongFrameException} will be
     *                             thrown.
     * @param maxLengthFieldLength the length of the length field
     * @param secondaryDecoder     decoder used when OctetCounting fail
     * @param failFast             If <tt>true</tt>, a {@link TooLongFrameException} is thrown as
     *                             soon as the decoder notices the length of the frame will exceed
     *                             <tt>maxFrameLength</tt> regardless of whether the entire frame
     *                             has been read.  If <tt>false</tt>, a {@link TooLongFrameException}
     */
    public OctetCountingFrameDecoder(
            int maxFrameLength, int maxLengthFieldLength,
            ByteToMessageDecoder secondaryDecoder, boolean failFast) {

        checkPositive(maxFrameLength, "maxFrameLength");

        this.secondaryDecoder = secondaryDecoder;
        this.maxFrameLength = maxFrameLength;
        this.maxLengthFieldLength = maxLengthFieldLength;
        this.failFast = failFast;
    }

    /**
     * Determines the frame length based on octet counting method as per RFC 6587 section 3.4.1.
     * This method reads bytes from the buffer until it identifies the length of the frame. The buffer's
     * reader index will be updated upon successful completion.
     *
     * @param buf       ByteBuf containing the data to be decoded
     * @param maxLength The maximum allowed length for the length field
     * @return The length of the frame if it can be determined; -1 if the data is incomplete
     * @throws CorruptedFrameException if the frame doesn't start with a valid octet count number
     * @throws DecoderException        if the length field exceeds the maximum allowed field length
     */
    protected static long getFrameLength(ByteBuf buf, int maxLength) {
        int offset = buf.readerIndex();
        char c = (char) buf.getByte(offset);
        int i = 0;
        if (!('1' <= c && c <= '9')) {
            throw new CorruptedFrameException(
                    "OctetCounting frames should start with a digit other than 0, but got '" + c + "'.");
        }
        do {
            i++;
            if (i > maxLength) {
                throw new DecoderException("Length field exceed max field length : " + maxLength);
            }
            if (!buf.isReadable(i + 1)) {
                return -1;
            }
            c = (char) buf.getByte(offset + i);
        } while (Character.isDigit(c));
        if (c != ' ') {
            throw new CorruptedFrameException(
                    "OctetCounting frames should start with a number and a space, but got '" +
                            c + "' after the number.");
        }
        buf.skipBytes(i + 1);
        return Integer.parseInt(buf.toString(offset, i, Charset.defaultCharset()));
    }

    @Override
    protected final void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            Object decoded = decode(ctx, in);
            if (decoded != null) {
                out.add(decoded);
            }
        } catch (DecoderException e) {
            if (secondaryDecoder != null) {
                secondaryDecoder.decode(ctx, in, out);
            } else {
                throw e;
            }
        }
    }

    private void discardingTooLongFrame(ByteBuf in) {
        long bytesToDiscard = this.bytesToDiscard;
        int localBytesToDiscard = (int) Math.min(bytesToDiscard, in.readableBytes());
        in.skipBytes(localBytesToDiscard);
        bytesToDiscard -= localBytesToDiscard;
        this.bytesToDiscard = bytesToDiscard;

        failIfNecessary(false);
    }

    private void exceededFrameLength(ByteBuf in, long frameLength) {
        long discard = frameLength - in.readableBytes();
        tooLongFrameLength = frameLength;

        if (discard < 0) {
            // buffer contains more bytes then the frameLength so we can discard all now
            in.skipBytes((int) frameLength);
        } else {
            // Enter the discard mode and discard everything received so far.
            discardingTooLongFrame = true;
            bytesToDiscard = discard;
            in.skipBytes(in.readableBytes());
        }
        failIfNecessary(true);
    }

    /**
     * Create a frame out of the {@link ByteBuf} and return it.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in  the {@link ByteBuf} from which to read data
     * @return frame           the {@link ByteBuf} which represent the frame or {@code null} if no frame could
     * be created.
     */
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        long frameLength = 0;
        if (frameLengthInt == -1) { // new frame

            if (discardingTooLongFrame) {
                discardingTooLongFrame(in);
            }

            frameLength = getFrameLength(in, maxLengthFieldLength);

            if (frameLength == -1) { //wait for more byte
                return null;
            }

            if (frameLength > maxFrameLength) {
                exceededFrameLength(in, frameLength);
                return null;
            }
            // never overflows because it's less than maxFrameLength
            frameLengthInt = (int) frameLength;
        }
        if (in.readableBytes() < frameLengthInt) { // frameLengthInt exist , just check buf
            return null;
        }

        // extract frame
        int readerIndex = in.readerIndex();
        ByteBuf frame = in.retainedSlice(readerIndex, frameLengthInt);
        in.readerIndex(readerIndex + frameLengthInt);
        frameLengthInt = -1; // start processing the next frame
        return frame;
    }

    private void failIfNecessary(boolean firstDetectionOfTooLongFrame) {
        if (bytesToDiscard == 0) {
            // Reset to the initial state and tell the handlers that
            // the frame was too large.
            long tooLongFrameLength = this.tooLongFrameLength;
            this.tooLongFrameLength = 0;
            discardingTooLongFrame = false;
            if (!failFast || firstDetectionOfTooLongFrame) {
                fail(tooLongFrameLength);
            }
        } else {
            // Keep discarding and notify handlers if necessary.
            if (failFast && firstDetectionOfTooLongFrame) {
                fail(tooLongFrameLength);
            }
        }
    }

    private void fail(long frameLength) {
        if (frameLength > 0) {
            throw new TooLongFrameException(
                    "Frame length exceeds " + maxFrameLength +
                            ": " + frameLength + " - discarded");
        } else {
            throw new TooLongFrameException(
                    "Frame length exceeds " + maxFrameLength +
                            " - discarding");
        }
    }
}
