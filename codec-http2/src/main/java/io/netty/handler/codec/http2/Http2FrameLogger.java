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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.util.internal.logging.InternalLogLevel;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Logs HTTP2 frames for debugging purposes.
 */
public class Http2FrameLogger extends ChannelHandlerAdapter {

    public enum Direction {
        INBOUND,
        OUTBOUND
    }

    private final InternalLogger logger;
    private final InternalLogLevel level;

    public Http2FrameLogger(InternalLogLevel level) {
        this(level, InternalLoggerFactory.getInstance(Http2FrameLogger.class));
    }

    public Http2FrameLogger(InternalLogLevel level, InternalLogger logger) {
        if (level == null) {
            throw new NullPointerException("level");
        }
        if (logger == null) {
            throw new NullPointerException("logger");
        }
        this.level = level;
        this.logger = logger;
    }

    public void logData(Direction direction, int streamId, ByteBuf data, int padding,
            boolean endStream) {
        log(direction,
                "DATA: streamId=%d, padding=%d, endStream=%b, length=%d, bytes=%s",
                streamId, padding, endStream, data.readableBytes(), ByteBufUtil.hexDump(data));
    }

    public void logHeaders(Direction direction, int streamId, Http2Headers headers, int padding,
            boolean endStream) {
        log(direction, "HEADERS: streamId:%d, headers=%s, padding=%d, endStream=%b",
                streamId, headers, padding, endStream);
    }

    public void logHeaders(Direction direction, int streamId, Http2Headers headers,
            int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) {
        log(direction,
                "HEADERS: streamId:%d, headers=%s, streamDependency=%d, weight=%d, exclusive=%b, "
                        + "padding=%d, endStream=%b", streamId, headers,
                streamDependency, weight, exclusive, padding, endStream);
    }

    public void logPriority(Direction direction, int streamId, int streamDependency, short weight,
            boolean exclusive) {
        log(direction, "PRIORITY: streamId=%d, streamDependency=%d, weight=%d, exclusive=%b",
                streamId, streamDependency, weight, exclusive);
    }

    public void logRstStream(Direction direction, int streamId, long errorCode) {
        log(direction, "RST_STREAM: streamId=%d, errorCode=%d", streamId, errorCode);
    }

    public void logSettingsAck(Direction direction) {
        log(direction, "SETTINGS ack=true");
    }

    public void logSettings(Direction direction, Http2Settings settings) {
        log(direction, "SETTINGS: ack=false, settings=%s", settings);
    }

    public void logPing(Direction direction, ByteBuf data) {
        log(direction, "PING: ack=false, length=%d, bytes=%s", data.readableBytes(), ByteBufUtil.hexDump(data));
    }

    public void logPingAck(Direction direction, ByteBuf data) {
        log(direction, "PING: ack=true, length=%d, bytes=%s", data.readableBytes(), ByteBufUtil.hexDump(data));
    }

    public void logPushPromise(Direction direction, int streamId, int promisedStreamId,
            Http2Headers headers, int padding) {
        log(direction, "PUSH_PROMISE: streamId=%d, promisedStreamId=%d, headers=%s, padding=%d",
                streamId, promisedStreamId, headers, padding);
    }

    public void logGoAway(Direction direction, int lastStreamId, long errorCode, ByteBuf debugData) {
        log(direction, "GO_AWAY: lastStreamId=%d, errorCode=%d, length=%d, bytes=%s", lastStreamId,
                errorCode, debugData.readableBytes(), ByteBufUtil.hexDump(debugData));
    }

    public void logWindowsUpdate(Direction direction, int streamId, int windowSizeIncrement) {
        log(direction, "WINDOW_UPDATE: streamId=%d, windowSizeIncrement=%d", streamId,
                windowSizeIncrement);
    }

    public void logUnknownFrame(Direction direction, byte frameType, int streamId, Http2Flags flags, ByteBuf data) {
        log(direction, "UNKNOWN: frameType=%d, streamId=%d, flags=%d, length=%d, bytes=%s",
                frameType & 0xFF, streamId, flags.value(), data.readableBytes(), ByteBufUtil.hexDump(data));
    }

    private void log(Direction direction, String format, Object... args) {
        if (logger.isEnabled(level)) {
            StringBuilder b = new StringBuilder("\n----------------");
            b.append(direction.name());
            b.append("--------------------\n");
            b.append(String.format(format, args));
            b.append("\n------------------------------------");
            logger.log(level, b.toString());
        }
    }
}
