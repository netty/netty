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
 * A writer responsible for marshalling HTTP/2 frames to the channel.
 */
public interface Http2FrameWriter extends Closeable {

    /**
     * Writes a DATA frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param promise the promise for the write.
     * @param streamId the stream for which to send the frame.
     * @param data the payload of the frame.
     * @param padding the amount of padding to be added to the end of the frame
     * @param endStream indicates if this is the last frame to be sent for the stream.
     * @param endSegment indicates if this is the last frame in the current segment.
     * @return the future for the write.
     */
    ChannelFuture writeData(ChannelHandlerContext ctx, ChannelPromise promise, int streamId,
            ByteBuf data, int padding, boolean endStream, boolean endSegment);

    /**
     * Writes a HEADERS frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param promise the promise for the write.
     * @param streamId the stream for which to send the frame.
     * @param headers the headers to be sent.
     * @param padding the amount of padding to be added to the end of the frame
     * @param endStream indicates if this is the last frame to be sent for the stream.
     * @param endSegment indicates if this is the last frame in the current segment.
     * @return the future for the write.
     */
    ChannelFuture writeHeaders(ChannelHandlerContext ctx, ChannelPromise promise, int streamId,
            Http2Headers headers, int padding, boolean endStream, boolean endSegment);

    /**
     * Writes a HEADERS frame with priority specified to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param promise the promise for the write.
     * @param streamId the stream for which to send the frame.
     * @param headers the headers to be sent.
     * @param streamDependency the stream on which this stream should depend, or 0 if it should
     *            depend on the connection.
     * @param weight the weight for this stream.
     * @param exclusive whether this stream should be the exclusive dependant of its parent.
     * @param padding the amount of padding to be added to the end of the frame
     * @param endStream indicates if this is the last frame to be sent for the stream.
     * @param endSegment indicates if this is the last frame in the current segment.
     * @return the future for the write.
     */
    ChannelFuture writeHeaders(ChannelHandlerContext ctx, ChannelPromise promise, int streamId,
            Http2Headers headers, int streamDependency, short weight, boolean exclusive,
            int padding, boolean endStream, boolean endSegment);

    /**
     * Writes a PRIORITY frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param promise the promise for the write.
     * @param streamId the stream for which to send the frame.
     * @param streamDependency the stream on which this stream should depend, or 0 if it should
     *            depend on the connection.
     * @param weight the weight for this stream.
     * @param exclusive whether this stream should be the exclusive dependant of its parent.
     * @return the future for the write.
     */
    ChannelFuture writePriority(ChannelHandlerContext ctx, ChannelPromise promise, int streamId,
            int streamDependency, short weight, boolean exclusive);

    /**
     * Writes a RST_STREAM frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param promise the promise for the write.
     * @param streamId the stream for which to send the frame.
     * @param errorCode the error code indicating the nature of the failure.
     * @return the future for the write.
     */
    ChannelFuture writeRstStream(ChannelHandlerContext ctx, ChannelPromise promise, int streamId,
            long errorCode);

    /**
     * Writes a SETTINGS frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param promise the promise for the write.
     * @param settings the settings to be sent.
     * @return the future for the write.
     */
    ChannelFuture writeSettings(ChannelHandlerContext ctx, ChannelPromise promise,
            Http2Settings settings);

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
     * @param promise the promise for the write.
     * @param ack indicates whether this is an ack of a PING frame previously received from the
     *            remote endpoint.
     * @param data the payload of the frame.
     * @return the future for the write.
     */
    ChannelFuture writePing(ChannelHandlerContext ctx, ChannelPromise promise, boolean ack,
            ByteBuf data);

    /**
     * Writes a PUSH_PROMISE frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param promise the promise for the write.
     * @param streamId the stream for which to send the frame.
     * @param promisedStreamId the ID of the promised stream.
     * @param headers the headers to be sent.
     * @param padding the amount of padding to be added to the end of the frame
     * @return the future for the write.
     */
    ChannelFuture writePushPromise(ChannelHandlerContext ctx, ChannelPromise promise, int streamId,
            int promisedStreamId, Http2Headers headers, int padding);

    /**
     * Writes a GO_AWAY frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param promise the promise for the write.
     * @param lastStreamId the last known stream of this endpoint.
     * @param errorCode the error code, if the connection was abnormally terminated.
     * @param debugData application-defined debug data.
     * @return the future for the write.
     */
    ChannelFuture writeGoAway(ChannelHandlerContext ctx, ChannelPromise promise, int lastStreamId,
            long errorCode, ByteBuf debugData);

    /**
     * Writes a WINDOW_UPDATE frame to the remote endpoint.
     *
     * @param ctx the context to use for writing.
     * @param promise the promise for the write.
     * @param streamId the stream for which to send the frame.
     * @param windowSizeIncrement the number of bytes by which the local inbound flow control window
     *            is increasing.
     * @return the future for the write.
     */
    ChannelFuture writeWindowUpdate(ChannelHandlerContext ctx, ChannelPromise promise,
            int streamId, int windowSizeIncrement);

    /**
     * Generic write method for any HTTP/2 frame. This allows writing of non-standard frames.
     *
     * @param ctx the context to use for writing.
     * @param promise the promise for the write.
     * @param frameType the frame type identifier.
     * @param streamId the stream for which to send the frame.
     * @param flags the flags to write for this frame.
     * @param payload the payload to write for this frame.
     * @return the future for the write.
     */
    ChannelFuture writeFrame(ChannelHandlerContext ctx, ChannelPromise promise, byte frameType,
            int streamId, Http2Flags flags, ByteBuf payload);

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
}
