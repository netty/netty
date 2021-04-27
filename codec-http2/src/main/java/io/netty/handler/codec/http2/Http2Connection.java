/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.handler.codec.http2;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.UnstableApi;

/**
 * Manager for the state of an HTTP/2 connection with the remote end-point.
 */
@UnstableApi
public interface Http2Connection {
    /**
     * Listener for life-cycle events for streams in this connection.
     */
    interface Listener {
        /**
         * Notifies the listener that the given stream was added to the connection. This stream may
         * not yet be active (i.e. {@code OPEN} or {@code HALF CLOSED}).
         * <p>
         * If a {@link RuntimeException} is thrown it will be logged and <strong>not propagated</strong>.
         * Throwing from this method is not supported and is considered a programming error.
         */
        void onStreamAdded(Http2Stream stream);

        /**
         * Notifies the listener that the given stream was made active (i.e. {@code OPEN} or {@code HALF CLOSED}).
         * <p>
         * If a {@link RuntimeException} is thrown it will be logged and <strong>not propagated</strong>.
         * Throwing from this method is not supported and is considered a programming error.
         */
        void onStreamActive(Http2Stream stream);

        /**
         * Notifies the listener that the given stream has transitioned from {@code OPEN} to {@code HALF CLOSED}.
         * This method will <strong>not</strong> be called until a state transition occurs from when
         * {@link #onStreamActive(Http2Stream)} was called.
         * The stream can be inspected to determine which side is {@code HALF CLOSED}.
         * <p>
         * If a {@link RuntimeException} is thrown it will be logged and <strong>not propagated</strong>.
         * Throwing from this method is not supported and is considered a programming error.
         */
        void onStreamHalfClosed(Http2Stream stream);

        /**
         * Notifies the listener that the given stream is now {@code CLOSED} in both directions and will no longer
         * be accessible via {@link #forEachActiveStream(Http2StreamVisitor)}.
         * <p>
         * If a {@link RuntimeException} is thrown it will be logged and <strong>not propagated</strong>.
         * Throwing from this method is not supported and is considered a programming error.
         */
        void onStreamClosed(Http2Stream stream);

        /**
         * Notifies the listener that the given stream has now been removed from the connection and
         * will no longer be returned via {@link Http2Connection#stream(int)}. The connection may
         * maintain inactive streams for some time before removing them.
         * <p>
         * If a {@link RuntimeException} is thrown it will be logged and <strong>not propagated</strong>.
         * Throwing from this method is not supported and is considered a programming error.
         */
        void onStreamRemoved(Http2Stream stream);

        /**
         * Called when a {@code GOAWAY} frame was sent for the connection.
         * <p>
         * If a {@link RuntimeException} is thrown it will be logged and <strong>not propagated</strong>.
         * Throwing from this method is not supported and is considered a programming error.
         * @param lastStreamId the last known stream of the remote endpoint.
         * @param errorCode    the error code, if abnormal closure.
         * @param debugData    application-defined debug data.
         */
        void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData);

        /**
         * Called when a {@code GOAWAY} was received from the remote endpoint. This event handler duplicates {@link
         * Http2FrameListener#onGoAwayRead(io.netty.channel.ChannelHandlerContext, int, long, io.netty.buffer.ByteBuf)}
         * but is added here in order to simplify application logic for handling {@code GOAWAY} in a uniform way. An
         * application should generally not handle both events, but if it does this method is called second, after
         * notifying the {@link Http2FrameListener}.
         * <p>
         * If a {@link RuntimeException} is thrown it will be logged and <strong>not propagated</strong>.
         * Throwing from this method is not supported and is considered a programming error.
         * @param lastStreamId the last known stream of the remote endpoint.
         * @param errorCode    the error code, if abnormal closure.
         * @param debugData    application-defined debug data.
         */
        void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData);
    }

    /**
     * A view of the connection from one endpoint (local or remote).
     */
    interface Endpoint<F extends Http2FlowController> {
        /**
         * Increment and get the next generated stream id this endpoint. If negative, the stream IDs are
         * exhausted for this endpoint an no further streams may be created.
         */
        int incrementAndGetNextStreamId();

        /**
         * Indicates whether the given streamId is from the set of IDs used by this endpoint to
         * create new streams.
         */
        boolean isValidStreamId(int streamId);

        /**
         * Indicates whether or not this endpoint may have created the given stream. This is {@code true} if
         * {@link #isValidStreamId(int)} and {@code streamId} <= {@link #lastStreamCreated()}.
         */
        boolean mayHaveCreatedStream(int streamId);

        /**
         * Indicates whether or not this endpoint created the given stream.
         */
        boolean created(Http2Stream stream);

        /**
         * Indicates whether or a stream created by this endpoint can be opened without violating
         * {@link #maxActiveStreams()}.
         */
        boolean canOpenStream();

        /**
         * Creates a stream initiated by this endpoint. This could fail for the following reasons:
         * <ul>
         * <li>The requested stream ID is not the next sequential ID for this endpoint.</li>
         * <li>The stream already exists.</li>
         * <li>{@link #canOpenStream()} is {@code false}.</li>
         * <li>The connection is marked as going away.</li>
         * </ul>
         * <p>
         * The initial state of the stream will be immediately set before notifying {@link Listener}s. The state
         * transition is sensitive to {@code halfClosed} and is defined by {@link Http2Stream#open(boolean)}.
         * @param streamId The ID of the stream
         * @param halfClosed see {@link Http2Stream#open(boolean)}.
         * @see Http2Stream#open(boolean)
         */
        Http2Stream createStream(int streamId, boolean halfClosed) throws Http2Exception;

        /**
         * Creates a push stream in the reserved state for this endpoint and notifies all listeners.
         * This could fail for the following reasons:
         * <ul>
         * <li>Server push is not allowed to the opposite endpoint.</li>
         * <li>The requested stream ID is not the next sequential stream ID for this endpoint.</li>
         * <li>The number of concurrent streams is above the allowed threshold for this endpoint.</li>
         * <li>The connection is marked as going away.</li>
         * <li>The parent stream ID does not exist or is not {@code OPEN} from the side sending the push
         * promise.</li>
         * <li>Could not set a valid priority for the new stream.</li>
         * </ul>
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
         * This is the <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_ENABLE_PUSH</a> value sent
         * from the opposite endpoint. This method should only be called by Netty (not users) as a result of a
         * receiving a {@code SETTINGS} frame.
         */
        void allowPushTo(boolean allow);

        /**
         * This is the <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_ENABLE_PUSH</a> value sent
         * from the opposite endpoint. The initial value must be {@code true} for the client endpoint and always false
         * for a server endpoint.
         */
        boolean allowPushTo();

        /**
         * Gets the number of active streams (i.e. {@code OPEN} or {@code HALF CLOSED}) that were created by this
         * endpoint.
         */
        int numActiveStreams();

        /**
         * Gets the maximum number of streams (created by this endpoint) that are allowed to be active at
         * the same time. This is the
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_CONCURRENT_STREAMS</a>
         * value sent from the opposite endpoint to restrict stream creation by this endpoint.
         * <p>
         * The default value returned by this method must be "unlimited".
         */
        int maxActiveStreams();

        /**
         * Sets the limit for {@code SETTINGS_MAX_CONCURRENT_STREAMS}.
         * @param maxActiveStreams The maximum number of streams (created by this endpoint) that are allowed to be
         * active at once. This is the
         * <a href="https://tools.ietf.org/html/rfc7540#section-6.5.2">SETTINGS_MAX_CONCURRENT_STREAMS</a> value sent
         * from the opposite endpoint to restrict stream creation by this endpoint.
         */
        void maxActiveStreams(int maxActiveStreams);

        /**
         * Gets the ID of the stream last successfully created by this endpoint.
         */
        int lastStreamCreated();

        /**
         * If a GOAWAY was received for this endpoint, this will be the last stream ID from the
         * GOAWAY frame. Otherwise, this will be {@code -1}.
         */
        int lastStreamKnownByPeer();

        /**
         * Gets the flow controller for this endpoint.
         */
        F flowController();

        /**
         * Sets the flow controller for this endpoint.
         */
        void flowController(F flowController);

        /**
         * Gets the {@link Endpoint} opposite this one.
         */
        Endpoint<? extends Http2FlowController> opposite();
    }

    /**
     * A key to be used for associating application-defined properties with streams within this connection.
     */
    interface PropertyKey {
    }

    /**
     * Close this connection. No more new streams can be created after this point and
     * all streams that exists (active or otherwise) will be closed and removed.
     * <p>Note if iterating active streams via {@link #forEachActiveStream(Http2StreamVisitor)} and an exception is
     * thrown it is necessary to call this method again to ensure the close completes.
     * @param promise Will be completed when all streams have been removed, and listeners have been notified.
     * @return A future that will be completed when all streams have been removed, and listeners have been notified.
     */
    Future<Void> close(Promise<Void> promise);

    /**
     * Creates a new key that is unique within this {@link Http2Connection}.
     */
    PropertyKey newKey();

    /**
     * Adds a listener of stream life-cycle events.
     */
    void addListener(Listener listener);

    /**
     * Removes a listener of stream life-cycle events. If the same listener was added multiple times
     * then only the first occurrence gets removed.
     */
    void removeListener(Listener listener);

    /**
     * Gets the stream if it exists. If not, returns {@code null}.
     */
    Http2Stream stream(int streamId);

    /**
     * Indicates whether or not the given stream may have existed within this connection. This is a short form
     * for calling {@link Endpoint#mayHaveCreatedStream(int)} on both endpoints.
     */
    boolean streamMayHaveExisted(int streamId);

    /**
     * Gets the stream object representing the connection, itself (i.e. stream zero). This object
     * always exists.
     */
    Http2Stream connectionStream();

    /**
     * Gets the number of streams that are actively in use (i.e. {@code OPEN} or {@code HALF CLOSED}).
     */
    int numActiveStreams();

    /**
     * Provide a means of iterating over the collection of active streams.
     *
     * @param visitor The visitor which will visit each active stream.
     * @return The stream before iteration stopped or {@code null} if iteration went past the end.
     */
    Http2Stream forEachActiveStream(Http2StreamVisitor visitor) throws Http2Exception;

    /**
     * Indicates whether or not the local endpoint for this connection is the server.
     */
    boolean isServer();

    /**
     * Gets a view of this connection from the local {@link Endpoint}.
     */
    Endpoint<Http2LocalFlowController> local();

    /**
     * Gets a view of this connection from the remote {@link Endpoint}.
     */
    Endpoint<Http2RemoteFlowController> remote();

    /**
     * Indicates whether or not a {@code GOAWAY} was received from the remote endpoint.
     */
    boolean goAwayReceived();

    /**
     * Indicates that a {@code GOAWAY} was received from the remote endpoint and sets the last known stream.
     * @param lastKnownStream The Last-Stream-ID in the
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.8">GOAWAY</a> frame.
     * @param errorCode the Error Code in the
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.8">GOAWAY</a> frame.
     * @param message The Additional Debug Data in the
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.8">GOAWAY</a> frame. Note that reference count ownership
     * belongs to the caller (ownership is not transferred to this method).
     */
    void goAwayReceived(int lastKnownStream, long errorCode, ByteBuf message) throws Http2Exception;

    /**
     * Indicates whether or not a {@code GOAWAY} was sent to the remote endpoint.
     */
    boolean goAwaySent();

    /**
     * Updates the local state of this {@link Http2Connection} as a result of a {@code GOAWAY} to send to the remote
     * endpoint.
     * @param lastKnownStream The Last-Stream-ID in the
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.8">GOAWAY</a> frame.
     * @param errorCode the Error Code in the
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.8">GOAWAY</a> frame.
     * <a href="https://tools.ietf.org/html/rfc7540#section-6.8">GOAWAY</a> frame. Note that reference count ownership
     * belongs to the caller (ownership is not transferred to this method).
     * @return {@code true} if the corresponding {@code GOAWAY} frame should be sent to the remote endpoint.
     */
    boolean goAwaySent(int lastKnownStream, long errorCode, ByteBuf message) throws Http2Exception;
}
