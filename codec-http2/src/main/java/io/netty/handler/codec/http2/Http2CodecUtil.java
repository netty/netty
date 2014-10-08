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
import static io.netty.util.CharsetUtil.UTF_8;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.Http2StreamRemovalPolicy.Action;

/**
 * Constants and utility method used for encoding/decoding HTTP2 frames.
 */
public final class Http2CodecUtil {

    private static final byte[] CONNECTION_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(UTF_8);
    private static final byte[] EMPTY_PING = new byte[8];
    private static IgnoreSettingsHandler ignoreSettingsHandler = new IgnoreSettingsHandler();

    public static final int CONNECTION_STREAM_ID = 0;
    public static final int HTTP_UPGRADE_STREAM_ID = 1;
    public static final String HTTP_UPGRADE_SETTINGS_HEADER = "HTTP2-Settings";
    public static final String HTTP_UPGRADE_PROTOCOL_NAME = "h2c-14";
    public static final String TLS_UPGRADE_PROTOCOL_NAME = "h2-14";

    public static final int PING_FRAME_PAYLOAD_LENGTH = 8;
    public static final short MAX_UNSIGNED_BYTE = 0xFF;
    public static final int MAX_UNSIGNED_SHORT = 0xFFFF;
    public static final long MAX_UNSIGNED_INT = 0xFFFFFFFFL;
    public static final int FRAME_HEADER_LENGTH = 9;
    public static final int SETTING_ENTRY_LENGTH = 6;
    public static final int PRIORITY_ENTRY_LENGTH = 5;
    public static final int INT_FIELD_LENGTH = 4;
    public static final short MAX_WEIGHT = (short) 256;
    public static final short MIN_WEIGHT = (short) 1;

    public static final int SETTINGS_HEADER_TABLE_SIZE = 1;
    public static final int SETTINGS_ENABLE_PUSH = 2;
    public static final int SETTINGS_MAX_CONCURRENT_STREAMS = 3;
    public static final int SETTINGS_INITIAL_WINDOW_SIZE = 4;
    public static final int SETTINGS_MAX_FRAME_SIZE = 5;
    public static final int SETTINGS_MAX_HEADER_LIST_SIZE = 6;

    public static final int MAX_FRAME_SIZE_LOWER_BOUND = 0x4000;
    public static final int MAX_FRAME_SIZE_UPPER_BOUND = 0xFFFFFF;

    public static final int DEFAULT_WINDOW_SIZE = 65535;
    public static final boolean DEFAULT_ENABLE_PUSH = true;
    public static final short DEFAULT_PRIORITY_WEIGHT = 16;
    public static final int DEFAULT_HEADER_TABLE_SIZE = 4096;
    public static final int DEFAULT_MAX_HEADER_SIZE = 8192;
    public static final int DEFAULT_MAX_FRAME_SIZE = MAX_FRAME_SIZE_LOWER_BOUND;

    /**
     * Indicates whether or not the given value for max frame size falls within the valid range.
     */
    public static boolean isMaxFrameSizeValid(int maxFrameSize) {
        return maxFrameSize >= MAX_FRAME_SIZE_LOWER_BOUND
                && maxFrameSize <= MAX_FRAME_SIZE_UPPER_BOUND;
    }

    /**
     * Returns a buffer containing the the {@link #CONNECTION_PREFACE}.
     */
    public static ByteBuf connectionPrefaceBuf() {
        // Return a duplicate so that modifications to the reader index will not affect the original
        // buffer.
        return Unpooled.wrappedBuffer(CONNECTION_PREFACE);
    }

    /**
     * Returns a buffer filled with all zeros that is the appropriate length for a PING frame.
     */
    public static ByteBuf emptyPingBuf() {
        // Return a duplicate so that modifications to the reader index will not affect the original
        // buffer.
        return Unpooled.wrappedBuffer(EMPTY_PING);
    }

    /**
     * Returns a simple {@link Http2StreamRemovalPolicy} that immediately calls back the
     * {@link Action} when a stream is marked for removal.
     */
    public static Http2StreamRemovalPolicy immediateRemovalPolicy() {
        return new Http2StreamRemovalPolicy() {
            private Action action;

            @Override
            public void setAction(Action action) {
                this.action = checkNotNull(action, "action");
            }

            @Override
            public void markForRemoval(Http2Stream stream) {
                if (action == null) {
                    throw new IllegalStateException(
                            "Action must be called before removing streams.");
                }
                action.removeStream(stream);
            }
        };
    }

    /**
     * Creates a new {@link ChannelHandler} that does nothing but ignore inbound settings frames.
     * This is a useful utility to avoid verbose logging output for pipelines that don't handle
     * settings frames directly.
     */
    public static ChannelHandler ignoreSettingsHandler() {
        return ignoreSettingsHandler;
    }

    /**
     * Converts the given cause to a {@link Http2Exception} if it isn't already.
     */
    public static Http2Exception toHttp2Exception(Throwable cause) {
        if (cause instanceof Http2Exception) {
            return (Http2Exception) cause;
        }

        // Look for an embedded Http2Exception to see the appropriate error to use.
        Http2Error error = INTERNAL_ERROR;
        Http2Exception embedded = getEmbeddedHttp2Exception(cause);
        if (embedded != null) {
            error = embedded.error();
        }

        // Wrap the cause.
        if (embedded instanceof Http2StreamException) {
            int streamId = ((Http2StreamException) embedded).streamId();
            return new Http2StreamException(streamId, error, cause.getMessage(), cause);
        } else {
            return new Http2Exception(error, cause.getMessage(), cause);
        }
    }

    /**
     * Iteratively looks through the causaility chain for the given exception and returns the first
     * {@link Http2Exception} or {@code null} if none.
     */
    public static Http2Exception getEmbeddedHttp2Exception(Throwable cause) {
        while (cause != null) {
            if (cause instanceof Http2Exception) {
                return (Http2Exception) cause;
            }
            cause = cause.getCause();
        }
        return null;
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
        out.writeByte((int) (value >> 24 & 0xFF));
        out.writeByte((int) (value >> 16 & 0xFF));
        out.writeByte((int) (value >> 8 & 0xFF));
        out.writeByte((int) (value & 0xFF));
    }

    /**
     * Writes a big-endian (16-bit) unsigned integer to the buffer.
     */
    public static void writeUnsignedShort(int value, ByteBuf out) {
        out.writeByte(value >> 8 & 0xFF);
        out.writeByte(value & 0xFF);
    }

    /**
     * Writes an HTTP/2 frame header to the output buffer.
     */
    public static void writeFrameHeader(ByteBuf out, int payloadLength, byte type,
            Http2Flags flags, int streamId) {
        out.ensureWritable(FRAME_HEADER_LENGTH + payloadLength);
        out.writeMedium(payloadLength);
        out.writeByte(type);
        out.writeByte(flags.value());
        out.writeInt(streamId);
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

    /**
     * A{@link ChannelHandler} that does nothing but ignore inbound settings frames. This is a
     * useful utility to avoid verbose logging output for pipelines that don't handle settings
     * frames directly.
     */
    @ChannelHandler.Sharable
    private static class IgnoreSettingsHandler extends ChannelHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (!(msg instanceof Http2Settings)) {
                super.channelRead(ctx, msg);
            }
        }
    }

    private Http2CodecUtil() {
    }
}
