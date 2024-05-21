/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
 * An listener of HTTP/2 frames.
 */
public interface Http2FrameListener {
    /**
     * Handles an inbound {@code DATA} frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the subject stream for the frame.
     * @param data payload buffer for the frame. This buffer will be released by the codec.
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     * @param endOfStream Indicates whether this is the last frame to be sent from the remote endpoint for this stream.
     * @return the number of bytes that have been processed by the application. The returned bytes are used by the
     * inbound flow controller to determine the appropriate time to expand the inbound flow control window (i.e. send
     * {@code WINDOW_UPDATE}). Returning a value equal to the length of {@code data} + {@code padding} will effectively
     * opt-out of application-level flow control for this frame. Returning a value less than the length of {@code data}
     * + {@code padding} will defer the returning of the processed bytes, which the application must later return via
     * {@link Http2LocalFlowController#consumeBytes(Http2Stream, int)}. The returned value must
     * be >= {@code 0} and <= {@code data.readableBytes()} + {@code padding}.
     */
    int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                   boolean endOfStream) throws Http2Exception;

    /**
     * Handles an inbound {@code HEADERS} frame.
     * <p>
     * Only one of the following methods will be called for each {@code HEADERS} frame sequence.
     * One will be called when the {@code END_HEADERS} flag has been received.
     * <ul>
     * <li>{@link #onHeadersRead(ChannelHandlerContext, int, Http2Headers, int, boolean)}</li>
     * <li>{@link #onHeadersRead(ChannelHandlerContext, int, Http2Headers, int, short, boolean, int, boolean)}</li>
     * <li>{@link #onPushPromiseRead(ChannelHandlerContext, int, int, Http2Headers, int)}</li>
     * </ul>
     * <p>
     * To say it another way; the {@link Http2Headers} will contain all of the headers
     * for the current message exchange step (additional queuing is not necessary).
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the subject stream for the frame.
     * @param headers the received headers.
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     * @param endOfStream Indicates whether this is the last frame to be sent from the remote endpoint
     *            for this stream.
     */
    void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
            boolean endOfStream) throws Http2Exception;

    /**
     * Handles an inbound {@code HEADERS} frame with priority information specified.
     * Only called if {@code END_HEADERS} encountered.
     * <p>
     * Only one of the following methods will be called for each {@code HEADERS} frame sequence.
     * One will be called when the {@code END_HEADERS} flag has been received.
     * <ul>
     * <li>{@link #onHeadersRead(ChannelHandlerContext, int, Http2Headers, int, boolean)}</li>
     * <li>{@link #onHeadersRead(ChannelHandlerContext, int, Http2Headers, int, short, boolean, int, boolean)}</li>
     * <li>{@link #onPushPromiseRead(ChannelHandlerContext, int, int, Http2Headers, int)}</li>
     * </ul>
     * <p>
     * To say it another way; the {@link Http2Headers} will contain all of the headers
     * for the current message exchange step (additional queuing is not necessary).
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the subject stream for the frame.
     * @param headers the received headers.
     * @param streamDependency the stream on which this stream depends, or 0 if dependent on the
     *            connection.
     * @param weight the new weight for the stream.
     * @param exclusive whether or not the stream should be the exclusive dependent of its parent.
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     * @param endOfStream Indicates whether this is the last frame to be sent from the remote endpoint
     *            for this stream.
     */
    void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int streamDependency, short weight, boolean exclusive, int padding, boolean endOfStream)
            throws Http2Exception;

    /**
     * Handles an inbound {@code PRIORITY} frame.
     * <p>
     * Note that is it possible to have this method called and no stream object exist for either
     * {@code streamId}, {@code streamDependency}, or both. This is because the {@code PRIORITY} frame can be
     * sent/received when streams are in the {@code CLOSED} state.
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
     * Handles an inbound {@code RST_STREAM} frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the stream that is terminating.
     * @param errorCode the error code identifying the type of failure.
     */
    void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception;

    /**
     * Handles an inbound {@code SETTINGS} acknowledgment frame.
     * @param ctx the context from the handler where the frame was read.
     */
    void onSettingsAckRead(ChannelHandlerContext ctx) throws Http2Exception;

    /**
     * Handles an inbound {@code SETTINGS} frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param settings the settings received from the remote endpoint.
     */
    void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) throws Http2Exception;

    /**
     * Handles an inbound {@code PING} frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param data the payload of the frame.
     */
    void onPingRead(ChannelHandlerContext ctx, long data) throws Http2Exception;

    /**
     * Handles an inbound {@code PING} acknowledgment.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param data the payload of the frame.
     */
    void onPingAckRead(ChannelHandlerContext ctx, long data) throws Http2Exception;

    /**
     * Handles an inbound {@code PUSH_PROMISE} frame. Only called if {@code END_HEADERS} encountered.
     * <p>
     * Promised requests MUST be authoritative, cacheable, and safe.
     * See <a href="https://tools.ietf.org/html/rfc7540#section-8.2">[RFC 7540], Section 8.2</a>.
     * <p>
     * Only one of the following methods will be called for each {@code HEADERS} frame sequence.
     * One will be called when the {@code END_HEADERS} flag has been received.
     * <ul>
     * <li>{@link #onHeadersRead(ChannelHandlerContext, int, Http2Headers, int, boolean)}</li>
     * <li>{@link #onHeadersRead(ChannelHandlerContext, int, Http2Headers, int, short, boolean, int, boolean)}</li>
     * <li>{@link #onPushPromiseRead(ChannelHandlerContext, int, int, Http2Headers, int)}</li>
     * </ul>
     * <p>
     * To say it another way; the {@link Http2Headers} will contain all of the headers
     * for the current message exchange step (additional queuing is not necessary).
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the stream the frame was sent on.
     * @param promisedStreamId the ID of the promised stream.
     * @param headers the received headers.
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     */
    void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding) throws Http2Exception;

    /**
     * Handles an inbound {@code GO_AWAY} frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param lastStreamId the last known stream of the remote endpoint.
     * @param errorCode the error code, if abnormal closure.
     * @param debugData application-defined debug data. If this buffer needs to be retained by the
     *            listener they must make a copy.
     */
    void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData)
            throws Http2Exception;

    /**
     * Handles an inbound {@code WINDOW_UPDATE} frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the stream the frame was sent on.
     * @param windowSizeIncrement the increased number of bytes of the remote endpoint's flow
     *            control window.
     */
    void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement)
            throws Http2Exception;

    /**
     * Handler for a frame not defined by the HTTP/2 spec.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param frameType the frame type from the HTTP/2 header.
     * @param streamId the stream the frame was sent on.
     * @param flags the flags in the frame header.
     * @param payload the payload of the frame.
     */
    void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags, ByteBuf payload)
            throws Http2Exception;
}
