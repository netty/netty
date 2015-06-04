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
import java.util.Arrays;
import java.util.Deque;

/**
 * Basic implementation of {@link Http2RemoteFlowController}.
 */
public class DefaultHttp2RemoteFlowController implements Http2RemoteFlowController {
    private final Http2StreamVisitor WRITE_ALLOCATED_BYTES = new Http2StreamVisitor() {
        @Override
        public boolean visit(Http2Stream stream) {
            int written = state(stream).writeAllocatedBytes();
            if (written != -1 && listener != null) {
                listener.streamWritten(stream, written);
            }
            return true;
        }
    };
    private final Http2Connection connection;
    private final Http2Connection.PropertyKey stateKey;
    private int initialWindowSize = DEFAULT_WINDOW_SIZE;
    private ChannelHandlerContext ctx;
    private Listener listener;

    public DefaultHttp2RemoteFlowController(Http2Connection connection) {
        this.connection = checkNotNull(connection, "connection");

        // Add a flow state for the connection.
        stateKey = connection.newKey();
        connection.connectionStream().setProperty(stateKey,
                new DefaultState(connection.connectionStream(), initialWindowSize));

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

            @Override
            public void onPriorityTreeParentChanged(Http2Stream stream, Http2Stream oldParent) {
                Http2Stream parent = stream.parent();
                if (parent != null) {
                    int delta = state(stream).streamableBytesForTree();
                    if (delta != 0) {
                        state(parent).incrementStreamableBytesForTree(delta);
                    }
                }
            }

            @Override
            public void onPriorityTreeParentChanging(Http2Stream stream, Http2Stream newParent) {
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
    public void incrementWindowSize(ChannelHandlerContext ctx, Http2Stream stream, int delta) throws Http2Exception {
        // This call does not trigger any writes, all writes will occur when writePendingBytes is called.
        if (stream.id() == CONNECTION_STREAM_ID) {
            // Update the connection window
            connectionState().incrementStreamWindow(delta);
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
    public void addFlowControlled(ChannelHandlerContext ctx, Http2Stream stream, FlowControlled frame) {
        checkNotNull(ctx, "ctx");
        checkNotNull(frame, "frame");
        if (this.ctx != null && this.ctx != ctx) {
            throw new IllegalArgumentException("Writing data from multiple ChannelHandlerContexts is not supported");
        }
        // Save the context. We'll use this later when we write pending bytes.
        this.ctx = ctx;
        final AbstractState state;
        try {
            state = state(stream);
            state.enqueueFrame(frame);
        } catch (Throwable t) {
            frame.error(t);
        }
    }

    /**
     * For testing purposes only. Exposes the number of streamable bytes for the tree rooted at
     * the given stream.
     */
    int streamableBytesForTree(Http2Stream stream) {
        return state(stream).streamableBytesForTree();
    }

    private AbstractState state(Http2Stream stream) {
        return (AbstractState) checkNotNull(stream, "stream").getProperty(stateKey);
    }

    private AbstractState connectionState() {
        return (AbstractState) connection.connectionStream().getProperty(stateKey);
    }

    /**
     * Returns the flow control window for the entire connection.
     */
    private int connectionWindowSize() {
        return connectionState().windowSize();
    }

    /**
     * Writes as many pending bytes as possible, according to stream priority.
     */
    @Override
    public void writePendingBytes() throws Http2Exception {
        Http2Stream connectionStream = connection.connectionStream();
        int connectionWindowSize = state(connectionStream).windowSize();

        if (connectionWindowSize > 0) {
            // Allocate the bytes for the connection window to the streams, but do not write.
            allocateBytesForTree(connectionStream, connectionWindowSize);
        }

        // Now write all of the allocated bytes, must write as there may be empty frames with
        // EOS = true
        connection.forEachActiveStream(WRITE_ALLOCATED_BYTES);
    }

    /**
     * This will allocate bytes by stream weight and priority for the entire tree rooted at {@code parent}, but does not
     * write any bytes. The connection window is generally distributed amongst siblings according to their weight,
     * however we need to ensure that the entire connection window is used (assuming streams have >= connection window
     * bytes to send) and we may need some sort of rounding to accomplish this.
     *
     * @param parent The parent of the tree.
     * @param connectionWindowSize The connection window this is available for use at this point in the tree.
     * @return An object summarizing the write and allocation results.
     */
    int allocateBytesForTree(Http2Stream parent, int connectionWindowSize) throws Http2Exception {
        AbstractState state = state(parent);
        if (state.streamableBytesForTree() <= 0) {
            return 0;
        }
        // If the number of streamable bytes for this tree will fit in the connection window
        // then there is no need to prioritize the bytes...everyone sends what they have
        if (state.streamableBytesForTree() <= connectionWindowSize) {
            SimpleChildFeeder childFeeder = new SimpleChildFeeder(connectionWindowSize);
            parent.forEachChild(childFeeder);
            return childFeeder.bytesAllocated;
        }

        ChildFeeder childFeeder = new ChildFeeder(parent, connectionWindowSize);
        // Iterate once over all children of this parent and try to feed all the children.
        parent.forEachChild(childFeeder);

        // Now feed any remaining children that are still hungry until the connection
        // window collapses.
        childFeeder.feedHungryChildren();

        return childFeeder.bytesAllocated;
    }

    /**
     * A {@link Http2StreamVisitor} that performs the HTTP/2 priority algorithm to distribute the available connection
     * window appropriately to the children of a given stream.
     */
    private final class ChildFeeder implements Http2StreamVisitor {
        final int maxSize;
        int totalWeight;
        int connectionWindow;
        int nextTotalWeight;
        int nextConnectionWindow;
        int bytesAllocated;
        Http2Stream[] stillHungry;
        int nextTail;

        ChildFeeder(Http2Stream parent, int connectionWindow) {
            maxSize = parent.numChildren();
            totalWeight = parent.totalChildWeights();
            this.connectionWindow = connectionWindow;
            this.nextConnectionWindow = connectionWindow;
        }

        @Override
        public boolean visit(Http2Stream child) throws Http2Exception {
            // In order to make progress toward the connection window due to possible rounding errors, we make sure
            // that each stream (with data to send) is given at least 1 byte toward the connection window.
            int connectionWindowChunk = max(1, (int) (connectionWindow * (child.weight() / (double) totalWeight)));
            int bytesForTree = min(nextConnectionWindow, connectionWindowChunk);

            AbstractState state = state(child);
            int bytesForChild = min(state.streamableBytes(), bytesForTree);

            // Allocate the bytes to this child.
            if (bytesForChild > 0) {
                state.allocate(bytesForChild);
                bytesAllocated += bytesForChild;
                nextConnectionWindow -= bytesForChild;
                bytesForTree -= bytesForChild;

                // If this subtree still wants to send then re-insert into children list and re-consider for next
                // iteration. This is needed because we don't yet know if all the peers will be able to use
                // all of their "fair share" of the connection window, and if they don't use it then we should
                // divide their unused shared up for the peers who still want to send.
                if (nextConnectionWindow > 0 && state.streamableBytesForTree() > 0) {
                    stillHungry(child);
                    nextTotalWeight += child.weight();
                }
            }

            // Allocate any remaining bytes to the children of this stream.
            if (bytesForTree > 0) {
                int childBytesAllocated = allocateBytesForTree(child, bytesForTree);
                bytesAllocated += childBytesAllocated;
                nextConnectionWindow -= childBytesAllocated;
            }

            return nextConnectionWindow > 0;
        }

        void feedHungryChildren() throws Http2Exception {
            if (stillHungry == null) {
                // There are no hungry children to feed.
                return;
            }

            totalWeight = nextTotalWeight;
            connectionWindow = nextConnectionWindow;

            // Loop until there are not bytes left to stream or the connection window has collapsed.
            for (int tail = nextTail; tail > 0 && connectionWindow > 0;) {
                nextTotalWeight = 0;
                nextTail = 0;

                // Iterate over the children that are currently still hungry.
                for (int head = 0; head < tail && nextConnectionWindow > 0; ++head) {
                    if (!visit(stillHungry[head])) {
                        // The connection window has collapsed, break out of the loop.
                        break;
                    }
                }
                connectionWindow = nextConnectionWindow;
                totalWeight = nextTotalWeight;
                tail = nextTail;
            }
        }

        /**
         * Indicates that the given child is still hungry (i.e. still has streamable bytes that can
         * fit within the current connection window).
         */
        void stillHungry(Http2Stream child) {
            ensureSpaceIsAllocated(nextTail);
            stillHungry[nextTail++] = child;
        }

        /**
         * Ensures that the {@link #stillHungry} array is properly sized to hold the given index.
         */
        void ensureSpaceIsAllocated(int index) {
            if (stillHungry == null) {
                // Initial size is 1/4 the number of children. Clipping the minimum at 2, which will over allocate if
                // maxSize == 1 but if this was true we shouldn't need to re-allocate because the 1 child should get
                // all of the available connection window.
                stillHungry = new Http2Stream[max(2, maxSize / 4)];
            } else if (index == stillHungry.length) {
                // Grow the array by a factor of 2.
                stillHungry = Arrays.copyOf(stillHungry, min(maxSize, stillHungry.length * 2));
            }
        }
    }

    /**
     * A simplified version of {@link ChildFeeder} that is only used when all streamable bytes fit within the
     * available connection window.
     */
    private final class SimpleChildFeeder implements Http2StreamVisitor {
        int bytesAllocated;
        int connectionWindow;

        SimpleChildFeeder(int connectionWindow) {
            this.connectionWindow = connectionWindow;
        }

        @Override
        public boolean visit(Http2Stream child) throws Http2Exception {
            AbstractState childState = state(child);
            int bytesForChild = childState.streamableBytes();

            if (bytesForChild > 0 || childState.hasFrame()) {
                childState.allocate(bytesForChild);
                bytesAllocated += bytesForChild;
                connectionWindow -= bytesForChild;
            }
            int childBytesAllocated = allocateBytesForTree(child, connectionWindow);
            bytesAllocated += childBytesAllocated;
            connectionWindow -= childBytesAllocated;
            return true;
        }
    }

    /**
     * The remote flow control state for a single stream.
     */
    private final class DefaultState extends AbstractState {
        private final Deque<FlowControlled> pendingWriteQueue;
        private int window;
        private int pendingBytes;
        private int allocated;
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
        void allocate(int bytes) {
            allocated += bytes;
            // Also artificially reduce the streamable bytes for this tree to give the appearance
            // that the data has been written. This will be restored before the allocated bytes are
            // actually written.
            incrementStreamableBytesForTree(-bytes);
        }

        @Override
        int writeAllocatedBytes() {
            int numBytes = allocated;

            // Restore the number of streamable bytes to this branch.
            incrementStreamableBytesForTree(allocated);
            resetAllocated();

            // Perform the write.
            return writeBytes(numBytes);
        }

        /**
         * Reset the number of bytes that have been allocated to this stream by the priority algorithm.
         */
        private void resetAllocated() {
            allocated = 0;
        }

        @Override
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

        @Override
        int writableWindow() {
            return min(window, connectionWindowSize());
        }

        @Override
        int streamableBytes() {
            return max(0, min(pendingBytes - allocated, window));
        }

        @Override
        int streamableBytesForTree() {
            return streamableBytesForTree;
        }

        @Override
        void enqueueFrame(FlowControlled frame) {
            incrementPendingBytes(frame.size());
            FlowControlled last = pendingWriteQueue.peekLast();
            if (last == null || !last.merge(frame)) {
                pendingWriteQueue.offer(frame);
            }
        }

        @Override
        boolean hasFrame() {
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
        }

        @Override
        int writeBytes(int bytes) {
            boolean wrote = false;
            int bytesAttempted = 0;
            int writableBytes = min(bytes, writableWindow());
            while (hasFrame() && (writableBytes > 0 || peek().size() == 0)) {
                wrote = true;
                bytesAttempted += write(peek(), writableBytes);
                writableBytes = min(bytes - bytesAttempted, writableWindow());
            }
            if (wrote) {
                return bytesAttempted;
            } else {
                return -1;
            }
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
                frame.write(max(0, allowedBytes));
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
         * Increments the number of pending bytes for this node. If there was any change to the number of bytes that
         * fit into the stream window, then {@link #incrementStreamableBytesForTree} is called to recursively update
         * this branch of the priority tree.
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
                connectionState().incrementStreamWindow(negativeBytes);
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
            decrementPendingBytes(frame.size());
            frame.error(cause);
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
        int writableWindow() {
            return 0;
        }

        @Override
        int streamableBytes() {
            return 0;
        }

        @Override
        int streamableBytesForTree() {
            return streamableBytesForTree;
        }

        @Override
        int writeAllocatedBytes() {
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
        int writeBytes(int bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        void enqueueFrame(FlowControlled frame) {
            throw new UnsupportedOperationException();
        }

        @Override
        void allocate(int bytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        boolean hasFrame() {
            return false;
        }
    }

    /**
     * An abstraction which provides specific extensions used by remote flow control.
     */
    private abstract class AbstractState {
        protected final Http2Stream stream;
        protected int streamableBytesForTree;

        AbstractState(Http2Stream stream) {
            this.stream = stream;
        }

        AbstractState(AbstractState existingState) {
            this.stream = existingState.stream();
            this.streamableBytesForTree = existingState.streamableBytesForTree();
        }

        /**
         * The stream this state is associated with.
         */
        final Http2Stream stream() {
            return stream;
        }

        /**
         * Recursively increments the {@link #streamableBytesForTree()} for this branch in the priority tree starting
         * at the current node.
         */
        final void incrementStreamableBytesForTree(int numBytes) {
            streamableBytesForTree += numBytes;
            if (!stream.isRoot()) {
                state(stream.parent()).incrementStreamableBytesForTree(numBytes);
            }
        }

        abstract int windowSize();

        abstract int initialWindowSize();

        /**
         * Write the allocated bytes for this stream.
         *
         * @return the number of bytes written for a stream or {@code -1} if no write occurred.
         */
        abstract int writeAllocatedBytes();

        /**
         * Returns the number of pending bytes for this node that will fit within the
         * {@link #writableWindow()}. This is used for the priority algorithm to determine the aggregate
         * number of bytes that can be written at each node. Each node only takes into account its
         * stream window so that when a change occurs to the connection window, these values need
         * not change (i.e. no tree traversal is required).
         */
        abstract int streamableBytes();

        /**
         * Get the {@link #streamableBytes()} for the entire tree rooted at this node.
         */
        abstract int streamableBytesForTree();

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
         * Returns the maximum writable window (minimum of the stream and connection windows).
         */
        abstract int writableWindow();

        /**
         * Writes up to the number of bytes from the pending queue. May write less if limited by the writable window, by
         * the number of pending writes available, or because a frame does not support splitting on arbitrary
         * boundaries. Will return {@code -1} if there are no frames to write.
         */
        abstract int writeBytes(int bytes);

        /**
         * Adds the {@code frame} to the pending queue and increments the pending byte count.
         */
        abstract void enqueueFrame(FlowControlled frame);

        /**
         * Increment the number of bytes allocated to this stream by the priority algorithm
         */
        abstract void allocate(int bytes);

        /**
         * Indicates whether or not there are frames in the pending queue.
         */
        abstract boolean hasFrame();
    }
}
