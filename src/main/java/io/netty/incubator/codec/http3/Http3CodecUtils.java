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
import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.ObjectUtil;

final class Http3CodecUtils {
    static final long DEFAULT_MAX_HEADER_LIST_SIZE = 0xffffffffL;

    // See https://tools.ietf.org/html/draft-ietf-quic-http-32#section-7.2.8
    static final long MIN_RESERVED_FRAME_TYPE = 0x1f * 1 + 0x21;
    static final long MAX_RESERVED_FRAME_TYPE = 0x1f * (long) Integer.MAX_VALUE + 0x21;

    private Http3CodecUtils() { }

    static long checkIsReservedFrameType(long type) {
        return ObjectUtil.checkInRange(type, MIN_RESERVED_FRAME_TYPE, MAX_RESERVED_FRAME_TYPE, "type");
    }

    static boolean isReservedFrameType(long type) {
        return type >= MIN_RESERVED_FRAME_TYPE && type <= MAX_RESERVED_FRAME_TYPE;
    }

    static boolean isReservedHttp2(long type) {
        switch ((int) type) {
            // Reserved types that were used in HTTP/2
            case 0x2:
            case 0x6:
            case 0x8:
            case 0x9:
                return true;
            default:
                return false;
        }
    }

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

    static void criticalStreamClosed(ChannelHandlerContext ctx) {
        if (ctx.channel().parent().isActive()) {
            // Stream was closed while the parent channel is still active
            Http3CodecUtils.connectionError(
                    ctx, Http3ErrorCode.H3_CLOSED_CRITICAL_STREAM, "Critical stream closed.", false);
        }
    }

    /**
     * A connection-error should be handled as defined in the HTTP3 spec.
     * @param ctx           the {@link ChannelHandlerContext} of the handle that handles it.
     * @param exception     the {@link Http3Exception} that caused the error.
     * @param fireException {@code true} if we should also fire the {@link Http3Exception} through the pipeline.
     */
    static void connectionError(ChannelHandlerContext ctx, Http3Exception exception, boolean fireException) {
        if (fireException) {
            ctx.fireExceptionCaught(exception);
        }
        connectionError(ctx, exception.errorCode(), exception.getMessage());
    }

    /**
     * A connection-error should be handled as defined in the HTTP3 spec.
     * @param ctx           the {@link ChannelHandlerContext} of the handle that handles it.
     * @param errorCode     the {@link Http3ErrorCode} that caused the error.
     * @param msg           the message that should be used as reason for the error, may be {@code null}.
     * @param fireException {@code true} if we should also fire the {@link Http3Exception} through the pipeline.
     */
    static void connectionError(ChannelHandlerContext ctx, Http3ErrorCode errorCode,
                                String msg, boolean fireException) {
         if (fireException) {
             ctx.fireExceptionCaught(new Http3Exception(errorCode, msg));
         }
         connectionError(ctx, errorCode, msg);
    }

    private static void connectionError(ChannelHandlerContext ctx, Http3ErrorCode errorCode, String msg) {
        QuicChannel parent = (QuicChannel) ctx.channel().parent();
        final ByteBuf buffer;
        if (msg != null) {
            // As we call an operation on the parent we should also use the parents allocator to allocate the buffer.
            buffer = parent.alloc().buffer();
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
