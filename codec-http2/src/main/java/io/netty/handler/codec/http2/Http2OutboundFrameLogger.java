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

import static io.netty.handler.codec.http2.Http2FrameLogger.Direction.OUTBOUND;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.internal.UnstableApi;

/**
 * Decorator around a {@link Http2FrameWriter} that logs all outbound frames before calling the
 * writer.
 */
@UnstableApi
public class Http2OutboundFrameLogger implements Http2FrameWriter {
    private final Http2FrameWriter writer;
    private final Http2FrameLogger logger;

    public Http2OutboundFrameLogger(Http2FrameWriter writer, Http2FrameLogger logger) {
        this.writer = checkNotNull(writer, "writer");
        this.logger = checkNotNull(logger, "logger");
    }

    @Override
    public ChannelFuture writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data,
            int padding, boolean endStream, ChannelPromise promise) {
        logger.logData(OUTBOUND, ctx, streamId, data, padding, endStream);
        return writer.writeData(ctx, streamId, data, padding, endStream, promise);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId,
            Http2Headers headers, int padding, boolean endStream, ChannelPromise promise) {
        logger.logHeaders(OUTBOUND, ctx, streamId, headers, padding, endStream);
        return writer.writeHeaders(ctx, streamId, headers, padding, endStream, promise);
    }

    @Override
    public ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId,
            Http2Headers headers, int streamDependency, short weight, boolean exclusive,
            int padding, boolean endStream, ChannelPromise promise) {
        logger.logHeaders(OUTBOUND, ctx, streamId, headers, streamDependency, weight, exclusive,
                padding, endStream);
        return writer.writeHeaders(ctx, streamId, headers, streamDependency, weight,
                exclusive, padding, endStream, promise);
    }

    @Override
    public ChannelFuture writePriority(ChannelHandlerContext ctx, int streamId,
            int streamDependency, short weight, boolean exclusive, ChannelPromise promise) {
        logger.logPriority(OUTBOUND, ctx, streamId, streamDependency, weight, exclusive);
        return writer.writePriority(ctx, streamId, streamDependency, weight, exclusive, promise);
    }

    @Override
    public ChannelFuture writeRstStream(ChannelHandlerContext ctx,
            int streamId, long errorCode, ChannelPromise promise) {
        logger.logRstStream(OUTBOUND, ctx, streamId, errorCode);
        return writer.writeRstStream(ctx, streamId, errorCode, promise);
    }

    @Override
    public ChannelFuture writeSettings(ChannelHandlerContext ctx,
            Http2Settings settings, ChannelPromise promise) {
        logger.logSettings(OUTBOUND, ctx, settings);
        return writer.writeSettings(ctx, settings, promise);
    }

    @Override
    public ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise) {
        logger.logSettingsAck(OUTBOUND, ctx);
        return writer.writeSettingsAck(ctx, promise);
    }

    @Override
    public ChannelFuture writePing(ChannelHandlerContext ctx, boolean ack,
            long data, ChannelPromise promise) {
        if (ack) {
            logger.logPingAck(OUTBOUND, ctx, data);
        } else {
            logger.logPing(OUTBOUND, ctx, data);
        }
        return writer.writePing(ctx, ack, data, promise);
    }

    @Override
    public ChannelFuture writePushPromise(ChannelHandlerContext ctx, int streamId,
            int promisedStreamId, Http2Headers headers, int padding, ChannelPromise promise) {
        logger.logPushPromise(OUTBOUND, ctx, streamId, promisedStreamId, headers, padding);
        return writer.writePushPromise(ctx, streamId, promisedStreamId, headers, padding, promise);
    }

    @Override
    public ChannelFuture writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
            ByteBuf debugData, ChannelPromise promise) {
        logger.logGoAway(OUTBOUND, ctx, lastStreamId, errorCode, debugData);
        return writer.writeGoAway(ctx, lastStreamId, errorCode, debugData, promise);
    }

    @Override
    public ChannelFuture writeWindowUpdate(ChannelHandlerContext ctx,
            int streamId, int windowSizeIncrement, ChannelPromise promise) {
        logger.logWindowsUpdate(OUTBOUND, ctx, streamId, windowSizeIncrement);
        return writer.writeWindowUpdate(ctx, streamId, windowSizeIncrement, promise);
    }

    @Override
    public ChannelFuture writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
            Http2Flags flags, ByteBuf payload, ChannelPromise promise) {
        logger.logUnknownFrame(OUTBOUND, ctx, frameType, streamId, flags, payload);
        return writer.writeFrame(ctx, frameType, streamId, flags, payload, promise);
    }

    @Override
    public void close() {
        writer.close();
    }

    @Override
    public Configuration configuration() {
        return writer.configuration();
    }
}
