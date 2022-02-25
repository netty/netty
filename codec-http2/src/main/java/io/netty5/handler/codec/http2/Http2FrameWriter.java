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

package io.netty5.handler.codec.http2;

import io.netty5.buffer.ByteBuf;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.util.concurrent.Future;
import io.netty5.util.internal.UnstableApi;

import java.io.Closeable;

/**
 * A writer responsible for marshaling HTTP/2 frames to the channel. All of the write methods in
 * this interface write to the context, but DO NOT FLUSH. To perform a flush, you must separately
 * call {@link ChannelHandlerContext#flush()}.
 */
@UnstableApi
public interface Http2FrameWriter extends Http2DataWriter, Closeable {
    /**
     * Configuration specific to {@link Http2FrameWriter}
     */
    interface Configuration {
        /**
         * Get the {@link Http2HeadersEncoder.Configuration} for this {@link Http2FrameWriter}
         */
        Http2HeadersEncoder.Configuration headersConfiguration();

        /**
         * Get the {@link Http2FrameSizePolicy} for this {@link Http2FrameWriter}
         */
        Http2FrameSizePolicy frameSizePolicy();
    }

    /**
     * Writes a HEADERS frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param headers the headers to be sent.
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     * @param endStream indicates if this is the last frame to be sent for the stream.
     * @return the future for the write.
     * <a href="https://tools.ietf.org/html/rfc7540#section-10.5.1">Section 10.5.1</a> states the following:
     * <pre>
     * The header block MUST be processed to ensure a consistent connection state, unless the connection is closed.
     * </pre>
     * If this call has modified the HPACK header state you <strong>MUST</strong> throw a connection error.
     * <p>
     * If this call has <strong>NOT</strong> modified the HPACK header state you are free to throw a stream error.
     */
    Future<Void> writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                        int padding, boolean endStream);

    /**
     * Writes a HEADERS frame with priority specified to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param headers the headers to be sent.
     * @param streamDependency the stream on which this stream should depend, or 0 if it should
     *            depend on the connection.
     * @param weight the weight for this stream.
     * @param exclusive whether this stream should be the exclusive dependant of its parent.
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     * @param endStream indicates if this is the last frame to be sent for the stream.
     * @return the future for the write.
     * <a href="https://tools.ietf.org/html/rfc7540#section-10.5.1">Section 10.5.1</a> states the following:
     * <pre>
     * The header block MUST be processed to ensure a consistent connection state, unless the connection is closed.
     * </pre>
     * If this call has modified the HPACK header state you <strong>MUST</strong> throw a connection error.
     * <p>
     * If this call has <strong>NOT</strong> modified the HPACK header state you are free to throw a stream error.
     */
    Future<Void> writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
                               int streamDependency, short weight, boolean exclusive, int padding, boolean endStream);

    /**
     * Writes a PRIORITY frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param streamDependency the stream on which this stream should depend, or 0 if it should
     *            depend on the connection.
     * @param weight the weight for this stream.
     * @param exclusive whether this stream should be the exclusive dependant of its parent.
     * @return the future for the write.
     */
    Future<Void> writePriority(ChannelHandlerContext ctx, int streamId, int streamDependency,
            short weight, boolean exclusive);

    /**
     * Writes a RST_STREAM frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param errorCode the error code indicating the nature of the failure.
     * @return the future for the write.
     */
    Future<Void> writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode);

    /**
     * Writes a SETTINGS frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param settings the settings to be sent.
     * @return the future for the write.
     */
    Future<Void> writeSettings(ChannelHandlerContext ctx, Http2Settings settings);

    /**
     * Writes a SETTINGS acknowledgment to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @return the future for the write.
     */
    Future<Void> writeSettingsAck(ChannelHandlerContext ctx);

    /**
     * Writes a PING frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param ack indicates whether this is an ack of a PING frame previously received from the
     *            remote endpoint.
     * @param data the payload of the frame.
     * @return the future for the write.
     */
    Future<Void> writePing(ChannelHandlerContext ctx, boolean ack, long data);

    /**
     * Writes a PUSH_PROMISE frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param promisedStreamId the ID of the promised stream.
     * @param headers the headers to be sent.
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     * @return the future for the write.
     * <a href="https://tools.ietf.org/html/rfc7540#section-10.5.1">Section 10.5.1</a> states the following:
     * <pre>
     * The header block MUST be processed to ensure a consistent connection state, unless the connection is closed.
     * </pre>
     * If this call has modified the HPACK header state you <strong>MUST</strong> throw a connection error.
     * <p>
     * If this call has <strong>NOT</strong> modified the HPACK header state you are free to throw a stream error.
     */
    Future<Void> writePushPromise(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                   Http2Headers headers, int padding);

    /**
     * Writes a GO_AWAY frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param lastStreamId the last known stream of this endpoint.
     * @param errorCode the error code, if the connection was abnormally terminated.
     * @param debugData application-defined debug data. This will be released by this method.
     * @return the future for the write.
     */
    Future<Void> writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
            ByteBuf debugData);

    /**
     * Writes a WINDOW_UPDATE frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param windowSizeIncrement the number of bytes by which the local inbound flow control window
     *            is increasing.
     * @return the future for the write.
     */
    Future<Void> writeWindowUpdate(ChannelHandlerContext ctx, int streamId,
            int windowSizeIncrement);

    /**
     * Generic write method for any HTTP/2 frame. This allows writing of non-standard frames.
     *
     * @param ctx the context to use for writing.
     * @param frameType the frame type identifier.
     * @param streamId the stream for which to send the frame.
     * @param flags the flags to write for this frame.
     * @param payload the payload to write for this frame. This will be released by this method.
     * @return the future for the write.
     */
    Future<Void> writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
            Http2Flags flags, ByteBuf payload);

    /**
     * Get the configuration related elements for this {@link Http2FrameWriter}
     */
    Configuration configuration();

    /**
     * Closes this writer and frees any allocated resources.
     */
    @Override
    void close();
}
