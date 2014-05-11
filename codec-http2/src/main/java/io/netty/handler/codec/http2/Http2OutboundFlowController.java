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

/**
 * Controls the outbound flow of data frames to the remote endpoint.
 */
public interface Http2OutboundFlowController {

    /**
     * Interface that abstracts the writing of frames to the remote endpoint.
     */
    interface FrameWriter {

        /**
         * Writes a single data frame to the remote endpoint.
         */
        void writeFrame(int streamId, ByteBuf data, int padding, boolean endStream,
                boolean endSegment, boolean compressed);

        /**
         * Called if an error occurred before the write could take place. Sets the failure on the
         * channel promise.
         */
        void setFailure(Throwable cause);
    }

    /**
     * Creates a new priority for the a stream with respect to out-bound flow control.
     *
     * @param streamId the stream to be prioritized.
     * @param parent an optional stream that the given stream should depend on. Zero, if no
     *            dependency.
     * @param weight the weight to be assigned to this stream relative to its parent. This value
     *            must be between 1 and 256 (inclusive)
     * @param exclusive indicates that the stream should be the exclusive dependent on its parent.
     *            This only applies if the stream has a parent.
     */
    void addStream(int streamId, int parent, short weight, boolean exclusive);

    /**
     * Updates the priority for a stream with respect to out-bound flow control.
     *
     * @param streamId the stream to be prioritized.
     * @param parent an optional stream that the given stream should depend on. Zero, if no
     *            dependency.
     * @param weight the weight to be assigned to this stream relative to its parent. This value
     *            must be between 1 and 256 (inclusive)
     * @param exclusive indicates that the stream should be the exclusive dependent on its parent.
     *            This only applies if the stream has a parent.
     */
    void updateStream(int streamId, int parent, short weight, boolean exclusive);

    /**
     * Removes the given stream from those considered for out-bound flow control.
     */
    void removeStream(int streamId);

    /**
     * Sets the initial size of the connection's outbound flow control window. The outbound flow
     * control windows for all streams are updated by the delta in the initial window size. This is
     * called as part of the processing of a SETTINGS frame received from the remote endpoint.
     *
     * @param newWindowSize the new initial window size.
     */
    void initialOutboundWindowSize(int newWindowSize) throws Http2Exception;

    /**
     * Gets the initial size of the connection's outbound flow control window.
     */
    int initialOutboundWindowSize();

    /**
     * Updates the size of the stream's outbound flow control window. This is called upon receiving
     * a WINDOW_UPDATE frame from the remote endpoint.
     *
     * @param streamId the ID of the stream, or zero if the window is for the entire connection.
     * @param deltaWindowSize the change in size of the outbound flow control window.
     * @throws Http2Exception thrown if a protocol-related error occurred.
     */
    void updateOutboundWindowSize(int streamId, int deltaWindowSize) throws Http2Exception;

    /**
     * Indicates that the given stream or the entire connection is blocked and that no more messages
     * should be sent.
     *
     * @param streamId the stream ID that is blocked or zero if the entire connection is blocked.
     * @throws Http2Exception thrown if a protocol-related error occurred.
     */
    void setBlocked(int streamId) throws Http2Exception;

    /**
     * Sends the frame with outbound flow control applied. The frame may be written at a later time,
     * depending on whether the remote endpoint can receive the frame now.
     * <p/>
     * Data frame flow control processing requirements:
     * <p/>
     * Sender must not send a data frame with data length greater than the transfer window size.
     * After sending each data frame, the stream's transfer window size is decremented by the amount
     * of data transmitted. When the window size becomes less than or equal to 0, the sender must
     * pause transmitting data frames.
     *
     * @param streamId the ID of the stream on which the data is to be sent.
     * @param data the data be be sent to the remote endpoint.
     * @param padding the number of bytes of padding to be added to the frame.
     * @param endStream indicates whether this frames is to be the last sent on this stream.
     * @param endSegment indicates whether this is to be the last frame in the segment.
     * @param compressed whether the data is compressed using gzip compression.
     * @param frameWriter peforms to the write of the frame to the remote endpoint.
     * @throws Http2Exception thrown if a protocol-related error occurred.
     */
    void sendFlowControlled(int streamId, ByteBuf data, int padding, boolean endStream,
            boolean endSegment, boolean compressed, FrameWriter frameWriter) throws Http2Exception;
}
