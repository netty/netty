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

package io.netty.handler.codec.http2.draft10.connection;

import static io.netty.handler.codec.http2.draft10.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.draft10.Http2Exception.format;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.draft10.Http2Exception;

/**
 * Constants and utility method used for encoding/decoding HTTP2 frames.
 */
public final class Http2ConnectionUtil {

    public static final int DEFAULT_FLOW_CONTROL_WINDOW_SIZE = 65535;
    public static final int DEFAULT_HEADER_TABLE_SIZE = 4096;
    public static final int DEFAULT_MAX_HEADER_SIZE = 4096;

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
        ByteBuf debugData = Unpooled.EMPTY_BUFFER;
        if (cause != null) {
            // Create the debug message.
            byte[] msg = cause.getMessage().getBytes();
            debugData = ctx.alloc().buffer(msg.length);
            debugData.writeBytes(msg);
        }
        return debugData;
    }

    private Http2ConnectionUtil() {
    }
}
