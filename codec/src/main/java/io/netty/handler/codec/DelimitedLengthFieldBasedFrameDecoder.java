/*
 * Copyright 2017 The Netty Project
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
package io.netty.handler.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.CharsetUtil;

import java.nio.charset.Charset;

/**
 * <p> Frames input by looking for a length field (in bytes) that is delimited, as opposed to being a fixed byte length.
 *  Only single-byte encoding (i.e. UTF-8) of the length field and delimiter characters are supported, although the
 * remaining frame can be anything (i.e. is not interpreted by this decoder). </p><p> Uses the same approach as the
 * {@link DelimiterBasedFrameDecoder} to find delimiter in input </p>
 */
public class DelimitedLengthFieldBasedFrameDecoder extends BaseLengthBasedFrameDecoder {

    public static final Charset CHARSET = CharsetUtil.UTF_8;

    private final int lengthAdjustment;
    private final ByteBuf delimiter;
    private final boolean trimLengthString;

    private boolean consumingLength = true;
    private long frameLength;

    private final int maxPossibleLengthInBytes;

    public DelimitedLengthFieldBasedFrameDecoder(
            int maxFrameLength,
            int lengthAdjustment,
            boolean failFast,
            ByteBuf delimiter,
            boolean trimLengthString
    ) {
        super(maxFrameLength, failFast);
        if (delimiter == null) {
            throw new NullPointerException("delimiter");
        }
        if (!delimiter.isReadable()) {
            throw new IllegalArgumentException("empty delimiter");
        }

        this.delimiter = delimiter.slice(delimiter.readerIndex(), delimiter.readableBytes());
        this.lengthAdjustment = lengthAdjustment;
        this.trimLengthString = trimLengthString;

        maxPossibleLengthInBytes = String.valueOf(maxFrameLength).length();
    }

    protected int indexWithin(ByteBuf haystack, ByteBuf needle) {
        return DelimiterBasedFrameDecoder.indexWithin(haystack, needle);
    }

    /**
     * Create a frame out of the {@link ByteBuf} and return it.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link ByteToMessageDecoder} belongs to
     * @param in the {@link ByteBuf} from which to read data
     *
     * @return the {@link ByteBuf} which represents the frame or {@code null} if no frame could be created.
     */
    @Override
    protected Object decode(@SuppressWarnings({ "unused", "squid:S1172" }) ChannelHandlerContext ctx, ByteBuf in)
            throws Exception {
        if (discardingTooLongFrame) {
            long bytesToDiscard = this.bytesToDiscard;
            int localBytesToDiscard = (int) Math.min(bytesToDiscard, in.readableBytes());
            in.skipBytes(localBytesToDiscard);
            bytesToDiscard -= localBytesToDiscard;
            this.bytesToDiscard = bytesToDiscard;

            failIfNecessary(false);

            // if exception was not thrown, reader has been advanced by readable bytes already, so return null to
            // continue processing too long frame
            return null;
        }

        if (consumingLength) {
            int delimIndex = indexWithin(in, delimiter);
            if (delimIndex < 0) {
                return null;
            }

            frameLength = extractFrameLength(in, delimIndex);

            if (frameLength < 0) {
                throw new CorruptedFrameException("negative pre-adjustment length field: " + frameLength);
            }

            // this will not overflow because lengthAdjustment is an int
            frameLength += lengthAdjustment;

            //consume length field and delimiter bytes
            in.skipBytes(delimIndex + delimiter.capacity());

            //consume delimiter bytes
            consumingLength = false;
        }

        if (frameLength > maxFrameLength) {
            long discard = frameLength - in.readableBytes();
            tooLongFrameLength = frameLength;

            if (discard < 0) {
                // buffer contains more bytes then the frameLength so we can discard all now
                // will not overflow because frameLength must be less than in.readableBytes, an int
                in.skipBytes((int) frameLength);
            } else {
                // Enter the discard mode and discard everything received so far.
                discardingTooLongFrame = true;
                bytesToDiscard = discard;
                in.skipBytes(in.readableBytes());
            }
            failIfNecessary(true);
            return null;
        }

        // never overflows because it's less than maxFrameLength
        int frameLengthInt = (int) frameLength;
        if (in.readableBytes() < frameLengthInt) {
            // need more bytes available to read actual frame
            return null;
        }

        // extract frame
        int readerIndex = in.readerIndex();
        ByteBuf frame = extractFrame(in, readerIndex, frameLengthInt);
        in.readerIndex(readerIndex + frameLengthInt);

        // the frame is now entirely present, reset state vars
        consumingLength = true;
        frameLength = 0;

        return frame;
    }

    private int extractFrameLength(ByteBuf in, int delimIndex) {
        if (delimIndex > maxPossibleLengthInBytes) {
            throw new TooLongFrameException(
                    "Length field delimiter offset (" + delimIndex + ") exceeds maximum possible byte length for" +
                    " specified frame size (" + maxPossibleLengthInBytes + ")"
            );
        }
        final String lengthStr = in.toString(in.readerIndex(), delimIndex, CHARSET);
        try {
            return Integer.parseInt(trimLengthString? lengthStr.trim() : lengthStr);

        } catch (NumberFormatException e) {
            throw new CorruptedFrameException(
                    String.format(
                            "Invalid length field decoded: %s",
                            lengthStr
                    ),
                    e
            );
        }
    }

    @Override
    protected void resetStateOnTooLongFrame() {
        consumingLength = true;
        frameLength = 0;
    }
}
