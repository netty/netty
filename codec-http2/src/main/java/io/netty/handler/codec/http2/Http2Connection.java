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

import java.util.Collection;

/**
 * Manager for the state of an HTTP/2 connection with the remote end-point.
 */
public interface Http2Connection {

    /**
     * Listener for life-cycle events for streams in this connection.
     */
    interface Listener {
        /**
         * Notifies the listener that the given stream was added to the connection. This stream may
         * not yet be active (i.e. open/half-closed).
         */
        void streamAdded(Http2Stream stream);

        /**
         * Notifies the listener that the given stream was made active (i.e. open in at least one
         * direction).
         */
        void streamActive(Http2Stream stream);

        /**
         * Notifies the listener that the given stream is now half-closed. The stream can be
         * inspected to determine which side is closed.
         */
        void streamHalfClosed(Http2Stream stream);

        /**
         * Notifies the listener that the given stream is now closed in both directions.
         */
        void streamInactive(Http2Stream stream);

        /**
         * Notifies the listener that the given stream has now been removed from the connection and
         * will no longer be returned via {@link Http2Connection#stream(int)}. The connection may
         * maintain inactive streams for some time before removing them.
         */
        void streamRemoved(Http2Stream stream);

        /**
         * Notifies the listener that the priority for the stream has changed. The parent of the
         * stream may have changed, so the previous parent is also provided.
         * <p>
         * Either this method or {@link #streamPrioritySubtreeChanged} will be called, but not both
         * for a single change. This method is called for simple priority changes. If a priority
         * change causes a circular dependency between the stream and one of its descendants, the
         * subtree must be restructured causing {@link #streamPrioritySubtreeChanged} instead.
         *
         * @param stream the stream for which the priority has changed.
         * @param previousParent the previous parent of the stream. May be the same as its current
         *            parent if unchanged.
         */
        void streamPriorityChanged(Http2Stream stream, Http2Stream previousParent);

        /**
         * Called when a priority change for a stream creates a circular dependency between the
         * stream and one of its descendants. This requires a restructuring of the priority tree.
         * <p>
         * Either this method or {@link #streamPriorityChanged} will be called, but not both for a
         * single change. For simple changes that do not cause the tree to be restructured,
         * {@link #streamPriorityChanged} will be called instead.
         *
         * @param stream the stream for which the priority has changed, causing the tree to be
         *            restructured.
         * @param subtreeRoot the new root of the subtree that has changed.
         */
        void streamPrioritySubtreeChanged(Http2Stream stream, Http2Stream subtreeRoot);
    }

    /**
     * A view of the connection from one endpoint (local or remote).
     */
    interface Endpoint {

        /**
         * Returns the next valid streamId for this endpoint.
         */
        int nextStreamId();

        /**
         * Creates a stream initiated by this endpoint. This could fail for the following reasons:
         * <p/>
         * - The requested stream ID is not the next sequential ID for this endpoint. <br>
         * - The stream already exists. <br>
         * - The number of concurrent streams is above the allowed threshold for this endpoint. <br>
         * - The connection is marked as going away}. <br>
         *
         * @param streamId The ID of the stream
         * @param halfClosed if true, the stream is created in the half-closed state with respect to
         *            this endpoint. Otherwise it's created in the open state.
         */
        Http2Stream createStream(int streamId, boolean halfClosed) throws Http2Exception;

        /**
         * Creates a push stream in the reserved state for this endpoint and notifies all listeners.
         * This could fail for the following reasons:
         * <p/>
         * - Server push is not allowed to the opposite endpoint. <br>
         * - The requested stream ID is not the next sequential stream ID for this endpoint. <br>
         * - The number of concurrent streams is above the allowed threshold for this endpoint. <br>
         * - The connection is marked as going away. <br>
         * - The parent stream ID does not exist or is not open from the side sending the push
         * promise. <br>
         * - Could not set a valid priority for the new stream.
         *
         * @param streamId the ID of the push stream
         * @param parent the parent stream used to initiate the push stream.
         */
        Http2Stream reservePushStream(int streamId, Http2Stream parent) throws Http2Exception;

        /**
         * Indicates whether or not this endpoint is the server-side of the connection.
         */
        boolean isServer();

        /**
         * Sets whether server push is allowed to this endpoint.
         */
        void allowPushTo(boolean allow);

        /**
         * Gets whether or not server push is allowed to this endpoint. This is always false
         * for a server endpoint.
         */
        boolean allowPushTo();

        /**
         * Gets the maximum number of concurrent streams allowed by this endpoint.
         */
        int maxStreams();

        /**
         * Sets the maximum number of concurrent streams allowed by this endpoint.
         */
        void maxStreams(int maxStreams);

        /**
         * Indicates whether or not this endpoint allows compression.
         */
        boolean allowCompressedData();

        /**
         * Sets whether or not this endpoint allows compression.
         */
        void allowCompressedData(boolean allow);

        /**
         * Gets the ID of the stream last successfully created by this endpoint.
         */
        int lastStreamCreated();

        /**
         * Gets the {@link Endpoint} opposite this one.
         */
        Endpoint opposite();
    }

    /**
     * Adds a listener of stream life-cycle events. Adding the same listener multiple times has no effect.
     */
    void addListener(Listener listener);

    /**
     * Removes a listener of stream life-cycle events.
     */
    void removeListener(Listener listener);

    /**
     * Attempts to get the stream for the given ID. If it doesn't exist, throws.
     */
    Http2Stream requireStream(int streamId) throws Http2Exception;

    /**
     * Gets the stream if it exists. If not, returns {@code null}.
     */
    Http2Stream stream(int streamId);

    /**
     * Gets the stream object representing the connection, itself (i.e. stream zero). This object
     * always exists.
     */
    Http2Stream connectionStream();

    /**
     * Gets the number of streams that are currently either open or half-closed.
     */
    int numActiveStreams();

    /**
     * Gets all streams that are currently either open or half-closed. The returned collection is
     * sorted by priority.
     */
    Collection<Http2Stream> activeStreams();

    /**
     * Indicates whether or not the local endpoint for this connection is the server.
     */
    boolean isServer();

    /**
     * Gets a view of this connection from the local {@link Endpoint}.
     */
    Endpoint local();

    /**
     * Gets a view of this connection from the remote {@link Endpoint}.
     */
    Endpoint remote();

    /**
     * Marks that a GoAway frame has been sent on this connection. After calling this, both
     * {@link #isGoAwaySent()} and {@link #isGoAway()} will be {@code true}.
     */
    void goAwaySent();

    /**
     * Marks that a GoAway frame has been received on this connection. After calling this, both
     * {@link #isGoAwayReceived()} and {@link #isGoAway()} will be {@code true}.
     */
    void goAwayReceived();

    /**
     * Indicates that this connection received a GoAway message.
     */
    boolean isGoAwaySent();

    /**
     * Indicates that this connection send a GoAway message.
     */
    boolean isGoAwayReceived();

    /**
     * Indicates whether or not this endpoint is going away. This is a short form for
     * {@link #isGoAwaySent()} || {@link #isGoAwayReceived()}.
     */
    boolean isGoAway();
}
