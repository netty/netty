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
package io.netty5.handler.codec.http2;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferUtil;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import io.netty5.handler.logging.LogLevel;
import io.netty5.util.internal.UnstableApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import static java.util.Objects.requireNonNull;

/**
 * Logs HTTP2 frames for debugging purposes.
 */
@UnstableApi
public class Http2FrameLogger {

    public enum Direction {
        INBOUND,
        OUTBOUND
    }

    private static final int BUFFER_LENGTH_THRESHOLD = 64;
    private final Logger logger;
    private final Level level;

    public Http2FrameLogger(LogLevel level) {
        this(requireNonNull(level, "level"), LoggerFactory.getLogger(Http2FrameLogger.class));
    }

    public Http2FrameLogger(LogLevel level, String name) {
        this(requireNonNull(level, "level"), LoggerFactory.getLogger(requireNonNull(name, "name")));
    }

    public Http2FrameLogger(LogLevel level, Class<?> clazz) {
        this(requireNonNull(level, "level"), LoggerFactory.getLogger(requireNonNull(clazz, "clazz")));
    }

    private Http2FrameLogger(LogLevel level, Logger logger) {
        this.level = level.unwrap();
        this.logger = logger;
    }

    public boolean isEnabled() {
        return logger.isEnabledForLevel(level);
    }

    public void logData(Direction direction, ChannelHandlerContext ctx, int streamId, Buffer data, int padding,
                        boolean endStream) {
        if (isEnabled()) {
            logger.atLevel(level).log("{} {} DATA: streamId={} padding={} endStream={} length={} bytes={}",
                    ctx.channel(), direction.name(), streamId, padding, endStream, data.readableBytes(),
                    toString(data));
        }
    }

    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int padding, boolean endStream) {
        if (isEnabled()) {
            logger.atLevel(level).log("{} {} HEADERS: streamId={} headers={} padding={} endStream={}", ctx.channel(),
                    direction.name(), streamId, headers, padding, endStream);
        }
    }

    public void logHeaders(Direction direction, ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) {
        if (isEnabled()) {
            logger.atLevel(level).log("{} {} HEADERS: streamId={} headers={} streamDependency={} weight={} " +
                            "exclusive={} padding={} endStream={}",
                    ctx.channel(), direction.name(), streamId, headers, streamDependency, weight, exclusive, padding,
                    endStream);
        }
    }

    public void logPriority(Direction direction, ChannelHandlerContext ctx, int streamId, int streamDependency,
            short weight, boolean exclusive) {
        if (isEnabled()) {
            logger.atLevel(level).log("{} {} PRIORITY: streamId={} streamDependency={} weight={} exclusive={}",
                    ctx.channel(), direction.name(), streamId, streamDependency, weight, exclusive);
        }
    }

    public void logRstStream(Direction direction, ChannelHandlerContext ctx, int streamId, long errorCode) {
        if (isEnabled()) {
            logger.atLevel(level).log("{} {} RST_STREAM: streamId={} errorCode={}", ctx.channel(),
                    direction.name(), streamId, errorCode);
        }
    }

    public void logSettingsAck(Direction direction, ChannelHandlerContext ctx) {
        logger.atLevel(level).log("{} {} SETTINGS: ack=true", ctx.channel(), direction.name());
    }

    public void logSettings(Direction direction, ChannelHandlerContext ctx, Http2Settings settings) {
        if (isEnabled()) {
            logger.atLevel(level).log("{} {} SETTINGS: ack=false settings={}", ctx.channel(), direction.name(),
                    settings);
        }
    }

    public void logPing(Direction direction, ChannelHandlerContext ctx, long data) {
        if (isEnabled()) {
            logger.atLevel(level).log("{} {} PING: ack=false bytes={}", ctx.channel(), direction.name(), data);
        }
    }

    public void logPingAck(Direction direction, ChannelHandlerContext ctx, long data) {
        if (isEnabled()) {
            logger.atLevel(level).log("{} {} PING: ack=true bytes={}", ctx.channel(), direction.name(), data);
        }
    }

    public void logPushPromise(Direction direction, ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding) {
        if (isEnabled()) {
            logger.atLevel(level).log("{} {} PUSH_PROMISE: streamId={} promisedStreamId={} headers={} padding={}",
                    ctx.channel(), direction.name(), streamId, promisedStreamId, headers, padding);
        }
    }

    public void logGoAway(Direction direction, ChannelHandlerContext ctx, int lastStreamId, long errorCode,
            Buffer debugData) {
        if (isEnabled()) {
            logger.atLevel(level).log("{} {} GO_AWAY: lastStreamId={} errorCode={} length={} bytes={}", ctx.channel(),
                    direction.name(), lastStreamId, errorCode, debugData.readableBytes(), toString(debugData));
        }
    }

    public void logWindowsUpdate(Direction direction, ChannelHandlerContext ctx, int streamId,
            int windowSizeIncrement) {
        if (isEnabled()) {
            logger.atLevel(level).log("{} {} WINDOW_UPDATE: streamId={} windowSizeIncrement={}", ctx.channel(),
                    direction.name(), streamId, windowSizeIncrement);
        }
    }

    public void logUnknownFrame(Direction direction, ChannelHandlerContext ctx, short frameType, int streamId,
            Http2Flags flags, Buffer data) {
        if (isEnabled()) {
            logger.atLevel(level).log("{} {} UNKNOWN: frameType={} streamId={} flags={} length={} bytes={}",
                    ctx.channel(), direction.name(), frameType & 0xFF, streamId, flags.value(), data.readableBytes(),
                    toString(data));
        }
    }

    private String toString(Buffer buf) {
        if (level == Level.TRACE || buf.readableBytes() <= BUFFER_LENGTH_THRESHOLD) {
            // Log the entire buffer.
            return BufferUtil.hexDump(buf);
        }

        // Otherwise just log the first 64 bytes.
        int length = Math.min(buf.readableBytes(), BUFFER_LENGTH_THRESHOLD);
        return BufferUtil.hexDump(buf, buf.readerOffset(), length) + "...";
    }
}
