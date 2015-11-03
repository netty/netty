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

/**
 * An object (used by remote flow control) that is responsible for distributing the bytes to be
 * written across the streams in the connection.
 */
public interface StreamByteDistributor {

    /**
     * State information for the stream, indicating the number of bytes that are currently
     * streamable. This is provided to the {@link #updateStreamableBytes(StreamState)} method.
     */
    interface StreamState {
        /**
         * Gets the stream this state is associated with.
         */
        Http2Stream stream();

        /**
         * Returns the number of pending bytes for this node that will fit within the stream flow
         * control window. This is used for the priority algorithm to determine the aggregate number
         * of bytes that can be written at each node. Each node only takes into account its stream
         * window so that when a change occurs to the connection window, these values need not
         * change (i.e. no tree traversal is required).
         */
        int streamableBytes();

        /**
         * Indicates whether or not there are frames pending for this stream.
         */
        boolean hasFrame();
    }

    /**
     * Object that performs the writing of the bytes that have been allocated for a stream.
     */
    interface Writer {
        /**
         * Writes the allocated bytes for this stream.
         * <p>
         * Any {@link Throwable} thrown from this method is considered a programming error.
         * A {@code GOAWAY} frame will be sent and the will be connection closed.
         * @param stream the stream for which to perform the write.
         * @param numBytes the number of bytes to write.
         */
        void write(Http2Stream stream, int numBytes);
    }

    /**
     * Called when the streamable bytes for a stream has changed. Until this
     * method is called for the first time for a give stream, the stream is assumed to have no
     * streamable bytes.
     */
    void updateStreamableBytes(StreamState state);

    /**
     * Distributes up to {@code maxBytes} to those streams containing streamable bytes and
     * iterates across those streams to write the appropriate bytes. Criteria for
     * traversing streams is undefined and it is up to the implementation to determine when to stop
     * at a given stream.
     *
     * <p>The streamable bytes are not automatically updated by calling this method. It is up to the
     * caller to indicate the number of bytes streamable after the write by calling
     * {@link #updateStreamableBytes(StreamState)}.
     *
     * @param maxBytes the maximum number of bytes to write.
     * @return {@code true} if there are still streamable bytes that have not yet been written,
     * otherwise {@code false}.
     * @throws Http2Exception If an internal exception occurs and internal connection state would otherwise be
     * corrupted.
     */
    boolean distribute(int maxBytes, Writer writer) throws Http2Exception;
}
