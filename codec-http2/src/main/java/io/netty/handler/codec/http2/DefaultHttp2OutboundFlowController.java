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
import static io.netty.handler.codec.http2.Http2Error.FLOW_CONTROL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.STREAM_CLOSED;
import static io.netty.handler.codec.http2.Http2Exception.format;
import static io.netty.handler.codec.http2.Http2Exception.protocolError;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2PriorityTree.Priority;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * Basic implementation of {@link Http2OutboundFlowController}.
 */
public class DefaultHttp2OutboundFlowController implements Http2OutboundFlowController {
    /**
     * The interval (in ns) at which the removed priority garbage collector runs.
     */
    private final long GARBAGE_COLLECTION_INTERVAL = TimeUnit.SECONDS.toNanos(2);

    private final Http2PriorityTree<FlowState> priorityTree =
            new DefaultHttp2PriorityTree<FlowState>();
    private final Queue<Priority<FlowState>> garbage = new ArrayDeque<Priority<FlowState>>();
    private int initialWindowSize = DEFAULT_FLOW_CONTROL_WINDOW_SIZE;
    private int connectionWindowSize = DEFAULT_FLOW_CONTROL_WINDOW_SIZE;
    private long lastGarbageCollection;

    @Override
    public void addStream(int streamId, int parent, short weight, boolean exclusive) {
        if (streamId <= 0) {
            throw new IllegalArgumentException("Stream ID must be > 0");
        }
        Priority<FlowState> priority = priorityTree.get(streamId);
        if (priority != null) {
            throw new IllegalArgumentException("stream " + streamId + " already exists");
        }

        FlowState state = new FlowState();
        priority = priorityTree.prioritize(streamId, parent, weight, exclusive, state);
        state.init(priority);
    }

    @Override
    public void updateStream(int streamId, int parent, short weight, boolean exclusive) {
        if (streamId <= 0) {
            throw new IllegalArgumentException("Stream ID must be > 0");
        }
        Priority<FlowState> priority = priorityTree.get(streamId);
        if (priority == null) {
            throw new IllegalArgumentException("stream " + streamId + " does not exist");
        }

        priorityTree.prioritize(streamId, parent, weight, exclusive, priority.data());
    }

    @Override
    public void removeStream(int streamId) {
        if (streamId <= 0) {
            throw new IllegalArgumentException("Stream ID must be > 0");
        }

        Priority<FlowState> priority = priorityTree.get(streamId);
        if (priority != null) {
            priority.data().markForRemoval();
        }
    }

    @Override
    public void initialOutboundWindowSize(int newWindowSize) throws Http2Exception {
        int delta = newWindowSize - initialWindowSize;
        initialWindowSize = newWindowSize;
        addAndGetConnectionWindowSize(delta);
        for (Priority<FlowState> priority : priorityTree) {
            FlowState state = priority.data();
            if (!state.isMarkedForRemoval()) {
                // Verify that the maximum value is not exceeded by this change.
                state.addAndGetWindow(delta);
            }
        }

        if (delta > 0) {
            // The window size increased, send any pending frames for all streams.
            writePendingBytes();
        }
    }

    @Override
    public int initialOutboundWindowSize() {
        return initialWindowSize;
    }

    @Override
    public void updateOutboundWindowSize(int streamId, int delta) throws Http2Exception {
        if (streamId == CONNECTION_STREAM_ID) {
            // Update the connection window and write any pending frames for all streams.
            addAndGetConnectionWindowSize(delta);
            writePendingBytes();
        } else {
            // Update the stream window and write any pending frames for the stream.
            FlowState state = getStateOrFail(streamId);
            state.addAndGetWindow(delta);
            state.writeBytes(state.writableWindow());
        }
    }

    @Override
    public void setBlocked(int streamId) throws Http2Exception {
        // Ignore blocked frames. Just rely on window updates.
    }

    @Override
    public void sendFlowControlled(int streamId, ByteBuf data, int padding, boolean endStream,
            boolean endSegment, boolean compressed, FrameWriter frameWriter) throws Http2Exception {
        FlowState state = getStateOrFail(streamId);
        FlowState.Frame frame =
                state.newFrame(data, padding, endStream, endSegment, compressed, frameWriter);

        int dataLength = data.readableBytes();
        if (state.writableWindow() >= dataLength) {
            // Window size is large enough to send entire data frame
            frame.write();
            return;
        }

        // Enqueue the frame to be written when the window size permits.
        frame.enqueue();

        if (state.writableWindow() <= 0) {
            // Stream is stalled, don't send anything now.
            return;
        }

        // Create and send a partial frame up to the window size.
        frame.split(state.writableWindow()).write();
    }

    /**
     * Attempts to get the {@link FlowState} for the given stream. If not available, raises a
     * {@code PROTOCOL_ERROR}.
     */
    private FlowState getStateOrFail(int streamId) throws Http2Exception {
        FlowState state = getFlowState(streamId);
        if (state == null) {
            throw protocolError("Missing flow control window for stream: %d", streamId);
        }
        return state;
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
     * Writes as many pending bytes as possible, according to stream priority.
     */
    private void writePendingBytes() throws Http2Exception {
        // Perform garbage collection to remove any priorities marked for deletion from the tree.
        garbageCollect();

        // Calculate the total writable bytes for each stream in the tree.
        // TODO: consider maintaining a running total of the writable bytes at each node so we
        // don't have to walk the entire tree here.
        int totalWritableBytes = 0;
        for (Priority<FlowState> priority : priorityTree.root().children()) {
            totalWritableBytes += priority.data().calcWritableBytes();
        }

        // Recursively write as many of the total writable bytes as possible.
        writeAllowedBytes(totalWritableBytes, priorityTree.root());
    }

    /**
     * Recursively traverses the priority tree rooted at the given node. Attempts to write the
     * allowed bytes for the streams in this sub tree based on their weighted priorities.
     *
     * @param allowance an allowed number of bytes that may be written to the streams in this sub
     *            tree.
     * @param priority the sub tree to write to. On the first invocation, this will be the root of
     *            the priority tree (i.e. the connection node).
     */
    private void writeAllowedBytes(int allowance, Priority<FlowState> priority)
            throws Http2Exception {
        // Get the flow control state for this priority node. It may be null if processing the root
        // node (i.e. the connection) or if the priority is being retained for a short time
        // after the stream was closed.
        FlowState state = priority.data();
        if (state != null) {
            // Write the allowed bytes for this node. If not all of the allowance was used,
            // restore what's left so that it can be propagated to future nodes.
            int bytesWritten = state.writeBytes(allowance);
            allowance -= bytesWritten;
        }

        if (allowance <= 0 || priority.isLeaf()) {
            // Nothing left to do in this sub tree.
            return;
        }

        // Clip the remaining connection flow control window by the allowance.
        int remainingWindow = Math.min(allowance, connectionWindowSize);

        // Decreasing totals for all nodes yet to be processed in the current pass.
        // For the root node, the allowance is the total number of writable bytes.
        int unallocatedBytes = priority.isRoot() ? allowance : priority.data().unallocatedBytes();

        // Optimization. If the window is big enough to fit all the data. Just write everything
        // and skip the priority algorithm.
        if (unallocatedBytes <= remainingWindow) {
            for (Priority<FlowState> child : priority.children()) {
                child.data().writeBytes(child.data().unallocatedBytes());
            }
            return;
        }

        // Get the total weight of all children directly under the current node.
        int remainingWeight = priority.totalChildWeights();

        // Iterate over the children and spread the remaining bytes across them as is appropriate
        // based on the weights. This algorithm loops over all of the children more than once,
        // although it should typically only take a few passes to complete. In each pass we
        // give a node its share of the current remaining bytes. The node's weight and bytes
        // allocated are then decremented from the totals, so that the subsequent
        // nodes split the difference. If after being processed, a node still has writable data,
        // it is added to the end of the queue and will be processed again in the next pass.

        // Increasing totals for nodes that have been re-added to the queue for the next pass.
        int unallocatedBytesForNextPass = 0;
        int remainingWeightForNextPass = 0;

        // Copy the children to a deque
        ArrayDeque<Priority<FlowState>> deque =
                new ArrayDeque<Priority<FlowState>>(priority.children());
        for (;;) {
            Priority<FlowState> next = deque.poll();
            if (next == null) {
                break;
            }
            FlowState node = next.data();

            if (remainingWeight == 0) {
                // We've reached the end one pass of the nodes. Reset the totals based on
                // the nodes that were re-added to the deque since they still have data available.
                unallocatedBytes = unallocatedBytesForNextPass;
                remainingWeight = remainingWeightForNextPass;
                unallocatedBytesForNextPass = 0;
                remainingWeightForNextPass = 0;
            }

            int weight = node.priority().weight();

            // Determine the amount of data that's still unallocated and will fit into
            // the current connection window.
            int writableData = Math.min(unallocatedBytes, remainingWindow);
            if (writableData > 0 && node.unallocatedBytes() > 0) {

                // Determine the value (in bytes) of a single unit of weight.
                double dataToWeightRatio = writableData / (double) remainingWeight;

                // Determine the portion of the current writable data that is assigned to this
                // node.
                int writableChunk = (int) (weight * dataToWeightRatio);

                // Clip the chunk allocated by the total amount of unallocated data remaining in
                // the node.
                int allocatedChunk = Math.min(writableChunk, node.unallocatedBytes());

                // Update the remaining connection window size.
                remainingWindow -= allocatedChunk;

                // This node has been processed for this loop. Remove it from the loop totals.
                unallocatedBytes -= node.unallocatedBytes();
                remainingWeight -= weight;

                // Update the node state.
                node.allocateBytes(allocatedChunk);
                if (node.unallocatedBytes() > 0) {
                    // There is still data remaining for this stream. Add it to the end of the
                    // deque to be processed in the next loop.
                    unallocatedBytesForNextPass += node.unallocatedBytes();
                    remainingWeightForNextPass += weight;
                    deque.add(node.priority());

                    // Don't write the data for this node yet - there may be more that will
                    // be allocated in the next loop.
                    continue;
                }
            } else {
                // This node has been processed for this loop. Remove it from the loop totals.
                unallocatedBytes -= node.unallocatedBytes();
                remainingWeight -= weight;
            }

            if (node.allocatedBytes() > 0) {
                // Write the allocated data for this stream.
                writeAllowedBytes(node.allocatedBytes(), node.priority());
            }
        }
    }

    private FlowState getFlowState(int streamId) {
        Priority<FlowState> priority = priorityTree.get(streamId);
        return priority != null ? priority.data() : null;
    }

    /**
     * Removes any priorities from the tree that were marked for removal greater than
     * {@link #GARBAGE_COLLECTION_INTERVAL} milliseconds ago. Garbage collection will run at most on
     * the interval {@link #GARBAGE_COLLECTION_INTERVAL}, so calling it more frequently will have no
     * effect.
     */
    private void garbageCollect() {
        if (garbage.isEmpty()) {
            return;
        }

        long time = System.nanoTime();
        if (time - lastGarbageCollection < GARBAGE_COLLECTION_INTERVAL) {
            // Only run the garbage collection on the threshold interval (at most).
            return;
        }
        lastGarbageCollection = time;

        for (;;) {
            Priority<FlowState> next = garbage.peek();
            if (next == null) {
                break;
            }
            long removeTime = next.data().removalTime();
            if (time - removeTime > GARBAGE_COLLECTION_INTERVAL) {
                Priority<FlowState> priority = garbage.remove();
                priorityTree.remove(priority.streamId());
            } else {
                break;
            }
        }
    }

    /**
     * The outbound flow control state for a single stream.
     */
    private final class FlowState {
        private final Queue<Frame> pendingWriteQueue = new ArrayDeque<Frame>(2);
        private Priority<FlowState> priority;
        private int windowSize = initialWindowSize;
        private long removalTime;
        private int writableBytes;
        private int allocatedBytes;

        /**
         * Initializes this flow state with the stream priority.
         */
        void init(Priority<FlowState> priority) {
            this.priority = priority;
        }

        /**
         * Gets the priority in the tree associated with this flow state.
         */
        Priority<FlowState> priority() {
            return priority;
        }

        /**
         * Indicates that this priority has been marked for removal, thus making it a candidate for
         * garbage collection.
         */
        boolean isMarkedForRemoval() {
            return removalTime > 0L;
        }

        /**
         * If marked for removal, indicates the removal time of this priority.
         */
        long removalTime() {
            return removalTime;
        }

        /**
         * Marks this state for removal, thus making it a candidate for garbage collection. Sets the
         * removal time to the current system time.
         */
        void markForRemoval() {
            if (!isMarkedForRemoval()) {
                removalTime = System.nanoTime();
                garbage.add(priority);
                clear();
            }
        }

        /**
         * Increments the flow control window for this stream by the given delta and returns the new
         * value.
         */
        int addAndGetWindow(int delta) throws Http2Exception {
            if (isIntegerOverflow(windowSize, delta)) {
                throw new Http2StreamException(priority.streamId(), FLOW_CONTROL_ERROR,
                        "Window size overflow for stream");
            }
            windowSize += delta;
            return windowSize;
        }

        /**
         * Returns the maximum writable window (minimum of the stream and connection windows).
         */
        int writableWindow() {
            return Math.min(windowSize, connectionWindowSize);
        }

        /**
         * Used by the priority algorithm to allocate bytes to this stream.
         */
        void allocateBytes(int bytes) {
            allocatedBytes += bytes;
        }

        /**
         * Used by the priority algorithm to get the intermediate allocation of bytes to this
         * stream.
         */
        int allocatedBytes() {
            return allocatedBytes;
        }

        /**
         * Used by the priority algorithm to determine the number of writable bytes that have not
         * yet been allocated.
         */
        int unallocatedBytes() {
            return writableBytes - allocatedBytes;
        }

        /**
         * Used by the priority algorithm to calculate the number of writable bytes for this
         * sub-tree. Writable bytes takes into account the connection window and the stream windows
         * for each node.
         */
        int calcWritableBytes() {
            writableBytes = 0;

            // Calculate the writable bytes for this node.
            if (!isMarkedForRemoval()) {
                int window = writableWindow();
                for (Frame frame : pendingWriteQueue) {
                    writableBytes += Math.min(window, frame.data.readableBytes());
                    if (writableBytes == window) {
                        break;
                    }
                }
            }

            // Calculate the writable bytes for all children.
            for (Priority<FlowState> child : priority.children()) {
                writableBytes += child.data().calcWritableBytes();
            }

            return writableBytes;
        }

        /**
         * Creates a new frame with the given values but does not add it to the pending queue.
         */
        Frame newFrame(ByteBuf data, int padding, boolean endStream, boolean endSegment,
                boolean compressed, FrameWriter writer) {
            return new Frame(data, padding, endStream, endSegment, compressed, writer);
        }

        /**
         * Indicates whether or not there are frames in the pending queue.
         */
        boolean hasFrame() {
            return !pendingWriteQueue.isEmpty();
        }

        /**
         * Returns the the head of the pending queue, or {@code null} if empty or the current window
         * size is zero.
         */
        Frame peek() {
            if (windowSize > 0) {
                return pendingWriteQueue.peek();
            }
            return null;
        }

        /**
         * Clears the pending queue and writes errors for each remaining frame.
         */
        void clear() {
            while (true) {
                Frame frame = pendingWriteQueue.poll();
                if (frame == null) {
                    break;
                }
                frame.writeError(format(STREAM_CLOSED,
                        "Stream closed before write could take place"));
            }
        }

        /**
         * Writes up to the number of bytes from the pending queue. May write less if limited by the
         * writable window, by the number of pending writes available, or because a frame does not
         * support splitting on arbitrary boundaries.
         */
        int writeBytes(int bytes) throws Http2Exception {
            int bytesWritten = 0;
            if (isMarkedForRemoval()) {
                return bytesWritten;
            }

            int maxBytes = Math.min(bytes, writableWindow());
            while (bytesWritten < maxBytes && hasFrame()) {
                Frame pendingWrite = peek();
                if (maxBytes >= pendingWrite.size()) {
                    // Window size is large enough to send entire data frame
                    bytesWritten += pendingWrite.size();
                    pendingWrite.write();
                } else {
                    // We can send a partial frame
                    Frame partialFrame = pendingWrite.split(maxBytes);
                    bytesWritten += partialFrame.size();
                    partialFrame.write();
                }

                // Update the threshold.
                maxBytes = Math.min(bytes - bytesWritten, writableWindow());
            }
            return bytesWritten;
        }

        /**
         * A wrapper class around the content of a data frame.
         */
        private final class Frame {
            private final ByteBuf data;
            private final int padding;
            private final boolean endStream;
            private final boolean endSegment;
            private final boolean compressed;
            private final FrameWriter writer;
            private boolean enqueued;

            Frame(ByteBuf data, int padding, boolean endStream, boolean endSegment,
                    boolean compressed, FrameWriter writer) {
                this.data = data;
                this.padding = padding;
                this.endStream = endStream;
                this.endSegment = endSegment;
                this.compressed = compressed;
                this.writer = writer;
            }

            int size() {
                return data.readableBytes();
            }

            void enqueue() {
                if (!enqueued) {
                    enqueued = true;
                    pendingWriteQueue.offer(this);
                }
            }

            /**
             * Writes the frame and decrements the stream and connection window sizes. If the frame
             * is in the pending queue, the written bytes are removed from this branch of the
             * priority tree.
             */
            void write() throws Http2Exception {
                int dataLength = data.readableBytes();
                connectionWindowSize -= dataLength;
                addAndGetWindow(-dataLength);
                writer.writeFrame(priority.streamId(), data, padding, endStream, endSegment,
                        compressed);
            }

            /**
             * Discards this frame, writing an error. If this frame is in the pending queue, the
             * unwritten bytes are removed from this branch of the priority tree.
             */
            void writeError(Http2Exception cause) {
                data.release();
                writer.setFailure(cause);
            }

            /**
             * Creates a new frame that is a view of this frame's data buffer starting at the
             * current read index with the given number of bytes. The reader index on the input
             * frame is then advanced by the number of bytes. The returned frame will not have
             * end-of-stream set and it will not be automatically placed in the pending queue.
             *
             * @param maxBytes the maximum number of bytes that is allowed in the created frame.
             * @return the partial frame.
             */
            Frame split(int maxBytes) {
                // TODO: Should padding be included in the chunks or only the last frame?
                maxBytes = Math.min(maxBytes, data.readableBytes());
                return new Frame(data.readSlice(maxBytes).retain(), 0, false, false, compressed,
                        writer);
            }
        }
    }
}
