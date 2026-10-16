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
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;

/**
 * Decorator around a {@link Http2FrameWriter} that logs all outbound frames before calling the
 * writer.
 */
public class Http2OutboundFrameLogger implements Http2FrameWriter {
    private final Http2FrameWriter writer;
    private final Http2FrameLogger logger;

    public Http2OutboundFrameLogger(Http2FrameWriter writer, Http2FrameLogger logger) {
        this.writer = checkNotNull(writer, "writer");
        this.logger = checkNotNull(logger, "logger");
    }

    @Override
    public void writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                          int padding, boolean endStream, Promise<Void> promise) {
        logger.logData(OUTBOUND, ctx, streamId, data, padding, endStream);
        writer.writeData(ctx, streamId, data, padding, endStream, promise);
    }

    @Override
    public void writeHeaders(ChannelHandlerContext ctx, int streamId,
                             Http2Headers headers, int padding, boolean endStream, Promise<Void> promise) {
        logger.logHeaders(OUTBOUND, ctx, streamId, headers, padding, endStream);
        writer.writeHeaders(ctx, streamId, headers, padding, endStream, promise);
    }

    @Override
    public void writeHeaders(ChannelHandlerContext ctx, int streamId,
                             Http2Headers headers, int streamDependency, short weight, boolean exclusive,
                             int padding, boolean endStream, Promise<Void> promise) {
        logger.logHeaders(OUTBOUND, ctx, streamId, headers, streamDependency, weight, exclusive,
                padding, endStream);
        writer.writeHeaders(ctx, streamId, headers, streamDependency, weight,
                exclusive, padding, endStream, promise);
    }

    @Override
    public void writePriority(ChannelHandlerContext ctx, int streamId,
                              int streamDependency, short weight, boolean exclusive, Promise<Void> promise) {
        logger.logPriority(OUTBOUND, ctx, streamId, streamDependency, weight, exclusive);
        writer.writePriority(ctx, streamId, streamDependency, weight, exclusive, promise);
    }

    @Override
    public void writeRstStream(ChannelHandlerContext ctx,
                               int streamId, long errorCode, Promise<Void> promise) {
        logger.logRstStream(OUTBOUND, ctx, streamId, errorCode);
        writer.writeRstStream(ctx, streamId, errorCode, promise);
    }

    @Override
    public void writeSettings(ChannelHandlerContext ctx,
                              Http2Settings settings, Promise<Void> promise) {
        logger.logSettings(OUTBOUND, ctx, settings);
        writer.writeSettings(ctx, settings, promise);
    }

    @Override
    public void writeSettingsAck(ChannelHandlerContext ctx, Promise<Void> promise) {
        logger.logSettingsAck(OUTBOUND, ctx);
        writer.writeSettingsAck(ctx, promise);
    }

    @Override
    public void writePing(ChannelHandlerContext ctx, boolean ack,
                          long data, Promise<Void> promise) {
        if (ack) {
            logger.logPingAck(OUTBOUND, ctx, data);
        } else {
            logger.logPing(OUTBOUND, ctx, data);
        }
        writer.writePing(ctx, ack, data, promise);
    }

    @Override
    public void writePushPromise(ChannelHandlerContext ctx, int streamId,
                                 int promisedStreamId, Http2Headers headers, int padding, Promise<Void> promise) {
        logger.logPushPromise(OUTBOUND, ctx, streamId, promisedStreamId, headers, padding);
        writer.writePushPromise(ctx, streamId, promisedStreamId, headers, padding, promise);
    }

    @Override
    public void writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
                            ByteBuf debugData, Promise<Void> promise) {
        logger.logGoAway(OUTBOUND, ctx, lastStreamId, errorCode, debugData);
        writer.writeGoAway(ctx, lastStreamId, errorCode, debugData, promise);
    }

    @Override
    public void writeWindowUpdate(ChannelHandlerContext ctx,
                                  int streamId, int windowSizeIncrement, Promise<Void> promise) {
        logger.logWindowsUpdate(OUTBOUND, ctx, streamId, windowSizeIncrement);
        writer.writeWindowUpdate(ctx, streamId, windowSizeIncrement, promise);
    }

    @Override
    public void writeFrame(ChannelHandlerContext ctx, short frameType, int streamId,
                           Http2Flags flags, ByteBuf payload, Promise<Void> promise) {
        logger.logUnknownFrame(OUTBOUND, ctx, frameType, streamId, flags, payload);
        writer.writeFrame(ctx, frameType, streamId, flags, payload, promise);
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
