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
         * Notifies the listener that a priority tree parent change has occurred. This method will be invoked
         * in a top down order relative to the priority tree. This method will also be invoked after all tree
         * structure changes have been made and the tree is in steady state relative to the priority change
         * which caused the tree structure to change.
         * @param stream The stream which had a parent change (new parent and children will be steady state)
         * @param oldParent The old parent which {@code stream} used to be a child of (may be {@code null})
         */
        void priorityTreeParentChanged(Http2Stream stream, Http2Stream oldParent);

        /**
         * Notifies the listener that a parent dependency is about to change
         * This is called while the tree is being restructured and so the tree
         * structure is not necessarily steady state.
         * @param stream The stream which the parent is about to change to {@code newParent}
         * @param newParent The stream which will be the parent of {@code stream}
         */
        void priorityTreeParentChanging(Http2Stream stream, Http2Stream newParent);

        /**
         * Notifies the listener that the weight has changed for {@code stream}
         * @param stream The stream which the weight has changed
         * @param oldWeight The old weight for {@code stream}
         */
        void onWeightChanged(Http2Stream stream, short oldWeight);

        /**
         * Called when a GO_AWAY frame has either been sent or received for the connection.
         */
        void goingAway();
    }

    /**
     * A view of the connection from one endpoint (local or remote).
     */
    interface Endpoint {

        /**
         * Returns the next valid streamId for this endpoint. If negative, the stream IDs are
         * exhausted for this endpoint an no further streams may be created.
         */
        int nextStreamId();

        /**
         * Indicates whether the given streamId is from the set of IDs used by this endpoint to
         * create new streams.
         */
        boolean createdStreamId(int streamId);

        /**
         * Indicates whether or not this endpoint is currently accepting new streams. This will be
         * be false if {@link #numActiveStreams()} + 1 >= {@link #maxStreams()} or if the stream IDs
         * for this endpoint have been exhausted (i.e. {@link #nextStreamId()} < 0).
         */
        boolean acceptingNewStreams();

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
         * Gets the number of currently active streams that were created by this endpoint.
         */
        int numActiveStreams();

        /**
         * Gets the maximum number of concurrent streams allowed by this endpoint.
         */
        int maxStreams();

        /**
         * Sets the maximum number of concurrent streams allowed by this endpoint.
         */
        void maxStreams(int maxStreams);

        /**
         * Gets the ID of the stream last successfully created by this endpoint.
         */
        int lastStreamCreated();

        /**
         * Gets the last stream created by this endpoint that is "known" by the opposite endpoint.
         * If a GOAWAY was received for this endpoint, this will be the last stream ID from the
         * GOAWAY frame. Otherwise, this will be same as {@link #lastStreamCreated()}.
         */
        int lastKnownStream();

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
     * Creates a new stream initiated by the local endpoint. See {@link Endpoint#createStream(int, boolean)}.
     */
    Http2Stream createLocalStream(int streamId, boolean halfClosed) throws Http2Exception;

    /**
     * Gets a view of this connection from the remote {@link Endpoint}.
     */
    Endpoint remote();

    /**
     * Creates a new stream initiated by the remote endpoint. See {@link Endpoint#createStream(int, boolean)}.
     */
    Http2Stream createRemoteStream(int streamId, boolean halfClosed) throws Http2Exception;

    /**
     * Indicates whether or not a {@code GOAWAY} was received from the remote endpoint.
     */
    boolean goAwayReceived();

    /**
     * Indicates that a {@code GOAWAY} was received from the remote endpoint and sets the last known stream.
     */
    void goAwayReceived(int lastKnownStream);

    /**
     * Indicates whether or not a {@code GOAWAY} was sent to the remote endpoint.
     */
    boolean goAwaySent();

    /**
     * Indicates that a {@code GOAWAY} was sent to the remote endpoint and sets the last known stream.
     */
    void goAwaySent(int lastKnownStream);

    /**
     * Indicates whether or not either endpoint has received a GOAWAY.
     */
    boolean isGoAway();
}
