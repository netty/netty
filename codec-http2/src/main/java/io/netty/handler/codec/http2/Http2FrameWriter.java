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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.io.Closeable;

/**
 * A writer responsible for marshalling HTTP/2 frames to the channel. All of the write methods in
 * this interface write to the context, but DO NOT FLUSH. To perform a flush, you must separately
 * call {@link ChannelHandlerContext#flush()}.
 */
public interface Http2FrameWriter extends Http2DataWriter, Closeable {

    /**
     * Writes a HEADERS frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param headers the headers to be sent.
     * @param padding the amount of padding to be added to the end of the frame
     * @param endStream indicates if this is the last frame to be sent for the stream.
     * @param promise the promise for the write.
     * @return the future for the write.
     */
    ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int padding, boolean endStream, ChannelPromise promise);

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
     * @param padding the amount of padding to be added to the end of the frame
     * @param endStream indicates if this is the last frame to be sent for the stream.
     * @param promise the promise for the write.
     * @return the future for the write.
     */
    ChannelFuture writeHeaders(ChannelHandlerContext ctx, int streamId, Http2Headers headers,
            int streamDependency, short weight, boolean exclusive, int padding, boolean endStream,
            ChannelPromise promise);

    /**
     * Writes a PRIORITY frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param streamDependency the stream on which this stream should depend, or 0 if it should
     *            depend on the connection.
     * @param weight the weight for this stream.
     * @param exclusive whether this stream should be the exclusive dependant of its parent.
     * @param promise the promise for the write.
     * @return the future for the write.
     */
    ChannelFuture writePriority(ChannelHandlerContext ctx, int streamId, int streamDependency,
            short weight, boolean exclusive, ChannelPromise promise);

    /**
     * Writes a RST_STREAM frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param errorCode the error code indicating the nature of the failure.
     * @param promise the promise for the write.
     * @return the future for the write.
     */
    ChannelFuture writeRstStream(ChannelHandlerContext ctx, int streamId, long errorCode,
            ChannelPromise promise);

    /**
     * Writes a SETTINGS frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param settings the settings to be sent.
     * @param promise the promise for the write.
     * @return the future for the write.
     */
    ChannelFuture writeSettings(ChannelHandlerContext ctx, Http2Settings settings,
            ChannelPromise promise);

    /**
     * Writes a SETTINGS acknowledgment to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param promise the promise for the write.
     * @return the future for the write.
     */
    ChannelFuture writeSettingsAck(ChannelHandlerContext ctx, ChannelPromise promise);

    /**
     * Writes a PING frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param ack indicates whether this is an ack of a PING frame previously received from the
     *            remote endpoint.
     * @param data the payload of the frame.
     * @param promise the promise for the write.
     * @return the future for the write.
     */
    ChannelFuture writePing(ChannelHandlerContext ctx, boolean ack, ByteBuf data,
            ChannelPromise promise);

    /**
     * Writes a PUSH_PROMISE frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param promisedStreamId the ID of the promised stream.
     * @param headers the headers to be sent.
     * @param padding the amount of padding to be added to the end of the frame
     * @param promise the promise for the write.
     * @return the future for the write.
     */
    ChannelFuture writePushPromise(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
            Http2Headers headers, int padding, ChannelPromise promise);

    /**
     * Writes a GO_AWAY frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param lastStreamId the last known stream of this endpoint.
     * @param errorCode the error code, if the connection was abnormally terminated.
     * @param debugData application-defined debug data.
     * @param promise the promise for the write.
     * @return the future for the write.
     */
    ChannelFuture writeGoAway(ChannelHandlerContext ctx, int lastStreamId, long errorCode,
            ByteBuf debugData, ChannelPromise promise);

    /**
     * Writes a WINDOW_UPDATE frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param streamId the stream for which to send the frame.
     * @param windowSizeIncrement the number of bytes by which the local inbound flow control window
     *            is increasing.
     * @param promise the promise for the write.
     * @return the future for the write.
     */
    ChannelFuture writeWindowUpdate(ChannelHandlerContext ctx, int streamId,
            int windowSizeIncrement, ChannelPromise promise);

    /**
     * Generic write method for any HTTP/2 frame. This allows writing of non-standard frames.
     *
     * @param ctx the context to use for writing.
     * @param frameType the frame type identifier.
     * @param streamId the stream for which to send the frame.
     * @param flags the flags to write for this frame.
     * @param payload the payload to write for this frame.
     * @param promise the promise for the write.
     * @return the future for the write.
     */
    ChannelFuture writeFrame(ChannelHandlerContext ctx, byte frameType, int streamId,
            Http2Flags flags, ByteBuf payload, ChannelPromise promise);

    /**
     * Closes this writer and frees any allocated resources.
     */
    @Override
    void close();

    /**
     * Sets the maximum size of the HPACK header table used for decoding HTTP/2 headers.
     */
    void maxHeaderTableSize(long max) throws Http2Exception;

    /**
     * Gets the maximum size of the HPACK header table used for decoding HTTP/2 headers.
     */
    long maxHeaderTableSize();

    /**
     * Sets the maximum allowed frame size. Attempts to write frames longer than this maximum will fail.
     */
    void maxFrameSize(int max);

    /**
     * Gets the maximum allowed frame size.
     */
    int maxFrameSize();

    /**
     * Sets the maximum allowed header elements.
     */
    void maxHeaderListSize(int max);

    /**
     * Gets the maximum allowed header elements.
     */
    int maxHeaderListSize();
}
