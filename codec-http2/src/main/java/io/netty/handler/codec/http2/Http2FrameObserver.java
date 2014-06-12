/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * An observer of HTTP/2 frames.
 */
public interface Http2FrameObserver {

    /**
     * Handles an inbound DATA frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the subject stream for the frame.
     * @param data payload buffer for the frame. If this buffer needs to be retained by the observer
     *            they must make a copy. This buffer does not need to be released
     *            by the observer.
     * @param padding the number of padding bytes found at the end of the frame.
     * @param endOfStream Indicates whether this is the last frame to be sent from the remote
     *            endpoint for this stream.
     * @param endOfSegment Indicates whether this frame is the end of the current segment.
     * @param compressed Indicates whether or not the payload is compressed with gzip encoding.
     */
    void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
            boolean endOfStream, boolean endOfSegment, boolean compressed) throws Http2Exception;

    /**
     * Handles an inbound HEADERS frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the subject stream for the frame.
     * @param headers the received headers.
     * @param padding the number of padding bytes found at the end of the frame.
     * @param endStream Indicates whether this is the last frame to be sent from the remote endpoint
     *            for this stream.
     * @param endSegment Indicates whether this frame is the end of the current segment.
     */
    void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
            boolean endStream, boolean endSegment) throws Http2Exception;

    /**
     * Handles an inbound HEADERS frame with priority information specified.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the subject stream for the frame.
     * @param headers the received headers.
     * @param streamDependency the stream on which this stream depends, or 0 if dependent on the
     *            connection.
     * @param weight the new weight for the stream.
     * @param exclusive whether or not the stream should be the exclusive dependent of its parent.
     * @param padding the number of padding bytes found at the end of the frame.
     * @param endStream Indicates whether this is the last frame to be sent from the remote endpoint
     *            for this stream.
     * @param endSegment Indicates whether this frame is the end of the current segment.
     */
    void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int streamDependency, short weight, boolean exclusive, int padding, boolean endStream,
            boolean endSegment) throws Http2Exception;

    /**
     * Handles an inbound PRIORITY frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the subject stream for the frame.
     * @param streamDependency the stream on which this stream depends, or 0 if dependent on the
     *            connection.
     * @param weight the new weight for the stream.
     * @param exclusive whether or not the stream should be the exclusive dependent of its parent.
     */
    void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency,
            short weight, boolean exclusive) throws Http2Exception;

    /**
     * Handles an inbound RST_STREAM frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the stream that is terminating.
     * @param errorCode the error code identifying the type of failure.
     */
    void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception;

    /**
     * Handles an inbound SETTINGS acknowledgment frame.
     * @param ctx the context from the handler where the frame was read.
     */
    void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception;

    /**
     * Handles an inbound SETTINGS frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param settings the settings received from the remote endpoint.
     */
    void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception;

    /**
     * Handles an inbound PING frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param data the payload of the frame. If this buffer needs to be retained by the observer
     *            they must make a copy. This buffer does not need to be released
     *            by the observer.
     */
    void onPingRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception;

    /**
     * Handles an inbound PING acknowledgment.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param data the payload of the frame. If this buffer needs to be retained by the observer
     *            they must make a copy. This buffer does not need to be released
     *            by the observer.
     */
    void onPingAckRead(ChannelHandlerContext ctx, ByteBuf data) throws Http2Exception;

    /**
     * Handles an inbound PUSH_PROMISE frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the stream the frame was sent on.
     * @param promisedStreamId the ID of the promised stream.
     * @param headers the received headers.
     * @param padding the number of padding bytes found at the end of the frame.
     */
    void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding) throws Http2Exception;

    /**
     * Handles an inbound GO_AWAY frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param lastStreamId the last known stream of the remote endpoint.
     * @param errorCode the error code, if abnormal closure.
     * @param debugData application-defined debug data. If this buffer needs to be retained by the
     *            observer they must make a copy. This buffer does not need to be released
     *            by the observer.
     */
    void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
            throws Http2Exception;

    /**
     * Handles an inbound WINDOW_UPDATE frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the stream the frame was sent on.
     * @param windowSizeIncrement the increased number of bytes of the remote endpoint's flow
     *            control window.
     */
    void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
            throws Http2Exception;

    /**
     * Handles an inbound ALT_SVC frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the stream.
     * @param maxAge the freshness lifetime of the alternative service association.
     * @param port the port that the alternative service is available upon.
     * @param protocolId the ALPN protocol identifier of the alternative service. If this buffer
     *            needs to be retained by the observer they must make a copy. This buffer does not
     *            need to be released by the observer.
     * @param host the host that the alternative service is available upon.
     * @param origin an optional origin that the alternative service is available upon. May be
     *            {@code null}.
     */
    void onAltSvcRead(ChannelHandlerContext ctx, int streamId, long maxAge, int port,
            ByteBuf protocolId, String host, String origin) throws Http2Exception;

    /**
     * Handles an inbound BLOCKED frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the stream that is blocked or 0 if the entire connection is blocked.
     */
    void onBlockedRead(ChannelHandlerContext ctx, int streamId) throws Http2Exception;
}
