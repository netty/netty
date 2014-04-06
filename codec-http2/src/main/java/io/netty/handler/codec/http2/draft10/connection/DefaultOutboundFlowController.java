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

import static io.netty.handler.codec.http2.draft10.Http2Error.FLOW_CONTROL_ERROR;
import static io.netty.handler.codec.http2.draft10.Http2Error.STREAM_CLOSED;
import static io.netty.handler.codec.http2.draft10.Http2Exception.format;
import static io.netty.handler.codec.http2.draft10.Http2Exception.protocolError;
import static io.netty.handler.codec.http2.draft10.connection.Http2ConnectionUtil.DEFAULT_FLOW_CONTROL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.draft10.frame.Http2FrameCodecUtil.CONNECTION_STREAM_ID;
import io.netty.handler.codec.http2.draft10.Http2Exception;
import io.netty.handler.codec.http2.draft10.Http2StreamException;
import io.netty.handler.codec.http2.draft10.frame.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.draft10.frame.Http2DataFrame;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/**
 * Basic implementation of {@link OutboundFlowController}.
 */
public class DefaultOutboundFlowController implements OutboundFlowController {

    private final Http2Connection connection;
    private final Map<Integer, StreamState> streamStates = new HashMap<Integer, StreamState>();
    private int initialWindowSize = DEFAULT_FLOW_CONTROL_WINDOW_SIZE;
    private int connectionWindowSize = DEFAULT_FLOW_CONTROL_WINDOW_SIZE;

    public DefaultOutboundFlowController(Http2Connection connection) {
        if (connection == null) {
            throw new NullPointerException("connection");
        }
        this.connection = connection;
        connection.addListener(new Http2Connection.Listener() {
            @Override
            public void streamCreated(int streamId) {
                streamStates.put(streamId, new StreamState(streamId));
            }

            @Override
            public void streamClosed(int streamId) {
                StreamState state = streamStates.remove(streamId);
                if (state != null) {
                    state.clearPendingWrites();
                }
            }
        });
    }

    @Override
    public void setInitialOutboundWindowSize(int newWindowSize) throws Http2Exception {
        int delta = newWindowSize - initialWindowSize;
        initialWindowSize = newWindowSize;
        addAndGetConnectionWindowSize(delta);
        for (StreamState window : streamStates.values()) {
            // Verify that the maximum value is not exceeded by this change.
            window.addAndGetWindow(delta);
        }

        if (delta > 0) {
            // The window size increased, send any pending frames for all streams.
            writePendingFrames();
        }
    }

    @Override
    public void updateOutboundWindowSize(int streamId, int delta) throws Http2Exception {
        StreamState streamWindow;
        if (streamId == CONNECTION_STREAM_ID) {
            // Update the connection window and write any pending frames for all streams.
            addAndGetConnectionWindowSize(delta);
            writePendingFrames();
        } else {
            // Update the stream window and write any pending frames for the stream.
            streamWindow = getStateOrFail(streamId);
            streamWindow.addAndGetWindow(delta);
            streamWindow.writePendingFrames(Integer.MAX_VALUE);
        }
    }

    @Override
    public void sendFlowControlled(Http2DataFrame frame, FrameWriter frameWriter)
            throws Http2Exception {

        StreamState streamState = getStateOrFail(frame.getStreamId());

        int dataLength = frame.content().readableBytes();
        if (streamState.writableWindow() >= dataLength) {
            // Window size is large enough to send entire data frame
            writeFrame(frame, streamState, frameWriter);
            return;
        }

        // Enqueue the frame to be written when the window size permits.
        streamState.addPendingWrite(new PendingWrite(frame, frameWriter));

        if (streamState.writableWindow() <= 0) {
            // Stream is stalled, don't send anything now.
            return;
        }

        // Create and send a partial frame up to the window size.
        Http2DataFrame partialFrame = readPartialFrame(frame, streamState.writableWindow());
        writeFrame(partialFrame, streamState, frameWriter);
    }

    /**
     * Attempts to get the {@link StreamState} for the given stream. If not available, raises a
     * {@code PROTOCOL_ERROR}.
     */
    private StreamState getStateOrFail(int streamId) throws Http2Exception {
        StreamState streamState = streamStates.get(streamId);
        if (streamState == null) {
            throw protocolError("Missing flow control window for stream: %d", streamId);
        }
        return streamState;
    }

    /**
     * Writes the frame and decrements the stream and connection window sizes.
     */
    private void writeFrame(Http2DataFrame frame, StreamState state, FrameWriter frameWriter)
            throws Http2Exception {
        int dataLength = frame.content().readableBytes();
        connectionWindowSize -= dataLength;
        state.addAndGetWindow(-dataLength);
        frameWriter.writeFrame(frame);
    }

    /**
     * Creates a view of the given frame starting at the current read index with the given number of
     * bytes. The reader index on the input frame is then advanced by the number of bytes. The
     * returned frame will not have end-of-stream set.
     */
    private static Http2DataFrame readPartialFrame(Http2DataFrame frame, int numBytes) {
        return new DefaultHttp2DataFrame.Builder().setStreamId(frame.getStreamId())
                .setContent(frame.content().readSlice(numBytes).retain()).build();
    }

    /**
     * Indicates whether applying the delta to the given value will cause an integer overflow.
     */
    private static boolean isIntegerOverflow(int previousValue, int delta) {
        return delta > 0 && (Integer.MAX_VALUE - delta) < previousValue;
    }

    /**
     * Increments the connectionWindowSize and returns the new value.
     */
    private int addAndGetConnectionWindowSize(int delta) throws Http2Exception {
        if (isIntegerOverflow(connectionWindowSize, delta)) {
            throw format(FLOW_CONTROL_ERROR, "Window update exceeds maximum for connection");
        }
        return connectionWindowSize += delta;
    }

    /**
     * Writes any pending frames for the entire connection.
     */
    private void writePendingFrames() throws Http2Exception {
        // The request for for the entire connection, write any pending frames across
        // all active streams. Active streams are already sorted by their priority.
        for (Http2Stream stream : connection.getActiveStreams()) {
            StreamState state = getStateOrFail(stream.getId());
            state.writePendingFrames(1);
        }
    }

    /**
     * The outbound flow control state for a single stream.
     */
    private class StreamState {
        private final int streamId;
        private final Queue<PendingWrite> pendingWriteQueue = new ArrayDeque<PendingWrite>();
        private int windowSize = initialWindowSize;

        public StreamState(int streamId) {
            this.streamId = streamId;
        }

        public int addAndGetWindow(int delta) throws Http2Exception {
            if (isIntegerOverflow(windowSize, delta)) {
                throw new Http2StreamException(streamId, FLOW_CONTROL_ERROR,
                        "Window size overflow for stream");
            }
            windowSize += delta;
            return windowSize;
        }

        public int writableWindow() {
            return Math.min(windowSize, connectionWindowSize);
        }

        public void addPendingWrite(PendingWrite pendingWrite) {
            pendingWriteQueue.offer(pendingWrite);
        }

        public boolean hasPendingWrite() {
            return !pendingWriteQueue.isEmpty();
        }

        public PendingWrite peekPendingWrite() {
            if (windowSize > 0) {
                return pendingWriteQueue.peek();
            }
            return null;
        }

        public void removePendingWrite() {
            pendingWriteQueue.poll();
        }

        public void clearPendingWrites() {
            while (true) {
                PendingWrite pendingWrite = pendingWriteQueue.poll();
                if (pendingWrite == null) {
                    break;
                }
                pendingWrite.writeError(
                        format(STREAM_CLOSED, "Stream closed before write could take place"));
            }
        }

        /**
         * Sends all pending writes for this stream so long as there is space the the stream and
         * connection windows.
         *
         * @param maxFrames the maximum number of frames to send.
         */
        public void writePendingFrames(int maxFrames) throws Http2Exception {
            while (maxFrames > 0 && writableWindow() > 0 && hasPendingWrite()) {
                maxFrames--;
                PendingWrite pendingWrite = peekPendingWrite();

                if (writableWindow() >= pendingWrite.size()) {
                    // Window size is large enough to send entire data frame
                    removePendingWrite();
                    writeFrame(pendingWrite.frame(), this, pendingWrite.writer());
                } else {
                    // We can send a partial frame
                    Http2DataFrame partialDataFrame =
                            readPartialFrame(pendingWrite.frame(), writableWindow());
                    writeFrame(partialDataFrame, this, pendingWrite.writer());
                }
            }
        }
    }

    /**
     * Pending write for a single data frame.
     */
    private static class PendingWrite {
        private final Http2DataFrame frame;
        private final FrameWriter writer;

        public PendingWrite(Http2DataFrame frame, FrameWriter writer) {
            this.frame = frame;
            this.writer = writer;
        }

        public Http2DataFrame frame() {
            return frame;
        }

        public FrameWriter writer() {
            return writer;
        }

        public int size() {
            return frame.content().readableBytes();
        }

        public void writeError(Http2Exception cause) {
            frame.release();
            writer.setFailure(cause);
        }
    }
}
