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
import io.netty.buffer.ByteBufAllocator;

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
    public void readFrame(ByteBufAllocator alloc, ByteBuf input, final Http2FrameObserver observer)
            throws Http2Exception {
        reader.readFrame(alloc, input, new Http2FrameObserver() {

            @Override
            public void onDataRead(int streamId, ByteBuf data, int padding, boolean endOfStream,
                    boolean endOfSegment, boolean compressed) throws Http2Exception {
                logger.logData(INBOUND, streamId, data, padding, endOfStream, endOfSegment,
                        compressed);
                observer.onDataRead(streamId, data, padding, endOfStream, endOfSegment, compressed);
            }

            @Override
            public void onHeadersRead(int streamId, Http2Headers headers, int padding,
                    boolean endStream, boolean endSegment) throws Http2Exception {
                logger.logHeaders(INBOUND, streamId, headers, padding, endStream, endSegment);
                observer.onHeadersRead(streamId, headers, padding, endStream, endSegment);
            }

            @Override
            public void onHeadersRead(int streamId, Http2Headers headers, int streamDependency,
                    short weight, boolean exclusive, int padding, boolean endStream,
                    boolean endSegment) throws Http2Exception {
                logger.logHeaders(INBOUND, streamId, headers, streamDependency, weight, exclusive,
                        padding, endStream, endSegment);
                observer.onHeadersRead(streamId, headers, streamDependency, weight, exclusive,
                        padding, endStream, endSegment);
            }

            @Override
            public void onPriorityRead(int streamId, int streamDependency, short weight,
                    boolean exclusive) throws Http2Exception {
                logger.logPriority(INBOUND, streamId, streamDependency, weight, exclusive);
                observer.onPriorityRead(streamId, streamDependency, weight, exclusive);
            }

            @Override
            public void onRstStreamRead(int streamId, long errorCode) throws Http2Exception {
                logger.logRstStream(INBOUND, streamId, errorCode);
                observer.onRstStreamRead(streamId, errorCode);
            }

            @Override
            public void onSettingsAckRead() throws Http2Exception {
                logger.logSettingsAck(INBOUND);
                observer.onSettingsAckRead();
            }

            @Override
            public void onSettingsRead(Http2Settings settings) throws Http2Exception {
                logger.logSettings(INBOUND, settings);
                observer.onSettingsRead(settings);
            }

            @Override
            public void onPingRead(ByteBuf data) throws Http2Exception {
                logger.logPing(INBOUND, data);
                observer.onPingRead(data);
            }

            @Override
            public void onPingAckRead(ByteBuf data) throws Http2Exception {
                logger.logPingAck(INBOUND, data);
                observer.onPingAckRead(data);
            }

            @Override
            public void onPushPromiseRead(int streamId, int promisedStreamId, Http2Headers headers,
                    int padding) throws Http2Exception {
                logger.logPushPromise(INBOUND, streamId, promisedStreamId, headers, padding);
                observer.onPushPromiseRead(streamId, promisedStreamId, headers, padding);
            }

            @Override
            public void onGoAwayRead(int lastStreamId, long errorCode, ByteBuf debugData)
                    throws Http2Exception {
                logger.logGoAway(INBOUND, lastStreamId, errorCode, debugData);
                observer.onGoAwayRead(lastStreamId, errorCode, debugData);
            }

            @Override
            public void onWindowUpdateRead(int streamId, int windowSizeIncrement)
                    throws Http2Exception {
                logger.logWindowsUpdate(INBOUND, streamId, windowSizeIncrement);
                observer.onWindowUpdateRead(streamId, windowSizeIncrement);
            }

            @Override
            public void onAltSvcRead(int streamId, long maxAge, int port, ByteBuf protocolId,
                    String host, String origin) throws Http2Exception {
                logger.logAltSvc(INBOUND, streamId, maxAge, port, protocolId, host, origin);
                observer.onAltSvcRead(streamId, maxAge, port, protocolId, host, origin);
            }

            @Override
            public void onBlockedRead(int streamId) throws Http2Exception {
                logger.logBlocked(INBOUND, streamId);
                observer.onBlockedRead(streamId);
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
