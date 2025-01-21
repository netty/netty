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

/**
 * A single stream within an HTTP2 connection. Streams are compared to each other by priority.
 */
public interface Http2Stream {

    /**
     * The allowed states of an HTTP2 stream.
     */
    enum State {
        IDLE(false, false),
        RESERVED_LOCAL(false, false),
        RESERVED_REMOTE(false, false),
        OPEN(true, true),
        HALF_CLOSED_LOCAL(false, true),
        HALF_CLOSED_REMOTE(true, false),
        CLOSED(false, false);

        private final boolean localSideOpen;
        private final boolean remoteSideOpen;

        State(boolean localSideOpen, boolean remoteSideOpen) {
            this.localSideOpen = localSideOpen;
            this.remoteSideOpen = remoteSideOpen;
        }

        /**
         * Indicates whether the local side of this stream is open (i.e. the state is either
         * {@link State#OPEN} or {@link State#HALF_CLOSED_REMOTE}).
         */
        public boolean localSideOpen() {
            return localSideOpen;
        }

        /**
         * Indicates whether the remote side of this stream is open (i.e. the state is either
         * {@link State#OPEN} or {@link State#HALF_CLOSED_LOCAL}).
         */
        public boolean remoteSideOpen() {
            return remoteSideOpen;
        }
    }

    /**
     * Gets the unique identifier for this stream within the connection.
     */
    int id();

    /**
     * Gets the state of this stream.
     */
    State state();

    /**
     * Opens this stream, making it available via {@link Http2Connection#forEachActiveStream(Http2StreamVisitor)} and
     * transition state to:
     * <ul>
     * <li>{@link State#OPEN} if {@link #state()} is {@link State#IDLE} and {@code halfClosed} is {@code false}.</li>
     * <li>{@link State#HALF_CLOSED_LOCAL} if {@link #state()} is {@link State#IDLE} and {@code halfClosed}
     * is {@code true} and the stream is local. In this state, {@link #isHeadersSent()} is {@code true}</li>
     * <li>{@link State#HALF_CLOSED_REMOTE} if {@link #state()} is {@link State#IDLE} and {@code halfClosed}
     * is {@code true} and the stream is remote. In this state, {@link #isHeadersReceived()} is {@code true}</li>
     * <li>{@link State#RESERVED_LOCAL} if {@link #state()} is {@link State#HALF_CLOSED_REMOTE}.</li>
     * <li>{@link State#RESERVED_REMOTE} if {@link #state()} is {@link State#HALF_CLOSED_LOCAL}.</li>
     * </ul>
     */
    Http2Stream open(boolean halfClosed) throws Http2Exception;

    /**
     * Closes the stream.
     */
    Http2Stream close();

    /**
     * Closes the local side of this stream. If this makes the stream closed, the child is closed as
     * well.
     */
    Http2Stream closeLocalSide();

    /**
     * Closes the remote side of this stream. If this makes the stream closed, the child is closed
     * as well.
     */
    Http2Stream closeRemoteSide();

    /**
     * Indicates whether a {@code RST_STREAM} frame has been sent from the local endpoint for this stream.
     */
    boolean isResetSent();

    /**
     * Sets the flag indicating that a {@code RST_STREAM} frame has been sent from the local endpoint
     * for this stream. This does not affect the stream state.
     */
    Http2Stream resetSent();

    /**
     * Associates the application-defined data with this stream.
     * @return The value that was previously associated with {@code key}, or {@code null} if there was none.
     */
    <V> V setProperty(Http2Connection.PropertyKey key, V value);

    /**
     * Returns application-defined data if any was associated with this stream.
     */
    <V> V getProperty(Http2Connection.PropertyKey key);

    /**
     * Returns and removes application-defined data if any was associated with this stream.
     */
    <V> V removeProperty(Http2Connection.PropertyKey key);

    /**
     * Indicates that headers have been sent to the remote endpoint on this stream. The first call to this method would
     * be for the initial headers (see {@link #isHeadersSent()}} and the second call would indicate the trailers
     * (see {@link #isTrailersReceived()}).
     * @param isInformational {@code true} if the headers contain an informational status code (for responses only).
     */
    Http2Stream headersSent(boolean isInformational);

    /**
     * Indicates whether or not headers were sent to the remote endpoint.
     */
    boolean isHeadersSent();

    /**
     * Indicates whether or not trailers were sent to the remote endpoint.
     */
    boolean isTrailersSent();

    /**
     * Indicates that headers have been received. The first call to this method would be for the initial headers
     * (see {@link #isHeadersReceived()}} and the second call would indicate the trailers
     * (see {@link #isTrailersReceived()}).
     * @param isInformational {@code true} if the headers contain an informational status code (for responses only).
     */
    Http2Stream headersReceived(boolean isInformational);

    /**
     * Indicates whether or not the initial headers have been received.
     */
    boolean isHeadersReceived();

    /**
     * Indicates whether or not the trailers have been received.
     */
    boolean isTrailersReceived();

    /**
     * Indicates that a push promise was sent to the remote endpoint.
     */
    Http2Stream pushPromiseSent();

    /**
     * Indicates whether or not a push promise was sent to the remote endpoint.
     */
    boolean isPushPromiseSent();
}
