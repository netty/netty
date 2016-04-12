/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http2;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.logging.LogLevel;
import io.netty.util.internal.UnstableApi;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Logs HTTP2 frames for debugging purposes.
 */
@UnstableApi
public class Http2FrameLogger extends ChannelHandlerAdapter {

    public enum Direction {
        INBOUND,
        OUTBOUND
    }

    private static final int BUFFER_LENGTH_THRESHOLD = 64;
    private final InternalLogger logger;
    private final InternalLogLevel level;

    public Http2FrameLogger(LogLevel level) {
        this(level.toInternalLevel(), InternalLoggerFactory.getInstance(Http2FrameLogger.class));
    }

    public Http2FrameLogger(LogLevel level, String name) {
        this(level.toInternalLevel(), InternalLoggerFactory.getInstance(name));
    }

    public Http2FrameLogger(LogLevel level, Class<?> clazz) {
        this(level.toInternalLevel(), InternalLoggerFactory.getInstance(clazz));
    }

    private Http2FrameLogger(InternalLogLevel level, InternalLogger logger) {
        this.level = checkNotNull(level, "level");
        this.logger = checkNotNull(logger, "logger");
    }

    public void logData(Direction direction, ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
            boolean endStream) {
        if (enabled()) {
            log(direction,
                    "%s DATA: streamId=%d, padding=%d, endStream=%b, length=%d, bytes=%s",
                    ctx.channel(), streamId, padding, endStream, data.readableBytes(), toString(data));
        }
    }

    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int padding, boolean endStream) {
        if (enabled()) {
            log(direction, "%s HEADERS: streamId=%d, headers=%s, padding=%d, endStream=%b",
                    ctx.channel(), streamId, headers, padding, endStream);
        }
    }

    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) {
        if (enabled()) {
            log(direction,
                    "%s HEADERS: streamId=%d, headers=%s, streamDependency=%d, weight=%d, "
                            + "exclusive=%b, padding=%d, endStream=%b",
                    ctx.channel(), streamId, headers, streamDependency, weight, exclusive, padding, endStream);
        }
    }

    public void logPriority(Direction direction, ChannelHandlerContext ctx, int streamId, int streamDependency,
            short weight, boolean exclusive) {
        if (enabled()) {
            log(direction, "%s PRIORITY: streamId=%d, streamDependency=%d, weight=%d, exclusive=%b",
                    ctx.channel(), streamId, streamDependency, weight, exclusive);
        }
    }

    public void logRstStream(Direction direction, ChannelHandlerContext ctx, int streamId, long errorCode) {
        if (enabled()) {
            log(direction, "%s RST_STREAM: streamId=%d, errorCode=%d", ctx.channel(), streamId, errorCode);
        }
    }

    public void logSettingsAck(Direction direction, ChannelHandlerContext ctx) {
        if (enabled()) {
            log(direction, "%s SETTINGS: ack=true", ctx.channel());
        }
    }

    public void logSettings(Direction direction, ChannelHandlerContext ctx, Http2Settings settings) {
        if (enabled()) {
            log(direction, "%s SETTINGS: ack=false, settings=%s", ctx.channel(), settings);
        }
    }

    public void logPing(Direction direction, ChannelHandlerContext ctx, ByteBuf data) {
        if (enabled()) {
            log(direction, "%s PING: ack=false, length=%d, bytes=%s", ctx.channel(),
                    data.readableBytes(), toString(data));
        }
    }

    public void logPingAck(Direction direction, ChannelHandlerContext ctx, ByteBuf data) {
        if (enabled()) {
            log(direction, "%s PING: ack=true, length=%d, bytes=%s",
                    ctx.channel(), data.readableBytes(), toString(data));
        }
    }

    public void logPushPromise(Direction direction, ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding) {
        if (enabled()) {
            log(direction, "%s PUSH_PROMISE: streamId=%d, promisedStreamId=%d, headers=%s, padding=%d",
                    ctx.channel(), streamId, promisedStreamId, headers, padding);
        }
    }

    public void logGoAway(Direction direction, ChannelHandlerContext ctx, int lastStreamId, long errorCode,
            ByteBuf debugData) {
        if (enabled()) {
            log(direction, "%s GO_AWAY: lastStreamId=%d, errorCode=%d, length=%d, bytes=%s",
                    ctx.channel(), lastStreamId, errorCode, debugData.readableBytes(), toString(debugData));
        }
    }

    public void logWindowsUpdate(Direction direction, ChannelHandlerContext ctx, int streamId,
            int windowSizeIncrement) {
        if (enabled()) {
            log(direction, "%s WINDOW_UPDATE: streamId=%d, windowSizeIncrement=%d",
                    ctx.channel(), streamId, windowSizeIncrement);
        }
    }

    public void logUnknownFrame(Direction direction, ChannelHandlerContext ctx, byte frameType, int streamId,
            Http2Flags flags, ByteBuf data) {
        if (enabled()) {
            log(direction, "%s UNKNOWN: frameType=%d, streamId=%d, flags=%d, length=%d, bytes=%s",
                    ctx.channel(), frameType & 0xFF, streamId, flags.value(), data.readableBytes(), toString(data));
        }
    }

    private boolean enabled() {
        return logger.isEnabled(level);
    }

    private String toString(ByteBuf buf) {
        if (level == InternalLogLevel.TRACE || buf.readableBytes() <= BUFFER_LENGTH_THRESHOLD) {
            // Log the entire buffer.
            return ByteBufUtil.hexDump(buf);
        }

        // Otherwise just log the first 64 bytes.
        int length = Math.min(buf.readableBytes(), BUFFER_LENGTH_THRESHOLD);
        return ByteBufUtil.hexDump(buf, buf.readerIndex(), length) + "...";
    }

    private void log(Direction direction, String format, Object... args) {
        StringBuilder b = new StringBuilder(200);
        b.append("\n----------------")
                .append(direction.name())
                .append("--------------------\n")
                .append(String.format(format, args))
                .append("\n------------------------------------");
        logger.log(level, b.toString());
    }
}
