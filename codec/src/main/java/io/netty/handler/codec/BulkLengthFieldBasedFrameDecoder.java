/*
 * Copyright 2016 The Netty Project
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
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * A decoder that mostly mimics {@link LengthFieldBasedFrameDecoder} semantics. The only difference is this decoder
 * batches incoming {@link ByteBuf} messages and then decodes them at once, passing a big {@link ByteBuf} which can
 * possibly contain N messages (where N >= 1) through the pipeline at once.
 */
public class BulkLengthFieldBasedFrameDecoder extends BulkFrameDecoder {

    private final int maxFrameLength;
    private final int lengthFieldOffset;
    private final int lengthFieldLength;
    private final int lengthFieldEndOffset;

    /**
     * Creates a new instance.
     *
     * @param maxFrameLength the maximum length of the frame.  If the length of the frame is greater than this value,
     * {@link TooLongFrameException} will be thrown.
     * @param lengthFieldOffset the offset of the length field
     * @param lengthFieldLength the length of the length field
     */
    public BulkLengthFieldBasedFrameDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength) {
        this(0, maxFrameLength, lengthFieldOffset, lengthFieldLength);
    }

    /**
     * Creates a new instance.
     *
     * @param startBufferSize the start buffer size which is used for batching incoming {@link ByteBuf} messages. Size
     * of this buffer must be greater or equal to zero. If the size of this buffer is zero then the default size will be
     * used (= 256 bytes).
     * @param maxFrameLength the maximum length of the frame.  If the length of the frame is greater than this value,
     * {@link TooLongFrameException} will be thrown.
     * @param lengthFieldOffset the offset of the length field
     * @param lengthFieldLength the length of the length field
     */
    public BulkLengthFieldBasedFrameDecoder(int startBufferSize, int maxFrameLength, int lengthFieldOffset,
                                            int lengthFieldLength) {
        super(startBufferSize);

        if (maxFrameLength <= 0) {
            throw new IllegalArgumentException(
                    "maxFrameLength must be a positive integer: " +
                    maxFrameLength);
        }

        if (lengthFieldOffset < 0) {
            throw new IllegalArgumentException(
                    "lengthFieldOffset must be a non-negative integer: " +
                    lengthFieldOffset);
        }

        if (lengthFieldOffset > maxFrameLength - lengthFieldLength) {
            throw new IllegalArgumentException(
                    "maxFrameLength (" + maxFrameLength + ") " +
                    "must be equal to or greater than " +
                    "lengthFieldOffset (" + lengthFieldOffset + ") + " +
                    "lengthFieldLength (" + lengthFieldLength + ").");
        }

        this.maxFrameLength = maxFrameLength;
        this.lengthFieldOffset = lengthFieldOffset;
        this.lengthFieldLength = lengthFieldLength;
        lengthFieldEndOffset = lengthFieldOffset + lengthFieldLength;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) {
        int readableBytes = buffer.readableBytes();
        int rdIdx = buffer.readerIndex();

        while (readableBytes >= lengthFieldEndOffset) {
            int actualLengthFieldOffset = rdIdx + lengthFieldOffset;
            long frameLength = getUnadjustedFrameLength(buffer, actualLengthFieldOffset, lengthFieldLength);

            if (frameLength < 0) {
                throw new CorruptedFrameException(
                        "negative pre-adjustment length field: " + frameLength);
            }

            frameLength += lengthFieldEndOffset;

            if (frameLength > maxFrameLength) {
                throw new TooLongFrameException(
                        "Adjusted frame length exceeds " + maxFrameLength +
                        ": " + frameLength + " - discarded");
            }

            int frameLengthInt = (int) frameLength;

            if (readableBytes < frameLengthInt) {
                break;
            }

            readableBytes -= frameLengthInt;
            rdIdx += frameLengthInt;
        }

        if (rdIdx == 0) {
            return;
        }

        final int previousWriterIndex = buffer.writerIndex();

        buffer.writerIndex(rdIdx);

        final ByteBuf chunk = ctx.alloc().buffer(rdIdx, rdIdx).writeBytes(buffer, rdIdx);

        buffer.writerIndex(previousWriterIndex);
        buffer.discardReadBytes();

        out.add(chunk);
    }

    protected long getUnadjustedFrameLength(ByteBuf buf, int offset, int length) {
        long frameLength;
        switch (length) {
        case 1:
            frameLength = buf.getUnsignedByte(offset);
            break;
        case 2:
            frameLength = buf.getUnsignedShort(offset);
            break;
        case 3:
            frameLength = buf.getUnsignedMedium(offset);
            break;
        case 4:
            frameLength = buf.getUnsignedInt(offset);
            break;
        case 8:
            frameLength = buf.getLong(offset);
            break;
        default:
            throw new DecoderException(
                    "unsupported lengthFieldLength: " + lengthFieldLength + " (expected: 1, 2, 3, 4, or 8)");
        }
        return frameLength;
    }
}
