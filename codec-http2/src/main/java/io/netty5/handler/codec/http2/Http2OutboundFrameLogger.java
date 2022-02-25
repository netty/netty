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

import io.netty5.buffer.ByteBuf;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.UnstableApi;

import static io.netty5.handler.codec.http2.Http2FrameLogger.Direction.OUTBOUND;
import static java.util.Objects.requireNonNull;

/**
 * Decorator around a {@link Http2FrameWriter} that logs all outbound frames before calling the
 * writer.
 */
@UnstableApi
public class Http2OutboundFrameLogger implements Http2FrameWriter {
    private final Http2FrameWriter writer;
    private final Http2FrameLogger logger;

    public Http2OutboundFrameLogger(Http2FrameWriter writer, Http2FrameLogger logger) {
        this.writer = requireNonNull(writer, "writer");
        this.logger = requireNonNull(logger, "logger");
    }

    @Override
    public Future<Void> writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                                  int padding, boolean endStream) {
        logger.logData(OUTBOUND, ctx, streamId, data, padding, endStream);
        return writer.writeData(ctx, streamId, data, padding, endStream);
    }

    @Override
    public Future<Void> writeHeaders(ChannelHandlerContext ctx, int streamId,
                                     Http2Headers headers, int padding, boolean endStream) {
        logger.logHeaders(OUTBOUND, ctx, streamId, headers, padding, endStream);
        return writer.writeHeaders(ctx, streamId, headers, padding, endStream);
    }

    @Override
    public Future<Void> writeHeaders(ChannelHandlerContext ctx, int streamId,
                                     Http2Headers headers, int streamDependency, short weight, boolean exclusive,
                                     int padding, boolean endStream) {
        logger.logHeaders(OUTBOUND, ctx, streamId, headers, streamDependency, weight, exclusive,
                padding, endStream);
        return writer.writeHeaders(ctx, streamId, headers, streamDependency, weight,
                exclusive, padding, endStream);
    }

    @Override
    public Future<Void> writePriority(ChannelHandlerContext ctx, int streamId,
                                      int streamDependency, short weight, boolean exclusive) {
        logger.logPriority(OUTBOUND, ctx, streamId, streamDependency, weight, exclusive);
        return writer.writePriority(ctx, streamId, streamDependency, weight, exclusive);
    }

    @Override
    public Future<Void> writeRstStream(ChannelHandlerContext ctx,
                                       int streamId, long errorCode) {
        logger.logRstStream(OUTBOUND, ctx, streamId, errorCode);
        return writer.writeRstStream(ctx, streamId, errorCode);
    }

    @Override
    public Future<Void> writeSettings(ChannelHandlerContext ctx,
                                      Http2Settings settings) {
        logger.logSettings(OUTBOUND, ctx, settings);
        return writer.writeSettings(ctx, settings);
    }

    @Override
    public Future<Void> writeSettingsAck(ChannelHandlerContext ctx) {
        logger.logSettingsAck(OUTBOUND, ctx);
        return writer.writeSettingsAck(ctx);
    }

    @Override
    public Future<Void> writePing(ChannelHandlerContext ctx, boolean ack,
                                  long data) {
        if (ack) {
            logger.logPingAck(OUTBOUND, ctx, data);
        } else {
            logger.logPing(OUTBOUND, ctx, data);
        }
        return writer.writePing(ctx, ack, data);
    }

    @Override
    public Future<Void> writePushPromise(ChannelHandlerContext ctx, int streamId,
                                         int promisedStreamId, Http2Headers headers, int padding) {
        logger.logPushPromise(OUTBOUND, ctx, streamId, promisedStreamId, headers, padding);
        return writer.writePushPromise(ctx, streamId, promisedStreamId, headers, padding);
    }

    @Override
    public Future<Void> writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
            ByteBuf debugData) {
        logger.logGoAway(OUTBOUND, ctx, lastStreamId, errorCode, debugData);
        return writer.writeGoAway(ctx, lastStreamId, errorCode, debugData);
    }

    @Override
    public Future<Void> writeWindowUpdate(ChannelHandlerContext ctx,
                                          int streamId, int windowSizeIncrement) {
        logger.logWindowsUpdate(OUTBOUND, ctx, streamId, windowSizeIncrement);
        return writer.writeWindowUpdate(ctx, streamId, windowSizeIncrement);
    }

    @Override
    public Future<Void> writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                                   Http2Flags flags, ByteBuf payload) {
        logger.logUnknownFrame(OUTBOUND, ctx, frameType, streamId, flags, payload);
        return writer.writeFrame(ctx, frameType, streamId, flags, payload);
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
