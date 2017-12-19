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

import io.netty.util.internal.UnstableApi;

/**
 * An object (used by remote flow control) that is responsible for distributing the bytes to be
 * written across the streams in the connection.
 */
@UnstableApi
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
         * Get the amount of bytes this stream has pending to send. The actual amount written must not exceed
         * {@link #windowSize()}!
         * @return The amount of bytes this stream has pending to send.
         * @see Http2CodecUtil#streamableBytes(StreamState)
         */
        long pendingBytes();

        /**
         * Indicates whether or not there are frames pending for this stream.
         */
        boolean hasFrame();

        /**
         * The size (in bytes) of the stream's flow control window. The amount written must not exceed this amount!
         * <p>A {@link StreamByteDistributor} needs to know the stream's window size in order to avoid allocating bytes
         * if the window size is negative. The window size being {@code 0} may also be significant to determine when if
         * an stream has been given a chance to write an empty frame, and also enables optimizations like not writing
         * empty frames in some situations (don't write headers until data can also be written).
         * @return the size of the stream's flow control window.
         * @see Http2CodecUtil#streamableBytes(StreamState)
         */
        int windowSize();
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
     * Explicitly update the dependency tree. This method is called independently of stream state changes.
     * @param childStreamId The stream identifier associated with the child stream.
     * @param parentStreamId The stream identifier associated with the parent stream. May be {@code 0},
     *                       to make {@code childStreamId} and immediate child of the connection.
     * @param weight The weight which is used relative to other child streams for {@code parentStreamId}. This value
     *               must be between 1 and 256 (inclusive).
     * @param exclusive If {@code childStreamId} should be the exclusive dependency of {@code parentStreamId}.
     */
    void updateDependencyTree(int childStreamId, int parentStreamId, short weight, boolean exclusive);

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
