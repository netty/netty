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
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.FLOW_CONTROL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.streamError;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Basic implementation of {@link Http2OutboundFlowController}.
 */
public class DefaultHttp2OutboundFlowController implements Http2OutboundFlowController {

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
    private final Http2FrameWriter frameWriter;
    private int initialWindowSize = DEFAULT_WINDOW_SIZE;
    private ChannelHandlerContext ctx;
    private boolean frameSent;

    public DefaultHttp2OutboundFlowController(Http2Connection connection, Http2FrameWriter frameWriter) {
        this.connection = checkNotNull(connection, "connection");
        this.frameWriter = checkNotNull(frameWriter, "frameWriter");

        // Add a flow state for the connection.
        connection.connectionStream().outboundFlow(new OutboundFlowState(connection.connectionStream()));

        // Register for notification of new streams.
        connection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void streamAdded(Http2Stream stream) {
                // Just add a new flow state to the stream.
                stream.outboundFlow(new OutboundFlowState(stream));
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
                    state(parent).incrementStreamableBytesForTree(state(stream).streamableBytesForTree());
                }
            }

            @Override
            public void priorityTreeParentChanging(Http2Stream stream, Http2Stream newParent) {
                Http2Stream parent = stream.parent();
                if (parent != null) {
                    state(parent).incrementStreamableBytesForTree(-state(stream).streamableBytesForTree());
                }
            }
        });
    }

    @Override
    public void initialOutboundWindowSize(int newWindowSize) throws Http2Exception {
        if (newWindowSize < 0) {
            throw new IllegalArgumentException("Invalid initial window size: " + newWindowSize);
        }

        int delta = newWindowSize - initialWindowSize;
        initialWindowSize = newWindowSize;
        for (Http2Stream stream : connection.activeStreams()) {
            // Verify that the maximum value is not exceeded by this change.
            OutboundFlowState state = state(stream);
            state.incrementStreamWindow(delta);
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
            connectionState().incrementStreamWindow(delta);
            writePendingBytes();
        } else {
            // Update the stream window and write any pending frames for the stream.
            OutboundFlowState state = stateOrFail(streamId);
            state.incrementStreamWindow(delta);
            frameSent = false;
            state.writeBytes(state.writableWindow());
            if (frameSent) {
                flush();
            }
        }
    }

    @Override
    public ChannelFuture writeData(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
            boolean endStream, ChannelPromise promise) {
        checkNotNull(ctx, "ctx");
        checkNotNull(promise, "promise");
        checkNotNull(data, "data");
        if (this.ctx != null && this.ctx != ctx) {
            throw new IllegalArgumentException("Writing data from multiple ChannelHandlerContexts is not supported");
        }
        if (padding < 0) {
            throw new IllegalArgumentException("padding must be >= 0");
        }
        if (streamId <= 0) {
            throw new IllegalArgumentException("streamId must be >= 0");
        }

        // Save the context. We'll use this later when we write pending bytes.
        this.ctx = ctx;

        try {
            OutboundFlowState state = stateOrFail(streamId);

            int window = state.writableWindow();
            boolean framesAlreadyQueued = state.hasFrame();

            OutboundFlowState.Frame frame = state.newFrame(promise, data, padding, endStream);
            if (!framesAlreadyQueued && window >= frame.size()) {
                // Window size is large enough to send entire data frame
                frame.write();
                ctx.flush();
                return promise;
            }

            // Enqueue the frame to be written when the window size permits.
            frame.enqueue();

            if (framesAlreadyQueued || window <= 0) {
                // Stream already has frames pending or is stalled, don't send anything now.
                return promise;
            }

            // Create and send a partial frame up to the window size.
            frame.split(window).write();
            ctx.flush();
        } catch (Http2Exception e) {
            promise.setFailure(e);
        }
        return promise;
    }

    @Override
    public ChannelFuture lastWriteForStream(int streamId) {
        OutboundFlowState state = state(streamId);
        return state != null ? state.lastNewFrame() : null;
    }

    private static OutboundFlowState state(Http2Stream stream) {
        return (OutboundFlowState) stream.outboundFlow();
    }

    private OutboundFlowState connectionState() {
        return state(connection.connectionStream());
    }

    private OutboundFlowState state(int streamId) {
        Http2Stream stream = connection.stream(streamId);
        return stream != null ? state(stream) : null;
    }

    /**
     * Attempts to get the {@link OutboundFlowState} for the given stream. If not available, raises a
     * {@code PROTOCOL_ERROR}.
     */
    private OutboundFlowState stateOrFail(int streamId) throws Http2Exception {
        OutboundFlowState state = state(streamId);
        if (state == null) {
            throw connectionError(PROTOCOL_ERROR, "Missing flow control window for stream: %d", streamId);
        }
        return state;
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
        if (ctx != null) {
            ctx.flush();
        }
    }

    /**
     * Writes as many pending bytes as possible, according to stream priority.
     */
    private void writePendingBytes() throws Http2Exception {
        Http2Stream connectionStream = connection.connectionStream();
        int connectionWindow = state(connectionStream).window();

        if (connectionWindow > 0) {
            frameSent = false;
            writeChildren(connectionStream, connectionWindow);
            for (Http2Stream stream : connection.activeStreams()) {
                writeChildNode(state(stream));
            }
            if (frameSent) {
                flush();
            }
        }
    }

    /**
     * Write the children of {@code parent} in the priority tree. This will allocate bytes by stream weight.
     * @param parent The parent of the nodes which will be written.
     * @param connectionWindow The connection window this is available for use at this point in the tree.
     * @return An object summarizing the write and allocation results.
     */
    private int writeChildren(Http2Stream parent, int connectionWindow) {
        OutboundFlowState state = state(parent);
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
    private static void writeChildNode(OutboundFlowState state) {
        state.writeBytes(state.allocated());
        state.resetAllocated();
    }

    /**
     * The outbound flow control state for a single stream.
     */
    final class OutboundFlowState implements Http2FlowState {
        private final Queue<Frame> pendingWriteQueue;
        private final Http2Stream stream;
        private int window = initialWindowSize;
        private int pendingBytes;
        private int streamableBytesForTree;
        private int allocated;
        private ChannelFuture lastNewFrame;

        private OutboundFlowState(Http2Stream stream) {
            this.stream = stream;
            pendingWriteQueue = new ArrayDeque<Frame>(2);
        }

        @Override
        public int window() {
            return window;
        }

        /**
         * Increment the number of bytes allocated to this stream by the priority algorithm
         */
        private void allocate(int bytes) {
            allocated += bytes;
        }

        /**
         * Gets the number of bytes that have been allocated to this stream by the priority algorithm.
         */
        private int allocated() {
            return allocated;
        }

        /**
         * Reset the number of bytes that have been allocated to this stream by the priority algorithm.
         */
        private void resetAllocated() {
            allocated = 0;
        }

        /**
         * Increments the flow control window for this stream by the given delta and returns the new value.
         */
        private int incrementStreamWindow(int delta) throws Http2Exception {
            if (delta > 0 && Integer.MAX_VALUE - delta < window) {
                throw streamError(stream.id(), FLOW_CONTROL_ERROR,
                        "Window size overflow for stream: %d", stream.id());
            }
            int previouslyStreamable = streamableBytes();
            window += delta;

            // Update this branch of the priority tree if the streamable bytes have changed for this node.
            int streamableDelta = streamableBytes() - previouslyStreamable;
            incrementStreamableBytesForTree(streamableDelta);
            return window;
        }

        /**
         * Returns the future for the last new frame created for this stream.
         */
        ChannelFuture lastNewFrame() {
            return lastNewFrame;
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
         * Creates a new frame with the given values but does not add it to the pending queue.
         */
        private Frame newFrame(final ChannelPromise promise, ByteBuf data, int padding, boolean endStream) {
            // Store this as the future for the most recent write attempt.
            lastNewFrame = promise;
            return new Frame(new SimplePromiseAggregator(promise), data, padding, endStream);
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
        private Frame peek() {
            return pendingWriteQueue.peek();
        }

        /**
         * Clears the pending queue and writes errors for each remaining frame.
         */
        private void clear() {
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
        private int writeBytes(int bytes) {
            if (!stream.localSideOpen()) {
                return 0;
            }

            int bytesAttempted = 0;
            int maxBytes = min(bytes, writableWindow());
            while (hasFrame()) {
                Frame pendingWrite = peek();
                if (maxBytes >= pendingWrite.size()) {
                    // Window size is large enough to send entire data frame
                    bytesAttempted += pendingWrite.size();
                    pendingWrite.write();
                } else if (maxBytes <= 0) {
                    // No data from the current frame can be written - we're done.
                    // We purposely check this after first testing the size of the
                    // pending frame to properly handle zero-length frame.
                    break;
                } else {
                    // We can send a partial frame
                    Frame partialFrame = pendingWrite.split(maxBytes);
                    bytesAttempted += partialFrame.size();
                    partialFrame.write();
                }

                // Update the threshold.
                maxBytes = min(bytes - bytesAttempted, writableWindow());
            }
            return bytesAttempted;
        }

        /**
         * Recursively increments the streamable bytes for this branch in the priority tree starting at the current
         * node.
         */
        private void incrementStreamableBytesForTree(int numBytes) {
            if (numBytes != 0) {
                streamableBytesForTree += numBytes;
                if (!stream.isRoot()) {
                    state(stream.parent()).incrementStreamableBytesForTree(numBytes);
                }
            }
        }

        /**
         * A wrapper class around the content of a data frame.
         */
        private final class Frame {
            final ByteBuf data;
            final boolean endStream;
            final SimplePromiseAggregator promiseAggregator;
            final ChannelPromise promise;
            int padding;
            boolean enqueued;

            Frame(SimplePromiseAggregator promiseAggregator, ByteBuf data, int padding, boolean endStream) {
                this.data = data;
                this.padding = padding;
                this.endStream = endStream;
                this.promiseAggregator = promiseAggregator;
                promise = ctx.newPromise();
                promiseAggregator.add(promise);
            }

            /**
             * Gets the total size (in bytes) of this frame including the data and padding.
             */
            int size() {
                return data.readableBytes() + padding;
            }

            void enqueue() {
                if (!enqueued) {
                    enqueued = true;
                    pendingWriteQueue.offer(this);

                    // Increment the number of pending bytes for this stream.
                    incrementPendingBytes(size());
                }
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
                incrementStreamableBytesForTree(delta);
            }

            /**
             * Writes the frame and decrements the stream and connection window sizes. If the frame is in the pending
             * queue, the written bytes are removed from this branch of the priority tree.
             * <p>
             * Note: this does not flush the {@link ChannelHandlerContext}.
             */
            void write() {
                // Using a do/while loop because if the buffer is empty we still need to call
                // the writer once to send the empty frame.
                final Http2FrameSizePolicy frameSizePolicy = frameWriter.configuration().frameSizePolicy();
                do {
                    int bytesToWrite = size();
                    int frameBytes = min(bytesToWrite, frameSizePolicy.maxFrameSize());
                    if (frameBytes == bytesToWrite) {
                        // All the bytes fit into a single HTTP/2 frame, just send it all.
                        try {
                            connectionState().incrementStreamWindow(-bytesToWrite);
                            incrementStreamWindow(-bytesToWrite);
                        } catch (Http2Exception e) {
                            // Should never get here since we're decrementing.
                            throw new AssertionError("Invalid window state when writing frame: " + e.getMessage());
                        }
                        frameWriter.writeData(ctx, stream.id(), data, padding, endStream, promise);
                        frameSent = true;
                        decrementPendingBytes(bytesToWrite);
                        if (enqueued) {
                            // It's enqueued - remove it from the head of the pending write queue.
                            pendingWriteQueue.remove();
                        }
                        return;
                    }

                    // Split a chunk that will fit into a single HTTP/2 frame and write it.
                    Frame frame = split(frameBytes);
                    frame.write();
                } while (size() > 0);
            }

            /**
             * Discards this frame, writing an error. If this frame is in the pending queue, the unwritten bytes are
             * removed from this branch of the priority tree.
             */
            void writeError(Http2Exception cause) {
                decrementPendingBytes(size());
                data.release();
                promise.setFailure(cause);
            }

            /**
             * Creates a new frame that is a view of this frame's data. The {@code maxBytes} are first split from the
             * data buffer. If not all the requested bytes are available, the remaining bytes are then split from the
             * padding (if available).
             *
             * @param maxBytes the maximum number of bytes that is allowed in the created frame.
             * @return the partial frame.
             */
            Frame split(int maxBytes) {
                // The requested maxBytes should always be less than the size of this frame.
                assert maxBytes < size() : "Attempting to split a frame for the full size.";

                // Get the portion of the data buffer to be split. Limit to the readable bytes.
                int dataSplit = min(maxBytes, data.readableBytes());

                // Split any remaining bytes from the padding.
                int paddingSplit = min(maxBytes - dataSplit, padding);

                ByteBuf splitSlice = data.readSlice(dataSplit).retain();
                padding -= paddingSplit;

                Frame frame = new Frame(promiseAggregator, splitSlice, paddingSplit, false);

                int totalBytesSplit = dataSplit + paddingSplit;
                decrementPendingBytes(totalBytesSplit);
                return frame;
            }

            /**
             * If this frame is in the pending queue, decrements the number of pending bytes for the stream.
             */
            void decrementPendingBytes(int bytes) {
                if (enqueued) {
                    incrementPendingBytes(-bytes);
                }
            }
        }
    }

    /**
     * Lightweight promise aggregator.
     */
    private class SimplePromiseAggregator {
        final ChannelPromise promise;
        final AtomicInteger awaiting = new AtomicInteger();
        final ChannelFutureListener listener = new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                } else {
                    if (awaiting.decrementAndGet() == 0) {
                        promise.trySuccess();
                    }
                }
            }
        };

        SimplePromiseAggregator(ChannelPromise promise) {
            this.promise = promise;
        }

        void add(ChannelPromise promise) {
            awaiting.incrementAndGet();
            promise.addListener(listener);
        }
    }
}
