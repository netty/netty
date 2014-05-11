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
import static java.lang.Math.max;
import static java.lang.Math.min;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http2.Http2PriorityTree.Priority;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * Basic implementation of {@link Http2OutboundFlowController}.
 */
public class DefaultHttp2OutboundFlowController implements Http2OutboundFlowController {
    /**
     * The interval (in ns) at which the removed priority garbage collector runs.
     */
    private static final long GARBAGE_COLLECTION_INTERVAL = TimeUnit.SECONDS.toNanos(2);

    /**
     * A comparators that sorts priority nodes in ascending order by the amount
     * of priority data available for its subtree.
     */
    private static final Comparator<Priority<FlowState>> DATA_WEIGHT =
            new Comparator<Priority<FlowState>>() {
                private static final int MAX_DATA_THRESHOLD = Integer.MAX_VALUE / 256;
                @Override
                public int compare(Priority<FlowState> o1, Priority<FlowState> o2) {
                    int o1Data = o1.data().priorityBytes();
                    int o2Data = o2.data().priorityBytes();
                    if (o1Data > MAX_DATA_THRESHOLD || o2Data > MAX_DATA_THRESHOLD) {
                        // Corner case to make sure we don't overflow an integer with
                        // the multiply.
                        return o1.data().priorityBytes() - o2.data().priorityBytes();
                    }

                    // Scale the data by the weight.
                    return (o1Data * o1.weight()) - (o2Data * o2.weight());
                }
            };

    private final Http2PriorityTree<FlowState> priorityTree;
    private final FlowState connectionFlow;
    private final GarbageCollector garbageCollector;
    private int initialWindowSize = DEFAULT_FLOW_CONTROL_WINDOW_SIZE;

    public DefaultHttp2OutboundFlowController() {
        priorityTree = new DefaultHttp2PriorityTree<FlowState>();
        connectionFlow = new FlowState(priorityTree.root());
        priorityTree.root().data(connectionFlow);
        garbageCollector = new GarbageCollector();
    }

    @Override
    public void addStream(int streamId, int parent, short weight, boolean exclusive) {
        if (streamId <= 0) {
            throw new IllegalArgumentException("Stream ID must be > 0");
        }
        Priority<FlowState> priority = priorityTree.get(streamId);
        if (priority != null) {
            throw new IllegalArgumentException("stream " + streamId + " already exists");
        }

        priority = priorityTree.prioritize(streamId, parent, weight, exclusive);
        priority.data(new FlowState(priority));
    }

    @Override
    public void updateStream(int streamId, int parentId, short weight, boolean exclusive) {
        if (streamId <= 0) {
            throw new IllegalArgumentException("Stream ID must be > 0");
        }
        Priority<FlowState> priority = priorityTree.get(streamId);
        if (priority == null) {
            throw new IllegalArgumentException("stream " + streamId + " does not exist");
        }

        // Get the parent stream.
        Priority<FlowState> parent = priorityTree.root();
        if (parentId != 0) {
            parent = priorityTree.get(parentId);
            if (parent == null) {
                throw new IllegalArgumentException("Parent stream " + parentId + " does not exist");
            }
        }

        // Determine whether we're adding a circular dependency on the parent. If so, this will
        // restructure the tree to move this priority above the parent.
        boolean circularDependency = parent.isDescendantOf(priority);
        Priority<FlowState> previousParent = priority.parent();
        boolean parentChange = previousParent != parent;

        // If the parent is changing, remove the priority bytes from all relevant branches of the
        // priority tree. We will restore them where appropriate after the move.
        if (parentChange) {
            // The parent is changing, remove the priority bytes for this subtree from its previous
            // parent.
            previousParent.data().incrementPriorityBytes(-priority.data().priorityBytes());
            if (circularDependency) {
                // The parent is currently a descendant of priority. Remove the priority bytes
                // for its subtree starting at its parent node.
                parent.parent().data().incrementPriorityBytes(-parent.data().priorityBytes());
            }
        }

        // Adjust the priority tree.
        priorityTree.prioritize(streamId, parentId, weight, exclusive);

        // If the parent was changed, restore the priority bytes to the appropriate branches
        // of the priority tree.
        if (parentChange) {
            if (circularDependency) {
                // The parent was re-rooted. Update the priority bytes for its parent branch.
               parent.parent().data().incrementPriorityBytes(parent.data().priorityBytes());
            }

            // Add the priority bytes for this subtree to the new parent branch.
            parent.data().incrementPriorityBytes(priority.data().priorityBytes());
        }
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
        connectionFlow.incrementStreamWindow(delta);
        for (Priority<FlowState> priority : priorityTree) {
            FlowState state = priority.data();
            if (!state.isMarkedForRemoval()) {
                // Verify that the maximum value is not exceeded by this change.
                state.incrementStreamWindow(delta);
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
            connectionFlow.incrementStreamWindow(delta);
            writePendingBytes();
        } else {
            // Update the stream window and write any pending frames for the stream.
            FlowState state = getStateOrFail(streamId);
            state.incrementStreamWindow(delta);
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
     * Returns the flow control window for the entire connection.
     */
    private int connectionWindow() {
        return priorityTree.root().data().streamWindow();
    }

    /**
     * Writes as many pending bytes as possible, according to stream priority.
     */
    private void writePendingBytes() throws Http2Exception {
        // Perform garbage collection to remove any priorities marked for deletion from the tree.
        garbageCollector.run();

        // Recursively write as many of the total writable bytes as possible.
        Priority<FlowState> root = priorityTree.root();
        writeAllowedBytes(root, root.data().priorityBytes());
    }

    /**
     * Recursively traverses the priority tree rooted at the given node. Attempts to write the
     * allowed bytes for the streams in this sub tree based on their weighted priorities.
     *
     * @param priority the sub tree to write to. On the first invocation, this will be the root of
     *            the priority tree (i.e. the connection node).
     * @param allowance an allowed number of bytes that may be written to the streams in this sub
     *            tree.
     */
    private void writeAllowedBytes(Priority<FlowState> priority, int allowance)
            throws Http2Exception {
        // Write the allowed bytes for this node. If not all of the allowance was used,
        // restore what's left so that it can be propagated to future nodes.
        FlowState state = priority.data();
        int bytesWritten = state.writeBytes(allowance);
        allowance -= bytesWritten;

        if (allowance <= 0 || priority.isLeaf()) {
            // Nothing left to do in this sub tree.
            return;
        }

        // Clip the remaining connection flow control window by the allowance.
        int remainingWindow = min(allowance, connectionWindow());

        // The total number of unallocated bytes from the children of this node.
        int unallocatedBytes = state.priorityBytes() - state.streamableBytes();

        // Optimization. If the window is big enough to fit all the data. Just write everything
        // and skip the priority algorithm.
        if (unallocatedBytes <= remainingWindow) {
            for (Priority<FlowState> child : priority.children()) {
                writeAllowedBytes(child, child.data().unallocatedPriorityBytes());
            }
            return;
        }

        // Copy and sort the children of this node. They are sorted in ascending order the total
        // priority bytes for the subtree scaled by the weight of the node. The algorithm gives
        // preference to nodes that appear later in the list, since the weight of each node
        // increases in value as the list is iterated. This means that with this node ordering,
        // the most bytes will be written to those nodes with the largest aggregate number of
        // bytes and the highest priority.
        ArrayList<Priority<FlowState>> states = new ArrayList<Priority<FlowState>>(priority.children());
        Collections.sort(states, DATA_WEIGHT);

        // Iterate over the children and spread the remaining bytes across them as is appropriate
        // based on the weights. This algorithm loops over all of the children more than once,
        // although it should typically only take a few passes to complete. In each pass we
        // give a node its share of the current remaining bytes. The node's weight and bytes
        // allocated are then decremented from the totals, so that the subsequent
        // nodes split the difference. If after being processed, a node still has writable data,
        // it is added back to the queue for further processing in the next pass.
        int remainingWeight = priority.totalChildWeights();
        int nextTail = 0;
        int unallocatedBytesForNextPass = 0;
        int remainingWeightForNextPass = 0;
        for (int head = 0, tail = states.size(); ; ++head) {
            if (head >= tail) {
                // We've reached the end one pass of the nodes. Reset the totals based on
                // the nodes that were re-added to the deque since they still have data available.
                unallocatedBytes = unallocatedBytesForNextPass;
                remainingWeight = remainingWeightForNextPass;
                unallocatedBytesForNextPass = 0;
                remainingWeightForNextPass = 0;
                head = 0;
                tail = nextTail;
                nextTail = 0;
            }

            // Get the next state, or break if nothing to do.
            if (head >= tail) {
                break;
            }
            Priority<FlowState> next = states.get(head);
            FlowState node = next.data();
            int weight = node.priority().weight();

            // Determine the value (in bytes) of a single unit of weight.
            double dataToWeightRatio = min(unallocatedBytes, remainingWindow) / (double) remainingWeight;
            unallocatedBytes -= node.unallocatedPriorityBytes();
            remainingWeight -= weight;

            if (dataToWeightRatio > 0.0 && node.unallocatedPriorityBytes() > 0) {

                // Determine the portion of the current writable data that is assigned to this
                // node.
                int writableChunk = (int) (weight * dataToWeightRatio);

                // Clip the chunk allocated by the total amount of unallocated data remaining in
                // the node.
                int allocatedChunk = min(writableChunk, node.unallocatedPriorityBytes());

                // Update the remaining connection window size.
                remainingWindow -= allocatedChunk;

                // Mark these bytes as allocated.
                node.allocatePriorityBytes(allocatedChunk);
                if (node.unallocatedPriorityBytes() > 0) {
                    // There is still data remaining for this stream. Add it back to the queue
                    // for the next pass.
                    unallocatedBytesForNextPass += node.unallocatedPriorityBytes();
                    remainingWeightForNextPass += weight;
                    states.set(nextTail++, node.priority());
                    continue;
                }
            }

            if (node.allocatedPriorityBytes() > 0) {
                // Write the allocated data for this stream.
                writeAllowedBytes(node.priority(), node.allocatedPriorityBytes());

                // We're done with this node. Remark all bytes as unallocated for future
                // invocations.
                node.allocatePriorityBytes(0);
            }
        }
    }

    private FlowState getFlowState(int streamId) {
        Priority<FlowState> priority = priorityTree.get(streamId);
        return priority != null ? priority.data() : null;
    }

    /**
     * The outbound flow control state for a single stream.
     */
    private final class FlowState {
        private final Queue<Frame> pendingWriteQueue;
        private final Priority<FlowState> priority;
        private int streamWindow = initialWindowSize;
        private long removalTime;
        private int pendingBytes;
        private int priorityBytes;
        private int allocatedPriorityBytes;

        FlowState(Priority<FlowState> priority) {
            this.priority = priority;
            pendingWriteQueue = new ArrayDeque<Frame>(2);
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
                garbageCollector.add(priority);
                clear();
            }
        }

        /**
         * The flow control window for this stream. If this state is for stream 0, then this is
         * the flow control window for the entire connection.
         */
        int streamWindow() {
            return streamWindow;
        }

        /**
         * Increments the flow control window for this stream by the given delta and returns the new
         * value.
         */
        int incrementStreamWindow(int delta) throws Http2Exception {
            if (delta > 0 && (Integer.MAX_VALUE - delta) < streamWindow) {
                throw new Http2StreamException(priority.streamId(), FLOW_CONTROL_ERROR,
                        "Window size overflow for stream: " + priority.streamId());
            }
            int previouslyStreamable = streamableBytes();
            streamWindow += delta;

            // Update this branch of the priority tree if the streamable bytes have changed for this
            // node.
            incrementPriorityBytes(streamableBytes() - previouslyStreamable);
            return streamWindow;
        }

        /**
         * Returns the maximum writable window (minimum of the stream and connection windows).
         */
        int writableWindow() {
            return min(streamWindow, connectionWindow());
        }

        /**
         * Returns the number of pending bytes for this node that will fit within the
         * {@link #streamWindow}. This is used for the priority algorithm to determine the aggregate
         * total for {@link #priorityBytes} at each node. Each node only takes into account it's
         * stream window so that when a change occurs to the connection window, these values need
         * not change (i.e. no tree traversal is required).
         */
        int streamableBytes() {
            return max(0, min(pendingBytes, streamWindow));
        }

        /**
         * The aggregate total of all {@link #streamableBytes()} for subtree rooted at this node.
         */
        int priorityBytes() {
            return priorityBytes;
        }

        /**
         * Used by the priority algorithm to allocate bytes to this stream.
         */
        void allocatePriorityBytes(int bytes) {
            allocatedPriorityBytes += bytes;
        }

        /**
         * Used by the priority algorithm to get the intermediate allocation of bytes to this
         * stream.
         */
        int allocatedPriorityBytes() {
            return allocatedPriorityBytes;
        }

        /**
         * Used by the priority algorithm to determine the number of writable bytes that have not
         * yet been allocated.
         */
        int unallocatedPriorityBytes() {
            return priorityBytes - allocatedPriorityBytes;
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
            if (streamWindow > 0) {
                return pendingWriteQueue.peek();
            }
            return null;
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

            int maxBytes = min(bytes, writableWindow());
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
                maxBytes = min(bytes - bytesWritten, writableWindow());
            }
            return bytesWritten;
        }

        /**
         * Increments the number of pending bytes for this node. If there was any change to the
         * number of bytes that fit into the stream window, then {@link #incrementPriorityBytes} to
         * recursively update this branch of the priority tree.
         */
        private void incrementPendingBytes(int numBytes) {
            int previouslyStreamable = streamableBytes();
            pendingBytes += numBytes;
            incrementPriorityBytes(streamableBytes() - previouslyStreamable);
        }

        /**
         * Recursively increments the priority bytes for this branch in the priority tree starting
         * at the current node.
         */
        private void incrementPriorityBytes(int numBytes) {
            if (numBytes != 0) {
                priorityBytes += numBytes;
                if (!priority.isRoot()) {
                    priority.parent().data().incrementPriorityBytes(numBytes);
                }
            }
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

                    // Increment the number of pending bytes for this stream.
                    incrementPendingBytes(data.readableBytes());
                }
            }

            /**
             * Writes the frame and decrements the stream and connection window sizes. If the frame
             * is in the pending queue, the written bytes are removed from this branch of the
             * priority tree.
             */
            void write() throws Http2Exception {
                int dataLength = data.readableBytes();
                connectionFlow.incrementStreamWindow(-dataLength);
                incrementStreamWindow(-dataLength);
                writer.writeFrame(priority.streamId(), data, padding, endStream, endSegment,
                        compressed);
                decrementPendingBytes(dataLength);
            }

            /**
             * Discards this frame, writing an error. If this frame is in the pending queue, the
             * unwritten bytes are removed from this branch of the priority tree.
             */
            void writeError(Http2Exception cause) {
                decrementPendingBytes(data.readableBytes());
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
                maxBytes = min(maxBytes, data.readableBytes());
                Frame frame = new Frame(data.readSlice(maxBytes).retain(), 0, false, false, compressed,
                        writer);
                decrementPendingBytes(maxBytes);
                return frame;
            }

            /**
             * If this frame is in the pending queue, decrements the number of pending bytes for the
             * stream.
             */
            void decrementPendingBytes(int bytes) {
                if (enqueued) {
                    incrementPendingBytes(-bytes);
                }
            }
        }
    }

    /**
     * Controls garbage collection for priorities that have been marked for removal.
     */
    private final class GarbageCollector implements Runnable {

        private final Queue<Priority<FlowState>> garbage;
        private long lastGarbageCollection;

        GarbageCollector() {
            garbage = new ArrayDeque<Priority<FlowState>>();
        }

        void add(Priority<FlowState> priority) {
            priority.data().removalTime = System.nanoTime();
            garbage.add(priority);
        }

        /**
         * Removes any priorities from the tree that were marked for removal greater than
         * {@link #GARBAGE_COLLECTION_INTERVAL} milliseconds ago. Garbage collection will run at most on
         * the interval {@link #GARBAGE_COLLECTION_INTERVAL}, so calling it more frequently will have no
         * effect.
         */
        @Override
        public void run() {
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
    }
}
