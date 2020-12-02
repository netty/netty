/*
 * Copyright 2020 The Netty Project
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
package io.netty.incubator.codec.http3;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.util.CharsetUtil;

final class Http3CodecUtils {

    private Http3CodecUtils() { }

    /**
     * Returns the number of bytes needed to encode the variable length integer.
     *
     * See <a href="https://tools.ietf.org/html/draft-ietf-quic-transport-32#section-16">
     *     Variable-Length Integer Encoding</a>.
     */
    static int numBytesForVariableLengthInteger(long value) {
        if (value <= 63) {
            return 1;
        }
        if (value <= 16383) {
            return 2;
        }
        if (value <= 1073741823) {
            return 4;
        }
        if (value <= 4611686018427387903L) {
            return 8;
        }
        throw new IllegalArgumentException();
    }

    /**
     * Write the variable length integer into the {@link ByteBuf}.
     *
     * See <a href="https://tools.ietf.org/html/draft-ietf-quic-transport-32#section-16">
     *     Variable-Length Integer Encoding</a>.
     */
    static void writeVariableLengthInteger(ByteBuf out, long value) {
        int numBytes = numBytesForVariableLengthInteger(value);
        writeVariableLengthInteger(out, value, numBytes);
    }

    /**
     * Write the variable length integer into the {@link ByteBuf}.
     *
     * See <a href="https://tools.ietf.org/html/draft-ietf-quic-transport-32#section-16">
     *     Variable-Length Integer Encoding</a>.
     */
    static void writeVariableLengthInteger(ByteBuf out, long value, int numBytes) {
        int writerIndex = out.writerIndex();
        switch (numBytes) {
            case 1:
                out.writeByte((byte) value);
                break;
            case 2:
                out.writeShort((short) value);
                encodeLengthIntoBuffer(out, writerIndex, (byte) 0x40);
                break;
            case 4:
                out.writeInt((int) value);
                encodeLengthIntoBuffer(out, writerIndex, (byte) 0x80);
                break;
            case 8:
                out.writeLong(value);
                encodeLengthIntoBuffer(out, writerIndex, (byte) 0xc0);
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static void encodeLengthIntoBuffer(ByteBuf out, int index, byte b) {
        out.setByte(index, out.getByte(index) | b);
    }

    /**
     * Read the variable length integer from the {@link ByteBuf}.
     *
     * See <a href="https://tools.ietf.org/html/draft-ietf-quic-transport-32#section-16">
     *     Variable-Length Integer Encoding </a>
     */
    static long readVariableLengthInteger(ByteBuf in, int len) {
        switch (len) {
            case 1:
                return in.readUnsignedByte();
            case 2:
                return in.readUnsignedShort() & 0x3fff;
            case 4:
                return in.readUnsignedInt() & 0x3fffffff;
            case 8:
                return in.readLong() & 0x3fffffffffffffffL;
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * Returns the number of bytes that were encoded into the byte for a variable length integer to read.
     *
     * See <a href="https://tools.ietf.org/html/draft-ietf-quic-transport-32#section-16">
     *     Variable-Length Integer Encoding </a>
     */
     static int numBytesForVariableLengthInteger(byte b) {
        byte val = (byte) (b >> 6);
        if ((val & 1) != 0) {
            if ((val & 2) != 0) {
                return 8;
            }
            return 2;
        }
        if ((val & 2) != 0) {
            return 4;
        }
        return 1;
    }

    static void closeParent(Channel channel, Http3ErrorCode errorCode, String msg) {
        QuicChannel parent = (QuicChannel) channel.parent();
        final ByteBuf buffer;
        if (msg != null) {
            buffer = parent.alloc().directBuffer();
            buffer.writeCharSequence(msg, CharsetUtil.US_ASCII);
        } else {
            buffer = Unpooled.EMPTY_BUFFER;
        }
        parent.close(true, errorCode.code, buffer);
    }

    static void readIfNoAutoRead(ChannelHandlerContext ctx) {
        if (!ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
    }
}
