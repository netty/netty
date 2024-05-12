/*
 * Copyright 2015 The Netty Project
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
package io.netty.handler.codec.protobuf;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.nano.CodedInputByteBufferNano;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;

import java.util.List;

/**
 * A decoder that splits the received {@link ByteBuf}s dynamically by the
 * value of the Google Protocol Buffers
 * <a href="https://developers.google.com/protocol-buffers/docs/encoding#varints">Base
 * 128 Varints</a> integer length field in the message. For example:
 * <pre>
 * BEFORE DECODE (302 bytes)       AFTER DECODE (300 bytes)
 * +--------+---------------+      +---------------+
 * | Length | Protobuf Data |----->| Protobuf Data |
 * | 0xAC02 |  (300 bytes)  |      |  (300 bytes)  |
 * +--------+---------------+      +---------------+
 * </pre>
 *
 * @see CodedInputStream
 * @see CodedInputByteBufferNano
 */
public class ProtobufVarint32FrameDecoder extends ByteToMessageDecoder {

    // TODO maxFrameLength + safe skip + fail-fast option
    //      (just like LengthFieldBasedFrameDecoder)

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
            throws Exception {
        in.markReaderIndex();
        int preIndex = in.readerIndex();
        int length = readRawVarint32(in);
        if (preIndex == in.readerIndex()) {
            return;
        }
        if (length < 0) {
            throw new CorruptedFrameException("negative length: " + length);
        }

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
        } else {
            out.add(in.readRetainedSlice(length));
        }
    }

    /**
     * Reads variable length 32bit int from buffer
     *
     * @return decoded int if buffers readerIndex has been forwarded else nonsense value
     */
    static int readRawVarint32(ByteBuf buffer) {
        if (buffer.readableBytes() < 4) {
            return readRawVarint24(buffer);
        }
        int wholeOrMore = buffer.getIntLE(buffer.readerIndex());
        int firstOneOnStop = ~wholeOrMore & 0x80808080;
        if (firstOneOnStop == 0) {
            return readRawVarint40(buffer, wholeOrMore);
        }
        int bitsToKeep = Integer.numberOfTrailingZeros(firstOneOnStop) + 1;
        buffer.skipBytes(bitsToKeep >> 3);
        int shiftToHighlightTheWhole = 32 - bitsToKeep;
        // this is not brilliant, we have a data dependency here
        int wholeWithContinuations = (wholeOrMore << shiftToHighlightTheWhole) >> shiftToHighlightTheWhole;
        // mix them up as per varint spec while dropping the continuation bits
        int result = wholeWithContinuations & 0x7F |
                     (((wholeWithContinuations >> 8) & 0x7F) << 7) |
                     (((wholeWithContinuations >> 16) & 0x7F) << 14) |
                     (((wholeWithContinuations >> 24) & 0x7F) << 21);
        return result;
    }

    private static int readRawVarint40(ByteBuf buffer, int wholeOrMore) {
        byte lastByte;
        if (buffer.readableBytes() == 4 || (lastByte = buffer.getByte(buffer.readerIndex() + 4)) < 0) {
            throw new CorruptedFrameException("malformed varint.");
        }
        buffer.skipBytes(5);
        // add it to wholeOrMore
        return wholeOrMore & 0x7F |
               (((wholeOrMore >> 8) & 0x7F) << 7) |
               (((wholeOrMore >> 16) & 0x7F) << 14) |
               (((wholeOrMore >> 24) & 0x7F) << 21) |
               (lastByte << 28);
    }

    private static int readRawVarint24(ByteBuf buffer) {
        if (!buffer.isReadable()) {
            return 0;
        }
        buffer.markReaderIndex();

        byte tmp = buffer.readByte();
        if (tmp >= 0) {
            return tmp;
        }
        int result = tmp & 127;
        if (!buffer.isReadable()) {
            buffer.resetReaderIndex();
            return 0;
        }
        if ((tmp = buffer.readByte()) >= 0) {
            return result | tmp << 7;
        }
        result |= (tmp & 127) << 7;
        if (!buffer.isReadable()) {
            buffer.resetReaderIndex();
            return 0;
        }
        if ((tmp = buffer.readByte()) >= 0) {
            return result | tmp << 14;
        }
        return result | (tmp & 127) << 14;
    }
}
