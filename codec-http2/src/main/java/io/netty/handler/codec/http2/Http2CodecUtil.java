/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.format;
import static io.netty.util.CharsetUtil.UTF_8;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Constants and utility method used for encoding/decoding HTTP2 frames.
 */
public final class Http2CodecUtil {
    private static final ByteBuf CONNECTION_PREFACE_BUF = Unpooled.unmodifiableBuffer(Unpooled
            .copiedBuffer("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n", UTF_8));
    private static final ByteBuf EMPTY_PING_BUF = Unpooled.unmodifiableBuffer(Unpooled
            .copiedBuffer(new byte[8]));

    public static final int CONNECTION_STREAM_ID = 0;
    public static final int MAX_FRAME_PAYLOAD_LENGTH = 16383;
    public static final int PING_FRAME_PAYLOAD_LENGTH = 8;
    public static final short MAX_UNSIGNED_BYTE = 0xFF;
    public static final int MAX_UNSIGNED_SHORT = 0xFFFF;
    public static final long MAX_UNSIGNED_INT = 0xFFFFFFFFL;
    public static final int FRAME_HEADER_LENGTH = 8;
    public static final int FRAME_LENGTH_MASK = 0x3FFF;
    public static final int SETTING_ENTRY_LENGTH = 5;
    public static final int PRIORITY_ENTRY_LENGTH = 5;
    public static final int INT_FIELD_LENGTH = 4;

    public static final short SETTINGS_HEADER_TABLE_SIZE = 1;
    public static final short SETTINGS_ENABLE_PUSH = 2;
    public static final short SETTINGS_MAX_CONCURRENT_STREAMS = 3;
    public static final short SETTINGS_INITIAL_WINDOW_SIZE = 4;
    public static final short SETTINGS_COMPRESS_DATA = 5;

    public static final int DEFAULT_FLOW_CONTROL_WINDOW_SIZE = 65535;
    public static final short DEFAULT_PRIORITY_WEIGHT = 16;
    public static final int DEFAULT_HEADER_TABLE_SIZE = 4096;
    public static final int DEFAULT_MAX_HEADER_SIZE = 8192;

    /**
     * Returns a buffer containing the the {@link #CONNECTION_PREFACE}.
     */
    public static ByteBuf connectionPrefaceBuf() {
        // Return a duplicate so that modifications to the reader index will not affect the original
        // buffer.
        return CONNECTION_PREFACE_BUF.duplicate().retain();
    }

    /**
     * Returns a buffer filled with all zeros that is the appropriate length for a PING frame.
     */
    public static ByteBuf emptyPingBuf() {
        // Return a duplicate so that modifications to the reader index will not affect the original
        // buffer.
        return EMPTY_PING_BUF.duplicate().retain();
    }

    /**
     * Converts the given cause to a {@link Http2Exception} if it isn't already.
     */
    public static Http2Exception toHttp2Exception(Throwable cause) {
        if (cause instanceof Http2Exception) {
            return (Http2Exception) cause;
        }
        String msg = cause != null ? cause.getMessage() : "Failed writing the data frame.";
        return format(INTERNAL_ERROR, msg);
    }

    /**
     * Creates a buffer containing the error message from the given exception. If the cause is
     * {@code null} returns an empty buffer.
     */
    public static ByteBuf toByteBuf(ChannelHandlerContext ctx, Throwable cause) {
        if (cause == null || cause.getMessage() == null) {
            return Unpooled.EMPTY_BUFFER;
        }

        // Create the debug message.
        byte[] msg = cause.getMessage().getBytes(UTF_8);
        ByteBuf debugData = ctx.alloc().buffer(msg.length);
        debugData.writeBytes(msg);
        return debugData;
    }

    /**
     * Reads a big-endian (31-bit) integer from the buffer.
     */
    public static int readUnsignedInt(ByteBuf buf) {
        return (buf.readByte() & 0x7F) << 24 | (buf.readByte() & 0xFF) << 16
                | (buf.readByte() & 0xFF) << 8 | buf.readByte() & 0xFF;
    }

    /**
     * Writes a big-endian (32-bit) unsigned integer to the buffer.
     */
    public static void writeUnsignedInt(long value, ByteBuf out) {
        out.writeByte((int) ((value >> 24) & 0xFF));
        out.writeByte((int) ((value >> 16) & 0xFF));
        out.writeByte((int) ((value >> 8) & 0xFF));
        out.writeByte((int) ((value & 0xFF)));
    }

    /**
     * Writes a big-endian (16-bit) unsigned integer to the buffer.
     */
    public static void writeUnsignedShort(int value, ByteBuf out) {
        out.writeByte((int) ((value >> 8) & 0xFF));
        out.writeByte((int) ((value & 0xFF)));
    }

    /**
     * Fails the given promise with the cause and then re-throws the cause.
     */
    public static <T extends Throwable> T failAndThrow(ChannelPromise promise, T cause) throws T {
        if (!promise.isDone()) {
            promise.setFailure(cause);
        }
        throw cause;
    }

    private Http2CodecUtil() {
    }
}
