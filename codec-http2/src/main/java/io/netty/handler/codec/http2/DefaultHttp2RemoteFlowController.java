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
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Queue;

/**
 * Basic implementation of {@link Http2RemoteFlowController}.
 */
public class DefaultHttp2RemoteFlowController implements Http2RemoteFlowController {

    /**
     * A {@link Comparator} that sorts streams in ascending order the amount of streamable data.
     */
    private static final Comparator<Http2Stream> WEIGHT_ORDER = new Comparator<Http2Stream>() {
        @Override
        public int compare(Http2Stream o1, Http2Stream o2) {
            return o2.weight() - o1.weight();
        }
    };

    private final Http2Connection connection;
    private int initialWindowSize = DEFAULT_WINDOW_SIZE;
    private ChannelHandlerContext ctx;
    private boolean needFlush;

    public DefaultHttp2RemoteFlowController(Http2Connection connection) {
        this.connection = checkNotNull(connection, "connection");

        // Add a flow state for the connection.
        connection.connectionStream().setProperty(FlowState.class,
                new FlowState(connection.connectionStream(), initialWindowSize));

        // Register for notification of new streams.
        connection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void streamAdded(Http2Stream stream) {
                // Just add a new flow state to the stream.
                stream.setProperty(FlowState.class, new FlowState(stream, 0));
            }

            @Override
            public void streamActive(Http2Stream stream) {
                // Need to be sure the stream's initial window is adjusted for SETTINGS
                // frames which may have been exchanged while it was in IDLE
                state(stream).window(initialWindowSize);
            }

            @Override
            public void streamHalfClosed(Http2Stream stream) {
                if (!stream.localSideOpen()) {
                    // Any pending frames can never be written, clear and
                    // write errors for any pending frames.
                    state(stream).clear();
                }
            }

            @Override
            public void streamInactive(Http2Stream stream) {
                // Any pending frames can never be written, clear and
                // write errors for any pending frames.
                state(stream).clear();
            }

            @Override
            public void priorityTreeParentChanged(Http2Stream stream, Http2Stream oldParent) {
                Http2Stream parent = stream.parent();
                if (parent != null) {
                    int delta = state(stream).streamableBytesForTree();
                    if (delta != 0) {
                        state(parent).incrementStreamableBytesForTree(delta);
                    }
                }
            }

            @Override
            public void priorityTreeParentChanging(Http2Stream stream, Http2Stream newParent) {
                Http2Stream parent = stream.parent();
                if (parent != null) {
                    int delta = -state(stream).streamableBytesForTree();
                    if (delta != 0) {
                        state(parent).incrementStreamableBytesForTree(delta);
                    }
                }
            }
        });
    }

    @Override
    public void initialWindowSize(int newWindowSize) throws Http2Exception {
        if (newWindowSize < 0) {
            throw new IllegalArgumentException("Invalid initial window size: " + newWindowSize);
        }

        int delta = newWindowSize - initialWindowSize;
        initialWindowSize = newWindowSize;
        for (Http2Stream stream : connection.activeStreams()) {
            // Verify that the maximum value is not exceeded by this change.
            state(stream).incrementStreamWindow(delta);
        }

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
        return state(stream).window();
    }

    @Override
    public void incrementWindowSize(ChannelHandlerContext ctx, Http2Stream stream, int delta) throws Http2Exception {
        if (stream.id() == CONNECTION_STREAM_ID) {
            // Update the connection window and write any pending frames for all streams.
            connectionState().incrementStreamWindow(delta);
            writePendingBytes();
        } else {
            // Update the stream window and write any pending frames for the stream.
            FlowState state = state(stream);
            state.incrementStreamWindow(delta);
            state.writeBytes(state.writableWindow());
            flush();
        }
    }

    @Override
    public void sendFlowControlled(ChannelHandlerContext ctx, Http2Stream stream,
                                   FlowControlled payload) {
        checkNotNull(ctx, "ctx");
        checkNotNull(payload, "payload");
        if (this.ctx != null && this.ctx != ctx) {
            throw new IllegalArgumentException("Writing data from multiple ChannelHandlerContexts is not supported");
        }
        // Save the context. We'll use this later when we write pending bytes.
        this.ctx = ctx;
        try {
            FlowState state = state(stream);
            state.newFrame(payload);
            state.writeBytes(state.writableWindow());
            flush();
        } catch (Throwable e) {
            payload.error(e);
        }
    }

    /**
     * For testing purposes only. Exposes the number of streamable bytes for the tree rooted at
     * the given stream.
     */
    int streamableBytesForTree(Http2Stream stream) {
        return state(stream).streamableBytesForTree();
    }

    private static FlowState state(Http2Stream stream) {
        checkNotNull(stream, "stream");
        return stream.getProperty(FlowState.class);
    }

    private FlowState connectionState() {
        return state(connection.connectionStream());
    }

    /**
     * Returns the flow control window for the entire connection.
     */
    private int connectionWindow() {
        return connectionState().window();
    }

    /**
     * Flushes the {@link ChannelHandlerContext} if we've received any data frames.
     */
    private void flush() {
        if (needFlush) {
            ctx.flush();
            needFlush = false;
        }
    }

    /**
     * Writes as many pending bytes as possible, according to stream priority.
     */
    private void writePendingBytes() throws Http2Exception {
        Http2Stream connectionStream = connection.connectionStream();
        int connectionWindow = state(connectionStream).window();

        if (connectionWindow > 0) {
            writeChildren(connectionStream, connectionWindow);
            for (Http2Stream stream : connection.activeStreams()) {
                writeChildNode(state(stream));
            }
            flush();
        }
    }

    /**
     * Write the children of {@code parent} in the priority tree. This will allocate bytes by stream weight.
     * @param parent The parent of the nodes which will be written.
     * @param connectionWindow The connection window this is available for use at this point in the tree.
     * @return An object summarizing the write and allocation results.
     */
    private int writeChildren(Http2Stream parent, int connectionWindow) {
        FlowState state = state(parent);
        if (state.streamableBytesForTree() <= 0) {
            return 0;
        }
        int bytesAllocated = 0;

        // If the number of streamable bytes for this tree will fit in the connection window
        // then there is no need to prioritize the bytes...everyone sends what they have
        if (state.streamableBytesForTree() <= connectionWindow) {
            for (Http2Stream child : parent.children()) {
                state = state(child);
                int bytesForChild = state.streamableBytes();

                if (bytesForChild > 0 || state.hasFrame()) {
                    state.allocate(bytesForChild);
                    writeChildNode(state);
                    bytesAllocated += bytesForChild;
                    connectionWindow -= bytesForChild;
                }
                int childBytesAllocated = writeChildren(child, connectionWindow);
                bytesAllocated += childBytesAllocated;
                connectionWindow -= childBytesAllocated;
            }
            return bytesAllocated;
        }

        // This is the priority algorithm which will divide the available bytes based
        // upon stream weight relative to its peers
        Http2Stream[] children = parent.children().toArray(new Http2Stream[parent.numChildren()]);
        Arrays.sort(children, WEIGHT_ORDER);
        int totalWeight = parent.totalChildWeights();
        for (int tail = children.length; tail > 0;) {
            int head = 0;
            int nextTail = 0;
            int nextTotalWeight = 0;
            int nextConnectionWindow = connectionWindow;
            for (; head < tail && nextConnectionWindow > 0; ++head) {
                Http2Stream child = children[head];
                state = state(child);
                int weight = child.weight();
                double weightRatio = weight / (double) totalWeight;

                int bytesForTree = Math.min(nextConnectionWindow, (int) Math.ceil(connectionWindow * weightRatio));
                int bytesForChild = Math.min(state.streamableBytes(), bytesForTree);

                if (bytesForChild > 0 || state.hasFrame()) {
                    state.allocate(bytesForChild);
                    bytesAllocated += bytesForChild;
                    nextConnectionWindow -= bytesForChild;
                    bytesForTree -= bytesForChild;
                    // If this subtree still wants to send then re-insert into children list and re-consider for next
                    // iteration. This is needed because we don't yet know if all the peers will be able to use
                    // all of their "fair share" of the connection window, and if they don't use it then we should
                    // divide their unused shared up for the peers who still want to send.
                    if (state.streamableBytesForTree() - bytesForChild > 0) {
                        children[nextTail++] = child;
                        nextTotalWeight += weight;
                    }
                    if (state.streamableBytes() - bytesForChild == 0) {
                        writeChildNode(state);
                    }
                }

                if (bytesForTree > 0) {
                    int childBytesAllocated = writeChildren(child, bytesForTree);
                    bytesAllocated += childBytesAllocated;
                    nextConnectionWindow -= childBytesAllocated;
                }
            }
            connectionWindow = nextConnectionWindow;
            totalWeight = nextTotalWeight;
            tail = nextTail;
        }

        return bytesAllocated;
    }

    /**
     * Write bytes allocated to {@code state}
     */
    private static void writeChildNode(FlowState state) {
        state.writeBytes(state.allocated());
        state.resetAllocated();
    }

    /**
     * The outbound flow control state for a single stream.
     */
    final class FlowState {
        private final Queue<Frame> pendingWriteQueue;
        private final Http2Stream stream;
        private int window;
        private int pendingBytes;
        private int streamableBytesForTree;
        private int allocated;

        FlowState(Http2Stream stream, int initialWindowSize) {
            this.stream = stream;
            window(initialWindowSize);
            pendingWriteQueue = new ArrayDeque<Frame>(2);
        }

        int window() {
            return window;
        }

        void window(int initialWindowSize) {
            window = initialWindowSize;
        }

        /**
         * Increment the number of bytes allocated to this stream by the priority algorithm
         */
        void allocate(int bytes) {
            allocated += bytes;
        }

        /**
         * Gets the number of bytes that have been allocated to this stream by the priority algorithm.
         */
        int allocated() {
            return allocated;
        }

        /**
         * Reset the number of bytes that have been allocated to this stream by the priority algorithm.
         */
        void resetAllocated() {
            allocated = 0;
        }

        /**
         * Increments the flow control window for this stream by the given delta and returns the new value.
         */
        int incrementStreamWindow(int delta) throws Http2Exception {
            if (delta > 0 && Integer.MAX_VALUE - delta < window) {
                throw streamError(stream.id(), FLOW_CONTROL_ERROR,
                        "Window size overflow for stream: %d", stream.id());
            }
            int previouslyStreamable = streamableBytes();
            window += delta;

            // Update this branch of the priority tree if the streamable bytes have changed for this node.
            int streamableDelta = streamableBytes() - previouslyStreamable;
            if (streamableDelta != 0) {
                incrementStreamableBytesForTree(streamableDelta);
            }
            return window;
        }

        /**
         * Returns the maximum writable window (minimum of the stream and connection windows).
         */
        int writableWindow() {
            return min(window, connectionWindow());
        }

        /**
         * Returns the number of pending bytes for this node that will fit within the {@link #window}. This is used for
         * the priority algorithm to determine the aggregate total for {@link #priorityBytes} at each node. Each node
         * only takes into account it's stream window so that when a change occurs to the connection window, these
         * values need not change (i.e. no tree traversal is required).
         */
        int streamableBytes() {
            return max(0, min(pendingBytes, window));
        }

        int streamableBytesForTree() {
            return streamableBytesForTree;
        }

        /**
         * Creates a new payload with the given values and immediately enqueues it.
         */
        Frame newFrame(FlowControlled payload) {
            // Store this as the future for the most recent write attempt.
            Frame frame = new Frame(payload);
            pendingWriteQueue.offer(frame);
            return frame;
        }

        /**
         * Indicates whether or not there are frames in the pending queue.
         */
        boolean hasFrame() {
            return !pendingWriteQueue.isEmpty();
        }

        /**
         * Returns the the head of the pending queue, or {@code null} if empty.
         */
        Frame peek() {
            return pendingWriteQueue.peek();
        }

        /**
         * Clears the pending queue and writes errors for each remaining frame.
         */
        void clear() {
            for (;;) {
                Frame frame = pendingWriteQueue.poll();
                if (frame == null) {
                    break;
                }
                frame.writeError(streamError(stream.id(), INTERNAL_ERROR,
                        "Stream closed before write could take place"));
            }
        }

        /**
         * Writes up to the number of bytes from the pending queue. May write less if limited by the writable window, by
         * the number of pending writes available, or because a frame does not support splitting on arbitrary
         * boundaries.
         */
        int writeBytes(int bytes) {
            int bytesAttempted = 0;
            while (hasFrame()) {
                int maxBytes = min(bytes - bytesAttempted, writableWindow());
                bytesAttempted += peek().write(maxBytes);
                if (bytes - bytesAttempted <= 0) {
                  break;
                }
            }
            return bytesAttempted;
        }

        /**
         * Recursively increments the streamable bytes for this branch in the priority tree starting at the current
         * node.
         */
        void incrementStreamableBytesForTree(int numBytes) {
            streamableBytesForTree += numBytes;
            if (!stream.isRoot()) {
                state(stream.parent()).incrementStreamableBytesForTree(numBytes);
            }
        }

        /**
         * A wrapper class around the content of a data frame.
         */
        private final class Frame {
            final FlowControlled payload;

            Frame(FlowControlled payload) {
                this.payload = payload;
                // Increment the number of pending bytes for this stream.
                incrementPendingBytes(payload.size());
            }

            /**
             * Increments the number of pending bytes for this node. If there was any change to the number of bytes that
             * fit into the stream window, then {@link #incrementStreamableBytesForTree} to recursively update this
             * branch of the priority tree.
             */
            private void incrementPendingBytes(int numBytes) {
                int previouslyStreamable = streamableBytes();
                pendingBytes += numBytes;

                int delta = streamableBytes() - previouslyStreamable;
                if (delta != 0) {
                    incrementStreamableBytesForTree(delta);
                }
            }

            /**
             * Writes the frame and decrements the stream and connection window sizes. If the frame is in the pending
             * queue, the written bytes are removed from this branch of the priority tree.
             * <p>
             * Note: this does not flush the {@link ChannelHandlerContext}.
             */
            int write(int allowedBytes) {
                int before = payload.size();
                needFlush |= payload.write(Math.max(0, allowedBytes));
                int writtenBytes = before - payload.size();
                try {
                    connectionState().incrementStreamWindow(-writtenBytes);
                    incrementStreamWindow(-writtenBytes);
                } catch (Http2Exception e) { // Should never get here since we're decrementing.
                    throw new RuntimeException("Invalid window state when writing frame: " + e.getMessage(), e);
                }
                decrementPendingBytes(writtenBytes);
                if (payload.size() == 0) {
                    pendingWriteQueue.remove();
                }
                return writtenBytes;
            }

            /**
             * Discards this frame, writing an error. If this frame is in the pending queue, the unwritten bytes are
             * removed from this branch of the priority tree.
             */
            void writeError(Http2Exception cause) {
                decrementPendingBytes(payload.size());
                payload.error(cause);
            }

            /**
             * If this frame is in the pending queue, decrements the number of pending bytes for the stream.
             */
            void decrementPendingBytes(int bytes) {
                incrementPendingBytes(-bytes);
            }
        }
    }
}
