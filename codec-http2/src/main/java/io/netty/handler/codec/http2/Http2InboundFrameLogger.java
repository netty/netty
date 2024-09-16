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

import static io.netty.handler.codec.http2.Http2FrameLogger.Direction.INBOUND;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * Decorator around a {@link Http2FrameReader} that logs all inbound frames before calling
 * back the listener.
 */
public class Http2InboundFrameLogger implements Http2FrameReader {
    private final Http2FrameReader reader;
    private final Http2FrameLogger logger;

    public Http2InboundFrameLogger(Http2FrameReader reader, Http2FrameLogger logger) {
        this.reader = checkNotNull(reader, "reader");
        this.logger = checkNotNull(logger, "logger");
    }

    @Override
    public void readFrame(ChannelHandlerContext ctx, ByteBuf input, final Http2FrameListener listener)
            throws Http2Exception {
        reader.readFrame(ctx, input, new Http2FrameListener() {

            @Override
            public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                    int padding, boolean endOfStream)
                    throws Http2Exception {
                logger.logData(INBOUND, ctx, streamId, data, padding, endOfStream);
                return listener.onDataRead(ctx, streamId, data, padding, endOfStream);
            }

            @Override
            public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                    Http2Headers headers, int padding, boolean endStream)
                    throws Http2Exception {
                logger.logHeaders(INBOUND, ctx, streamId, headers, padding, endStream);
                listener.onHeadersRead(ctx, streamId, headers, padding, endStream);
            }

            @Override
            public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                    Http2Headers headers, int streamDependency, short weight, boolean exclusive,
                    int padding, boolean endStream) throws Http2Exception {
                logger.logHeaders(INBOUND, ctx, streamId, headers, streamDependency, weight, exclusive,
                        padding, endStream);
                listener.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive,
                        padding, endStream);
            }

            @Override
            public void onPriorityRead(ChannelHandlerContext ctx, int streamId,
                    int streamDependency, short weight, boolean exclusive) throws Http2Exception {
                logger.logPriority(INBOUND, ctx, streamId, streamDependency, weight, exclusive);
                listener.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
            }

            @Override
            public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                    throws Http2Exception {
                logger.logRstStream(INBOUND, ctx, streamId, errorCode);
                listener.onRstStreamRead(ctx, streamId, errorCode);
            }

            @Override
            public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
                logger.logSettingsAck(INBOUND, ctx);
                listener.onSettingsAckRead(ctx);
            }

            @Override
            public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
                    throws Http2Exception {
                logger.logSettings(INBOUND, ctx, settings);
                listener.onSettingsRead(ctx, settings);
            }

            @Override
            public void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
                logger.logPing(INBOUND, ctx, data);
                listener.onPingRead(ctx, data);
            }

            @Override
            public void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception {
                logger.logPingAck(INBOUND, ctx, data);
                listener.onPingAckRead(ctx, data);
            }

            @Override
            public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId,
                    int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
                logger.logPushPromise(INBOUND, ctx, streamId, promisedStreamId, headers, padding);
                listener.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
            }

            @Override
            public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
                    ByteBuf debugData) throws Http2Exception {
                logger.logGoAway(INBOUND, ctx, lastStreamId, errorCode, debugData);
                listener.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
            }

            @Override
            public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
                    throws Http2Exception {
                logger.logWindowsUpdate(INBOUND, ctx, streamId, windowSizeIncrement);
                listener.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
            }

            @Override
            public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
                    Http2Flags flags, ByteBuf payload) throws Http2Exception {
                logger.logUnknownFrame(INBOUND, ctx, frameType, streamId, flags, payload);
                listener.onUnknownFrame(ctx, frameType, streamId, flags, payload);
            }
        });
    }

    @Override
    public void close() {
        reader.close();
    }

    @Override
    public Configuration configuration() {
        return reader.configuration();
    }
}
