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
import static io.netty.handler.codec.http2.Http2Error.FLOW_CONTROL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.streamError;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
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
     * The default maximum connection size used as a limit when the number of active streams is
     * large. Set to 2 MiB.
     */
    public static final int DEFAULT_MAX_CONNECTION_WINDOW_SIZE = 1048576 * 2;

    private final Http2Connection connection;
    private final Http2FrameWriter frameWriter;
    private final double windowUpdateRatio;
    private int maxConnectionWindowSize = DEFAULT_MAX_CONNECTION_WINDOW_SIZE;
    private int initialWindowSize = DEFAULT_WINDOW_SIZE;

    public DefaultHttp2InboundFlowController(Http2Connection connection, Http2FrameWriter frameWriter) {
        this(connection, frameWriter, DEFAULT_WINDOW_UPDATE_RATIO);
    }

    public DefaultHttp2InboundFlowController(Http2Connection connection,
            Http2FrameWriter frameWriter, double windowUpdateRatio) {
        this.connection = checkNotNull(connection, "connection");
        this.frameWriter = checkNotNull(frameWriter, "frameWriter");
        if (Double.compare(windowUpdateRatio, 0.0) <= 0 || Double.compare(windowUpdateRatio, 1.0) >= 0) {
            throw new IllegalArgumentException("Invalid ratio: " + windowUpdateRatio);
        }
        this.windowUpdateRatio = windowUpdateRatio;

        // Add a flow state for the connection.
        final Http2Stream connectionStream = connection.connectionStream();
        final FlowState connectionFlowState = new FlowState(connectionStream);
        connectionStream.inboundFlow(connectionFlowState);
        connectionStream.garbageCollector(connectionFlowState);

        // Register for notification of new streams.
        connection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void streamAdded(Http2Stream stream) {
                final FlowState flowState = new FlowState(stream);
                stream.inboundFlow(flowState);
                stream.garbageCollector(flowState);
            }
        });
    }

    public DefaultHttp2InboundFlowController setMaxConnectionWindowSize(int maxConnectionWindowSize) {
        if (maxConnectionWindowSize <= 0) {
            throw new IllegalArgumentException("maxConnectionWindowSize must be > 0");
        }
        this.maxConnectionWindowSize = maxConnectionWindowSize;
        return this;
    }

    @Override
    public void initialInboundWindowSize(int newWindowSize) throws Http2Exception {
        int deltaWindowSize = newWindowSize - initialWindowSize;
        initialWindowSize = newWindowSize;

        // Apply the delta to all of the windows.
        for (Http2Stream stream : connection.activeStreams()) {
            state(stream).updatedInitialWindowSize(deltaWindowSize);
        }
    }

    @Override
    public int initialInboundWindowSize() {
        return initialWindowSize;
    }

    @Override
    public void applyFlowControl(ChannelHandlerContext ctx, int streamId, ByteBuf data,
            int padding, boolean endOfStream) throws Http2Exception {
        int dataLength = data.readableBytes() + padding;
        int delta = -dataLength;

        // Apply the connection-level flow control. Immediately return the bytes for the connection
        // window so that data on this stream does not starve other stream.
        FlowState connectionState = connectionState();
        connectionState.addAndGet(delta);
        connectionState.returnProcessedBytes(dataLength);
        connectionState.updateWindowIfAppropriate(ctx);

        // Apply the stream-level flow control, but do not return the bytes immediately.
        FlowState state = stateOrFail(streamId);
        state.endOfStream(endOfStream);
        state.addAndGet(delta);
    }

    private FlowState connectionState() {
        return state(connection.connectionStream());
    }

    private FlowState state(int streamId) {
        Http2Stream stream = connection.stream(streamId);
        return stream != null ? state(stream) : null;
    }

    private FlowState state(Http2Stream stream) {
        return (FlowState) stream.inboundFlow();
    }

    /**
     * Gets the window for the stream or raises a {@code PROTOCOL_ERROR} if not found.
     */
    private FlowState stateOrFail(int streamId) throws Http2Exception {
        FlowState state = state(streamId);
        if (state == null) {
            throw connectionError(PROTOCOL_ERROR, "Flow control window missing for stream: %d", streamId);
        }
        return state;
    }

    /**
     * Flow control window state for an individual stream.
     */
    private final class FlowState implements Http2FlowState, Http2FlowControlWindowManager {
        private final Http2Stream stream;

        /**
         * The actual flow control window that is decremented as soon as {@code DATA} arrives.
         */
        private int window;

        /**
         * A view of {@link #window} that is used to determine when to send {@code WINDOW_UPDATE}
         * frames. Decrementing this window for received {@code DATA} frames is delayed until the
         * application has indicated that the data has been fully processed. This prevents sending
         * a {@code WINDOW_UPDATE} until the number of processed bytes drops below the threshold.
         */
        private int processedWindow;

        private int lowerBound;
        private boolean endOfStream;

        FlowState(Http2Stream stream) {
            this.stream = stream;
            window = initialWindowSize;
            processedWindow = window;
        }

        @Override
        public int window() {
            return window;
        }

        void endOfStream(boolean endOfStream) {
            this.endOfStream = endOfStream;
        }

        /**
         * Returns the initial size of this window.
         */
        int initialWindowSize() {
            int maxWindowSize = initialWindowSize;
            if (stream.id() == CONNECTION_STREAM_ID) {
                // Determine the maximum number of streams that we can allow without integer overflow
                // of maxWindowSize * numStreams. Also take care to avoid division by zero when
                // maxWindowSize == 0.
                int maxNumStreams = Integer.MAX_VALUE;
                if (maxWindowSize > 0) {
                    maxNumStreams /= maxWindowSize;
                }
                int numStreams = Math.min(maxNumStreams, Math.max(1, connection.numActiveStreams()));
                maxWindowSize = Math.min(maxConnectionWindowSize, maxWindowSize * numStreams);
            }
            return maxWindowSize;
        }

        @Override
        public void returnProcessedBytes(ChannelHandlerContext ctx, int numBytes) throws Http2Exception {
            if (stream.id() == CONNECTION_STREAM_ID) {
                throw new UnsupportedOperationException("Returning bytes for the connection window is not supported");
            }
            checkNotNull(ctx, "ctx");
            if (numBytes <= 0) {
                throw new IllegalArgumentException("numBytes must be positive");
            }

            // Return the bytes processed and update the window.
            returnProcessedBytes(numBytes);
            updateWindowIfAppropriate(ctx);
        }

        @Override
        public int unProcessedBytes() {
            return processedWindow - window;
        }

        @Override
        public Http2Stream stream() {
            return stream;
        }

        /**
         * Updates the flow control window for this stream if it is appropriate.
         */
        void updateWindowIfAppropriate(ChannelHandlerContext ctx) {
            if (endOfStream || initialWindowSize <= 0) {
                return;
            }

            int threshold = (int) (initialWindowSize() * windowUpdateRatio);
            if (processedWindow <= threshold) {
                updateWindow(ctx);
            }
        }

        /**
         * Returns the processed bytes for this stream.
         */
        void returnProcessedBytes(int delta) throws Http2Exception {
            if (processedWindow - delta < window) {
                throw streamError(stream.id(), INTERNAL_ERROR,
                        "Attempting to return too many bytes for stream %d", stream.id());
            }
            processedWindow -= delta;
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
            if (delta < 0 && window < lowerBound) {
                if (stream.id() == CONNECTION_STREAM_ID) {
                    throw connectionError(FLOW_CONTROL_ERROR, "Connection flow control window exceeded");
                } else {
                    throw streamError(stream.id(), FLOW_CONTROL_ERROR,
                            "Flow control window exceeded for stream: %d", stream.id());
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
            if (delta > 0 && window > Integer.MAX_VALUE - delta) { // Integer overflow.
                throw connectionError(PROTOCOL_ERROR, "Flow control window overflowed for stream: %d", stream.id());
            }
            window += delta;
            processedWindow += delta;

            if (delta < 0) {
                lowerBound = delta;
            }
        }

        /**
         * Called to perform a window update for this stream (or connection). Updates the window
         * size back to the size of the initial window and sends a window update frame to the remote
         * endpoint.
         */
        void updateWindow(ChannelHandlerContext ctx) {
            // Expand the window for this stream back to the size of the initial window.
            int deltaWindowSize = initialWindowSize() - processedWindow;
            processedWindow += deltaWindowSize;
            try {
                addAndGet(deltaWindowSize);
            } catch (Http2Exception e) {
                // This should never fail since we're adding.
                throw new AssertionError("Caught exception while updating window with delta: "
                        + deltaWindowSize);
            }

            // Send a window update for the stream/connection.
            frameWriter.writeWindowUpdate(ctx, stream.id(), deltaWindowSize, ctx.newPromise());
            ctx.flush();
        }
    }
}
