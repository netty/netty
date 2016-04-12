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

import io.netty.util.internal.MathUtil;
import io.netty.util.internal.PriorityQueue;
import io.netty.util.internal.PriorityQueueNode;
import io.netty.util.internal.UnstableApi;

import java.util.Queue;

import static io.netty.handler.codec.http2.Http2CodecUtil.CONNECTION_STREAM_ID;
import static io.netty.handler.codec.http2.Http2CodecUtil.streamableBytes;
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Math.min;

/**
 * A {@link StreamByteDistributor} that is sensitive to stream priority and uses
 * <a href="https://en.wikipedia.org/wiki/Weighted_fair_queueing">Weighted Fair Queueing</a> approach for distributing
 * bytes.
 * <p>
 * Inspiration for this distributor was taken from Linux's
 * <a href="https://git.kernel.org/cgit/linux/kernel/git/stable/linux-stable.git/tree/Documentation/scheduler
 * /sched-design-CFS.txt">Completely Fair Scheduler</a>
 * to model the distribution of bytes to simulate an "ideal multi-tasking CPU", but in this case we are simulating
 * an "ideal multi-tasking NIC".
 * <p>
 * Each write operation will use the {@link #allocationQuantum(int)} to know how many more bytes should be allocated
 * relative to the next stream which wants to write. This is to balance fairness while also considering goodput.
 */
@UnstableApi
public final class WeightedFairQueueByteDistributor implements StreamByteDistributor {
    private final Http2Connection.PropertyKey stateKey;
    private final State connectionState;
    /**
     * The minimum number of bytes that we will attempt to allocate to a stream. This is to
     * help improve goodput on a per-stream basis.
     */
    private int allocationQuantum = 1024;

    public WeightedFairQueueByteDistributor(Http2Connection connection) {
        stateKey = connection.newKey();
        Http2Stream connectionStream = connection.connectionStream();
        connectionStream.setProperty(stateKey, connectionState = new State(connectionStream, 16));

        // Register for notification of new streams.
        connection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void onStreamAdded(Http2Stream stream) {
                stream.setProperty(stateKey, new State(stream));
            }

            @Override
            public void onWeightChanged(Http2Stream stream, short oldWeight) {
                Http2Stream parent;
                if (state(stream).activeCountForTree != 0 && (parent = stream.parent()) != null) {
                    state(parent).totalQueuedWeights += stream.weight() - oldWeight;
                }
            }

            @Override
            public void onStreamClosed(Http2Stream stream) {
                state(stream).close();
            }

            @Override
            public void onPriorityTreeParentChanged(Http2Stream stream, Http2Stream oldParent) {
                Http2Stream parent = stream.parent();
                if (parent != null) {
                    State state = state(stream);
                    if (state.activeCountForTree != 0) {
                        State pState = state(parent);
                        pState.offerAndInitializePseudoTime(state);
                        pState.isActiveCountChangeForTree(state.activeCountForTree);
                    }
                }
            }

            @Override
            public void onPriorityTreeParentChanging(Http2Stream stream, Http2Stream newParent) {
                Http2Stream parent = stream.parent();
                if (parent != null) {
                    State state = state(stream);
                    if (state.activeCountForTree != 0) {
                        State pState = state(parent);
                        pState.remove(state);
                        pState.isActiveCountChangeForTree(-state.activeCountForTree);
                    }
                }
            }
        });
    }

    @Override
    public void updateStreamableBytes(StreamState state) {
        state(state.stream()).updateStreamableBytes(streamableBytes(state),
                                                    state.hasFrame() && state.windowSize() >= 0);
    }

    @Override
    public boolean distribute(int maxBytes, Writer writer) throws Http2Exception {
        checkNotNull(writer, "writer");

        // As long as there is some active frame we should write at least 1 time.
        if (connectionState.activeCountForTree == 0) {
            return false;
        }

        // The goal is to write until we write all the allocated bytes or are no longer making progress.
        // We still attempt to write even after the number of allocated bytes has been exhausted to allow empty frames
        // to be sent. Making progress means the active streams rooted at the connection stream has changed.
        int oldIsActiveCountForTree;
        do {
            oldIsActiveCountForTree = connectionState.activeCountForTree;
            // connectionState will never be active, so go right to its children.
            maxBytes -= distributeToChildren(maxBytes, writer, connectionState);
        } while (connectionState.activeCountForTree != 0 &&
                (maxBytes > 0 || oldIsActiveCountForTree != connectionState.activeCountForTree));

        return connectionState.activeCountForTree != 0;
    }

    /**
     * Sets the amount of bytes that will be allocated to each stream. Defaults to 1KiB.
     * @param allocationQuantum the amount of bytes that will be allocated to each stream. Must be &gt; 0.
     */
    public void allocationQuantum(int allocationQuantum) {
        if (allocationQuantum <= 0) {
            throw new IllegalArgumentException("allocationQuantum must be > 0");
        }
        this.allocationQuantum = allocationQuantum;
    }

    private int distribute(int maxBytes, Writer writer, State state) throws Http2Exception {
        if (state.active) {
            int nsent = min(maxBytes, state.streamableBytes);
            state.write(nsent, writer);
            if (nsent == 0 && maxBytes != 0) {
                // If a stream sends zero bytes, then we gave it a chance to write empty frames and it is now
                // considered inactive until the next call to updateStreamableBytes. This allows descendant streams to
                // be allocated bytes when the parent stream can't utilize them. This may be as a result of the
                // stream's flow control window being 0.
                state.updateStreamableBytes(state.streamableBytes, false);
            }
            return nsent;
        }

        return distributeToChildren(maxBytes, writer, state);
    }

    /**
     * It is a pre-condition that {@code state.poll()} returns a non-{@code null} value. This is a result of the way
     * the allocation algorithm is structured and can be explained in the following cases:
     * <h3>For the recursive case</h3>
     * If a stream has no children (in the allocation tree) than that node must be active or it will not be in the
     * allocation tree. If a node is active then it will not delegate to children and recursion ends.
     * <h3>For the initial case</h3>
     * We check connectionState.activeCountForTree == 0 before any allocation is done. So if the connection stream
     * has no active children we don't get into this method.
     */
    private int distributeToChildren(int maxBytes, Writer writer, State state) throws Http2Exception {
        long oldTotalQueuedWeights = state.totalQueuedWeights;
        State childState = state.poll();
        State nextChildState = state.peek();
        try {
            assert nextChildState == null || nextChildState.pseudoTimeToWrite >= childState.pseudoTimeToWrite :
                "nextChildState.pseudoTime(" + nextChildState.pseudoTimeToWrite + ") < " + " childState.pseudoTime(" +
                    childState.pseudoTimeToWrite + ")";
            int nsent = distribute(nextChildState == null ? maxBytes :
                            min(maxBytes, (int) min((nextChildState.pseudoTimeToWrite - childState.pseudoTimeToWrite) *
                                                childState.stream.weight() / oldTotalQueuedWeights + allocationQuantum,
                                                Integer.MAX_VALUE)
                               ),
                               writer,
                               childState);
            state.pseudoTime += nsent;
            childState.updatePseudoTime(state, nsent, oldTotalQueuedWeights);
            return nsent;
        } finally {
            // Do in finally to ensure the internal state is not corrupted if an exception is thrown.
            // The offer operation is delayed until we unroll up the recursive stack, so we don't have to remove from
            // the priority queue due to a write operation.
            if (childState.activeCountForTree != 0) {
                state.offer(childState);
            }
        }
    }

    private State state(Http2Stream stream) {
        return stream.getProperty(stateKey);
    }

    /**
     * For testing only!
     */
    int streamableBytes0(Http2Stream stream) {
        return state(stream).streamableBytes;
    }

    /**
     * The remote flow control state for a single stream.
     */
    private final class State implements PriorityQueueNode<State> {
        final Http2Stream stream;
        private final Queue<State> queue;
        int streamableBytes;
        /**
         * Count of nodes rooted at this sub tree with {@link #active} equal to {@code true}.
         */
        int activeCountForTree;
        private int priorityQueueIndex = INDEX_NOT_IN_QUEUE;
        /**
         * An estimate of when this node should be given the opportunity to write data.
         */
        long pseudoTimeToWrite;
        /**
         * A pseudo time maintained for immediate children to base their {@link pseudoTimeToSend} off of.
         */
        long pseudoTime;
        long totalQueuedWeights;
        boolean active;

        State(Http2Stream stream) {
            this(stream, 0);
        }

        State(Http2Stream stream, int initialSize) {
            this.stream = stream;
            queue = new PriorityQueue<State>(initialSize);
        }

        void write(int numBytes, Writer writer) throws Http2Exception {
            try {
                writer.write(stream, numBytes);
            } catch (Throwable t) {
                throw connectionError(INTERNAL_ERROR, t, "byte distribution write error");
            }
        }

        void isActiveCountChangeForTree(int increment) {
            assert activeCountForTree + increment >= 0;
            activeCountForTree += increment;
            if (!stream.isRoot()) {
                State pState = state(stream.parent());
                if (activeCountForTree == 0) {
                    pState.remove(this);
                } else if (activeCountForTree - increment == 0) { // if frame count was 0 but is now not, then queue.
                    pState.offerAndInitializePseudoTime(this);
                }
                pState.isActiveCountChangeForTree(increment);
            }
        }

        void updateStreamableBytes(int newStreamableBytes, boolean isActive) {
            if (this.active != isActive) {
                isActiveCountChangeForTree(isActive ? 1 : -1);
                this.active = isActive;
            }

            streamableBytes = newStreamableBytes;
        }

        /**
         * Assumes the parents {@link #totalQueuedWeights} includes this node's weight.
         */
        void updatePseudoTime(State parentState, int nsent, long totalQueuedWeights) {
            assert stream.id() != CONNECTION_STREAM_ID && nsent >= 0;
            // If the current pseudoTimeToSend is greater than parentState.pseudoTime then we previously over accounted
            // and should use parentState.pseudoTime.
            pseudoTimeToWrite = min(pseudoTimeToWrite, parentState.pseudoTime) +
                                nsent * totalQueuedWeights / stream.weight();
        }

        /**
         * The concept of pseudoTime can be influenced by priority tree manipulations or if a stream goes from "active"
         * to "non-active". This method accounts for that by initializing the {@link #pseudoTimeToWrite} for
         * {@code state} to {@link #pseudoTime} of this node and then calls {@link #offer(State)}.
         */
        void offerAndInitializePseudoTime(State state) {
            state.pseudoTimeToWrite = pseudoTime;
            offer(state);
        }

        void offer(State state) {
            queue.offer(state);
            totalQueuedWeights += state.stream.weight();
        }

        /**
         * Must only be called if the queue is non-empty!
         */
        State poll() {
            State state = queue.poll();
            // This method is only ever called if the queue is non-empty.
            totalQueuedWeights -= state.stream.weight();
            return state;
        }

        void remove(State state) {
            if (queue.remove(state)) {
                totalQueuedWeights -= state.stream.weight();
            }
        }

        State peek() {
            return queue.peek();
        }

        void close() {
            updateStreamableBytes(0, false);
        }

        @Override
        public int compareTo(State o) {
            return MathUtil.compare(pseudoTimeToWrite, o.pseudoTimeToWrite);
        }

        @Override
        public int priorityQueueIndex() {
            return priorityQueueIndex;
        }

        @Override
        public void priorityQueueIndex(int i) {
            priorityQueueIndex = i;
        }
    }
}
