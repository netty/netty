/*
 * Copyright 2015 The Netty Project
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

import java.util.Arrays;

import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * A {@link StreamByteDistributor} that implements the HTTP/2 priority tree algorithm for allocating
 * bytes for all streams in the connection.
 */
public final class PriorityStreamByteDistributor implements StreamByteDistributor {
    private final Http2Connection connection;
    private final Http2Connection.PropertyKey stateKey;
    private final WriteVisitor writeVisitor = new WriteVisitor();

    public PriorityStreamByteDistributor(Http2Connection connection) {
        this.connection = checkNotNull(connection, "connection");

        // Add a state for the connection.
        stateKey = connection.newKey();
        connection.connectionStream().setProperty(stateKey,
                new PriorityState(connection.connectionStream()));

        // Register for notification of new streams.
        connection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void onStreamAdded(Http2Stream stream) {
                stream.setProperty(stateKey, new PriorityState(stream));
            }

            @Override
            public void onStreamClosed(Http2Stream stream) {
                state(stream).close();
            }

            @Override
            public void onPriorityTreeParentChanged(Http2Stream stream, Http2Stream oldParent) {
                Http2Stream parent = stream.parent();
                if (parent != null) {
                    long delta = state(stream).unallocatedStreamableBytesForTree();
                    if (delta != 0) {
                        state(parent).unallocatedStreamableBytesForTreeChanged(delta);
                    }
                }
            }

            @Override
            public void onPriorityTreeParentChanging(Http2Stream stream, Http2Stream newParent) {
                Http2Stream parent = stream.parent();
                if (parent != null) {
                    long delta = state(stream).unallocatedStreamableBytesForTree();
                    if (delta != 0) {
                        state(parent).unallocatedStreamableBytesForTreeChanged(-delta);
                    }
                }
            }
        });
    }

    @Override
    public void updateStreamableBytes(StreamState streamState) {
        state(streamState.stream()).updateStreamableBytes(streamState.streamableBytes(),
                streamState.hasFrame());
    }

    @Override
    public boolean distribute(int maxBytes, Writer writer) throws Http2Exception {
        checkNotNull(writer, "writer");
        if (maxBytes > 0) {
            allocateBytesForTree(connection.connectionStream(), maxBytes);
        }

        // Need to write even if maxBytes == 0 in order to handle the case of empty frames.
        writeVisitor.writeAllocatedBytes(writer);

        return state(connection.connectionStream()).unallocatedStreamableBytesForTree() > 0;
    }

    /**
     * For testing only.
     */
    int unallocatedStreamableBytes(Http2Stream stream) {
        return state(stream).unallocatedStreamableBytes();
    }

    /**
     * For testing only.
     */
    long unallocatedStreamableBytesForTree(Http2Stream stream) {
        return state(stream).unallocatedStreamableBytesForTree();
    }

    /**
     * This will allocate bytes by stream weight and priority for the entire tree rooted at {@code
     * parent}, but does not write any bytes. The connection window is generally distributed amongst
     * siblings according to their weight, however we need to ensure that the entire connection
     * window is used (assuming streams have >= connection window bytes to send) and we may need
     * some sort of rounding to accomplish this.
     *
     * @param parent The parent of the tree.
     * @param connectionWindowSize The connection window this is available for use at this point in
     * the tree.
     * @return The number of bytes actually allocated.
     */
    private int allocateBytesForTree(Http2Stream parent, int connectionWindowSize) {
        PriorityState state = state(parent);
        if (state.unallocatedStreamableBytesForTree() <= 0) {
            return 0;
        }
        // If the number of streamable bytes for this tree will fit in the connection window
        // then there is no need to prioritize the bytes...everyone sends what they have
        if (state.unallocatedStreamableBytesForTree() <= connectionWindowSize) {
            SimpleChildFeeder childFeeder = new SimpleChildFeeder(connectionWindowSize);
            forEachChild(parent, childFeeder);
            return childFeeder.bytesAllocated;
        }

        ChildFeeder childFeeder = new ChildFeeder(parent, connectionWindowSize);
        // Iterate once over all children of this parent and try to feed all the children.
        forEachChild(parent, childFeeder);

        // Now feed any remaining children that are still hungry until the connection
        // window collapses.
        childFeeder.feedHungryChildren();

        return childFeeder.bytesAllocated;
    }

    private void forEachChild(Http2Stream parent, Http2StreamVisitor childFeeder) {
        try {
            parent.forEachChild(childFeeder);
        } catch (Http2Exception e) {
            // Should never happen since the feeder doesn't throw.
            throw new IllegalStateException(e);
        }
    }

    private PriorityState state(Http2Stream stream) {
        return checkNotNull(stream, "stream").getProperty(stateKey);
    }

    /**
     * A {@link Http2StreamVisitor} that performs the HTTP/2 priority algorithm to distribute the
     * available connection window appropriately to the children of a given stream.
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
        public boolean visit(Http2Stream child) {
            // In order to make progress toward the connection window due to possible rounding errors, we make sure
            // that each stream (with data to send) is given at least 1 byte toward the connection window.
            int connectionWindowChunk =
                    max(1, (int) (connectionWindow * (child.weight() / (double) totalWeight)));
            int bytesForTree = min(nextConnectionWindow, connectionWindowChunk);

            PriorityState state = state(child);
            int bytesForChild = min(state.unallocatedStreamableBytes(), bytesForTree);

            // Allocate the bytes to this child.
            if (bytesForChild > 0) {
                state.allocate(bytesForChild);
                bytesAllocated += bytesForChild;
                nextConnectionWindow -= bytesForChild;
                bytesForTree -= bytesForChild;
            }

            // Allocate any remaining bytes to the children of this stream.
            if (bytesForTree > 0) {
                int childBytesAllocated = allocateBytesForTree(child, bytesForTree);
                bytesAllocated += childBytesAllocated;
                nextConnectionWindow -= childBytesAllocated;
            }

            if (nextConnectionWindow > 0) {
                // If this subtree still wants to send then it should be re-considered to take bytes that are unused by
                // sibling nodes. This is needed because we don't yet know if all the peers will be able to use all of
                // their "fair share" of the connection window, and if they don't use it then we should divide their
                // unused shared up for the peers who still want to send.
                if (state.unallocatedStreamableBytesForTree() > 0) {
                    stillHungry(child);
                }
                return true;
            }

            return false;
        }

        void feedHungryChildren() {
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
            nextTotalWeight += child.weight();
        }

        /**
         * Ensures that the {@link #stillHungry} array is properly sized to hold the given index.
         */
        void ensureSpaceIsAllocated(int index) {
            if (stillHungry == null) {
                // Initial size is 1/4 the number of children. Clipping the minimum at 2, which will over allocate if
                // maxSize == 1 but if this was true we shouldn't need to re-allocate because the 1 child should get
                // all of the available connection window.
                stillHungry = new Http2Stream[max(2, maxSize >>> 2)];
            } else if (index == stillHungry.length) {
                // Grow the array by a factor of 2.
                stillHungry = Arrays.copyOf(stillHungry, min(maxSize, stillHungry.length << 1));
            }
        }
    }

    /**
     * A simplified version of {@link ChildFeeder} that is only used when all streamable bytes fit
     * within the available connection window.
     */
    private final class SimpleChildFeeder implements Http2StreamVisitor {
        int bytesAllocated;
        int connectionWindow;

        SimpleChildFeeder(int connectionWindow) {
            this.connectionWindow = connectionWindow;
        }

        @Override
        public boolean visit(Http2Stream child) {
            PriorityState childState = state(child);
            int bytesForChild = childState.unallocatedStreamableBytes();

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
    private final class PriorityState {
        final Http2Stream stream;
        boolean hasFrame;
        int streamableBytes;
        int allocated;
        long unallocatedStreamableBytesForTree;

        PriorityState(Http2Stream stream) {
            this.stream = stream;
        }

        /**
         * Recursively increments the {@link #unallocatedStreamableBytesForTree()} for this branch in
         * the priority tree starting at the current node.
         */
        void unallocatedStreamableBytesForTreeChanged(long delta) {
            unallocatedStreamableBytesForTree += delta;
            if (!stream.isRoot()) {
                state(stream.parent()).unallocatedStreamableBytesForTreeChanged(delta);
            }
        }

        void allocate(int bytes) {
            allocated += bytes;

            if (bytes != 0) {
                // Also artificially reduce the streamable bytes for this tree to give the appearance
                // that the data has been written. This will be restored before the allocated bytes are
                // actually written.
                unallocatedStreamableBytesForTreeChanged(-bytes);
            }
        }

        /**
         * Reset the number of bytes that have been allocated to this stream by the priority
         * algorithm.
         */
        void resetAllocated() {
            allocate(-allocated);
        }

        void updateStreamableBytes(int newStreamableBytes, boolean hasFrame) {
            assert hasFrame || newStreamableBytes == 0;
            this.hasFrame = hasFrame;

            int delta = newStreamableBytes - streamableBytes;
            if (delta != 0) {
                streamableBytes = newStreamableBytes;

                // Update this branch of the priority tree if the streamable bytes have changed for this node.
                unallocatedStreamableBytesForTreeChanged(delta);
            }
        }

        void close() {
            // Unallocate all bytes.
            resetAllocated();

            // Clear the streamable bytes.
            updateStreamableBytes(0, false);
        }

        boolean hasFrame() {
            return hasFrame;
        }

        int unallocatedStreamableBytes() {
            return streamableBytes - allocated;
        }

        long unallocatedStreamableBytesForTree() {
            return unallocatedStreamableBytesForTree;
        }
    }

    /**
     * A connection stream visitor that delegates to the user provided visitor.
     */
    private final class WriteVisitor implements Http2StreamVisitor {
        private boolean iterating;
        private Writer writer;

        void writeAllocatedBytes(Writer writer) throws Http2Exception {
            if (iterating) {
                throw connectionError(INTERNAL_ERROR, "byte distribution re-entry error");
            }
            this.writer = writer;
            try {
                iterating = true;
                connection.forEachActiveStream(this);
            } finally {
                iterating = false;
            }
        }

        @Override
        public boolean visit(Http2Stream stream) throws Http2Exception {
            PriorityState state = state(stream);
            int allocated = state.allocated;

            // Unallocate all bytes for this stream.
            state.resetAllocated();

            try {
                // Write the allocated bytes.
                writer.write(stream, allocated);
            } catch (Throwable t) { // catch Throwable in case any unchecked re-throw tricks are used.
                // Stop calling the visitor and close the connection as exceptions from the writer are not supported.
                // If we don't close the connection there is risk that our internal state may be corrupted.
                throw connectionError(INTERNAL_ERROR, t, "byte distribution write error");
            }

            // We have to iterate across all streams to ensure that we reset the allocated bytes.
            return true;
        }
    }
}
