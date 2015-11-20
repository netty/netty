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

import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A {@link StreamByteDistributor} that ignores stream priority and uniformly allocates bytes to all
 * streams. This class uses a minimum chunk size that will be allocated to each stream. While
 * fewer streams may be written to in each call to {@link #distribute(int, Writer)}, doing this
 * should improve the goodput on each written stream.
 */
public final class UniformStreamByteDistributor implements StreamByteDistributor {
    static final int DEFAULT_MIN_ALLOCATION_CHUNK = 1024;

    private final Http2Connection.PropertyKey stateKey;
    private final Deque<State> queue = new ArrayDeque<State>(4);

    /**
     * The minimum number of bytes that we will attempt to allocate to a stream. This is to
     * help improve goodput on a per-stream basis.
     */
    private int minAllocationChunk = DEFAULT_MIN_ALLOCATION_CHUNK;
    private long totalStreamableBytes;

    public UniformStreamByteDistributor(Http2Connection connection) {
        checkNotNull(connection, "connection");

        // Add a state for the connection.
        stateKey = connection.newKey();
        Http2Stream connectionStream = connection.connectionStream();
        connectionStream.setProperty(stateKey, new State(connectionStream));

        // Register for notification of new streams.
        connection.addListener(new Http2ConnectionAdapter() {
            @Override
            public void onStreamAdded(Http2Stream stream) {
                stream.setProperty(stateKey, new State(stream));
            }

            @Override
            public void onStreamClosed(Http2Stream stream) {
                state(stream).close();
            }
        });
    }

    /**
     * Sets the minimum allocation chunk that will be allocated to each stream. Defaults to 1KiB.
     *
     * @param minAllocationChunk the minimum number of bytes that will be allocated to each stream.
     * Must be > 0.
     */
    public void minAllocationChunk(int minAllocationChunk) {
        if (minAllocationChunk <= 0) {
            throw new IllegalArgumentException("minAllocationChunk must be > 0");
        }
        this.minAllocationChunk = minAllocationChunk;
    }

    @Override
    public void updateStreamableBytes(StreamState streamState) {
        State state = state(streamState.stream());
        state.updateStreamableBytes(streamState.streamableBytes(), streamState.hasFrame());
    }

    @Override
    public boolean distribute(int maxBytes, Writer writer) throws Http2Exception {
        checkNotNull(writer, "writer");

        final int size = queue.size();
        if (size == 0) {
            return totalStreamableBytes > 0;
        }

        final int chunkSize = max(minAllocationChunk, maxBytes / size);

        State state = queue.pollFirst();
        do {
            state.enqueued = false;
            if (state.streamableBytes > 0 && maxBytes == 0) {
                // Stop at the first state that can't send. Add this state back to the head of
                // the queue. Note that empty frames at the head of the queue will always be
                // written.
                queue.addFirst(state);
                state.enqueued = true;
                break;
            }

            // Allocate as much data as we can for this stream.
            int chunk = min(chunkSize, min(maxBytes, state.streamableBytes));
            maxBytes -= chunk;

            // Write the allocated bytes and enqueue as necessary.
            state.write(chunk, writer);
        } while ((state = queue.pollFirst()) != null);

        return totalStreamableBytes > 0;
    }

    private State state(Http2Stream stream) {
        return checkNotNull(stream, "stream").getProperty(stateKey);
    }

    /**
     * The remote flow control state for a single stream.
     */
    private final class State {
        final Http2Stream stream;
        int streamableBytes;
        boolean enqueued;

        State(Http2Stream stream) {
            this.stream = stream;
        }

        void updateStreamableBytes(int newStreamableBytes, boolean hasFrame) {
            assert hasFrame || newStreamableBytes == 0;

            int delta = newStreamableBytes - streamableBytes;
            if (delta != 0) {
                streamableBytes = newStreamableBytes;
                totalStreamableBytes += delta;
            }
            if (hasFrame) {
                // It's not in the queue but has data to send, add it.
                addToQueue();
            }
        }

        /**
         * Write any allocated bytes for the given stream and updates the streamable bytes,
         * assuming all of the bytes will be written.
         */
        void write(int numBytes, Writer writer) throws Http2Exception {
            // Update the streamable bytes, assuming that all the bytes will be written.
            int newStreamableBytes = streamableBytes - numBytes;
            updateStreamableBytes(newStreamableBytes, newStreamableBytes > 0);

            try {
                // Write the allocated bytes.
                writer.write(stream, numBytes);
            } catch (Throwable t) {
                throw connectionError(INTERNAL_ERROR, t, "byte distribution write error");
            }
        }

        void addToQueue() {
            if (!enqueued) {
                enqueued = true;
                queue.addLast(this);
            }
        }

        void removeFromQueue() {
            if (enqueued) {
                enqueued = false;
                queue.remove(this);
            }
        }

        void close() {
            // Remove this state from the queue.
            removeFromQueue();

            // Clear the streamable bytes.
            updateStreamableBytes(0, false);
        }
    }
}
