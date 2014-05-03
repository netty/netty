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
import static io.netty.handler.codec.http2.Http2CodecUtil.DEFAULT_FLOW_CONTROL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.Http2Exception.flowControlError;
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.Map;

/**
 * Basic implementation of {@link Http2InboundFlowController}.
 */
public class DefaultHttp2InboundFlowController implements Http2InboundFlowController {

    private int initialWindowSize = DEFAULT_FLOW_CONTROL_WINDOW_SIZE;
    private final FlowState connectionState = new FlowState(CONNECTION_STREAM_ID);
    private final Map<Integer, FlowState> streamStates = new HashMap<Integer, FlowState>();

    @Override
    public void addStream(int streamId) {
        if (streamId <= 0) {
            throw new IllegalArgumentException("Stream ID must be > 0");
        }
        if (streamStates.containsKey(streamId)) {
            throw new IllegalArgumentException("Stream " + streamId + " already exists.");
        }
        streamStates.put(streamId, new FlowState(streamId));
    }

    @Override
    public void removeStream(int streamId) {
        streamStates.remove(streamId);
    }

    @Override
    public void initialInboundWindowSize(int newWindowSize) throws Http2Exception {
        int deltaWindowSize = newWindowSize - initialWindowSize;
        initialWindowSize = newWindowSize;

        // Apply the delta to all of the windows.
        connectionState.addAndGet(deltaWindowSize);
        for (FlowState window : streamStates.values()) {
            window.updatedInitialWindowSize(deltaWindowSize);
        }
    }

    @Override
    public int initialInboundWindowSize() {
        return initialWindowSize;
    }

    @Override
    public void applyInboundFlowControl(int streamId, ByteBuf data, int padding,
            boolean endOfStream, boolean endOfSegment, boolean compressed, FrameWriter frameWriter)
            throws Http2Exception {
        int dataLength = data.readableBytes();
        applyConnectionFlowControl(dataLength, frameWriter);
        applyStreamFlowControl(streamId, dataLength, endOfStream, frameWriter);
    }

    /**
     * Apply connection-wide flow control to the incoming data frame.
     */
    private void applyConnectionFlowControl(int dataLength, FrameWriter frameWriter)
            throws Http2Exception {
        // Remove the data length from the available window size. Throw if the lower bound
        // was exceeded.
        connectionState.addAndGet(-dataLength);

        // If less than the window update threshold remains, restore the window size
        // to the initial value and send a window update to the remote endpoint indicating
        // the new window size.
        if (connectionState.windowSize() <= getWindowUpdateThreshold()) {
            connectionState.updateWindow(frameWriter);
        }
    }

    /**
     * Apply stream-based flow control to the incoming data frame.
     */
    private void applyStreamFlowControl(int streamId, int dataLength, boolean endOfStream,
            FrameWriter frameWriter) throws Http2Exception {
        // Remove the data length from the available window size. Throw if the lower bound
        // was exceeded.
        FlowState state = getStateOrFail(streamId);
        state.addAndGet(-dataLength);

        // If less than the window update threshold remains, restore the window size
        // to the initial value and send a window update to the remote endpoint indicating
        // the new window size.
        if (state.windowSize() <= getWindowUpdateThreshold() && !endOfStream) {
            state.updateWindow(frameWriter);
        }
    }

    /**
     * Gets the threshold for a window size below which a window update should be issued.
     */
    private int getWindowUpdateThreshold() {
        return initialWindowSize / 2;
    }

    /**
     * Gets the window for the stream or raises a {@code PROTOCOL_ERROR} if not found.
     */
    private FlowState getStateOrFail(int streamId) throws Http2Exception {
        FlowState window = streamStates.get(streamId);
        if (window == null) {
            throw protocolError("Flow control window missing for stream: %d", streamId);
        }
        return window;
    }

    /**
     * Flow control window state for an individual stream.
     */
    private final class FlowState {
        private int windowSize;
        private int lowerBound;
        private final int streamId;

        FlowState(int streamId) {
            this.streamId = streamId;
            windowSize = initialWindowSize;
        }

        int windowSize() {
            return windowSize;
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
            windowSize += delta;
            if (delta > 0) {
                lowerBound = 0;
            }

            // Window size can become negative if we sent a SETTINGS frame that reduces the
            // size of the transfer window after the peer has written data frames.
            // The value is bounded by the length that SETTINGS frame decrease the window.
            // This difference is stored for the connection when writing the SETTINGS frame
            // and is cleared once we send a WINDOW_UPDATE frame.
            if (windowSize < lowerBound) {
                if (streamId == CONNECTION_STREAM_ID) {
                    throw protocolError("Connection flow control window exceeded");
                } else {
                    throw flowControlError("Flow control window exceeded for stream: %d", streamId);
                }
            }

            return windowSize;
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
            if (delta > 0 && windowSize > Integer.MAX_VALUE - delta) {
                // Integer overflow.
                throw flowControlError("Flow control window overflowed for stream: %d", streamId);
            }
            windowSize += delta;

            if (delta < 0) {
                lowerBound = delta;
            }
        }

        /**
         * Called to perform a window update for this stream (or connection). Updates the window
         * size back to the size of the initial window and sends a window update frame to the remote
         * endpoint.
         */
        void updateWindow(FrameWriter frameWriter) throws Http2Exception {
            // Expand the window for this stream back to the size of the initial window.
            int deltaWindowSize = initialWindowSize - windowSize();
            addAndGet(deltaWindowSize);

            // Send a window update for the stream/connection.
            frameWriter.writeFrame(streamId, deltaWindowSize);
        }
    }
}
