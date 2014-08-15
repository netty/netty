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

import static io.netty.handler.codec.http2.Http2FrameLogger.Direction.OUTBOUND;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

/**
 * Decorator around a {@link Http2FrameWriter} that logs all outbound frames before calling the
 * writer.
 */
public class Http2OutboundFrameLogger implements Http2FrameWriter {

    private final Http2FrameWriter writer;
    private final Http2FrameLogger logger;

    public Http2OutboundFrameLogger(Http2FrameWriter writer, Http2FrameLogger logger) {
        if (writer == null) {
            throw new NullPointerException("writer");
        }
        if (logger == null) {
            throw new NullPointerException("logger");
        }
        this.writer = writer;
        this.logger = logger;
    }

    @Override
    public ChannelFuture writeData(ChannelHandlerContext ctx, ChannelPromise promise, int streamId,
            ByteBuf data, int padding, boolean endStream) {
        logger.logData(OUTBOUND, streamId, data, padding, endStream);
        return writer.writeData(ctx, promise, streamId, data, padding, endStream);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, Http2Headers headers, int padding, boolean endStream) {
        logger.logHeaders(OUTBOUND, streamId, headers, padding, endStream);
        return writer.writeHeaders(ctx, promise, streamId, headers, padding, endStream);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, Http2Headers headers, int streamDependency, short weight,
            boolean exclusive, int padding, boolean endStream) {
        logger.logHeaders(OUTBOUND, streamId, headers, streamDependency, weight, exclusive,
                padding, endStream);
        return writer.writeHeaders(ctx, promise, streamId, headers, streamDependency, weight,
                exclusive, padding, endStream);
    }

    @Override
    public ChannelFuture writePriority(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int streamDependency, short weight, boolean exclusive) {
        logger.logPriority(OUTBOUND, streamId, streamDependency, weight, exclusive);
        return writer.writePriority(ctx, promise, streamId, streamDependency, weight, exclusive);
    }

    @Override
    public ChannelFuture writeRstStream(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, long errorCode) {
        return writer.writeRstStream(ctx, promise, streamId, errorCode);
    }

    @Override
    public ChannelFuture writeSettings(ChannelHandlerContext ctx, ChannelPromise promise,
            Http2Settings settings) {
        logger.logSettings(OUTBOUND, settings);
        return writer.writeSettings(ctx, promise, settings);
    }

    @Override
    public ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.logSettingsAck(OUTBOUND);
        return writer.writeSettingsAck(ctx, promise);
    }

    @Override
    public ChannelFuture writePing(ChannelHandlerContext ctx, ChannelPromise promise, boolean ack,
            ByteBuf data) {
        logger.logPing(OUTBOUND, data);
        return writer.writePing(ctx, promise, ack, data);
    }

    @Override
    public ChannelFuture writePushPromise(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int promisedStreamId, Http2Headers headers, int padding) {
        logger.logPushPromise(OUTBOUND, streamId, promisedStreamId, headers, padding);
        return writer.writePushPromise(ctx, promise, streamId, promisedStreamId, headers, padding);
    }

    @Override
    public ChannelFuture writeGoAway(ChannelHandlerContext ctx, ChannelPromise promise,
            int lastStreamId, long errorCode, ByteBuf debugData) {
        logger.logGoAway(OUTBOUND, lastStreamId, errorCode, debugData);
        return writer.writeGoAway(ctx, promise, lastStreamId, errorCode, debugData);
    }

    @Override
    public ChannelFuture writeWindowUpdate(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int windowSizeIncrement) {
        logger.logWindowsUpdate(OUTBOUND, streamId, windowSizeIncrement);
        return writer.writeWindowUpdate(ctx, promise, streamId, windowSizeIncrement);
    }

    @Override
    public ChannelFuture writeFrame(ChannelHandlerContext ctx, ChannelPromise promise,
            byte frameType, int streamId, Http2Flags flags, ByteBuf payload) {
        logger.logUnknownFrame(OUTBOUND, frameType, streamId, flags, payload);
        return writer.writeFrame(ctx, promise, frameType, streamId, flags, payload);
    }

    @Override
    public void close() {
        writer.close();
    }

    @Override
    public void maxHeaderTableSize(long max) throws Http2Exception {
        writer.maxHeaderTableSize(max);
    }

    @Override
    public long maxHeaderTableSize() {
        return writer.maxHeaderTableSize();
    }

    @Override
    public void maxFrameSize(int max) {
        writer.maxFrameSize(max);
    }

    @Override
    public int maxFrameSize() {
        return writer.maxFrameSize();
    }

    @Override
    public void maxHeaderListSize(int max) {
        writer.maxHeaderListSize(max);
    }

    @Override
    public int maxHeaderListSize() {
        return writer.maxHeaderListSize();
    }
}
