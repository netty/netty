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
import static io.netty.handler.codec.http2.Http2Exception.streamError;
import static io.netty.handler.codec.http2.Http2Stream.State.IDLE;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Stream.State;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Basic implementation of {@link Http2RemoteFlowController}.
 * <p>
 * This class is <strong>NOT</strong> thread safe. The assumption is all methods must be invoked from a single thread.
 * Typically this thread is the event loop thread for the {@link ChannelHandlerContext} managed by this class.
 */
public class DefaultHttp2RemoteFlowController implements Http2RemoteFlowController {
    private static final int MIN_WRITABLE_CHUNK = 32 * 1024;

    private final StreamByteDistributor.Writer writer = new StreamByteDistributor.Writer() {
        @Override
        public void write(Http2Stream stream, int numBytes) {
            int written = state(stream).writeAllocatedBytes(numBytes);
            if (written != -1 && listener != null) {
                listener.streamWritten(stream, written);
            }
        }
    };
    private final Http2Connection connection;
    private final Http2Connection.PropertyKey stateKey;
    private final StreamByteDistributor streamByteDistributor;
    private final AbstractState connectionState;
    private int initialWindowSize = DEFAULT_WINDOW_SIZE;
    private ChannelHandlerContext ctx;
    private Listener listener;

    public DefaultHttp2RemoteFlowController(Http2Connection connection) {
        this(connection, new PriorityStreamByteDistributor(connection));
    }

    public DefaultHttp2RemoteFlowController(Http2Connection connection,
                                            StreamByteDistributor streamByteDistributor) {
        this.connection = checkNotNull(connection, "connection");
        this.streamByteDistributor = checkNotNull(streamByteDistributor, "streamWriteDistributor");
        streamByteDistributor.writer(writer);

        // Add a flow state for the connection.
        stateKey = connection.newKey();
        connectionState = new DefaultState(connection.connectionStream(), initialWindowSize);
        connection.connectionStream().setProperty(stateKey, connectionState);

        // Register for notification of new streams.
        connection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void onStreamAdded(Http2Stream stream) {
                // If the stream state is not open then the stream is not yet eligible for flow controlled frames and
                // only requires the ReducedFlowState. Otherwise the full amount of memory is required.
                stream.setProperty(stateKey, stream.state() == IDLE ?
                        new ReducedState(stream) :
                        new DefaultState(stream, 0));
            }

            @Override
            public void onStreamActive(Http2Stream stream) {
                // If the object was previously created, but later activated then we have to ensure
                // the full state is allocated and the proper initialWindowSize is used.
                AbstractState state = state(stream);
                if (state.getClass() == DefaultState.class) {
                    state.window(initialWindowSize);
                } else {
                    stream.setProperty(stateKey, new DefaultState(state, initialWindowSize));
                }
            }

            @Override
            public void onStreamClosed(Http2Stream stream) {
                // Any pending frames can never be written, cancel and
                // write errors for any pending frames.
                AbstractState state = state(stream);
                state.cancel();

                // If the stream is now eligible for removal, but will persist in the priority tree then we can
                // decrease the amount of memory required for this stream because no flow controlled frames can
                // be exchanged on this stream
                if (stream.prioritizableForTree() != 0) {
                    stream.setProperty(stateKey, new ReducedState(state));
                }
            }

            @Override
            public void onStreamHalfClosed(Http2Stream stream) {
                if (State.HALF_CLOSED_LOCAL.equals(stream.state())) {
                    /**
                     * When this method is called there should not be any
                     * pending frames left if the API is used correctly. However,
                     * it is possible that a erroneous application can sneak
                     * in a frame even after having already written a frame with the
                     * END_STREAM flag set, as the stream state might not transition
                     * immediately to HALF_CLOSED_LOCAL / CLOSED due to flow control
                     * delaying the write.
                     *
                     * This is to cancel any such illegal writes.
                     */
                    state(stream).cancel();
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     * <p>
     * Any queued {@link FlowControlled} objects will be sent.
     */
    @Override
    public void channelHandlerContext(ChannelHandlerContext ctx) throws Http2Exception {
        this.ctx = ctx;

        // Don't worry about cleaning up queued frames here if ctx is null. It is expected that all streams will be
        // closed and the queue cleanup will occur when the stream state transitions occur.

        // If any frames have been queued up, we should send them now that we have a channel context.
        if (ctx != null && ctx.channel().isWritable()) {
            writePendingBytes();
        }
    }

    @Override
    public ChannelHandlerContext channelHandlerContext() {
        return ctx;
    }

    @Override
    public void initialWindowSize(int newWindowSize) throws Http2Exception {
        assert ctx == null || ctx.executor().inEventLoop();
        if (newWindowSize < 0) {
            throw new IllegalArgumentException("Invalid initial window size: " + newWindowSize);
        }

        final int delta = newWindowSize - initialWindowSize;
        initialWindowSize = newWindowSize;
        connection.forEachActiveStream(new Http2StreamVisitor() {
            @Override
            public boolean visit(Http2Stream stream) throws Http2Exception {
                // Verify that the maximum value is not exceeded by this change.
                state(stream).incrementStreamWindow(delta);
                return true;
            }
        });

        if (delta > 0) {
            // The window size increased, send any pending frames for all streams.
            writePendingBytes();
        }
    }

    @Override
    public int initialWindowSize() {
        return initialWindowSize;
    }

    @Override
    public int windowSize(Http2Stream stream) {
        return state(stream).windowSize();
    }

    @Override
    public int initialWindowSize(Http2Stream stream) {
        return state(stream).initialWindowSize();
    }

    @Override
    public void incrementWindowSize(Http2Stream stream, int delta) throws Http2Exception {
        assert ctx == null || ctx.executor().inEventLoop();
        if (stream.id() == CONNECTION_STREAM_ID) {
            // Update the connection window
            connectionState.incrementStreamWindow(delta);
        } else {
            // Update the stream window
            AbstractState state = state(stream);
            state.incrementStreamWindow(delta);
        }
    }

    @Override
    public void listener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public Listener listener() {
        return this.listener;
    }

    @Override
    public void addFlowControlled(Http2Stream stream, FlowControlled frame) {
        // The context can be null assuming the frame will be queued and send later when the context is set.
        assert ctx == null || ctx.executor().inEventLoop();
        checkNotNull(frame, "frame");
        final AbstractState state;
        try {
            state = state(stream);
            state.enqueueFrame(frame);
        } catch (Throwable t) {
            frame.error(ctx, t);
        }
    }

    private AbstractState state(Http2Stream stream) {
        return (AbstractState) checkNotNull(stream, "stream").getProperty(stateKey);
    }

    /**
     * Returns the flow control window for the entire connection.
     */
    private int connectionWindowSize() {
        return connectionState.windowSize();
    }

    private int minUsableChannelBytes() {
        // The current allocation algorithm values "fairness" and doesn't give any consideration to "goodput". It
        // is possible that 1 byte will be allocated to many streams. In an effort to try to make "goodput"
        // reasonable with the current allocation algorithm we have this "cheap" check up front to ensure there is
        // an "adequate" amount of connection window before allocation is attempted. This is not foolproof as if the
        // number of streams is >= this minimal number then we may still have the issue, but the idea is to narrow the
        // circumstances in which this can happen without rewriting the allocation algorithm.
        return Math.max(ctx.channel().config().getWriteBufferLowWaterMark(), MIN_WRITABLE_CHUNK);
    }

    private int maxUsableChannelBytes() {
        if (ctx == null) {
            return 0;
        }

        // If the channel isWritable, allow at least minUseableChannelBytes.
        int channelWritableBytes = (int) Math.min(Integer.MAX_VALUE, ctx.channel().bytesBeforeUnwritable());
        int useableBytes = channelWritableBytes > 0 ? max(channelWritableBytes, minUsableChannelBytes()) : 0;

        // Clip the usable bytes by the connection window.
        return min(connectionState.windowSize(), useableBytes);
    }

    /**
     * Package private for testing purposes only!
     *
     * @return The amount of bytes that can be supported by underlying {@link
     * io.netty.channel.Channel} without queuing "too-much".
     */
    private int writableBytes() {
        return Math.min(connectionWindowSize(), maxUsableChannelBytes());
    }

    /**
     * Writes as many pending bytes as possible, according to stream priority.
     */
    @Override
    public void writePendingBytes() throws Http2Exception {
        int bytesToWrite = writableBytes();
        boolean haveUnwrittenBytes;

        // Using a do-while loop so that we always write at least once, regardless if we have
        // bytesToWrite or not. This ensures that zero-length frames will always be written.
        do {
            // Distribute the connection window across the streams and write the data.
            haveUnwrittenBytes = streamByteDistributor.distribute(bytesToWrite);
        } while (haveUnwrittenBytes && (bytesToWrite = writableBytes()) > 0 && ctx.channel().isWritable());
    }

    /**
     * The remote flow control state for a single stream.
     */
    private final class DefaultState extends AbstractState {
        private final Deque<FlowControlled> pendingWriteQueue;
        private int window;
        private int pendingBytes;
        // Set to true while a frame is being written, false otherwise.
        private boolean writing;
        // Set to true if cancel() was called.
        private boolean cancelled;

        DefaultState(Http2Stream stream, int initialWindowSize) {
            super(stream);
            window(initialWindowSize);
            pendingWriteQueue = new ArrayDeque<FlowControlled>(2);
        }

        DefaultState(AbstractState existingState, int initialWindowSize) {
            super(existingState);
            window(initialWindowSize);
            pendingWriteQueue = new ArrayDeque<FlowControlled>(2);
        }

        @Override
        int windowSize() {
            return window;
        }

        @Override
        int initialWindowSize() {
            return initialWindowSize;
        }

        @Override
        void window(int initialWindowSize) {
            window = initialWindowSize;
        }

        @Override
        int writeAllocatedBytes(int allocated) {
            try {
                // Perform the write.
                return writeBytes(allocated);
            } finally {
                streamByteDistributor.updateStreamableBytes(this);
            }
        }

        @Override
        int incrementStreamWindow(int delta) throws Http2Exception {
            if (delta > 0 && Integer.MAX_VALUE - delta < window) {
                throw streamError(stream.id(), FLOW_CONTROL_ERROR,
                        "Window size overflow for stream: %d", stream.id());
            }
            window += delta;

            streamByteDistributor.updateStreamableBytes(this);
            return window;
        }

        int writableWindow() {
            return min(window, connectionWindowSize());
        }

        @Override
        public int streamableBytes() {
            return max(0, min(pendingBytes, window));
        }

        @Override
        void enqueueFrame(FlowControlled frame) {
            incrementPendingBytes(frame.size());
            FlowControlled last = pendingWriteQueue.peekLast();
            if (last == null || !last.merge(ctx, frame)) {
                pendingWriteQueue.offer(frame);
            }
        }

        @Override
        public boolean hasFrame() {
            return !pendingWriteQueue.isEmpty();
        }

        /**
         * Returns the the head of the pending queue, or {@code null} if empty.
         */
        private FlowControlled peek() {
            return pendingWriteQueue.peek();
        }

        @Override
        void cancel() {
            cancel(null);
        }

        /**
         * Clears the pending queue and writes errors for each remaining frame.
         * @param cause the {@link Throwable} that caused this method to be invoked.
         */
        private void cancel(Throwable cause) {
            cancelled = true;
            // Ensure that the queue can't be modified while we are writing.
            if (writing) {
                return;
            }
            for (;;) {
                FlowControlled frame = pendingWriteQueue.poll();
                if (frame == null) {
                    break;
                }
                writeError(frame, streamError(stream.id(), INTERNAL_ERROR, cause,
                                              "Stream closed before write could take place"));
            }
            streamByteDistributor.updateStreamableBytes(this);
        }

        int writeBytes(int bytes) {
            if (!hasFrame()) {
                return -1;
            }
            // Check if the first frame is a "writable" frame to get the "-1" return status out of the way
            FlowControlled frame = peek();
            int maxBytes = min(bytes, writableWindow());
            if (maxBytes <= 0 && frame.size() != 0) {
                // The frame had data and all of it was written.
                return -1;
            }
            int originalBytes = bytes;
            bytes -= write(frame, maxBytes);

            // Write the remainder of frames that we are allowed to
            while (hasFrame()) {
                frame = peek();
                maxBytes = min(bytes, writableWindow());
                if (maxBytes <= 0 && frame.size() != 0) {
                    // The frame had data and all of it was written.
                    break;
                }
                bytes -= write(frame, maxBytes);
            }
            return originalBytes - bytes;
        }

        /**
         * Writes the frame and decrements the stream and connection window sizes. If the frame is in the pending
         * queue, the written bytes are removed from this branch of the priority tree.
         */
        private int write(FlowControlled frame, int allowedBytes) {
            int before = frame.size();
            int writtenBytes;
            // In case an exception is thrown we want to remember it and pass it to cancel(Throwable).
            Throwable cause = null;
            try {
                assert !writing;

                // Write the portion of the frame.
                writing = true;
                frame.write(ctx, max(0, allowedBytes));
                if (!cancelled && frame.size() == 0) {
                    // This frame has been fully written, remove this frame and notify it. Since we remove this frame
                    // first, we're guaranteed that its error method will not be called when we call cancel.
                    pendingWriteQueue.remove();
                    frame.writeComplete();
                }
            } catch (Throwable t) {
                // Mark the state as cancelled, we'll clear the pending queue via cancel() below.
                cancelled = true;
                cause = t;
            } finally {
                writing = false;
                // Make sure we always decrement the flow control windows
                // by the bytes written.
                writtenBytes = before - frame.size();
                decrementFlowControlWindow(writtenBytes);
                decrementPendingBytes(writtenBytes);
                // If a cancellation occurred while writing, call cancel again to
                // clear and error all of the pending writes.
                if (cancelled) {
                    cancel(cause);
                }
            }
            return writtenBytes;
        }

        /**
         * Increments the number of pending bytes for this node and updates the {@link StreamByteDistributor}.
         */
        private void incrementPendingBytes(int numBytes) {
            pendingBytes += numBytes;
            streamByteDistributor.updateStreamableBytes(this);
        }

        /**
         * If this frame is in the pending queue, decrements the number of pending bytes for the stream.
         */
        private void decrementPendingBytes(int bytes) {
            incrementPendingBytes(-bytes);
        }

        /**
         * Decrement the per stream and connection flow control window by {@code bytes}.
         */
        private void decrementFlowControlWindow(int bytes) {
            try {
                int negativeBytes = -bytes;
                connectionState.incrementStreamWindow(negativeBytes);
                incrementStreamWindow(negativeBytes);
            } catch (Http2Exception e) {
                // Should never get here since we're decrementing.
                throw new IllegalStateException("Invalid window state when writing frame: " + e.getMessage(), e);
            }
        }

        /**
         * Discards this {@link FlowControlled}, writing an error. If this frame is in the pending queue,
         * the unwritten bytes are removed from this branch of the priority tree.
         */
        private void writeError(FlowControlled frame, Http2Exception cause) {
            assert ctx != null;
            decrementPendingBytes(frame.size());
            frame.error(ctx, cause);
        }
    }

    /**
     * The remote flow control state for a single stream that is not in a state where flow controlled frames cannot
     * be exchanged.
     */
    private final class ReducedState extends AbstractState {
        ReducedState(Http2Stream stream) {
            super(stream);
        }

        ReducedState(AbstractState existingState) {
            super(existingState);
        }

        @Override
        int windowSize() {
            return 0;
        }

        @Override
        int initialWindowSize() {
            return 0;
        }

        @Override
        public int streamableBytes() {
            return 0;
        }

        @Override
        int writeAllocatedBytes(int allocated) {
            throw new UnsupportedOperationException();
        }

        @Override
        void cancel() {
        }

        @Override
        void window(int initialWindowSize) {
            throw new UnsupportedOperationException();
        }

        @Override
        int incrementStreamWindow(int delta) throws Http2Exception {
            // This operation needs to be supported during the initial settings exchange when
            // the peer has not yet acknowledged this peer being activated.
            return 0;
        }

        @Override
        void enqueueFrame(FlowControlled frame) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasFrame() {
            return false;
        }
    }

    /**
     * An abstraction which provides specific extensions used by remote flow control.
     */
    private abstract class AbstractState implements StreamByteDistributor.StreamState {
        protected final Http2Stream stream;

        AbstractState(Http2Stream stream) {
            this.stream = stream;
        }

        AbstractState(AbstractState existingState) {
            this.stream = existingState.stream();
        }

        /**
         * The stream this state is associated with.
         */
        @Override
        public final Http2Stream stream() {
            return stream;
        }

        abstract int windowSize();

        abstract int initialWindowSize();

        /**
         * Write the allocated bytes for this stream.
         *
         * @return the number of bytes written for a stream or {@code -1} if no write occurred.
         */
        abstract int writeAllocatedBytes(int allocated);

        /**
         * Any operations that may be pending are cleared and the status of these operations is failed.
         */
        abstract void cancel();

        /**
         * Reset the window size for this stream.
         */
        abstract void window(int initialWindowSize);

        /**
         * Increments the flow control window for this stream by the given delta and returns the new value.
         */
        abstract int incrementStreamWindow(int delta) throws Http2Exception;

        /**
         * Adds the {@code frame} to the pending queue and increments the pending byte count.
         */
        abstract void enqueueFrame(FlowControlled frame);
    }
}
