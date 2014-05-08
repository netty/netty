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

import static io.netty.handler.codec.http2.Http2FrameLogger.Direction.INBOUND;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * Decorator around a {@link Http2FrameReader} that logs all inbound frames before calling
 * back the observer.
 */
public class Http2InboundFrameLogger implements Http2FrameReader {

    private final Http2FrameReader reader;
    private final Http2FrameLogger logger;

    public Http2InboundFrameLogger(Http2FrameReader reader, Http2FrameLogger logger) {
        if (reader == null) {
            throw new NullPointerException("reader");
        }
        if (logger == null) {
            throw new NullPointerException("logger");
        }
        this.reader = reader;
        this.logger = logger;
    }

    @Override
    public void readFrame(ChannelHandlerContext ctx, ByteBuf input, final Http2FrameObserver observer)
            throws Http2Exception {
        reader.readFrame(ctx, input, new Http2FrameObserver() {

            @Override
            public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data,
                    int padding, boolean endOfStream, boolean endOfSegment, boolean compressed)
                    throws Http2Exception {
                logger.logData(INBOUND, streamId, data, padding, endOfStream, endOfSegment,
                        compressed);
                observer.onDataRead(ctx, streamId, data, padding, endOfStream, endOfSegment, compressed);
            }

            @Override
            public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                    Http2Headers headers, int padding, boolean endStream, boolean endSegment)
                    throws Http2Exception {
                logger.logHeaders(INBOUND, streamId, headers, padding, endStream, endSegment);
                observer.onHeadersRead(ctx, streamId, headers, padding, endStream, endSegment);
            }

            @Override
            public void onHeadersRead(ChannelHandlerContext ctx, int streamId,
                    Http2Headers headers, int streamDependency, short weight, boolean exclusive,
                    int padding, boolean endStream, boolean endSegment) throws Http2Exception {
                logger.logHeaders(INBOUND, streamId, headers, streamDependency, weight, exclusive,
                        padding, endStream, endSegment);
                observer.onHeadersRead(ctx, streamId, headers, streamDependency, weight, exclusive,
                        padding, endStream, endSegment);
            }

            @Override
            public void onPriorityRead(ChannelHandlerContext ctx, int streamId,
                    int streamDependency, short weight, boolean exclusive) throws Http2Exception {
                logger.logPriority(INBOUND, streamId, streamDependency, weight, exclusive);
                observer.onPriorityRead(ctx, streamId, streamDependency, weight, exclusive);
            }

            @Override
            public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode)
                    throws Http2Exception {
                logger.logRstStream(INBOUND, streamId, errorCode);
                observer.onRstStreamRead(ctx, streamId, errorCode);
            }

            @Override
            public void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception {
                logger.logSettingsAck(INBOUND);
                observer.onSettingsAckRead(ctx);
            }

            @Override
            public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
                    throws Http2Exception {
                logger.logSettings(INBOUND, settings);
                observer.onSettingsRead(ctx, settings);
            }

            @Override
            public void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
                logger.logPing(INBOUND, data);
                observer.onPingRead(ctx, data);
            }

            @Override
            public void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception {
                logger.logPingAck(INBOUND, data);
                observer.onPingAckRead(ctx, data);
            }

            @Override
            public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId,
                    int promisedStreamId, Http2Headers headers, int padding) throws Http2Exception {
                logger.logPushPromise(INBOUND, streamId, promisedStreamId, headers, padding);
                observer.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
            }

            @Override
            public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
                    ByteBuf debugData) throws Http2Exception {
                logger.logGoAway(INBOUND, lastStreamId, errorCode, debugData);
                observer.onGoAwayRead(ctx, lastStreamId, errorCode, debugData);
            }

            @Override
            public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
                    throws Http2Exception {
                logger.logWindowsUpdate(INBOUND, streamId, windowSizeIncrement);
                observer.onWindowUpdateRead(ctx, streamId, windowSizeIncrement);
            }

            @Override
            public void onAltSvcRead(ChannelHandlerContext ctx, int streamId, long maxAge,
                    int port, ByteBuf protocolId, String host, String origin) throws Http2Exception {
                logger.logAltSvc(INBOUND, streamId, maxAge, port, protocolId, host, origin);
                observer.onAltSvcRead(ctx, streamId, maxAge, port, protocolId, host, origin);
            }

            @Override
            public void onBlockedRead(ChannelHandlerContext ctx, int streamId) throws Http2Exception {
                logger.logBlocked(INBOUND, streamId);
                observer.onBlockedRead(ctx, streamId);
            }
        });
    }

    @Override
    public void close() {
        reader.close();
    }

    @Override
    public void maxHeaderTableSize(int max) {
        reader.maxHeaderTableSize(max);
    }

    @Override
    public int maxHeaderTableSize() {
        return reader.maxHeaderTableSize();
    }

}
