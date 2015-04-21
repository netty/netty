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
import static io.netty.handler.codec.http2.Http2CodecUtil.MAX_INITIAL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.Http2CodecUtil.MIN_INITIAL_WINDOW_SIZE;
import static io.netty.handler.codec.http2.Http2Error.FLOW_CONTROL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.handler.codec.http2.Http2Exception.streamError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Exception.CompositeStreamException;
import io.netty.handler.codec.http2.Http2Exception.StreamException;
import io.netty.handler.codec.http2.Http2Stream.FlowControlState;
import io.netty.util.internal.PlatformDependent;

/**
 * Basic implementation of {@link Http2LocalFlowController}.
 */
public class DefaultHttp2LocalFlowController implements Http2LocalFlowController {
    /**
     * The default ratio of window size to initial window size below which a {@code WINDOW_UPDATE}
     * is sent to expand the window.
     */
    public static final float DEFAULT_WINDOW_UPDATE_RATIO = 0.5f;

    private final Http2Connection connection;
    private final Http2FrameWriter frameWriter;
    private ChannelHandlerContext ctx;
    private volatile float windowUpdateRatio;
    private volatile int initialWindowSize = DEFAULT_WINDOW_SIZE;

    public DefaultHttp2LocalFlowController(Http2Connection connection, Http2FrameWriter frameWriter) {
        this(connection, frameWriter, DEFAULT_WINDOW_UPDATE_RATIO);
    }

    public DefaultHttp2LocalFlowController(Http2Connection connection,
            Http2FrameWriter frameWriter, float windowUpdateRatio) {
        this.connection = checkNotNull(connection, "connection");
        this.frameWriter = checkNotNull(frameWriter, "frameWriter");
        windowUpdateRatio(windowUpdateRatio);

        // Add a flow state for the connection.
        connection.connectionStream().localFlowState(
                new DefaultFlowState(connection.connectionStream(), initialWindowSize));

        // Register for notification of new streams.
        connection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void onStreamAdded(Http2Stream stream) {
                stream.localFlowState(new DefaultFlowState(stream, 0));
            }

            @Override
            public void onStreamActive(Http2Stream stream) {
                // Need to be sure the stream's initial window is adjusted for SETTINGS
                // frames which may have been exchanged while it was in IDLE
                state(stream).window(initialWindowSize);
            }

            @Override
            public void onStreamClosed(Http2Stream stream) {
                try {
                    // When a stream is closed, consume any remaining bytes so that they
                    // are restored to the connection window.
                    DefaultFlowState state = state(stream);
                    int unconsumedBytes = state.unconsumedBytes();
                    if (ctx != null && unconsumedBytes > 0) {
                        connectionState().consumeBytes(ctx, unconsumedBytes);
                        state.consumeBytes(ctx, unconsumedBytes);
                    }
                } catch (Http2Exception e) {
                    PlatformDependent.throwException(e);
                }
            }
        });
    }

    @Override
    public void initialWindowSize(int newWindowSize) throws Http2Exception {
        int delta = newWindowSize - initialWindowSize;
        initialWindowSize = newWindowSize;

        WindowUpdateVisitor visitor = new WindowUpdateVisitor(delta);
        connection.forEachActiveStream(visitor);
        visitor.throwIfError();
    }

    @Override
    public int initialWindowSize() {
        return initialWindowSize;
    }

    @Override
    public void incrementWindowSize(ChannelHandlerContext ctx, Http2Stream stream, int delta) throws Http2Exception {
        checkNotNull(ctx, "ctx");
        DefaultFlowState state = state(stream);
        // Just add the delta to the stream-specific initial window size so that the next time the window
        // expands it will grow to the new initial size.
        state.incrementInitialStreamWindow(delta);
        state.writeWindowUpdateIfNeeded(ctx);
    }

    @Override
    public void consumeBytes(ChannelHandlerContext ctx, Http2Stream stream, int numBytes)
            throws Http2Exception {
        // Streams automatically consume all remaining bytes when they are closed, so just ignore
        // if already closed.
        if (stream != null && !isClosed(stream)) {
            if (stream.id() == CONNECTION_STREAM_ID) {
                throw new UnsupportedOperationException("Returning bytes for the connection window is not supported");
            }
            if (numBytes <= 0) {
                throw new IllegalArgumentException("numBytes must be positive");
            }

            connectionState().consumeBytes(ctx, numBytes);
            state(stream).consumeBytes(ctx, numBytes);
        }
    }

    @Override
    public int unconsumedBytes(Http2Stream stream) {
        return state(stream).unconsumedBytes();
    }

    private static void checkValidRatio(float ratio) {
        if (Double.compare(ratio, 0.0) <= 0 || Double.compare(ratio, 1.0) >= 0) {
            throw new IllegalArgumentException("Invalid ratio: " + ratio);
        }
    }

    /**
     * The window update ratio is used to determine when a window update must be sent. If the ratio
     * of bytes processed since the last update has meet or exceeded this ratio then a window update will
     * be sent. This is the global window update ratio that will be used for new streams.
     * @param ratio the ratio to use when checking if a {@code WINDOW_UPDATE} is determined necessary for new streams.
     * @throws IllegalArgumentException If the ratio is out of bounds (0, 1).
     */
    public void windowUpdateRatio(float ratio) {
        checkValidRatio(ratio);
        windowUpdateRatio = ratio;
    }

    /**
     * The window update ratio is used to determine when a window update must be sent. If the ratio
     * of bytes processed since the last update has meet or exceeded this ratio then a window update will
     * be sent. This is the global window update ratio that will be used for new streams.
     */
    public float windowUpdateRatio() {
        return windowUpdateRatio;
    }

    /**
     * The window update ratio is used to determine when a window update must be sent. If the ratio
     * of bytes processed since the last update has meet or exceeded this ratio then a window update will
     * be sent. This window update ratio will only be applied to {@code streamId}.
     * <p>
     * Note it is the responsibly of the caller to ensure that the the
     * initial {@code SETTINGS} frame is sent before this is called. It would
     * be considered a {@link Http2Error#PROTOCOL_ERROR} if a {@code WINDOW_UPDATE}
     * was generated by this method before the initial {@code SETTINGS} frame is sent.
     * @param ctx the context to use if a {@code WINDOW_UPDATE} is determined necessary.
     * @param stream the stream for which {@code ratio} applies to.
     * @param ratio the ratio to use when checking if a {@code WINDOW_UPDATE} is determined necessary.
     * @throws Http2Exception If a protocol-error occurs while generating {@code WINDOW_UPDATE} frames
     */
    public void windowUpdateRatio(ChannelHandlerContext ctx, Http2Stream stream, float ratio) throws Http2Exception {
        checkValidRatio(ratio);
        DefaultFlowState state = state(stream);
        state.windowUpdateRatio(ratio);
        state.writeWindowUpdateIfNeeded(ctx);
    }

    /**
     * The window update ratio is used to determine when a window update must be sent. If the ratio
     * of bytes processed since the last update has meet or exceeded this ratio then a window update will
     * be sent. This window update ratio will only be applied to {@code streamId}.
     * @throws Http2Exception If no stream corresponding to {@code stream} could be found.
     */
    public float windowUpdateRatio(Http2Stream stream) throws Http2Exception {
        return state(stream).windowUpdateRatio();
    }

    @Override
    public void receiveFlowControlledFrame(ChannelHandlerContext ctx, Http2Stream stream, ByteBuf data,
            int padding, boolean endOfStream) throws Http2Exception {
        this.ctx = checkNotNull(ctx, "ctx");
        int dataLength = data.readableBytes() + padding;

        // Apply the connection-level flow control
        DefaultFlowState connectionState = connectionState();
        connectionState.receiveFlowControlledFrame(dataLength);

        if (stream != null && !isClosed(stream)) {
            // Apply the stream-level flow control
            DefaultFlowState state = state(stream);
            state.endOfStream(endOfStream);
            state.receiveFlowControlledFrame(dataLength);
        } else if (dataLength > 0) {
            // Immediately consume the bytes for the connection window.
            connectionState.consumeBytes(ctx, dataLength);
        }
    }

    private DefaultFlowState connectionState() {
        return (DefaultFlowState) connection.connectionStream().localFlowState();
    }

    private static DefaultFlowState state(Http2Stream stream) {
        return (DefaultFlowState) checkNotNull(stream, "stream").localFlowState();
    }

    private static boolean isClosed(Http2Stream stream) {
        return stream.state() == Http2Stream.State.CLOSED;
    }

    /**
     * Flow control window state for an individual stream.
     */
    private final class DefaultFlowState implements FlowControlState {
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

        /**
         * This is what is used to determine how many bytes need to be returned relative to {@link #processedWindow}.
         * Each stream has their own initial window size.
         */
        private volatile int initialStreamWindowSize;

        /**
         * This is used to determine when {@link #processedWindow} is sufficiently far away from
         * {@link #initialStreamWindowSize} such that a {@code WINDOW_UPDATE} should be sent.
         * Each stream has their own window update ratio.
         */
        private volatile float streamWindowUpdateRatio;

        private int lowerBound;
        private boolean endOfStream;

        DefaultFlowState(Http2Stream stream, int initialWindowSize) {
            this.stream = stream;
            window(initialWindowSize);
            streamWindowUpdateRatio = windowUpdateRatio;
        }

        @Override
        public int windowSize() {
            return window;
        }

        @Override
        public int initialWindowSize() {
            return initialStreamWindowSize;
        }

        void window(int initialWindowSize) {
            window = processedWindow = initialStreamWindowSize = initialWindowSize;
        }

        void endOfStream(boolean endOfStream) {
            this.endOfStream = endOfStream;
        }

        float windowUpdateRatio() {
            return streamWindowUpdateRatio;
        }

        void windowUpdateRatio(float ratio) {
            streamWindowUpdateRatio = ratio;
        }

        /**
         * Increment the initial window size for this stream.
         * @param delta The amount to increase the initial window size by.
         */
        void incrementInitialStreamWindow(int delta) {
            // Clip the delta so that the resulting initialStreamWindowSize falls within the allowed range.
            int newValue = (int) min(MAX_INITIAL_WINDOW_SIZE,
                    max(MIN_INITIAL_WINDOW_SIZE, initialStreamWindowSize + (long) delta));
            delta = newValue - initialStreamWindowSize;

            initialStreamWindowSize += delta;
        }

        /**
         * Increment the windows which are used to determine many bytes have been processed.
         * @param delta The amount to increment the window by.
         * @throws Http2Exception if integer overflow occurs on the window.
         */
        void incrementFlowControlWindows(int delta) throws Http2Exception {
            if (delta > 0 && window > MAX_INITIAL_WINDOW_SIZE - delta) {
                throw streamError(stream.id(), FLOW_CONTROL_ERROR,
                        "Flow control window overflowed for stream: %d", stream.id());
            }

            window += delta;
            processedWindow += delta;
            lowerBound = delta < 0 ? delta : 0;
        }

        /**
         * A flow control event has occurred and we should decrement the amount of available bytes for this stream.
         * @param dataLength The amount of data to for which this stream is no longer eligible to use for flow control.
         * @throws Http2Exception If too much data is used relative to how much is available.
         */
        void receiveFlowControlledFrame(int dataLength) throws Http2Exception {
            assert dataLength >= 0;

            // Apply the delta. Even if we throw an exception we want to have taken this delta into account.
            window -= dataLength;

            // Window size can become negative if we sent a SETTINGS frame that reduces the
            // size of the transfer window after the peer has written data frames.
            // The value is bounded by the length that SETTINGS frame decrease the window.
            // This difference is stored for the connection when writing the SETTINGS frame
            // and is cleared once we send a WINDOW_UPDATE frame.
            if (window < lowerBound) {
                throw streamError(stream.id(), FLOW_CONTROL_ERROR,
                        "Flow control window exceeded for stream: %d", stream.id());
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

        void consumeBytes(ChannelHandlerContext ctx, int numBytes) throws Http2Exception {
            // Return the bytes processed and update the window.
            returnProcessedBytes(numBytes);
            writeWindowUpdateIfNeeded(ctx);
        }

        int unconsumedBytes() {
            return processedWindow - window;
        }

        /**
         * Updates the flow control window for this stream if it is appropriate.
         */
        void writeWindowUpdateIfNeeded(ChannelHandlerContext ctx) throws Http2Exception {
            if (endOfStream || initialStreamWindowSize <= 0 || isClosed(stream)) {
                return;
            }

            int threshold = (int) (initialStreamWindowSize * streamWindowUpdateRatio);
            if (processedWindow <= threshold) {
                writeWindowUpdate(ctx);
            }
        }

        /**
         * Called to perform a window update for this stream (or connection). Updates the window size back
         * to the size of the initial window and sends a window update frame to the remote endpoint.
         */
        void writeWindowUpdate(ChannelHandlerContext ctx) throws Http2Exception {
            // Expand the window for this stream back to the size of the initial window.
            int deltaWindowSize = initialStreamWindowSize - processedWindow;
            try {
                incrementFlowControlWindows(deltaWindowSize);
            } catch (Throwable t) {
                throw connectionError(INTERNAL_ERROR, t,
                        "Attempting to return too many bytes for stream %d", stream.id());
            }

            // Send a window update for the stream/connection.
            frameWriter.writeWindowUpdate(ctx, stream.id(), deltaWindowSize, ctx.newPromise());
            ctx.flush();
        }
    }

    /**
     * Provides a means to iterate over all active streams and increment the flow control windows.
     */
    private static final class WindowUpdateVisitor implements Http2StreamVisitor {
        private CompositeStreamException compositeException;
        private final int delta;

        public WindowUpdateVisitor(int delta) {
            this.delta = delta;
        }

        @Override
        public boolean visit(Http2Stream stream) throws Http2Exception {
            try {
                // Increment flow control window first so state will be consistent if overflow is detected.
                DefaultFlowState state = state(stream);
                state.incrementFlowControlWindows(delta);
                state.incrementInitialStreamWindow(delta);
            } catch (StreamException e) {
                if (compositeException == null) {
                    compositeException = new CompositeStreamException(e.error(), 4);
                }
                compositeException.add(e);
            }
            return true;
        }

        public void throwIfError() throws CompositeStreamException {
            if (compositeException != null) {
                throw compositeException;
            }
        }
    }
}
