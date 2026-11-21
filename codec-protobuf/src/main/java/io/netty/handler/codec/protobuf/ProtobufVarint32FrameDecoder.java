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
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.protobuf.internal.ProtobufVarint32;

import java.util.List;

import static io.netty.util.internal.ObjectUtil.checkPositive;

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
 */
public class ProtobufVarint32FrameDecoder extends ByteToMessageDecoder {

    private final int maxFrameLength;
    private long bytesToDiscard;

    /**
     * Creates a new instance with no frame length limit.
     */
    public ProtobufVarint32FrameDecoder() {
        this(Integer.MAX_VALUE);
    }

    /**
     * Creates a new instance with the specified maximum frame length.
     *
     * @param maxFrameLength the maximum length of the frame.
     *                       If the length exceeds this value,
     *                       {@link TooLongFrameException} will be thrown.
     */
    public ProtobufVarint32FrameDecoder(int maxFrameLength) {
        this.maxFrameLength = checkPositive(maxFrameLength, "maxFrameLength");
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
            throws Exception {
        if (bytesToDiscard > 0) {
            int localBytesToDiscard = (int) Math.min(bytesToDiscard, in.readableBytes());
            in.skipBytes(localBytesToDiscard);
            bytesToDiscard -= localBytesToDiscard;
            return;
        }

        in.markReaderIndex();
        int preIndex = in.readerIndex();
        int length = ProtobufVarint32.readRawVarint32(in);
        if (preIndex == in.readerIndex()) {
            return;
        }
        if (length < 0) {
            throw new CorruptedFrameException("negative length: " + length);
        }

        if (length > maxFrameLength) {
            long discard = length - in.readableBytes();
            if (discard <= 0) {
                in.skipBytes(length);
            } else {
                bytesToDiscard = discard;
                in.skipBytes(in.readableBytes());
            }
            throw new TooLongFrameException(
                    "Frame length exceeds " + maxFrameLength
                    + ": " + length);
        }

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
        } else {
            out.add(in.readRetainedSlice(length));
        }
    }
}
