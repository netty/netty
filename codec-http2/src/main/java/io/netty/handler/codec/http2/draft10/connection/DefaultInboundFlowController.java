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

package io.netty.handler.codec.http2.draft10.connection;

import static io.netty.handler.codec.http2.draft10.Http2Exception.flowControlError;
import static io.netty.handler.codec.http2.draft10.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.draft10.connection.Http2ConnectionUtil.DEFAULT_FLOW_CONTROL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.CONNECTION_STREAM_ID;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2WindowUpdateFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2WindowUpdateFrame;

import java.util.HashMap;
import java.util.Map;

/**
 * Basic implementation of {@link InboundFlowController}.
 */
public class DefaultInboundFlowController implements InboundFlowController {

    private int initialWindowSize = DEFAULT_FLOW_CONTROL_WINDOW_SIZE;
    private final StreamWindow connectionWindow = new StreamWindow(CONNECTION_STREAM_ID);
    private final Map<Integer, StreamWindow> streamWindows = new HashMap<Integer, StreamWindow>();

    public DefaultInboundFlowController(Http2Connection connection) {
        if (connection == null) {
            throw new NullPointerException("connecton");
        }
        connection.addListener(new Http2Connection.Listener() {
            @Override
            public void streamCreated(int streamId) {
                streamWindows.put(streamId, new StreamWindow(streamId));
            }

            @Override
            public void streamClosed(int streamId) {
                streamWindows.remove(streamId);
            }
        });
    }

    @Override
    public void setInitialInboundWindowSize(int newWindowSize) throws Http2Exception {
        int deltaWindowSize = newWindowSize - initialWindowSize;
        initialWindowSize = newWindowSize;

        // Apply the delta to all of the windows.
        connectionWindow.addAndGet(deltaWindowSize);
        for (StreamWindow window : streamWindows.values()) {
            window.updatedInitialWindowSize(deltaWindowSize);
        }
    }

    @Override
    public int getInitialInboundWindowSize() {
        return initialWindowSize;
    }

    @Override
    public void applyInboundFlowControl(Http2DataFrame dataFrame, FrameWriter frameWriter)
            throws Http2Exception {
        applyConnectionFlowControl(dataFrame, frameWriter);
        applyStreamFlowControl(dataFrame, frameWriter);
    }

    /**
     * Apply connection-wide flow control to the incoming data frame.
     */
    private void applyConnectionFlowControl(Http2DataFrame dataFrame, FrameWriter frameWriter)
            throws Http2Exception {
        // Remove the data length from the available window size. Throw if the lower bound
        // was exceeded.
        connectionWindow.addAndGet(-dataFrame.content().readableBytes());

        // If less than the window update threshold remains, restore the window size
        // to the initial value and send a window update to the remote endpoint indicating
        // the new window size.
        if (connectionWindow.getSize() <= getWindowUpdateThreshold()) {
            connectionWindow.updateWindow(frameWriter);
        }
    }

    /**
     * Apply stream-based flow control to the incoming data frame.
     */
    private void applyStreamFlowControl(Http2DataFrame dataFrame, FrameWriter frameWriter)
            throws Http2Exception {
        // Remove the data length from the available window size. Throw if the lower bound
        // was exceeded.
        StreamWindow window = getWindowOrFail(dataFrame.getStreamId());
        window.addAndGet(-dataFrame.content().readableBytes());

        // If less than the window update threshold remains, restore the window size
        // to the initial value and send a window update to the remote endpoint indicating
        // the new window size.
        if (window.getSize() <= getWindowUpdateThreshold() && !dataFrame.isEndOfStream()) {
            window.updateWindow(frameWriter);
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
    private StreamWindow getWindowOrFail(int streamId) throws Http2Exception {
        StreamWindow window = streamWindows.get(streamId);
        if (window == null) {
            throw protocolError("Flow control window missing for stream: %d", streamId);
        }
        return window;
    }

    /**
     * Flow control window state for an individual stream.
     */
    private final class StreamWindow {
        private int windowSize;
        private int lowerBound;
        private final int streamId;

        public StreamWindow(int streamId) {
            this.streamId = streamId;
            windowSize = initialWindowSize;
        }

        public int getSize() {
            return windowSize;
        }

        /**
         * Adds the given delta to the window size and returns the new value.
         *
         * @param delta the delta in the initial window size.
         * @throws Http2Exception thrown if the new window is less than the allowed lower bound.
         */
        public int addAndGet(int delta) throws Http2Exception {
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
         * Called when sending a SETTINGS frame with a new initial window size. If the window has gotten
         * smaller (i.e. deltaWindowSize < 0), the lower bound is set to that value. This will
         * temporarily allow for receipt of data frames which were sent by the remote endpoint before
         * receiving the SETTINGS frame.
         *
         * @param delta the delta in the initial window size.
         * @throws Http2Exception thrown if integer overflow occurs on the window.
         */
        public void updatedInitialWindowSize(int delta) throws Http2Exception {
            windowSize += delta;
            if (delta > 0 && windowSize < Integer.MIN_VALUE + delta) {
                // Integer overflow.
                throw flowControlError("Flow control window overflowed for stream: %d", streamId);
            }

            if (delta < 0) {
                lowerBound = delta;
            }
        }

        /**
         * Called to perform a window update for this stream (or connection). Updates the window size
         * back to the size of the initial window and sends a window update frame to the remote
         * endpoint.
         */
        public void updateWindow(FrameWriter frameWriter) throws Http2Exception {
            // Expand the window for this stream back to the size of the initial window.
            int deltaWindowSize = initialWindowSize - getSize();
            addAndGet(deltaWindowSize);

            // Send a window update for the stream/connection.
            Http2WindowUpdateFrame updateFrame = new DefaultHttp2WindowUpdateFrame.Builder()
                    .setStreamId(streamId).setWindowSizeIncrement(deltaWindowSize).build();
            frameWriter.writeFrame(updateFrame);
        }
    }
}
