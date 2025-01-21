/*
 * Copyright 2014 The Netty Project
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
package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.logging.LogLevel;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Logs HTTP2 frames for debugging purposes.
 */
public class Http2FrameLogger extends ChannelHandlerAdapter {

    public enum Direction {
        INBOUND,
        OUTBOUND
    }

    private static final int BUFFER_LENGTH_THRESHOLD = 64;
    private final InternalLogger logger;
    private final InternalLogLevel level;

    public Http2FrameLogger(LogLevel level) {
        this(checkAndConvertLevel(level), InternalLoggerFactory.getInstance(Http2FrameLogger.class));
    }

    public Http2FrameLogger(LogLevel level, String name) {
        this(checkAndConvertLevel(level), InternalLoggerFactory.getInstance(checkNotNull(name, "name")));
    }

    public Http2FrameLogger(LogLevel level, Class<?> clazz) {
        this(checkAndConvertLevel(level), InternalLoggerFactory.getInstance(checkNotNull(clazz, "clazz")));
    }

    private Http2FrameLogger(InternalLogLevel level, InternalLogger logger) {
        this.level = level;
        this.logger = logger;
    }

    private static InternalLogLevel checkAndConvertLevel(LogLevel level) {
        return checkNotNull(level, "level").toInternalLevel();
    }

    public boolean isEnabled() {
        return logger.isEnabled(level);
    }

    public void logData(Direction direction, ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
            boolean endStream) {
        if (isEnabled()) {
            logger.log(level, "{} {} DATA: streamId={} padding={} endStream={} length={} bytes={}", ctx.channel(),
                    direction.name(), streamId, padding, endStream, data.readableBytes(), toString(data));
        }
    }

    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int padding, boolean endStream) {
        if (isEnabled()) {
            logger.log(level, "{} {} HEADERS: streamId={} headers={} padding={} endStream={}", ctx.channel(),
                    direction.name(), streamId, headers, padding, endStream);
        }
    }

    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) {
        if (isEnabled()) {
            logger.log(level, "{} {} HEADERS: streamId={} headers={} streamDependency={} weight={} exclusive={} " +
                    "padding={} endStream={}", ctx.channel(),
                    direction.name(), streamId, headers, streamDependency, weight, exclusive, padding, endStream);
        }
    }

    public void logPriority(Direction direction, ChannelHandlerContext ctx, int streamId, int streamDependency,
            short weight, boolean exclusive) {
        if (isEnabled()) {
            logger.log(level, "{} {} PRIORITY: streamId={} streamDependency={} weight={} exclusive={}", ctx.channel(),
                    direction.name(), streamId, streamDependency, weight, exclusive);
        }
    }

    public void logRstStream(Direction direction, ChannelHandlerContext ctx, int streamId, long errorCode) {
        if (isEnabled()) {
            logger.log(level, "{} {} RST_STREAM: streamId={} errorCode={}", ctx.channel(),
                    direction.name(), streamId, errorCode);
        }
    }

    public void logSettingsAck(Direction direction, ChannelHandlerContext ctx) {
        logger.log(level, "{} {} SETTINGS: ack=true", ctx.channel(), direction.name());
    }

    public void logSettings(Direction direction, ChannelHandlerContext ctx, Http2Settings settings) {
        if (isEnabled()) {
            logger.log(level, "{} {} SETTINGS: ack=false settings={}", ctx.channel(), direction.name(), settings);
        }
    }

    public void logPing(Direction direction, ChannelHandlerContext ctx, long data) {
        if (isEnabled()) {
            logger.log(level, "{} {} PING: ack=false bytes={}", ctx.channel(),
                    direction.name(), data);
        }
    }

    public void logPingAck(Direction direction, ChannelHandlerContext ctx, long data) {
        if (isEnabled()) {
            logger.log(level, "{} {} PING: ack=true bytes={}", ctx.channel(),
                    direction.name(), data);
        }
    }

    public void logPushPromise(Direction direction, ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding) {
        if (isEnabled()) {
            logger.log(level, "{} {} PUSH_PROMISE: streamId={} promisedStreamId={} headers={} padding={}",
                    ctx.channel(), direction.name(), streamId, promisedStreamId, headers, padding);
        }
    }

    public void logGoAway(Direction direction, ChannelHandlerContext ctx, int lastStreamId, long errorCode,
            ByteBuf debugData) {
        if (isEnabled()) {
            logger.log(level, "{} {} GO_AWAY: lastStreamId={} errorCode={} length={} bytes={}", ctx.channel(),
                    direction.name(), lastStreamId, errorCode, debugData.readableBytes(), toString(debugData));
        }
    }

    public void logWindowsUpdate(Direction direction, ChannelHandlerContext ctx, int streamId,
            int windowSizeIncrement) {
        if (isEnabled()) {
            logger.log(level, "{} {} WINDOW_UPDATE: streamId={} windowSizeIncrement={}", ctx.channel(),
                    direction.name(), streamId, windowSizeIncrement);
        }
    }

    public void logUnknownFrame(Direction direction, ChannelHandlerContext ctx, byte frameType, int streamId,
            Http2Flags flags, ByteBuf data) {
        if (isEnabled()) {
            logger.log(level, "{} {} UNKNOWN: frameType={} streamId={} flags={} length={} bytes={}", ctx.channel(),
                    direction.name(), frameType & 0xFF, streamId, flags.value(), data.readableBytes(), toString(data));
        }
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
}
