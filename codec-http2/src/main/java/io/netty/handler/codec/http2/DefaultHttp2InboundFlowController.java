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

import static io.netty.handler.codec.http2.Http2CodecUtil.CONNECTION_STREAM_ID;
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_WINDOW_SIZE;
import static io.netty.handler.codec.http2.Http2Exception.flowControlError;
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * Basic implementation of {@link Http2InboundFlowController}.
 */
public class DefaultHttp2InboundFlowController implements Http2InboundFlowController {
    /**
     * The default ratio of window size to initial window size below which a {@code WINDOW_UPDATE}
     * is sent to expand the window.
     */
    public static final double DEFAULT_WINDOW_UPDATE_RATIO = 0.5;

    /**
     * A value for the window update ratio to be use in order to disable window updates for
     * a stream (i.e. {@code 0}).
     */
    public static final double WINDOW_UPDATE_OFF = 0.0;

    private final Http2Connection connection;
    private final Http2FrameWriter frameWriter;
    private int initialWindowSize = DEFAULT_WINDOW_SIZE;

    public DefaultHttp2InboundFlowController(Http2Connection connection, Http2FrameWriter frameWriter) {
        this.connection = checkNotNull(connection, "connection");
        this.frameWriter = checkNotNull(frameWriter, "frameWriter");

        // Add a flow state for the connection.
        connection.connectionStream().inboundFlow(new InboundFlowState(CONNECTION_STREAM_ID));

        // Register for notification of new streams.
        connection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void streamAdded(Http2Stream stream) {
                stream.inboundFlow(new InboundFlowState(stream.id()));
            }
        });
    }

    @Override
    public void initialInboundWindowSize(int newWindowSize) throws Http2Exception {
        int deltaWindowSize = newWindowSize - initialWindowSize;
        initialWindowSize = newWindowSize;

        // Apply the delta to all of the windows.
        connectionState().addAndGet(deltaWindowSize);
        for (Http2Stream stream : connection.activeStreams()) {
            state(stream).updatedInitialWindowSize(deltaWindowSize);
        }
    }

    @Override
    public int initialInboundWindowSize() {
        return initialWindowSize;
    }

    /**
     * Sets the per-stream ratio used to control when window update is performed. The value
     * specified is a ratio of the window size to the initial window size, below which a
     * {@code WINDOW_UPDATE} should be sent to expand the window. If the given value is
     * {@link #WINDOW_UPDATE_OFF} (i.e. {@code 0}) window updates will be disabled for the
     * stream.
     *
     * @throws IllegalArgumentException if the stream does not exist or if the ratio value is
     *             outside of the range 0 (inclusive) to 1 (exclusive).
     */
    public void setWindowUpdateRatio(ChannelHandlerContext ctx, int streamId, double ratio) {
        if (ratio < WINDOW_UPDATE_OFF || ratio >= 1.0) {
            throw new IllegalArgumentException("Invalid ratio: " + ratio);
        }

        InboundFlowState state = state(streamId);
        if (state == null) {
            throw new IllegalArgumentException("Stream does not exist: " + streamId);
        }

        // Set the ratio.
        state.setWindowUpdateRatio(ratio);

        // In the event of enabling window update, check to see if we need to update the window now.
        try {
            state.updateWindowIfAppropriate(ctx);
        } catch (Http2Exception e) {
            // Shouldn't happen since we only increase the window.
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
            throws Http2Exception {
        int dataLength = data.readableBytes() + padding;
        boolean windowUpdateSent = false;
        try {
            // Apply the connection-level flow control.
            windowUpdateSent = applyConnectionFlowControl(ctx, dataLength);

            // Apply the stream-level flow control.
            windowUpdateSent |= applyStreamFlowControl(ctx, streamId, dataLength, endOfStream);
        } finally {
            // Optimization: only flush once for any sent WINDOW_UPDATE frames.
            if (windowUpdateSent) {
                ctx.flush();
            }
        }
    }

    private InboundFlowState connectionState() {
        return state(connection.connectionStream());
    }

    private InboundFlowState state(int streamId) {
        return state(connection.stream(streamId));
    }

    private InboundFlowState state(Http2Stream stream) {
        return stream != null? (InboundFlowState) stream.inboundFlow() : null;
    }

    /**
     * Gets the window for the stream or raises a {@code PROTOCOL_ERROR} if not found.
     */
    private InboundFlowState stateOrFail(int streamId) throws Http2Exception {
        InboundFlowState state = state(streamId);
        if (state == null) {
            throw protocolError("Flow control window missing for stream: %d", streamId);
        }
        return state;
    }

    /**
     * Apply connection-wide flow control to the incoming data frame.
     *
     * @return {@code true} if a {@code WINDOW_UPDATE} frame was sent for the connection.
     */
    private boolean applyConnectionFlowControl(ChannelHandlerContext ctx, int dataLength)
            throws Http2Exception {
        // Remove the data length from the available window size. Throw if the lower bound
        // was exceeded.
        InboundFlowState connectionState = connectionState();
        connectionState.addAndGet(-dataLength);

        // If appropriate, send a WINDOW_UPDATE frame to open the window.
        return connectionState.updateWindowIfAppropriate(ctx);
    }

    /**
     * Apply stream-based flow control to the incoming data frame.
     *
     * @return {@code true} if a {@code WINDOW_UPDATE} frame was sent for the stream.
     */
    private boolean applyStreamFlowControl(ChannelHandlerContext ctx, int streamId, int dataLength,
            boolean endOfStream) throws Http2Exception {
        // Remove the data length from the available window size. Throw if the lower bound
        // was exceeded.
        InboundFlowState state = stateOrFail(streamId);
        state.addAndGet(-dataLength);
        state.endOfStream(endOfStream);

        // If appropriate, send a WINDOW_UPDATE frame to open the window.
        return state.updateWindowIfAppropriate(ctx);
    }

    /**
     * Flow control window state for an individual stream.
     */
    private final class InboundFlowState implements FlowState {
        private final int streamId;
        private int window;
        private int lowerBound;
        private boolean endOfStream;
        private double windowUpdateRatio = DEFAULT_WINDOW_UPDATE_RATIO;

        InboundFlowState(int streamId) {
            this.streamId = streamId;
            window = initialWindowSize;
        }

        @Override
        public int window() {
            return window;
        }

        void endOfStream(boolean endOfStream) {
            this.endOfStream = endOfStream;
        }

        /**
         * Enables or disables window updates for this stream.
         */
        void setWindowUpdateRatio(double ratio) {
            windowUpdateRatio = ratio;
        }

        /**
         * Updates the flow control window for this stream if it is appropriate.
         *
         * @return {@code} true if a {@code WINDOW_UPDATE} frame was sent.
         */
        boolean updateWindowIfAppropriate(ChannelHandlerContext ctx) throws Http2Exception {
            if (windowUpdateRatio == WINDOW_UPDATE_OFF || endOfStream || initialWindowSize <= 0) {
                return false;
            }

            int threshold = (int) (initialWindowSize * windowUpdateRatio);
            if (window <= threshold) {
                updateWindow(ctx);
                return true;
            }
            return false;
        }

        /**
         * Adds the given delta to the window size and returns the new value.
         *
         * @param delta the delta in the initial window size.
         * @throws Http2Exception thrown if the new window is less than the allowed lower bound.
         */
        int addAndGet(int delta) throws Http2Exception {
            // Apply the delta. Even if we throw an exception we want to have taken this delta into
            // account.
            window += delta;
            if (delta > 0) {
                lowerBound = 0;
            }

            // Window size can become negative if we sent a SETTINGS frame that reduces the
            // size of the transfer window after the peer has written data frames.
            // The value is bounded by the length that SETTINGS frame decrease the window.
            // This difference is stored for the connection when writing the SETTINGS frame
            // and is cleared once we send a WINDOW_UPDATE frame.
            if (window < lowerBound) {
                if (streamId == CONNECTION_STREAM_ID) {
                    throw protocolError("Connection flow control window exceeded");
                } else {
                    throw flowControlError("Flow control window exceeded for stream: %d", streamId);
                }
            }

            return window;
        }

        /**
         * Called when sending a SETTINGS frame with a new initial window size. If the window has
         * gotten smaller (i.e. deltaWindowSize < 0), the lower bound is set to that value. This
         * will temporarily allow for receipt of data frames which were sent by the remote endpoint
         * before receiving the SETTINGS frame.
         *
         * @param delta the delta in the initial window size.
         * @throws Http2Exception thrown if integer overflow occurs on the window.
         */
        void updatedInitialWindowSize(int delta) throws Http2Exception {
            if (delta > 0 && window > Integer.MAX_VALUE - delta) {
                // Integer overflow.
                throw flowControlError("Flow control window overflowed for stream: %d", streamId);
            }
            window += delta;

            if (delta < 0) {
                lowerBound = delta;
            }
        }

        /**
         * Called to perform a window update for this stream (or connection). Updates the window
         * size back to the size of the initial window and sends a window update frame to the remote
         * endpoint.
         */
        void updateWindow(ChannelHandlerContext ctx) throws Http2Exception {
            // Expand the window for this stream back to the size of the initial window.
            int deltaWindowSize = initialWindowSize - window;
            addAndGet(deltaWindowSize);

            // Send a window update for the stream/connection.
            frameWriter.writeWindowUpdate(ctx, streamId, deltaWindowSize, ctx.newPromise());
        }
    }
}
