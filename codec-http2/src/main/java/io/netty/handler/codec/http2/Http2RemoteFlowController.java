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

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.UnstableApi;

/**
 * A {@link Http2FlowController} for controlling the flow of outbound {@code DATA} frames to the remote
 * endpoint.
 */
@UnstableApi
public interface Http2RemoteFlowController extends Http2FlowController {
    /**
     * Get the {@link ChannelHandlerContext} for which to apply flow control on.
     * <p>
     * This is intended for us by {@link FlowControlled} implementations only. Use with caution.
     * @return The {@link ChannelHandlerContext} for which to apply flow control on.
     */
    ChannelHandlerContext channelHandlerContext();

    /**
     * Queues a payload for transmission to the remote endpoint. There is no guarantee as to when the data
     * will be written or how it will be assigned to frames.
     * before sending.
     * <p>
     * Writes do not actually occur until {@link #writePendingBytes()} is called.
     *
     * @param stream the subject stream. Must not be the connection stream object.
     * @param payload payload to write subject to flow-control accounting and ordering rules.
     */
    void addFlowControlled(Http2Stream stream, FlowControlled payload);

    /**
     * Determine if {@code stream} has any {@link FlowControlled} frames currently queued.
     * @param stream the stream to check if it has flow controlled frames.
     * @return {@code true} if {@code stream} has any {@link FlowControlled} frames currently queued.
     */
    boolean hasFlowControlled(Http2Stream stream);

    /**
     * Write all data pending in the flow controller up to the flow-control limits.
     *
     * @throws Http2Exception throws if a protocol-related error occurred.
     */
    void writePendingBytes() throws Http2Exception;

    /**
     * Set the active listener on the flow-controller.
     *
     * @param listener to notify when the a write occurs, can be {@code null}.
     */
    void listener(Listener listener);

    /**
     * Determine if the {@code stream} has bytes remaining for use in the flow control window.
     * <p>
     * Note that this method respects channel writability. The channel must be writable for this method to
     * return {@code true}.
     *
     * @param stream The stream to test.
     * @return {@code true} if the {@code stream} has bytes remaining for use in the flow control window and the
     * channel is writable, {@code false} otherwise.
     */
    boolean isWritable(Http2Stream stream);

    /**
     * Notification that the writability of {@link #channelHandlerContext()} has changed.
     * @throws Http2Exception If any writes occur as a result of this call and encounter errors.
     */
    void channelWritabilityChanged() throws Http2Exception;

    /**
     * Explicitly update the dependency tree. This method is called independently of stream state changes.
     * @param childStreamId The stream identifier associated with the child stream.
     * @param parentStreamId The stream identifier associated with the parent stream. May be {@code 0},
     *                       to make {@code childStreamId} and immediate child of the connection.
     * @param weight The weight which is used relative to other child streams for {@code parentStreamId}. This value
     *               must be between 1 and 256 (inclusive).
     * @param exclusive If {@code childStreamId} should be the exclusive dependency of {@code parentStreamId}.
     */
    void updateDependencyTree(int childStreamId, int parentStreamId, short weight, boolean exclusive);

    /**
     * Implementations of this interface are used to progressively write chunks of the underlying
     * payload to the stream. A payload is considered to be fully written if {@link #write} has
     * been called at least once and it's {@link #size} is now zero.
     */
    interface FlowControlled {
        /**
         * The size of the payload in terms of bytes applied to the flow-control window.
         * Some payloads like {@code HEADER} frames have no cost against flow control and would
         * return 0 for this value even though they produce a non-zero number of bytes on
         * the wire. Other frames like {@code DATA} frames have both their payload and padding count
         * against flow-control.
         */
        int size();

        /**
         * Called to indicate that an error occurred before this object could be completely written.
         * <p>
         * The {@link Http2RemoteFlowController} will make exactly one call to either
         * this method or {@link #writeComplete()}.
         * </p>
         *
         * @param ctx The context to use if any communication needs to occur as a result of the error.
         * This may be {@code null} if an exception occurs when the connection has not been established yet.
         * @param cause of the error.
         */
        void error(ChannelHandlerContext ctx, Throwable cause);

        /**
         * Called after this object has been successfully written.
         * <p>
         * The {@link Http2RemoteFlowController} will make exactly one call to either
         * this method or {@link #error(ChannelHandlerContext, Throwable)}.
         * </p>
         */
        void writeComplete();

        /**
         * Writes up to {@code allowedBytes} of the encapsulated payload to the stream. Note that
         * a value of 0 may be passed which will allow payloads with flow-control size == 0 to be
         * written. The flow-controller may call this method multiple times with different values until
         * the payload is fully written, i.e it's size after the write is 0.
         * <p>
         * When an exception is thrown the {@link Http2RemoteFlowController} will make a call to
         * {@link #error(ChannelHandlerContext, Throwable)}.
         * </p>
         *
         * @param ctx The context to use for writing.
         * @param allowedBytes an upper bound on the number of bytes the payload can write at this time.
         */
        void write(ChannelHandlerContext ctx, int allowedBytes);

        /**
         * Merge the contents of the {@code next} message into this message so they can be written out as one unit.
         * This allows many small messages to be written as a single DATA frame.
         *
         * @return {@code true} if {@code next} was successfully merged and does not need to be enqueued,
         *     {@code false} otherwise.
         */
        boolean merge(ChannelHandlerContext ctx, FlowControlled next);
    }

    /**
     * Listener to the number of flow-controlled bytes written per stream.
     */
    interface Listener {
        /**
         * Notification that {@link Http2RemoteFlowController#isWritable(Http2Stream)} has changed for {@code stream}.
         * <p>
         * This method should not throw. Any thrown exceptions are considered a programming error and are ignored.
         * @param stream The stream which writability has changed for.
         */
        void writabilityChanged(Http2Stream stream);
    }
}
